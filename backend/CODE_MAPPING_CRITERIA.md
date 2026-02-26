# 코드 매핑 기준 및 프로세스

## 코드 매핑 기준 (3단계 우선순위)

### 우선순위 0: 강제 매핑 (cz_forced_map)
- 관리자가 수동으로 설정한 매핑
- 가장 높은 우선순위
- `match_reason = 'MANUAL'`
- `status = 'LOCKED'` (자동 업데이트 안 됨)

### 우선순위 1: 차량번호 동일 매칭 (PLATE_EQUAL)
- 같은 차량번호(CAR_NO)를 가진 CHACHACHA 매물의 표준 코드 사용
- CHACHACHA가 가장 신뢰도 높은 플랫폼으로 간주
- `match_reason = 'PLATE_EQUAL'`
- `confidence_score = 1.0` (100%)
- `status = 'AUTO'` (자동 승인)

### 우선순위 2: 텍스트 유사도 매칭 (HIER_TEXT)
- 표준 코드 사전과 플랫폼 이름을 정규화 후 유사도 비교
- 표준 사전: `cz_maker`, `cz_model_group`, `cz_model`, `cz_trim`, `cz_grade`
- 계층적 매칭 (부모가 결정되어야 자식 매칭 가능):

#### 2-1. Maker 매칭
- 유사도 임계값: **85% 이상**
- 표준 사전 전체에서 가장 유사한 Maker 선택

#### 2-2. Model Group 매칭
- 유사도 임계값: **88% 이상**
- **Maker가 결정된 경우에만** 해당 Maker 하위에서 비교

#### 2-3. Model 매칭
- 유사도 임계값: **90% 이상**
- **Maker + Model Group이 결정된 경우에만** 해당 하위에서 비교

#### 2-4. Trim 매칭
- 유사도 임계값: **90% 이상**
- **Maker + Model Group + Model이 결정된 경우에만** 해당 하위에서 비교

#### 2-5. Grade 매칭
- 유사도 임계값: **90% 이상**
- **Maker + Model Group + Model + Trim이 결정된 경우에만** 해당 하위에서 비교

### 최종 상태 결정

```java
String status = ("PLATE_EQUAL".equals(reason) || score >= 0.93) ? "AUTO" : "REVIEW";
```

- **AUTO**: 자동 매칭 성공
  - 차량번호 매칭 (`PLATE_EQUAL`)
  - 또는 최종 신뢰도 **93% 이상**
- **REVIEW**: 수동 검토 필요
  - 최종 신뢰도 **93% 미만**
  - 관리자가 확인 후 수동으로 매핑 필요

## 신뢰도 점수 계산

각 단계별 가중치:
- Maker: 15%
- Model Group: 25%
- Model: 30%
- Trim: 15%
- Grade: 15%

최종 신뢰도 = 각 단계 점수 × 가중치의 합

예시:
- Maker 90%, Group 88%, Model 92%, Trim 85%, Grade 80%
- 최종 = 0.15×0.90 + 0.25×0.88 + 0.30×0.92 + 0.15×0.85 + 0.15×0.80 = **0.8885 (88.85%)**
- 88.85% < 93% → **REVIEW 상태**

## 부분 매칭 허용

- 결정된 부모는 그대로 존중
- 자식만 매칭 실패해도 부모는 저장
- 예: Maker, Group, Model은 매칭되었지만 Trim, Grade는 실패 → Maker, Group, Model만 저장
