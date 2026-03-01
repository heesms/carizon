# 관리자 페이지 설정 가이드

## 어드민 프로젝트 실행 방법

### 1. 프론트엔드 프로젝트 생성

```bash
# admin-frontend 디렉토리로 이동
cd admin-frontend

# 의존성 설치
npm install
```

### 2. 개발 서버 실행

```bash
npm run dev
```

### 3. 접속

브라우저에서 **http://localhost:3000** 접속

**중요**: 백엔드 서버(`http://localhost:8080`)가 실행 중이어야 합니다.

- Cursor 터미널에서 `npm run dev` 시 포트 권한 오류(EACCES)가 나면, **Git Bash·PowerShell 등 별도 터미널**에서 `admin-frontend`로 이동 후 `npm run dev` 실행하면 됩니다.

## LLM 프롬프트 및 설정 관리

### LLM 프롬프트란?

**LLM 프롬프트는 고객이 입력한 쿼리를 기반으로 추천 설명을 생성하는 템플릿입니다.**

- **학습하지 않음**: LLM은 매번 프롬프트를 받아서 응답을 생성합니다.
- **DB에서 관리**: 프롬프트 템플릿을 DB에 저장하여 관리자가 수정 가능
- **고객 입력 기반**: 고객이 "싼타페 3000만원 이하"라고 입력하면, 이를 기반으로 추천 생성

### LLM 환경 설정 (일치율 조절)

#### 1. 매칭 가중치 설정

각 항목별로 일치율에 대한 가중치를 조절할 수 있습니다:

- **가격 일치 가중치** (`similarity.price_weight`): 기본값 0.3 (30%)
- **제조사 일치 가중치** (`similarity.maker_weight`): 기본값 0.25 (25%)
- **모델명 일치 가중치** (`similarity.model_weight`): 기본값 0.25 (25%)
- **차종 일치 가중치** (`similarity.body_type_weight`): 기본값 0.1 (10%)
- **연료 일치 가중치** (`similarity.fuel_weight`): 기본값 0.05 (5%)
- **변속기 일치 가중치** (`similarity.transmission_weight`): 기본값 0.05 (5%)

#### 2. 임계값 설정

- **높은 유사도 임계값** (`similarity.high_threshold`): 기본값 0.7 (70%)
- **저주행거리 임계값** (`mileage.low_threshold`): 기본값 50000km
- **가격 허용 오차율** (`price.tolerance_percent`): 기본값 10%

#### 3. 프리셋

미리 정의된 가중치 조합:

- **가격 중심** (`price_focused`): 가격 일치를 더 중요시
- **차량명 중심** (`name_focused`): 제조사/모델명 일치를 더 중요시
- **균형** (`balanced`): 모든 항목을 균형있게 고려

## API 사용 예시

### LLM 프롬프트 조회/수정

```bash
# 모든 프롬프트 조회
GET /admin/config/llm/prompts

# 특정 프롬프트 조회
GET /admin/config/llm/prompts/intro

# 프롬프트 수정
POST /admin/config/llm/prompts/intro
{
  "prompt_text": "사용자가 다음과 같은 요구사항으로 중고차를 찾고 있습니다:",
  "is_active": true
}
```

### 매칭 가중치 조회/수정

```bash
# 모든 가중치 조회
GET /admin/config/llm/matching

# 특정 가중치 수정
POST /admin/config/llm/matching/similarity.price_weight
{
  "config_value": 0.5,
  "description": "가격 일치 가중치 (50%)"
}

# 일괄 수정
POST /admin/config/llm/matching/batch
{
  "similarity.price_weight": 0.5,
  "similarity.maker_weight": 0.3,
  "similarity.model_weight": 0.2
}

# 프리셋 적용
POST /admin/config/llm/matching/preset/price_focused
```

## 데이터베이스 초기화

LLM 설정 테이블을 생성하려면:

```sql
-- SQL 파일 실행
SOURCE src/main/resources/db/migrations/001_create_llm_config_tables.sql

-- 또는 직접 실행
-- (파일 내용 참고)
```

## 동작 방식

### 1. 고객 쿼리 입력
고객이 "싼타페 3000만원 이하"라고 입력

### 2. 임베딩 검색
- 쿼리를 벡터로 변환
- Chroma에서 유사한 차량 검색
- 유사도 점수 계산

### 3. 필터링 및 가중치 적용
- 가격, 제조사, 모델명 등으로 필터링
- DB 설정의 가중치를 적용하여 최종 점수 계산

### 4. LLM 추천 생성
- 검색된 차량 목록을 LLM 프롬프트에 넣어서 추천 설명 생성
- 각 차량에 추천 이유 추가 (유사도, 가격, 주행거리 등)

### 5. 응답 반환
고객에게 추천 차량 목록과 설명 반환

## 관리자 페이지에서 할 수 있는 것

1. **프롬프트 수정**: LLM이 생성하는 추천 설명의 톤앤매너 조정
2. **가중치 조정**: 어떤 항목(가격/차량명/차종)을 더 중요하게 볼지 조정
3. **임계값 조정**: "높은 유사도" 기준, "저주행거리" 기준 등 조정
4. **프리셋 적용**: 빠르게 가중치 조합 변경

## 주의사항

- **학습하지 않음**: LLM은 매번 프롬프트를 받아서 생성하므로, 프롬프트를 수정하면 즉시 반영됨
- **가중치 합계**: 모든 가중치의 합이 1.0이 되도록 조정하는 것을 권장
- **테스트 필요**: 가중치 변경 후 실제 검색 결과를 테스트하여 효과 확인
