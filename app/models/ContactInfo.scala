package models

case class ContactInfo(name: String,
                       city: Int,
                       street: String,
                       house: Int,
                       phone: String,
                       email: Option[String] = None,
                       discount: Option[String] = None)
