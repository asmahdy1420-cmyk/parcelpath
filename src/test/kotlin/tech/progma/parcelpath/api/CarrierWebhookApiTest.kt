package tech.progma.parcelpath.api

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

class CarrierWebhookApiTest : ApiTestSupport() {
    private fun deliver(
        key: String?,
        body: String,
    ) = mockMvc.post("/api/v1/carrier-webhooks/events") {
        contentType = MediaType.APPLICATION_JSON
        key?.let { header("Idempotency-Key", it) }
        content = body
    }

    @Test
    fun `a retried webhook is recorded once and replays the original event`() {
        val tracking = createShipment()["trackingNumber"].asText()
        val body = eventJson("PICKED_UP", trackingNumber = tracking)

        val first =
            deliver("scan-123", body)
                .andExpect {
                    status { isCreated() }
                    header { string("Idempotent-Replayed", "false") }
                }.json()
        val retry =
            deliver("scan-123", body)
                .andExpect {
                    status { isOk() }
                    header { string("Idempotent-Replayed", "true") }
                }.json()

        assertEquals(first["id"], retry["id"])
        assertEquals(1, events.count())
        assertEquals("PICKED_UP", shipments.findByTrackingNumber(tracking)!!.status.name)
    }

    @Test
    fun `reusing a key with a different payload is rejected`() {
        val tracking = createShipment()["trackingNumber"].asText()
        deliver("scan-456", eventJson("PICKED_UP", trackingNumber = tracking)).andExpect { status { isCreated() } }

        deliver("scan-456", eventJson("PICKED_UP", city = "Bursa", trackingNumber = tracking)).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.type") { value("/problems/idempotency-key-reused") }
        }
        assertEquals(1, events.count())
    }

    @Test
    fun `a missing Idempotency-Key header is a 400 problem`() {
        val tracking = createShipment()["trackingNumber"].asText()

        deliver(null, eventJson("PICKED_UP", trackingNumber = tracking)).andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
        }
        assertEquals(0, events.count())
    }

    @Test
    fun `an unknown tracking number is a 404 problem`() {
        deliver("scan-789", eventJson("PICKED_UP", trackingNumber = "PPDOESNOTEXIST")).andExpect {
            status { isNotFound() }
        }
    }
}
