# 파이프라인 단계별 실행 내용

## 📊 파이프라인 전체 흐름

```
1. 크롤링 (Crawling)
   ↓
   raw_encar, raw_kcar, raw_chachacha, raw_chutcha, raw_charancha, raw_tcar
   ↓
2. 머지 (Merge)
   ↓
   platform_car (플랫폼별 원본 코드 저장)
   ↓
3. 코드 매핑 (Code Mapping)
   ↓
   cz_code_map (플랫폼 코드 → 카리즌 표준 코드 매핑)
   ↓
4. car_master 머지 (Master Merge)
   ↓
   car_master (표준 코드로 통합된 최종 데이터)
```

---

## 🔍 각 단계별 상세 내용

### 1단계: 크롤링 (Crawling)

**목적:** 외부 플랫폼에서 중고차 데이터 수집

**입력:** 없음 (외부 API 호출)

**출력:** `raw_*` 테이블
- `raw_encar`
- `raw_kcar`
- `raw_chachacha`
- `raw_chutcha`
- `raw_charancha`
- `raw_tcar`

**실행 API:**
```
POST /admin/crawl/runAll          # 전체 플랫폼 크롤링
POST /admin/crawl/encar            # 엔카만
POST /admin/crawl/kcar             # KCar만
POST /admin/crawl/cha              # 차차차만
POST /admin/crawl/chutcha          # 차차차만
POST /admin/crawl/charancha        # 차란차만
POST /admin/crawl/tcar             # 티카만
```

**실행 내용:**
- 각 플랫폼의 API를 호출하여 중고차 목록 수집
- 수집한 데이터를 `raw_*` 테이블에 INSERT
- 플랫폼별 원본 데이터 그대로 저장 (정규화 전)

---

### 2단계: 머지 (Merge)

**목적:** `raw_*` 테이블의 데이터를 `platform_car` 테이블로 통합

**입력:** `raw_*` 테이블 (raw_encar, raw_kcar, raw_chachacha 등)

**출력:** `platform_car` 테이블

**실행 API:**
```
POST /admin/pipeline/merge-only              # 전체 플랫폼 머지
POST /admin/crawl/merge                     # 전체 플랫폼 머지 (동일)
POST /admin/crawl/merge/ENCAR               # 엔카만 머지
POST /admin/crawl/merge/KCAR                # KCar만 머지
POST /admin/crawl/merge/CHACHACHA           # 차차차만 머지
POST /admin/crawl/merge/CHA                 # 차차차만 머지 (별칭)
```

**실행 내용:**
- 각 `raw_*` 테이블에서 데이터를 읽어옴
- 플랫폼별로 데이터 구조를 정규화하여 `platform_car` 테이블에 INSERT/UPDATE
- 플랫폼별 원본 코드 저장:
  - `maker_code`, `model_group_code`, `model_code`, `trim_code`, `grade_code` (플랫폼 원본)
  - `maker_name`, `model_group_name`, `model_name`, `trim_name`, `grade_name` (플랫폼 원본)
- `ON DUPLICATE KEY UPDATE`로 중복 처리
- 배치 크기: 5,000건씩 처리 (성능 최적화)

**예시 SQL (엔카):**
```sql
INSERT INTO platform_car
  (platform_name, platform_car_key, car_no, 
   maker_code, model_group_code, model_code, trim_code, grade_code,
   maker_name, model_group_name, model_name, trim_name, grade_name,
   price, km, ...)
SELECT
  'ENCAR', r.vehicle_id, r.vehicle_no,
  r.manufacturer_code, r.model_group_code, r.model_code, r.grade_code, r.grade_detail_code,
  r.manufacturer_name, r.model_group_name, r.model_name, r.grade_name, r.grade_detail_name,
  r.price, r.mileage, ...
FROM raw_encar r
WHERE r.id > ? AND r.id <= ?
ON DUPLICATE KEY UPDATE ...
```

---

### 3단계: 코드 매핑 (Code Mapping)

**목적:** `platform_car`의 플랫폼별 코드를 카리즌 표준 코드로 매핑

