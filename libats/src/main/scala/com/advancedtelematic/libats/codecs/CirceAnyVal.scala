package com.advancedtelematic.libats.codecs

import io.circe.{Decoder, Encoder}

// This causes problems on circe.semiauto marcros, it's difficult to predict when this is/is not in scope for the macro
// which leads to some codecs being generated/not generated with unclear results

@deprecated("use explicit codecs generation with io.circe.generic.semiauto instead", "0.0.1")
trait CirceAnyVal {

  import shapeless._

  private def anyValEncoder[Wrapper <: AnyVal, Wrapped](implicit gen: Generic.Aux[Wrapper, Wrapped :: HNil],
                                                         wrappedEncoder: Encoder[Wrapped]): Encoder[Wrapper] =
    wrappedEncoder.contramap[Wrapper](a => gen.to(a).head)

  private def anyValDecoder[Wrapper <: AnyVal, Wrapped](implicit gen: Generic.Aux[Wrapper, Wrapped :: HNil],
                                                         wrappedDecoder: Decoder[Wrapped]): Decoder[Wrapper] =
    wrappedDecoder.map { x =>
      gen.from(x :: HNil)
    }

  @deprecated("use explicit codecs generation with io.circe.generic.semiauto instead", "0.0.1")
  implicit def anyValStringEncoder[Wrapper <: AnyVal]
  (implicit gen: Generic.Aux[Wrapper, String :: HNil]): Encoder[Wrapper] = anyValEncoder[Wrapper, String]

  @deprecated("use explicit codecs generation with io.circe.generic.semiauto instead", "0.0.1")
  implicit def anyValStringDecoder[Wrapper <: AnyVal]
  (implicit gen: Generic.Aux[Wrapper, String :: HNil]): Decoder[Wrapper] = anyValDecoder[Wrapper, String]
}

@deprecated("use explicit codecs generation with io.circe.generic.semiauto instead", "0.0.1")
object CirceAnyVal extends CirceAnyVal
