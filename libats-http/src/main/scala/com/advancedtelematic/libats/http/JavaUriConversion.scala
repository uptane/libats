package com.advancedtelematic.libats.http

import java.net.URI

import org.apache.pekko.http.scaladsl.model.Uri

import scala.language.implicitConversions

object JavaUriConversion {
  implicit def javaUriToPekkoUriConversion(value: URI): Uri = Uri(value.toString)
}
