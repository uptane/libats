/**
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 * License: MPL-2.0
 */
package com.advancedtelematic.libats.http

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.server.{Directive0, Directives}
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

import scala.util.Try

object LogDirectives {
  import Directives.*

  type MetricsBuilder = (HttpRequest, HttpResponse) => Map[String, Any]

  private lazy val logger = LoggerFactory.getLogger("com.advancedtelematic.libats.http.LogDirectives")

  def logResponseMetrics(serviceName: String,
                         extraMetrics: MetricsBuilder = (_, _) => Map.empty,
                         level: Level = Level.INFO)
                        (implicit system: ActorSystem): Directive0 = {

    val ignoredPathsPreffixes = List(Path("/health"), Path("/metrics"))

    extractRequestContext.flatMap { ctx =>
      val startAt = System.currentTimeMillis()
      val namespace = ctx.request.headers.find(_.is("x-ats-namespace")).map("req_namespace" -> _.value()).toMap

      mapResponse { resp =>
        val responseTime = System.currentTimeMillis() - startAt
        val allMetrics =
          defaultMetrics(ctx.request, resp, responseTime, serviceName) ++ extraMetrics(ctx.request, resp) ++ namespace

        val msgLevel = if (ignoredPathsPreffixes.exists(p => ctx.request.uri.path.startsWith(p)))
          Level.DEBUG
        else
          level

        val builder = allMetrics.foldLeft(logger.atLevel(msgLevel)) {
          case (b, (key, value)) => b.addKeyValue(key, value)
        }
        builder.log(formatResponseLog(allMetrics))

        resp
      }
    }
  }

  private def defaultMetrics(request: HttpRequest, response: HttpResponse, serviceTime: Long, serviceName: String): Map[String, Any] = {
    Map(
      "http_method" -> request.method.name,
      "http_path" -> request.uri.path.toString,
      "http_query" -> s"'${request.uri.rawQueryString.getOrElse("")}'",
      "http_stime" -> serviceTime,
      "http_status" -> response.status.intValue,
      "http_service_name" -> serviceName
    ) ++ response.headers.find(_.name() == "X-B3-TraceId").map(_.value()).map("trace_id" -> _)
  }

  private lazy val usingJsonAppender = {
    import scala.jdk.CollectionConverters.*
    val loggers = Try(LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]).toOption.toList.flatMap(_.getLoggerList.asScala)
    loggers.exists(_.iteratorForAppenders().asScala.exists(_.getName.contains("json")))
  }

  private def formatResponseLog(metrics: Map[String, Any]): String = {
    if (usingJsonAppender)
      "http request" // `metrics` will be logged in json mdc context, see com.advancedtelematic.libats.logging.JsonEncoder
    else
      metrics.toList.map { case (m, v) => s"$m=$v"}.mkString(" ")
  }
}
