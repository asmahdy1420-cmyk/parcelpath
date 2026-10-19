package tech.progma.parcelpath.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EtaEstimatorTest {
    // 2026-03-05 is a Thursday.
    private val thursday = Instant.parse("2026-03-05T10:00:00Z")

    private fun input(
        status: ShipmentStatus,
        serviceLevel: ServiceLevel = ServiceLevel.STANDARD,
        destination: String = "TR",
        lastEventAt: Instant? = thursday,
    ) = EtaEstimator.Input(
        status = status,
        serviceLevel = serviceLevel,
        originCountry = "TR",
        destinationCountry = destination,
        createdAt = thursday,
        pickedUpAt = thursday,
        lastEventAt = lastEventAt,
    )

    @Test
    fun `domestic standard shipment counts business days only`() {
        // Thursday + 4 business days skips the weekend: Fri, Mon, Tue, Wed.
        assertEquals(LocalDate.parse("2026-03-11"), EtaEstimator.estimate(input(ShipmentStatus.IN_TRANSIT)))
    }

    @Test
    fun `international express adds cross-border days`() {
        val eta = EtaEstimator.estimate(input(ShipmentStatus.PICKED_UP, ServiceLevel.EXPRESS, destination = "AE"))
        // 1 + 3 business days from Thursday: Fri, Mon, Tue, Wed.
        assertEquals(LocalDate.parse("2026-03-11"), eta)
    }

    @Test
    fun `out for delivery means today and a failed attempt means the next business day`() {
        val friday = Instant.parse("2026-03-06T09:00:00Z")

        assertEquals(
            LocalDate.parse("2026-03-06"),
            EtaEstimator.estimate(input(ShipmentStatus.OUT_FOR_DELIVERY, lastEventAt = friday)),
        )
        assertEquals(
            LocalDate.parse("2026-03-09"),
            EtaEstimator.estimate(input(ShipmentStatus.FAILED_ATTEMPT, lastEventAt = friday)),
        )
    }

    @Test
    fun `terminal shipments have no estimate`() {
        assertNull(EtaEstimator.estimate(input(ShipmentStatus.DELIVERED)))
        assertNull(EtaEstimator.estimate(input(ShipmentStatus.CANCELLED)))
    }
}
