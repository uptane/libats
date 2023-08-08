/**
 * Copyright: Copyright (C) 2016, Jaguar Land Rover
 * License: MPL-2.0
 */
package com.advancedtelematic.libats.slick.codecs

import eu.timepit.refined.api.{Refined, Validate}
import slick.jdbc.MySQLProfile.api._

import cats.syntax.either._
import scala.language.higherKinds

/**
  * Map refined types to their underlying types when interacting with
  * the database.
  */
trait SlickRefined {
  implicit def refinedMappedType[T, P]
  (implicit delegate: ColumnType[T], validate: Validate[T, P]) : ColumnType[Refined[T, P]] =
    MappedColumnType.base[Refined[T, P], T](_.value, eu.timepit.refined.refineV[P](_).valueOr(err => throw new IllegalArgumentException(err)))
}

object SlickRefined extends SlickRefined
