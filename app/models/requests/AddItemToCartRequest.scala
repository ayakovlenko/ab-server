package models.requests

case class AddItemToCartRequest(item: Long, user: String, channel: String)
