name := "libats-slick"

libraryDependencies ++= {
  val slickV = "3.4.1"
  val flywayV = "8.2.3"
  val scalaTestV = "3.2.16"

  Seq(
    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,
    "org.flywaydb" % "flyway-core" % flywayV,
    "org.flywaydb" % "flyway-mysql" % flywayV,

    "org.scalatest"     %% "scalatest" % scalaTestV % Provided,

    "org.mariadb.jdbc" % "mariadb-java-client" % "3.1.4" % Test,

    "org.bouncycastle" % "bcprov-jdk18on" % "1.76" % Provided,
    "org.bouncycastle" % "bcpkix-jdk18on" % "1.76" % Provided,

    "com.beachape" %% "enumeratum" % "1.7.3" % Provided,
  )
}
