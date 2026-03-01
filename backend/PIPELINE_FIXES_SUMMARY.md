# 파이프라인 수정 완료 요약

## 수정 완료 사항

### ✅ 1. MasterMergeService.upsertAliveToCarMaster JOIN 조건 수정

**문제:**
- `level` 컬럼을 사용하여 JOIN 시도했지만, `CodeMappingService`는 `level` 없이 저장

**수정:**
- `level` 컬럼 대신 `p_maker_code`, `p_model_code` 등으로 직접 JOIN하도록 수정
- `updateCarMasterFromMapping`과 동일한 방식으로 통일

**수정 전:**
```sql
LEFT JOIN cz_code_map cm_m 
  ON cm_m.platform_name = p.PLATFORM_NAME 
  AND cm_m.level = 'MAKER'
  AND cm_m.platform_code = p.MAKER_CODE
```

**수정 후:**
```sql
LEFT JOIN cz_code_map cm_m 
  ON cm_m.platform_name = p.PLATFORM_NAME 
  AND COALESCE(cm_m.p_maker_code,'') = COALESCE(p.MAKER_CODE,'')
```

### ✅ 2. BatchJobService에 코드 매핑 작업 추가

**추가된 작업 타입:**
- `CODE_MAPPING`: platform_car → cz_code_map
- `MASTER_MERGE`: platform_car + cz_code_map → car_master

**추가된 메서드:**
- `executeCodeMappingJob()`: 코드 매핑 실행
- `executeMasterMergeJob()`: car_master 머지 실행

### ✅ 3. 워크플로우 업데이트

**수정된 워크플로우 순서:**
```
1. crawl_all (크롤링)
   ↓
2. merge_all (raw_* → platform_car)
   ↓
3. code_mapping_all (platform_car → cz_code_map) ⭐ 추가
   ↓
4. master_merge_all (platform_car + cz_code_map → car_master) ⭐ 추가
   ↓
5. indexing_incremental (인덱싱)
   ↓
6. embedding_incremental (임베딩)
```

## 올바른 파이프라인 흐름

```
1. 크롤링
   ↓
   raw_* 테이블
   ↓
2. 머지 (MergeService)
   ↓
   platform_car 테이블 (플랫폼별 원본 코드)
   ↓
3. 코드 매핑 (CodeMappingService) ⭐ 자동 실행
   ↓
   cz_code_map 테이블 (플랫폼 코드 → 표준 코드)
   ↓
4. car_master 머지 (MasterMergeService) ⭐ 자동 실행
   ↓
   car_master 테이블 (표준 코드로 통합, 광고 데이터 최신화)
   ↓
5. 인덱싱 & 임베딩
```

## 실행 방법

### 자동 실행 (스케줄러)
- 매일 새벽 3:15: 크롤링
- 매일 새벽 4:30: 머지
- 매일 새벽 4:45: 코드 매핑 ⭐
- 매일 새벽 5:00: car_master 머지 ⭐
- 매일 새벽 5:00: 인덱싱
- 매일 새벽 6:00: 임베딩

### 수동 실행
```bash
# 1. 크롤링
POST /admin/crawl/runAll

# 2. 머지
POST /admin/crawl/merge

# 3. 코드 매핑 (새로 추가)
POST /admin/batch/jobs/code_mapping_all/execute

# 4. car_master 머지 (새로 추가)
POST /admin/batch/jobs/master_merge_all/execute

# 또는 워크플로우 전체 실행
POST /admin/batch/workflows/daily_pipeline/execute
```

## 확인 사항

### 1. cz_code_map 테이블 구조 확인

실제 테이블에 다음 컬럼들이 있는지 확인:
- `platform_name`
- `p_maker_code`, `p_model_group_code`, `p_model_code`, `p_trim_code`, `p_grade_code`
- `maker_code`, `model_group_code`, `model_code`, `trim_code`, `grade_code`
- `status` (AUTO, REVIEW, LOCKED)

**주의:** `level` 컬럼은 사용하지 않습니다.

### 2. 코드 매핑 상태 확인

```sql
-- 매핑 성공률 확인
SELECT status, COUNT(*) as count
FROM cz_code_map
GROUP BY status;

-- REVIEW 상태 확인 (수동 검토 필요)
SELECT * FROM cz_code_map WHERE status = 'REVIEW' LIMIT 10;
```

### 3. car_master 코드 통합 확인

```sql
-- 표준 코드가 NULL인 경우 확인
SELECT COUNT(*) 
FROM car_master 
WHERE MODEL_CODE IS NULL AND adv_status = 'ONSALE';

-- 모델별 통합 확인
SELECT MODEL_CODE, COUNT(*) as car_count
FROM car_master
WHERE adv_status = 'ONSALE'
GROUP BY MODEL_CODE
ORDER BY car_count DESC
LIMIT 10;
```

## 다음 단계

1. ✅ 코드 매핑 배치 작업 추가 완료
2. ✅ car_master 머지 배치 작업 추가 완료
3. ✅ 워크플로우 업데이트 완료
4. ✅ JOIN 조건 수정 완료
5. ⏳ DB 마이그레이션 실행 필요 (`004_add_code_mapping_jobs.sql`)
6. ⏳ 테스트 및 검증 필요
