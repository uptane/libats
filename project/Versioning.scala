import com.github.sbt.git.GitVersioning
import com.github.sbt.git.SbtGit._
import com.github.sbt.git.ConsoleGitRunner

object Versioning {
  lazy val settings = Seq(
    git.runner := ConsoleGitRunner,
    git.useGitDescribe := true,
    git.baseVersion := "0.0.1"
  )

  val Plugin = GitVersioning
}
