package com.example.payments.integration

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers
import com.atlassian.oai.validator.report.LevelResolver
import com.atlassian.oai.validator.report.ValidationReport.Level
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.util.UUID

/**
 * End-to-end through HTTP -> service -> real PostgreSQL (Testcontainers) -> outbox relay.
 * Every response is also validated against openapi/payments-api.yaml (contract test).
 */

private val responseOnlyValidator = OpenApiInteractionValidator
    .createForSpecificationUrl("openapi/payments-api.yaml")   // same path you just fixed
    .withLevelResolver(
        LevelResolver.create()
            .withLevel("validation.request.body.schema.minimum", Level.IGNORE)
            .build()
    )
    .build()

private val responseOnlyContract = OpenApiValidationMatchers.openApi().isValid(responseOnlyValidator)

@Tag("integration")
@SpringBootTest(properties = ["payments.outbox.relay.poll-interval-ms=100"])
@AutoConfigureMockMvc
@Import(IntegrationTestConfig::class)
class PaymentsApiIntegrationTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val publisher: RecordingEventPublisher,
    @Autowired private val objectMapper: ObjectMapper,
) {
    private val contract = openApi().isValid(OpenApiInteractionValidator.createFor("openapi/payments-api.yaml").build())

    @BeforeEach
    fun clean() {
        jdbc.sql("TRUNCATE payments, outbox_events").update()
        publisher.messages.clear()
    }

    private fun create(key: String = UUID.randomUUID().toString(), amount: Long = 1500, reference: String = "order-1"): ResultActions =
        mvc.perform(
            post("/v1/payments")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":$amount,"currency":"GBP","reference":"$reference"}"""),
        )

    private fun idOf(result: ResultActions): String =
        objectMapper.readTree(result.andReturn().response.contentAsString)["id"].asText()

    @Test
    fun `full lifecycle - authorise, get, capture`() {
        val created = create().andExpect(status().isCreated).andExpect(contract)
        val id = idOf(created)

        mvc.perform(get("/v1/payments/$id"))
            .andExpect(status().isOk).andExpect(contract)
            .andExpect(jsonPath("$.status").value("AUTHORISED"))

        mvc.perform(post("/v1/payments/$id/capture"))
            .andExpect(status().isOk).andExpect(contract)
            .andExpect(jsonPath("$.status").value("CAPTURED"))
    }

    @Test
    fun `idempotent replay returns 200 and the same payment, a different body is a 409`() {
        val key = "idem-key-0001"
        val id = idOf(create(key).andExpect(status().isCreated))

        create(key).andExpect(status().isOk).andExpect(contract).andExpect(jsonPath("$.id").value(id))
        create(key, amount = 2500).andExpect(status().isConflict).andExpect(contract)

        assertThat(jdbc.sql("SELECT count(*) FROM payments").query(Long::class.java).single()).isEqualTo(1)
    }

    @Test
    fun `invalid transitions and unknown ids produce problem responses`() {
        val id = idOf(create().andExpect(status().isCreated))
        mvc.perform(post("/v1/payments/$id/cancel")).andExpect(status().isOk)
        mvc.perform(post("/v1/payments/$id/capture")).andExpect(status().isConflict).andExpect(contract)
        mvc.perform(get("/v1/payments/${UUID.randomUUID()}")).andExpect(status().isNotFound).andExpect(contract)
    }

    @Test
    fun `validation failures are 400s that match the contract`() {
        mvc.perform(
            post("/v1/payments").header("Idempotency-Key", "idem-key-0002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":-5,"currency":"GBP","reference":"x"}"""),
        ).andExpect(status().isBadRequest).andExpect(responseOnlyContract)
    }

    @Test
    fun `listing filters by status and paginates`() {
        val first = idOf(create(reference = "a"))
        create(reference = "b")
        create(reference = "c")
        mvc.perform(post("/v1/payments/$first/capture")).andExpect(status().isOk)

        mvc.perform(get("/v1/payments?limit=2"))
            .andExpect(status().isOk).andExpect(contract)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.total").value(3))

        mvc.perform(get("/v1/payments?status=CAPTURED"))
            .andExpect(status().isOk).andExpect(contract)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(first))
    }

    @Test
    fun `state changes are relayed from the outbox exactly as recorded`() {
        val id = idOf(create())
        mvc.perform(post("/v1/payments/$id/capture")).andExpect(status().isOk)

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(publisher.messages.map { it.type }).containsExactly("payment.authorised", "payment.captured")
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val unpublished = jdbc.sql("SELECT count(*) FROM outbox_events WHERE published_at IS NULL")
                .query(Long::class.java).single()
            assertThat(unpublished).isZero()
        }
        assertThat(publisher.messages.map { it.aggregateId }.distinct()).containsExactly(UUID.fromString(id))
    }

    @Test
    fun `health probes and the contract file are exposed`() {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk)
        mvc.perform(get("/openapi/payments-api.yaml")).andExpect(status().isOk)
    }
}
