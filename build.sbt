
val Library = new {
  object Version {
    val pekko = "1.4.0"
    val pekkoHttp = "1.3.0"
    // val pekkoHttpCirce = "1.39.2"
    val circe = "0.14.14"
    val refined = "0.11.3"
    val scalaTest = "3.2.12"
    val metricsV = "4.2.37"
    val cats = "2.13.0"
    val logback = "1.5.22"
    val flyway = "11.20.0"
  }

  val javaUuidGenerator = "com.fasterxml.uuid" % "java-uuid-generator" % "5.2.0"

  val logback = "ch.qos.logback" % "logback-classic" % Version.logback

  val flyway = "org.flywaydb" % "flyway-core" % Version.flyway

  val flywayMysql = "org.flywaydb" % "flyway-mysql" % Version.flyway

  val Prometheus = Seq(
    "io.prometheus" % "simpleclient_common" % "0.16.0",
    "io.prometheus" % "simpleclient_dropwizard" % "0.16.0"
  )

  val Pekko = Set(
    "org.apache.pekko" %% "pekko-slf4j",
    "org.apache.pekko" %% "pekko-actor",
    "org.apache.pekko" %% "pekko-stream"
  ).map(_ % Version.pekko)

  val pekkoHttp = Seq(
    "org.apache.pekko" %% "pekko-http" % Version.pekkoHttp,
    "com.github.pjfanning" %% "pekko-http-circe" % "3.3.0",
    ) ++ Pekko

  val pekkoHttpTestKit = Seq(
    "org.apache.pekko" %% "pekko-http-testkit" % Version.pekkoHttp,
    "org.apache.pekko" %% "pekko-stream-testkit" % Version.pekko
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
    "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "3.5.1"
  )
}

lazy val scala213 = "2.13.18"
lazy val supportedScalaVersions = List(scala213)
lazy val commonDeps =
  libraryDependencies ++= Library.circe ++
    Seq(Library.refined, Library.scalatest) ++
    Library.cats :+ Library.logback :+ Library.javaUuidGenerator

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
  .settings(libraryDependencies ++= Library.pekkoHttp)
  .settings(libraryDependencies ++= Library.jvmMetrics)
  .settings(libraryDependencies ++= Library.circe)
  .settings(libraryDependencies ++= Library.pekkoHttpTestKit)
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
  .settings(libraryDependencies ++= Library.pekkoHttpTestKit)
  .settings(libraryDependencies += Library.flyway)
  .settings(libraryDependencies += Library.flywayMysql)
  .dependsOn(libats)
  .dependsOn(libats_db)
  .dependsOn(libats_http)

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
  .settings(libraryDependencies ++= Library.pekkoHttpTestKit)
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

lazy val libats_metrics_pekko = (project in file("libats-metrics-pekko"))
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

lazy val libats_publish_pekko = (project in file("libats-publish-pekko"))
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
    libats_db, libats_slick, libats_metrics, libats_metrics_pekko,
    libats_metrics_prometheus, libats_logging, libats_publish_pekko)

