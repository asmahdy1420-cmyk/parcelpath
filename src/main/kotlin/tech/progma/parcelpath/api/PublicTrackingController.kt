package tech.progma.parcelpath.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tech.progma.parcelpath.service.ShipmentService

@RestController
@RequestMapping("/api/v1/public/tracking")
@Tag(name = "Public tracking", description = "What a recipient sees with only a tracking number")
class PublicTrackingController(
    private val service: ShipmentService,
) {
    @GetMapping("/{trackingNumber}")
    @Operation(summary = "Track a parcel by tracking number")
    fun track(
        @PathVariable trackingNumber: String,
    ): PublicTrackingResponse {
        val shipment = service.getByTrackingNumber(trackingNumber.uppercase())
        return PublicTrackingResponse.of(shipment, service.timeline(shipment.id))
    }
}
