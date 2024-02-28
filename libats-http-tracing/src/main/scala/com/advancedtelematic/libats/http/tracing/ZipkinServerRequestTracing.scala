package com.advancedtelematic.libats.http.tracing

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.server.Directive1
import brave.http.{HttpServerHandler, HttpServerRequest, HttpServerResponse, HttpTracing}
import brave.{Span, Tracing as BraveTracing}
import com.advancedtelematic.libats.http.tracing.Tracing.{AkkaHttpClientTracing, ServerRequestTracing, Tracing}
import zipkin2.reporter.BytesMessageSender
import zipkin2.reporter.brave.AsyncZipkinSpanHandler
import zipkin2.reporter.okhttp3.OkHttpSender

import scala.collection.concurrent.TrieMap


class ZipkinTracing(httpTracing: HttpTracing) extends Tracing {
  import akka.http.scaladsl.server.Directives.*

  private class ZipkinTracedRequest(val request: HttpRequest) extends HttpServerRequest {
    def method(): String =
      request.method.value

    def url(): String =
      request.uri.toString()

    def path(): String =
      request.uri.path.toString()

    def header(name: String): String =
      request.headers.find(_.name().equalsIgnoreCase(name)).map(_.value()).orNull

    override def unwrap(): AnyRef = request
  }

  private class ZipkinTracedResponse(val response: HttpResponse) extends HttpServerResponse {
    override def unwrap(): AnyRef = request

    override def statusCode(): Int = response.status.intValue()
  }

  private def createAkkaHandler(tracing: HttpTracing)  =
    HttpServerHandler.create(tracing)

  //noinspection SimplifyBoolean
  private def traceRequest(req: HttpRequest): Boolean = {
    List("/health", "/metrics").forall { p =>
      req.uri.path.startsWith(Uri.Path(p)) == false
    }
  }

  private def filterHeader(name: String, value: String): Option[(String, String)] = {
    if (name.toLowerCase == "authorization")
      Option(name -> "<removed>")
    else if (name.toLowerCase == "timeout-access")
      None
    else
      Option(name -> value)
  }

  private lazy val handler = createAkkaHandler(httpTracing)

  override def traceRequests: Directive1[ServerRequestTracing] = extractRequest.flatMap {
    case req if traceRequest(req) =>
      val span = handler.handleReceive(new ZipkinTracedRequest(req))

      req.headers
        .flatMap(h => filterHeader(h.name(), h.value()))
        .foreach { case (name, value) =>
          span.tag(name, value)
        }

      mapResponse { resp =>
        val headers = TrieMap.empty[String, String]

        httpTracing.tracing().propagation
          .injector((_: HttpResponse, key: String, value: String) => headers.put(key, value))
          .inject(span.context(), resp)

        val respWithHeaders = headers.foldLeft(resp) { case (acc, (k, v)) =>
          acc.addHeader(RawHeader(k, v))
        }

        handler.handleSend(new ZipkinTracedResponse(respWithHeaders), span)

        respWithHeaders
      }.tflatMap(_ => provide(new ZipkinServerRequestTracing(httpTracing, span)))
    case _ =>
      provide(new NullServerRequestTracing)
  }

  override def shutdown(): Unit = {
    httpTracing.tracing().close()
  }
}

object ZipkinServerRequestTracing {
  def apply(uri: Uri, serviceName: String): ZipkinTracing = {
    val sender = OkHttpSender.create(uri.toString() + "/api/v2/spans")
    val handler = AsyncZipkinSpanHandler.create(sender: BytesMessageSender)

    val tracing = BraveTracing.newBuilder
      .localServiceName(serviceName)
      .addSpanHandler(handler).build

    val httpTracing = HttpTracing.newBuilder(tracing).build()

    new ZipkinTracing(httpTracing)
  }
}

class ZipkinServerRequestTracing(httpTracing: HttpTracing, requestSpan: Span) extends ServerRequestTracing {
  override def newChild: ZipkinServerRequestTracing = {
    val child = httpTracing.tracing().tracer().newChild(requestSpan.context()).start()
    new ZipkinServerRequestTracing(httpTracing, child)
  }

  override def finishSpan(): Unit = requestSpan.finish()

  override def httpClientTracing(remoteServiceName: String): AkkaHttpClientTracing = {
    new ZipkinAkkaHttpClientTracing(httpTracing, requestSpan, remoteServiceName)
  }

  override def traceId: Long = requestSpan.context().traceId()

  override def traceIdString: String = requestSpan.context().traceIdString()
}
