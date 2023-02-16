package com.advancedtelematic.libats.slick.db

import com.advancedtelematic.libats.data.DataType.Namespace
import java.util.UUID
import SlickAnyVal._
import com.advancedtelematic.libats.test.{DatabaseSpec, MysqlDatabaseSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.{Seconds, Span}
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery
import org.scalatest.matchers.should.Matchers
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext
import scala.util.control.NoStackTrace
import com.advancedtelematic.libats.slick.codecs.SlickEnumMapper

object SlickExtensionsSpec {

  implicit val objectStatusMapping = SlickEnumMapper.enumMapper(ObjectStatus)

  case class Book(id: Long, title: String, code: Option[String] = None, createdAt: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS))

  class BooksTable(tag: Tag) extends Table[Book](tag, "books") {
    def id = column[Long]("id", O.PrimaryKey)
    def title = column[String]("title")
    def code = column[Option[String]]("code")
    def createdAt = column[Instant]("created_at")(SlickExtensions.javaInstantMapping)

    override def * = (id, title, code, createdAt) <> ((Book.apply _).tupled, Book.unapply)
  }

  protected val books = TableQuery[BooksTable]

  case class BookMeta(id: Long, bookId: Long, tag: Long)

  class BookMetaTable(tag: Tag) extends Table[BookMeta](tag, "book_meta") {
    def id    = column[Long]("id")
    def bookId = column[Long]("book_id")
    def bookTag  = column[Long]("tag")

    def pk = primaryKey("book_meta_pk", (bookId, id))

    override def * = (bookId, id, bookTag) <> ((BookMeta.apply _).tupled, BookMeta.unapply)
  }

  protected val bookMeta = TableQuery[BookMetaTable]

  object ObjectStatus extends Enumeration {
    type ObjectStatus = Value

    val UPLOADED = Value
  }

  case class TObject(namespace: Namespace, objectId: String, status: ObjectStatus.ObjectStatus)

  class TObjectTable(tag: Tag) extends Table[TObject](tag, "objects") {
    def namespace = column[Namespace]("namespace")
    def objectId = column[String]("object_id")
    def status = column[ObjectStatus.ObjectStatus]("status")

    def pk = primaryKey("object_pk", (namespace, objectId))

    override def * = (namespace, objectId, status) <> ((TObject.apply _).tupled, TObject.unapply)
  }

  protected val objects = TableQuery[TObjectTable]
}


class SlickExtensionsSpec extends AnyFunSuite with Matchers with ScalaFutures with MysqlDatabaseSpec {

  import SlickExtensions._
  import SlickExtensionsSpec._

  val Error = new Exception("Expected Error") with NoStackTrace

  import ExecutionContext.Implicits.global

  override implicit def patienceConfig = PatienceConfig().copy(timeout = Span(5, Seconds))

  override protected def testDbConfig: Config = ConfigFactory.load().getConfig("ats.database")

  test("resultHead on a Query returns the first query result") {
    val book = Book(10, "Some book")

    val f = for {
      _ <- db.run(books += book)
      inserted <- db.run(books.resultHead(Error))
    } yield inserted

    f.futureValue shouldBe book
  }

  test("resultHead on a Query returns the error in arg") {
    val f = db.run(books.filter(_.id === 15L).resultHead(Error))
    f.failed.futureValue shouldBe Error
  }

  test("maybeFilter uses filter if condition is defined") {
    val f = for {
      _ <- db.run(books += Book(20, "Some book", Option("20 some code")))
      result <- db.run(books.maybeFilter(_.id === Option(20L)).result)
    } yield result

    f.futureValue.length shouldBe 1
    f.futureValue.head.id shouldBe 20L
  }

  test("maybeFilter ignores filter if condition is None") {
    val f = for {
      _ <- db.run(books += Book(30, "Some book"))
      result <- db.run(books.maybeFilter(_.id === Option.empty[Long]).result)
    } yield result

    f.futureValue.length shouldBe >(1)
    f.futureValue.map(_.id) should contain(30L)
  }

  test("maybeContains uses string if it is defined") {
    val f = for {
      _ <- db.run(books += Book(40, "A very interesting book", Some("30 some code")))
      result <- db.run(books.maybeContains(_.title, Some("interesting")).result)
    } yield result

    f.futureValue.length shouldBe 1
    f.futureValue.head.id shouldBe 40L
  }

  test("maybeContains gives all elements if string is empty") {
    val result = db.run(books.maybeContains(_.title, Some("")).result)
    result.futureValue.length shouldBe 4
  }

  test("maybeContains gives all elements if string is None") {
    val result = db.run(books.maybeContains(_.title, None).result)
    result.futureValue.length shouldBe 4
  }

  test("handleIntegrityErrors works with mariadb 10.2") {
    val g = BookMeta(-1, -1, 0)
    val f = db.run(bookMeta.insertOrUpdate(g).handleIntegrityErrors(Error))

    f.failed.futureValue shouldBe Error
  }

  test("handleForeignKeyError throws the expected error") {
    val g = BookMeta(1, 1984, 0)
    val f = db.run((bookMeta += g).handleForeignKeyError(Error))

    f.failed.futureValue shouldBe Error
  }

  test("handleForeignKeyError ignores the error when FK exists") {
    val b = Book(15, "The Count of Monte Cristo", Some("9781377261379"))
    val bm = BookMeta(15, 15, 0)
    val f = db.run((books += b).andThen(bookMeta += bm).handleForeignKeyError(Error))

    f.futureValue shouldBe 1
  }

  test("insertIfNotExists inserts the element if it does not exist") {
    val b = Book(16, "Also sprach Zarathustra", None)
    val a = books.insertIfNotExists(b) { _.filter(_.id === b.id) }
    db.run(a).futureValue shouldBe ()
    db.run(books.filter(_.id === b.id).result).futureValue should contain only b
  }

  test("insertIfNotExists does nothing and does not fail if the element does not exists") {
    val b = Book(17, "Jenseits von Gut und Böse", None)
    db.run(books += b).futureValue shouldBe 1
    val a = books.insertIfNotExists(b) { _.filter(_.id === b.id) }
    db.run(a).futureValue shouldBe ()
    db.run(books.filter(_.id === b.id).result).futureValue should contain only b
  }

  // Comes from treehub, fails when upgrading 3.x
  test("fails with mariadb connector/j 3.x") {
    val uuid = UUID.randomUUID().toString
    val ns = Namespace(uuid)
    val objectId = "a1/e8de5d0c43e200eb2dd8f9fdb30b9e0c5df94f4ab52e0561ea70fbf235a27a.commitmeta"
    val o = TObject(ns, objectId, ObjectStatus.UPLOADED)
    val f = db.run(objects += o).futureValue

    db.run(objects
      .filter(_.objectId === objectId)
      .filter(_.namespace === ns)
      .filter(_.status === ObjectStatus.UPLOADED).exists.result)
      .futureValue shouldBe true
  }

  test("reads/writes dates properly") {
    val now = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS)

    val b = Book(18, "Livro do desassossego", None, createdAt = now)
    db.run(books += b).futureValue shouldBe 1

    val saved = db.run(books.filter(_.id === b.id).result).futureValue

    saved should contain only b

    saved.head.createdAt shouldBe now
  }
}
