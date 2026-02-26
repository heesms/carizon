# Carizon 요건서 (Backend / Admin / Frontend)
본 문서는 Carizon 시스템을 **소스 코드를 직접 열람하지 않고도** 재구현할 수 있도록, 기능·API·데이터·인프라·운영 요구사항을 한 곳에 정리한 요건서입니다.

## 1) 목적과 범위
- **목적**: 중고차(플랫폼 수집 → 정규화/머지 → 검색/추천 제공) 서비스를 백엔드/사용자 프론트/관리자 어드민까지 포함해 재구현 가능하도록 명세 제공
- **범위(모듈)**:
  - **Backend**: 크롤링, 머지, 검색(ES/Meili/DB), RAG 추천(Chroma+LLM), 코드/매핑 관리, 배치/파이프라인
  - **Frontend(사용자)**: 검색/필터/상세/추천/좋아요/위클리베스트 조회
  - **Admin(관리자)**: 크롤링/머지 실행, 데이터 조회, 임베딩/검색 인덱싱 실행, 설정/프롬프트 관리, 파이프라인 실행

## 2) 용어 정의
- **Platform**: 원천 중고차 플랫폼(예: ENCAR, KCAR, CHACHACHA, CHUTCHA, CHARANCHA, TCAR)
- **raw_* 테이블**: 플랫폼 원천 payload(원문 JSON 등) 저장 영역 (예: `raw_encar`)
- **platform_car**: 플랫폼별 정규화된 차량 레코드(원천별 중복 존재 가능)
- **car_master**: 플랫폼 데이터를 통합(머지)한 대표 차량 엔티티(사용자 화면의 기본 단위)
- **코드/매핑**: 플랫폼 코드 → Carizon 표준 코드(메이커/모델/트림/등급 등) 매핑
- **Embedding**: 차량 텍스트를 벡터화하여 Chroma에 저장
- **RAG 추천**: 사용자 질의 → (벡터 검색+필터) → LLM 응답 생성

## 3) 시스템 구성(권장 인프라)
### 3.1 런타임 서비스와 포트(로컬 기준)
| 구분 | 서비스 | 역할 | 기본 포트 |
|---|---|---|---|
| 앱 | backend(app) | Spring Boot API 서버 | 8080 |
| 앱 | frontend | 사용자 웹 | 3000 |
| 앱 | admin | 관리자 웹 | 3001 |
| DB | mysql | 메인 DB | 3306 |
| 캐시 | redis | 캐시/세션성 데이터(선택) | 6379 |
| 검색 | elasticsearch | 차량 검색 인덱스 | 9200 |
| 검색(선택) | meilisearch | (레거시/선택) | 7700 |
| 메시징(선택) | kafka | 인덱스 동기화 이벤트 | 9092 |
| RAG | chroma | 벡터 DB | 8000 |
| RAG(선택) | ollama | LLM/임베딩 모델 서버 | 11434 |

### 3.2 기술 스택(구현 요구)
- **Backend**
  - Java 21, Spring Boot 3.3.x
  - MySQL 8.0, (선택) Redis, Elasticsearch 8.x, (선택) Kafka
  - OpenAPI(Swagger UI) 제공
  - RAG: LangChain4j + Chroma + (Ollama 또는 HuggingFace)
- **Frontend(사용자)**: React 18 + Vite + TypeScript + React Router + TailwindCSS
- **Admin(관리자)**: React 18 + Vite + TypeScript + Ant Design + Axios + React Query + Zustand + Recharts

## 4) 보안/권한(현행 + 목표)
### 4.1 현행(개발 편의)
- **모든 요청 permitAll**, CSRF off, CORS 허용(origin whitelist)

### 4.2 목표(운영 요구사항)
- **관리자 API 보호**:
  - `/admin/**` 및 `/api/admin/**`는 인증 필수
  - 최소 역할: `ADMIN`(운영), `OPS`(배치/크롤만), `VIEWER`(읽기 전용)
- **인증 방식**(택1)
  - 세션 기반 + 사내 SSO 연동, 또는
  - JWT Bearer 토큰
- **감사 로그**: 관리자 실행(크롤/머지/인덱싱/임베딩/프롬프트 변경)은 누가/언제/무엇을 실행했는지 기록

