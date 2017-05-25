package pl.edu.agh.llampart.client

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.{Config, ConfigFactory}
import Client._
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object ClientStarter {

  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.parseResources("client_config.conf")
    val system = ActorSystem("client", config)
    import system.dispatcher
    implicit val timeout = Timeout(10 seconds)
    val mainActor = system.actorOf(Props(new Client(args.headOption.getOrElse("Test42"))))
    Iterator.continually(readLine(mainActor)).takeWhile(identity).foreach(_ => ())
    system.terminate()
  }
  private val lineRegex = """(\-[fbvh])\s+(\w*)""".r
  private val helpString = "Available commands are: -f <title>, -b <title>, -v <title>"

  def readLine(actor: ActorRef)(implicit timeout: Timeout, context: ExecutionContext): Boolean = {
    val line = scala.io.StdIn.readLine("> ")
    line match {
      case lineRegex("-f", title) =>
        actor ! Find(title)
        true
      case lineRegex("-b", title) =>
        actor ! Buy(title)
        true
      case lineRegex("-v", title) =>
        actor ! RequestView(title)
        true
      case lineRegex("-h", "") =>
        println(helpString)
        true
      case s if s.startsWith(":quit") =>
        false
      case _ =>
        println(s"Invalid option! $helpString")
        true
    }
  }

}
