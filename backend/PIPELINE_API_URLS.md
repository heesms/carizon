# 파이프라인 API URL 목록

> **참고:** 각 API가 실행하는 단계별 상세 내용은 [PIPELINE_STEPS_DETAIL.md](./PIPELINE_STEPS_DETAIL.md)를 참고하세요.

## 📊 파이프라인 흐름 요약

```
raw_* (크롤링 데이터)
  ↓ [머지]
platform_car (플랫폼별 원본 코드)
  ↓ [코드 매핑]
cz_code_map (플랫폼 코드 → 카리즌 표준 코드)
  ↓ [car_master 머지]
car_master (표준 코드로 통합된 최종 데이터)
```

---

# 파이프라인 API URL 목록

## 📋 전체 파이프라인 실행

### 1. 전체 파이프라인 실행 (머지 → 코드매핑 → car_master)
```
POST /admin/pipeline/full
```
**파라미터:**
- `bizDate` (optional): 기준일자 (YYYY-MM-DD 형식, 기본값: 오늘)
- `skipCrawl` (optional): 크롤링 스킵 여부 (기본값: false)

**예시:**
```bash
# 오늘 기준 전체 파이프라인 실행
POST /admin/pipeline/full

# 특정 날짜 기준 실행
POST /admin/pipeline/full?bizDate=2024-01-15

# 크롤링 스킵하고 실행
POST /admin/pipeline/full?skipCrawl=true
```

### 2. 워크플로우 실행 (크롤링 포함)
```
POST /admin/pipeline/workflow
```
**파라미터:**
- `bizDate` (optional): 기준일자 (YYYY-MM-DD 형식, 기본값: 오늘)

**예시:**
```bash
POST /admin/pipeline/workflow
POST /admin/pipeline/workflow?bizDate=2024-01-15
```

---

## 🔄 단계별 실행

### 3. 머지만 실행 (raw_* → platform_car)
```
POST /admin/pipeline/merge-only
```
**파라미터:**
- `bizDate` (optional): 기준일자 (YYYY-MM-DD 형식, 기본값: 오늘)

**예시:**
```bash
POST /admin/pipeline/merge-only
POST /admin/pipeline/merge-only?bizDate=2024-01-15
```

### 4. 코드 매핑만 실행 (platform_car → cz_code_map)
```
POST /admin/pipeline/code-mapping-only
```
**파라미터:**
- `scope` (optional): 범위 (TODAY 또는 FULL, 기본값: TODAY)

**예시:**
```bash
# 오늘 데이터만 매핑
POST /admin/pipeline/code-mapping-only

# 전체 데이터 매핑
POST /admin/pipeline/code-mapping-only?scope=FULL
```

### 5. car_master 머지만 실행 (platform_car + cz_code_map → car_master)
```
POST /admin/pipeline/master-merge-only
```
**파라미터:**
- `bizDate` (optional): 기준일자 (YYYY-MM-DD 형식, 기본값: 오늘)

**예시:**
```bash
POST /admin/pipeline/master-merge-only
POST /admin/pipeline/master-merge-only?bizDate=2024-01-15
```

---

## 🕷️ 크롤링 API

### 6. 전체 크롤링 실행
```
POST /admin/crawl/runAll
```

### 7. 플랫폼별 크롤링
```
POST /admin/crawl/encar      # 엔카
POST /admin/crawl/kcar       # KCar
POST /admin/crawl/cha        # 차차차
POST /admin/crawl/chutcha    # 차차차
POST /admin/crawl/charancha  # 차란차
POST /admin/crawl/tcar       # 티카
```

---

## 🔀 머지 API

### 8. 전체 머지 (모든 플랫폼)
```
POST /admin/crawl/merge
```
**파라미터:**
- `bizDate` (optional): 기준일자 (YYYY-MM-DD 형식, 기본값: 오늘)

**예시:**
```bash
POST /admin/crawl/merge
POST /admin/crawl/merge?bizDate=2024-01-15
```

### 9. 플랫폼별 머지
```
POST /admin/crawl/merge/{platform}
```
**파라미터:**
- `platform`: 플랫폼명 (ENCAR, KCAR, CHACHACHA, CHA 등)
- `bizDate` (optional): 기준일자 (YYYY-MM-DD 형식, 기본값: 오늘)