## 5) 공통 API 규격
### 5.1 응답 포맷
모든 API는 아래 형식(또는 호환되는 JSON)을 반환해야 합니다.

```json
{
  "success": true,
  "message": "optional",
  "data": { },
  "timestamp": "2026-02-17T00:00:00",
  "errorCode": "optional"
}
```

### 5.2 에러 처리
- 클라이언트는 `success=false` 및 `message`로 사용자 메시지 표시 가능해야 함
- 서버는 4xx/5xx 시에도 표준 포맷을 가능한 범위에서 유지

## 6) 사용자 프론트엔드(Frontend) 요구사항
### 6.1 라우팅/화면
- **`/` Home**
  - 서비스 소개, 빠른 검색 진입, 추천 섹션(선택)
- **`/search` 검색**
  - 키워드/필터/정렬/페이징
  - 결과를 카드 리스트로 표시(대표 이미지, 메이커/모델/트림, 연식, 주행거리, 가격 범위, 지역 등)
- **`/cars/:id` 상세**
  - `car_master` 기반 상세 + 플랫폼별 매물 리스트 + 링크(PC/모바일)
  - 대표 이미지 1장 이상
- **`/recommendation` 추천**
  - 사용자 질의 입력 → 추천 결과(설명 + 차량 리스트)
  - 각 차량에 좋아요 기능 표시

### 6.2 사용자가 사용하는 주요 API
#### 6.2.1 차량 목록(DB 기반)
- **GET `/api/cars`**
  - Query: 다양한 필터/정렬/페이지 파라미터(서버가 `Map<String,Object>`로 수신 가능하도록 유연하게 처리)
  - Response `data` 예시:
    - `totalElements`: 전체 개수
    - `content`: `CarListItemDto[]`
      - `carId`, `maker`, `model`, `trim`, `year`, `km`, `priceMin`, `priceMax`, `priceUpdatedAt`
      - `representativeImageUrl`, `modelCode`, `fuel`, `region`

#### 6.2.2 차량 상세
- **GET `/api/cars/{carId}`**
  - Response `data`는 최소 아래를 포함:
    - `car`: 차량 기본 정보(메이커/모델/트림/연식/연료/지역 등)
    - `platformRows`: 플랫폼별 상세 행(최소 `CarDetailRow[]` 호환)
    - `priceHistory`: (선택) 가격 히스토리

#### 6.2.3 코드 조회(필터 UI 지원)
- **GET `/api/codes/makers`**
- **GET `/api/codes/model-groups`**
- **GET `/api/codes/models`**
- **GET `/api/codes/trims`**
- **GET `/api/codes/grades`**

#### 6.2.4 모델 이미지
- **GET `/api/models/{modelCode}/images`**
  - 목적: 추천/상세 화면에서 모델 대표 이미지를 제공

#### 6.2.5 검색 API (인덱스 기반)
- **GET `http://localhost:8080/api/search`**
  - Query: `q, maker, model, priceMin, priceMax, yearMin, yearMax, kmMax, sort(RECENT|LOW_PRICE|LOW_KM|NEW_YEAR), page, size`
  - 인덱스 문서 필드(최소): `id, maker, model, trim, year, km, priceMin, priceMax, region, bodyType, fuel, updatedAt, imageUrl, platforms[]`
  - 비고: 검색 모드(ES/Meili/DB)는 관리자 설정으로 전환 가능해야 함

#### 6.2.6 RAG 추천
- **POST `/api/recommendations`**
  - Body: `RecommendationRequest`
    - `query`(필수), `maxResults`(기본 5), 가격/제조사/연료/차종/연식 등 필터, `intent`(선택)
  - Response: `RecommendationResponse`
    - `recommendation`(LLM 서술), `cars[]`(추천 차량 리스트 + 점수/이유/링크/이미지)
- **GET `/api/recommendations/health`**

#### 6.2.7 좋아요
- **POST `/api/likes/{carId}`**: IP 기반으로 1회만 반영(서버 정책)
- **GET `/api/likes/{carId}`**: count + liked
- **POST `/api/likes/batch`**: `{ "carIds": [1,2,3] }` → `{ carId: count }`

