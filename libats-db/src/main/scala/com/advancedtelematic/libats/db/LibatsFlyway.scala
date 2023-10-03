package com.advancedtelematic.libats.db

import com.typesafe.config.Config
import org.flywaydb.core.Flyway
import scala.collection.JavaConverters._

protected [libats] object LibatsFlyway {
  def apply(dbConfig: Config): Flyway = {
    val url =
      if (dbConfig.hasPath("url"))
        dbConfig.getString("url")
      else
        dbConfig.getString("jdbcurl")

    val user = if(dbConfig.hasPath("properties.user"))
      dbConfig.getString("properties.user")
    else
      dbConfig.getString("user")

    val password = if(dbConfig.hasPath("properties.password"))
      dbConfig.getString("properties.password")
    else
      dbConfig.getString("password")

    val flywayConfig = Flyway.configure().dataSource(url, user, password)

    if (dbConfig.hasPath("flyway.locations")) {
      val locations = dbConfig.getStringList("flyway.locations").asScala.toList
      flywayConfig.locations(locations: _*)
    }

    if (dbConfig.hasPath("flyway.schema-table")) {
      flywayConfig.table(dbConfig.getString("flyway.schema-table"))
    }

    if (dbConfig.hasPath("flyway.clean-disabled")) {
      flywayConfig.cleanDisabled(dbConfig.getBoolean("flyway.clean-disabled"))
    }

    if (dbConfig.hasPath("flyway.schema")) {
      flywayConfig.schemas(dbConfig.getString("flyway.schema"))
    } else if (dbConfig.hasPath("database")) {
      flywayConfig.schemas(dbConfig.getString("database"))
    } else if (dbConfig.hasPath("catalog")) {
      flywayConfig.schemas(dbConfig.getString("catalog"))
    }

    flywayConfig.load()
  }
}
