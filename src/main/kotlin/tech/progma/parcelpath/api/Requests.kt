package tech.progma.parcelpath.api

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import tech.progma.parcelpath.domain.ServiceLevel
import tech.progma.parcelpath.domain.TrackingEventType
import tech.progma.parcelpath.service.EventLocation
import tech.progma.parcelpath.service.NewShipment
import tech.progma.parcelpath.service.RecordEvent
import java.time.Instant

private const val COUNTRY_CODE = "^[A-Za-z]{2}$"

/*
 * Request fields are nullable on purpose: a missing field then reaches bean validation and is
 * reported as a field error in the problem details, instead of failing JSON parsing.
 */

data class CreateShipmentRequest(
    @field:Size(max = 64)
    val internalReference: String? = null,
    @field:NotBlank @field:Size(max = 120)
    val senderName: String? = null,
    @field:NotBlank @field:Size(max = 120)
    val recipientName: String? = null,
    @field:NotBlank @field:Size(max = 80)
    val originCity: String? = null,
    @field:NotNull @field:Pattern(regexp = COUNTRY_CODE, message = "must be an ISO 3166-1 alpha-2 code")
    val originCountry: String? = null,
    @field:NotBlank @field:Size(max = 80)
    val destinationCity: String? = null,
    @field:NotNull @field:Pattern(regexp = COUNTRY_CODE, message = "must be an ISO 3166-1 alpha-2 code")
    val destinationCountry: String? = null,
    @field:NotNull
    val serviceLevel: ServiceLevel? = null,
    @field:NotNull @field:Positive @field:Max(70_000)
    val weightGrams: Int? = null,
) {
    fun toCommand() =
        NewShipment(
            internalReference = internalReference,
            senderName = senderName!!,
            recipientName = recipientName!!,
            originCity = originCity!!,
            originCountry = originCountry!!,
            destinationCity = destinationCity!!,
            destinationCountry = destinationCountry!!,
            serviceLevel = serviceLevel!!,
            weightGrams = weightGrams!!,
        )
}

data class LocationRequest(
    @field:NotBlank @field:Size(max = 80)
    val city: String? = null,
    @field:NotNull @field:Pattern(regexp = COUNTRY_CODE, message = "must be an ISO 3166-1 alpha-2 code")
    val country: String? = null,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: Double? = null,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: Double? = null,
) {
    fun toLocation() = EventLocation(city!!, country!!, latitude, longitude)
}

data class TrackingEventRequest(
    @field:NotNull
    val type: TrackingEventType? = null,
    @field:NotNull @field:Valid
    val location: LocationRequest? = null,
    @field:NotNull
    val occurredAt: Instant? = null,
    @field:Size(max = 255)
    val description: String? = null,
) {
    fun toCommand() = RecordEvent(type!!, location!!.toLocation(), occurredAt!!, description)
}

data class CarrierEventRequest(
    @field:NotBlank @field:Size(max = 20)
    val trackingNumber: String? = null,
    @field:NotNull
    val type: TrackingEventType? = null,
    @field:NotNull @field:Valid
    val location: LocationRequest? = null,
    @field:NotNull
    val occurredAt: Instant? = null,
    @field:Size(max = 255)
    val description: String? = null,
) {
    fun toCommand() = RecordEvent(type!!, location!!.toLocation(), occurredAt!!, description)
}
