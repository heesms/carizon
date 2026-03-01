# Chroma 임베딩 항목 전체 목록

차량 1건당 Chroma에 저장되는 **모든** 정보를 정리한 문서입니다.

---

## 1. 저장 단위

| 항목 | 설명 |
|------|------|
| **엔티티** | 차량 1건 (MySQL `car_master` + `platform_car` 1 row) |
| **Chroma ID** | `"car_" + carId` (예: `car_12345`) |
| **컬렉션** | `rag.chroma.collectionName` (기본: `car_listings`) |

---

## 2. Document (임베딩용 텍스트) — 전부 나열

아래 순서대로 한 줄씩 이어 붙인 문자열이 `documents`에 들어갑니다.

| 순서 | 섹션 | 내용 예시 |
|------|------|-----------|
| 1 | 차량 기본 정보 | `차량: 현대 싼타페 디젤 2.2 [SUV] SUV형 스포츠유틸리티 (RV)\n` |
| 2 | 가격대 | `가격대: 중고급차 (2,800만원)\n` 또는 `가격: 2,800만원\n` |
| 3 | 연식 | `연식: 2020년 (중고형)\n` |
| 4 | 주행거리 | `주행거리: 45,000km (저주행)\n` |
| 5 | 배기량 | `배기량: 2200cc (중형)\n` |
| 6 | 연료 | `연료: 디젤 (디젤)\n` 또는 `연료: 디젤\n` |
| 7 | 변속기 | `변속기: 자동 (자동 8단)\n` 또는 `변속기: 자동\n` |
| 8 | 차종 카테고리 | `차종카테고리: SUV\n` |
| 9 | 차종 원본 | `차종: RV\n` |
| 10 | 색상 | `색상: 흰색\n` |
| 11 | 지역 | `지역: 서울\n` |
| 12 | 판매상태 | `판매상태: ONSALE\n` |
| 13 | 모델 기본 정보 (있을 때만) | 아래 13-1~13-6 |

### 13. 모델 기본 정보 (서브 필드)

| 키 | 텍스트 예시 |
|----|-------------|
| typical_fuel | `모델기본연료: 디젤\n` |
| typical_transmission | `모델기본변속기: 자동\n` |
| typical_body_type | `모델기본차종: RV\n` |
| avg_displacement | `모델평균배기량: 2200cc\n` |
| min_year, max_year | `모델연식범위: 2015년~2024년\n` |

- 차종 카테고리 매핑: 세단, SUV, 미니밴, 해치백, 왜건, 쿠페, 컨버터블, 픽업트럭 (그 외는 원본 body_type)
- 가격 카테고리: 고급차(5000만↑), 중고급차(3000만↑), 중형차(1500만↑), 경형차
- 연식 카테고리: 최신형(3년 이내), 중고형(7년 이내), 구형
- 주행 카테고리: 저주행(3만↓), 중주행(10만↓), 고주행
- 배기량 카테고리: 소형(1600↓), 중형(2500↓), 대형
- 연료 정규화: 가솔린, 디젤, 하이브리드, 전기, LPG
- 변속기 정규화: 자동, 수동, CVT, DCT

---

## 3. Embedding (벡터)

| 항목 | 설명 |
|------|------|
| **생성** | `EmbeddingService.generateEmbedding(document 텍스트)` |
| **형식** | `float[]` → Chroma 전송 시 `List<Double>`로 변환 |
| **차원** | 임베딩 모델에 따름 (예: nomic-embed-text 768차원) |

---

## 4. Metadata (JSON → Chroma는 key/value 문자열)

차량 1건당 아래 키들이 들어갑니다. (없는 필드는 제외)

### 4.1 항상 들어가는 키

| 키 | 타입 | 설명 |
|----|------|------|
| carId | number | 차량 마스터 ID |
| platformCarId | number | 플랫폼별 매물 ID (없으면 미포함) |

### 4.2 차량/플랫폼 기본

| 키 | 타입 | 설명 |
|----|------|------|
| maker | string | 제조사명 (platform_car.maker_name) |
| country | string | 제조국 (cz_maker.country_name) |
| modelGroup | string | 모델 그룹명 |
| model | string | 모델명 |
| trim | string | 트림명 |
| platformName | string | 플랫폼명 (ENCAR, KCAR 등) |

### 4.3 스펙·가격

| 키 | 타입 | 설명 |
|----|------|------|
| year | number | 연식 |
| yearCategory | string | 최신형 / 중고형 / 구형 |
| mileage | number | 주행거리(km) |
| mileageCategory | string | 저주행 / 중주행 / 고주행 |
| price | number | 가격(만원) |
| priceCategory | string | 고급차 / 중고급차 / 중형차 / 경형차 |
| displacement | number | 배기량(cc) |
| displacementCategory | string | 소형 / 중형 / 대형 |

