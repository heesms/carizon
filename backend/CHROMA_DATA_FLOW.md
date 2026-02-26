# Chroma에 데이터 넣는 과정

## 전체 흐름

```
MySQL (car_master, platform_car)
    ↓
1. 차량 데이터 조회 (CarTextConverterService)
    ↓
2. 텍스트로 변환 (예: "현대 싼타페, 2020년식, 45000km, 2800만원...")
    ↓
3. 임베딩 생성 (EmbeddingService → Ollama/HuggingFace)
    ↓
4. 벡터 배열 생성 (float[])
    ↓
5. Chroma에 저장 (ChromaVectorStoreService)
```

## 상세 과정

### 1단계: 차량 데이터 조회 및 텍스트 변환

**파일:** `CarTextConverterService.java`

```java
// MySQL에서 차량 정보 조회
CarDetailRow car = SELECT FROM car_master, platform_car WHERE car_id = ?

// 텍스트로 변환
String text = "차량 정보: 현대 싼타페 디젤 2.2
연식: 2020년
주행거리: 45,000km
가격: 2,800만원
연료: 디젤
변속기: 자동
색상: 흰색
차종: SUV
지역: 서울
판매상태: ONSALE"
```

### 2단계: 임베딩 생성

**파일:** `EmbeddingService.java`

```java
// 텍스트를 벡터로 변환
float[] embedding = embeddingService.generateEmbedding(text);

// Ollama 사용 시:
POST http://localhost:11434/api/embeddings
{
  "model": "nomic-embed-text",
  "prompt": "차량 정보: 현대 싼타페..."
}

// 응답: [0.123, -0.456, 0.789, ...] (384차원 벡터)
```

### 3단계: Chroma에 저장

**파일:** `ChromaVectorStoreService.java`

```java
// Chroma API 호출
POST http://localhost:8000/api/v1/collections/car_listings/add
{
  "ids": ["car_1"],                    // 고유 ID
  "embeddings": [[0.123, -0.456, ...]], // 벡터 배열
  "documents": ["차량 정보: 현대..."],   // 원본 텍스트
  "metadatas": [{                      // 메타데이터
    "carId": "1",
    "platformCarId": "123",
    "maker": "현대",
    "model": "싼타페",
    "price": "2800",
    "year": "2020"
  }]
}
```

## 실제 코드 흐름

### 배치 서비스에서 호출

```java
// CarEmbeddingBatchService.embedCar()
1. CarTextConverterService.createCarEmbedding(carId)
   → MySQL에서 차량 데이터 조회
   → 텍스트로 변환
   → CarEmbeddingDto 생성

2. EmbeddingService.generateEmbedding(text)
   → Ollama API 호출
   → 벡터 배열 반환

3. ChromaVectorStoreService.addCarEmbedding(carEmbedding)
   → Chroma API 호출
   → 벡터 저장
```

## Chroma API 엔드포인트

### 컬렉션 생성 (자동)
```
POST http://localhost:8000/api/v1/collections
{
  "name": "car_listings",
  "metadata": {}
}
```

### 데이터 추가
```
POST http://localhost:8000/api/v1/collections/car_listings/add
{
  "ids": ["car_1", "car_2"],
  "embeddings": [[...], [...]],
  "documents": ["텍스트1", "텍스트2"],
  "metadatas": [{...}, {...}]
}
```

### 검색
```
POST http://localhost:8000/api/v1/collections/car_listings/query
{
  "query_embeddings": [[...]],
  "n_results": 5
}
```

## 데이터 구조 예시

### 저장되는 데이터

**ID:** `car_1`

**Document (텍스트):**
```
차량 정보: 현대 싼타페 디젤 2.2
연식: 2020년
주행거리: 45,000km
가격: 2,800만원
연료: 디젤
변속기: 자동
색상: 흰색
차종: SUV
지역: 서울
판매상태: ONSALE
```

**Embedding (벡터):**
```
[0.123, -0.456, 0.789, ..., 0.234] (384차원)
```

**Metadata (JSON):**
```json
{
  "carId": "1",
  "platformCarId": "123",
  "maker": "현대",
  "model": "싼타페",
  "price": "2800",
  "year": "2020"
}
```

## 사용 방법

### API로 임베딩 생성 및 저장

```bash
# 단일 차량
POST /admin/embedding/car/1

# 여러 차량 (범위)
POST /admin/embedding/range?fromCarId=1&toCarId=100

# 전체 차량
POST /admin/embedding/all
```

### 내부적으로 일어나는 일

1. MySQL에서 차량 데이터 조회
2. 텍스트로 변환
3. Ollama로 임베딩 생성
4. Chroma에 저장

## 확인 방법

### Chroma에서 데이터 확인

```bash
# 컬렉션 목록 조회
curl http://localhost:8000/api/v1/collections

# 컬렉션 정보 조회
curl http://localhost:8000/api/v1/collections/car_listings

# 데이터 개수 확인 (Python 스크립트 필요)
# 또는 애플리케이션 로그에서 확인
```

## 주의사항

1. **임베딩 생성 시간**: Ollama API 호출이 필요하므로 시간이 걸림
2. **벡터 차원**: 모델에 따라 다름 (nomic-embed-text는 768차원)
3. **메타데이터**: 검색 후 필터링에 사용됨
4. **ID 중복**: 같은 carId로 다시 저장하면 업데이트됨
