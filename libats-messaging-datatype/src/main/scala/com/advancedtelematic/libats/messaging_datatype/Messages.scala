package com.advancedtelematic.libats.messaging_datatype

import cats.syntax.show._
import com.advancedtelematic.libats.codecs.CirceValidatedGeneric
import com.advancedtelematic.libats.data.DataType.{CorrelationId, Namespace, ResultCode, ResultDescription}
import com.advancedtelematic.libats.data.EcuIdentifier
import com.advancedtelematic.libats.data.EcuIdentifier._
import com.advancedtelematic.libats.messaging_datatype.DataType.UpdateType.UpdateType
import com.advancedtelematic.libats.messaging_datatype.DataType._
import com.advancedtelematic.libats.messaging_datatype.Messages.{CampaignLaunched, DeviceEventMessage, DeviceSystemInfoChanged, DeviceUpdateAssigned, DeviceUpdateCanceled, DeviceUpdateCompleted, DeviceUpdateEvent, EcuAndHardwareId, EcuReplaced, EcuReplacement, EcuReplacementFailed, SystemInfo, UserCreated}
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

import java.net.URI
import java.time.Instant
import java.util.UUID

object MessageCodecs {
  import com.advancedtelematic.libats.codecs.CirceCodecs._

  implicit val eventTypeCodec: Codec[EventType] = deriveCodec
  implicit val eventCodec: Codec[Event] = deriveCodec

  implicit val deviceEventEncoder: Encoder[DeviceEventMessage] = Encoder.instance { x =>
    eventCodec(x.event).mapObject(_.add("namespace", x.namespace.get.asJson))
  }

  implicit val deviceEventDecoder: Decoder[DeviceEventMessage] = Decoder.instance { c =>
    for {
      event <- c.as[Event]
      ns    <- c.get[String]("namespace").map(Namespace.apply)
    } yield DeviceEventMessage(ns, event)
  }

  implicit val ecuIdentifierKeyCodec: KeyEncoder[EcuIdentifier] = CirceValidatedGeneric.validatedGenericKeyEncoder[EcuIdentifier, String]
  implicit val ecuIdentifierKeyDecoder: KeyDecoder[EcuIdentifier] = CirceValidatedGeneric.validatedGenericKeyDecoder[EcuIdentifier, String]

  implicit val deviceUpdateEventCodec: Codec[DeviceUpdateEvent] = deriveCodec
  implicit val deviceUpdateAvailableCodec: Codec[DeviceUpdateAssigned] = deriveCodec
  implicit val deviceUpdateCanceledCodec: Codec[DeviceUpdateCanceled] = deriveCodec
  implicit val deviceUpdateCompletedCodec: Codec[DeviceUpdateCompleted] = deriveCodec
  implicit val userCreatedCodec: Codec[UserCreated] = deriveCodec
  implicit val campaignLaunchedCodec: Codec[CampaignLaunched] = deriveCodec
  implicit val packageIdCodec: Codec[PackageId] = deriveCodec
  implicit val resultCodeEncoder: Encoder[ResultCode] = Encoder.encodeString.contramap(_.value)
  implicit val resultCodeDecoder: Decoder[ResultCode] = Decoder.decodeString.map(ResultCode)
  implicit val resultDescriptionEncoder: Encoder[ResultDescription] = Encoder.encodeString.contramap(_.value)
  implicit val resultDescriptionDecoder: Decoder[ResultDescription] = Decoder.decodeString.map(ResultDescription)
  implicit val installationResultCodec: Codec[InstallationResult] = deriveCodec
  implicit val ecuInstallationReportCodec: Codec[EcuInstallationReport] = deriveCodec
  implicit val updateTypeCodec: Codec[UpdateType] = Codec.codecForEnumeration(UpdateType)
  implicit val systemInfoCodec: Codec[SystemInfo] = deriveCodec
  implicit val deviceSystemInfoChangedCodec: Codec[DeviceSystemInfoChanged] = deriveCodec
  implicit val ecuAndHardwareIdCodec: Codec[EcuAndHardwareId] = deriveCodec
  implicit val ecuReplacementCodec: Codec[EcuReplacement] = Codec.from(
    Decoder.instance { c =>
      c.get[Boolean]("success").flatMap {
        case true => deriveDecoder[EcuReplaced].tryDecode(c)
        case false => deriveDecoder[EcuReplacementFailed].tryDecode(c)
      }
    },
    Encoder.instance {
      case e: EcuReplaced => deriveEncoder[EcuReplaced].apply(e).mapObject(("success", Json.fromBoolean(true)) +: _)
      case e: EcuReplacementFailed => deriveEncoder[EcuReplacementFailed].apply(e).mapObject(("success", Json.fromBoolean(false)) +: _)
    }
  )
}

object Messages {
  import MessageCodecs._
  import com.advancedtelematic.libats.codecs.CirceCodecs._

  final case class UserCreated(id: String)

  final case class DeviceSeen(namespace: Namespace, uuid: DeviceId, lastSeen: Instant = Instant.now)

  final case class CampaignLaunched(namespace: String, updateId: UUID,
                                    devices: Set[UUID], pkgUri: URI,
                                    pkg: PackageId, pkgSize: Long, pkgChecksum: String)

