# Carizon Monorepo
- backend: Spring Boot 3 (Java 21) /api/hello
- frontend: React + Vite (`/api` 프록시)
- crawler: Python requests 배치(샘플 JSON→MySQL)
- infra: Docker Compose(Caddy/Backend/MySQL/OpenSearch/Redis)
- CI/CD: GitHub Actions → Oracle VM(SSH) 자동배포
22