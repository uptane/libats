
val Library = new {
  object Version {
    val akka = "2.8.5"
    val akkaHttp = "10.5.2"
    val akkaHttpCirce = "1.39.2"
    val circe = "0.14.6"
    val refined = "0.11.0"
    val scalaTest = "3.2.12"
    val metricsV = "4.2.23"
    val cats = "2.10.0"
    val logback = "1.4.14"
    val flyway = "10.5.0"
  }

  val logback = "ch.qos.logback" % "logback-classic" % Version.logback

  val flyway = "org.flywaydb" % "flyway-core" % Version.flyway

  val flywayMysql = "org.flywaydb" % "flyway-mysql" % Version.flyway

  val Prometheus = Seq(
    "io.prometheus" % "simpleclient_common" % "0.16.0",
    "io.prometheus" % "simpleclient_dropwizard" % "0.16.0"
  )

  val Akka = Set(
    "com.typesafe.akka" %% "akka-slf4j",
    "com.typesafe.akka" %% "akka-actor",
    "com.typesafe.akka" %% "akka-stream"
  ).map(_ % Version.akka)

  val akkaHttp = Seq(
      "com.typesafe.akka" %% "akka-http" % Version.akkaHttp,
      "de.heikoseeberger" %% "akka-http-circe" % Version.akkaHttpCirce
    ) ++ Akka

  val akkaHttpTestKit = Seq(
    "com.typesafe.akka" %% "akka-http-testkit" % Version.akkaHttp,
    "com.typesafe.akka" %% "akka-stream-testkit" % Version.akka
  ).map(_ % Test)

  val circe = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
  ).map(_ % Version.circe)

  val refined = "eu.timepit" %% "refined" % Version.refined

  val scalatest = "org.scalatest" %% "scalatest" % Version.scalaTest % "test,provided"

  val jvmMetrics =  Seq(
    "io.dropwizard.metrics" % "metrics-core" % Version.metricsV,
    "io.dropwizard.metrics" % "metrics-jvm" % Version.metricsV,
    "io.dropwizard.metrics" % "metrics-logback" % Version.metricsV
  )

  val cats = Seq(
    "org.typelevel" %% "cats-core" % Version.cats,
    "org.typelevel" %% "cats-kernel" % Version.cats
  )

  val brave = Seq(
    "io.zipkin.brave" % "brave" % "5.18.1",
    "io.zipkin.brave" % "brave-instrumentation-http" % "5.18.1",
    "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "3.2.1"
  )
}

lazy val scala213 = "2.13.12"
lazy val supportedScalaVersions = List(scala213)
lazy val commonDeps =
  libraryDependencies ++= Library.circe ++ Seq(Library.refined, Library.scalatest) ++ Library.cats :+ Library.logback

lazy val commonConfigs = Seq.empty

lazy val commonSettings = Seq(
  organization := "io.github.uptane",
  organizationName := "uptane",
  organizationHomepage := Some(url("https://uptane.github.io/")),
  licenses += ("MPL-2.0", url("http://mozilla.org/MPL/2.0/")),
  description := "Common  library for uptane scala projects",
  crossScalaVersions := supportedScalaVersions,
  scalaVersion := scala213,
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature", "-Xsource:3"),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n <= 12 => Seq("-Ypartial-unification", "-Xexperimental")
      case _ => Seq.empty
    }
  },
  resolvers += "sonatype-snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
  resolvers += "sonatype-releases" at "https://s01.oss.sonatype.org/content/repositories/releases",
  buildInfoOptions += BuildInfoOption.ToMap,
  buildInfoOptions += BuildInfoOption.BuildTime) ++ Versioning.settings