**입력:** `platform_car` 테이블

**출력:** `cz_code_map` 테이블

**실행 API:**
```
POST /admin/pipeline/code-mapping-only                    # 전체 플랫폼 코드 매핑
POST /admin/code-mapping/auto-mapping/ENCAR              # 엔카만 코드 매핑
POST /admin/code-mapping/auto-mapping/KCAR               # KCar만 코드 매핑
POST /admin/code-mapping/auto-mapping/CHACHACHA          # 차차차만 코드 매핑
POST /admin/code-mapping/auto-mapping/CHUTCHA            # 차차차만 코드 매핑
POST /admin/code-mapping/auto-mapping/CHARANCHA          # 차란차만 코드 매핑
POST /admin/code-mapping/auto-mapping/TCAR               # 티카만 코드 매핑
```

**파라미터:**
- `scope`: `TODAY` (오늘 데이터만) 또는 `FULL` (전체 데이터)

**실행 내용:**
- `platform_car`에서 플랫폼별 코드와 이름을 읽어옴
- 매핑 우선순위:
  1. **강제 매핑** (`cz_forced_map`): 관리자가 수동으로 설정한 매핑
  2. **차량번호 동일 매칭** (`PLATE_EQUAL`): 같은 차량번호를 가진 차차차 매물의 표준 코드 사용
  3. **텍스트 유사도 매칭** (`HIER_TEXT`): 표준 코드 사전과 플랫폼 이름을 정규화 후 유사도 비교
     - Maker: 85% 이상
     - Model Group: 88% 이상
     - Model: 90% 이상
     - Trim: 90% 이상
     - Grade: 90% 이상
- 매핑 결과를 `cz_code_map` 테이블에 저장:
  - `p_maker_code`, `p_model_code` 등: 플랫폼 원본 코드
  - `maker_code`, `model_code` 등: 카리즌 표준 코드
  - `confidence_score`: 신뢰도 (0.0 ~ 1.0)
  - `status`: `AUTO` (93% 이상), `REVIEW` (93% 미만), `LOCKED` (수동 고정)

**예시:**
```
platform_car (ENCAR):
  p_maker_code: "KIA"
  p_model_code: "KORANDO_SPORT"
  p_model_name: "더 뉴 코란도 스포츠"

↓ 코드 매핑 ↓

cz_code_map:
  platform_name: "ENCAR"
  p_maker_code: "KIA"
  p_model_code: "KORANDO_SPORT"
  maker_code: "KIA"              ← 카리즌 표준 코드
  model_code: "KORANDO"          ← 카리즌 표준 코드
  confidence_score: 0.92
  status: "REVIEW"
```

---

### 4단계: car_master 머지 (Master Merge)

**목적:** `platform_car`와 `cz_code_map`을 조인하여 표준 코드로 통합된 최종 데이터 생성

**입력:** 
- `platform_car` 테이블
- `cz_code_map` 테이블

**출력:** `car_master` 테이블

**실행 API:**
```
POST /admin/pipeline/master-merge-only      # car_master 머지
```

**실행 내용:**
1. **upsertAliveToCarMaster**: 오늘 수집된 데이터 기준으로 `car_master` 생성/업데이트
   - `platform_car`와 `cz_code_map`을 JOIN하여 표준 코드 추출
   - 같은 `CAR_NO`를 가진 여러 플랫폼 데이터를 하나의 `car_master` 레코드로 통합
   - 표준 코드 (`maker_code`, `model_code` 등) 저장

2. **updateCarMasterFromMapping**: 기존 `car_master`의 코드를 `cz_code_map` 기준으로 업데이트
   - 우선순위 기반으로 플랫폼별 매핑 적용
   - `cz_platform_priority` 테이블의 우선순위에 따라 1개 플랫폼만 선택

