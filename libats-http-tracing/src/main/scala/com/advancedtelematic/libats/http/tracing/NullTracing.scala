package com.advancedtelematic.libats.http.tracing

import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.server.{Directive1, Directives}
import com.advancedtelematic.libats.http.tracing.Tracing.{PekkoHttpClientTracing, ServerRequestTracing, Tracing}

import scala.concurrent.{ExecutionContext, Future}


class NullServerRequestTracing extends ServerRequestTracing  {
  override def newChild: ServerRequestTracing = this

  override def finishSpan(): Unit = ()

  override def httpClientTracing(remoteServiceName: String): PekkoHttpClientTracing = new NullHttpClientTracing

  override def traceId: Long = 0L

  override def traceIdString: String = "0"
}

class NullTracing extends Tracing {
  override def traceRequests: Directive1[ServerRequestTracing] = Directives.provide(new NullServerRequestTracing)

  override def shutdown(): Unit = ()
}

class NullHttpClientTracing extends PekkoHttpClientTracing {
  override def trace(fn: HttpRequest => Future[HttpResponse])(implicit ec: ExecutionContext): HttpRequest => Future[HttpResponse] = fn
}
