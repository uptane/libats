package com.advancedtelematic.publish_pekko


import org.apache.pekko.http.scaladsl.server.{Directive0, Directive1}
import com.advancedtelematic.libats.http.NamespaceDirectives.defaultNamespaceExtractor
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematics.publish_pekko.Message._

import scala.concurrent.ExecutionContext.Implicits.global

class PekkoHttpRequestPublisher(val msgBusPublisher: MessageBusPublisher) {
  import org.apache.pekko.http.scaladsl.server.Directives._
  import org.apache.pekko.http.scaladsl.server.Directive

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

object PekkoHttpRequestPublisher {
  def apply(msgBusPublisher: MessageBusPublisher) = new PekkoHttpRequestPublisher((msgBusPublisher))
}