**예시 SQL:**
```sql
INSERT INTO car_master
  (CAR_NO, MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE, ...)
SELECT t.CAR_NO,
       t.MAKER_CODE, t.MODEL_GROUP_CODE, t.MODEL_CODE, t.TRIM_CODE, t.GRADE_CODE, ...
FROM (
  SELECT p.CAR_NO,
         ANY_VALUE(cm_m.maker_code) AS MAKER_CODE,
         ANY_VALUE(cm_md.model_code) AS MODEL_CODE, ...
  FROM platform_car p
  LEFT JOIN cz_code_map cm_m 
    ON cm_m.platform_name = p.PLATFORM_NAME 
   AND cm_m.p_maker_code = p.MAKER_CODE
   AND cm_m.status IN ('LOCKED','AUTO')
  ...
  GROUP BY p.CAR_NO
) t
ON DUPLICATE KEY UPDATE ...
```

**결과:**
- `car_master` 테이블에 표준 코드로 통합된 최종 데이터 저장
- 여러 플랫폼의 동일 차량이 하나의 `car_master` 레코드로 통합
- 표준 코드 기준으로 검색/추천 가능

---

## 🎯 통합 파이프라인 API 실행 내용

### POST /admin/pipeline/full

**실행 순서:**
1. ⏭️ 크롤링: 스킵 (별도 실행 필요)
2. ✅ 머지: `mergeService.mergeAllPlatforms(date)` 실행
   - 모든 플랫폼의 `raw_*` → `platform_car` 변환
3. ✅ 코드 매핑: 모든 플랫폼에 대해 `codeMappingService.runAutoMapping()` 실행
   - `platform_car` → `cz_code_map` 매핑
4. ✅ car_master 머지: `masterMergeService.upsertAliveToCarMaster()` + `updateCarMasterFromMapping()` 실행
   - `platform_car` + `cz_code_map` → `car_master` 통합

**실행 시간:**
- 머지: 약 5~10초 (데이터량에 따라)
- 코드 매핑: 약 3~5초
- car_master 머지: 약 2~3초
- **총 소요 시간: 약 10~18초**

---

### POST /admin/pipeline/workflow

**실행 순서:**
1. ✅ 크롤링: `crawl_all` 작업 실행
2. ✅ 머지: `merge_all` 작업 실행
3. ✅ 코드 매핑: `code_mapping_all` 작업 실행
4. ✅ car_master 머지: `master_merge_all` 작업 실행
5. ✅ 인덱싱: `indexing_incremental` 작업 실행
6. ✅ 임베딩: `embedding_incremental` 작업 실행

**참고:** `daily_pipeline` 워크플로우의 모든 단계를 순차 실행

---

## 📋 단계별 요약

| 단계 | 입력 테이블 | 출력 테이블 | 주요 작업 | 실행 API |
|------|------------|------------|----------|---------|
| **1. 크롤링** | 없음 | `raw_*` | 외부 API 호출하여 데이터 수집 | `/admin/crawl/*` |
| **2. 머지** | `raw_*` | `platform_car` | 플랫폼별 데이터 정규화 및 통합 | `/admin/pipeline/merge-only` |
| **3. 코드 매핑** | `platform_car` | `cz_code_map` | 플랫폼 코드 → 카리즌 표준 코드 매핑 | `/admin/pipeline/code-mapping-only` |
| **4. car_master 머지** | `platform_car` + `cz_code_map` | `car_master` | 표준 코드로 통합된 최종 데이터 생성 | `/admin/pipeline/master-merge-only` |

---

## 🔄 플랫폼별 실행 예시

### 엔카만 전체 파이프라인 실행

```bash
# 1. 엔카 크롤링
POST /admin/crawl/encar

# 2. 엔카 머지
POST /admin/crawl/merge/ENCAR

# 3. 엔카 코드 매핑
POST /admin/code-mapping/auto-mapping/ENCAR

# 4. car_master 머지 (전체 플랫폼 대상)
POST /admin/pipeline/master-merge-only
```

### 전체 플랫폼 파이프라인 실행

```bash
# 한 번에 실행 (권장)
POST /admin/pipeline/full?skipCrawl=true

# 또는 단계별 실행
POST /admin/pipeline/merge-only
POST /admin/pipeline/code-mapping-only
POST /admin/pipeline/master-merge-only
```
