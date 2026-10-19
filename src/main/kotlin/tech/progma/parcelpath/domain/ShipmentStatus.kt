package tech.progma.parcelpath.domain

enum class ShipmentStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    FAILED_ATTEMPT,
    DELIVERED,
    RETURNED,
    CANCELLED,
    ;

    val isTerminal: Boolean
        get() = this == DELIVERED || this == RETURNED || this == CANCELLED
}
