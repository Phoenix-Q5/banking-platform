# Harbor AI Agent — local Ollama + RAG

The `ai-agent` service is Harbor Bank’s **local code intelligence** layer:

- Runs LLMs via **Ollama in Docker** (or optionally on the Mac host)
- Indexes the banking-platform repo into a local vector store (embeddings)
- Answers architecture / bug / fix questions with retrieved source context (RAG)
- Designed so a later CI “code scan + deploy” pipeline can call the same APIs

Ops monitoring still lives in `ops-agent`. Both share the compose `ollama` service by default.

## Recommended: Ollama in Docker Compose

No Mac app install required. Compose starts `ollama` + pulls models once via `ollama-init`.

```bash
docker compose up -d --build ollama ollama-init ai-agent
```

- Ollama API: http://localhost:11434  
- AI Agent UI: http://localhost:8095  

First boot downloads `llama3.2:1b` and `nomic-embed-text` into the `ollama-data` volume (can take several minutes).

> **Note:** `llama3.2:3b` often gets OOM-killed inside Docker Desktop on Macs with limited RAM
> (`signal: killed` → HTTP 500). Default is **1B**. To use 3B, give Docker ≥8GB memory and set
> `OLLAMA_CHAT_MODEL=llama3.2:3b`.

Then index + ask:

```bash
curl -s -X POST http://localhost:8095/api/ai/index | jq
curl -s http://localhost:8095/api/ai/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"How does ops-agent investigate Alertmanager webhooks?"}' | jq
```

### Why you saw `host.docker.internal … Connection refused`

That error means **nothing was listening on the Mac host at :11434**. Either:

1. Ollama wasn’t installed/running on the Mac, or  
2. The Mac Ollama app was bound only to `127.0.0.1` (Docker can’t reach that via `host.docker.internal`)

Compose now defaults to **`http://ollama:11434`** on the Docker network, which avoids that class of failure.

### Optional: use Mac-native Ollama instead

```bash
# Install from https://ollama.com/download
# Bind all interfaces so Docker Desktop can reach it:
launchctl setenv OLLAMA_HOST "0.0.0.0:11434"
# Quit & reopen Ollama.app, then:
ollama pull llama3.2:1b
ollama pull nomic-embed-text

# Point ai-agent at the host:
OLLAMA_BASE_URL=http://host.docker.internal:11434 docker compose up -d ai-agent
```

## Run ai-agent as a local JVM (talks to compose Ollama or Mac Ollama)

```bash
export AI_RAG_ROOT="$(pwd)"
export AI_RAG_AUTO_INDEX=false
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_CHAT_MODEL=llama3.2:1b
export OLLAMA_EMBED_MODEL=nomic-embed-text

mvn -pl ai-agent -am spring-boot:run
```

## Point ops-agent at the same Ollama

```bash
export LLM_ENABLED=true
export LLM_PROVIDER=ollama
export LLM_BASE_URL=http://ollama:11434/v1   # inside compose
export LLM_API_KEY=ollama
export LLM_MODEL=llama3.2:1b
docker compose up -d ops-agent
```

## API surface

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Code-agent UI |
| GET | `/api/ai/health` | Ollama reachability + RAG chunk count |
| POST | `/api/ai/index` | (Re)build embeddings over the repo |
| POST | `/api/ai/ask` | RAG chat Q&A |
| POST | `/api/ai/analyze` | Structured code analysis over a focus area |

## Env knobs

| Variable | Default (compose) | Purpose |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://ollama:11434` | Ollama endpoint |
| `OLLAMA_CHAT_MODEL` | `llama3.2:1b` | Chat / analysis model (use `3b` only with ≥8GB Docker RAM) |
| `OLLAMA_EMBED_MODEL` | `nomic-embed-text` | Embedding model for RAG |
| `OLLAMA_NUM_CTX` | `2048` | Context window (lower = less RAM) |
| `OLLAMA_NUM_PREDICT` | `512` | Max generated tokens |
| `AI_RAG_ROOT` | `/workspace` | Repo path to index |
| `AI_RAG_AUTO_INDEX` | `false` | Set `true` to index on boot |
| `AI_RAG_MAX_FILES` | `350` | Cap files for laptop speed |

## What’s next (intentionally later)

- CI code-scan job calling `/api/ai/analyze`
- PR comment / fix suggestion pipeline
- Optional persisted vector DB (pgvector) instead of JSON file store
