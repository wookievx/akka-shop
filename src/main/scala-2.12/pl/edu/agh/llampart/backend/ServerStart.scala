package pl.edu.agh.llampart.backend

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import pl.edu.agh.llampart.backend.db.RequestHandler.{Order, Terminate, View}
import pl.edu.agh.llampart.backend.db.{BookRepository, RequestHandler}
import pl.edu.agh.llampart.client.Client
import akka.pattern.ask
import scala.concurrent.duration._
import scala.language.postfixOps

object ServerStart {
  val handlerName = "handler"
  val systemName = "server"


  private implicit val timeout = Timeout(10 seconds)


  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.parseResources("server_config.conf")
    val booksUrl = config.getString("paths.books-path")
    val configUrl = config.getString("paths.config-path")
    implicit val actorSystem = ActorSystem(systemName, config)
    implicit val materializer = ActorMaterializer()
    import actorSystem.dispatcher
    val repository = new BookRepository(booksUrl)
    val tempOrders = s"$configUrl/orders.txt"
    val tempAccounts = s"$configUrl/accounts.txt"
    val serverActor = actorSystem.actorOf(Props(new RequestHandler(repository, tempOrders, tempAccounts)), handlerName)
    scala.io.StdIn.readLine()
    serverActor ? Terminate onComplete { _ => actorSystem.terminate() }
  }
}
