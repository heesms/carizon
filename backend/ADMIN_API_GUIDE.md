# 관리자 페이지 API 가이드

## 완료된 기능

### 1. 임베딩 증분 업데이트 ✅
- **엔드포인트**: `POST /admin/embedding/incremental?since=2024-01-01T00:00:00`
- **설명**: `car_master.updated_at` 기준으로 변경된 차량만 임베딩
- **기본값**: `since` 미지정 시 1시간 전 이후 업데이트된 차량

### 2. Meilisearch 테스트 API ✅
- **엔드포인트**: `POST /admin/search/test`
- **파라미터**:
  - `q`: 검색어
  - `makerCode`: 제조사 코드
  - `priceMin`, `priceMax`: 가격 범위
  - `sort`: 정렬 (LOW_PRICE, LOW_KM, NEW_YEAR)
  - `page`, `size`: 페이징

### 3. 관리자 페이지용 API ✅

#### 대시보드
- `GET /admin/dashboard/stats` - 시스템 통계

#### 크롤링 관리
- `GET /admin/crawl/runs` - 크롤링 현황 조회
- `POST /admin/crawl/runAll` - 전체 크롤링 실행
- `POST /admin/crawl/{platform}` - 플랫폼별 크롤링 (encar, kcar, cha, chutcha, charancha, tcar)
- `POST /admin/crawl/merge` - 데이터 머지 실행
- `POST /admin/crawl/merge/{platform}` - 플랫폼별 머지

#### 데이터 조회
- `GET /admin/data/car-master` - 차량 마스터 조회 (필터링, 페이징)
- `GET /admin/data/platform-car` - 플랫폼 차량 조회 (필터링, 페이징)

#### 임베딩 관리
- `GET /admin/embedding/status` - 임베딩 현황 (개수, 샘플)
- `GET /admin/embedding/samples` - 샘플 조회
- `POST /admin/embedding/all` - 전체 임베딩
- `POST /admin/embedding/incremental` - 증분 임베딩
- `POST /admin/embedding/car/{carId}` - 단일 차량 임베딩
- `POST /admin/embedding/range` - 범위 임베딩

#### 검색 관리
- `POST /admin/search/reindex` - 전체 재인덱싱
- `POST /admin/search/incremental` - 증분 인덱싱
- `POST /admin/search/batch` - 배치 인덱싱
- `POST /admin/search/test` - 검색 테스트

#### 설정 관리
- `GET /admin/config/search-mode` - 검색 모드 조회
- `POST /admin/config/search-mode` - 검색 모드 설정
- `GET /admin/config/codes` - 코드 목록 조회
- `GET /admin/config/llm-prompts` - LLM 프롬프트 조회 (레거시)

#### LLM 설정 관리 (신규)
- `GET /admin/config/llm/prompts` - 모든 LLM 프롬프트 조회
- `GET /admin/config/llm/prompts/{type}` - 특정 프롬프트 조회
- `POST /admin/config/llm/prompts/{type}` - 프롬프트 수정
- `GET /admin/config/llm/matching` - 매칭 가중치 조회
- `POST /admin/config/llm/matching/{key}` - 특정 가중치 수정
- `POST /admin/config/llm/matching/batch` - 가중치 일괄 수정
- `GET /admin/config/llm/matching/preset` - 프리셋 목록 조회
- `POST /admin/config/llm/matching/preset/{presetName}` - 프리셋 적용

## 배치 처리 권장 사항

### 1. 크롤링 → 머지 → 인덱싱 파이프라인

```bash
# 1. 크롤링 실행
POST /admin/crawl/runAll

# 2. 데이터 머지 (크롤링 완료 후)
POST /admin/crawl/merge

# 3. Meilisearch 증분 인덱싱 (머지 완료 후)
POST /admin/search/incremental?since=2024-01-01T00:00:00

# 4. 임베딩 증분 업데이트 (머지 완료 후)
POST /admin/embedding/incremental?since=2024-01-01T00:00:00
```

### 2. 스케줄러 설정 예시

