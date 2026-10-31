package com.example.payments.adapter.web

import com.example.payments.domain.ConcurrentPaymentUpdateException
import com.example.payments.domain.IdempotencyKeyReuseException
import com.example.payments.domain.InvalidPaymentException
import com.example.payments.domain.InvalidPaymentTransitionException
import com.example.payments.domain.PaymentException
import com.example.payments.domain.PaymentNotFoundException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

/**
 * Maps every failure to an RFC 9457 `application/problem+json` body.
 * Framework errors (malformed JSON, missing header, bean validation) are handled by the base class.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(PaymentException::class)
    fun handleDomain(ex: PaymentException): ProblemDetail {
        val (status, slug) =
            when (ex) {
                is PaymentNotFoundException -> HttpStatus.NOT_FOUND to "payment-not-found"
                is InvalidPaymentException -> HttpStatus.UNPROCESSABLE_ENTITY to "invalid-payment"
                is InvalidPaymentTransitionException -> HttpStatus.CONFLICT to "invalid-transition"
                is IdempotencyKeyReuseException -> HttpStatus.CONFLICT to "idempotency-key-reuse"
                is ConcurrentPaymentUpdateException -> HttpStatus.CONFLICT to "concurrent-update"
            }
        return problem(status, slug, ex.message)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "validation-failed", ex.message)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unhandled exception", ex)
        // Never leak internals to clients; the trace id in the logs is the correlation handle.
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred")
    }

    private fun problem(status: HttpStatus, slug: String, detail: String?): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail ?: status.reasonPhrase).apply {
            type = URI.create("urn:problem:$slug")
            title = status.reasonPhrase
        }
}
