package com.advancedtelematic.libats.data

import java.util.UUID
import com.advancedtelematic.libats.data.DataType.HashMethod.HashMethod
import eu.timepit.refined.api.{RefType, Refined, Validate}
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.Size
import eu.timepit.refined.generic.Equal
import eu.timepit.refined.string.HexStringSpec
import io.circe.{Decoder, Encoder}
import cats.syntax.either.*

import scala.language.postfixOps

object DataType {

  // The underlying type is String instead of UUID because we need to support the legacy format of the namespaces
  final case class Namespace(get: String) extends AnyVal

  object Namespace {
    def generate: Namespace = new Namespace(s"urn:here-ota:namespace:${UUID.randomUUID().toString}")
  }

  final case class ResultCode(value: String) extends AnyVal
  final case class ResultDescription(value: String) extends AnyVal

  case class Checksum(method: HashMethod, hash: Refined[String, ValidChecksum])

  object HashMethod extends Enumeration {
    type HashMethod = Value

    val SHA256 = Value("sha256")
  }

  case class ValidChecksum()

  implicit val validChecksum: Validate.Plain[String, ValidChecksum] =
    ValidationUtils.validHexValidation(ValidChecksum(), length = 64)

  sealed trait CorrelationId
  final case class CampaignId(value: UUID) extends CorrelationId {
    override def toString: String = s"urn:here-ota:campaign:$value"
  }
  final case class MultiTargetUpdateId(value: UUID) extends CorrelationId {
    override def toString: String = s"urn:here-ota:mtu:$value"
  }
  final case class AutoUpdateId(value: UUID) extends CorrelationId {
    override def toString: String = s"urn:here-ota:auto-update:$value"
  }

  type ValidLockboxHash = String Refined (HexStringSpec And Size[Equal[12]])

  final case class OfflineUpdateId(name: String, version: Long, hash: ValidLockboxHash) extends CorrelationId {
    override def toString: String = s"urn:tdx-ota:lockbox:$name:$version:${hash.value}"
  }
  object CorrelationId {
    private[this] val CorrelationIdRe = """^urn:here-ota:(mtu|auto-update|campaign):([0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12})$"""r

    private [this] val TrxCorrelationIdRe = """^urn:tdx-ota:lockbox:([A-Za-z0-9_-]+):([0-9]+):([0-9a-fA-F]{12})$"""r

    def fromString(s: String): Either[String, CorrelationId] = s match {
      case CorrelationIdRe("mtu", uuid) =>
        Right(MultiTargetUpdateId(UUID.fromString(uuid)))
      case CorrelationIdRe("campaign", uuid) =>
        Right(CampaignId(UUID.fromString(uuid)))
      case CorrelationIdRe("auto-update", uuid) =>
        Right(AutoUpdateId(UUID.fromString(uuid)))
      case TrxCorrelationIdRe(name, version, hash) =>
        RefType.applyRef[ValidLockboxHash](hash).map { hash =>
          OfflineUpdateId(name, version.toLong, hash)
        }
      case x =>
        Left(s"Invalid correlationId: '$x'")
    }

    implicit val DecoderInstance: Decoder[CorrelationId] = Decoder.decodeString.emap(CorrelationId.fromString)
    implicit val EncoderInstance: Encoder[CorrelationId] = Encoder.encodeString.contramap(_.toString)
  }
}
