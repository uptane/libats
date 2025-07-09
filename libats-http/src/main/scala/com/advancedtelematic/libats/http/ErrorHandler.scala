/*
 * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
 * License: MPL-2.0
 */

package com.advancedtelematic.libats.http

import java.util.UUID
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes.*
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes, Uri}
import akka.http.scaladsl.server.{Directives, ExceptionHandler, *}
import cats.Show
import com.advancedtelematic.libats.data.{
  ErrorCode,
  ErrorCodes,
  ErrorRepresentation
}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.syntax.*
import com.advancedtelematic.libats.codecs.CirceUuid.*
import io.circe.{DecodingFailure, Json}
import cats.syntax.option.*
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.util.control.NoStackTrace
import scala.language.existentials

object Errors {
  import Directives.*
  import ErrorRepresentation.*

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  abstract class Error(val code: ErrorCode,
                       val responseCode: StatusCode,
                       val msg: String,
                       val cause: Option[Throwable] = None,
                       val errorId: UUID = UUID.randomUUID())
      extends Exception(msg, cause.orNull)
      with NoStackTrace

  case class JsonError(code: ErrorCode,
                       responseCode: StatusCode,
                       json: Json,
                       msg: String,
                       errorId: UUID = UUID.randomUUID())
      extends Exception(msg)
      with NoStackTrace

  case class RawError(code: ErrorCode,
                      responseCode: StatusCode,
                      desc: String,
                      errorId: UUID = UUID.randomUUID())
      extends Exception(desc)
      with NoStackTrace

  case class RemoteServiceError(msg: String,
                                response: HttpResponse,
                                description: Json = Json.Null,
                                causeCode: ErrorCode =
                                  ErrorCodes.RemoteServiceError,
                                cause: Option[ErrorRepresentation] = None,
                                errorId: UUID = UUID.randomUUID())
      extends Exception(s"Remote Service Error: $msg")
      with NoStackTrace

  case class MissingEntityId[T](id: T)(implicit ct: ClassTag[T], show: Show[T])
      extends Error(
        ErrorCodes.MissingEntity,
        StatusCodes.NotFound,
        s"Missing entity: ${ct.runtimeClass.getSimpleName} ${show.show(id)}")

  case class MissingEntity[T]()(implicit ct: ClassTag[T])
      extends Error(ErrorCodes.MissingEntity,
                    StatusCodes.NotFound,
                    s"Missing entity: ${ct.runtimeClass.getSimpleName}")

  case class EntityAlreadyExists[T]()(implicit ct: ClassTag[T])
      extends Error(ErrorCodes.ConflictingEntity,
                    StatusCodes.Conflict,
                    s"Entity already exists: ${ct.runtimeClass.getSimpleName}")

  val TooManyElements = RawError(ErrorCodes.TooManyElements,
                                 StatusCodes.InternalServerError,
                                 "Too many elements found")

  implicit val rawErrorToRepr: ToErrorRepr[RawError] = e =>
    ErrorRepresentation(e.code, e.desc, None, e.errorId.some)

  implicit val decoderErrorToRepr: ToErrorRepr[DecodingFailure] = df =>
    ErrorRepresentation(ErrorCodes.InvalidEntity, df.getMessage)

  implicit val jsonErrorToRepr: ToErrorRepr[JsonError] = je =>
    ErrorRepresentation(je.code, je.msg, je.json.some, je.errorId.some)

  implicit val onRemoteServiceErrorToRepr: ToErrorRepr[RemoteServiceError] =
    rse =>
      ErrorRepresentation(rse.causeCode,
                          rse.msg,
                          rse.cause.map(_.asJson),
                          rse.errorId.some)

  implicit val onErrorToRepr: ToErrorRepr[Error] = e =>
    ErrorRepresentation(e.code,
                        e.msg,
                        e.cause.map(_.getMessage.asJson),
                        e.errorId.some)

  // Add more handlers here, or use RawError
  private val toErrorRepresentation
    : PartialFunction[Throwable, (StatusCode, ErrorRepresentation)] = {
    case e: RawError =>
      e.responseCode -> e.toErrorRepr
    case df: DecodingFailure =>
      StatusCodes.BadRequest -> df.toErrorRepr
    case je: JsonError =>
      je.responseCode -> je.toErrorRepr
    case rse: RemoteServiceError =>
      StatusCodes.BadGateway -> rse.toErrorRepr
    case e: Error =>
      e.responseCode -> e.toErrorRepr
    case err: java.sql.SQLIntegrityConstraintViolationException
        if err.getErrorCode == 1062 =>
      StatusCodes.Conflict -> ErrorRepresentation(ErrorCodes.ConflictingEntity,
                                                  "Entry already exists")
  }

  val logAndHandleErrors: PartialFunction[Throwable, Route] =
    toErrorRepresentation andThen {
      case (status, errorRepr) =>
        log
          .atError()
          .addKeyValue("errrorId", errorRepr.errorId.asJson)
          .addKeyValue("error", errorRepr.asJson)
          .log("an error occurred")

        complete(status -> errorRepr)
    }
}

object ErrorHandler {
  import Directives.*

  def logError(log: LoggingAdapter, uri: Uri, error: Throwable): UUID = {
    val id = UUID.randomUUID()
    log.error(error, s"Request error $id ($uri)")
    id
  }

  def errorRepr(id: UUID, error: Throwable): ErrorRepresentation = {
    ErrorRepresentation(
      ErrorCodes.InternalServerError,
      description = Option(error.getMessage).getOrElse("an error occurred"),
      errorId = id.some,
    )
  }

  private def defaultHandler: ExceptionHandler =
    Errors.logAndHandleErrors orElse ExceptionHandler {
      case e: Throwable =>
        (extractLog & extractUri) { (log, uri) =>
          val errorId = logError(log, uri, e)
          val entity = errorRepr(errorId, e)
          complete(InternalServerError -> entity.asJson)
        }
    }

  val handleErrors: Directive0 = handleExceptions(defaultHandler)
}
