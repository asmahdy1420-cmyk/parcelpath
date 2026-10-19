package tech.progma.parcelpath.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Rule-based delivery estimate. Deliberately simple and explainable:
 *
 * - transit days come from the service level, plus [INTERNATIONAL_EXTRA_DAYS] across borders;
 * - only business days count (no deliveries on Saturday or Sunday);
 * - the clock starts at pickup, or at creation while the parcel still waits for pickup;
 * - out for delivery means "today", a failed attempt means "next business day";
 * - terminal shipments have no estimate.
 */
object EtaEstimator {
    const val INTERNATIONAL_EXTRA_DAYS = 3

    data class Input(
        val status: ShipmentStatus,
        val serviceLevel: ServiceLevel,
        val originCountry: String,
        val destinationCountry: String,
        val createdAt: Instant,
        val pickedUpAt: Instant?,
        val lastEventAt: Instant?,
    )

    fun estimate(input: Input): LocalDate? {
        val transitDays =
            input.serviceLevel.domesticTransitDays +
                if (input.originCountry.equals(
                        input.destinationCountry,
                        ignoreCase = true,
                    )
                ) {
                    0
                } else {
                    INTERNATIONAL_EXTRA_DAYS
                }

        return when (input.status) {
            ShipmentStatus.CREATED -> addBusinessDays(input.createdAt.utcDate(), transitDays + 1)
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.IN_TRANSIT,
            -> addBusinessDays((input.pickedUpAt ?: input.createdAt).utcDate(), transitDays)
            ShipmentStatus.OUT_FOR_DELIVERY ->
                nextBusinessDayOnOrAfter(
                    (input.lastEventAt ?: input.createdAt).utcDate(),
                )
            ShipmentStatus.FAILED_ATTEMPT -> addBusinessDays((input.lastEventAt ?: input.createdAt).utcDate(), 1)
            ShipmentStatus.DELIVERED,
            ShipmentStatus.RETURNED,
            ShipmentStatus.CANCELLED,
            -> null
        }
    }

    internal fun addBusinessDays(
        start: LocalDate,
        days: Int,
    ): LocalDate {
        var date = start
        var remaining = days
        while (remaining > 0) {
            date = date.plusDays(1)
            if (date.isBusinessDay()) remaining--
        }
        return date
    }

    private fun nextBusinessDayOnOrAfter(date: LocalDate): LocalDate =
        if (date.isBusinessDay()) date else addBusinessDays(date, 1)

    private fun LocalDate.isBusinessDay() = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY

    private fun Instant.utcDate(): LocalDate = atZone(ZoneOffset.UTC).toLocalDate()
}
