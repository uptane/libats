package com.advancedtelematic.libats.slick.codecs

import enumeratum.EnumEntry
import slick.jdbc.MySQLProfile.api._

import scala.reflect.ClassTag

object SlickEnumeratum {
  def enumeratumMapper[T <: EnumEntry](enum: enumeratum.Enum[T])(implicit ct: ClassTag[T]) = {
    MappedColumnType.base[T, String](_.entryName, (s: String) => enum.withNameInsensitive(s))
  }
}
