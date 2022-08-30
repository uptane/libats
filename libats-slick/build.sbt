name := "libats-slick"

libraryDependencies ++= {
  val slickV = "3.4.0"
  val scalaTestV = "3.0.8"

  Seq(
    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,

    "org.scalatest"     %% "scalatest" % scalaTestV % Provided,

    "org.mariadb.jdbc" % "mariadb-java-client" % "3.0.7" % Test,

    "org.bouncycastle" % "bcprov-jdk15on" % "1.70" % Provided,
    "org.bouncycastle" % "bcpkix-jdk15on" % "1.70" % Provided
  )
}
