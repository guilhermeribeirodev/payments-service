package com.example.payments.domain

import java.util.UUID

sealed class PaymentException(
    message: String,
) : RuntimeException(message)

class PaymentNotFoundException(
    id: UUID,
) : PaymentException("Payment $id was not found")

class InvalidPaymentException(
    message: String,
) : PaymentException(message)

class InvalidPaymentTransitionException(
    id: UUID,
    from: PaymentStatus,
    to: PaymentStatus,
) : PaymentException("Payment $id cannot move from $from to $to")

class IdempotencyKeyReuseException(
    key: String,
) : PaymentException("Idempotency-Key '$key' was already used with a different request body")

class ConcurrentPaymentUpdateException(
    id: UUID,
) : PaymentException("Payment $id was modified concurrently, please retry")
