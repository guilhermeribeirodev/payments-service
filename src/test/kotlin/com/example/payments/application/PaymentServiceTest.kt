package com.example.payments.application

import com.example.payments.application.port.EventOutbox
import com.example.payments.application.port.PagedResult
import com.example.payments.application.port.PaymentRepository
import com.example.payments.domain.ConcurrentPaymentUpdateException
import com.example.payments.domain.IdempotencyKeyReuseException
import com.example.payments.domain.InvalidPaymentException
import com.example.payments.domain.InvalidPaymentTransitionException
import com.example.payments.domain.Payment
import com.example.payments.domain.PaymentEvent
import com.example.payments.domain.PaymentNotFoundException
import com.example.payments.domain.PaymentStatus
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Pure unit tests: the ports are replaced by in-memory fakes, no Spring, no database. */
class PaymentServiceTest {
    private val repo = InMemoryPaymentRepository()
    private val outbox = RecordingOutbox()
    private val meters = SimpleMeterRegistry()
    private val clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)
    private val service = PaymentService(repo, outbox, clock, meters)

    private fun command(key: String = "key-12345678", amount: Long = 1500, reference: String = "order-1") =
        AuthorisePaymentCommand(amount, "GBP", reference, key)

    @Test
    fun `authorise creates a payment and records an event`() {
        val result = service.authorise(command())

        assertThat(result).isInstanceOf(AuthoriseResult.Created::class.java)
        assertThat(result.payment.status).isEqualTo(PaymentStatus.AUTHORISED)
        assertThat(outbox.events).singleElement().isInstanceOf(PaymentEvent.Authorised::class.java)
        assertThat(meters.counter("payments.transitions", "to", "authorised").count()).isEqualTo(1.0)
    }

    @Test
    fun `replaying the same request returns the original payment without a second event`() {
        val first = service.authorise(command())
        val second = service.authorise(command())

        assertThat(second).isInstanceOf(AuthoriseResult.Replayed::class.java)
        assertThat(second.payment.id).isEqualTo(first.payment.id)
        assertThat(outbox.events).hasSize(1)
    }

    @Test
    fun `reusing a key with a different request is rejected`() {
        service.authorise(command())
        assertThatThrownBy { service.authorise(command(amount = 9999)) }
            .isInstanceOf(IdempotencyKeyReuseException::class.java)
    }

    @Test
    fun `invalid money is rejected before anything is stored`() {
        assertThatThrownBy { service.authorise(command(amount = 0)) }.isInstanceOf(InvalidPaymentException::class.java)
        assertThat(repo.all()).isEmpty()
        assertThat(outbox.events).isEmpty()
    }

    @Test
    fun `capture transitions the payment and records an event`() {
        val id = service.authorise(command()).payment.id

        val captured = service.capture(id)

        assertThat(captured.status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(repo.findById(id)!!.status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(outbox.events.last()).isInstanceOf(PaymentEvent.Captured::class.java)
    }

    @Test
    fun `cancelling a captured payment fails and records nothing`() {
        val id = service.authorise(command()).payment.id
        service.capture(id)
        val eventsBefore = outbox.events.size

        assertThatThrownBy { service.cancel(id) }.isInstanceOf(InvalidPaymentTransitionException::class.java)
        assertThat(outbox.events).hasSize(eventsBefore)
    }

    @Test
    fun `unknown payments are reported as not found`() {
        assertThatThrownBy { service.get(UUID.randomUUID()) }.isInstanceOf(PaymentNotFoundException::class.java)
        assertThatThrownBy { service.capture(UUID.randomUUID()) }.isInstanceOf(PaymentNotFoundException::class.java)
    }

    @Test
    fun `optimistic locking conflicts surface as ConcurrentPaymentUpdateException`() {
        val id = service.authorise(command()).payment.id
        repo.failNextUpdateWithConflict = true

        assertThatThrownBy { service.capture(id) }.isInstanceOf(ConcurrentPaymentUpdateException::class.java)
    }

    @Test
    fun `list filters by status and paginates newest first`() {
        val a = service.authorise(command("key-00000001", reference = "a")).payment
        service.authorise(command("key-00000002", reference = "b"))
        service.capture(a.id)

        val captured = service.list(PaymentStatus.CAPTURED, limit = 10, offset = 0)
        assertThat(captured.total).isEqualTo(1)
        assertThat(captured.items.map { it.id }).containsExactly(a.id)

        assertThat(service.list(null, limit = 1, offset = 0).items).hasSize(1)
        assertThat(service.list(null, limit = 1, offset = 0).total).isEqualTo(2)
    }

    // ---- fakes -----------------------------------------------------------------------------

    private class InMemoryPaymentRepository : PaymentRepository {
        private val byId = linkedMapOf<UUID, Payment>()
        var failNextUpdateWithConflict = false

        fun all() = byId.values.toList()

        override fun insertIfAbsent(payment: Payment): Boolean {
            if (byId.values.any { it.idempotencyKey == payment.idempotencyKey }) return false
            byId[payment.id] = payment
            return true
        }

        override fun update(payment: Payment, expectedVersion: Long) {
            if (failNextUpdateWithConflict) throw ConcurrentPaymentUpdateException(payment.id)
            check(byId[payment.id]?.version == expectedVersion) { "version mismatch" }
            byId[payment.id] = payment
        }

        override fun findById(id: UUID) = byId[id]

        override fun findByIdempotencyKey(key: String) = byId.values.firstOrNull { it.idempotencyKey == key }

        override fun list(status: PaymentStatus?, limit: Int, offset: Int): PagedResult<Payment> {
            val matching = byId.values.filter { status == null || it.status == status }.reversed()
            return PagedResult(matching.drop(offset).take(limit), matching.size.toLong())
        }
    }

    private class RecordingOutbox : EventOutbox {
        val events = mutableListOf<PaymentEvent>()

        override fun append(event: PaymentEvent) {
            events += event
        }
    }
}
