package actors

import akka.actor.{Actor, Props}
import akka.event.Logging
import models.{ContactInfo, OrderInfo}

class Order extends Actor {

  import Order._

  private val log = Logging(context.system, this)

  private var items: List[Long] = Nil

  private var contactInfo: Option[ContactInfo] = None

  private var sessionId: Option[String] = None

  def orderInfo: OrderInfo = OrderInfo(contactInfo, items)

  override def receive: Receive = {
    case GetOrderInfo =>
      sender() ! Some(orderInfo)
    case AddItem(item) =>
      items = item :: items
      sender() ! orderInfo
      log.debug("{}: {}", context.self.path, items)
    case UpdateContactInfo(newContactInfo) =>
      contactInfo = Some(newContactInfo)
      sender() ! orderInfo
    case AttachSession(id) =>
      sessionId = Some(id)
      sender() ! Some(orderInfo)
      log.debug("{}: session {} attached", context.self.path, sessionId)
    case FindSession =>
      sender() ! sessionId
  }
}

object Order {

  case object GetOrderInfo

  case class AddItem(item: Long)

  case class UpdateContactInfo(contactInfo: ContactInfo)

  case class AttachSession(sessionId: String)

  case object FindSession

  def props: Props = Props[Order]
}
