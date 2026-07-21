-- Ops-agent durable storage: incidents and emergency restart requests are
-- kept as JSONB documents keyed by id, with hot columns lifted for ordering.

CREATE TABLE IF NOT EXISTS ops_incidents (
    id         VARCHAR(64) PRIMARY KEY,
    updated_at TIMESTAMPTZ NOT NULL,
    doc        JSONB       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ops_incidents_updated_at ON ops_incidents (updated_at DESC);

CREATE TABLE IF NOT EXISTS ops_restart_requests (
    id           VARCHAR(64) PRIMARY KEY,
    requested_at TIMESTAMPTZ NOT NULL,
    doc          JSONB       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ops_restart_requests_requested_at ON ops_restart_requests (requested_at DESC);
