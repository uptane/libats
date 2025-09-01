name := "libats-db"

libraryDependencies ++= {
  Seq(
    "com.typesafe" % "config" % "1.4.4",
    "eu.0io" %% "anorm-async" % "0.0.2" % Test exclude("org.slf4j", "slf4j-api"),
    "org.mariadb.jdbc" % "mariadb-java-client" % "3.5.5" % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.13" % Provided
  )
}

fork := true
