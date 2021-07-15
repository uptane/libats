name := "libats-metrics-prometheus"

libraryDependencies ++= {
  Seq(
    "io.prometheus" % "simpleclient_common" % "0.11.0",
    "io.prometheus" % "simpleclient_dropwizard" % "0.11.0"
  )
}
