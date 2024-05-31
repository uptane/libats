name := "libats-db"

libraryDependencies ++= {
  Seq(
    "com.typesafe" % "config" % "1.4.3",
    "eu.0io" %% "anorm-async" % "0.0.3-SNAPSHOT" % Test exclude("org.slf4j", "slf4j-api"),
    "org.mariadb.jdbc" % "mariadb-java-client" % "3.4.0" % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.13" % Provided
  )
}

fork := true
