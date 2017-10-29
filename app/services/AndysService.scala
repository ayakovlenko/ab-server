package services

import javax.inject.{Inject, Named, Singleton}

import actors.Orders
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import errors._
import models.{ContactInfo, MenuItem, OrderConfirmation, OrderInfo}
import monix.eval.Task
import parsers.AndysParser
import play.api.Logger

import scala.concurrent.duration._

@Singleton
class AndysService @Inject()(@Named("andys-orders") orders: ActorRef,
                             andysHttpClient: AndysHttpClient)
                            (implicit system: ActorSystem) {

  private implicit val askTimeout: Timeout = 5.seconds

  //noinspection ScalaUnusedSymbol
  private val PaymentCard = 1

  private val PaymentCash = 2

  // ---

  /**
    * Parses pizza page on andys.md.
    *
    * @param lang language of the page
    * @return
    */
  def menu(lang: String, category: Int): Task[List[MenuItem]] =
    andysHttpClient.requestMenuPage(lang, category).map(AndysParser.parsePizzaPage(_, lang))

  /**
    * Adds item to the virtual order.
    */
  def addItemToOrder(item: Long, user: String, channel: String): Task[OrderInfo] = Task.deferFuture {
    (orders ? Orders.AddItem(channel, user, item)).mapTo[OrderInfo]
  }

  /**
    * Updates contact info of the virtual order.
    */
  def updateContactInfo(contactInfo: ContactInfo, user: String, channel: String): Task[OrderInfo] = Task.deferFuture {
    (orders ? Orders.UpdateContactInfo(contactInfo, user, channel)).mapTo[OrderInfo]
  }

  def checkoutOrder(user: String, channel: String, lang: String): Task[Either[OrderPlacementError, OrderConfirmation]] = {
    def error(e: OrderPlacementError) = Task.now(Left(e))

    Task.deferFuture {
      (orders ? Orders.GetOrderInfo(channel, user)).mapTo[Option[OrderInfo]]
    }.flatMap {
      case None => error(OrderNotFound)
      case Some(OrderInfo(_, Nil)) => error(OrderHasNoItems)
      case Some(OrderInfo(None, _)) => error(OrderHasNoContactInfo)
      case Some(OrderInfo(Some(contact), items)) =>
        andysHttpClient.createSession().flatMap {
          case None =>
            Logger.warn("failed to create a created")
            error(UnknownOrderPlacementError)
          case Some(sId) =>
            Logger.info(s"created session: $sId")
            for {
              _ <- Task.sequence {
                items.groupBy(identity).mapValues(_.size).map {
                  case (item, quantity) => andysHttpClient.addItemToOrder(item, quantity, sId)
                }
              }
              _ <- andysHttpClient.setPaymentMethod(PaymentCash, sId)
              _ <- andysHttpClient.addContactInfoToOrder(contact, sId)
              _ <- attachSession(user, channel, sId)
              confirmation <- getConfirmation(user, channel, lang)
            } yield Right(confirmation)
        }
    }
  }

  def attachSession(user: String, channel: String, sessionId: String): Task[Option[OrderInfo]] =
    Task.deferFuture {
      (orders ? Orders.AttachSession(channel, user, sessionId)).mapTo[Option[OrderInfo]]
    }

  def resetCart(user: String, channel: String): Task[Unit] = Task {
    orders ! Orders.ResetCart(channel, user)
  }

  def getConfirmation(user: String, channel: String, lang: String): Task[List[(String, String)]] =
    Task.deferFutureAction { implicit req =>
      (orders ? Orders.FindSession(channel, user)).mapTo[Option[String]]
    }.flatMap {
      case None => Task.now(Nil)
      case Some(sessionId) =>
        andysHttpClient.requestConfirmationPage(lang, sessionId).map(AndysParser.parseConfirmationPage(_, lang))
    }

  def placeOrder(user: String, channel: String, lang: String): Task[String] =
    Task.deferFutureAction { implicit s =>
      (orders ? Orders.FindSession(channel, user)).mapTo[Option[String]]
    }.flatMap {
      case None => Task.now("failed to place order")
      case Some(sessionId) =>
        andysHttpClient.requestPlaceOrder(lang, sessionId)
          .map { response =>
            Logger.info(s"placed order $sessionId: $response")
            response
          }
    }
}