lazy val libats = (project in file("libats"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonDeps)
  .settings(commonSettings)
  .settings(Publish.settings)

lazy val libats_http = (project in file("libats-http"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonDeps)
  .settings(commonSettings)
  .settings(libraryDependencies ++= Library.akkaHttp)
  .settings(libraryDependencies ++= Library.jvmMetrics)
  .settings(libraryDependencies ++= Library.circe)
  .settings(libraryDependencies ++= Library.akkaHttpTestKit)
  .settings(Publish.settings)
  .dependsOn(libats)
  .dependsOn(libats_metrics)
  .dependsOn(libats_db)

lazy val libats_http_tracing = (project in file("libats-http-tracing"))
  .settings(name := "libats-http-tracing")
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonDeps)
  .settings(commonSettings)
  .dependsOn(libats_http)
  .settings(libraryDependencies ++= Library.brave)
  .settings(Publish.settings)
  .dependsOn(libats)

lazy val libats_db = (project in file("libats-db"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonDeps)
  .settings(commonSettings)
  .settings(libraryDependencies += Library.flyway)
  .settings(libraryDependencies += Library.flywayMysql % Test)
  .settings(Publish.settings)

lazy val libats_slick = (project in file("libats-slick"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonDeps)
  .settings(commonSettings)
  .settings(Publish.settings)
  .settings(libraryDependencies ++= Library.jvmMetrics)
  .settings(libraryDependencies ++= Library.akkaHttpTestKit)
  .settings(libraryDependencies += Library.flyway)
  .settings(libraryDependencies += Library.flywayMysql)
  .dependsOn(libats)
  .dependsOn(libats_db)
  .dependsOn(libats_http)

lazy val libats_anorm = (project in file("libats-anorm"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonDeps)
  .settings(commonSettings)
  .settings(Publish.settings)
  .settings(libraryDependencies += Library.logback)
  .settings(libraryDependencies += Library.flywayMysql % Test)
  .dependsOn(libats)
  .dependsOn(libats_db)
  .dependsOn(libats_metrics)

lazy val libats_messaging_datatype = (project in file("libats-messaging-datatype"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(commonDeps)
  .settings(Publish.settings)
  .dependsOn(libats)

lazy val libats_messaging = (project in file("libats-messaging"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonDeps)
  .settings(commonSettings)
  .settings(Publish.settings)
  .settings(libraryDependencies ++= Library.akkaHttpTestKit)
  .dependsOn(libats)
  .dependsOn(libats_metrics)
  .dependsOn(libats_http)
  .dependsOn(libats_messaging_datatype)
  .settings(libraryDependencies ++= Library.Prometheus)

lazy val libats_metrics = (project in file("libats-metrics"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(libraryDependencies ++= Library.circe)
  .settings(libraryDependencies ++= Library.jvmMetrics)
  .settings(libraryDependencies += Library.logback)
  .settings(Publish.settings)

lazy val libats_metrics_akka = (project in file("libats-metrics-akka"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(Publish.settings)
  .dependsOn(libats_metrics)
  .dependsOn(libats_http)

lazy val libats_metrics_prometheus = (project in file("libats-metrics-prometheus"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(Publish.settings)
  .settings(libraryDependencies ++= Library.Prometheus)
  .dependsOn(libats_metrics)
  .dependsOn(libats_http)

lazy val libats_logging = (project in file("libats-logging"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(libraryDependencies ++= Library.circe :+ Library.logback)
  .settings(name := "libats-logging")
  .settings(Publish.settings)

lazy val libats_publish_akka = (project in file("libats-publish-akka"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(Publish.settings)
  .dependsOn(libats_http)
  .dependsOn(libats_messaging)


lazy val libats_root = (project in file("."))
  .enablePlugins(DependencyGraph)
  .settings(Publish.disable)
  .settings(scalaVersion := scala213)
  .settings(crossScalaVersions := Nil)
  .aggregate(libats, libats_http, libats_http_tracing, libats_messaging, libats_messaging_datatype,
    libats_db, libats_anorm, libats_slick, libats_metrics, libats_metrics_akka,
    libats_metrics_prometheus, libats_logging, libats_publish_akka)

