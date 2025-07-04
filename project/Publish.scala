import com.jsuereth.sbtpgp.SbtPgp.autoImport._
import sbt.Keys._
import sbt._
import xerial.sbt.Sonatype.GitHubHosting
import xerial.sbt.Sonatype.autoImport._
import xerial.sbt.Sonatype.sonatypeCentralHost

import java.net.URI

object Publish {

  private def readSettings(envKey: String, propKey: Option[String] = None): String = {
    sys.env
      .get(envKey)
      .orElse(propKey.flatMap(sys.props.get(_)))
      .getOrElse("")
  }

  lazy val repoHost = URI.create(repoUrl).getHost

  lazy val repoUser = readSettings("PUBLISH_USER")

  lazy val repoPassword = readSettings("PUBLISH_PASSWORD")

  lazy val repoUrl = readSettings("PUBLISH_URL")

  lazy val repoRealm = readSettings("PUBLISH_REALM")

    lazy val settings = Seq(
    credentials += Credentials(repoRealm, repoHost, repoUser, repoPassword),
    usePgpKeyHex("6ED5E5ABE9BF80F173343B98FFA246A21356D296"),
    isSnapshot := version.value.trim.endsWith("SNAPSHOT"),
    pomIncludeRepository := { _ => false },
    sonatypeCredentialHost := sonatypeCentralHost,
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
    publishMavenStyle := true,
    sonatypeProjectHosting := Some(GitHubHosting("uptane", "libats", "releases@uptane.github.io")),
    publishTo := {
      if (repoUrl.isEmpty) {
        sonatypePublishToBundle.value
      } else {
        if (isSnapshot.value)
          Some("snapshots" at repoUrl)
        else
          Some("releases" at repoUrl)
      }
    }
  )

  lazy val disable = Seq(
    sonatypeCredentialHost := sonatypeCentralHost,
    sonatypeProfileName := "io.github.uptane",
    publish / skip := true,
    publishArtifact := false,
    publish := (()),
    publishTo := None,
    publishLocal := (())
  )
}
