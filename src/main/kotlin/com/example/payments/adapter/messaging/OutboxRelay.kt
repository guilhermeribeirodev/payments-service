package com.example.payments.adapter.messaging

import com.example.payments.application.port.EventPublisher
import com.example.payments.application.port.OutboxStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Polls the outbox and hands events to the [EventPublisher]. Delivery is at-least-once: a crash
 * between publish and commit re-sends the batch, so consumers must de-duplicate on the event id.
 * Rows are locked with SKIP LOCKED, so several instances can run the relay safely.
 */
@Component
@ConditionalOnProperty("payments.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelay(
    private val store: OutboxStore,
    private val publisher: EventPublisher,
    @Value("\${payments.outbox.relay.batch-size:50}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${payments.outbox.relay.poll-interval-ms:1000}")
    @Transactional
    fun relay() {
        val batch = store.lockUnpublished(batchSize)
        if (batch.isEmpty()) return

        val published = mutableListOf<UUID>()
        for (message in batch) {
            try {
                publisher.publish(message)
                published += message.id
            } catch (e: Exception) {
                // Keep order: stop at the first failure and retry on the next poll.
                log.warn("Failed to publish event {} ({}); will retry", message.id, message.type, e)
                break
            }
        }
        store.markPublished(published)
        log.debug("Relayed {}/{} outbox events", published.size, batch.size)
    }
}
