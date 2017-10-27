package services

import javax.inject.{Inject, Named, Singleton}

import actors.Orders
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import models.{ContactInfo, MenuItem, OrderInfo}
import monix.eval.Task
import parsers.AndysParser
import akka.pattern.ask
import errors._

import play.api.Logger

import scala.concurrent.duration._

@Singleton
class AndysService @Inject()(@Named("andys-orders") orders: ActorRef)
                            (implicit system: ActorSystem) {

  import AndysService._

  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private implicit val timeout: FiniteDuration = 5.seconds

  private implicit val askTimeout: Timeout = 5.seconds

  private val RootUrl = "https://www.andys.md"

  /**
    * Parses pizza page on andys.md.
    *
    * @param lang language of the page
    * @return
    */
  def pizzas(lang: String): Task[List[MenuItem]] = Task.deferFutureAction { implicit s =>
    Http().singleRequest(HttpRequest(uri = s"$RootUrl/$lang/pages/menu/8/"))
      .flatMap {
        _.entity.toStrict(timeout).map(_.data.utf8String)
      }
      .map { html =>
        AndysParser.parsePizzaPage(html, lang)
      }
  }

  /**
    * Adds item to the virtual order.
    */
  def addItemToOrder(item: Long, user: String, channel: String): Task[OrderInfo] = Task.fromFuture {
    (orders ? Orders.AddItem(channel, user, item)).mapTo[OrderInfo]
  }

  /**
    * Updates contact info of the virtual order.
    */
  def updateContactInfo(contactInfo: ContactInfo, user: String, channel: String): Task[OrderInfo] = Task.fromFuture {
    (orders ? Orders.UpdateContactInfo(contactInfo, user, channel)).mapTo[OrderInfo]
  }

  def placeOrder(user: String, channel: String): Task[Either[OrderPlacementError, String]] = {
    def error(e: OrderPlacementError) = Task.now(Left(e))

    Task.deferFuture {
      (orders ? Orders.GetOrderInfo(channel, user)).mapTo[Option[OrderInfo]]
    }.flatMap {
      case None => error(OrderNotFound)
      case Some(OrderInfo(_, Nil)) => error(OrderHasNoItems)
      case Some(OrderInfo(None, _)) => error(OrderHasNoContactInfo)
      case Some(OrderInfo(Some(contact), items)) =>
        createSession().flatMap {
          case None =>
            Logger.warn("failed to create a created")
            error(UnknownOrderPlacementError)
          case Some(sId) =>
            Logger.info(s"created session: $sId")
            for {
              _ <- Task.sequence(items.map(addPizzaToOrder(_, sId)))
              _ <- addContactInfoToOrder(contact, sId)
              _ <- attachSession(user, channel, sId)
            } yield Right(sId)
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
        requestConfirmationPage(lang, sessionId).map(AndysParser.parseConfirmationPage(_, lang))
    }

  // ---

  /**
    * Creates new session.
    *
    * @return session id
    */
  private def createSession(): Task[Option[String]] = Task.deferFutureAction { implicit s =>
    Http().singleRequest(HttpRequest(
      HttpMethods.HEAD,
      uri = RootUrl,
    )).map {
      _.headers
        .filter(_.name() == "Set-Cookie")
        .map(_.asInstanceOf[`Set-Cookie`])
        .find(_.cookie.name == "PHPSESSID")
        .map(_.cookie.value)
    }
  }

  private def requestConfirmationPage(lang: String, sessionId: String): Task[String] =
    Task.deferFutureAction { implicit s =>
      Http()
        .singleRequest(HttpRequest(
          method = HttpMethods.GET,
          uri = s"$RootUrl/$lang/pages/cart/step4/",
          headers = List(Cookie("PHPSESSID", sessionId)),
        ))
        .flatMap(_.entity.toStrict(timeout).map(_.data.utf8String))
    }

  private def addPizzaToOrder(pizzaId: Long, sessionId: String): Task[Unit] = Task.deferFutureAction { implicit s =>
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = s"$RootUrl/pages/addtocart/",
      headers = List(Cookie("PHPSESSID", sessionId)),
      entity = Map(
        "id" -> pizzaId,
        "korj" -> 0,
        "souce" -> 0,
        "quan" -> 1
      ).asFormData
    )).map { _ =>
      Logger.info(s"added item $pizzaId to order $sessionId")
      ()
    }
  }

  private def addContactInfoToOrder(contactInfo: ContactInfo, sessionId: String): Task[Unit] =
    Task.deferFutureAction { implicit s =>
      Http().singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"$RootUrl/pages/setcartadr/",
        headers = List(Cookie("PHPSESSID", sessionId)),
        entity = Map(
          "name" -> contactInfo.name,
          "city" -> contactInfo.city,
          "street" -> contactInfo.street,
          "house" -> contactInfo.house,
          "phone" -> contactInfo.phone,
          "orderInfoDiscountCard" -> contactInfo.discount.getOrElse("")
        ).asFormData
      )).map { _ =>
        Logger.info(s"added contact info to order $sessionId")
        ()
      }
    }.timeout(timeout)
}

object AndysService {

  implicit class MapLike[A](val map: Map[String, A]) extends AnyVal {

    def asFormData: HttpEntity.Strict = {
      FormData(map.mapValues(asHttpEntity)).toEntity
    }

    private def asHttpEntity(a: A): HttpEntity.Strict = {
      HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString(a.toString))
    }
  }
}
