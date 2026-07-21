#!/bin/sh
# Waits for Flyway migrations to finish, then seeds all databases once.
# Idempotent: skips if seed customers already exist.

PGHOST="${PGHOST:-postgres}"
PGUSER="${PGUSER:-banking}"
export PGPASSWORD="${PGPASSWORD:-banking}"

echo "==> db-seed: waiting for schema to be ready..."

RETRIES=48   # 48 × 5s = 4 minutes max
i=0
# support_pin_hash arrives with customer-service V2 — waiting on it ensures
# migrations (not just the base table) are done before we seed.
until psql -h "$PGHOST" -U "$PGUSER" -d customerdb \
      -c "SELECT support_pin_hash FROM customers LIMIT 1" >/dev/null 2>&1 \
   && psql -h "$PGHOST" -U "$PGUSER" -d loandb \
      -c "SELECT 1 FROM loans LIMIT 1" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge "$RETRIES" ]; then
    echo "==> db-seed: timed out waiting for tables. Skipping seed."
    exit 0
  fi
  echo "==> db-seed: attempt $i/$RETRIES — tables not ready yet, retrying in 5s..."
  sleep 5
done

# Idempotency check — skip if seed rows are already present
COUNT=$(psql -h "$PGHOST" -U "$PGUSER" -d customerdb -t -A \
        -c "SELECT COUNT(*) FROM customers WHERE email LIKE 'seed.%'")

if [ "$COUNT" -gt "0" ]; then
  echo "==> db-seed: already seeded ($COUNT seed customers found)."
  # Backfill: databases seeded before support PINs existed get the demo PIN.
  MISSING=$(psql -h "$PGHOST" -U "$PGUSER" -d customerdb -t -A \
            -c "SELECT COUNT(*) FROM customers WHERE support_pin_hash IS NULL")
  if [ "$MISSING" -gt "0" ]; then
    echo "==> db-seed: backfilling support PIN for $MISSING customers..."
    psql -h "$PGHOST" -U "$PGUSER" -d customerdb -c \
      "CREATE EXTENSION IF NOT EXISTS pgcrypto;
       UPDATE customers
       SET support_pin_hash = crypt('1234', gen_salt('bf', 10)),
           support_pin_set_at = NOW()
       WHERE support_pin_hash IS NULL;"
  fi
  echo "==> db-seed: nothing else to do."
  exit 0
fi

echo "==> db-seed: running seed script..."
psql -h "$PGHOST" -U "$PGUSER" -d customerdb -f /seed/seed.sql

echo "==> db-seed: finished."
