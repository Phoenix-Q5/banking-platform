
CREATE TABLE beneficiaries (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    routing_number VARCHAR(32),
    bank_name VARCHAR(120),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_beneficiaries_customer ON beneficiaries(customer_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    from_account_id UUID NOT NULL,
    beneficiary_id UUID,
    payment_type VARCHAR(30) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reference VARCHAR(120),
    description TEXT,
    scheduled_for DATE,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payments_customer ON payments(customer_id);
CREATE INDEX idx_payments_from_account ON payments(from_account_id);
