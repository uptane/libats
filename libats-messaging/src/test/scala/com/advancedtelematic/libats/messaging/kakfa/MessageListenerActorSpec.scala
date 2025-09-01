package com.advancedtelematic.libats.messaging

import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import com.advancedtelematic.libats.messaging.daemon.MessageBusListenerActor
import com.advancedtelematic.libats.messaging.daemon.MessageBusListenerActor.Subscribe
import com.advancedtelematic.libats.messaging_datatype.MessageLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}
import org.scalatest.funsuite.{AnyFunSuite, AnyFunSuiteLike}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace

case class MsgListenerSpecItem(id: Int, payload: String)

object MsgListenerSpecItem {
  implicit val messageLike: com.advancedtelematic.libats.messaging_datatype.MessageLike[com.advancedtelematic.libats.messaging.MsgListenerSpecItem] = MessageLike.derive[MsgListenerSpecItem](_.id.toString)

  case object MsgListenerSpecError extends Exception("test ERROR") with NoStackTrace
}

class StorageListenerMonitor extends ListenerMonitor {
  var processed = 0
  var error = 0
  var finished = 0

  override def onProcessed: Future[Unit] = Future.successful(processed += 1)

  override def onError(cause: Throwable): Future[Unit] = Future.successful(error += 1)

  override def onFinished: Future[Unit] = Future.successful(finished += 1)
}

class MessageListenerActorSpec extends TestKit(ActorSystem("MessageListenerActorSpec"))
  with AnyFunSuiteLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures
  with PatienceConfiguration
  with Eventually {

  import MsgListenerSpecItem._

  implicit val _ec: scala.concurrent.ExecutionContextExecutor = system.dispatcher

  val msg = MsgListenerSpecItem(1, "payload")

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  test("message listener source monitors message events") {
    val source: Source[MsgListenerSpecItem, NotUsed] = Source(List(msg))
    val monitor = new StorageListenerMonitor
    val probe = TestProbe()
    val sink = Sink.foreach[MsgListenerSpecItem] { probe.ref ! _ }
    val actor = system.actorOf(Props(new MessageBusListenerActor(source, monitor, sink)))

    actor ! Subscribe

    probe.expectMsgType[MsgListenerSpecItem]

    eventually {
      monitor.error shouldBe 0
      monitor.finished shouldBe 1
      monitor.processed shouldBe 1
    }
  }

  test("monitor receives errors on error") {
    val source: Source[MsgListenerSpecItem, NotUsed] = Source.failed(MsgListenerSpecError)
    val monitor = new StorageListenerMonitor
    val actor = system.actorOf(Props(new MessageBusListenerActor(source, monitor, Sink.ignore)))

    actor ! Subscribe

    eventually {
      monitor.error shouldBe 1
      monitor.finished shouldBe 0
      monitor.processed shouldBe 0
    }
  }

  override protected def afterAll(): Unit = {
    system.terminate()
  }
}
