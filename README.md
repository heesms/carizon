# Carizon Monorepo

Carizon delivers cross-platform used-car intelligence. This repository contains the Spring Boot
backend services, React web frontend, batch jobs, infrastructure stubs, and the OpenAPI contract
that ties them together.

## Repository structure

```
carizon/
  backend/        # Spring Boot multi-module project (api, core, jobs)
  web/            # React 18 + Vite + Tailwind frontend
  infra/          # Deployment stubs (nginx, docker)
  openapi/        # API contract (OpenAPI 3)
  README.md
```

### Backend modules

| Module | Description |
| ------ | ----------- |
| `api`  | HTTP APIs, security (Google OAuth), controllers, rate limiting stub |
| `core` | Domain entities, repositories, services, search providers, Flyway migrations |
| `jobs` | Batch and scheduled jobs for price snapshots & QC stubs |

The backend uses Flyway for schema management and expects the canonical DDL in
`backend/core/src/main/resources/db/migration/V20251010__initial.sql`.

### Frontend

The web app is built with React 18, TypeScript, Vite, Tailwind, Zustand, and shadcn-compatible UI
patterns. Routes are defined for customer and admin experiences and guard admin pages by role.

## Getting started

### Prerequisites

- Java 17
- Maven 3.9+
- Node.js 20+
- pnpm 8+
- MySQL 8 (or compatible)

### Environment variables

Backend (`backend/api`):

```
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
DB_URL=jdbc:mysql://localhost:3306/carizon
DB_USER=carizon
DB_PASSWORD=carizon
SESSION_COOKIE_NAME=CARIZON_SESSION
FEATURE_SEARCH_PROVIDER=db
REDIS_URL=redis://localhost:6379 (optional)
ES_URL=http://localhost:9200 (optional)
CDN_BASE_URL=http://localhost:8081 (optional)
```

Frontend (`web/.env`):

```
VITE_API_BASE_URL=/api
VITE_ENV=dev
```

### Backend setup

```bash
cd backend
mvn -pl api -am spring-boot:run
```

This launches the API service at `http://localhost:8080/api` with Google OAuth login.

To run scheduled jobs:

```bash
cd backend
mvn -pl jobs -am spring-boot:run
```

### Frontend setup

```bash
cd web
pnpm install
pnpm dev
```

Access the UI at `http://localhost:5173`.

### Linting & tests

```bash
cd backend
mvn clean verify

cd web
pnpm build
```

### OpenAPI client generation

```
# Update the contract if endpoints change
openapi/openapi_carizon.yaml

# Install your preferred OpenAPI generator and output to web/src/api
```

## Infrastructure stubs

- `infra/nginx/nginx.conf.sample`: CDN/cache configuration sample for image assets.
- `infra/docker/backend.Dockerfile` & `infra/docker/web.Dockerfile`: multi-stage Docker builds.
- `.github/workflows/ci.yml`: GitHub Actions pipeline for build, test, and container image builds.

## Contributing

1. Fork and clone the repository.
2. Create feature branches from `main`.
3. Run formatting (`mvn clean verify`, `pnpm build`) before submitting pull requests.
4. Update documentation when behaviour changes.

## License

All rights reserved.