#### 6.2.8 위클리베스트(추천 콘텐츠)
- **GET `/api/recommendation/weekly-best/all`**
- **GET `/api/recommendation/weekly-best/models/{modelCode}`**
- **GET `/api/recommendation/weekly-best/blog-content/{modelCode}`**

### 6.4 주요 API JSON 예시
#### 6.4.1 차량 목록 예시
- **요청**
```json
GET /api/cars?maker=HYUNDAI&model=SONATA&yearMin=2018&yearMax=2022&kmMax=80000&sort=LOW_PRICE&page=0&size=20
```
- **응답(`data`)**
```json
{
  "success": true,
  "data": {
    "totalElements": 1234,
    "page": 0,
    "size": 20,
    "content": [
      {
        "carId": 1000123,
        "maker": "HYUNDAI",
        "model": "SONATA",
        "trim": "1.6 터보 스마트",
        "year": 2020,
        "km": 35000,
        "priceMin": 1800,
        "priceMax": 1850,
        "priceUpdatedAt": "2026-02-17T10:15:00",
        "representativeImageUrl": "https://cdn.carizon.com/images/1000123/main.jpg",
        "modelCode": "HY_SONATA_DL3",
        "fuel": "가솔린",
        "region": "서울"
      }
    ]
  }
}
```

#### 6.4.2 차량 상세 예시
- **요청**
```json
GET /api/cars/1000123
```
- **응답(`data`)**
```json
{
  "success": true,
  "data": {
    "car": {
      "carId": 1000123,
      "carNo": "12가3456",
      "maker": "HYUNDAI",
      "model": "SONATA",
      "trim": "1.6 터보 스마트",
      "year": 2020,
      "mileage": 35000,
      "fuel": "가솔린",
      "transmission": "자동",
      "color": "화이트",
      "bodyType": "세단",
      "region": "서울",
      "priceNew": 3200
    },
    "platformRows": [
      {
        "carId": 1000123,
        "platformName": "ENCAR",
        "platformCarId": 987654321,
        "price": 1850,
        "status": "ONSALE",
        "pcUrl": "https://www.encar.com/dc/dc_cardetailview.do?carid=987654321",
        "mUrl": "https://m.encar.com/view/987654321",
        "lastSeenDate": "2026-02-17",
        "representativeImageUrl": "https://ci.encar.com/carpicture/...jpg"
      }
    ],
    "priceHistory": [
      { "checkedAt": "2026-02-01T09:00:00", "price": 1950 },
      { "checkedAt": "2026-02-10T09:00:00", "price": 1900 },
      { "checkedAt": "2026-02-17T09:00:00", "price": 1850 }
    ]
  }
}
```

#### 6.4.3 추천 API 예시
- **요청**
```json
POST /api/recommendations
Content-Type: application/json

{
  "query": "가족 4명이 탈 SUV, 예산 2천만원대, 연식은 18년 이상",
  "maxResults": 5,
  "minPrice": 1500,
  "maxPrice": 2500,
  "bodyTypeFilter": "SUV",
  "minYear": 2018,
  "intent": "FAMILY"
}
```
- **응답(`data`)**
```json
{
  "success": true,
  "data": {
    "recommendation": "가족 4명이 편안하게 탈 수 있는 SUV로, 현대 싼타페와 기아 쏘렌토를 중심으로 추천드립니다...",
    "cars": [
      {
        "carId": 2000456,
        "maker": "HYUNDAI",
        "model": "SANTAFE",
        "trim": "2.0 디젤 프리미엄",
        "year": 2019,
        "mileage": 42000,
        "price": 2300,
        "fuel": "디젤",
        "transmission": "자동",
        "color": "화이트",
        "region": "경기",
        "pcUrl": "https://www.encar.com/...",
        "mUrl": "https://m.encar.com/...",
        "imageUrl": "https://cdn.carizon.com/images/2000456/main.jpg",
        "relevanceScore": 0.89,
        "reason": "예산 내 4인 가족 SUV, 비교적 최신 연식과 적당한 주행거리"
      }
    ]
  }
}
```

