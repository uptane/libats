package com.advancedtelematic.libats.anorm.db

import anorm.{Column, ToStatement}
import com.advancedtelematic.libats.data.UUIDKey.UUIDKey
import shapeless._

import java.sql.PreparedStatement
import java.util.UUID
import scala.reflect.ClassTag

object AnormUUIDKey {

  implicit def uuidToStatement[U <: UUIDKey](): ToStatement[U] = new ToStatement[U] {
    override def set(s: PreparedStatement, index: Int, v: U): Unit =
      s.setString(0, v.uuid.toString)
  }

  implicit def uuidColumn[U <: UUIDKey](implicit gen: Generic.Aux[U, UUID :: HNil], ct: ClassTag[U]): Column[U] =
    anorm.Column.columnToString.map { str =>
      gen.from(UUID.fromString(str) :: HNil)
    }
}
