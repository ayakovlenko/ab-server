package services

import javax.inject.{Inject, Named, Singleton}

import actors.Orders
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import errors._
import models.{ContactInfo, MenuItem, OrderInfo}
import monix.eval.Task
import okhttp3.{MultipartBody, OkHttpClient, Request}
import parsers.AndysParser
import play.api.Logger

import scala.concurrent.duration._

@Singleton
class AndysService @Inject()(@Named("andys-orders") orders: ActorRef)
                            (implicit system: ActorSystem) {

  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private implicit val timeout: FiniteDuration = 10.seconds

  private implicit val askTimeout: Timeout = 5.seconds

  private val RootUrl = "https://www.andys.md"

  private val client = new OkHttpClient()

  /**
    * Parses pizza page on andys.md.
    *
    * @param lang language of the page
    * @return
    */
  def menu(lang: String, category: Int): Task[List[MenuItem]] = Task {

    val request = new Request.Builder()
      .url(s"$RootUrl/$lang/pages/menu/$category/")
      .get()
      .build()

    val response = client.newCall(request).execute()

    val html = response.body().string()

    AndysParser.parsePizzaPage(html, lang)
  }

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
              _ <- Task.sequence {
                items.groupBy(identity).mapValues(_.size).map {
                  case (item, quantity) => addItemToOrder(item, quantity, sId)
                }
              }
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
  private def createSession(): Task[Option[String]] =
    Task.deferFutureAction { implicit s =>
      val httpRequest = HttpRequest()
        .withUri(RootUrl)
        .withMethod(HttpMethods.HEAD)

      Http().singleRequest(httpRequest)
        .map {
          _.headers
          .filter(_.name() == "Set-Cookie")
          .map(_.asInstanceOf[`Set-Cookie`])
          .find(_.cookie.name == "PHPSESSID")
          .map(_.cookie.value)
        }
    }

  private def requestConfirmationPage(lang: String, sessionId: String): Task[String] = Task {
    val request = new Request.Builder()
      .url(s"$RootUrl/$lang/pages/cart/step4/")
      .get()
      .addHeader("cookie", s"PHPSESSID=$sessionId")
      .build()

    val response = client.newCall(request).execute()

    response.body().string()
  }.timeout(timeout)

  private def addItemToOrder(item: Long, quantity: Int, sessionId: String): Task[Unit] = Task {

    val body = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("id", item.toString)
      .addFormDataPart("quan", quantity.toString)
      .build()

    val request = new Request.Builder()
      .url(s"$RootUrl/pages/addtocart/")
      .post(body)
      .addHeader("cookie", s"PHPSESSID=$sessionId")
      .build()

    client.newCall(request).execute()
  }.map { _ =>
    Logger.info(s"added item $item ($quantity) to order $sessionId")
    ()
  }.timeout(timeout)

  private def addContactInfoToOrder(contactInfo: ContactInfo, sessionId: String): Task[Unit] = {
    import AndysService._

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

  /*
  private def addContactInfoToOrder(contactInfo: ContactInfo, sessionId: String): Task[Unit] = Task {
    val body = new MultipartBody.Builder()
      //.setType(MultipartBody.FORM)
      .addFormDataPart("name", contactInfo.name)
      .addFormDataPart("city", contactInfo.city.toString)
      .addFormDataPart("street", contactInfo.street)
      .addFormDataPart("house", contactInfo.house.toString)
      .addFormDataPart("phone", contactInfo.phone)
      .addFormDataPart("orderInfoDiscountCard", contactInfo.discount.getOrElse(""))
      .build()

    val request = new Request.Builder()
      .url(s"$RootUrl/pages/setcartadr/")
      .post(body)
      .addHeader("cookie", s"PHPSESSID=$sessionId")
      .build()

    client.newCall(request).execute()
  }.map { _ =>
    Logger.info(s"added contact info to order $sessionId")
    ()
  }.timeout(timeout)
  */
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
