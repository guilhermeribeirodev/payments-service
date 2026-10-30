package com.example.payments.adapter.web

import com.example.payments.adapter.web.api.PaymentsApi
import com.example.payments.adapter.web.model.CreatePaymentRequest
import com.example.payments.adapter.web.model.PaymentPage
import com.example.payments.application.AuthorisePaymentCommand
import com.example.payments.application.AuthoriseResult
import com.example.payments.application.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID
import com.example.payments.adapter.web.model.Payment as PaymentDto
import com.example.payments.adapter.web.model.PaymentStatus as PaymentStatusDto

/**
 * Thin HTTP adapter. The method signatures come from the generated [PaymentsApi] interface, so the
 * compiler enforces that we implement exactly what `payments-api.yaml` declares.
 */
@RestController
class PaymentsController(private val service: PaymentService) : PaymentsApi {
    override fun createPayment(
        idempotencyKey: String,
        createPaymentRequest: CreatePaymentRequest,
    ): ResponseEntity<PaymentDto> {
        val result = service.authorise(
            AuthorisePaymentCommand(
                amountMinor = createPaymentRequest.amount,
                currency = createPaymentRequest.currency,
                reference = createPaymentRequest.reference,
                idempotencyKey = idempotencyKey,
            ),
        )
        val body = result.payment.toDto()
        return when (result) {
            is AuthoriseResult.Created -> ResponseEntity.created(URI.create("/v1/payments/${body.id}")).body(body)
            is AuthoriseResult.Replayed -> ResponseEntity.ok(body)
        }
    }

    override fun getPayment(paymentId: UUID): ResponseEntity<PaymentDto> =
        ResponseEntity.ok(service.get(paymentId).toDto())

    override fun capturePayment(paymentId: UUID): ResponseEntity<PaymentDto> =
        ResponseEntity.ok(service.capture(paymentId).toDto())

    override fun cancelPayment(paymentId: UUID): ResponseEntity<PaymentDto> =
        ResponseEntity.ok(service.cancel(paymentId).toDto())

    override fun listPayments(status: PaymentStatusDto?, limit: Int, offset: Int): ResponseEntity<PaymentPage> =
        ResponseEntity.ok(service.list(status?.toDomain(), limit, offset).toDto(limit, offset))
}
