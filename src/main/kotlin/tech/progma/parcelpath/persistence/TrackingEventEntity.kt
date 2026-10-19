package tech.progma.parcelpath.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import tech.progma.parcelpath.domain.ShipmentStatus
import tech.progma.parcelpath.domain.TrackingEventType
import java.time.Instant
import java.util.UUID

enum class EventSource { CARRIER, OPERATOR }

/** One immutable row per scan. Hibernate never issues an UPDATE for this entity. */
@Entity
@Immutable
@Table(name = "tracking_event")
class TrackingEventEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id", nullable = false)
    val shipmentId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    val type: TrackingEventType,
    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", nullable = false)
    val statusAfter: ShipmentStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    val source: EventSource,
    @Column(name = "description")
    val description: String?,
    @Column(name = "city", nullable = false)
    val city: String,
    @Column(name = "country", nullable = false)
    val country: String,
    @Column(name = "latitude")
    val latitude: Double?,
    @Column(name = "longitude")
    val longitude: Double?,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
    @Column(name = "recorded_at", nullable = false)
    val recordedAt: Instant,
    @Column(name = "idempotency_key")
    val idempotencyKey: String?,
    @Column(name = "request_hash")
    val requestHash: String?,
)
