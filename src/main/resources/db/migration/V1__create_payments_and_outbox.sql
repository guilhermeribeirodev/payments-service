CREATE TABLE payments (
    id              UUID         PRIMARY KEY,
    amount_minor    BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency        VARCHAR(3)   NOT NULL,
    reference       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('AUTHORISED', 'CAPTURED', 'CANCELLED')),
    idempotency_key VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX ix_payments_created_at        ON payments (created_at DESC, id DESC);
CREATE INDEX ix_payments_status_created_at ON payments (status, created_at DESC, id DESC);

-- Transactional outbox: events are written in the same transaction as the state change,
-- then relayed to the message broker by a poller (at-least-once delivery).
CREATE TABLE outbox_events (
    id           UUID         PRIMARY KEY,
    aggregate_id UUID         NOT NULL,
    type         VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at, id) WHERE published_at IS NULL;
