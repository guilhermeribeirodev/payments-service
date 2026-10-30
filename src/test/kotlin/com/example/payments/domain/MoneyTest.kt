package com.example.payments.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MoneyTest {
    @Test
    fun `accepts a positive amount with a valid currency`() {
        assertThat(Money(1500, "GBP")).isEqualTo(Money(1500, "GBP"))
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1, Long.MIN_VALUE])
    fun `rejects zero and negative amounts`(amount: Long) {
        assertThatThrownBy { Money(amount, "GBP") }.isInstanceOf(InvalidPaymentException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "gbp", "XXXX", "ZZZ1"])
    fun `rejects unknown currency codes`(code: String) {
        assertThatThrownBy { Money(100, code) }.isInstanceOf(InvalidPaymentException::class.java)
    }
}
