package com.example.payments.application

import com.example.payments.application.port.EventOutbox
import com.example.payments.application.port.PagedResult
import com.example.payments.application.port.PaymentRepository
import com.example.payments.domain.IdempotencyKeyReuseException
import com.example.payments.domain.Money
import com.example.payments.domain.Payment
import com.example.payments.domain.PaymentEvent
import com.example.payments.domain.PaymentNotFoundException
import com.example.payments.domain.PaymentStatus
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.annotation.Observed
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

data class AuthorisePaymentCommand(
    val amountMinor: Long,
    val currency: String,
    val reference: String,
    val idempotencyKey: String,
)

sealed interface AuthoriseResult {
    val payment: Payment

    data class Created(override val payment: Payment) : AuthoriseResult

    data class Replayed(override val payment: Payment) : AuthoriseResult
}

@Service
class PaymentService(
    private val payments: PaymentRepository,
    private val outbox: EventOutbox,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
) {
    @Observed(name = "payments.authorise")
    @Transactional
    fun authorise(command: AuthorisePaymentCommand): AuthoriseResult {
        val money = Money(command.amountMinor, command.currency)
        val candidate = Payment.authorise(
            id = UUID.randomUUID(),
            money = money,
            reference = command.reference,
            idempotencyKey = command.idempotencyKey,
            now = clock.instant(),
        )

        if (payments.insertIfAbsent(candidate)) {
            outbox.append(PaymentEvent.Authorised(candidate.id, candidate.createdAt, money.amountMinor, money.currency))
            countTransition(PaymentStatus.AUTHORISED)
            return AuthoriseResult.Created(candidate)
        }

        val existing = checkNotNull(payments.findByIdempotencyKey(command.idempotencyKey)) {
            "Idempotency key conflict reported but no payment found"
        }
        if (!existing.sameRequestAs(money, command.reference)) {
            throw IdempotencyKeyReuseException(command.idempotencyKey)
        }
        return AuthoriseResult.Replayed(existing)
    }

    @Observed(name = "payments.capture")
    @Transactional
    fun capture(id: UUID): Payment =
        transition(id, Payment::capture) { PaymentEvent.Captured(it.id, it.updatedAt) }

    @Observed(name = "payments.cancel")
    @Transactional
    fun cancel(id: UUID): Payment =
        transition(id, Payment::cancel) { PaymentEvent.Cancelled(it.id, it.updatedAt) }

    @Transactional(readOnly = true)
    fun get(id: UUID): Payment = payments.findById(id) ?: throw PaymentNotFoundException(id)

    @Transactional(readOnly = true)
    fun list(status: PaymentStatus?, limit: Int, offset: Int): PagedResult<Payment> =
        payments.list(status, limit, offset)

    private fun transition(
        id: UUID,
        change: (Payment, java.time.Instant) -> Payment,
        event: (Payment) -> PaymentEvent,
    ): Payment {
        val current = payments.findById(id) ?: throw PaymentNotFoundException(id)
        val updated = change(current, clock.instant())
        payments.update(updated, expectedVersion = current.version)
        outbox.append(event(updated))
        countTransition(updated.status)
        return updated
    }

    private fun countTransition(to: PaymentStatus) {
        meterRegistry.counter("payments.transitions", "to", to.name.lowercase()).increment()
    }
}
