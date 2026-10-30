package com.example.payments.domain

import java.time.Instant
import java.util.UUID

enum class PaymentStatus { AUTHORISED, CAPTURED, CANCELLED }

/**
 * The payment aggregate. Immutable: every transition returns a new instance with `version` bumped,
 * which the repository uses for optimistic locking.
 */
data class Payment(
    val id: UUID,
    val money: Money,
    val reference: String,
    val status: PaymentStatus,
    val idempotencyKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
) {
    fun capture(now: Instant): Payment = transitionTo(PaymentStatus.CAPTURED, now)

    fun cancel(now: Instant): Payment = transitionTo(PaymentStatus.CANCELLED, now)

    /** True when [other] describes the same request this payment was created from (idempotent replay). */
    fun sameRequestAs(money: Money, reference: String): Boolean = this.money == money && this.reference == reference

    private fun transitionTo(target: PaymentStatus, now: Instant): Payment {
        if (status != PaymentStatus.AUTHORISED) {
            throw InvalidPaymentTransitionException(id, status, target)
        }
        return copy(status = target, updatedAt = now, version = version + 1)
    }

    companion object {
        fun authorise(id: UUID, money: Money, reference: String, idempotencyKey: String, now: Instant): Payment =
            Payment(
                id = id,
                money = money,
                reference = reference,
                status = PaymentStatus.AUTHORISED,
                idempotencyKey = idempotencyKey,
                createdAt = now,
                updatedAt = now,
                version = 0,
            )
    }
}
