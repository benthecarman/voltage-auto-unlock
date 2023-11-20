package cloud.voltage

import akka.actor.ActorSystem
import com.amazonaws.HttpMethod
import com.amazonaws.lambda.thirdparty.com.google.gson.{Gson, GsonBuilder}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.google.protobuf.ByteString
import grizzled.slf4j.Logging
import lnrpc.UnlockWalletRequest
import org.bitcoins.lnd.rpc.{LndRpcClient, LndUtils}
import play.api.libs.json._

import java.util
import java.util.{Map => JavaMap}
import scala.concurrent._
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.util._

class ServerlessHandler
    extends RequestHandler[JavaMap[String, Object],
                           APIGatewayProxyResponseEvent]
    with LndUtils
    with AwsSecretsUtil
    with Logging {
  val gson: Gson = new GsonBuilder().setPrettyPrinting().create

  implicit lazy val system: ActorSystem = ActorSystem("voltage-unlocker")

  implicit lazy val ec: ExecutionContext = system.dispatcher

  lazy val (lnd, voltageSecret): (LndRpcClient, VoltageSecret) = getLnd()

  def handleVoltageEvent(event: VoltageEvent): Future[ApiResponse[JsValue]] = {
    event.statusOpt match {
      case Some("waiting_unlock") =>
        val byteStrPass = ByteString.copyFromUtf8(voltageSecret.lndPassword)
        val req: UnlockWalletRequest =
          UnlockWalletRequest(walletPassword = byteStrPass,
                              statelessInit = true)

        logger.info("Unlocking voltage node!")

        lnd.unlocker
          .unlockWallet(req)
          .map(_ => ApiResponse.success(200, JsNull))
      case Some(status) =>
        logger.warn(s"Unhandled status: $status")
        Future.successful(ApiResponse.success(200, JsNull))
      case None =>
        Future.successful(ApiResponse.success(200, JsNull))
    }
  }

  def handle(
      path: String,
      method: HttpMethod,
      headers: Map[String, String],
      body: JsValue): Future[ApiResponse[JsValue]] = {
    val t = Try {
      import HttpMethod._

      (path, method) match {
        case ("/voltage", POST) =>
          val secretHeader = headers
            .getOrElse("Voltage-Secret",
                       headers.getOrElse(
                         "Voltage_secret",
                         headers.getOrElse("VOLTAGE_SECRET",
                                           throw new RuntimeException(
                                             "No Voltage_secret header"))))

          if (!secretHeader.trim.equalsIgnoreCase(voltageSecret.secret))
            throw new RuntimeException(s"Invalid voltage secret")
          val request = parseBody[VoltageEvent](body)
          if (!request.api.trim.equalsIgnoreCase(voltageSecret.api))
            throw new RuntimeException(
              s"Invalid voltage api, got ${request.api}, expected ${voltageSecret.api}")

          handleVoltageEvent(request)
        case (_, _) =>
          val fail = ApiResponse.error(404, s"Unknown path $path")
          Future.successful(fail)
      }
    }
    Future.fromTry(t).flatten.map(_.toJsonResponse)
  }

  override def handleRequest(
      event: JavaMap[String, Object],
      context: Context): APIGatewayProxyResponseEvent = {
    logger.trace("Received request!")

    val sourceT = Try(event.get("source").asInstanceOf[String])
    val source = sourceT.getOrElse("")

    if (source == "serverless-plugin-warmup") {
      logger.trace("keeping lambda warm")

      new APIGatewayProxyResponseEvent()
        .withStatusCode(200)
    } else {
      val path = Try(event.get("path").asInstanceOf[String])
        .getOrElse {
          val response = ApiResponse.error(400, "No path found")
          return response.gatewayProxyResponseEvent
        }

      val method =
        HttpMethod.valueOf(event.get("httpMethod").asInstanceOf[String])

      val headers =
        event.get("headers").asInstanceOf[util.HashMap[String, String]]

      // need to cast because amazon will break otherwise :/
      val bodyT = Try(Json.parse(event.get("body").asInstanceOf[String]))
      val body = bodyT.getOrElse(JsNull)

      val f =
        handle(path, method, headers.asScala.toMap, body)
          .recoverWith { case err: Throwable => // unhandled
            logger.error("Error processing request: ", err)
            Future.successful(ApiResponse.error(500, err.getMessage))
          }

      // we are using an API gateway which has a 30 second timeout
      val waitTime = 29.seconds
      val response = Await.result(f, waitTime)

      logger.trace("sending lambda response")
      response.gatewayProxyResponseEvent
    }
  }

  private def parseBody[T](body: JsValue)(implicit jsReads: Reads[T]): T = {
    body.validate[T] match {
      case JsSuccess(value, _) => value
      case JsError(_) =>
        throw new RuntimeException(
          s"Could not parse json to the proper request type, got $body")
    }
  }
}
