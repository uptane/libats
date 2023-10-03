package com.advancedtelematic.libats.slick.db

import akka.http.scaladsl.model.Uri
import slick.jdbc.MySQLProfile.api._

object SlickUriMapper {
  implicit val uriMapper: slick.jdbc.MySQLProfile.BaseColumnType[akka.http.scaladsl.model.Uri] = MappedColumnType.base[Uri, String](
    _.toString,
    Uri.apply
  )
}


