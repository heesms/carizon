# Carizon Scaffold v1

## Quick Start
1) `cd infra && docker compose up -d`  (Meilisearch:7700, CDN:8088)
2) `cd backend && ./mvnw spring-boot:run`
3) `cd frontend && npm i && npm run dev`

## Search API (Meilisearch)
- GET `http://localhost:8080/api/search`
  - q, maker, model, priceMin, priceMax, yearMin, yearMax, kmMax, sort(RECENT|LOW_PRICE|LOW_KM|NEW_YEAR), page, size
- Meili index: `cars` (document fields: id, maker, model, trim, year, km, priceMin, priceMax, region, bodyType, fuel, updatedAt, imageUrl, platforms[])

## Frontend
- `/search` : 필터/정렬 포함 메일리검색 → 카드 목록
- `/cars` : DB 기반 목록 (기존)- `/cars/:id` : 상세 + 가격히스토리
