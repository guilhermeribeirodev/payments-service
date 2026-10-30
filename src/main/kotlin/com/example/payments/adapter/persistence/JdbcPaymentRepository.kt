package com.example.payments.adapter.persistence

import com.example.payments.application.port.PagedResult
import com.example.payments.application.port.PaymentRepository
import com.example.payments.domain.ConcurrentPaymentUpdateException
import com.example.payments.domain.Money
import com.example.payments.domain.Payment
import com.example.payments.domain.PaymentStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JdbcPaymentRepository(private val jdbc: JdbcClient) : PaymentRepository {
    override fun insertIfAbsent(payment: Payment): Boolean =
        jdbc.sql(
            """
            INSERT INTO payments (id, amount_minor, currency, reference, status, idempotency_key, created_at, updated_at, version)
            VALUES (:id, :amount, :currency, :reference, :status, :key, :createdAt, :updatedAt, :version)
            ON CONFLICT (idempotency_key) DO NOTHING
            """,
        )
            .param("id", payment.id)
            .param("amount", payment.money.amountMinor)
            .param("currency", payment.money.currency)
            .param("reference", payment.reference)
            .param("status", payment.status.name)
            .param("key", payment.idempotencyKey)
            .param("createdAt", payment.createdAt.atOffset(ZoneOffset.UTC))
            .param("updatedAt", payment.updatedAt.atOffset(ZoneOffset.UTC))
            .param("version", payment.version)
            .update() == 1

    override fun update(payment: Payment, expectedVersion: Long) {
        val rows = jdbc.sql(
            "UPDATE payments SET status = :status, updated_at = :updatedAt, version = :version WHERE id = :id AND version = :expected",
        )
            .param("status", payment.status.name)
            .param("updatedAt", payment.updatedAt.atOffset(ZoneOffset.UTC))
            .param("version", payment.version)
            .param("id", payment.id)
            .param("expected", expectedVersion)
            .update()
        if (rows != 1) throw ConcurrentPaymentUpdateException(payment.id)
    }

    override fun findById(id: UUID): Payment? =
        jdbc.sql("SELECT * FROM payments WHERE id = :id").param("id", id).query(rowMapper).optional().orElse(null)

    override fun findByIdempotencyKey(key: String): Payment? =
        jdbc.sql("SELECT * FROM payments WHERE idempotency_key = :key").param("key", key)
            .query(rowMapper).optional().orElse(null)

    override fun list(status: PaymentStatus?, limit: Int, offset: Int): PagedResult<Payment> {
        val where = if (status != null) "WHERE status = :status" else ""
        fun JdbcClient.StatementSpec.withStatus() = if (status != null) param("status", status.name) else this

        val total = jdbc.sql("SELECT count(*) FROM payments $where").withStatus()
            .query(Long::class.java).single()
        val items = jdbc.sql("SELECT * FROM payments $where ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
            .withStatus()
            .param("limit", limit)
            .param("offset", offset)
            .query(rowMapper)
            .list()
        return PagedResult(items, total)
    }

    private val rowMapper = RowMapper<Payment> { rs, _ ->
        Payment(
            id = rs.getObject("id", UUID::class.java),
            money = Money(rs.getLong("amount_minor"), rs.getString("currency")),
            reference = rs.getString("reference"),
            status = PaymentStatus.valueOf(rs.getString("status")),
            idempotencyKey = rs.getString("idempotency_key"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
            updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java).toInstant(),
            version = rs.getLong("version"),
        )
    }
}
