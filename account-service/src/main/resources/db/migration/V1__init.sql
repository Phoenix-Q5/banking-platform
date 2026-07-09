CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    account_number  VARCHAR(34) NOT NULL UNIQUE,
    customer_id     UUID NOT NULL,
    balance         NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency        CHAR(3) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);
