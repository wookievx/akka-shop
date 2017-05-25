package pl.edu.agh.llampart.backend.db

import akka.NotUsed
import akka.event.jul.Logger
import akka.stream.ThrottleMode
import akka.stream.scaladsl.{Merge, Source}

import scala.concurrent.Future
import scala.io.BufferedSource
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent._
import scala.util.Success

class BookRepository(contentRoot: String) {

  import BookRepository._
  import scala.concurrent.ExecutionContext.Implicits.global

  private val logger = Logger(getClass.getSimpleName)

  private val entryRegex = """([a-zA-Z0-9]+)\s*:\s*(\d+)""".r

  private def fileSource(fileName: String): BufferedSource = scala.io.Source.fromFile(s"$contentRoot/$fileName")

  private def bookFileSource(fileName: String) = fileSource(s"contents/$fileName")

  private def registryLookup(registry: String, title: String): Future[Book] = Future {
    blocking {
      fileSource(registry).getLines().collectFirst {
        case entryRegex(id, price) if id == title =>
          Book(price.toInt, title)
      }
    }
  }.collect {
    case Some(x) => x
  }

  def findBook(title: String): Future[Book] = {
    val reg1Lookup = registryLookup(`registry_1.txt`, title)
    val reg2Lookup = registryLookup(`registry_2.txt`, title)
    val f1 = Seq(reg1Lookup, reg2Lookup).reduce(_ fallbackTo _)
    val f2 = Seq(reg2Lookup, reg1Lookup).reduce(_ fallbackTo _)
    Future.firstCompletedOf(Seq(f1, f2))
  }

  private def registryReader(registry: String): Source[Book, NotUsed] = Source.fromIterator { () =>
    fileSource(registry).getLines().collect {
      case entryRegex(title, price) => Book(price.toInt, title)
    }
  }

  def knownBooks(): Source[Book, NotUsed] =
    Source.combine(registryReader(`registry_1.txt`), registryReader(`registry_2.txt`))(Merge(_))

  def streamBook(title: String): Source[String, NotUsed] =
    Source.fromIterator(() => bookFileSource(s"$title.txt").getLines()).throttle(1, 1 second, 1, _ => 1, ThrottleMode.Shaping)
}

object BookRepository {

  case class Book(price: Int, title: String)

  private val `registry_1.txt` = "registry_1.txt"
  private val `registry_2.txt` = "registry_2.txt"

}
