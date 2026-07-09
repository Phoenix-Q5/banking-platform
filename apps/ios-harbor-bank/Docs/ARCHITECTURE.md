# Harbor Bank iOS — Architecture

```
HarborBank (SwiftUI)
    │  HTTPS + Bearer JWT
    ▼
api-gateway (:8080)
    ├── /api/accounts
    ├── /api/transactions   ──► Kafka ──► notification-service ──► PUSH/IN_APP
    ├── /api/payments
    ├── /api/cards
    ├── /api/loans
    ├── /api/customers
    ├── /api/notifications  ◄── device tokens + alert inbox
    └── /api/audit

ops-agent (:8085)
    └── service incidents ──► notification-service (ADMIN/SUPPORT audience)
```

## Auth

Keycloak realm `banking`, public client `banking-mobile` (direct access grants for demo; production should use ASWebAuthenticationSession / Authorization Code + PKCE).

## Alerts

| Audience | Source | Examples |
|---|---|---|
| CUSTOMER | Kafka domain events | transfer completed/failed, payment, card freeze, loan status |
| ADMIN / SUPPORT | ops-agent webhook investigations | ServiceDown, CircuitBreakerOpen, HighErrorRate, HighLatency |

The Alerts tab switches inbox mode based on roles in the JWT.
