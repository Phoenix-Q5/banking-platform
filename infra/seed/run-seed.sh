#!/bin/sh
# Waits for Flyway migrations to finish, then seeds all databases once.
# Idempotent: skips if seed customers already exist.

PGHOST="${PGHOST:-postgres}"
PGUSER="${PGUSER:-banking}"
export PGPASSWORD="${PGPASSWORD:-banking}"

echo "==> db-seed: waiting for schema to be ready..."

RETRIES=48   # 48 Ã— 5s = 4 minutes max
i=0
until psql -h "$PGHOST" -U "$PGUSER" -d customerdb \
      -c "SELECT 1 FROM customers LIMIT 1" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge "$RETRIES" ]; then
    echo "==> db-seed: timed out waiting for tables. Skipping seed."
    exit 0
  fi
  echo "==> db-seed: attempt $i/$RETRIES â€” tables not ready yet, retrying in 5s..."
  sleep 5
done

# Idempotency check â€” skip if seed rows are already present
COUNT=$(psql -h "$PGHOST" -U "$PGUSER" -d customerdb -t -A \
        -c "SELECT COUNT(*) FROM customers WHERE email LIKE 'seed.%'")

if [ "$COUNT" -gt "0" ]; then
  echo "==> db-seed: already seeded ($COUNT seed customers found). Nothing to do."
  exit 0
fi

echo "==> db-seed: running seed script..."
psql -h "$PGHOST" -U "$PGUSER" -d customerdb -f /seed/seed.sql

echo "==> db-seed: finished."
