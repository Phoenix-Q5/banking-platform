-- device tokens for push delivery
CREATE TABLE IF NOT EXISTS device_tokens (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    platform VARCHAR(20) NOT NULL,
    token VARCHAR(512) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_device_tokens_token ON device_tokens(token);
CREATE INDEX IF NOT EXISTS idx_device_tokens_customer ON device_tokens(customer_id);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS event_id VARCHAR(64);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS event_type VARCHAR(80);
CREATE INDEX IF NOT EXISTS idx_notifications_event_id ON notifications(event_id);
