package com.example.payments.adapter.web

import com.example.payments.application.AuthoriseResult
import com.example.payments.application.PaymentService
import com.example.payments.domain.InvalidPaymentTransitionException
import com.example.payments.domain.Money
import com.example.payments.domain.Payment
import com.example.payments.domain.PaymentNotFoundException
import com.example.payments.domain.PaymentStatus
import com.ninjasquad.springmockk.MockkBean
import io.micrometer.observation.ObservationRegistry
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@TestConfiguration
class NoopObservationConfig {
    @Bean
    fun observationRegistry(): ObservationRegistry = ObservationRegistry.NOOP
}

/** Web slice: HTTP mapping, validation and error format only. The service is mocked. */
@WebMvcTest(PaymentsController::class)
@Import(NoopObservationConfig::class, ApiExceptionHandler::class)
class PaymentsControllerTest(
    @Autowired private val mvc: MockMvc,
) {
    @MockkBean
    private lateinit var service: PaymentService

    private val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val payment =
        Payment(
            id,
            Money(1500, "GBP"),
            "order-1",
            PaymentStatus.AUTHORISED,
            "key-12345678",
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T10:00:00Z"),
            0,
        )
    private val body = """{"amount":1500,"currency":"GBP","reference":"order-1"}"""

    private fun create(json: String = body, key: String? = "key-12345678") =
        post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(json).apply {
            if (key != null) header("Idempotency-Key", key)
        }

    @Test
    fun `POST returns 201 with Location for a new payment`() {
        every { service.authorise(any()) } returns AuthoriseResult.Created(payment)

        mvc
            .perform(create())
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/v1/payments/$id"))
            .andExpect(jsonPath("$.status").value("AUTHORISED"))
            .andExpect(jsonPath("$.amount").value(1500))
    }

    @Test
    fun `POST returns 200 for an idempotent replay`() {
        every { service.authorise(any()) } returns AuthoriseResult.Replayed(payment)

        mvc.perform(create()).andExpect(status().isOk)
    }

    @Test
    fun `POST without Idempotency-Key is a 400 problem`() {
        mvc
            .perform(create(key = null))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    @Test
    fun `POST with a malformed currency is a 400 problem`() {
        mvc
            .perform(create("""{"amount":1500,"currency":"gbp","reference":"order-1"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }

    @Test
    fun `POST with a non-positive amount is a 400 problem`() {
        mvc
            .perform(create("""{"amount":0,"currency":"GBP","reference":"order-1"}"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET of an unknown payment is a 404 problem`() {
        every { service.get(id) } throws PaymentNotFoundException(id)

        mvc
            .perform(get("/v1/payments/$id"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:problem:payment-not-found"))
    }

    @Test
    fun `capture of a cancelled payment is a 409 problem`() {
        every { service.capture(id) } throws
            InvalidPaymentTransitionException(id, PaymentStatus.CANCELLED, PaymentStatus.CAPTURED)

        mvc
            .perform(post("/v1/payments/$id/capture"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.type").value("urn:problem:invalid-transition"))
    }

    @Test
    fun `GET with a non-uuid id is a 400`() {
        mvc.perform(get("/v1/payments/not-a-uuid")).andExpect(status().isBadRequest)
    }

    @Test
    fun `list rejects a limit above the documented maximum`() {
        mvc.perform(get("/v1/payments?limit=101")).andExpect(status().isBadRequest)
    }
}
