package tech.progma.parcelpath.api

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals

class PublicTrackingApiTest : ApiTestSupport() {
    @Test
    fun `public tracking shows progress but hides internal fields`() {
        val created = createShipment()
        val tracking = created["trackingNumber"].asText()
        recordEvent(created["id"].asText(), "PICKED_UP").andExpect { status { isCreated() } }

        val body =
            mockMvc
                .get("/api/v1/public/tracking/${tracking.lowercase()}")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.trackingNumber") { value(tracking) }
                    jsonPath("$.status") { value("PICKED_UP") }
                    jsonPath("$.events[0].city") { value("Istanbul") }
                }.json()

        val exposed = body.fieldNames().asSequence().toSet()
        assertEquals(
            setOf(
                "trackingNumber",
                "status",
                "estimatedDelivery",
                "deliveredAt",
                "destinationCity",
                "destinationCountry",
                "events",
            ),
            exposed,
        )
        assertEquals(
            setOf("status", "description", "city", "country", "occurredAt"),
            body["events"][0].fieldNames().asSequence().toSet(),
        )
        val raw = body.toString()
        listOf("ORDER-1001", "Leyla Demir", "Bazaar Books", created["id"].asText()).forEach {
            assert(!raw.contains(it)) { "public response leaked $it" }
        }
    }

    @Test
    fun `unknown tracking number is a 404`() {
        mockMvc.get("/api/v1/public/tracking/PPNOPE").andExpect { status { isNotFound() } }
    }
}
