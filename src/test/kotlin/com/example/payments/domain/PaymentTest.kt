package com.example.payments.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PaymentTest {
    private val t0 = Instant.parse("2026-01-01T10:00:00Z")
    private val t1 = t0.plusSeconds(60)

    private fun authorised() = Payment.authorise(UUID.randomUUID(), Money(1000, "GBP"), "order-1", "key-12345678", t0)

    @Test
    fun `a new payment is authorised at version 0`() {
        val payment = authorised()
        assertThat(payment.status).isEqualTo(PaymentStatus.AUTHORISED)
        assertThat(payment.version).isZero()
    }

    @Test
    fun `capture moves to CAPTURED, bumps version and updatedAt`() {
        val captured = authorised().capture(t1)
        assertThat(captured.status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(captured.version).isEqualTo(1)
        assertThat(captured.updatedAt).isEqualTo(t1)
        assertThat(captured.createdAt).isEqualTo(t0)
    }

    @Test
    fun `cancel moves to CANCELLED`() {
        assertThat(authorised().cancel(t1).status).isEqualTo(PaymentStatus.CANCELLED)
    }

    @Test
    fun `terminal states cannot transition again`() {
        val captured = authorised().capture(t1)
        assertThatThrownBy { captured.cancel(t1) }.isInstanceOf(InvalidPaymentTransitionException::class.java)
        assertThatThrownBy { captured.capture(t1) }.isInstanceOf(InvalidPaymentTransitionException::class.java)

        val cancelled = authorised().cancel(t1)
        assertThatThrownBy { cancelled.capture(t1) }.isInstanceOf(InvalidPaymentTransitionException::class.java)
    }

    @Test
    fun `sameRequestAs compares amount, currency and reference`() {
        val payment = authorised()
        assertThat(payment.sameRequestAs(Money(1000, "GBP"), "order-1")).isTrue()
        assertThat(payment.sameRequestAs(Money(1001, "GBP"), "order-1")).isFalse()
        assertThat(payment.sameRequestAs(Money(1000, "GBP"), "order-2")).isFalse()
    }
}
