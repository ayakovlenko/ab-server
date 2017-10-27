package errors

sealed trait OrderPlacementError

case object OrderHasNoItems extends OrderPlacementError

case object OrderHasNoContactInfo extends OrderPlacementError

case object OrderNotFound extends OrderPlacementError

case object UnknownOrderPlacementError extends OrderPlacementError
