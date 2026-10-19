package tech.progma.parcelpath.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import tech.progma.parcelpath.persistence.ShipmentRepository
import tech.progma.parcelpath.persistence.TrackingEventRepository

@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestSupport {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var shipments: ShipmentRepository

    @Autowired
    lateinit var events: TrackingEventRepository

    @BeforeEach
    fun cleanDatabase() {
        events.deleteAllInBatch()
        shipments.deleteAllInBatch()
    }

    fun createShipment(
        serviceLevel: String = "STANDARD",
        destinationCountry: String = "TR",
    ): JsonNode =
        mockMvc
            .post("/api/v1/shipments") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "internalReference": "ORDER-1001",
                      "senderName": "Bazaar Books",
                      "recipientName": "Leyla Demir",
                      "originCity": "Istanbul",
                      "originCountry": "TR",
                      "destinationCity": "Izmir",
                      "destinationCountry": "$destinationCountry",
                      "serviceLevel": "$serviceLevel",
                      "weightGrams": 1200
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }
            .json()

    fun recordEvent(
        shipmentId: String,
        type: String,
        ifMatch: String? = null,
        occurredAt: String = "2026-03-05T10:00:00Z",
    ): ResultActionsDsl =
        mockMvc.post("/api/v1/shipments/$shipmentId/events") {
            contentType = MediaType.APPLICATION_JSON
            ifMatch?.let { header("If-Match", it) }
            content = eventJson(type, occurredAt)
        }

    fun eventJson(
        type: String,
        occurredAt: String = "2026-03-05T10:00:00Z",
        city: String = "Istanbul",
        trackingNumber: String? = null,
    ): String {
        val tracking = trackingNumber?.let { "\"trackingNumber\": \"$it\"," } ?: ""
        return """
            {
              $tracking
              "type": "$type",
              "location": { "city": "$city", "country": "TR", "latitude": 41.01, "longitude": 28.97 },
              "occurredAt": "$occurredAt",
              "description": "scan"
            }
            """.trimIndent()
    }

    fun ResultActionsDsl.json(): JsonNode = objectMapper.readTree(andReturn().response.contentAsString)
}
