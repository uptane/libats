package com.advancedtelematic.libats.anorm.db

import com.typesafe.config.Config
import eu._0io.anorm_async.Database

trait DatabaseSupport {
  val dbConfig: Config

  implicit lazy val db = Database.fromConfig(dbConfig)
}
