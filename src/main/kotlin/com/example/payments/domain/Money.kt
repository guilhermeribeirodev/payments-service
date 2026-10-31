package com.example.payments.domain

import java.util.Currency

/** An amount in minor units (pence, cents) with an ISO 4217 currency. Always positive. */
data class Money(
    val amountMinor: Long,
    val currency: String,
) {
    init {
        if (amountMinor <= 0) {
            throw InvalidPaymentException("Amount must be greater than zero, got $amountMinor")
        }
        try {
            Currency.getInstance(currency)
        } catch (_: IllegalArgumentException) {
            throw InvalidPaymentException("Unknown ISO 4217 currency code: $currency")
        }
    }
}