  final case class DeviceEventMessage(namespace: Namespace, event: Event)

  final case class DeviceMetricsObservation(namespace: Namespace, uuid: DeviceId, payload: Json, receivedAt: Instant)

  case class BandwidthUsage(id: UUID, namespace: Namespace, timestamp: Instant, byteCount: Long,
                            updateType: UpdateType, updateId: String)

  case class ImageStorageUsage(namespace: Namespace, timestamp: Instant, byteCount: Long)

  sealed trait DeviceUpdateEvent {
    def namespace: Namespace
    def eventTime: Instant
    def correlationId: CorrelationId
    def deviceUuid: DeviceId
  }

  final case class DeviceUpdateAssigned(
      namespace: Namespace,
      eventTime: Instant,
      correlationId: CorrelationId,
      deviceUuid: DeviceId
  ) extends DeviceUpdateEvent

  final case class DeviceUpdateInFlight(
      namespace: Namespace,
      eventTime: Instant,
      correlationId: CorrelationId,
      deviceUuid: DeviceId
  ) extends DeviceUpdateEvent

  final case class DeviceUpdateAssignmentRejected(
      namespace: Namespace,
      eventTime: Instant,
      correlationId: CorrelationId,
      deviceUuid: DeviceId
  ) extends DeviceUpdateEvent

  final case class DeviceUpdateCancelRequested(
      namespace: Namespace,
      eventTime: Instant,
      correlationId: CorrelationId,
      deviceUuid: DeviceId
  ) extends DeviceUpdateEvent

  final case class DeviceUpdateCanceled(
      namespace: Namespace,
      eventTime: Instant,
      correlationId: CorrelationId,
      deviceUuid: DeviceId
  ) extends DeviceUpdateEvent

  final case class DeviceUpdateCompleted(
      namespace: Namespace,
      eventTime: Instant,
      correlationId: CorrelationId,
      deviceUuid: DeviceId,
      result: InstallationResult,
      ecuReports: Map[EcuIdentifier, EcuInstallationReport],
      rawReport: Option[String] = None
  ) extends DeviceUpdateEvent

  final case class SystemInfo(product: Option[String])

  final case class DeviceSystemInfoChanged(namespace: Namespace, uuid: DeviceId, newSystemInfo: Option[SystemInfo])

  final case class CommitManifestUpdated(namespace: Namespace, commit: Commit, releaseBranch: String,
                                         metaUpdaterVersion: String, receivedAt: Instant = Instant.now())

  final case class AktualizrConfigChanged(namespace: Namespace, uuid: DeviceId, pollingSec: Int,
                                          secondaryPreinstallWaitSec: Option[Int], forceInstallCompletion: Boolean,
                                          installerType: String, receivedAt: Instant)

  final case class DeleteDeviceRequest(namespace: Namespace, uuid: DeviceId, timestamp: Instant = Instant.now())

  sealed trait EcuReplacement {
    val deviceUuid: DeviceId
    val eventTime: Instant
  }
  case class EcuAndHardwareId(ecuId: EcuIdentifier, hardwareId: String)
  final case class EcuReplaced(deviceUuid: DeviceId, former: EcuAndHardwareId, current: EcuAndHardwareId, eventTime: Instant = Instant.now) extends EcuReplacement
  final case class EcuReplacementFailed(deviceUuid: DeviceId, eventTime: Instant = Instant.now) extends EcuReplacement

  implicit val deviceSystemInfoChangedMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.DeviceSystemInfoChanged] = MessageLike.derive[DeviceSystemInfoChanged](_.uuid.toString)

  implicit val commitManifestUpdatedMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.CommitManifestUpdated] = MessageLike.derive[CommitManifestUpdated](_.commit.value)

  implicit val aktualizrConfigChangedMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.AktualizrConfigChanged] = MessageLike.derive[AktualizrConfigChanged](_.uuid.toString)

  implicit val userCreatedMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.UserCreated] = MessageLike[UserCreated](_.id)

  implicit val deviceSeenMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.DeviceSeen] = MessageLike.derive[DeviceSeen](_.uuid.toString)

  implicit val campaignLaunchedMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.CampaignLaunched] = MessageLike[CampaignLaunched](_.updateId.toString)

  implicit val bandwidthUsageMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.BandwidthUsage] = MessageLike.derive[BandwidthUsage](_.id.toString)

  implicit val imageStorageMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.ImageStorageUsage] = MessageLike.derive[ImageStorageUsage](_.namespace.get)

  implicit val deviceEventMessageType: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.DeviceEventMessage] = MessageLike[DeviceEventMessage](_.namespace.get)

  implicit val deviceUpdateEventMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.DeviceUpdateEvent] = MessageLike[DeviceUpdateEvent](_.namespace.get)

  implicit val deleteDeviceRequestMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.DeleteDeviceRequest] = MessageLike.derive[DeleteDeviceRequest](_.uuid.show)

  implicit val ecuReplacementMsgLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.EcuReplacement] = MessageLike[EcuReplacement](_.deviceUuid.show)

  implicit val deviceMetricsObservationMessageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging_datatype.Messages.DeviceMetricsObservation] = MessageLike.derive[DeviceMetricsObservation](_.namespace.get)
}
