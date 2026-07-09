
CREATE TABLE loans (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    product_code VARCHAR(40) NOT NULL,
    principal NUMERIC(19,4) NOT NULL,
    interest_rate NUMERIC(8,4) NOT NULL,
    term_months INT NOT NULL,
    monthly_payment NUMERIC(19,4) NOT NULL,
    outstanding_balance NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    purpose VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_loans_customer ON loans(customer_id);
CREATE INDEX idx_loans_status ON loans(status);
