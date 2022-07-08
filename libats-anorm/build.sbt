name := "libats-anorm"

libraryDependencies ++= {
  Seq(
    "eu.0io" %% "anorm-async" % "0.0.3-SNAPSHOT" exclude("org.slf4j", "slf4j-api"),
    "org.mariadb.jdbc" % "mariadb-java-client" % "2.7.6" % Test
  )
}
