# 파이프라인 분석 및 문제점 정리

## 현재 파이프라인 흐름

```
1. 크롤링
   ↓
   raw_* 테이블 (원본 크롤링 데이터)
   ↓
2. 머지 (MergeService)
   ↓
   platform_car 테이블 (플랫폼별 원본 코드 저장)
   ↓
3. 코드 매핑 (CodeMappingService) ⚠️ 자동 실행 안 됨
   ↓
   cz_code_map 테이블 (플랫폼 코드 → 표준 코드 매핑)
   ↓
4. car_master 생성 (MasterMergeService)
   ↓
   car_master 테이블 (표준 코드로 통합)
```

## 발견된 문제점

### 🔴 문제 1: 코드 매핑이 자동으로 실행되지 않음

**현재 상황:**
- `BatchJobService.executeMergeJob()`은 `mergeService.mergeAllPlatforms()`만 호출
- 코드 매핑 단계가 빠져있음
- 워크플로우(`daily_pipeline`)에도 코드 매핑 단계가 없음

**영향:**
- `car_master` 생성 시 `cz_code_map`에 매핑이 없으면 표준 코드가 NULL이 됨
- 광고 데이터가 제대로 통합되지 않음

### 🔴 문제 2: cz_code_map 테이블 구조 불일치 가능성

**CodeMappingService 저장 방식:**
```sql
INSERT INTO cz_code_map (
  platform_name,
  p_maker_code, p_model_group_code, p_model_code, p_trim_code, p_grade_code,
  maker_code, model_group_code, model_code, trim_code, grade_code,
  ...
)
```

**MasterMergeService 조회 방식:**
```sql
LEFT JOIN cz_code_map cm_m 
  ON cm_m.platform_name = p.PLATFORM_NAME 
  AND cm_m.level = 'MAKER'           -- ⚠️ level 컬럼 사용
  AND cm_m.platform_code = p.MAKER_CODE
```

**문제:**
- `CodeMappingService`는 `level` 컬럼 없이 저장
- `MasterMergeService`는 `level` 컬럼을 사용하여 JOIN 시도
- 테이블 구조가 일치하지 않으면 JOIN 실패

### ⚠️ 문제 3: MergeService.postProcess()의 역할

**현재:**
```java
public int postProcess(LocalDate bizDate) {
    int linked = linkToMaster();  // car_id만 매핑
    snapshotPrices(bizDate);
    closeMissingAds(bizDate);
    return linked;
}
```

- `linkToMaster()`는 `platform_car.car_id`만 매핑
- 코드 매핑은 하지 않음
- 코드 매핑은 `MasterMergeService`에서 별도로 해야 함

## 해결 방안

### 1. 코드 매핑 단계를 배치 워크플로우에 추가

**수정 필요:**
- `BatchJobService`에 코드 매핑 작업 타입 추가
- 워크플로우에 코드 매핑 단계 추가 (머지 후, car_master 생성 전)

### 2. cz_code_map 테이블 구조 확인 및 수정

**확인 필요:**
- 실제 테이블 구조 (level 컬럼 존재 여부)
- CodeMappingService와 MasterMergeService의 JOIN 조건 일치 여부

### 3. 전체 파이프라인 재정의

**올바른 순서:**
```
1. 크롤링 → raw_*
2. 머지 → platform_car
3. 코드 매핑 → cz_code_map (각 플랫폼별)
4. car_master 생성 → car_master (표준 코드로 통합)
5. 광고 데이터 최신화 → car_master 업데이트
```

## 다음 단계

1. cz_code_map 테이블 실제 구조 확인
2. 코드 매핑 배치 작업 추가
3. 워크플로우 수정
4. 테스트 및 검증
