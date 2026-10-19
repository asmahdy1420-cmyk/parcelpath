package tech.progma.parcelpath.domain

/** The part of a shipment the state machine needs to decide a transition. */
data class ShipmentState(
    val status: ShipmentStatus,
    val failedAttempts: Int = 0,
)

sealed interface TransitionResult {
    data class Applied(
        val from: ShipmentStatus,
        val to: ShipmentStatus,
        val failedAttempts: Int,
    ) : TransitionResult

    data class Rejected(
        val from: ShipmentStatus,
        val event: TrackingEventType,
        val reason: String,
    ) : TransitionResult
}

/**
 * Pure, framework-free rules for how a shipment moves through its lifecycle.
 *
 * Every (status, event) pair is handled by an exhaustive `when`, so adding a new status
 * or event type is a compile error until the rule for it is written down.
 */
object ShipmentStateMachine {
    const val MAX_DELIVERY_ATTEMPTS = 3

    fun apply(
        state: ShipmentState,
        event: TrackingEventType,
    ): TransitionResult {
        val from = state.status

        fun to(status: ShipmentStatus) = TransitionResult.Applied(from, status, state.failedAttempts)

        fun reject(reason: String) = TransitionResult.Rejected(from, event, reason)

        return when (from) {
            ShipmentStatus.CREATED ->
                when (event) {
                    TrackingEventType.PICKED_UP -> to(ShipmentStatus.PICKED_UP)
                    TrackingEventType.CANCELLED -> to(ShipmentStatus.CANCELLED)
                    else -> reject("a shipment that has not been picked up can only be picked up or cancelled")
                }

            ShipmentStatus.PICKED_UP ->
                when (event) {
                    TrackingEventType.IN_TRANSIT -> to(ShipmentStatus.IN_TRANSIT)
                    TrackingEventType.CANCELLED -> reject("a shipment cannot be cancelled after pickup")
                    else -> reject("a picked-up shipment must go in transit first")
                }

            ShipmentStatus.IN_TRANSIT ->
                when (event) {
                    // Hub-to-hub scans keep the shipment in transit; they still get recorded.
                    TrackingEventType.IN_TRANSIT -> to(ShipmentStatus.IN_TRANSIT)
                    TrackingEventType.OUT_FOR_DELIVERY -> to(ShipmentStatus.OUT_FOR_DELIVERY)
                    TrackingEventType.CANCELLED -> reject("a shipment cannot be cancelled after pickup")
                    else -> reject("an in-transit shipment can only be scanned again or sent out for delivery")
                }

            ShipmentStatus.OUT_FOR_DELIVERY ->
                when (event) {
                    TrackingEventType.DELIVERED -> to(ShipmentStatus.DELIVERED)
                    TrackingEventType.DELIVERY_FAILED ->
                        TransitionResult.Applied(from, ShipmentStatus.FAILED_ATTEMPT, state.failedAttempts + 1)
                    TrackingEventType.CANCELLED -> reject("a shipment cannot be cancelled after pickup")
                    else -> reject("a shipment out for delivery can only be delivered or fail an attempt")
                }

            ShipmentStatus.FAILED_ATTEMPT ->
                when (event) {
                    TrackingEventType.OUT_FOR_DELIVERY ->
                        if (state.failedAttempts >= MAX_DELIVERY_ATTEMPTS) {
                            reject(
                                "maximum of $MAX_DELIVERY_ATTEMPTS delivery attempts reached; the shipment must be returned",
                            )
                        } else {
                            to(ShipmentStatus.OUT_FOR_DELIVERY)
                        }
                    TrackingEventType.RETURNED -> to(ShipmentStatus.RETURNED)
                    TrackingEventType.CANCELLED -> reject("a shipment cannot be cancelled after pickup")
                    else -> reject("after a failed attempt the shipment is either retried or returned")
                }

            ShipmentStatus.DELIVERED,
            ShipmentStatus.RETURNED,
            ShipmentStatus.CANCELLED,
            -> reject("shipment is already ${from.name} and accepts no further events")
        }
    }
}
