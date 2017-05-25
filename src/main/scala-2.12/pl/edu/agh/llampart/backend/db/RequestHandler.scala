package pl.edu.agh.llampart.backend.db

import akka.NotUsed
import akka.actor.{Actor, Props}
import akka.event.jul.Logger
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import pl.edu.agh.llampart.backend.db.BookRepository.Book
import pl.edu.agh.llampart.backend.db.UserAccountDb.{DoesUserOwns, PersistDb, RestoreDb, UserOwns}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class RequestHandler(bookRepository: BookRepository, orderDbPath: String, userAccountDbPath: String)(implicit materializer: ActorMaterializer) extends Actor {
  import RequestHandler._
  import context.dispatcher
  private implicit val timeout = Timeout(10 seconds)
  private val logger = Logger(getClass.getSimpleName)

  private lazy val orderDb = context.actorOf(Props(new OrderDbActor(orderDbPath)))
  private lazy val userAccountDb = context.actorOf(Props(new UserAccountDb(userAccountDbPath)))


  private def accountSource(accountIterator: Future[Iterator[String]]): Source[String, NotUsed] = {
    Source.fromFuture(accountIterator).mapConcat(_.to[IIterable])
  }

  @scala.throws(classOf[Exception])
  override def preStart(): Unit = {
    Await.ready(userAccountDb ? RestoreDb, 10 seconds)
  }

  override def receive: Receive = {
    case Search(title) =>
      val sendTo = sender()
      bookRepository.findBook(title).onComplete {
        case Success(b) =>
        sendTo ! Exists(b)
      }
    case ord@Order(title, client) =>
      val sendTo = sender()
      val combined = for {
        x <- bookRepository.findBook(title)
        _ <- orderDb ? ord
        _ <- userAccountDb ? UserOwns(client, title)
      } yield x
      combined.onComplete {
        sendTo ! _
      }
    case View(title, client) =>
      val sendTo = sender()
      val hasBook = for {
        _ <- bookRepository.findBook(title)
        has <- (userAccountDb ? DoesUserOwns(client, title)).mapTo[Boolean]
      } yield has
      hasBook.onComplete {
        case Success(true) =>
          bookRepository.streamBook(title).runWith(Sink.actorRef(sendTo, StreamEnd))
        case Success(false) =>
          sendTo ! NotOwned(title)
        case Failure(_) =>
          sendTo ! NotFound(title)
      }
    case ga: GetAccountState =>
      val sendTo = sender()
      val fBookIterator = (userAccountDb ? ga).mapTo[Iterator[String]]
      accountSource(fBookIterator).runWith(Sink.actorRef(sendTo, StreamEnd))
    case GetPresentBooks =>
      val sendTo = sender()
      bookRepository.knownBooks().runWith(Sink.actorRef(sendTo, StreamEnd))
    case Terminate =>
      val sendTo = sender()
      (userAccountDb ? PersistDb).onComplete { _ =>
        sendTo ! ()
      }
  }
}

object RequestHandler {

  type IIterable[A] = scala.collection.immutable.Iterable[A]
  val IIterable = scala.collection.immutable.Iterable

  sealed trait Command

  case class Search(title: String) extends Command

  case class Order(title: String, id: String) extends Command

  case class View(title: String, id: String) extends Command

  case class GetAccountState(id: String) extends Command

  case object GetPresentBooks extends Command

  case object Terminate

  sealed trait OrderResult

  case class Exists(book: Book) extends OrderResult

  case class NotOwned(title: String) extends OrderResult

  case class NotFound(title: String) extends OrderResult

  case object StreamEnd extends OrderResult

}
