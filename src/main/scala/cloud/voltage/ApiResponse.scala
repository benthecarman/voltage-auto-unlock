package cloud.voltage

import play.api.libs.json._

import java.util
import java.util.{Map => JavaMap}

case class ApiResponse[T](
    statusCode: Int,
    result: Option[T],
    error: Option[JsValue],
    headers: JavaMap[String, String]
)(implicit val resultWrites: Writes[T]) {

  def toJsonResponse: ApiResponse[JsValue] = {
    ApiResponse(statusCode, result.map(Json.toJson(_)), error, headers)
  }

  lazy val gatewayProxyResponseEvent: APIGatewayProxyResponseEvent = {
    val json = Json.toJson(this)
    new APIGatewayProxyResponseEvent()
      .withStatusCode(statusCode)
      .withBody(json.toString)
      .withHeaders(headers)
      .withIsBase64Encoded(false)
  }
}

object ApiResponse {

  implicit def apiResponseWrites[T](implicit
      resultWrites: Writes[T]): OWrites[ApiResponse[T]] = OWrites { response =>
    Json.obj(
      "statusCode" -> response.statusCode,
      "result" -> response.result,
      "error" -> response.error
    )
  }

  def redirect(url: String): ApiResponse[None.type] = {
    val headers = new util.HashMap[String, String]()
    headers.put("Location", url)

    ApiResponse(statusCode = 301,
                result = None,
                error = None,
                headers = headers)
  }

  def success(statusCode: Int, result: JsValue): ApiResponse[JsValue] =
    ApiResponse(statusCode = statusCode,
                result = Some(result),
                error = None,
                headers = new util.HashMap[String, String]())

  def success[T](statusCode: Int, result: T)(implicit
      resultWrites: Writes[T]): ApiResponse[T] =
    ApiResponse(statusCode = statusCode,
                result = Some(result),
                error = None,
                headers = new util.HashMap[String, String]())

  def error(statusCode: Int, err: JsValue): ApiResponse[None.type] =
    ApiResponse(statusCode = statusCode,
                result = None,
                error = Some(err),
                headers = new util.HashMap[String, String]())

  def error(statusCode: Int, err: String): ApiResponse[None.type] =
    error(statusCode, JsString(err))
}
