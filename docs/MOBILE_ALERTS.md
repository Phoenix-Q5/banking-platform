# Harbor Bank mobile alerts

## Customer transaction alerts

Domain services publish Kafka events → `notification-service` creates
`IN_APP` + `PUSH` notifications for the customer id(s) involved.

The iOS app registers an `IOS` device token under the customer's id.

## Admin / support service alerts

When ops-agent finishes investigating an Alertmanager incident it calls:

```
POST http://notification-service:8087/api/notifications/internal/service-alert
```

Notifications are stored under the well-known audience id:

```
00000000-0000-4000-8000-0000000000aa
```

Admin/support devices register against that same audience id, so they receive
service alerts (ServiceDown, CircuitBreakerOpen, HighErrorRate, …) in the
mobile **Alerts → Service** inbox.
