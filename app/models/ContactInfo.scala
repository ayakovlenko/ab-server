package models

case class ContactInfo(name: String,
                       city: Int,
                       street: String,
                       house: Int,
                       phone: String,
                       discount: Option[String] = None)
