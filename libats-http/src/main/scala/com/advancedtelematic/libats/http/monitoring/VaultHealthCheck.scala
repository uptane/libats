package com.advancedtelematic.libats.http.monitoring

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import com.advancedtelematic.libats.http.monitoring.HealthCheckHttpClient.HealthCheckError
import com.advancedtelematic.metrics.HealthCheck
import com.advancedtelematic.metrics.HealthCheck.{Down, HealthCheckResult, Up}
import io.circe.Json
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}


class VaultHealthCheck(address: Uri, token: String)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
  extends HealthCheckHttpClient with HealthCheck {
  import cats.syntax.either._

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  override def apply()(implicit ec: ExecutionContext): Future[HealthCheckResult] = {
    val checkF = for {
      _ <- sysHealth()
      _ <- tokenLookup()
    } yield Up

    checkF.recover {
      case ex =>
        log.error("Vault is down", ex)
        Down(ex)
    }
  }

  private def sysHealth(): Future[Json] = {
    val req = HttpRequest(HttpMethods.GET, address.withPath(Path("/v1") / "sys" / "health"))

    execute(req).flatMap { json =>
      val vaultSysE = for {
        isInitialized <- json.hcursor.downField("initialized").as[Boolean].leftMap(_.message)
        isSealed <- json.hcursor.downField("sealed").as[Boolean].leftMap(_.message)
        json <- {
          if(isSealed)
            Left("vault is sealed")
          else if(!isInitialized)
            Left("vault is not initialized")
          else
            Right(json)
        }
      } yield json

      Future.fromTry(vaultSysE.leftMap(HealthCheckError.apply).toTry)
    }
  }

  private def tokenLookup(): Future[Json] = {
    val req = HttpRequest(HttpMethods.GET, address.withPath(Path("/v1") / "auth" / "token" / "lookup-self"))
      .addHeader(RawHeader("X-Vault-Token", token))
    execute(req)
  }

  override def name: String = s"vault/${address.authority.host}"
}
