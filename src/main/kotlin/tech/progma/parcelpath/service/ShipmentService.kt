package tech.progma.parcelpath.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tech.progma.parcelpath.domain.ShipmentStateMachine
import tech.progma.parcelpath.domain.ShipmentStatus
import tech.progma.parcelpath.domain.TrackingEventType
import tech.progma.parcelpath.domain.TransitionResult
import tech.progma.parcelpath.persistence.ShipmentEntity
import tech.progma.parcelpath.persistence.ShipmentRepository
import tech.progma.parcelpath.persistence.TrackingEventEntity
import tech.progma.parcelpath.persistence.TrackingEventRepository
import java.time.Clock
import java.util.UUID

@Service
class ShipmentService(
    private val shipments: ShipmentRepository,
    private val events: TrackingEventRepository,
    private val trackingNumbers: TrackingNumberGenerator,
    private val clock: Clock,
) {
    @Transactional
    fun create(command: NewShipment): ShipmentEntity =
        shipments.save(
            ShipmentEntity(
                trackingNumber = trackingNumbers.next(),
                internalReference = command.internalReference,
                senderName = command.senderName,
                recipientName = command.recipientName,
                originCity = command.originCity,
                originCountry = command.originCountry.uppercase(),
                destinationCity = command.destinationCity,
                destinationCountry = command.destinationCountry.uppercase(),
                serviceLevel = command.serviceLevel,
                weightGrams = command.weightGrams,
                createdAt = clock.instant(),
            ),
        )

    @Transactional(readOnly = true)
    fun get(id: UUID): ShipmentEntity = shipments.findByIdOrNull(id) ?: throw ShipmentNotFoundException(id.toString())

    @Transactional(readOnly = true)
    fun getByTrackingNumber(trackingNumber: String): ShipmentEntity =
        shipments.findByTrackingNumber(trackingNumber) ?: throw ShipmentNotFoundException(trackingNumber)

    @Transactional(readOnly = true)
    fun list(
        status: ShipmentStatus?,
        pageable: Pageable,
    ): Page<ShipmentEntity> =
        if (status ==
            null
        ) {
            shipments.findAll(pageable)
        } else {
            shipments.findAllByStatus(status, pageable)
        }

    @Transactional(readOnly = true)
    fun events(
        shipmentId: UUID,
        pageable: Pageable,
    ): Page<TrackingEventEntity> {
        get(shipmentId)
        return events.findAllByShipmentId(shipmentId, pageable)
    }

    @Transactional(readOnly = true)
    fun timeline(shipmentId: UUID): List<TrackingEventEntity> =
        events.findAllByShipmentIdOrderByOccurredAtAscRecordedAtAsc(shipmentId)

    @Transactional(readOnly = true)
    fun findByIdempotencyKey(key: String): RecordedEvent? =
        events.findByIdempotencyKey(key)?.let { RecordedEvent(it, get(it.shipmentId), replayed = true) }

    /**
     * Applies one event: asks the state machine, appends the event, and updates the shipment.
     * The shipment row carries a JPA `@Version`, so two writers racing on the same shipment
     * cannot both win; the loser gets an optimistic locking failure (HTTP 409).
     */
    @Transactional
    fun recordEvent(
        shipmentId: UUID,
        command: RecordEvent,
        expectedVersion: Long? = null,
        idempotencyKey: String? = null,
        requestHash: String? = null,
    ): RecordedEvent {
        val shipment = get(shipmentId)
        if (expectedVersion != null && expectedVersion != shipment.version) {
            throw ObjectOptimisticLockingFailureException(ShipmentEntity::class.java, shipmentId)
        }

        val applied =
            when (val result = ShipmentStateMachine.apply(shipment.state(), command.type)) {
                is TransitionResult.Applied -> result
                is TransitionResult.Rejected -> throw InvalidTransitionException(result)
            }

        shipment.status = applied.to
        shipment.failedAttempts = applied.failedAttempts
        shipment.lastEventAt = maxOf(shipment.lastEventAt ?: command.occurredAt, command.occurredAt)
        when (command.type) {
            TrackingEventType.PICKED_UP -> shipment.pickedUpAt = command.occurredAt
            TrackingEventType.DELIVERED -> shipment.deliveredAt = command.occurredAt
            else -> Unit
        }

        val event =
            events.saveAndFlush(
                TrackingEventEntity(
                    shipmentId = shipment.id,
                    type = command.type,
                    statusAfter = applied.to,
                    source = command.source,
                    description = command.description,
                    city = command.location.city,
                    country = command.location.country.uppercase(),
                    latitude = command.location.latitude,
                    longitude = command.location.longitude,
                    occurredAt = command.occurredAt,
                    recordedAt = clock.instant(),
                    idempotencyKey = idempotencyKey,
                    requestHash = requestHash,
                ),
            )
        return RecordedEvent(event, shipments.saveAndFlush(shipment))
    }
}