#### 6.4.4 좋아요 API 예시
- **좋아요 추가 요청**
```json
POST /api/likes/2000456
```
- **응답**
```json
{
  "success": true,
  "data": {
    "liked": true,
    "count": 12
  }
}
```

#### 6.4.5 임베딩 상태 조회 예시
- **요청**
```json
GET /admin/embedding/status
```
- **응답(`data`)**
```json
{
  "success": true,
  "data": {
    "collection": {
      "count": 12345
    },
    "progress": {
      "running": true,
      "processed": 300,
      "total": 5000,
      "ok": 298,
      "fail": 2,
      "startedAt": "2026-02-17T10:00:00"
    },
    "samples": [
      {
        "carId": 1000123,
        "maker": "HYUNDAI",
        "model": "SONATA",
        "year": 2020
      }
    ]
  }
}
```

### 6.3 UX 요구사항(핵심)
- 검색은 **필터 변경 시 즉시 반영** 또는 “적용” 버튼(둘 중 하나로 일관)
- 결과 카드에서 **가격/연식/주행거리**가 한 눈에 보여야 함
- 상세에서 플랫폼 링크는 **PC/모바일 구분** 제공

## 7) 관리자(Admin) 요구사항
### 7.1 메뉴/화면
- **Dashboard (`/dashboard`)**
  - 전체 차량 수, 플랫폼별 수집 현황, 임베딩 현황, 최근 크롤링 실행 이력
- **Crawl (`/crawl`)**
  - 전체/플랫폼별 크롤 실행, 비동기 실행
  - 머지 실행(전체/플랫폼별)
  - 크롤링 실행 이력 조회
- **Data (`/data`)**
  - `car_master`, `platform_car` 테이블 조회(필터/페이징), 상세 모달
- **Embedding (`/embedding`)**
  - Chroma 통계/상태, 전체/증분/범위/필터 임베딩 실행, 샘플 조회
- **Search (`/search`)**
  - 인덱싱(전체/증분/배치), 동기화(sync), 검색 테스트, 상태 조회
- **Config (`/config`)**
  - 검색 모드 설정
  - 코드/매핑 관련 조회
  - LLM 프롬프트/매칭 파라미터 관리

### 7.2 관리자 API(필수 엔드포인트)
#### 7.2.1 크롤링/머지
- **POST `/admin/crawl/runAll`**: 순차 크롤링 시작
- **POST `/admin/crawl/runAllAsync`**: 비동기 크롤링 시작
- **POST `/admin/crawl/{platform}`**: `encar|kcar|cha|chutcha|charancha|tcar`
- **POST `/admin/crawl/merge`**: `bizDate`(옵션, ISO date)로 전체 머지
- **POST `/admin/crawl/merge/{platform}`**: 플랫폼별 머지
- **GET `/admin/crawl/runs?limit=20`**: 최근 실행 이력

#### 7.2.2 배치/워크플로우
- **GET `/admin/batch/jobs`**
- **POST `/admin/batch/jobs/{jobId}/execute`**
- **GET `/admin/batch/jobs/{jobId}/executions`**
- **GET `/admin/batch/workflows`**
- **POST `/admin/batch/workflows/{workflowId}/execute`**
- **GET `/admin/batch/workflows/{workflowId}/executions`**

#### 7.2.3 파이프라인(원클릭 운영)
- **POST `/admin/pipeline/full`**: 크롤→머지→(선택)인덱싱/임베딩 등 풀 파이프라인
- **POST `/admin/pipeline/workflow`**, `/merge-only`, `/code-mapping-only`, `/master-merge-only`
- **POST `/admin/pipeline/rebuild-*`**: 재빌드/재생성(운영자 권한 필수)

#### 7.2.4 검색 인덱스 운영
- **POST `/admin/search/reindex`**: 전체 인덱싱
- **POST `/admin/search/incremental`**: 증분 인덱싱
- **POST `/admin/search/batch`**
- **POST `/admin/search/sync`**: Kafka 기반 동기화(선택)
- **POST `/admin/search/test`**: 검색 테스트

