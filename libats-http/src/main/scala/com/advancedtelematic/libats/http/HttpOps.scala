package com.advancedtelematic.libats.http

import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import com.advancedtelematic.libats.data.DataType.Namespace

object HttpOps {

  implicit class HttpRequestOps(request: HttpRequest) {
    def withNs(ns: Namespace): HttpRequest =
      request.withHeaders(RawHeader("x-ats-namespace", ns.get))
  }

}
