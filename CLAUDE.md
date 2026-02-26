# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Carizon is a Korean used-car aggregation platform that crawls 6+ platforms (Encar, Kcar, Chacha, Chutcha, Charancha, Tcar), merges listings into a unified dataset, provides search via Elasticsearch, and offers RAG-based car recommendations using LangChain4j + Chroma + Ollama.

## Repository Structure

- **backend/** — Spring Boot 3.3.2 (Java 21, Maven). Main API server.
- **backend/admin-frontend/** — Admin UI (React 18, TypeScript, Ant Design, Vite). Gets embedded into the backend JAR via `build.sh`.
- **frontend/** — User-facing frontend (React 18, TypeScript, Vite, TailwindCSS).
- **crawler/** — Python crawler (requests, pymysql).
- **infra/** — Docker Compose configs and Caddyfile for deployment.

## Build & Run Commands

### Backend
```bash
cd backend
./mvnw spring-boot:run                  # Dev mode (port 8080)
mvn -q -B -DskipTests package           # Build JAR
./build.sh                              # Full build: admin-frontend + backend JAR
```

### Frontend (User)
```bash
cd frontend
npm i && npm run dev                    # Dev mode (port 3000)
npm run build                           # Production build
```

### Admin Frontend
```bash
cd backend/admin-frontend
npm install && npm run dev              # Dev mode (port 3000)
npm run build                           # Production build
```

### Full Stack via Docker Compose
```bash
cd backend && docker compose up -d      # All services: MySQL, Redis, ES, Kafka, Chroma, Ollama, app, frontend, admin
```

### Infrastructure Only (lightweight)
```bash
cd infra && docker compose up -d        # Meilisearch + CDN only
```

## Key Infrastructure Services

| Service         | Port  | Purpose                    |
|-----------------|-------|----------------------------|
| MySQL 8.0       | 3306  | Primary database           |
| Redis 7         | 6379  | Cache                      |
| Elasticsearch   | 9200  | Car search index           |
| Kafka           | 9092  | Index sync events          |
| Chroma          | 8000  | Vector DB for RAG          |
| Ollama          | 11434 | Local LLM & embeddings     |
| Meilisearch     | 7700  | Legacy search (optional)   |

## Backend Architecture

### Package Layout (`com.carizon`)
- **admin/** — Admin API controllers (`/admin/**`)
- **api/** — Public user API controllers (`/api/**`)
- **batch/** — Batch job orchestration (crawl, merge, embed, index workflows)
- **crawler/** — Platform-specific crawlers (Encar, Kcar, Chacha, etc.)
- **mapping/** — Code normalization & maker/model mapping
- **merge/** — Multi-platform data consolidation into `car_master`
- **rag/** — RAG recommendation engine (LangChain4j, Chroma, Ollama)
- **search/** — Elasticsearch integration + Kafka sync
- **recommendation/** — Weekly best cars, blog posts, WordPress integration
- **common/** — `ApiResponse` wrapper, `GlobalExceptionHandler`, shared HTTP client
- **config/** — Spring configs (Security, Redis, WebClient, OpenAPI)
- **integration/** — External service integration (model images)

### Data Pipeline
1. **Crawl**: Platform APIs → `raw_*` tables (per-platform)
2. **Merge**: `raw_*` → `platform_car` (normalized) → `car_master` (deduplicated by `car_no`)
3. **Price History**: Daily snapshots in `car_price_history`
4. **Index**: `car_master` → Elasticsearch (full/incremental reindex)
5. **Embed**: `car_master` → vector embeddings → Chroma (for RAG)
6. **Recommend**: User query → vector search → LLM-generated recommendation

### API Response Format
All APIs return `ApiResponse<T>`: `{ success, message, data, timestamp, errorCode }`.

### Health Endpoints
- `/actuator/health`, `/api/health`, `/api/recommendations/health`, `/api/model-images/health`

## Database

- **MySQL 8.0**, schema: `carizon`
- Migrations in `backend/src/main/resources/db/migrations/` (001–015)
- Key tables: `car_master`, `platform_car`, `car_price_history`, `crawl_run`, `llm_prompt_config`, `llm_matching_config`
- MyBatis mappers in `backend/src/main/resources/mapper/`

## Configuration

- Backend config: `backend/src/main/resources/application.yaml`
- Environment overrides: `SPRING_DATASOURCE_*`, `SPRING_ELASTICSEARCH_*`, `RAG_*`, `KAFKA_*`
- LLM prompts and recommendation weights are stored in DB and adjustable at runtime via admin API
- Kafka is disabled by default (`app.kafka.enabled: false`)

## CI/CD

GitHub Actions workflows in `.github/workflows/`:
- **deploy-backend.yml** — Build JAR → Docker → GHCR → SSH deploy (triggers on `backend/**` or `infra/**` changes to main)
- **deploy-frontend.yml** — Build → Docker → GHCR → SSH deploy (triggers on `frontend/**` changes to main)
- **deploy-crawler.yml** — Docker → GHCR → SSH deploy (triggers on `crawler/**` changes to main)

## Frontend Notes

- **User frontend** uses React Router: `/search`, `/cars`, `/cars/:id`, `/`
- **Admin frontend** pages: Dashboard, Crawl, Data, Embedding, Search, Config, Batch, Pipeline, CarizonCodes, CodeMapping, BlogPost
- Admin uses Zustand (client state) + React Query (server state)
- Admin API client configured in `backend/admin-frontend/src/api/client.ts`
