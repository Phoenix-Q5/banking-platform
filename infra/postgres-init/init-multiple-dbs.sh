#!/bin/bash
# Creates one database per service. The postgres image runs every *.sh in
# /docker-entrypoint-initdb.d/ on first startup only (i.e. when the data
# volume is empty).
set -e

for db in accountdb transactiondb customerdb paymentdb carddb notificationdb auditdb loandb opsagentdb; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    SELECT 'CREATE DATABASE $db OWNER $POSTGRES_USER'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
done
