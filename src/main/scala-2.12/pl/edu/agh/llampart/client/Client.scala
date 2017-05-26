package pl.edu.agh.llampart.client

import akka.actor.Actor.Receive
import akka.actor.SupervisorStrategy.Resume
import akka.actor.{Actor, ActorRef, ActorSelection, OneForOneStrategy, Props, SupervisorStrategy}
import akka.event.jul.Logger
import pl.edu.agh.llampart.backend.ServerStart
import pl.edu.agh.llampart.backend.db.BookRepository.Book

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.util.control.NonFatal

class Client(id: String) extends Actor {

  import pl.edu.agh.llampart.backend.db.RequestHandler._
  import Client._
  import context.dispatcher
  private val logger = Logger(getClass.getSimpleName)

  private val knownBooks = MSet.empty[Book]
  private val ownedBooks = MSet.empty[Book]

  private val remoteResolver: () => Future[ActorRef] = {
    var current: Future[ActorRef] = Future.failed(new NoSuchElementException)
    var isFresh: Deadline = 1 second fromNow
    () => {
      if (isFresh.isOverdue() || current.value.collect{case Failure(_) => true}.exists(identity)) {
        current = context.actorSelection(s"akka.tcp://${ServerStart.systemName}@127.0.0.1:2552/user/${ServerStart.handlerName}").resolveOne(10 seconds)
        isFresh = 1 second fromNow
      }
      current
    }
  }

  class AccountUpdater extends Actor {
    override def receive: Receive = {
      case title: String =>
        ownedBooks ++= knownBooks.find(_.title == title)
      case StreamEnd =>
        println(s"Account state: $ownedBooks")
        context.stop(self)
    }
  }

  class LibraryUpdater(accountUpdater: ActorRef) extends Actor {
    override def receive: Receive = {
      case b: Book =>
        knownBooks += b
      case StreamEnd =>
        for (r <- remoteResolver()) (r ! GetAccountState(id))(accountUpdater)
        println(s"Awailable books: $knownBooks")
        context.stop(self)
    }
  }


  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    val accountUpdater = context.actorOf(Props(new AccountUpdater))
    val libraryUpdater = context.actorOf(Props(new LibraryUpdater(accountUpdater)))
    for (r <- remoteResolver()) (r ! GetPresentBooks)(libraryUpdater)
  }

  override def receive: Receive = {
    case Find(title) =>
      for (r <- remoteResolver()) r ! Search(title)
    case Buy(title) =>
      if (knownBooks.exists(_.title == title)) {
        for (r <- remoteResolver()) r ! Order(title, id)
      } else {
        logger.warning(s"Didn't check if book $title actually exists")
      }
    case RequestView(title) =>
      if (ownedBooks.exists(_.title == title)) {
        for (r <- remoteResolver()) r ! View(title, id)
      } else {
        logger.warning(s"Cannot request view of the book: $title, reason: not owned")
      }
    case Success(b: Book) =>
      println(s"Purchased: $b")
      ownedBooks += b
    case Exists(b) =>
      println(s"$b exists")
      knownBooks += b
    case line: String =>
      println(line)
    case NotFound(title) =>
      logger.info(s"Book with title: $title not found")
    case NotOwned(title) =>
      logger.info(s"Book with title: $title not owned")
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(10){
    case NonFatal(e) => Resume
  }
}

object Client {

  sealed trait ClientCommand

  case class Find(title: String) extends ClientCommand

  case class Buy(title: String) extends ClientCommand

  case class RequestView(title: String) extends ClientCommand



  type MSet[A] = scala.collection.mutable.Set[A]
  val MSet = scala.collection.mutable.Set

}
