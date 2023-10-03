package com.advancedtelematic.libats.anorm.db

import anorm.{SqlRequestError, ToStatement}
import cats.syntax.either._
import io.circe.Json
import io.circe.syntax._

import java.sql.PreparedStatement
import scala.reflect.ClassTag

object AnormMysqlCirce {
  import io.circe.parser.decode
  import io.circe.{Decoder, Encoder}

  def circeToStatement[T : Encoder : Decoder : ClassTag] = new ToStatement[T] {
    override def set(s: PreparedStatement, index: Int, v: T): Unit =
      s.setString(index, v.asJson.noSpaces)
  }

  def circeColumn[T : Encoder : Decoder : ClassTag] = anorm.Column.columnToString.mapResult { str =>
    decode[T](str).leftMap { err => SqlRequestError(err) }
  }

  implicit val jsonToStatement: anorm.ToStatement[io.circe.Json] = circeToStatement[Json]

  implicit val jsonColumn: anorm.Column[io.circe.Json] = circeColumn[Json]
}
