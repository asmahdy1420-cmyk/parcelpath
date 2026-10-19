package tech.progma.parcelpath.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShipmentStateMachineTest {
    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource(
        "CREATED, PICKED_UP, PICKED_UP",
        "CREATED, CANCELLED, CANCELLED",
        "PICKED_UP, IN_TRANSIT, IN_TRANSIT",
        "IN_TRANSIT, IN_TRANSIT, IN_TRANSIT",
        "IN_TRANSIT, OUT_FOR_DELIVERY, OUT_FOR_DELIVERY",
        "OUT_FOR_DELIVERY, DELIVERED, DELIVERED",
        "OUT_FOR_DELIVERY, DELIVERY_FAILED, FAILED_ATTEMPT",
        "FAILED_ATTEMPT, OUT_FOR_DELIVERY, OUT_FOR_DELIVERY",
        "FAILED_ATTEMPT, RETURNED, RETURNED",
    )
    fun `allowed transitions move to the expected status`(
        from: ShipmentStatus,
        event: TrackingEventType,
        expected: ShipmentStatus,
    ) {
        val result = ShipmentStateMachine.apply(ShipmentState(from, failedAttempts = 1), event)

        assertIs<TransitionResult.Applied>(result)
        assertEquals(expected, result.to)
    }

    @ParameterizedTest(name = "{0} + {1} is rejected")
    @CsvSource(
        "CREATED, DELIVERED",
        "CREATED, IN_TRANSIT",
        "PICKED_UP, CANCELLED",
        "IN_TRANSIT, CANCELLED",
        "IN_TRANSIT, DELIVERED",
        "OUT_FOR_DELIVERY, CANCELLED",
        "OUT_FOR_DELIVERY, RETURNED",
        "FAILED_ATTEMPT, DELIVERED",
    )
    fun `invalid transitions are rejected with a reason`(
        from: ShipmentStatus,
        event: TrackingEventType,
    ) {
        val result = ShipmentStateMachine.apply(ShipmentState(from), event)

        assertIs<TransitionResult.Rejected>(result)
        assertTrue(result.reason.isNotBlank())
    }

    @ParameterizedTest
    @EnumSource(ShipmentStatus::class, names = ["DELIVERED", "RETURNED", "CANCELLED"])
    fun `terminal statuses accept no event at all`(terminal: ShipmentStatus) {
        TrackingEventType.entries.forEach { event ->
            assertIs<TransitionResult.Rejected>(ShipmentStateMachine.apply(ShipmentState(terminal), event))
        }
    }

    @Test
    fun `a failed attempt increments the attempt counter`() {
        val result =
            ShipmentStateMachine.apply(
                ShipmentState(ShipmentStatus.OUT_FOR_DELIVERY, 1),
                TrackingEventType.DELIVERY_FAILED,
            )

        assertIs<TransitionResult.Applied>(result)
        assertEquals(2, result.failedAttempts)
    }

    @Test
    fun `after the maximum number of attempts the parcel can only be returned`() {
        val exhausted = ShipmentState(ShipmentStatus.FAILED_ATTEMPT, ShipmentStateMachine.MAX_DELIVERY_ATTEMPTS)

        assertIs<TransitionResult.Rejected>(ShipmentStateMachine.apply(exhausted, TrackingEventType.OUT_FOR_DELIVERY))
        assertIs<TransitionResult.Applied>(ShipmentStateMachine.apply(exhausted, TrackingEventType.RETURNED))
    }
}
