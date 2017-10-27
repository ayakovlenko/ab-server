package models.requests

import models.ContactInfo

case class UpdateContactInfoRequest(contact: ContactInfo, user: String, channel: String)