#### 7.2.5 임베딩 운영(Chroma)
- **POST `/admin/embedding/all`**
- **POST `/admin/embedding/reset-and-all`**
- **POST `/admin/embedding/car/{carId}`**
- **POST `/admin/embedding/range`**
- **POST `/admin/embedding/filter`**
- **POST `/admin/embedding/incremental`** (since 파라미터 지원)
- **GET `/admin/embedding/status`**
- **GET `/admin/embedding/samples`**
- **GET `/admin/embedding/list?limit=100`**
- **POST `/admin/embedding/model/{modelCode}`**, **POST `/admin/embedding/model/all`**

#### 7.2.6 설정/프롬프트/매칭
- **GET/POST `/admin/config/search-mode`**
- **GET `/admin/config/codes`**
- **GET `/admin/config/llm-prompts`**
- **GET `/admin/config/llm/prompts`**, **GET `/admin/config/llm/prompts/{type}`**, **POST `/admin/config/llm/prompts/{type}`**
- **GET `/admin/config/llm/matching`**, **POST `/admin/config/llm/matching/{key}`**, **POST `/admin/config/llm/matching/batch`**
- **GET `/admin/config/llm/matching/preset`**, **POST `/admin/config/llm/matching/preset/{presetName}`**

#### 7.2.7 코드/매핑/강제매핑
- **GET `/admin/carizon-codes/*`**: makers/model-groups/models/trims/grades/mappings
- **GET `/admin/code-mapping/review`**, **GET `/admin/code-mapping/stats`**
- **PUT `/admin/code-mapping/review/{platformName}/update`**
- **POST `/admin/code-mapping/auto-mapping/{platformName}`**
- **GET/POST/PUT/DELETE `/admin/forced-mapping`**

#### 7.2.8 데이터 조회
- **GET `/admin/data/car-master`**
- **GET `/admin/data/platform-car`**

#### 7.2.9 위클리베스트(콘텐츠 운영)
- **POST `/admin/recommendation/weekly-best/batch/run`**
- **POST `/admin/recommendation/weekly-best/model/{modelCode}/generate`**
- **POST `/admin/recommendation/weekly-best/model/{modelCode}/post-to-wordpress`**
- **GET `/admin/recommendation/weekly-best/model/{modelCode}`**

## 8) 백엔드 도메인/데이터 요구사항(최소)
### 8.1 MySQL 스키마(개요)
구현체는 마이그레이션(Flyway 또는 동등)을 통해 아래 테이블 성격을 만족해야 합니다.
- `raw_encar` (예시): `payload(JSON)`, `car_image_url`, `price_new(INT NULL)`, `fetched_at`
- `platform_car`: 플랫폼별 표준화 차량(가격/링크/상태/이미지/last_seen 등)
- `car_master`: 사용자 기본 단위(메이커/모델/트림/대표 이미지/가격 범위/업데이트 시각 등)
- `crawl_run`: 크롤링 실행 이력(`run_id, source, status, total_items, started_at, ended_at, message`)
- (선택) `cz_model_new_price*`: 모델별 신차가 집계/소스

### 8.4 핵심 테이블 DDL 예시
아래는 구현 시 준수해야 할 **스키마 요구사항 예시**입니다. 실제 컬럼명/인덱스는 환경에 맞게 확장 가능하나, 의미는 동일해야 합니다.

