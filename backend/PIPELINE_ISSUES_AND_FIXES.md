# 파이프라인 문제점 및 수정 방안

## 🔴 발견된 문제점

### 문제 1: 코드 매핑이 자동으로 실행되지 않음

**현재 상황:**
- `BatchJobService.executeMergeJob()`은 `mergeService.mergeAllPlatforms()`만 호출
- 코드 매핑 단계가 빠져있음
- 워크플로우(`daily_pipeline`)에도 코드 매핑 단계가 없음

**영향:**
- `car_master` 생성 시 `cz_code_map`에 매핑이 없으면 표준 코드가 NULL이 됨
- 광고 데이터가 제대로 통합되지 않음

### 문제 2: cz_code_map 테이블 구조 불일치

**CodeMappingService 저장 방식:**
```sql
INSERT INTO cz_code_map (
  platform_name,
  p_maker_code, p_model_group_code, p_model_code, p_trim_code, p_grade_code,
  maker_code, model_group_code, model_code, trim_code, grade_code,
  ...
)
-- level 컬럼 없음
```

**MasterMergeService.upsertAliveToCarMaster 조회 방식:**
```sql
LEFT JOIN cz_code_map cm_m 
  ON cm_m.platform_name = p.PLATFORM_NAME 
  AND cm_m.level = 'MAKER'           -- ⚠️ level 컬럼 사용
  AND cm_m.platform_code = p.MAKER_CODE
```

**MasterMergeService.updateCarMasterFromMapping 조회 방식:**
```sql
JOIN cz_code_map m
  ON m.platform_name = pc.PLATFORM_NAME
  AND m.p_maker_code = pc.MAKER_CODE      -- ✅ level 없이 직접 JOIN
  AND m.p_model_code = pc.MODEL_CODE
  ...
```

**문제:**
- `upsertAliveToCarMaster`는 `level` 컬럼을 사용하지만, `CodeMappingService`는 `level` 없이 저장
- 두 메서드가 서로 다른 JOIN 방식을 사용
- 테이블 구조가 일치하지 않으면 JOIN 실패

## ✅ 수정 방안

### 1. 코드 매핑 배치 작업 추가

`BatchJobService`에 코드 매핑 작업 타입 추가 및 실행 로직 구현

### 2. MasterMergeService.upsertAliveToCarMaster 수정

`level` 컬럼 대신 `p_maker_code`, `p_model_code` 등으로 직접 JOIN하도록 수정

### 3. 워크플로우에 코드 매핑 단계 추가

`daily_pipeline` 워크플로우에 코드 매핑 단계 추가:
```
크롤링 → 머지 → 코드 매핑 → car_master 생성 → 인덱싱 → 임베딩
```

## 현재 파이프라인 상태

### ✅ 정상 작동하는 부분
1. 크롤링 → raw_* 테이블
2. 머지 → platform_car 테이블
3. 코드 매핑 → cz_code_map 테이블 (수동 실행 시)
4. car_master 생성 → car_master 테이블 (코드 매핑이 있으면)

### ⚠️ 문제가 있는 부분
1. 코드 매핑이 자동으로 실행되지 않음
2. `upsertAliveToCarMaster`의 JOIN 조건이 실제 테이블 구조와 불일치 가능성
