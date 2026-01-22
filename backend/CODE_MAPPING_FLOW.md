# 코드 매핑 및 머지 프로세스 설명

## 개요

플랫폼별로 다른 코드와 이름을 사용하는 중고차 매물을 표준 코드로 통합하는 프로세스입니다.

예시: "더 뉴 코란도 스포츠"
- ENCAR: `model_code="KORANDO_SPORT"`, `model_name="더뉴 코란도 스포츠"`
- KCAR: `model_code="KR001"`, `model_name="코란도 스포츠"`
- CHACHACHA: `model_code="CORANDO_NEW"`, `model_name="더 뉴 코란도 스포츠"`

→ 모두 표준 코드 `model_code="KIA_CORANDO_SPORT_2023"`로 통합

---

## 전체 프로세스 흐름

```
1. 크롤링
   ↓
   platform_car 테이블 (플랫폼별 원본 코드 저장)
   ↓
2. 코드 매핑 (CodeMappingService)
   ↓
   cz_code_map 테이블 (플랫폼 코드 → 표준 코드 매핑)
   ↓
3. 머지 (MasterMergeService)
   ↓
   car_master 테이블 (표준 코드로 통합)
```

---

## 1단계: 코드 매핑 (cz_code_map 생성)

### 실행 방법

```java
// CodeMappingService.runAutoMapping(platformName, scope)
codeMappingService.runAutoMapping("ENCAR", Scope.TODAY);  // 오늘 수집된 데이터만
codeMappingService.runAutoMapping("KCAR", Scope.FULL);   // 전체 데이터
```

### 프로세스 상세

#### 1-1. 입력 데이터 수집
```sql
-- platform_car에서 플랫폼별 코드와 이름 추출
SELECT DISTINCT
  CAR_NO,
  MAKER_CODE, MAKER_NAME,
  MODEL_GROUP_CODE, MODEL_GROUP_NAME,
  MODEL_CODE, MODEL_NAME,
  TRIM_CODE, TRIM_NAME,
  GRADE_CODE, GRADE_NAME
FROM platform_car
WHERE PLATFORM_NAME = 'ENCAR'
  AND last_seen_date >= '2024-01-01'
```

#### 1-2. 표준 코드 매칭 (3단계 우선순위)

**우선순위 0: 강제 매핑 (cz_forced_map)**
- 관리자가 수동으로 설정한 매핑
- 가장 높은 우선순위

**우선순위 1: 차량번호 동일 매칭**
- 같은 차량번호(CAR_NO)를 가진 CHACHACHA 매물의 표준 코드 사용
- CHACHACHA가 가장 신뢰도 높은 플랫폼으로 간주

**우선순위 2: 텍스트 유사도 매칭**
- 표준 코드 사전과 플랫폼 이름을 정규화 후 유사도 비교
- 표준 사전: `cz_maker`, `cz_model_group`, `cz_model`, `cz_trim`, `cz_grade`
- 계층적 매칭:
  1. Maker 매칭 (유사도 85% 이상)
  2. Model Group 매칭 (Maker 하위에서, 88% 이상)
  3. Model 매칭 (Maker + Group 하위에서, 90% 이상)
  4. Trim 매칭 (Maker + Group + Model 하위에서, 90% 이상)
  5. Grade 매칭 (Maker + Group + Model + Trim 하위에서, 90% 이상)

#### 1-3. cz_code_map 저장

```sql
INSERT INTO cz_code_map (
  platform_name,                    -- 'ENCAR'
  p_maker_code, p_model_code, ...   -- 플랫폼 원본 코드
  p_maker_name_norm, ...            -- 정규화된 이름
  maker_code, model_code, ...       -- 표준 코드
  confidence_score,                 -- 매칭 신뢰도 (0.0~1.0)
  match_reason,                     -- 'PLATE_EQUAL', 'HIER_TEXT', 'FORCED'
  status                            -- 'AUTO', 'REVIEW', 'LOCKED'
)
VALUES (...)
ON DUPLICATE KEY UPDATE ...
```

**status 값:**
- `AUTO`: 자동 매칭 성공 (신뢰도 93% 이상 또는 차량번호 매칭)
- `REVIEW`: 수동 검토 필요 (신뢰도 93% 미만)
- `LOCKED`: 관리자가 수동으로 고정 (더 이상 자동 업데이트 안 됨)

---

## 2단계: car_master 머지 (표준 코드 통합)

### 실행 방법

```java
// MasterMergeService.upsertAliveToCarMaster(bizDate)
masterMergeService.upsertAliveToCarMaster(LocalDate.now());
```

### 프로세스 상세

#### 2-1. platform_car + cz_code_map JOIN

```sql
SELECT p.CAR_NO,
       cm_m.maker_code,           -- 표준 코드
       cm_g.model_group_code,     -- 표준 코드
       cm_md.model_code,          -- 표준 코드
       cm_t.trim_code,            -- 표준 코드
       cm_gr.grade_code           -- 표준 코드
FROM platform_car p
LEFT JOIN cz_code_map cm_m 
  ON cm_m.platform_name = p.PLATFORM_NAME 
  AND cm_m.p_maker_code = p.MAKER_CODE
  AND cm_m.status IN ('LOCKED', 'AUTO')  -- 검증된 매핑만 사용
LEFT JOIN cz_code_map cm_g 
  ON cm_g.platform_name = p.PLATFORM_NAME 
  AND cm_g.p_model_group_code = p.MODEL_GROUP_CODE
  AND cm_g.status IN ('LOCKED', 'AUTO')
-- ... (model, trim, grade도 동일)
WHERE DATE(p.last_seen_date) = '2024-01-01'
GROUP BY p.CAR_NO
```

