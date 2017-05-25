package pl.edu.agh.llampart.backend.db

import java.io.{File, FileWriter}

import akka.actor.{Actor, Props}
import akka.event.jul.Logger
import pl.edu.agh.llampart.backend.db.RequestHandler.GetAccountState

import scala.collection.mutable.{Map => MMap}
import scala.util.Try

class UserAccountDb(userAccountDb: String) extends Actor {
  import UserAccountDb._
  private val logger = Logger(getClass.getSimpleName)

  private val accounts = MMap.empty[String, Account].withDefaultValue(Account.empty)

  private val entryRegex = """([a-zA-Z0-9]+)\s*:\s*(.*)""".r

  private def restoreDb(): Unit = {
    accounts.clear()
    val source = scala.io.Source.fromFile(new File(userAccountDb))
    val forces = source.getLines().toList
    accounts ++= forces.iterator.collect {
      case entryRegex(id, ac) =>
        id -> ac.split('-').toSet
    }
    logger.info(accounts.toString())
  }

  private def persistDb(): Unit = {
    logger.info(s"Persisting accounts: $accounts")
    val fileWriter = new FileWriter(userAccountDb)
    for ((id, account) <- accounts) {
      val merged = account.mkString("-")
      fileWriter.write(s"$id:$merged\n")
    }
    fileWriter.flush()
    fileWriter.close()
    logger.info("Successfully persisted accounts!")
  }

  override def receive: Receive = {
    case PersistDb =>
      val sendTo = sender()
      persistDb()
      sendTo ! ()
    case RestoreDb =>
      val sendTo = sender()
      val result = Try {
        restoreDb()
      }
      sendTo ! result
    case GetAccountState(id) =>
      sender() ! accounts.get(id).iterator.flatMap(_.iterator)
    case DoesUserOwns(id, title) =>
      val res = accounts.get(id).exists(_.contains(title))
      sender() ! res
    case UserOwns(id, title) =>
      accounts(id)= accounts(id) + title
      sender() ! "OK"
  }
}

object UserAccountDb {
  sealed trait Command
  case class DoesUserOwns(client: String, title: String) extends Command
  case class UserOwns(client: String, title: String) extends Command
  case object PersistDb extends Command
  case object RestoreDb extends Command

  private type Account = Set[String]
  private val Account = Set
}