**예시:**
```bash
POST /admin/crawl/merge/ENCAR
POST /admin/crawl/merge/KCAR
POST /admin/crawl/merge/CHACHACHA
POST /admin/crawl/merge/CHA
POST /admin/crawl/merge/ENCAR?bizDate=2024-01-15
```

---

## 🔗 코드 매핑 API

### 10. 전체 코드 매핑 (모든 플랫폼)
```
POST /admin/code-mapping/auto-mapping/all
```
또는 배치 작업으로:
```
POST /admin/batch/jobs/code_mapping_all/execute
```

### 11. 플랫폼별 코드 매핑
```
POST /admin/code-mapping/auto-mapping/{platformName}
```
**파라미터:**
- `platformName`: 플랫폼명 (ENCAR, KCAR, CHACHACHA, CHUTCHA, CHARANCHA, TCAR)
- `scope` (optional): 범위 (TODAY 또는 FULL, 기본값: TODAY)

**예시:**
```bash
POST /admin/code-mapping/auto-mapping/ENCAR
POST /admin/code-mapping/auto-mapping/ENCAR?scope=FULL
POST /admin/code-mapping/auto-mapping/KCAR
POST /admin/code-mapping/auto-mapping/CHACHACHA
```

---

## 📊 car_master 머지 API

### 12. car_master 머지
```
POST /admin/batch/jobs/master_merge_all/execute
```
또는:
```
POST /admin/pipeline/master-merge-only
```

---

## 🎯 배치 작업 API

### 13. 개별 배치 작업 실행
```
POST /admin/batch/jobs/{jobId}/execute
```

**주요 jobId:**
- `crawl_all`: 전체 크롤링
- `crawl_encar`, `crawl_kcar`, `crawl_cha`, `crawl_chutcha`, `crawl_charancha`, `crawl_tcar`: 플랫폼별 크롤링
- `merge_all`: 전체 머지
- `code_mapping_all`: 전체 코드 매핑
- `master_merge_all`: car_master 머지

**예시:**
```bash
POST /admin/batch/jobs/crawl_all/execute
POST /admin/batch/jobs/merge_all/execute
POST /admin/batch/jobs/code_mapping_all/execute
POST /admin/batch/jobs/master_merge_all/execute
```

### 14. 워크플로우 실행
```
POST /admin/batch/workflows/daily_pipeline/execute
```

---

## 📝 파이프라인 실행 순서 예시

### 전체 파이프라인 (권장)
```bash
# 1. 전체 파이프라인 실행 (가장 간단)
POST /admin/pipeline/full?skipCrawl=true

# 또는 워크플로우 실행 (크롤링 포함)
POST /admin/pipeline/workflow
```

### 단계별 실행
```bash
# 1. 크롤링 (선택)
POST /admin/crawl/runAll

# 2. 머지
POST /admin/crawl/merge

# 3. 코드 매핑
POST /admin/pipeline/code-mapping-only

# 4. car_master 머지
POST /admin/pipeline/master-merge-only
```

### 플랫폼별 실행
```bash
# 1. 특정 플랫폼 크롤링
POST /admin/crawl/encar

# 2. 특정 플랫폼 머지
POST /admin/crawl/merge/ENCAR

# 3. 특정 플랫폼 코드 매핑
POST /admin/code-mapping/auto-mapping/ENCAR

# 4. car_master 머지 (전체 플랫폼 대상)
POST /admin/pipeline/master-merge-only
```

---

## 🔍 응답 형식

모든 API는 다음 형식으로 응답합니다:

```json
{
  "success": true,
  "data": {
    // 결과 데이터
  },
  "message": "성공 메시지"
}
```

**전체 파이프라인 실행 응답 예시:**
```json
{
  "success": true,
  "data": {
    "merge": {
      "mergedCount": 1234,
      "durationMs": 5000
    },
    "codeMapping": {
      "mappedCount": 5678,
      "durationMs": 3000
    },
    "masterMerge": {
      "mergedCount": 1234,
      "durationMs": 2000
    },
    "totalDurationMs": 10000,
    "bizDate": "2024-01-15"
  }
}
```
