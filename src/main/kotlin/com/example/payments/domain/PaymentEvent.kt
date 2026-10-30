package com.example.payments.domain

import java.time.Instant
import java.util.UUID

/** Domain events, published through the transactional outbox. Treat the `type` strings as a public contract. */
sealed interface PaymentEvent {
    val type: String
    val paymentId: UUID
    val occurredAt: Instant

    data class Authorised(
        override val paymentId: UUID,
        override val occurredAt: Instant,
        val amountMinor: Long,
        val currency: String,
    ) : PaymentEvent {
        override val type: String = "payment.authorised"
    }

    data class Captured(override val paymentId: UUID, override val occurredAt: Instant) : PaymentEvent {
        override val type: String = "payment.captured"
    }

    data class Cancelled(override val paymentId: UUID, override val occurredAt: Instant) : PaymentEvent {
        override val type: String = "payment.cancelled"
    }
}
