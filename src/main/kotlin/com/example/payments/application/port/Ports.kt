package com.example.payments.application.port

import com.example.payments.domain.Payment
import com.example.payments.domain.PaymentEvent
import com.example.payments.domain.PaymentStatus
import java.time.Instant
import java.util.UUID

data class PagedResult<T>(
    val items: List<T>,
    val total: Long,
)

/** Outbound port: payment persistence. */
interface PaymentRepository {
    /** Inserts unless the idempotency key already exists. Returns true when a row was inserted. */
    fun insertIfAbsent(payment: Payment): Boolean

    /** Persists [payment]; throws ConcurrentPaymentUpdateException if the stored version != [expectedVersion]. */
    fun update(payment: Payment, expectedVersion: Long)

    fun findById(id: UUID): Payment?

    fun findByIdempotencyKey(key: String): Payment?

    fun list(status: PaymentStatus?, limit: Int, offset: Int): PagedResult<Payment>
}

/** Outbound port: record a domain event in the same transaction as the state change. */
interface EventOutbox {
    fun append(event: PaymentEvent)
}

/** A serialised event ready to be handed to the message broker. */
data class EventMessage(
    val id: UUID,
    val aggregateId: UUID,
    val type: String,
    val payload: String,
    val occurredAt: Instant,
)

/** Outbound port used by the relay to read and acknowledge pending outbox rows. */
interface OutboxStore {
    /** Locks (SKIP LOCKED) and returns up to [limit] unpublished messages, oldest first. */
    fun lockUnpublished(limit: Int): List<EventMessage>

    fun markPublished(ids: List<UUID>)
}

/** Outbound port: the broker. Implement with Kafka, SNS/SQS, Service Bus... Must tolerate redelivery. */
interface EventPublisher {
    fun publish(message: EventMessage)
}
