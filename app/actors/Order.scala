package actors

import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.actor.{Actor, ActorLogging, PoisonPill, Props, Timers}
import models.{ContactInfo, OrderInfo}

import scala.concurrent.duration._

class Order extends Actor with ActorLogging with Timers {

  import Order._

  private var items: List[Long] = Nil

  private var contactInfo: Option[ContactInfo] = None

  private var sessionId: Option[String] = None

  def orderInfo: OrderInfo = OrderInfo(contactInfo, items)

  // ---

  private var expires: Instant = _

  timers.startPeriodicTimer(
    key = TickKey,
    msg = Tick,
    interval = 10.seconds
  )

  private def prolongLife(): Unit = {
    expires = expires.plus(1, ChronoUnit.MINUTES)
    println(s"${self.path.name} will expire at $expires")
  }

  // ---

  override def receive: Receive = {
    case GetOrderInfo =>
      prolongLife()
      sender() ! Some(orderInfo)
    case AddItem(item) =>
      prolongLife()
      items = item :: items
      sender() ! orderInfo
      log.debug("{}: {}", self.path, items)
    case UpdateContactInfo(newContactInfo) =>
      prolongLife()
      contactInfo = Some(newContactInfo)
      sender() ! orderInfo
    case AttachSession(id) =>
      prolongLife()
      sessionId = Some(id)
      sender() ! Some(orderInfo)
      log.debug("{}: session {} attached", self.path, sessionId)
    case FindSession =>
      prolongLife()
      sender() ! sessionId
    case Tick =>
      if (expires.isBefore(Instant.now())) {
        println(s"${self.path.name} has expired")
        self ! PoisonPill
      }
  }

  override def preStart(): Unit = {
    expires = Instant.now().plus(1, ChronoUnit.MINUTES)

    super.preStart()
  }
}

object Order {

  case object GetOrderInfo

  case class AddItem(item: Long)

  case class UpdateContactInfo(contactInfo: ContactInfo)

  case class AttachSession(sessionId: String)

  case object FindSession

  private case object TickKey

  private case object Tick

  def props: Props = Props[Order]
}