### 4.4 연료·변속기

| 키 | 타입 | 설명 |
|----|------|------|
| fuel | string | 연료 원본 |
| fuelType | string | 정규화된 연료 (가솔린/디젤/하이브리드/전기/LPG) |
| transmission | string | 변속기 원본 |
| transmissionType | string | 정규화된 변속기 (자동/수동/CVT/DCT) |

### 4.5 차종·기타

| 키 | 타입 | 설명 |
|----|------|------|
| bodyType | string | 차종 원본 (RV, 세단 등) |
| bodyTypeCategory | string | 세단 / SUV / 미니밴 / 해치백 / 왜건 등 |
| color | string | 색상 |
| region | string | 지역 |
| status | string | 판매상태 (ONSALE, ADVERTISE 등) |

### 4.6 URL

| 키 | 타입 | 설명 |
|----|------|------|
| pcUrl | string | PC 상세 URL |
| mUrl | string | 모바일 상세 URL |

### 4.7 모델 기본 정보 (같은 model_code 기준 집계)

| 키 | 타입 | 설명 |
|----|------|------|
| modelTypicalFuel | string | 해당 모델 최빈 연료 |
| modelTypicalTransmission | string | 해당 모델 최빈 변속기 |
| modelTypicalBodyType | string | 해당 모델 최빈 차종 |
| modelAvgDisplacement | number | 해당 모델 평균 배기량(cc) |
| modelMinYear | number | 해당 모델 최소 연식 |
| modelMaxYear | number | 해당 모델 최대 연식 |

---

## 5. Chroma API에 실제로 보내는 페이로드 (배치 add)

```json
{
  "ids": ["car_1", "car_2", ...],
  "embeddings": [[d1, d2, ...], [d1, d2, ...], ...],
  "documents": ["차량: 현대 싼타페...\n가격대: ...", ...],
  "metadatas": [
    {
      "carId": "1",
      "platformCarId": "123",
      "maker": "현대",
      "country": "한국",
      "modelGroup": "싼타페",
      "model": "싼타페",
      "trim": "디젤 2.2",
      "year": "2020",
      "yearCategory": "중고형",
      "mileage": "45000",
      "mileageCategory": "저주행",
      "price": "2800",
      "priceCategory": "중고급차",
      "fuel": "디젤",
      "fuelType": "디젤",
      "transmission": "자동 8단",
      "transmissionType": "자동",
      "bodyType": "RV",
      "bodyTypeCategory": "SUV",
      "color": "흰색",
      "region": "서울",
      "status": "ONSALE",
      "platformName": "ENCAR",
      "displacement": "2200",
      "displacementCategory": "중형",
      "pcUrl": "https://...",
      "mUrl": "https://...",
      "modelTypicalFuel": "디젤",
      "modelTypicalTransmission": "자동",
      "modelTypicalBodyType": "RV",
      "modelAvgDisplacement": "2200",
      "modelMinYear": "2015",
      "modelMaxYear": "2024"
    },
    ...
  ]
}
```

- Chroma 메타데이터는 **문자열만** 허용하므로 숫자도 문자열로 저장됩니다.

---

## 6. 데이터 출처 (MySQL)

| 소스 테이블/컬럼 | Document/메타데이터에서 쓰이는 항목 |
|------------------|-------------------------------------|
| car_master | car_id, maker_code, model_code, year, mileage, displacement, fuel, transmission, color, body_type, adv_status |
| platform_car | maker_name, model_group_name, model_name, trim_name, fuel, transmission, color, body_type, region, platform_car_id, platform_name, price, status, pc_url, m_url |
| cz_maker | country_name (maker_code로 조인) |
| 모델 집계 (동일 model_code) | typical_fuel, typical_transmission, typical_body_type, avg_displacement, min_year, max_year |

---

## 7. 관련 코드 파일

| 파일 | 역할 |
|------|------|
| `CarTextConverterService.java` | 차량 → 텍스트 변환, 메타데이터 맵 생성, `createCarEmbedding()` |
| `CarEmbeddingBatchService.java` | DB 조회·임베딩 생성·Chroma 배치 저장 오케스트레이션 |
| `EmbeddingService` | 텍스트 → 벡터 생성 |
| `ChromaVectorStoreService.java` | Chroma v2 API 호출 (add, query, delete 등) |
| `CarEmbeddingDto` | carId, platformCarId, text, embedding, metadata(JSON 문자열) |

이 문서는 위 코드와 동기화된 “Chroma에 들어가는 모든 정보” 목록입니다.
