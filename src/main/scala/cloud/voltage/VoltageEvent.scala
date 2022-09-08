package cloud.voltage

import org.bitcoins.commons.serializers.SerializerUtil
import org.bitcoins.crypto.StringFactory
import play.api.libs.json._

case class VoltageEvent(
    `type`: VoltageEventType,
    details: JsObject,
    api: String) {

  lazy val statusOpt: Option[String] = `type` match {
    case VoltageEventType.Status =>
      val js = details \ "status"
      js.validate[String].asOpt
    case VoltageEventType.Error | VoltageEventType.Update => None
  }
}

object VoltageEvent {
  implicit val reads: Reads[VoltageEvent] = Json.reads[VoltageEvent]
}

sealed abstract class VoltageEventType

case object VoltageEventType extends StringFactory[VoltageEventType] {
  case object Status extends VoltageEventType
  case object Update extends VoltageEventType
  case object Error extends VoltageEventType

  val all: Vector[VoltageEventType] = Vector(Status, Update, Error)

  override def fromStringOpt(string: String): Option[VoltageEventType] = {
    val searchString = string.trim.toLowerCase
    all.find(_.toString.toLowerCase == searchString)
  }

  override def fromString(string: String): VoltageEventType = {
    fromStringOpt(string).getOrElse(
      throw new IllegalArgumentException(
        s"Could not find a VoltageEventType for string $string"))
  }

  implicit val VoltageEventTypeReads: Reads[VoltageEventType] = (js: JsValue) =>
    SerializerUtil.processJsStringOpt(fromStringOpt)(js)
}
