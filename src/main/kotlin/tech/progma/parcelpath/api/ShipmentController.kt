package tech.progma.parcelpath.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import tech.progma.parcelpath.domain.ShipmentStatus
import tech.progma.parcelpath.persistence.ShipmentEntity
import tech.progma.parcelpath.service.ShipmentService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/shipments")
@Tag(name = "Shipments", description = "Operator API")
class ShipmentController(
    private val service: ShipmentService,
) {
    @PostMapping
    @Operation(summary = "Create a shipment")
    fun create(
        @Valid @RequestBody request: CreateShipmentRequest,
    ): ResponseEntity<ShipmentResponse> {
        val shipment = service.create(request.toCommand())
        val location =
            ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(shipment.id)
                .toUri()
        return ResponseEntity.created(location).withEtag(shipment).body(ShipmentResponse.of(shipment))
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a shipment; the ETag carries the version to send back in If-Match")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<ShipmentResponse> {
        val shipment = service.get(id)
        return ResponseEntity.ok().withEtag(shipment).body(ShipmentResponse.of(shipment))
    }

    @GetMapping
    @Operation(summary = "List shipments, optionally filtered by status")
    fun list(
        @RequestParam(required = false) status: ShipmentStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): PageResponse<ShipmentResponse> = PageResponse.of(service.list(status, pageable), ShipmentResponse::of)

    @PostMapping("/{id}/events")
    @Operation(summary = "Record an operator event; send If-Match to guard against concurrent updates")
    fun recordEvent(
        @PathVariable id: UUID,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @Valid @RequestBody request: TrackingEventRequest,
    ): ResponseEntity<TrackingEventResponse> {
        val recorded = service.recordEvent(id, request.toCommand(), expectedVersion = parseEtag(ifMatch))
        return ResponseEntity.status(201).withEtag(recorded.shipment).body(TrackingEventResponse.of(recorded.event))
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "List a shipment's tracking events")
    fun events(
        @PathVariable id: UUID,
        @PageableDefault(size = 50, sort = ["occurredAt"], direction = Sort.Direction.ASC) pageable: Pageable,
    ): PageResponse<TrackingEventResponse> = PageResponse.of(service.events(id, pageable), TrackingEventResponse::of)

    private fun parseEtag(value: String?): Long? =
        value?.let {
            it.removePrefix("W/").trim('"').toLongOrNull()
                ?: throw IllegalArgumentException("If-Match must be an ETag returned by this API, e.g. \"3\"")
        }
}

fun ResponseEntity.BodyBuilder.withEtag(shipment: ShipmentEntity): ResponseEntity.BodyBuilder =
    eTag("\"${shipment.version ?: 0}\"")
