
CREATE TABLE cards (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    account_id UUID NOT NULL,
    card_number_last4 VARCHAR(4) NOT NULL,
    card_network VARCHAR(20) NOT NULL,
    card_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    daily_limit NUMERIC(19,4) NOT NULL,
    monthly_limit NUMERIC(19,4) NOT NULL,
    expires_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_cards_customer ON cards(customer_id);
CREATE INDEX idx_cards_account ON cards(account_id);
