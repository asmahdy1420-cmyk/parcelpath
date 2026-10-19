package tech.progma.parcelpath.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import tech.progma.parcelpath.domain.EtaEstimator
import tech.progma.parcelpath.domain.ServiceLevel
import tech.progma.parcelpath.domain.ShipmentState
import tech.progma.parcelpath.domain.ShipmentStatus
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "shipment")
class ShipmentEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "tracking_number", nullable = false, updatable = false)
    val trackingNumber: String,
    @Column(name = "internal_reference")
    val internalReference: String?,
    @Column(name = "sender_name", nullable = false)
    val senderName: String,
    @Column(name = "recipient_name", nullable = false)
    val recipientName: String,
    @Column(name = "origin_city", nullable = false)
    val originCity: String,
    @Column(name = "origin_country", nullable = false)
    val originCountry: String,
    @Column(name = "destination_city", nullable = false)
    val destinationCity: String,
    @Column(name = "destination_country", nullable = false)
    val destinationCountry: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "service_level", nullable = false)
    val serviceLevel: ServiceLevel,
    @Column(name = "weight_grams", nullable = false)
    val weightGrams: Int,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ShipmentStatus = ShipmentStatus.CREATED

    @Column(name = "failed_attempts", nullable = false)
    var failedAttempts: Int = 0

    @Column(name = "picked_up_at")
    var pickedUpAt: Instant? = null

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null

    @Column(name = "last_event_at")
    var lastEventAt: Instant? = null

    @Version
    @Column(name = "version", nullable = false)
    var version: Long? = null

    fun state() = ShipmentState(status, failedAttempts)

    fun etaInput() =
        EtaEstimator.Input(
            status = status,
            serviceLevel = serviceLevel,
            originCountry = originCountry,
            destinationCountry = destinationCountry,
            createdAt = createdAt,
            pickedUpAt = pickedUpAt,
            lastEventAt = lastEventAt,
        )
}
