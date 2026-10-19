package tech.progma.parcelpath.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.web.servlet.get
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertFailsWith

class PlatformTest : ApiTestSupport() {
    @Autowired
    lateinit var tx: TransactionTemplate

    @Test
    fun `JPA version column rejects a write based on a stale read`() {
        val id = java.util.UUID.fromString(createShipment()["id"].asText())
        val staleCopy = tx.execute { shipments.findById(id).orElseThrow() }!!

        tx.executeWithoutResult {
            shipments.findById(id).orElseThrow().failedAttempts = 1
        }

        staleCopy.failedAttempts = 2
        assertFailsWith<ObjectOptimisticLockingFailureException> { shipments.save(staleCopy) }
    }

    @Test
    fun `actuator health and the OpenAPI document are exposed`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.paths['/api/v1/public/tracking/{trackingNumber}']") { exists() }
        }
    }
}
