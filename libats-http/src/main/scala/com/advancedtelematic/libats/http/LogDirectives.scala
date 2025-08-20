/**
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 * License: MPL-2.0
 */
package com.advancedtelematic.libats.http

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.Logging
import org.apache.pekko.event.Logging.LogLevel
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.server.{Directive0, Directives}
import org.apache.pekko.stream.Materializer
import ch.qos.logback.classic.LoggerContext
import com.advancedtelematic.libats.http.logging.RequestLoggingActor
import org.slf4j.LoggerFactory

import scala.util.Try

object LogDirectives {
  import Directives.*

  type MetricsBuilder = (HttpRequest, HttpResponse) => Map[String, String]

  def logResponseMetrics(serviceName: String,
                         extraMetrics: MetricsBuilder = (_, _) => Map.empty,
                         level: LogLevel = Logging.InfoLevel)
                        (implicit system: ActorSystem): Directive0 = {

    val requestLoggingActorRef = system.actorOf(RequestLoggingActor.router(level), s"$serviceName-request-log-router")

    val ignoredPathsPreffixes = List(Path("/health"), Path("/metrics"))

    extractRequestContext.flatMap { ctx =>
      val startAt = System.currentTimeMillis()
      val namespace = ctx.request.headers.find(_.is("x-ats-namespace")).map("req_namespace" -> _.value()).toMap

      mapResponse { resp =>
        val responseTime = System.currentTimeMillis() - startAt
        val allMetrics =
          defaultMetrics(ctx.request, resp, responseTime, serviceName) ++ extraMetrics(ctx.request, resp) ++ namespace

        val level =  if(ignoredPathsPreffixes.exists(p => ctx.request.uri.path.startsWith(p)))
          Option(Logging.DebugLevel)
        else
          None

        requestLoggingActorRef ! RequestLoggingActor.LogMsg(formatResponseLog(allMetrics), allMetrics, level)

        resp
      }
    }
  }

  private def defaultMetrics(request: HttpRequest, response: HttpResponse, serviceTime: Long, serviceName: String): Map[String, String] = {
    Map(
      "http_method" -> request.method.name,
      "http_path" -> request.uri.path.toString,
      "http_query" -> s"'${request.uri.rawQueryString.getOrElse("")}'",
      "http_stime" -> serviceTime.toString,
      "http_status" -> response.status.intValue.toString,
      "http_service_name" -> serviceName
    ) ++ response.headers.find(_.name() == "X-B3-TraceId").map(_.value()).map("trace_id" -> _)
  }

  private lazy val usingJsonAppender = {
    import scala.jdk.CollectionConverters.*
    val loggers = Try(LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]).toOption.toList.flatMap(_.getLoggerList.asScala)
    loggers.exists(_.iteratorForAppenders().asScala.exists(_.getName.contains("json")))
  }

  private def formatResponseLog(metrics: Map[String, String]): String = {
    if (usingJsonAppender)
      "http request" // `metrics` will be logged in json mdc context, see com.advancedtelematic.libats.logging.JsonEncoder
    else
      metrics.toList.map { case (m, v) => s"$m=$v"}.mkString(" ")
  }
}
