# Car Search & Detail Feature Requirements

## 1. Domain Model Overview
The following database tables supply the vehicle catalogue and contextual metadata needed for the search and detail experiences.

| Table | Purpose | Key Columns |
| --- | --- | --- |
| `car_master` | Canonical record for each normalized vehicle currently tracked by the service. | `CAR_ID`, `CAR_NO`, maker/model/trim/grade codes, `YEAR`, `MILEAGE`, `PRICE`-related columns, `adv_status`, `region`, `displacement`, `BODY_TYPE`.
| `platform_car` | Platform-specific listing snapshot that maps back to a canonical car. | `PLATFORM_CAR_ID`, `PLATFORM_NAME`, `PLATFORM_CAR_KEY`, `CAR_NO`, normalized maker/model/trim names, `PRICE`, `KM`, `YYMM`, `STATUS`, `region`, `extra`.
| `car_price_history` | Historical price checks for a platform listing. | `HISTORY_ID`, `PLATFORM_CAR_ID`, `PRICE`, `CHECKED_AT`, `is_current`.
| `daily_listing_snapshot` | Daily summary of listing status per platform. | `snap_date`, `source`, `source_key`, `price`, `status`.
| `cz_*` reference tables | Lookup tables for makers, models, trims, grades, and platform priorities. | Codes and normalized names per dimension.
| `raw_*` tables | Raw ingestion payloads per source platform that may enrich detail screens (images, accident info, etc.). | Platform-specific columns (`payload`, platform-specific identifiers, status fields).

## 2. Frontend Requirements
### 2.1 Vehicle Search Screen
- **Filter Controls**
  - Text search by vehicle name / number (`CAR_MASTER.MODEL_CODE`, `MODEL_NAME`, `CAR_NO`).
  - Select inputs for maker, model group, model, trim, grade using `cz_maker`, `cz_model_group`, `cz_model`, `cz_trim`, `cz_grade`.
  - Range sliders or min/max inputs for year (`YEAR`), mileage (`MILEAGE`), price (latest `PRICE` from `platform_car` / `car_price_history`), displacement, registration month/year (`YYMM`).
  - Dropdowns for fuel, transmission, body type, region and advertisement status (`adv_status`).
  - Toggle for platforms (`platform_car.PLATFORM_NAME`) and sale status.
- **Result List**
  - Display canonical vehicle name (composed from maker/model/trim), year, mileage, and highlight current price.
  - Badges for platform count, sale status, body type, fuel.
  - Show latest update timestamp (`UPDATED_AT` or `last_seen_date`).
- **Pagination & Sorting**
  - Sorting options: price (asc/desc), newest listing (`CREATED_AT`), mileage, year.
  - Paginate or infinite scroll with API support for `page`/`limit`.

### 2.2 Vehicle Detail Screen
- **Header**
  - Canonical vehicle identifiers and main thumbnail (from `platform_car.extra` or `raw_*` payloads).
  - Current best price across platforms and status (onsale/sold).
- **Specification Section**
  - Year, mileage, color, transmission, fuel, body type, displacement, region.
  - Maker/model/trim hierarchy with normalized names (join to `cz_*` tables).
- **Platform Listings**
  - Table of platforms with price, mileage, status, platform-specific URLs (`M_URL`, `PC_URL`), last seen date.
  - Priority ordering by `cz_platform_priority`.
- **Price History**
  - Chart of price over time using `car_price_history` (`CHECKED_AT`, `PRICE`, `is_current`).
  - Daily snapshot overlay from `daily_listing_snapshot` when needed.
- **Additional Details**
  - Raw payload enrichments (accident info, options, dealer info) extracted from `raw_*` tables keyed by platform-specific IDs.
  - Crawl metadata (last crawl run from `crawl_run`).

## 3. Backend API Design
### 3.1 Search API (`GET /api/cars`)
- **Query Parameters**
  - `q` (string), `makerCode`, `modelGroupCode`, `modelCode`, `trimCode`, `gradeCode`.
  - Numeric ranges: `yearFrom`, `yearTo`, `mileageFrom`, `mileageTo`, `priceFrom`, `priceTo`, `displacementFrom`, `displacementTo`.
  - `fuel`, `transmission`, `bodyType`, `region`, `advStatus`, `platform`, `status`.
  - Sorting: `sortBy` (price|createdAt|mileage|year), `sortDir` (asc|desc).
  - Pagination: `page`, `pageSize`.
- **Response**
  - `items`: array containing canonical vehicle summary (id, displayName, year, mileage, price, heroImage, platformCount, lastSeenDate).
  - `total`: total matched vehicles.
  - `aggregations`: optional counts per filter for faceted UI.

### 3.2 Detail API (`GET /api/cars/{carId}`)
- **Response Sections**
  - `car`: canonical metadata (`car_master` columns).
  - `platformListings`: list of platform-specific entries sorted by priority, including URLs and statuses.
  - `priceHistory`: chronological array with price, checkedAt, isCurrent.
  - `dailySnapshots`: optional aggregated view by date.
  - `enrichments`: structured data from raw payloads (options, accident history, dealer info, media assets).
  - `crawlInfo`: latest crawl run status per platform for debugging.

### 3.3 Supporting Endpoints
- `GET /api/lookups/makers`, `/models`, `/trims`, `/grades` for populating filters.
- `GET /api/platforms/priorities` for UI ordering.
- (Optional) `GET /api/cars/{carId}/recommendations` leveraging similarity by model/price band.

## 4. Data Integration & Sync Considerations
- **Ingestion**: `raw_*` tables store platform payloads and are normalized into `platform_car` and `car_master`. Confirm ETL schedule and ensure `last_seen_date` updates during each crawl.
- **Deduplication**: Ensure mapping between `platform_car.PLATFORM_CAR_ID` and `car_master.CAR_ID` via `cz_code_map` and manual overrides (`cz_forced_map`).
- **History Tracking**: Maintain `car_price_history.is_current` to quickly fetch latest price; nightly job to mark superseded records.
- **Archival**: `platform_car_bak` retains removed listings; design API to optionally surface sold vehicles.

## 5. Non-Functional Requirements
- **Performance**: Index frequently filtered columns (`YEAR`, `MILEAGE`, `PRICE`, maker/model codes, `adv_status`, `region`). Consider caching hot search results.
- **Security & Access Control**: If dealer-only data exists, protect with role-based auth. Log sensitive access to raw payloads.
- **Observability**: Instrument APIs to log queries, include crawl run references for debugging inconsistent data.
- **Internationalization**: Ensure front-end text resources allow Korean localization; data values already stored in UTF-8.

## 6. Outstanding Questions
1. Should canonical price derive from a preferred platform (`cz_platform_priority`) or min/max across platforms?
2. Are there dealer-only attributes from raw payloads that require masking on customer-facing screens?
3. What is the expected SLA for data freshness per platform crawl run?
4. Do we need user personalization (saved cars, viewed history) for MVP?
5. What analytics events must be emitted from the search and detail pages?

Clarifying these items will finalize API contracts, caching strategy, and UX scope for the customer-facing car search experience.
