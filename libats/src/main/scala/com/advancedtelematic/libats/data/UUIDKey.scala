package com.advancedtelematic.libats.data

import java.util.UUID
import cats.Show
import com.fasterxml.uuid.Generators
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import shapeless.*

object UUIDKey {
  private lazy val timeBasedUuidGenerator = Generators.timeBasedEpochGenerator()

  abstract class UUIDKeyObj[Self <: UUIDKey] {

    type SelfGen = Generic.Aux[Self, UUID :: HNil]

    def generate()(implicit gen: SelfGen): Self =
      fromJava(UUID.randomUUID())

    protected def fromJava(value: UUID)(implicit gen: SelfGen): Self = {
      gen.from(value :: HNil)
    }

    implicit val encoder: Encoder[Self] = Encoder[String].contramap(_.uuid.toString)

    implicit def decoder(implicit gen: SelfGen): Decoder[Self] =
      Decoder[UUID].map(fromJava)

    implicit def keyDecoder(implicit gen: SelfGen): KeyDecoder[Self] =
      KeyDecoder[UUID].map { uuid => gen.from(uuid :: HNil) }

    implicit def keyEncoder: KeyEncoder[Self] = KeyEncoder[String].contramap(_.uuid.toString)

    implicit val abstractKeyShow: Show[Self] = Show.show[Self](_.uuid.toString)
  }

  abstract class UuidKeyObjTimeBased[Self <: UUIDKey] extends UUIDKeyObj[Self] {
    override def generate()(implicit gen: SelfGen) =
      fromJava(timeBasedUuidGenerator.generate())
  }

  abstract class UUIDKey {
    val uuid: UUID
  }
}
