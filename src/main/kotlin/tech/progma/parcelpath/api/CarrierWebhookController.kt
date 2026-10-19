package tech.progma.parcelpath.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tech.progma.parcelpath.service.CarrierWebhookService

@RestController
@RequestMapping("/api/v1/carrier-webhooks")
@Tag(name = "Carrier webhooks", description = "Idempotent event ingestion from carriers")
class CarrierWebhookController(
    private val service: CarrierWebhookService,
) {
    @PostMapping("/events")
    @Operation(summary = "Ingest a carrier scan. 201 on first delivery, 200 with Idempotent-Replayed: true on retries")
    fun ingest(
        @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) idempotencyKey: String,
        @Valid @RequestBody request: CarrierEventRequest,
    ): ResponseEntity<TrackingEventResponse> {
        val recorded = service.ingest(idempotencyKey, request.trackingNumber!!, request.toCommand())
        val status = if (recorded.replayed) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity
            .status(status)
            .header("Idempotent-Replayed", recorded.replayed.toString())
            .body(TrackingEventResponse.of(recorded.event))
    }
}
