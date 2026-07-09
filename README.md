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
| `demo.admin` | `password` | Admin / KYC / loans |
| `demo.support` | `password` | Contact center |

## Try a live transfer alert

1. Sign in as `demo.customer` at http://localhost:3001
2. Open an account (and a second one, or use another account id)
3. Send a transfer from Overview
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