#### 2-2. car_master 저장

```sql
INSERT INTO car_master (
  CAR_NO,              -- 차량번호 (UNIQUE KEY)
  MAKER_CODE,          -- 표준 코드
  MODEL_GROUP_CODE,    -- 표준 코드
  MODEL_CODE,          -- 표준 코드 ⭐
  TRIM_CODE,           -- 표준 코드
  GRADE_CODE,          -- 표준 코드
  adv_status,          -- 'ONSALE'
  last_seen_date
)
VALUES (...)
ON DUPLICATE KEY UPDATE ...
```

**중요:**
- `car_master`의 `MODEL_CODE`는 이미 표준 코드입니다
- 모든 플랫폼의 동일 모델이 같은 `MODEL_CODE`를 가지게 됩니다
- 따라서 플랫폼에 관계없이 모델 기준으로 통합 평가가 가능합니다

---

## 3단계: 우선순위 기반 최종 업데이트

같은 차량번호가 여러 플랫폼에 있을 때, 우선순위가 높은 플랫폼의 정보를 사용합니다.

```java
// MasterMergeService.updateCarMasterFromMapping()
masterMergeService.updateCarMasterFromMapping();
```

**플랫폼 우선순위:**
1. CHACHACHA (priority=1)
2. ENCAR (priority=2)
3. KCAR (priority=3)
4. CHUTCHA (priority=4)
5. CHARANCHA (priority=5)
6. TCAR (priority=6)

---

## 실제 예시: "더 뉴 코란도 스포츠"

### Step 1: 크롤링 후 platform_car

| platform_name | model_code | model_name |
|--------------|------------|------------|
| ENCAR | `KORANDO_SPORT` | 더뉴 코란도 스포츠 |
| KCAR | `KR001` | 코란도 스포츠 |
| CHACHACHA | `CORANDO_NEW` | 더 뉴 코란도 스포츠 |

### Step 2: 코드 매핑 후 cz_code_map

| platform_name | p_model_code | model_code (표준) | match_reason | status |
|--------------|--------------|-------------------|--------------|--------|
| ENCAR | `KORANDO_SPORT` | `KIA_CORANDO_SPORT_2023` | HIER_TEXT | AUTO |
| KCAR | `KR001` | `KIA_CORANDO_SPORT_2023` | HIER_TEXT | AUTO |
| CHACHACHA | `CORANDO_NEW` | `KIA_CORANDO_SPORT_2023` | PLATE_EQUAL | AUTO |

### Step 3: car_master 머지 후

| CAR_NO | MODEL_CODE (표준) | platform_count |
|--------|-------------------|----------------|
| 12가3456 | `KIA_CORANDO_SPORT_2023` | 3 |

→ 이제 `MODEL_CODE = 'KIA_CORANDO_SPORT_2023'`로 모든 플랫폼의 동일 모델을 통합 평가 가능!

---

## 배치 워크플로우

일반적인 배치 실행 순서:

```java
// 1. 크롤링 (각 플랫폼별)
encarCrawler.runOnce();
kcarCrawler.runOnceFull();
// ...

// 2. 코드 매핑 (각 플랫폼별)
codeMappingService.runAutoMapping("ENCAR", Scope.TODAY);
codeMappingService.runAutoMapping("KCAR", Scope.TODAY);
// ...

// 3. car_master 머지
masterMergeService.upsertAliveToCarMaster(LocalDate.now());

// 4. 우선순위 기반 업데이트
masterMergeService.updateCarMasterFromMapping();
```

---

## 표준 코드 사전 관리

표준 코드는 다음 테이블에 저장됩니다:
- `cz_maker`: 제조사 코드/이름
- `cz_model_group`: 모델 그룹 코드/이름
- `cz_model`: 모델 코드/이름 ⭐
- `cz_trim`: 트림 코드/이름
- `cz_grade`: 등급 코드/이름

이 사전 데이터는 관리자가 수동으로 관리하거나, 초기 데이터를 로드해야 합니다.

---

## 주의사항

1. **cz_code_map의 status**
   - `REVIEW` 상태는 수동 검토가 필요합니다
   - 관리자가 확인 후 `LOCKED` 또는 `AUTO`로 변경해야 합니다

2. **매칭 실패 시**
   - `cz_code_map`에 매핑이 없으면 `car_master`의 코드가 NULL이 될 수 있습니다
   - 정기적으로 `REVIEW` 상태를 확인하고 수동 매핑이 필요합니다

3. **강제 매핑 (cz_forced_map)**
   - 특수한 경우 수동으로 매핑을 고정할 수 있습니다
   - 예: 플랫폼별로 이름이 완전히 다른 경우

---

## 관련 파일

- `CodeMappingService.java`: 코드 매핑 로직
- `MasterMergeService.java`: car_master 머지 로직
- `StringNormalizer.java`: 이름 정규화 (공백 제거, 대소문자 통일 등)
- `Similarity.java`: 텍스트 유사도 계산
