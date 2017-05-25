package pl.edu.agh.llampart.backend.db

import java.io._

import akka.actor.Actor
import akka.actor.Actor.Receive

import scala.util.Failure
import scala.util.control.NonFatal

class OrderDbActor(orderDbFile: String) extends Actor {
  import RequestHandler._

  override def receive: Receive = {
    case order: Order =>
      val sendTo = sender()
      try {
        val fileStream = new OutputStreamWriter(new FileOutputStream(orderDbFile))
        fileStream.write(order.toString)
        fileStream.flush()
        fileStream.close()
      } catch {
        case NonFatal(e) =>
          sendTo ! Failure(e)
      }
      sendTo ! "OK"
  }
}
