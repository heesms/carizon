# 배치 관리 시스템 가이드

## 개요

배치 작업(크롤링, 머지, 인덱싱, 임베딩)을 통합 관리하는 시스템입니다.

## 데이터베이스 설정

### 1. 테이블 생성

```sql
SOURCE src/main/resources/db/migrations/003_create_batch_job_tables.sql;
```

### 2. 테이블 구조

- `batch_job_definition`: 배치 작업 정의
- `batch_job_execution`: 작업 실행 이력
- `batch_workflow_definition`: 워크플로우 정의
- `batch_workflow_execution`: 워크플로우 실행 이력

## 주요 기능

### 작업 관리

1. **크롤링 작업**
   - `crawl_all`: 전체 크롤링
   - `crawl_encar`, `crawl_kcar`, `crawl_cha` 등: 플랫폼별 크롤링

2. **머지 작업**
   - `merge_all`: 전체 데이터 머지 (원장 생성)

3. **인덱싱 작업**
   - `indexing_incremental`: 증분 인덱싱
   - `indexing_batch`: 배치 인덱싱 (1000건)
   - `indexing_reindex`: 전체 재인덱싱

4. **임베딩 작업**
   - `embedding_incremental`: 증분 임베딩
   - `embedding_all`: 전체 임베딩

### 워크플로우

- `daily_pipeline`: 크롤링 → 머지 → 인덱싱 → 임베딩 순차 실행

## API 사용법

### 작업 실행

```bash
# 작업 실행
POST /admin/batch/jobs/{jobId}/execute
Content-Type: application/json

{
  "bizDate": "2024-01-01",
  "limit": 1000,
  "since": "2024-01-01T00:00:00"
}
```

### 워크플로우 실행

```bash
# 워크플로우 실행
POST /admin/batch/workflows/{workflowId}/execute
```

### 실행 이력 조회

```bash
# 작업 실행 이력
GET /admin/batch/jobs/{jobId}/executions?limit=20

# 워크플로우 실행 이력
GET /admin/batch/workflows/{workflowId}/executions?limit=20
```

## 관리자 페이지

관리자 페이지의 "배치 관리" 메뉴에서:
- 작업 목록 조회
- 작업 즉시 실행
- 실행 이력 확인
- 워크플로우 관리

## 스케줄링

작업 정의에 `cron_expression`을 설정하면 자동으로 스케줄링됩니다.
(현재는 수동 실행만 지원, 향후 Quartz Scheduler 연동 예정)
