package tech.progma.parcelpath.service

import tech.progma.parcelpath.domain.TransitionResult

class ShipmentNotFoundException(
    reference: String,
) : RuntimeException("shipment $reference was not found")

class InvalidTransitionException(
    val rejection: TransitionResult.Rejected,
) : RuntimeException("cannot apply ${rejection.event} to a shipment in status ${rejection.from}: ${rejection.reason}")

class IdempotencyKeyReusedException(
    key: String,
) : RuntimeException("Idempotency-Key '$key' was already used for a different request")
