-- =============================================================
-- Core Banking Schema — V1
-- Event Sourcing + CQRS + Idempotence
-- =============================================================

-- Enums
CREATE TYPE account_status AS ENUM ('ACTIVE', 'FROZEN', 'CLOSED');
CREATE TYPE transaction_type AS ENUM ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT');
CREATE TYPE idempotency_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED');

-- 1. Accounts (Aggregate root)
CREATE TABLE accounts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number  VARCHAR(34)     NOT NULL,
    owner_name      VARCHAR(255)    NOT NULL,
    owner_email     VARCHAR(255)    NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'XOF',
    balance         DECIMAL(19,4)   NOT NULL DEFAULT 0,
    overdraft_limit DECIMAL(19,4)   NOT NULL DEFAULT 0,
    status          account_status  NOT NULL DEFAULT 'ACTIVE',
    aggr_version    BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_account_balance CHECK (balance + overdraft_limit >= 0),
    CONSTRAINT uq_account_number UNIQUE (account_number)
);

CREATE INDEX idx_accounts_owner_email ON accounts(owner_email);

-- 2. Events (Event Store — append-only)
CREATE TABLE events (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID            NOT NULL,
    aggregate_type  VARCHAR(100)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    version         BIGINT          NOT NULL,
    data            JSONB           NOT NULL,
    metadata        JSONB,
    idempotency_key VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_events_aggregate_version UNIQUE (aggregate_id, version),
    CONSTRAINT uq_events_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_events_account FOREIGN KEY (aggregate_id) REFERENCES accounts(id)
);

CREATE INDEX idx_events_aggregate_id ON events(aggregate_id);
CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_events_created_at ON events(created_at DESC);

-- 3. Transaction History (CQRS Read Model)
CREATE TABLE transaction_history (
    id                  UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          UUID              NOT NULL,
    transaction_type    transaction_type  NOT NULL,
    amount              DECIMAL(19,4)     NOT NULL,
    balance_before      DECIMAL(19,4)     NOT NULL,
    balance_after       DECIMAL(19,4)     NOT NULL,
    counterparty        VARCHAR(255),
    reference           VARCHAR(255)      NOT NULL,
    description         VARCHAR(500),
    event_id            UUID              NOT NULL,
    created_at          TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_tx_event FOREIGN KEY (event_id) REFERENCES events(id)
);

CREATE INDEX idx_tx_account_created ON transaction_history(account_id, created_at DESC);
CREATE INDEX idx_tx_reference ON transaction_history(reference);

-- 4. Account Balances (CQRS Materialized View)
CREATE TABLE account_balances (
    account_id      UUID            PRIMARY KEY,
    balance         DECIMAL(19,4)   NOT NULL,
    last_event_id   UUID            NOT NULL,
    last_updated    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_bal_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_bal_event FOREIGN KEY (last_event_id) REFERENCES events(id)
);

-- 5. Idempotency Keys (backup table)
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255)    PRIMARY KEY,
    resource_type   VARCHAR(100)    NOT NULL,
    resource_id     UUID,
    status          idempotency_status NOT NULL DEFAULT 'PENDING',
    response_body   JSONB,
    expires_at      TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at) WHERE status = 'PENDING';

-- Fonction : purge automatique des clés d'idempotence expirées
CREATE OR REPLACE FUNCTION purge_idempotency_keys()
RETURNS void AS $$
BEGIN
    DELETE FROM idempotency_keys WHERE expires_at < NOW();
END;
$$ LANGUAGE plpgsql;

-- Fonction : rejeu d'événements pour un agrégat
CREATE OR REPLACE FUNCTION replay_events(p_aggregate_id UUID)
RETURNS TABLE(event_type VARCHAR, data JSONB, version BIGINT) AS $$
BEGIN
    RETURN QUERY
    SELECT e.event_type, e.data, e.version
    FROM events e
    WHERE e.aggregate_id = p_aggregate_id
    ORDER BY e.version ASC;
END;
$$ LANGUAGE plpgsql;

-- Trigger : updated_at automatique
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_accounts_set_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
