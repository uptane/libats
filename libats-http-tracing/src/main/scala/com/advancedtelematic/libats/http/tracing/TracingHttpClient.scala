package com.advancedtelematic.libats.http.tracing

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.stream.Materializer
import com.advancedtelematic.libats.http.ServiceHttpClient
import com.advancedtelematic.libats.http.tracing.Tracing.ServerRequestTracing

import scala.concurrent.Future

abstract class TracingHttpClient(_httpClient: HttpRequest => Future[HttpResponse], remoteServiceName: String)
                                (implicit system: ActorSystem, serverTracing: ServerRequestTracing) extends ServiceHttpClient(_httpClient) {

  import system.dispatcher

  override def httpClient: HttpRequest => Future[HttpResponse] =
    serverTracing.httpClientTracing(remoteServiceName).trace(super.httpClient)
}