#### 8.4.1 `platform_car`
```sql
CREATE TABLE platform_car (
  platform_car_id    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  platform_name      VARCHAR(50)  NOT NULL,
  platform_car_key   VARCHAR(100) NOT NULL,
  car_no             VARCHAR(50)  NULL,          -- 차량번호 (번호판)
  car_id             BIGINT       NULL,          -- car_master.car_id FK
  maker_code         VARCHAR(50)  NULL,
  model_group_code   VARCHAR(50)  NULL,
  model_code         VARCHAR(50)  NULL,
  trim_code          VARCHAR(50)  NULL,
  grade_code         VARCHAR(50)  NULL,
  maker_name         VARCHAR(100) NULL,
  model_group_name   VARCHAR(100) NULL,
  model_name         VARCHAR(100) NULL,
  trim_name          VARCHAR(100) NULL,
  grade_name         VARCHAR(100) NULL,
  price              INT          NULL,
  price_new          INT          NULL COMMENT '신차가격(만원)',
  km                 INT          NULL,
  displacement       INT          NULL,
  yymm               VARCHAR(20)  NULL,
  status             VARCHAR(20)  NULL,
  color              VARCHAR(50)  NULL,
  fuel               VARCHAR(50)  NULL,
  transmission       VARCHAR(50)  NULL,
  body_type          VARCHAR(50)  NULL,
  region             VARCHAR(100) NULL,
  m_url              VARCHAR(500) NULL,
  pc_url             VARCHAR(500) NULL,
  first_ad_day       VARCHAR(20)  NULL,
  created_at         DATETIME     NULL,
  updated_at         DATETIME     NULL,
  extra              JSON         NULL,
  last_seen_date     DATE         NULL,
  car_image_url      VARCHAR(500) NULL,
  UNIQUE KEY uk_platform_car (platform_name, platform_car_key),
  INDEX idx_platform_car_car_id (car_id),
  INDEX idx_platform_car_last_seen_date (last_seen_date),
  INDEX idx_platform_car_car_no (car_no),
  INDEX idx_platform_car_platform_car_no (platform_name, car_no)
);
```

#### 8.4.2 `car_master`
```sql
CREATE TABLE car_master (
  car_id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  car_no            VARCHAR(50)  NOT NULL,          -- 차량번호 (번호판)
  maker_code        VARCHAR(50)  NULL,
  model_group_code  VARCHAR(50)  NULL,
  model_code        VARCHAR(50)  NULL,
  trim_code         VARCHAR(50)  NULL,
  grade_code        VARCHAR(50)  NULL,
  year              INT          NULL,
  mileage           INT          NULL,
  displacement      INT          NULL,
  fuel              VARCHAR(50)  NULL,
  transmission      VARCHAR(50)  NULL,
  color             VARCHAR(50)  NULL,
  body_type         VARCHAR(50)  NULL,
  region            VARCHAR(100) NULL,
  price_new         INT          NULL COMMENT '신차가격(만원)',
  adv_status        VARCHAR(20)  NULL,             -- ONSALE / SOLD 등
  last_seen_date    DATE         NULL,
  created_at        DATETIME     NOT NULL,
  updated_at        DATETIME     NOT NULL,
  UNIQUE KEY uk_car_master_car_no (car_no)
);
```

#### 8.4.3 `car_price_history` (가격 히스토리)
```sql
CREATE TABLE car_price_history (
  id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  platform_car_id BIGINT      NOT NULL,
  price           INT         NOT NULL,
  checked_at      DATETIME    NOT NULL,
  is_current      TINYINT(1)  NOT NULL DEFAULT 1,
  last_seen_at    DATETIME    NULL,
  INDEX idx_cph_platform_car (platform_car_id, is_current)
);
```

#### 8.4.4 `crawl_run` (크롤 실행 이력)
```sql
CREATE TABLE crawl_run (
  run_id      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  source      VARCHAR(50)  NOT NULL,    -- ENCАR / KCAR / ...
  status      VARCHAR(20)  NOT NULL,    -- RUNNING / SUCCESS / FAIL
  total_items INT          NULL,
  started_at  DATETIME     NOT NULL,
  ended_at    DATETIME     NULL,
  message     VARCHAR(500) NULL
);
```


### 8.2 검색 인덱스(Elasticsearch)
- 인덱스 이름(예): `cars`
- 최소 필드: `id, maker, model, trim, year, km, priceMin, priceMax, region, bodyType, fuel, updatedAt, imageUrl, platforms[]`
- 운영 요구:
  - 전체 리인덱싱(초기/대량 변경)
  - 증분 인덱싱(일상 운영)
  - Kafka 기반 동기화(선택)

### 8.3 벡터 DB(Chroma)
- 컬렉션 이름(예): `car_listings`, (선택) `model_descriptions`
- 메타데이터 최소: `carId, maker, model, trim, year, price, fuel, bodyTypeCategory` 등 필터 가능한 키
- 임베딩 실행 방식:
  - 전체, 증분(since), 범위(from~to), 단건, 필터

