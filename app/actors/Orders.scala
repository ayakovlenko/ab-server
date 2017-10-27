package actors

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import models.ContactInfo

class Orders extends Actor {

  import Orders._

  override def receive: Receive = {
    case GetOrderInfo(channel, user) =>
      findOrder(channel, user) match {
        case None => sender() ! None
        case Some(order) => order forward Order.GetOrderInfo
      }

    case AddItem(channel, user, item) =>
      val order = findOrCreateOrder(channel, user)

      order forward Order.AddItem(item)

    case UpdateContactInfo(contactInfo, user, channel) =>
      val order = findOrCreateOrder(channel, user)

      order forward Order.UpdateContactInfo(contactInfo)

    case FindSession(channel, user) =>
      findOrder(channel, user) match {
        case None => sender() ! None
        case Some(order) => order forward Order.FindSession
      }

    case AttachSession(channel, user, sessionId) =>
      findOrder(channel, user) match {
        case None => sender() ! None
        case Some(order) => order forward Order.AttachSession(sessionId)
      }

    case ResetCart(channel, user) =>
      val order = findOrCreateOrder(channel, user)

      order ! PoisonPill
  }

  private def orderActorName(channel: String, user: String): String = s"actor-$channel-$user"

  private def findOrder(channel: String, user: String): Option[ActorRef] = {
    val actorName = orderActorName(channel, user)
    context.child(actorName)
  }

  private def findOrCreateOrder(channel: String, user: String): ActorRef = {
    val actorName = orderActorName(channel, user)
    findOrder(channel, user).getOrElse(context.actorOf(Order.props, actorName))
  }
}

object Orders {

  case class GetOrderInfo(channel: String, user: String)

  case class AddItem(channel: String, user: String, item: Long)

  case class ResetCart(channel: String, user: String)

  case class UpdateContactInfo(contactInfo: ContactInfo, user: String, channel: String)

  case class AttachSession(channel: String, user: String, sessionId: String)

  case class FindSession(channel: String, user: String)

  def props: Props = Props[Orders]
}
