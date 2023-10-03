/*
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 *  License: MPL-2.0
 */

package com.advancedtelematic.libats.http

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

trait BootApp {
  implicit val system: ActorSystem
  val globalConfig: Config
}

trait BootAppDefaultConfig {
  val projectName: String

  implicit val system: ActorSystem = ActorSystem(projectName)
  implicit lazy val exec: scala.concurrent.ExecutionContextExecutor = system.dispatcher
  lazy val log = LoggerFactory.getLogger(this.getClass)

  lazy val globalConfig = ConfigFactory.load()
}

trait BootAppDatabaseConfig {
  self: BootAppDefaultConfig =>

  lazy val dbConfig = globalConfig.getConfig("ats." + projectName + ".database")
}
