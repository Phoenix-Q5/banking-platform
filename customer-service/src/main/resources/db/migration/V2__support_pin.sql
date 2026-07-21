-- Secret 4-digit support PIN (stored as bcrypt hash only) used to verify a
-- customer's identity before support staff may view sensitive details.
ALTER TABLE customers ADD COLUMN support_pin_hash VARCHAR(100);
ALTER TABLE customers ADD COLUMN support_pin_set_at TIMESTAMPTZ;
ALTER TABLE customers ADD COLUMN support_pin_failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN support_pin_locked_until TIMESTAMPTZ;
