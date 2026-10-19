package tech.progma.parcelpath.api

import org.hamcrest.Matchers.hasItems
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

class ShipmentApiTest : ApiTestSupport() {
    @Test
    fun `creating a shipment returns 201 with location, etag and a tracking number`() {
        mockMvc
            .post("/api/v1/shipments") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"senderName":"A","recipientName":"B","originCity":"Istanbul","originCountry":"tr",
                     "destinationCity":"Doha","destinationCountry":"qa","serviceLevel":"EXPRESS","weightGrams":300}
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                header { exists("Location") }
                header { string("ETag", "\"0\"") }
                jsonPath("$.status") { value("CREATED") }
                jsonPath("$.trackingNumber") { value(org.hamcrest.Matchers.matchesPattern("PP[2-9A-Z]{12}")) }
                jsonPath("$.destinationCountry") { value("QA") }
                jsonPath("$.estimatedDelivery") { exists() }
            }
    }

    @Test
    fun `validation errors come back as problem details with field errors`() {
        mockMvc
            .post("/api/v1/shipments") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"senderName":"","originCountry":"Turkey","weightGrams":-5}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.type") { value("/problems/validation-failed") }
                jsonPath("$.status") { value(400) }
                jsonPath("$.errors[*].field") {
                    value(hasItems("senderName", "recipientName", "originCountry", "weightGrams", "serviceLevel"))
                }
            }
    }

    @Test
    fun `unknown shipment is a 404 problem`() {
        mockMvc.get("/api/v1/shipments/00000000-0000-0000-0000-000000000000").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.title") { value("Shipment not found") }
        }
    }

    @Test
    fun `a full happy path ends delivered with timestamps and no ETA`() {
        val id = createShipment()["id"].asText()

        listOf(
            "PICKED_UP",
            "IN_TRANSIT",
            "IN_TRANSIT",
            "OUT_FOR_DELIVERY",
            "DELIVERY_FAILED",
            "OUT_FOR_DELIVERY",
            "DELIVERED",
        ).forEachIndexed { i, type ->
            recordEvent(id, type, occurredAt = "2026-03-0${i + 1}T10:00:00Z").andExpect { status { isCreated() } }
        }

        mockMvc.get("/api/v1/shipments/$id").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DELIVERED") }
            jsonPath("$.failedAttempts") { value(1) }
            jsonPath("$.deliveredAt") { value("2026-03-07T10:00:00Z") }
            jsonPath("$.estimatedDelivery") { doesNotExist() }
            header { string("ETag", "\"7\"") }
        }
        mockMvc.get("/api/v1/shipments/$id/events?size=3").andExpect {
            jsonPath("$.content.length()") { value(3) }
            jsonPath("$.totalElements") { value(7) }
            jsonPath("$.totalPages") { value(3) }
            jsonPath("$.content[0].type") { value("PICKED_UP") }
        }
    }

    @Test
    fun `an illegal transition is a 422 problem and changes nothing`() {
        val id = createShipment()["id"].asText()

        recordEvent(id, "DELIVERED").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.type") { value("/problems/invalid-transition") }
            jsonPath("$.currentStatus") { value("CREATED") }
            jsonPath("$.event") { value("DELIVERED") }
        }
        assertEquals(0, events.count())
    }

    @Test
    fun `cancelling after pickup is rejected`() {
        val id = createShipment()["id"].asText()
        recordEvent(id, "PICKED_UP").andExpect { status { isCreated() } }

        recordEvent(id, "CANCELLED").andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `a stale If-Match version is a 409 conflict`() {
        val id = createShipment()["id"].asText()
        recordEvent(id, "PICKED_UP", ifMatch = "\"0\"").andExpect {
            status { isCreated() }
            header { string("ETag", "\"1\"") }
        }

        // A second operator still holds version 0.
        recordEvent(id, "IN_TRANSIT", ifMatch = "\"0\"").andExpect {
            status { isConflict() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("/problems/version-conflict") }
        }
        assertEquals(1, events.count())
    }

    @Test
    fun `listing is paginated and filterable by status`() {
        repeat(3) { createShipment() }
        val picked = createShipment()["id"].asText()
        recordEvent(picked, "PICKED_UP").andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/shipments?size=2&page=0").andExpect {
            jsonPath("$.content.length()") { value(2) }
            jsonPath("$.totalElements") { value(4) }
            jsonPath("$.totalPages") { value(2) }
        }
        mockMvc.get("/api/v1/shipments?status=PICKED_UP").andExpect {
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.content[0].id") { value(picked) }
        }
    }
}
