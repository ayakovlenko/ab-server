package actors

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import models.{ContactInfo, OrderInfo}
import org.scalatest.{FlatSpecLike, Matchers}

class OrderTest extends TestKit(ActorSystem("test"))
  with FlatSpecLike
  with Matchers
  with StopSystemAfterAll {

  it should "add item to order" in {

    val order = TestActorRef[Order]

    order ! Order.AddItem(1)
    order ! Order.AddItem(2)

    order.underlyingActor.orderInfo match {
      case OrderInfo(_, items) =>
        items should contain allOf (1, 2)
    }
  }

  it should "add contact info to order" in {

    val order = TestActorRef[Order]

    val contactInfo = ContactInfo(
      "Ion Doe",
      2,
      "Stefan cel Mare",
      202,
      "+373 (69) 123456",
      None
    )

    order ! Order.UpdateContactInfo(contactInfo)

    order.underlyingActor.orderInfo match {
      case OrderInfo(contact, _) =>
        contact shouldBe Some(contactInfo)
    }
  }
}
