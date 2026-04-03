-- V1__payment_gateway_schema.sql

-- ─── payments ────────────────────────────────────────────────────────────────
-- Registro principal do pagamento
CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(64) NOT NULL UNIQUE,  -- Evita duplicatas do cliente
    merchant_id         VARCHAR(50) NOT NULL,
    amount              NUMERIC(15, 2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'BRL',
    payment_method      VARCHAR(20) NOT NULL,          -- CREDIT_CARD, DEBIT_CARD, PIX
    card_holder         VARCHAR(100),
    card_last_four      VARCHAR(4),
    card_brand          VARCHAR(20),                   -- VISA, MASTERCARD, etc.
    pix_key             VARCHAR(100),
    description         VARCHAR(255),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    acquirer_txn_id     VARCHAR(100),                  -- ID da transação no banco
    acquirer_response   TEXT,                          -- Resposta bruta do adquirente
    error_message       TEXT,
    attempt_count       INT NOT NULL DEFAULT 0,
    max_attempts        INT NOT NULL DEFAULT 5,
    next_retry_at       TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP
);

-- ─── payment_attempts ─────────────────────────────────────────────────────────
-- Histórico de cada tentativa (incluindo falhas)
CREATE TABLE payment_attempts (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      UUID NOT NULL REFERENCES payments(id),
    attempt_number  INT NOT NULL,
    status          VARCHAR(20) NOT NULL,               -- SUCCESS, FAILED, TIMEOUT, CB_OPEN
    acquirer_txn_id VARCHAR(100),
    request_payload TEXT,                               -- Payload enviado ao adquirente
    response_payload TEXT,                              -- Resposta recebida
    error_code      VARCHAR(50),
    error_message   TEXT,
    duration_ms     BIGINT,
    circuit_breaker_state VARCHAR(20),                  -- CLOSED / OPEN / HALF_OPEN
    attempted_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─── outbox_events ────────────────────────────────────────────────────────────
-- Outbox Pattern: eventos a serem publicados no RabbitMQ
-- Garante consistência: persistência e publicação são atômicas
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,               -- 'PAYMENT'
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,               -- 'PAYMENT_CREATED', 'PAYMENT_RETRY', etc.
    payload         TEXT NOT NULL,                      -- JSON com os dados do evento
    routing_key     VARCHAR(100) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
    attempts        INT NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP
);

-- ─── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_payments_status          ON payments(status);
CREATE INDEX idx_payments_merchant        ON payments(merchant_id);
CREATE INDEX idx_payments_next_retry      ON payments(next_retry_at) WHERE status = 'PENDING_RETRY';
CREATE INDEX idx_payments_idempotency     ON payments(idempotency_key);
CREATE INDEX idx_attempts_payment         ON payment_attempts(payment_id);
CREATE INDEX idx_outbox_status            ON outbox_events(status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate         ON outbox_events(aggregate_id);

-- ─── Comments ────────────────────────────────────────────────────────────────
COMMENT ON TABLE payments           IS 'Registro principal de cada pagamento';
COMMENT ON TABLE payment_attempts   IS 'Histórico de tentativas com detalhes de cada chamada ao adquirente';
COMMENT ON TABLE outbox_events      IS 'Outbox Pattern: eventos transacionais aguardando publicação no RabbitMQ';
COMMENT ON COLUMN payments.idempotency_key IS 'Chave única fornecida pelo cliente para evitar processamento duplicado';
COMMENT ON COLUMN outbox_events.payload    IS 'JSON com dados completos do evento para reidratação no consumidor';
