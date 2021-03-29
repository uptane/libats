name := "libats-auth"

libraryDependencies ++= {
  val JsonWebSecurityV = "0.4.5-11-ga01793d"

  Seq(
    "com.advancedtelematic" %% "jw-security-core" % JsonWebSecurityV,
    "com.advancedtelematic" %% "jw-security-jca" % JsonWebSecurityV,
    "com.advancedtelematic" %% "jw-security-akka-http" % JsonWebSecurityV
  )
}
