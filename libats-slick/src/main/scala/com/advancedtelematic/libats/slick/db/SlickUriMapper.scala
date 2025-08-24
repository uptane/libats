package com.advancedtelematic.libats.slick.db

import org.apache.pekko.http.scaladsl.model.Uri
import slick.jdbc.MySQLProfile.api._

object SlickUriMapper {
  implicit val uriMapper: slick.jdbc.MySQLProfile.BaseColumnType[Uri] = MappedColumnType.base[Uri, String](
    _.toString,
    Uri.apply
  )
}


