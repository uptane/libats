package com.advancedtelematic.libats.logging

import ch.qos.logback.classic.pattern.{TargetLengthBasedClassNameAbbreviator, ThrowableProxyConverter}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.LayoutBase
import com.advancedtelematic.libats.logging.AtsLayoutBase.svcName

import java.time.Instant

object AtsLayoutBase {
  def svcName(loggerName: String) =
    Option(loggerName)
      .filter(_.contains("com.advancedtelematic"))
      .flatMap {
        _.stripPrefix("com.advancedtelematic.tuf.")
          .stripPrefix("com.advancedtelematic.")
          .split('.')
          .headOption
      }
}

class AtsLayoutBase extends LayoutBase[ILoggingEvent] {

  private val throwableProxyConverter = new ThrowableProxyConverter
  private val abbreviator = new TargetLengthBasedClassNameAbbreviator(40)

  override def doLayout(event: ILoggingEvent): String = {
    val sb = new StringBuffer()

    sb.append(event.getLevel.levelStr.head)
    sb.append("|")
    sb.append(Instant.ofEpochMilli(event.getTimeStamp).toString)

    svcName(event.getLoggerName).foreach  { n =>
      sb.append("|")
      sb.append(n)
    }

    sb.append("|")
    sb.append(abbreviator.abbreviate(event.getLoggerName))
    sb.append("|")
    sb.append(event.getFormattedMessage)
    sb.append("\n")

    val maybeEx = Option(throwableProxyConverter.convert(event)).filter(_ != "")

    maybeEx.foreach { ex =>
      sb.append(ex)
    }

    sb.toString
  }
}
