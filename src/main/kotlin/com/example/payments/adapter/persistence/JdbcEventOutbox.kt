package com.example.payments.adapter.persistence

import com.example.payments.application.port.EventMessage
import com.example.payments.application.port.EventOutbox
import com.example.payments.application.port.OutboxStore
import com.example.payments.domain.PaymentEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Transactional outbox: `append` runs inside the caller's transaction, so the event exists if and
 * only if the state change committed. The relay (see messaging package) ships rows to the broker.
 */
@Repository
class JdbcEventOutbox(
    private val jdbc: JdbcClient,
    private val objectMapper: ObjectMapper,
) : EventOutbox,
    OutboxStore {
    override fun append(event: PaymentEvent) {
        jdbc
            .sql(
                """
            INSERT INTO outbox_events (id, aggregate_id, type, payload, created_at)
            VALUES (:id, :aggregateId, :type, CAST(:payload AS jsonb), :createdAt)
            """,
            ).param("id", UUID.randomUUID())
            .param("aggregateId", event.paymentId)
            .param("type", event.type)
            .param("payload", objectMapper.writeValueAsString(event))
            .param("createdAt", event.occurredAt.atOffset(ZoneOffset.UTC))
            .update()
    }

    override fun lockUnpublished(limit: Int): List<EventMessage> =
        jdbc
            .sql(
                """
            SELECT id, aggregate_id, type, payload::text AS payload, created_at
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
            ).param("limit", limit)
            .query(rowMapper)
            .list()

    override fun markPublished(ids: List<UUID>) {
        if (ids.isEmpty()) return
        jdbc
            .sql("UPDATE outbox_events SET published_at = now() WHERE id IN (:ids)")
            .param("ids", ids)
            .update()
    }

    private val rowMapper =
        RowMapper<EventMessage> { rs, _ ->
            EventMessage(
                id = rs.getObject("id", UUID::class.java),
                aggregateId = rs.getObject("aggregate_id", UUID::class.java),
                type = rs.getString("type"),
                payload = rs.getString("payload"),
                occurredAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
            )
        }
}
