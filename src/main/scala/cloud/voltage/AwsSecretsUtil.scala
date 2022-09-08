package cloud.voltage

import akka.actor.ActorSystem
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest
import grizzled.slf4j.Logging
import org.bitcoins.lnd.rpc.LndRpcClient
import org.bitcoins.lnd.rpc.config._
import play.api.libs.json._

import java.net.URI
import java.nio.ByteBuffer
import scala.util._

trait AwsSecretsUtil extends Logging {

  private case class LndSecretResult(
      LND_RPC_URI: String,
      LND_MACAROON: String,
      LND_CERTIFICATE: String,
      VOLTAGE_SECRET: String,
      VOLTAGE_API: String,
      LND_PASSWORD: String)

  case class VoltageSecret(secret: String, api: String, lndPassword: String)

  implicit private val lndSecretResultReads: Reads[LndSecretResult] =
    Json.reads[LndSecretResult]

  def getLnd()(implicit system: ActorSystem): (LndRpcClient, VoltageSecret) = {
    val result =
      getSecretResult[LndSecretResult]("LND_CREDENTIALS", None)

    val instance =
      LndInstanceRemote(rpcUri = new URI(result.LND_RPC_URI),
                        macaroon = result.LND_MACAROON,
                        certFileOpt = None,
                        certificateOpt = None)

    val lnd = new LndRpcClient(instance, None)
    val voltage = VoltageSecret(secret = result.VOLTAGE_SECRET.trim,
                                api = result.VOLTAGE_API.trim,
                                lndPassword = result.LND_PASSWORD.trim)

    logger.info("Created lnd client")

    (lnd, voltage)
  }

  private def getSecretResult[T](
      secretName: String,
      fallback: Option[T],
      versionStage: String = "AWSCURRENT")(implicit reads: Reads[T]): T = {
    val resT = Try {
      val secretE = getSecretJson(secretName, versionStage)
      val secretJson = secretE match {
        case Left(str)    => Json.parse(str)
        case Right(bytes) => Json.parse(bytes.array())
      }
      secretJson.validate[T].getOrElse {
        logger.info(s"Using fallback credentials for $secretName")
        fallback.getOrElse(
          throw new RuntimeException(s"Could not parse secret for $secretName"))
      }
    }

    resT match {
      case Failure(exception) =>
        logger.error(s"Error, using fallback credentials for $secretName",
                     exception)
        fallback.getOrElse(throw exception)
      case Success(result) => result
    }
  }

  private def getSecretJson(
      secretName: String,
      versionStage: String): Either[String, ByteBuffer] = {
    logger.info(s"Fetching secret $secretName")
    val region = sys.env.getOrElse("AWS_REGION", "us-east-2")
    val endpoint = s"secretsmanager.$region.amazonaws.com"

    val endpointConfig = new EndpointConfiguration(endpoint, region)
    val clientBuilder = AWSSecretsManagerClientBuilder.standard()
    clientBuilder.setEndpointConfiguration(endpointConfig)

    val client = clientBuilder.build
    val getSecretValueRequest = new GetSecretValueRequest()
      .withSecretId(secretName)
      .withVersionStage(versionStage)

    val getSecretValueResult = client.getSecretValue(getSecretValueRequest)

    // Depending on whether the secret was a string or binary, one of these fields will be populated
    if (getSecretValueResult.getSecretString != null) {
      Left(getSecretValueResult.getSecretString)
    } else {
      Right(getSecretValueResult.getSecretBinary)
    }
  }
}
