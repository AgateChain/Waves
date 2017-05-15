package scorex.network

import java.net.InetSocketAddress

import akka.actor._
import scorex.network.message.{Message, MessageSpec}

object NetworkController {
  case class RegisterMessagesHandler(specs: Seq[MessageSpec[_]], handler: ActorRef)

  case class DataFromPeer[V](messageType: Message.MessageCode, data: V, source: ConnectedPeer)

  case class SendToNetwork(message: Message[_ <: AnyRef], sendingStrategy: SendingStrategy)

  case class ConnectTo(address: InetSocketAddress)

  case class InboundConnection(connection: ActorRef, remote: InetSocketAddress)
}