```java
// 매일 새벽 3시: 전체 크롤링
@Scheduled(cron = "0 0 3 * * *")
public void dailyCrawl() {
    crawlService.runAll();
}

// 매일 새벽 4시: 데이터 머지
@Scheduled(cron = "0 0 4 * * *")
public void dailyMerge() {
    mergeService.mergeAllPlatforms(LocalDate.now());
}

// 매일 새벽 5시: Meilisearch 증분 인덱싱
@Scheduled(cron = "0 0 5 * * *")
public void dailyIndex() {
    indexingService.incrementalIndex(LocalDateTime.now().minusDays(1));
}

// 매일 새벽 6시: 임베딩 증분 업데이트
@Scheduled(cron = "0 0 6 * * *")
public void dailyEmbedding() {
    embeddingService.incrementalEmbed(LocalDateTime.now().minusDays(1));
}

// 매 시간: 배치 인덱싱 (1000건씩)
@Scheduled(cron = "0 0 * * * *")
public void hourlyBatchIndex() {
    indexingService.batchIndex(1000);
}
```

## 프론트엔드 프로젝트

프론트엔드 프로젝트 구조는 `admin-frontend/` 디렉토리에 생성했습니다.

### 시작하기

```bash
# 프로젝트 생성
npm create vite@latest admin-frontend -- --template react-ts
cd admin-frontend

# 의존성 설치
npm install antd @ant-design/icons axios react-query zustand

# 개발 서버 실행
npm run dev
```

### 주요 페이지

1. **대시보드** - 시스템 통계 및 현황
2. **크롤링 관리** - 크롤링 실행 및 현황
3. **데이터 조회** - car_master, platform_car 조회
4. **임베딩 관리** - 임베딩 현황 및 실행
5. **검색 관리** - Meilisearch 인덱싱 및 테스트
6. **설정 관리** - 검색 모드, 코드, LLM 프롬프트

## 추가 개선 사항

### 1. 검색 모드 설정 테이블
현재는 하드코딩되어 있으므로, 설정 테이블을 만들어서 관리하는 것을 권장합니다.

```sql
CREATE TABLE system_config (
  config_key VARCHAR(100) PRIMARY KEY,
  config_value TEXT,
  description VARCHAR(500),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO system_config (config_key, config_value, description) VALUES
('search.use_meilisearch', 'true', 'Meilisearch 사용 여부'),
('search.fallback_to_db', 'true', 'Meilisearch 실패 시 DB 폴백 여부');
```

### 2. LLM 프롬프트 및 매칭 설정 테이블 ✅
이미 생성됨: `src/main/resources/db/migrations/001_create_llm_config_tables.sql`

**테이블 구조:**
- `llm_prompt_config`: LLM 프롬프트 템플릿 관리
- `llm_matching_config`: 매칭 가중치 및 임계값 관리

**초기 데이터:**
- 프롬프트: intro, instruction, car-format 등
- 가중치: 가격, 제조사, 모델명, 차종, 연료, 변속기 일치 가중치
- 임계값: 높은 유사도, 저주행거리, 가격 허용 오차율

**사용 방법:**
```bash
# 프롬프트 수정
POST /admin/config/llm/prompts/intro
{
  "prompt_text": "사용자가 다음과 같은 요구사항으로 중고차를 찾고 있습니다:",
  "is_active": true
}

# 가중치 수정 (가격 일치를 더 중요하게)
POST /admin/config/llm/matching/similarity.price_weight
{
  "config_value": 0.5,
  "description": "가격 일치 가중치 (50%)"
}

# 프리셋 적용 (가격 중심)
POST /admin/config/llm/matching/preset/price_focused
```

### 3. 작업 이력 테이블
인덱싱/임베딩 작업 이력을 추적할 수 있는 테이블

```sql
CREATE TABLE indexing_job (
  job_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_type VARCHAR(50) NOT NULL, -- 'FULL', 'INCREMENTAL', 'BATCH'
  total_count INT,
  success_count INT,
  fail_count INT,
  started_at TIMESTAMP,
  ended_at TIMESTAMP,
  status VARCHAR(20) -- 'RUNNING', 'SUCCESS', 'FAIL'
);
```
