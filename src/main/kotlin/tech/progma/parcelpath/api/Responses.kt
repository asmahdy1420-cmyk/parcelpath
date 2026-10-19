package tech.progma.parcelpath.api

import org.springframework.data.domain.Page
import tech.progma.parcelpath.domain.EtaEstimator
import tech.progma.parcelpath.domain.ServiceLevel
import tech.progma.parcelpath.domain.ShipmentStatus
import tech.progma.parcelpath.domain.TrackingEventType
import tech.progma.parcelpath.persistence.EventSource
import tech.progma.parcelpath.persistence.ShipmentEntity
import tech.progma.parcelpath.persistence.TrackingEventEntity
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <E, T> of(
            page: Page<E>,
            mapper: (E) -> T,
        ) = PageResponse(page.content.map(mapper), page.number, page.size, page.totalElements, page.totalPages)
    }
}

/** Internal (operator) view: everything, including the optimistic-locking version. */
data class ShipmentResponse(
    val id: UUID,
    val trackingNumber: String,
    val internalReference: String?,
    val senderName: String,
    val recipientName: String,
    val originCity: String,
    val originCountry: String,
    val destinationCity: String,
    val destinationCountry: String,
    val serviceLevel: ServiceLevel,
    val weightGrams: Int,
    val status: ShipmentStatus,
    val failedAttempts: Int,
    val estimatedDelivery: LocalDate?,
    val createdAt: Instant,
    val pickedUpAt: Instant?,
    val deliveredAt: Instant?,
    val lastEventAt: Instant?,
    val version: Long,
) {
    companion object {
        fun of(s: ShipmentEntity) =
            ShipmentResponse(
                id = s.id,
                trackingNumber = s.trackingNumber,
                internalReference = s.internalReference,
                senderName = s.senderName,
                recipientName = s.recipientName,
                originCity = s.originCity,
                originCountry = s.originCountry,
                destinationCity = s.destinationCity,
                destinationCountry = s.destinationCountry,
                serviceLevel = s.serviceLevel,
                weightGrams = s.weightGrams,
                status = s.status,
                failedAttempts = s.failedAttempts,
                estimatedDelivery = EtaEstimator.estimate(s.etaInput()),
                createdAt = s.createdAt,
                pickedUpAt = s.pickedUpAt,
                deliveredAt = s.deliveredAt,
                lastEventAt = s.lastEventAt,
                version = s.version ?: 0,
            )
    }
}

data class TrackingEventResponse(
    val id: UUID,
    val shipmentId: UUID,
    val type: TrackingEventType,
    val statusAfter: ShipmentStatus,
    val source: EventSource,
    val description: String?,
    val city: String,
    val country: String,
    val latitude: Double?,
    val longitude: Double?,
    val occurredAt: Instant,
    val recordedAt: Instant,
) {
    companion object {
        fun of(e: TrackingEventEntity) =
            TrackingEventResponse(
                id = e.id,
                shipmentId = e.shipmentId,
                type = e.type,
                statusAfter = e.statusAfter,
                source = e.source,
                description = e.description,
                city = e.city,
                country = e.country,
                latitude = e.latitude,
                longitude = e.longitude,
                occurredAt = e.occurredAt,
                recordedAt = e.recordedAt,
            )
    }
}

/**
 * Public view for recipients. Built field by field from an allow-list, so a new internal
 * column can never leak by accident: no ids, names, references, weights, versions or keys.
 */
data class PublicTrackingResponse(
    val trackingNumber: String,
    val status: ShipmentStatus,
    val estimatedDelivery: LocalDate?,
    val deliveredAt: Instant?,
    val destinationCity: String,
    val destinationCountry: String,
    val events: List<PublicTrackingEvent>,
) {
    data class PublicTrackingEvent(
        val status: ShipmentStatus,
        val description: String?,
        val city: String,
        val country: String,
        val occurredAt: Instant,
    )

    companion object {
        fun of(
            s: ShipmentEntity,
            timeline: List<TrackingEventEntity>,
        ) = PublicTrackingResponse(
            trackingNumber = s.trackingNumber,
            status = s.status,
            estimatedDelivery = EtaEstimator.estimate(s.etaInput()),
            deliveredAt = s.deliveredAt,
            destinationCity = s.destinationCity,
            destinationCountry = s.destinationCountry,
            events =
                timeline.map {
                    PublicTrackingEvent(
                        it.statusAfter,
                        it.description,
                        it.city,
                        it.country,
                        it.occurredAt,
                    )
                },
        )
    }
}
