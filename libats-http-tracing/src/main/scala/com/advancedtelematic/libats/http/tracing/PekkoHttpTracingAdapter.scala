package com.advancedtelematic.libats.http.tracing

import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}

protected [tracing] trait PekkoHttpTracingAdapter {
  def method(request: HttpRequest): String =
    request.method.value

  def url(request: HttpRequest): String =
    request.uri.toString()

  def requestHeader(request: HttpRequest, name: String): String =
    request.headers.find(_.name() == name).map(_.value()).orNull

  def statusCode(response: HttpResponse): Integer =
    response.status.intValue()
}