## 9) 크롤링/머지/파이프라인 요구사항
### 9.1 크롤러
- 플랫폼별로 수집 API/HTML을 호출하여 원천 데이터를 확보
- 원천 payload(JSON)는 `raw_*`에 저장 가능해야 하며, 운영 트러블슈팅에 사용
- 장애 대응:
  - 특정 레코드 실패 시 **해당 레코드만 스킵**하고 다음 레코드/다음 청크 진행
  - 플랫폼 호출 실패 시 재시도/백오프(플랫폼별 정책)

### 9.2 머지
- 플랫폼별 정규화된 데이터를 `platform_car`에 적재
- 여러 플랫폼의 데이터를 `car_master`로 통합(코드/매핑 룰 기반)
- 머지 실행은 전체/플랫폼별로 가능해야 함

### 9.2.1 RAW → platform_car (플랫폼별 정규화)
- 각 플랫폼별로 `raw_*`에서 `platform_car`로 INSERT + ON DUPLICATE KEY UPDATE 수행
- **머지 키**
  - `platform_name`, `platform_car_key` 조합으로 유니크 보장
  - `car_no`(차량번호)는 가능한 경우 항상 채우고, 기존 값이 있으면 유지
- **플랫폼별 car_no 규칙(예시)**
  - CHACHACHA: `car_seq`(platform_car_key), `car_no` 그대로 사용
  - ENCAR: `vehicle_id`(platform_car_key), `vehicle_no`를 `car_no`로 사용
  - KCAR: 플랫폼 키 + 번호판 필드 사용
  - CHUTCHA / 기타: payload 내 `number_plate` 또는 `$.plateNumber` JSON 필드 사용
- **업데이트 정책(ODKU)**:
  - 가격/상태/마지막 본 날짜/이미지 등은 최신 수집값으로 덮어씀
  - 코드/이름(maker/model/trim/grade 등)은 `COALESCE(기존, 신규)`로 **기존 비어있던 값만 채움**
  - `last_seen_date`는 비즈니스 날짜(bizDate)로 세팅

### 9.2.2 platform_car → car_master 링크 (car_id 세팅)
- **머지 기준**: `car_no`(차량번호)를 **car_master의 유일 키**로 사용
- **알고리즘(요구 수준)**:
  1. `platform_car.car_id IS NULL` 인 레코드를 배치 단위로 조회(FOR UPDATE SKIP LOCKED)
  2. 해당 배치의 `car_no`들을 수집(중복 제거, NULL 제외)
  3. `car_master`에 존재하지 않는 `car_no`는 `INSERT INTO car_master (car_no, created_at, updated_at)`로 신규 생성
  4. `car_master`에서 `car_no → car_id` 맵을 조회
  5. `platform_car` 배치에 대해 `UPDATE platform_car SET car_id = ? WHERE platform_car_id = ? AND car_id IS NULL` 수행
  6. 완료 후, `car_master.price_new`가 NULL인 레코드는 `platform_car.price_new`의 MAX 값으로 **1회 백필**
- **요구사항**:
  - 동일 차량번호에 대해 **모든 플랫폼 레코드의 `car_id`가 동일해야 함**
  - car_no가 없는 레코드는 이후 car_master와 링크되지 않으므로, 운영에서 예외로 관리하거나 향후 다른 키로 확장

### 9.2.3 car_master 머지 (플랫폼 다수 → 대표 1건)
- MasterMerge 단계에서는 `platform_car` + `cz_code_map`을 이용해 `car_master`를 풍부화
- **alive 기준**:
  - `platform_car.last_seen_date`가 `bizDate` 범위에 있는 차량만 “alive”로 간주
  - alive 차량의 `CAR_NO` 목록을 기준으로 `car_master`에 upsert
- **upsertAliveToCarMaster 요구 알고리즘**:
  1. `platform_car`에서 alive CAR_NO 리스트 조회
  2. 배치 단위로 아래 쿼리 수행:
     - `INSERT INTO car_master (CAR_NO, MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE, price_new, adv_status, last_seen_date, UPDATED_AT)`
     - FROM 절에서 `platform_car`에 `cz_code_map`(메이커/모델/트림/등급 매핑)을 LEFT JOIN 해 표준 코드 계산
     - `GROUP BY CAR_NO`로 집계, `price_new`는 유효한 값 중 MAX 사용
     - `ON DUPLICATE KEY UPDATE`로 코드/price_new/adv_status/last_seen_date 갱신
