#!/bin/sh
# Waits for Flyway migrations to finish, then seeds all databases.
# Idempotent: seed.sql uses ON CONFLICT DO NOTHING / UPDATE … WHERE NULL.
# Always re-runs the SQL so a prior partial seed (e.g. customers inserted but
# loans aborted) still gets loans, pending approvals, and support PINs.

PGHOST="${PGHOST:-postgres}"
PGUSER="${PGUSER:-banking}"
export PGPASSWORD="${PGPASSWORD:-banking}"

echo "==> db-seed: waiting for schema to be ready..."

RETRIES=48   # 48 × 5s = 4 minutes max
i=0
# Wait for customer V2 (support_pin_hash) and loandb.loans so seed never
# aborts mid-script on missing tables/columns.
until psql -h "$PGHOST" -U "$PGUSER" -d customerdb \
      -c "SELECT support_pin_hash FROM customers LIMIT 1" >/dev/null 2>&1 \
   && psql -h "$PGHOST" -U "$PGUSER" -d loandb \
      -c "SELECT 1 FROM loans LIMIT 1" >/dev/null 2>&1 \
   && psql -h "$PGHOST" -U "$PGUSER" -d accountdb \
      -c "SELECT 1 FROM accounts LIMIT 1" >/dev/null 2>&1 \
   && psql -h "$PGHOST" -U "$PGUSER" -d transactiondb \
      -c "SELECT 1 FROM transactions LIMIT 1" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge "$RETRIES" ]; then
    echo "==> db-seed: timed out waiting for tables. Skipping seed."
    exit 0
  fi
  echo "==> db-seed: attempt $i/$RETRIES — tables not ready yet, retrying in 5s..."
  sleep 5
done

CUSTOMERS=$(psql -h "$PGHOST" -U "$PGUSER" -d customerdb -t -A \
        -c "SELECT COUNT(*) FROM customers WHERE email LIKE 'seed.%'")
LOANS=$(psql -h "$PGHOST" -U "$PGUSER" -d loandb -t -A \
        -c "SELECT COUNT(*) FROM loans")

echo "==> db-seed: current counts — seed customers=$CUSTOMERS loans=$LOANS"
echo "==> db-seed: running seed script (idempotent)..."
psql -h "$PGHOST" -U "$PGUSER" -d customerdb -f /seed/seed.sql

LOANS_AFTER=$(psql -h "$PGHOST" -U "$PGUSER" -d loandb -t -A \
        -c "SELECT COUNT(*) FROM loans")
echo "==> db-seed: finished. loans now=$LOANS_AFTER"
