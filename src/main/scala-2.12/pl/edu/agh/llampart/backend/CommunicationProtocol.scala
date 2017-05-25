package pl.edu.agh.llampart.backend

sealed trait CommunicationProtocol
case class OrderTitle(title: String, id: String) extends CommunicationProtocol
case class FindTitle(title: String) extends CommunicationProtocol
case class StreamTitle(title: String) extends CommunicationProtocol