- **동일 CAR_NO 다플랫폼 우선순위**:
  - `cz_platform_priority` 테이블로 플랫폼 우선순위 관리(예: CHACHACHA=1, ENCAR=2, ...)
  - 동일 CAR_NO에 대해:
    - 우선순위가 가장 높은 플랫폼
    - 그 중 연료가 가솔린/휘발유인 차량 우선
    - 최근 last_seen_date, platform_car_id 순으로 정렬
  - 이 규칙으로 1건만 선택해, `car_master`의 코드/스펙(연식, KM, 색상, 연료, 변속기, 지역 등)을 덮어씀

### 9.2.4 가격 히스토리 및 미노출 처리
- **가격 스냅샷**:
  - 매일 bizDate 기준으로, 해당 날짜에 `last_seen_date = bizDate`인 `platform_car`의 가격을 `car_price_history`에 INSERT
  - 동일 price가 이미 current로 기록되어 있으면 INSERT 생략
  - price 변경 시, 기존 current 레코드를 `is_current=0`, `last_seen_at=NOW()`로 업데이트
- **미노출(SOLD) 처리 개요**:
  - 특정 CAR_NO가 더 이상 어떤 `platform_car`에서도 alive가 아니면 `car_master.adv_status`를 SOLD 등으로 전환
  - 구현 방식은 `platform_car`와 `car_master`를 JOIN하여 alive가 없는 car_no를 찾고 상태 업데이트

### 9.3 파이프라인
- 운영자는 “원클릭”으로 (크롤→머지→인덱싱→임베딩)까지 실행 가능해야 함
- 각 단계는 단독 실행 가능해야 함(디버깅/운영 편의)

## 10) 로깅/관측(필수)
- **청크 단위 INFO 로그**: 크롤/상세수집/머지/인덱싱/임베딩은 “청크 완료”마다 1줄 로그
- SQL 상세 로그는 기본값에서 **비활성화**(필요 시 운영자가 레벨 조정)
- **헬스체크**
  - `GET /api/health`
  - `GET /api/recommendations/health`
  - `GET /api/model-images/health`
  - Actuator `/actuator/health` 제공 권장

## 11) 환경변수/설정(예시)
### 11.1 Backend
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`
- `ELASTICSEARCH_URIS`
- `KAFKA_ENABLED`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_CAR_SYNC_TOPIC`
- `RAG_CHROMA_BASE_URL`
- `RAG_LLM_OLLAMA_BASE_URL`, `RAG_EMBEDDING_OLLAMA_BASE_URL` (또는 HuggingFace API Key)

### 11.2 Admin/Frontend
- (개발) `VITE_API_BASE_URL` (기본은 상대 경로 프록시 가능)

## 12) 실행/배포 요구사항
### 12.1 로컬 개발(권장 플로우)
- `infra`에서 `docker compose up -d`로 검색/스토리지 등을 기동
- backend: `./mvnw spring-boot:run`
- frontend: `npm i && npm run dev`
- admin: `npm i && npm run dev`

### 12.2 Docker 배포
- `backend/docker-compose.yml` 기준으로 전체 스택을 `docker compose up -d`로 기동 가능해야 함
- 프론트/어드민은 nginx 정적 서빙 + `/api`, `/admin`은 backend로 프록시(권장)

## 13) 수용 기준(핵심)
- **검색**
  - 필터/정렬/페이지가 정상 동작하고, 결과에 대표 이미지/가격/연식/주행이 표시된다.
- **상세**
  - `car_master` 1건 상세와 플랫폼별 링크가 노출된다(PC/모바일).
- **추천**
  - 질의 1회에 대해 설명 텍스트 + 추천 차량 리스트(최소 3~5개)를 반환한다.
- **운영**
  - 어드민에서 크롤/머지/인덱싱/임베딩을 실행할 수 있고, 실행 결과가 청크 단위로 요약 로그/상태로 확인된다.
- **장애 허용**
  - 개별 원천 데이터 오류로 인해 전체 크롤/상세 적재가 중단되지 않는다(스킵/재시도 정책).

