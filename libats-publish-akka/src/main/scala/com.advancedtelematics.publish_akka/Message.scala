package com.advancedtelematics.publish_akka
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import io.circe._, io.circe.generic.semiauto._
import com.advancedtelematic.libats.codecs.CirceCodecs._
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

object Message {
  final case class HttpRequestDescription(method: String, uri: String, respCode: Int, namespace: Namespace, timeMs: Long)
  implicit val reqDescriptionEncoder: Encoder[HttpRequestDescription] = deriveEncoder
  implicit val reqDescriptionDecoder: Decoder[HttpRequestDescription] = deriveDecoder
  implicit val reqDescriptionMsgLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematics.publish_akka.Message.HttpRequestDescription] = MessageLike[HttpRequestDescription](_.namespace.get)
}
