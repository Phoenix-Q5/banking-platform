-- customer profiles
CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    external_user_id VARCHAR(128),
    email           VARCHAR(255) NOT NULL UNIQUE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(40),
    date_of_birth   DATE,
    address_line1   VARCHAR(255),
    address_line2   VARCHAR(255),
    city            VARCHAR(100),
    state           VARCHAR(100),
    postal_code     VARCHAR(20),
    country         CHAR(2) NOT NULL DEFAULT 'US',
    kyc_status      VARCHAR(30) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_external_user ON customers(external_user_id);
