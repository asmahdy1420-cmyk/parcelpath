package tech.progma.parcelpath.domain

/**
 * Something that happened to a parcel, as reported by a carrier scan or an operator.
 * The state machine decides what (if anything) each type does to a shipment's status.
 */
enum class TrackingEventType {
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERY_FAILED,
    DELIVERED,
    RETURNED,
    CANCELLED,
}
