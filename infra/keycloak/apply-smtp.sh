#!/bin/sh
# Applies SMTP settings to the banking realm via Keycloak Admin API.
# Skips when SMTP_HOST is empty so local demos still start without mail.

set -eu

KEYCLOAK_URL="${KEYCLOAK_URL:-http://keycloak:8080}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
SMTP_HOST="${SMTP_HOST:-}"

if [ -z "$SMTP_HOST" ]; then
  echo "SMTP_HOST not set — skipping Keycloak SMTP configuration (forgot-password emails disabled)."
  exit 0
fi

echo "Waiting for Keycloak at ${KEYCLOAK_URL}..."
i=0
until curl -sf "${KEYCLOAK_URL}/realms/master" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "Keycloak did not become ready in time"
    exit 1
  fi
  sleep 2
done

# Realm import can lag behind the server becoming reachable
i=0
until curl -sf "${KEYCLOAK_URL}/realms/banking" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "banking realm not ready"
    exit 1
  fi
  sleep 2
done

TOKEN=$(curl -sf -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d 'grant_type=password' \
  -d 'client_id=admin-cli' | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "Failed to obtain Keycloak admin token"
  exit 1
fi

SMTP_PORT="${SMTP_PORT:-587}"
SMTP_FROM="${SMTP_FROM:-noreply@harborbank.local}"
SMTP_USER="${SMTP_USER:-}"
SMTP_PASSWORD="${SMTP_PASSWORD:-}"
SMTP_STARTTLS="${SMTP_STARTTLS:-true}"
SMTP_SSL="${SMTP_SSL:-false}"
SMTP_AUTH="${SMTP_AUTH:-true}"

# Build smtpServer JSON (password may contain quotes — escape roughly)
escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

BODY=$(cat <<EOF
{
  "smtpServer": {
    "host": "$(escape "$SMTP_HOST")",
    "port": "$(escape "$SMTP_PORT")",
    "from": "$(escape "$SMTP_FROM")",
    "fromDisplayName": "Harbor Bank",
    "auth": "$(escape "$SMTP_AUTH")",
    "user": "$(escape "$SMTP_USER")",
    "password": "$(escape "$SMTP_PASSWORD")",
    "starttls": "$(escape "$SMTP_STARTTLS")",
    "ssl": "$(escape "$SMTP_SSL")",
    "replyTo": "$(escape "$SMTP_FROM")"
  },
  "resetPasswordAllowed": true
}
EOF
)

HTTP=$(curl -s -o /tmp/kc-smtp.out -w '%{http_code}' -X PUT \
  "${KEYCLOAK_URL}/admin/realms/banking" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "$BODY")

if [ "$HTTP" != "204" ] && [ "$HTTP" != "200" ]; then
  echo "Failed to apply SMTP (HTTP ${HTTP}): $(cat /tmp/kc-smtp.out)"
  exit 1
fi

echo "Keycloak SMTP configured for host=${SMTP_HOST} port=${SMTP_PORT} from=${SMTP_FROM}"
