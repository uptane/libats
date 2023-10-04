/*
 * Copyright: Copyright (C) 2015, Jaguar Land Rover
 * License: MPL-2.0
 */

package com.advancedtelematic.libats.messaging.kakfa

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import akka.kafka.CommitterSettings
import akka.stream.scaladsl.{Flow, Sink}
import akka.testkit.TestKit
import com.advancedtelematic.libats.messaging.kafka.{JsonDeserializerException, KafkaClient}
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import com.typesafe.config.ConfigFactory
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.concurrent.Future
import scala.concurrent.duration.*

case class KafkaSpecMessage0(payload: String)
case class KafkaSpecMessage1(payload: String)
case class KafkaSpecMessage2(payload: String)
case class KafkaSpecMessage3(payload: String)
case class KafkaSpecMessage4(payload: String)

object KafkaSpecMessage {
  implicit val messageLike0: MessageLike[KafkaSpecMessage0] = MessageLike.derive(_ => "KafkaSpecMessage0")
  implicit val messageLike1: MessageLike[KafkaSpecMessage1] = MessageLike.derive(_ => "KafkaSpecMessage1")
  implicit val messageLike2: MessageLike[KafkaSpecMessage2] = MessageLike.derive(_ => "KafkaSpecMessage2")
  implicit val messageLike3: MessageLike[KafkaSpecMessage3] = MessageLike.derive(_ => "KafkaSpecMessage3")
  implicit val messageLike4: MessageLike[KafkaSpecMessage4] = MessageLike.derive(_ => "KafkaSpecMessage4")
}

class KafkaClientIntegrationSpec extends TestKit(ActorSystem("KafkaClientSpec"))
  with AnyFunSuiteLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures
  with PatienceConfiguration
  with Eventually {

  import KafkaSpecMessage.*

  implicit val _ec: scala.concurrent.ExecutionContextExecutor = system.dispatcher

  override implicit def patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(500, Millis))

  val publisher = KafkaClient.publisher(system, system.settings.config)

  lazy val commiterSettings = CommitterSettings(ConfigFactory.load().getConfig("ats.messaging.kafka.committer"))

  test("can send an event to bus") {
    val testMsg = KafkaSpecMessage1(Instant.now.toString)
    val f = publisher.publish(testMsg).map(_ => 0)
    f.futureValue shouldBe 0
  }

  test("can send-receive events from bus") {
    val testMsg = KafkaSpecMessage2(Instant.now.toString)

    val flow = Flow[KafkaSpecMessage2].mapAsync(1)((_: KafkaSpecMessage2) => FastFuture.successful(Done))
    val source = KafkaClient.committableSource[KafkaSpecMessage2](system.settings.config, commiterSettings, "kafka-test", flow)
    val msgFuture = source.groupedWithin(10, 5.seconds).runWith(Sink.head)

    for {
      _ <- akka.pattern.after(3.seconds, system.scheduler)(Future.successful(()))
      _ <- publisher.publish(testMsg)
    } yield ()

    msgFuture.futureValue should contain(testMsg)
  }

  test("can send-receive and commit events from bus") {
    val testMsg = KafkaSpecMessage3(Instant.now.toString)

    val cfg = ConfigFactory.parseString(
      """
        |messaging.listener.parallelism=2
      """.stripMargin).withFallback(system.settings.config)

    val flow = Flow[KafkaSpecMessage3].mapAsync(1)((_: KafkaSpecMessage3) => FastFuture.successful(Done))
    val source = KafkaClient.committableSource[KafkaSpecMessage3](cfg, commiterSettings, "kafka-test", flow)

    val msgFuture = source.runWith(Sink.head)

    for {
      _ <- akka.pattern.after(3.seconds, system.scheduler)(Future.successful(()))
      _ <- publisher.publish(testMsg)
    } yield ()

    msgFuture.futureValue should equal(testMsg)
  }

  test("returns error when json cannot be deserialized and skipJsonErrors is false") {
    val testMsgJson = Json.obj("not-valid" -> 0.asJson)

    val jsonMsgLike = new MessageLike[Json]() {
      override def streamName: String = implicitly[MessageLike[KafkaSpecMessage0]].streamName // Push a bad json to the KafkaSpecMessage stream

      override def id(v: Json): String = "0L"

      override implicit val encoder: Encoder[Json] = Encoder.encodeJson
      override implicit val decoder: Decoder[Json] = Decoder.decodeJson
    }

    val cfg = ConfigFactory.parseMap(Map("ats.messaging.kafka.skipJsonErrors" -> false).asJava).withFallback(system.settings.config)

    val flow = Flow[KafkaSpecMessage0].mapAsync(1)((_: KafkaSpecMessage0) => FastFuture.successful(Done))
    val source = KafkaClient.committableSource[KafkaSpecMessage0](cfg, commiterSettings, "kafka-test", flow)

    for {
      _ <- akka.pattern.after(3.seconds)(Future.successful(()))
      _ <- publisher.publish(testMsgJson)(implicitly, jsonMsgLike)
    } yield ()

    val msgFuture = source.runWith(Sink.head)
    msgFuture.failed.futureValue shouldBe a[JsonDeserializerException]
  }

  test("skips error when json cannot be deserialized") {
    val testMsgJson = Json.obj("not-valid" -> 0.asJson)

    val jsonMsgLike = new MessageLike[Json]() {
      override def streamName: String = implicitly[MessageLike[KafkaSpecMessage4]].streamName // Push a bad json to the KafkaSpecMessage stream

      override def id(v: Json): String = "0L"

      override implicit val encoder: Encoder[Json] = Encoder.encodeJson
      override implicit val decoder: Decoder[Json] = Decoder.decodeJson
    }

    val cfg = system.settings.config

    val flow = Flow[KafkaSpecMessage4].mapAsync(1)((_: KafkaSpecMessage4) => FastFuture.successful(Done))
    val source = KafkaClient.committableSource[KafkaSpecMessage4](cfg, commiterSettings, "kafka-test", flow)

    val testMsg = KafkaSpecMessage4(Instant.now.toString)

    for {
      _ <- akka.pattern.after(3.seconds)(Future.successful(()))
      _ <- publisher.publish(testMsgJson) (implicitly, jsonMsgLike)
      _ <- publisher.publish(testMsg)
    } yield ()

    val msgFuture = source.runWith(Sink.head)
    msgFuture.futureValue should equal(testMsg)
  }
}
