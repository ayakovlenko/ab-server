package services

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import akka.http.scaladsl.model.headers.{Cookie, `Set-Cookie`}
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import models.ContactInfo
import monix.eval.Task
import okhttp3._
import play.api.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

@Singleton
class AndysHttpClient @Inject()(implicit system: ActorSystem) {

  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private implicit val timeout: FiniteDuration = 10.seconds

  private implicit val askTimeout: Timeout = 5.seconds

  private val RootUrl = "https://www.andys.md"

  private val client = new OkHttpClient()

  /**
    * Creates new session.
    *
    * @return session id
    */
  def createSession(): Task[Option[String]] =
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

  def requestConfirmationPage(lang: String, sessionId: String): Task[String] = Task {
    val request = new Request.Builder()
      .url(s"$RootUrl/$lang/pages/cart/step4/")
      .get()
      .addHeader("cookie", s"PHPSESSID=$sessionId")
      .build()

    val response = client.newCall(request).execute()

    response.body().string()
  }.timeout(timeout)

  def addItemToOrder(item: Long, quantity: Int, sessionId: String): Task[Unit] = Task {

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

    val response = client.newCall(request).execute()

    try {
      ()
    } finally {
      response.close()
    } /* ugh */
  }.map { _ =>
    Logger.info(s"added item $item ($quantity) to order $sessionId")
    ()
  }.timeout(timeout)

  def addContactInfoToOrder(contactInfo: ContactInfo, sessionId: String): Task[Unit] = {
    import AndysHttpClient._

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
          "orderInfoDiscountCard" -> contactInfo.discount.getOrElse(""),
          "mail" -> contactInfo.email.getOrElse("")
        ).asFormData
      )).map { _ =>
        Logger.info(s"added contact info to order $sessionId")
        ()
      }
    }.timeout(timeout)
  }

  def setPaymentMethod(paymentMethod: Int, sessionId: String): Task[Unit] = Task {
    val url = s"$RootUrl/pages/stptype_new/$paymentMethod/"

    val request = new Request.Builder()
      .url(url)
      .get()
      .addHeader("cookie", s"PHPSESSID=$sessionId")
      .build()

    val response = client.newCall(request).execute()

    try {
      ()
    } finally {
      response.close()
    } /* ugh */
  }.timeout(timeout)

  private class PlaceOrderRedirectInterceptor(lang: String) extends Interceptor {

    override def intercept(chain: Interceptor.Chain): Response = {
      val response = chain.proceed(chain.request())

      if (response.isRedirect) {
        val newResponse = response.newBuilder()
          .addHeader("Location", s"$RootUrl/ru/pages/cart/orderconfirmed/")
          .build()

        newResponse
      } else {
        response
      }
    }
  }

  private def placeOrderClient(lang: String) = new OkHttpClient.Builder()
    .addNetworkInterceptor(new PlaceOrderRedirectInterceptor(lang))
    .build()

  def requestPlaceOrder(lang: String, sessionId: String): Task[String] = Task {
    val request = new Request.Builder()
      .url(s"$RootUrl/pages/placeorder/")
      .get()
      .addHeader("cookie", s"PHPSESSID=$sessionId")
      .build()

    val response = placeOrderClient(lang).newCall(request).execute()

    val html = response.body().string()

    org.jsoup.Jsoup.parse(html).select("#prsent").text()
  }

  def requestMenuPage(lang: String, category: Int): Task[String] = Task {
    val request = new Request.Builder()
      .url(s"$RootUrl/$lang/pages/menu/$category/")
      .get()
      .build()

    val response = client.newCall(request).execute()

    response.body().string()
  }.timeout(timeout)


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

object AndysHttpClient {

  implicit class MapLike[A](val map: Map[String, A]) extends AnyVal {

    def asFormData: HttpEntity.Strict = {
      FormData(map.mapValues(asHttpEntity)).toEntity
    }

    private def asHttpEntity(a: A): HttpEntity.Strict = {
      HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString(a.toString))
    }
  }
}