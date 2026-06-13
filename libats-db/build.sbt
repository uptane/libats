name := "libats-db"

libraryDependencies ++= {
  Seq(
    "com.typesafe" % "config" % "1.4.9",
    "org.mariadb.jdbc" % "mariadb-java-client" % "3.5.9" % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.13" % Provided
  )
}

fork := true
