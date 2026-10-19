package tech.progma.parcelpath.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import tech.progma.parcelpath.persistence.EventSource

/**
 * Idempotent ingestion of carrier webhooks.
 *
 * Carriers retry on timeouts, so the same scan can arrive several times. The `Idempotency-Key`
 * header is stored with the event under a unique constraint:
 *
 * 1. key seen before with the same payload -> return the original event (a replay, no side effects);
 * 2. key seen before with a different payload -> reject, the client is misusing the key;
 * 3. two first-time deliveries racing -> the unique constraint lets exactly one insert win,
 *    and the loser is answered from the winner's row.
 *
 * This class is intentionally not transactional: the race in (3) is resolved after the losing
 * transaction has rolled back.
 */
@Service
class CarrierWebhookService(
    private val shipments: ShipmentService,
) {
    fun ingest(
        idempotencyKey: String,
        trackingNumber: String,
        command: RecordEvent,
    ): RecordedEvent {
        val carrierCommand = command.copy(source = EventSource.CARRIER)
        val hash = carrierCommand.fingerprint(trackingNumber)

        replay(idempotencyKey, hash)?.let { return it }

        val shipment = shipments.getByTrackingNumber(trackingNumber)
        return try {
            shipments.recordEvent(shipment.id, carrierCommand, idempotencyKey = idempotencyKey, requestHash = hash)
        } catch (e: DataIntegrityViolationException) {
            replay(idempotencyKey, hash) ?: throw e
        }
    }

    private fun replay(
        key: String,
        hash: String,
    ): RecordedEvent? {
        val existing = shipments.findByIdempotencyKey(key) ?: return null
        if (existing.event.requestHash != hash) throw IdempotencyKeyReusedException(key)
        return existing
    }
}
