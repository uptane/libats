val Library = new {
  object Version {
    val akka = "2.5.23"
    val akkaHttp = "10.1.8"
    val akkaHttpCirce = "1.27.0"
    val circe = "0.11.1"
    val refined = "0.8.7"
    val scalaTest = "3.0.5"
    val metricsV = "3.2.5"
    val cats = "1.5.0"
  }

  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Version.akka

  val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.akka

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
    "io.circe" %% "circe-java8"
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
    "org.typelevel" %% "cats-kernel" % Version.cats,
    "org.typelevel" %% "cats-macros" % Version.cats
  )

  val brave = Seq(
    "io.zipkin.brave" % "brave" % "5.6.8",
    "io.zipkin.brave" % "brave-instrumentation-http" % "5.6.8",
    "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.10.0"
  )
}

onLoad in Global := { s => "dependencyUpdates" :: s }

lazy val commonDeps =
  libraryDependencies ++= Library.circe ++ Seq(Library.refined, Library.scalatest) ++ Library.cats :+ Library.logback

lazy val commonConfigs = Seq.empty

lazy val commonSettings = Seq(
  organization := "com.advancedtelematic",
  licenses += ("MPL-2.0", url("http://mozilla.org/MPL/2.0/")),
  scalaVersion := "2.12.7",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature", "-Ypartial-unification", "-Xexperimental"),
  resolvers += "ATS Releases" at "http://nexus.advancedtelematic.com:8081/content/repositories/releases",
  resolvers += "ATS Snapshots" at "http://nexus.advancedtelematic.com:8081/content/repositories/snapshots",
  resolvers += "Central" at "http://nexus.advancedtelematic.com:8081/content/repositories/central",
  resolvers += "version99 Empty loggers" at "http://version99.qos.ch",
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
  .settings(libraryDependencies ++= Library.akkaHttpTestKit)
  .settings(Publish.settings)
  .dependsOn(libats)
  .dependsOn(libats_metrics)

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

lazy val libats_slick = (project in file("libats-slick"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonDeps)
  .settings(commonSettings)
  .settings(Publish.settings)
  .settings(libraryDependencies ++= Library.jvmMetrics)
  .settings(libraryDependencies ++= Library.akkaHttpTestKit)
  .dependsOn(libats)
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
  .settings(libraryDependencies ++= Library.akkaHttpTestKit)
  .dependsOn(libats)
  .dependsOn(libats_http)
  .dependsOn(libats_messaging_datatype)

lazy val libats_metrics = (project in file("libats-metrics"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(libraryDependencies ++= Library.akkaHttp)
  .settings(libraryDependencies ++= Library.circe :+ Library.akkaStream)
  .settings(libraryDependencies ++= Library.jvmMetrics)
  .settings(Publish.settings)

lazy val libats_metrics_kafka = (project in file("libats-metrics-kafka"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(Publish.settings)
  .dependsOn(libats_metrics)

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
  .dependsOn(libats_metrics)
  .dependsOn(libats_http)

lazy val libats_metrics_finagle = (project in file("libats-metrics-finagle"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(libraryDependencies ++= Library.circe :+ Library.akkaStream)
  .settings(Publish.settings).dependsOn(libats_metrics)

lazy val libats_auth = (project in file("libats-auth"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(commonDeps)
  .settings(Publish.settings)
  .settings(libraryDependencies += Library.akkaSlf4j)
  .dependsOn(libats)

lazy val libats_logging = (project in file("libats-logging"))
  .enablePlugins(BuildInfoPlugin, Versioning.Plugin)
  .configs(commonConfigs: _*)
  .settings(commonSettings)
  .settings(libraryDependencies ++= Library.circe :+ Library.logback)
  .settings(name := "libats-logging")
  .settings(Publish.settings)

lazy val libats_root = (project in file("."))
  .enablePlugins(DependencyGraph)
  .settings(Publish.disable)
  .settings(scalaVersion := "2.12.7")
  .aggregate(libats, libats_http, libats_http_tracing, libats_messaging, libats_messaging_datatype,
    libats_slick, libats_auth, libats_metrics, libats_metrics_kafka, libats_metrics_akka,
    libats_metrics_finagle, libats_metrics_prometheus, libats_logging)
