package controllers

import javax.inject.{Inject, Singleton}

import models.requests.{AddItemToCartRequest, CartRequest, UpdateContactInfoRequest}
import models.{ContactInfo, MenuItem, OrderInfo}
import monix.execution.Scheduler
import play.api.libs.json.{Format, Json, Reads, Writes}
import play.api.mvc._
import services.AndysService

import scala.collection.immutable.ListMap
import scala.concurrent.Future

@Singleton
class AndysController @Inject()(cc: ControllerComponents,
                                andysService: AndysService) extends AbstractController(cc) {

  import AndysController._

  private implicit val scheduler: Scheduler = Scheduler(cc.executionContext)

  def pizzas(lang: String): Action[AnyContent] = Action.async {
    andysService.pizzas(lang).map { pizzas =>
      Ok(Json.toJson(pizzas))
    }.runAsync
  }

  def addToCart(): Action[AnyContent] = Action.async { implicit req =>

    req.body.asJson.map(_.as[AddItemToCartRequest]) match {
      case None => Future(UnprocessableEntity)
      case Some(AddItemToCartRequest(item, user, channel)) =>
        andysService.addItemToOrder(item, user, channel).map { orderInfo =>
          Ok(Json.toJson(orderInfo))
        }.runAsync
    }
  }

  def addContactInfo(): Action[AnyContent] = Action.async { implicit req =>
    req.body.asJson.map(_.as[UpdateContactInfoRequest]) match {
      case None => Future(UnprocessableEntity)
      case Some(UpdateContactInfoRequest(contactInfo, user, channel)) =>
        andysService.updateContactInfo(contactInfo, user, channel).map { orderInfo =>
          Ok(Json.toJson(orderInfo))
        }.runAsync
    }
  }

  def resetCart(): Action[AnyContent] = Action.async { implicit req =>
    req.body.asJson.map(_.as[CartRequest]) match {
      case None => Future(UnprocessableEntity)
      case Some(CartRequest(user, channel)) =>
        andysService.resetCart(user, channel).map(_ => Ok).runAsync
    }
  }

  def completeOrder(): Action[AnyContent] = TODO

  def placeOrder(): Action[AnyContent] = Action.async { implicit req =>
    req.body.asJson.map(_.as[CartRequest]) match {
      case None => Future(UnprocessableEntity)
      case Some(CartRequest(user, channel)) =>
        andysService.placeOrder(user, channel).map {
          case Left(error) => BadRequest(error.toString)
          case Right(sessionId) => Ok(sessionId)
        }.runAsync
    }
  }

  def getConfirmation(user: String, channel: String, lang: String): Action[AnyContent] = Action.async {
    andysService.getConfirmation(user, channel, lang).map(attrs =>
      Ok(Json.toJson(ListMap(attrs: _*)))
    ).runAsync
  }
}

object AndysController {

  private implicit lazy val pizzaWrites: Writes[MenuItem] = Json.writes[MenuItem]

  private implicit lazy val orderInfoWrites: Writes[OrderInfo] = Json.writes[OrderInfo]

  private implicit lazy val contactInfoFormat: Format[ContactInfo] = Json.format[ContactInfo]

  private implicit lazy val addItemToCartRequestReads: Reads[AddItemToCartRequest] = Json.reads[AddItemToCartRequest]

  private implicit lazy val updateContactInfoRequestReads: Reads[UpdateContactInfoRequest] =
    Json.reads[UpdateContactInfoRequest]

  private implicit lazy val resetCartRequest: Reads[CartRequest] = Json.reads[CartRequest]

  private implicit lazy val optionStringReads: Reads[Option[String]] = Reads.optionWithNull
}
