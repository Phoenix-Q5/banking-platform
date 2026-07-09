# Harbor Bank — iOS App

Native SwiftUI mobile banking client for the [Harbor Bank platform](https://github.com/Phoenix-Q5/banking-platform).

Customers can bank like a real retail app (accounts, transfers, payments, cards, loans) and receive **transaction alerts**. Admins/support receive **service / ops alerts** from the platform ops-agent in the same inbox.

## Requirements

- macOS with Xcode 15+
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)
- Running Harbor Bank backend (`docker-compose up` in `banking-platform`)

## Quick start

```bash
# 1. Generate the Xcode project
xcodegen generate

# 2. Open in Xcode
open HarborBank.xcodeproj

# 3. Select an iPhone simulator and Run (⌘R)
```

### Pointing at your backend

Edit `HarborBank/Core/Networking/AppEnvironment.swift` (or set scheme env vars):

| Variable | Default | Purpose |
|---|---|---|
| `HARBOR_API_BASE` | `http://localhost:8080` | API gateway |
| `HARBOR_KEYCLOAK_BASE` | `http://localhost:8180` | Auth |
| `HARBOR_REALM` | `banking` | Keycloak realm |
| `HARBOR_CLIENT_ID` | `banking-mobile` | Public OIDC client |

> **Simulator tip:** `localhost` works for the iOS Simulator when the backend runs on your Mac. For a physical device, use your Mac’s LAN IP.

## Demo logins

| User | Password | Experience |
|---|---|---|
| `demo.customer` | `password` | Retail banking + transaction alerts |
| `demo.admin` | `password` | Admin console + **service alerts** |
| `demo.support` | `password` | Contact-center style lookup + service alerts |

## Features

### Customer
- Sign in with Keycloak (password grant / OIDC)
- Overview balances & recent activity
- Open accounts, transfer money
- Payments (ACH / wire / bill-pay) + beneficiaries
- Cards (issue, freeze/unfreeze)
- Loans (apply, track status)
- Alerts inbox (Kafka-driven transaction / payment / card / loan alerts)
- Push device registration (`IOS` platform → notification-service)

### Admin / Support
- Service alert inbox (ops-agent incidents: downtime, circuit breakers, latency, errors)
- Customer lookup
- KYC / loan decision actions (admin)
- Push registration for service alerts

## Push notifications

1. App registers an APNs device token (simulator uses a generated token for local/dev).
2. Token is posted to `POST /api/notifications/devices` with `platform=IOS`.
3. `notification-service` dispatches PUSH channel alerts from Kafka domain events (customers) and from ops-agent service incidents (admins).

Production: enable Push Notifications capability + real APNs key in Apple Developer.

## Repo layout

```
HarborBank/
  App/                 App entry + root navigation
  Core/                Auth, API client, models, push
  Features/            Login, Home, Accounts, Transfers, Payments,
                       Cards, Loans, Alerts, Admin
project.yml            XcodeGen spec
Docs/ARCHITECTURE.md   How the app talks to Harbor Bank
```

## Create this GitHub repository

If the empty GitHub repo does not exist yet (org/user needs to create it once):

```bash
# On GitHub: New repository → Phoenix-Q5/banking-platform-app (empty, no README)
git remote add origin https://github.com/Phoenix-Q5/banking-platform-app.git
git push -u origin main
```

## Related

- Backend platform: https://github.com/Phoenix-Q5/banking-platform
- Event docs: `docs/EVENTS.md` in the platform repo
