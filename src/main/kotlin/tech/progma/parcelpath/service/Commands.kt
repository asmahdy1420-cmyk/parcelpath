package tech.progma.parcelpath.service

import tech.progma.parcelpath.domain.ServiceLevel
import tech.progma.parcelpath.domain.TrackingEventType
import tech.progma.parcelpath.persistence.EventSource
import tech.progma.parcelpath.persistence.ShipmentEntity
import tech.progma.parcelpath.persistence.TrackingEventEntity
import java.security.MessageDigest
import java.time.Instant

data class NewShipment(
    val internalReference: String?,
    val senderName: String,
    val recipientName: String,
    val originCity: String,
    val originCountry: String,
    val destinationCity: String,
    val destinationCountry: String,
    val serviceLevel: ServiceLevel,
    val weightGrams: Int,
)

data class EventLocation(
    val city: String,
    val country: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class RecordEvent(
    val type: TrackingEventType,
    val location: EventLocation,
    val occurredAt: Instant,
    val description: String? = null,
    val source: EventSource = EventSource.OPERATOR,
) {
    /** Stable fingerprint of the payload, used to detect an Idempotency-Key reused for a different request. */
    fun fingerprint(trackingNumber: String): String {
        val canonical =
            listOf(
                trackingNumber,
                type.name,
                location.city,
                location.country.uppercase(),
                location.latitude?.toString().orEmpty(),
                location.longitude?.toString().orEmpty(),
                occurredAt.toString(),
                description.orEmpty(),
            ).joinToString("\u001F")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class RecordedEvent(
    val event: TrackingEventEntity,
    val shipment: ShipmentEntity,
    val replayed: Boolean = false,
)
