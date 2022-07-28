package com.advancedtelematic.publish_akka


import akka.http.scaladsl.server.{Directive0, Directive1}
import com.advancedtelematic.libats.http.NamespaceDirectives.defaultNamespaceExtractor
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematics.publish_akka.Message._

import scala.concurrent.ExecutionContext.Implicits.global

class AkkaHttpRequestPublisher(val msgBusPublisher: MessageBusPublisher) {
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server.Directive

  val publishRequest: Directive0 = (defaultNamespaceExtractor & extractRequestContext).tflatMap{ tuple =>
    val ns = tuple._1
    val ctx = tuple._2
    mapResponse { resp =>
      // fire and forget
      msgBusPublisher.publishSafe(HttpRequestDescription(ctx.request.method.toString(),
        ctx.request.uri.toString(),
        resp.status.intValue(),
        ns,
        System.currentTimeMillis())
      )
      resp
    }
  }
}

object AkkaHttpRequestPublisher {
  def apply(msgBusPublisher: MessageBusPublisher) = new AkkaHttpRequestPublisher((msgBusPublisher))
}