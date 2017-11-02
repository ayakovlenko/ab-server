package actors

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import org.scalatest.{FlatSpecLike, Matchers}

class OrdersActorSpec extends TestKit(ActorSystem("test"))
  with FlatSpecLike
  with Matchers
  with StopSystemAfterAll  {

  it should "create an actor per user" in {

    val orders = TestActorRef[Orders]

    orders ! Orders.AddItem("telegram", "1", 1)
    orders ! Orders.AddItem("telegram", "1", 2)
    orders ! Orders.AddItem("telegram", "2", 1)
    orders ! Orders.AddItem("telegram", "3", 1)

    orders.children.size shouldBe 3
  }
}
