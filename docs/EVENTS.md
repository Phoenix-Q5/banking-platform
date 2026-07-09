# Harbor Bank domain events

Kafka topic: `harbor.bank.domain-events`

## Event envelope

```json
{
  "eventId": "uuid",
  "eventType": "transfer.completed",
  "aggregateType": "transaction",
  "aggregateId": "uuid",
  "customerId": "uuid",
  "occurredAt": "2026-07-09T01:00:00Z",
  "source": "transaction-service",
  "payload": { }
}
```

## Event types

| Type | Publisher | Alert? |
|---|---|---|
| `transfer.completed` | transaction-service | Yes (sender + receiver) |
| `transfer.failed` | transaction-service | Yes |
| `payment.completed` | payment-service | Yes |
| `account.opened` | account-service | Yes |
| `card.issued` / `card.frozen` / `card.unfrozen` | card-service | Yes |
| `loan.applied` / `loan.status_changed` | loan-service | Yes |

## Consumers

- **notification-service** — creates IN_APP + PUSH notifications; registers devices via `POST /api/notifications/devices`
- **audit-service** — appends immutable audit rows

## Local Kafka

Compose starts Bitnami Kafka (KRaft, no ZooKeeper) on `9092`.
Services use `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`.

Disable with `HARBOR_KAFKA_ENABLED=false` if needed.
