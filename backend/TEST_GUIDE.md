# RAG 시스템 테스트 가이드

## 1단계: 필수 서비스 실행

### 1-1. Ollama 설치 및 실행

**Windows:**
1. https://ollama.ai/download 에서 Ollama 다운로드
2. 설치 후 실행 (시작 메뉴에서 "Ollama" 검색)

**설치 확인:**
```bash
ollama --version
```

### 1-2. 필요한 모델 다운로드

**LLM 모델 (추천용):**
```bash
ollama pull llama3.1:8b
```

**임베딩 모델 (벡터 검색용):**
```bash
ollama pull nomic-embed-text
```

**모델 확인:**
```bash
ollama list
```

### 1-3. Chroma 벡터 DB 실행

**Docker Compose 사용:**
```bash
cd c:\git\carizon\carizon\backend
docker-compose up -d chroma
```

**또는 직접 실행:**
```bash
docker run -d -p 8000:8000 chromadb/chroma
```

**확인:**
```bash
curl http://localhost:8000/api/v1/heartbeat
```

### 1-4. Spring Boot 애플리케이션 실행

**IDE에서 실행:**
- `AppApplication.java` 우클릭 → `Run`

**또는 터미널에서:**
```bash
mvn spring-boot:run
```

**확인:**
- http://localhost:8080/api/health 접속

## 2단계: 차량 데이터 임베딩 생성

### 2-1. 전체 차량 임베딩 생성 (시간 소요)

**API 호출:**
```bash
POST http://localhost:8080/admin/embedding/all
```

**또는 curl:**
```bash
curl -X POST http://localhost:8080/admin/embedding/all
```

**또는 Swagger UI:**
- http://localhost:8080/swagger-ui.html
- `/admin/embedding/all` 엔드포인트 찾아서 실행

### 2-2. 테스트용 소량 데이터만 임베딩

**특정 차량만:**
```bash
POST http://localhost:8080/admin/embedding/car/1
```

**범위 지정:**
```bash
POST http://localhost:8080/admin/embedding/range?fromCarId=1&toCarId=10
```

## 3단계: 추천 API 테스트

### 3-1. 기본 추천 테스트

**요청:**
```bash
POST http://localhost:8080/api/recommendations
Content-Type: application/json

{
  "query": "가족용 SUV, 3000만원 이하, 연비 좋은 차",
  "maxResults": 5
}
```

**curl 예시:**
```bash
curl -X POST http://localhost:8080/api/recommendations \
  -H "Content-Type: application/json" \
  -d "{\"query\": \"가족용 SUV, 3000만원 이하\", \"maxResults\": 5}"
```

### 3-2. 필터링 포함 추천

```json
{
  "query": "가족용 차량",
  "maxResults": 5,
  "maxPrice": 3000,
  "fuel": "가솔린"
}
```

### 3-3. Swagger UI에서 테스트

1. http://localhost:8080/swagger-ui.html 접속
2. `/api/recommendations` 엔드포인트 찾기
3. `Try it out` 클릭
4. Request body 입력
5. `Execute` 클릭

## 4단계: 문제 해결

### Ollama 연결 실패

**확인:**
```bash
curl http://localhost:11434/api/tags
```

**해결:**
- Ollama가 실행 중인지 확인
- 포트 11434가 열려있는지 확인
- 방화벽 설정 확인

### Chroma 연결 실패

**확인:**
```bash
curl http://localhost:8000/api/v1/heartbeat
```

**해결:**
- Docker 컨테이너가 실행 중인지 확인: `docker ps`
- 포트 8000이 열려있는지 확인

### 임베딩 생성 실패

**확인:**
- application.yaml에서 `rag.embedding.provider`가 `ollama`인지 확인
- Ollama 모델이 다운로드되었는지 확인: `ollama list`

### LLM 응답 실패

**확인:**
- application.yaml에서 `rag.llm.provider`가 `ollama`인지 확인
- `llama3.1:8b` 모델이 다운로드되었는지 확인

## 5단계: 테스트 시나리오

### 시나리오 1: 기본 추천
```json
{
  "query": "연비 좋은 차",
  "maxResults": 3
}
```

### 시나리오 2: 가격 필터
```json
{
  "query": "중형 세단",
  "maxResults": 5,
  "minPrice": 2000,
  "maxPrice": 4000
}
```

### 시나리오 3: 제조사 필터
```json
{
  "query": "현대차",
  "maxResults": 5,
  "maker": "현대"
}
```

## 6단계: 로그 확인

**애플리케이션 로그에서 확인:**
- 임베딩 생성 로그
- 벡터 검색 로그
- LLM 호출 로그

**Ollama 로그:**
- Ollama가 실행 중인 터미널에서 확인

## 빠른 테스트 체크리스트

- [ ] Ollama 설치 및 실행 확인
- [ ] `llama3.1:8b` 모델 다운로드
- [ ] `nomic-embed-text` 모델 다운로드 (선택)
- [ ] Chroma 벡터 DB 실행 확인
- [ ] Spring Boot 애플리케이션 실행 확인
- [ ] 차량 데이터 임베딩 생성 (최소 1개)
- [ ] 추천 API 테스트

## 다음 단계

임베딩이 생성되면 추천 API를 테스트할 수 있습니다!
