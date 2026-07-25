# Ops Agent — Installable LLM monitoring & troubleshooting plugin

The `ops-agent` module is a shippable Spring Boot service you plug into the banking
platform (or any Spring microservice estate that exposes Prometheus / Loki / Tempo /
Actuator). It turns alerts and operator questions into investigated incident reports
with mitigation playbooks.

## What it does

1. **Ingests alerts** from Alertmanager (`POST /api/agent/webhooks/alertmanager`)
2. **Investigates** by calling tools:
   - `prometheus_query` — PromQL for up/error/latency/circuit-breaker metrics
   - `loki_query` — LogQL for ERROR stacks and failure context
   - `tempo_search` — recent traces for slow/error spans
   - `service_health` — Spring `/actuator/health` (DB + downstream)
   - `circuit_breaker_state` — Resilience4j breaker state/events
3. **Reasons** with either:
   - an OpenAI-compatible LLM (`LLM_ENABLED=true` + `LLM_API_KEY`), or
   - a built-in **heuristic SRE engine** (default — works offline)
4. **Proposes mitigation playbooks** for service-down, circuit-breaker-open,
   high-error-rate / latency, and generic degradation
5. **Reports** markdown incident summaries via API and a lightweight console UI
6. **Command Center POC** (`/command-center.html`) — live cards for health,
   slow endpoints, JVM/GC, Tempo traces, and alerts, with one-click LLM explain

## Install into this platform

Already wired in `docker-compose.yml`. Start the stack:

```bash
docker-compose up -d --build
```

Ops agent console: http://localhost:8085  
Command Center POC: http://localhost:8085/command-center.html

### Enable a real LLM (optional)

```bash
export LLM_ENABLED=true
export LLM_API_KEY=sk-...
export LLM_BASE_URL=https://api.openai.com/v1   # or Ollama / Azure-compatible gateway
export LLM_MODEL=gpt-4o-mini
docker-compose up -d ops-agent
```

Without an API key the agent stays fully functional on the heuristic engine.

## Install into another application

Treat `ops-agent` as a sidecar/plugin:

1. Point env vars at your observability backends and service base URLs:
   - `PROMETHEUS_URL`, `LOKI_URL`, `TEMPO_URL`
   - `API_GATEWAY_URL`, `ACCOUNT_SERVICE_URL`, `TRANSACTION_SERVICE_URL`
     (or replace `ops-agent.services.*` in `application.yml` with your service map)
2. Route Alertmanager webhooks to
   `http://<ops-agent-host>:8085/api/agent/webhooks/alertmanager`
3. Optionally set `MITIGATION_MODE=auto` to execute safe automated checks
   (health/log/trace probes). Destructive actions always stay manual.

## API surface

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Operator console UI (incidents) |
| GET | `/command-center.html` | **Command Center POC** — live metrics cards + LLM explain |
| GET | `/api/agent/health-summary` | Engine status + tool catalog |
| GET | `/api/agent/tools` | Tool schemas for LLM/MCP-style clients |
| GET | `/api/agent/monitoring/snapshot` | Live health / endpoints / JVM / traces / alerts snapshot |
| POST | `/api/agent/monitoring/explain` | LLM (or heuristic) briefing over the live snapshot |
| POST | `/api/agent/investigate` | Start async investigation |
| POST | `/api/agent/chat` | Sync investigate from natural language |
| GET | `/api/agent/incidents` | List incidents |
| GET | `/api/agent/incidents/{id}` | Incident detail |
| GET | `/api/agent/incidents/{id}/report` | Markdown/JSON report |
| POST | `/api/agent/incidents/{id}/mitigations/{actionId}/approve` | Approve a mitigation step |
| POST | `/api/agent/webhooks/alertmanager` | Alertmanager webhook receiver |

### Command Center POC

Open http://localhost:8085/command-center.html after the stack is up. The page:

1. Polls `GET /api/agent/monitoring/snapshot` (Prometheus RED + JVM/GC, Tempo slow traces, Loki ERROR lines, firing alerts)
2. Surfaces warning/critical signals and suggested prompts
3. Calls `POST /api/agent/monitoring/explain` so the ops-agent investigates with the same tools used for incidents

```bash
# Live snapshot cards
curl -s http://localhost:8085/api/agent/monitoring/snapshot | jq '.overallStatus, .summary, .slowestEndpoints[:3]'

# Ask the agent to brief the platform (heuristic works offline; set LLM_ENABLED for real LLM)
curl -s http://localhost:8085/api/agent/monitoring/explain \
  -H 'Content-Type: application/json' \
  -d '{"focus":"endpoints","message":"Which APIs are slowest and why?"}' | jq '.reply'
```

### Example: chat investigation

```bash
curl -s http://localhost:8085/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"transaction-service circuit breaker is open and transfers are failing"}' | jq
```

### Example: simulate Alertmanager

```bash
curl -s -X POST http://localhost:8085/api/agent/webhooks/alertmanager \
  -H 'Content-Type: application/json' \
  -d '{
    "status":"firing",
    "alerts":[{
      "status":"firing",
      "fingerprint":"demo-cb-1",
      "labels":{
        "alertname":"CircuitBreakerOpen",
        "severity":"critical",
        "category":"resiliency",
        "application":"transaction-service"
      },
      "annotations":{
        "summary":"Circuit breaker open on transaction-service",
        "description":"Downstream calls are failing fast"
      }
    }]
  }'
```

## Safety model

- Default `MITIGATION_MODE=recommend` — agent proposes, humans approve
- Automated steps are limited to **read-only probes** (health, metrics, logs, traces)
- No credentials rotation, data deletion, or auth bypass actions are exposed
- LLM failures automatically fall back to the heuristic engine

## Local run (without Docker)

```bash
mvn -pl ops-agent -am spring-boot:run
```

Defaults assume Prometheus/Loki/Tempo and the three banking services on localhost ports
from the main README.
