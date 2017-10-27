package models

case class OrderInfo(contact: Option[ContactInfo], items: Seq[Long])
