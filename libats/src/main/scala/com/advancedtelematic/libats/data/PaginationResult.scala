package com.advancedtelematic.libats.data

import cats.{Applicative, Eval, Functor, Traverse}
import cats.syntax.traverse.*
import cats.syntax.foldable.*
import cats.syntax.functor.*

import com.advancedtelematic.libats.data.PaginationResult.{Limit, Offset}
import io.circe.Codec

final case class PaginationResult[A](values: Seq[A],
                                     total: Long,
                                     offset: Offset,
                                     limit: Limit) {
  def map[B](f: A => B): PaginationResult[B] = this.copy(values = values.map(f))
}

object PaginationResult {
  import io.circe.{Encoder, Decoder}
  import io.circe.generic.semiauto._

  final case class Offset(value: Long)

  final case class Limit(value: Long)

  import scala.language.implicitConversions

  implicit val offsetCodec: Codec[Offset] = Codec.from(
    Decoder.decodeLong.map(Offset.apply),
    Encoder.encodeLong.contramap[Offset](_.value),
  )
  implicit val limitCodec: Codec[Limit] = Codec.from(
    Decoder.decodeLong.map(Limit.apply),
    Encoder.encodeLong.contramap[Limit](_.value),
  )

  implicit def offsetAsLong(offset: Offset): Long = offset.value

  implicit def limitAsLong(limit: Limit): Long = limit.value

  implicit class LongAsParam(value: Long) {
    def toOffset: Offset = Offset(value)

    def toLimit: Limit = Limit(value)
  }

  implicit def paginationResultEncoder[T: Encoder]
    : Encoder[PaginationResult[T]] = deriveEncoder
  implicit def paginationResultDecoder[T: Decoder]
    : Decoder[PaginationResult[T]] = deriveDecoder

  implicit val functor: Functor[PaginationResult] =
    new Functor[PaginationResult] {
      override def map[A, B](fa: PaginationResult[A])(
          f: A => B): PaginationResult[B] =
        fa.map(f)
    }

  implicit val traverse: Traverse[PaginationResult] =
    new Traverse[PaginationResult] {
      override def traverse[G[_]: Applicative, A, B](fa: PaginationResult[A])(
          f: A => G[B]): G[PaginationResult[B]] = {
        import cats.syntax.traverse.*
        fa.values
          .traverse(f)
          .map(transformed =>
            PaginationResult(transformed, fa.total, fa.offset, fa.limit))
      }

      override def foldLeft[A, B](fa: PaginationResult[A], b: B)(
          f: (B, A) => B): B =
        fa.values.foldl(b)(f)

      override def foldRight[A, B](fa: PaginationResult[A], lb: Eval[B])(
          f: (A, Eval[B]) => Eval[B]): Eval[B] =
        fa.values.foldr(lb)(f)
    }
}
