package models.responses

import models.{ContactInfo, MenuItem}

case class OrderInfoResponse(contact: Option[ContactInfo], items: Seq[MenuItem])
