package tech.progma.parcelpath.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import tech.progma.parcelpath.domain.ServiceLevel
import tech.progma.parcelpath.domain.ShipmentStatus
import tech.progma.parcelpath.domain.TrackingEventType
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest
class RepositoryTest {
    @Autowired
    lateinit var shipments: ShipmentRepository

    @Autowired
    lateinit var events: TrackingEventRepository

    private val now = Instant.parse("2026-03-05T10:00:00Z")

    private fun shipment(
        trackingNumber: String,
        status: ShipmentStatus = ShipmentStatus.CREATED,
        lastEventAt: Instant? = null,
    ) = ShipmentEntity(
        trackingNumber = trackingNumber,
        internalReference = null,
        senderName = "Sender",
        recipientName = "Recipient",
        originCity = "Istanbul",
        originCountry = "TR",
        destinationCity = "Ankara",
        destinationCountry = "TR",
        serviceLevel = ServiceLevel.STANDARD,
        weightGrams = 500,
        createdAt = now.minus(10, ChronoUnit.DAYS),
    ).apply {
        this.status = status
        this.lastEventAt = lastEventAt
    }

    private fun event(
        shipment: ShipmentEntity,
        key: String?,
    ) = TrackingEventEntity(
        shipmentId = shipment.id,
        type = TrackingEventType.PICKED_UP,
        statusAfter = ShipmentStatus.PICKED_UP,
        source = EventSource.CARRIER,
        description = null,
        city = "Istanbul",
        country = "TR",
        latitude = null,
        longitude = null,
        occurredAt = now,
        recordedAt = now,
        idempotencyKey = key,
        requestHash = null,
    )

    @Test
    fun `finds a shipment by tracking number`() {
        shipments.save(shipment("PPREPO0000001"))

        assertNotNull(shipments.findByTrackingNumber("PPREPO0000001"))
        assertNull(shipments.findByTrackingNumber("PPMISSING0000"))
    }

    @Test
    fun `stale query returns only active shipments without recent scans, oldest first`() {
        shipments.save(
            shipment("PPSTALE000001", ShipmentStatus.IN_TRANSIT, lastEventAt = now.minus(5, ChronoUnit.DAYS)),
        )
        shipments.save(
            shipment("PPSTALE000002", ShipmentStatus.IN_TRANSIT, lastEventAt = now.minus(1, ChronoUnit.HOURS)),
        )
        shipments.save(shipment("PPSTALE000003", ShipmentStatus.DELIVERED, lastEventAt = now.minus(9, ChronoUnit.DAYS)))
        shipments.save(shipment("PPSTALE000004", ShipmentStatus.CREATED))

        val stale =
            shipments.findStale(
                listOf(ShipmentStatus.CREATED, ShipmentStatus.PICKED_UP, ShipmentStatus.IN_TRANSIT),
                before = now.minus(2, ChronoUnit.DAYS),
                pageable = PageRequest.of(0, 10),
            )

        assertEquals(listOf("PPSTALE000004", "PPSTALE000001"), stale.content.map { it.trackingNumber })
    }

    @Test
    fun `idempotency key is unique across tracking events`() {
        val s = shipments.saveAndFlush(shipment("PPUNIQ0000001"))
        events.saveAndFlush(event(s, "carrier-key-1"))

        assertThrows<DataIntegrityViolationException> { events.saveAndFlush(event(s, "carrier-key-1")) }
    }

    @Test
    fun `events without a key do not collide`() {
        val s = shipments.saveAndFlush(shipment("PPUNIQ0000002"))
        events.saveAndFlush(event(s, null))
        events.saveAndFlush(event(s, null))

        assertEquals(2, events.countByShipmentId(s.id))
    }
}
