# Harbor Bank

Production-shaped banking platform operated as **Harbor Bank** — Spring Boot
microservices, React banking UI, Kafka domain events, and an installable LLM ops agent.

```
harbor-bank/
├── banking-ui/              Customer + admin + contact-center UI
├── api-gateway/             Edge routing, JWT, CORS, circuit breakers
├── account-service/         Balances
├── transaction-service/     Transfers → Kafka transfer alerts
├── customer-service/        Profiles + KYC
├── payment-service/         ACH/wire/bill-pay → Kafka payment alerts
├── card-service/            Cards → Kafka card alerts
├── loan-service/            Loans → Kafka loan alerts
├── notification-service/    Kafka consumer → in-app + push alerts
├── audit-service/           Kafka consumer → immutable audit trail
├── banking-events/          Shared domain event contracts
├── ops-agent/               AI monitoring / troubleshooting
└── infra/                   Keycloak, Postgres, Kafka, Prometheus…
```

## Event-driven alerts

```
transfer / payment / card / loan / account
        │
        ▼
  Kafka topic: harbor.bank.domain-events
        │
        ├──► notification-service  → IN_APP + PUSH (device tokens)
        └──► audit-service         → audit trail
```

Automatic transaction alerts fire when transfers complete or fail. Push delivery
dispatches to registered device tokens (FCM/APNs/Web Push vendor hook is stubbed
with structured logs so the pipeline is end-to-end without vendor credentials).

## Customer accounts (username / password)

Self-registration at **http://localhost:3001/register** creates:

1. A **Keycloak** user (username + password, `CUSTOMER` role)
2. A **customer-service** profile linked via `externalUserId`

| Action | How |
|---|---|
| Create username/password | Register wizard → Credentials step |
| Sign in | Login page (or auto-login after register) |
| Forgot password | Login → “Forgot password?” → Keycloak email reset |
| Change password | Profile → “Change password (Keycloak)” |

Forgot-password emails require SMTP. Copy [`.env.example`](.env) to `.env` and set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`, then recreate (`docker-compose up -d`).

Customer UI: Overview, Funds (deposit/transfer), Products, Open Account, Payments, Cards, Loans, Alerts, Profile — plus account statements with CSV export.

## Run Harbor Bank

```bash
docker-compose up -d --build
```

| Surface | URL |
|---|---|
| **Harbor Bank UI** | **http://localhost:3001** |
| API Gateway | http://localhost:8080 |
| **Swagger UI (aggregated)** | **http://localhost:8080/swagger-ui.html** |
| Ops Agent | http://localhost:8085 |
| Keycloak | http://localhost:8180 |
| Kafka | localhost:9092 |

### Demo operators

| User | Password | Role |
|---|---|---|
| `demo.customer` | `password` | Retail banking |
| `demo.admin` | `password` | Admin / KYC / loans / approvals / freezes |
| `demo.support` | `password` | Contact center |

### Support PIN

Every seeded customer (including `demo.customer@example.com`) has the secret
4-digit support PIN **`1234`** on file (stored bcrypt-hashed). The contact
center (`demo.support`) must verify this PIN before balances, cards, loans,
and transfers are shown. Five wrong attempts lock verification for 15 minutes.
Customers manage their PIN under **Profile → Support PIN**. On self-registration
and admin onboard, a random 4-digit PIN is generated automatically and emailed
to the customer **and** to `OPS_EMAIL_RECIPIENTS` (SMTP tracing). If SMTP is
unset, the PIN is written to customer-service logs instead.

Ops recovery (hidden from Swagger — bcrypt is one-way; “decrypt” = brute-force
the 4-digit space). Authenticated JWT required:

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/customers/management/support-pin/hash` | `{ "pin": "1234" }` → bcrypt hash for SQL |
| POST | `/api/customers/management/support-pin/recover` | `{ "hash": "$2a$…" }` → plaintext PIN |
| POST | `/api/customers/management/support-pin/check` | `{ "pin", "hash" }` → `{ matches }` |
| GET | `/api/customers/management/support-pin/{customerId}` | PIN status + hash |
| POST | `/api/customers/management/support-pin/{customerId}/recover` | Recover that customer’s PIN |
| POST | `/api/customers/management/support-pin/{customerId}/reset` | Force-set PIN + clear lockout |
| POST | `/api/customers/management/support-pin/{customerId}/unlock` | Clear lockout only |
### Admin approvals

New accounts open in `PENDING_APPROVAL` and transfers of **$10,000 or more**
(configurable via `TRANSFER_APPROVAL_THRESHOLD`) are held until `demo.admin`
approves or rejects them in the Admin console tabs. Admins can also place a
temporary freeze on any customer account or card from the **Freeze** tab.

The **Loans** tab lists seeded applications (APPLIED / UNDER_REVIEW / APPROVED)
with Review → Approve → Activate or Reject. Seed data includes ~95 loans.

### Re-seed after a partial / empty loan DB

`db-seed` is idempotent and re-runs safely. If the Loans tab is empty:

```bash
docker-compose up -d --build
docker-compose run --rm db-seed
```

To wipe everything and start clean: `docker-compose down -v` then
`docker-compose up -d --build`.

## Try a live transfer alert

1. Sign in as `demo.customer` at http://localhost:3001
2. Open an account (and a second one, or use another account id)
3. Have `demo.admin` approve the new account(s) in Admin → Account approvals
4. Send a transfer from Overview (under $10,000, or approve it as `demo.admin`)
4. Open **Alerts** — you should see `Transfer completed` (IN_APP + PUSH) without the UI creating it manually

## Mobile app (iOS)

Native SwiftUI client lives in [`apps/ios-harbor-bank`](apps/ios-harbor-bank)
(intended standalone repo: `banking-platform-app` — see
[`apps/CREATE_BANKING_PLATFORM_APP_REPO.md`](apps/CREATE_BANKING_PLATFORM_APP_REPO.md)).

| Audience | Alerts |
|---|---|
| Customer | Kafka transaction / payment / card / loan alerts (IN_APP + PUSH) |
| Admin / Support | Ops-agent **service alerts** (downtime, CB open, latency, errors) |

```bash
cd apps/ios-harbor-bank
./Scripts/generate-project.sh
open HarborBank.xcodeproj
```

## Docs

- [docs/OPS_AGENT.md](docs/OPS_AGENT.md) — AI ops agent
- [docs/EVENTS.md](docs/EVENTS.md) — Kafka domain events
- [docs/postman/Harbor-Bank-API.postman_collection.json](docs/postman/Harbor-Bank-API.postman_collection.json) — Postman collection (all endpoints)
- [banking-ui/README.md](banking-ui/README.md) — Web UI local dev
- [apps/ios-harbor-bank/README.md](apps/ios-harbor-bank/README.md) — iOS app
