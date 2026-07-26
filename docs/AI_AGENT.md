# Harbor AI Agent — local Ollama + RAG

The `ai-agent` service is Harbor Bank’s **local code intelligence** layer:

- Runs entirely on your Mac via [Ollama](https://ollama.com) (1B–8B models)
- Indexes the banking-platform repo into a local vector store (embeddings)
- Answers architecture / bug / fix questions with retrieved source context (RAG)
- Designed so a later CI “code scan + deploy” pipeline can call the same APIs

Ops monitoring still lives in `ops-agent`. Both can share the same local Ollama instance.

## Mac setup (Apple Silicon)

```bash
# 1) Install Ollama (https://ollama.com/download) then:
ollama serve   # usually starts automatically as an app

# 2) Pull a small chat model + embedding model (fits typical 16–36GB Macs)
ollama pull llama3.2:3b
ollama pull nomic-embed-text

# Optional stronger chat (still laptop-friendly):
# ollama pull qwen2.5:7b
# ollama pull phi3:mini
```

Verify:

```bash
curl -s http://localhost:11434/api/tags | jq '.models[].name'
```

## Run ai-agent locally (recommended while developing)

From the repo root:

```bash
# Index path = repo root
export AI_RAG_ROOT="$(pwd)"
export AI_RAG_AUTO_INDEX=false
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_CHAT_MODEL=llama3.2:3b
export OLLAMA_EMBED_MODEL=nomic-embed-text

mvn -pl ai-agent -am spring-boot:run
```

UI: http://localhost:8095  
Swagger: http://localhost:8095/swagger-ui.html

Build the index once (first run embeds files — a few minutes):

```bash
curl -s -X POST http://localhost:8095/api/ai/index | jq
```

Ask:

```bash
curl -s http://localhost:8095/api/ai/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"How does ops-agent investigate Alertmanager webhooks?"}' | jq
```

Analyze a focus area:

```bash
curl -s http://localhost:8095/api/ai/analyze \
  -H 'Content-Type: application/json' \
  -d '{"focus":"transaction-service circuit breakers"}' | jq '.answer,.sources'
```

## Run with Docker Compose

Ollama stays on the **host**; containers reach it via `host.docker.internal`.

```bash
docker compose up -d --build ai-agent
# UI: http://localhost:8095
curl -s -X POST http://localhost:8095/api/ai/index | jq
```

Env knobs:

| Variable | Default | Purpose |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://host.docker.internal:11434` | Ollama endpoint |
| `OLLAMA_CHAT_MODEL` | `llama3.2:3b` | Chat / analysis model |
| `OLLAMA_EMBED_MODEL` | `nomic-embed-text` | Embedding model for RAG |
| `AI_RAG_ROOT` | `/workspace` (compose mount) | Repo path to index |
| `AI_RAG_AUTO_INDEX` | `false` in compose | Set `true` to index on boot |
| `AI_RAG_MAX_FILES` | `350` | Cap files for laptop speed |

## Point ops-agent at the same Ollama

```bash
export LLM_ENABLED=true
export LLM_PROVIDER=ollama
export LLM_BASE_URL=http://localhost:11434/v1   # or host.docker.internal in compose
export LLM_API_KEY=ollama
export LLM_MODEL=llama3.2:3b
docker compose up -d ops-agent
```

No cloud API key is required for Ollama.

## API surface

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Code-agent UI |
| GET | `/api/ai/health` | Ollama reachability + RAG chunk count |
| POST | `/api/ai/index` | (Re)build embeddings over the repo |
| POST | `/api/ai/ask` | RAG chat Q&A |
| POST | `/api/ai/analyze` | Structured code analysis over a focus area |

## What’s next (intentionally later)

- CI code-scan job calling `/api/ai/analyze`
- PR comment / fix suggestion pipeline
- Optional persisted vector DB (pgvector) instead of JSON file store

The integration above is the foundation those pipelines will call.
