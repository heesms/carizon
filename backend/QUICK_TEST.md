# 빠른 테스트 가이드

## 1. Ollama 설치 및 모델 다운로드

### Windows에서 Ollama 설치
1. https://ollama.ai/download 접속
2. Windows용 다운로드 및 설치
3. 설치 후 Ollama 실행 (시작 메뉴에서 검색)

### 필수 모델 다운로드
```bash
# 터미널에서 실행
ollama pull llama3.1:8b
ollama pull nomic-embed-text
```

## 2. Chroma 벡터 DB 실행

```bash
# backend 폴더에서 실행
docker-compose up -d chroma
```

또는 직접 실행:
```bash
docker run -d -p 8000:8000 chromadb/chroma
```

## 3. Spring Boot 애플리케이션 실행

IDE에서 `AppApplication.java` 실행하거나:
```bash
mvn spring-boot:run
```

## 4. 차량 데이터 임베딩 생성

**Swagger UI 사용 (가장 쉬움):**
1. http://localhost:8080/swagger-ui.html 접속
2. `/admin/embedding` 섹션 찾기
3. `/admin/embedding/car/{carId}` 선택
4. `Try it out` → carId에 `1` 입력 → `Execute`

**또는 curl:**
```bash
curl -X POST http://localhost:8080/admin/embedding/car/1
```

**테스트용으로 여러 차량:**
```bash
curl -X POST "http://localhost:8080/admin/embedding/range?fromCarId=1&toCarId=10"
```

## 5. 추천 API 테스트

**Swagger UI 사용:**
1. http://localhost:8080/swagger-ui.html 접속
2. `/api/recommendations` 찾기
3. `Try it out` 클릭
4. Request body 입력:
```json
{
  "query": "가족용 SUV, 3000만원 이하",
  "maxResults": 5
}
```
5. `Execute` 클릭

**또는 curl:**
```bash
curl -X POST http://localhost:8080/api/recommendations \
  -H "Content-Type: application/json" \
  -d "{\"query\": \"가족용 SUV, 3000만원 이하\", \"maxResults\": 5}"
```

## 체크리스트

- [ ] Ollama 설치 및 실행 확인 (`ollama list` 명령어로 확인)
- [ ] `llama3.1:8b` 모델 다운로드 완료
- [ ] `nomic-embed-text` 모델 다운로드 완료
- [ ] Chroma 실행 확인 (`curl http://localhost:8000/api/v1/heartbeat`)
- [ ] Spring Boot 실행 확인 (`curl http://localhost:8080/api/health`)
- [ ] 최소 1개 차량 임베딩 생성 완료
- [ ] 추천 API 테스트 성공

## 문제 해결

### Ollama 연결 안 될 때
```bash
# Ollama가 실행 중인지 확인
curl http://localhost:11434/api/tags
```

### Chroma 연결 안 될 때
```bash
# Chroma가 실행 중인지 확인
curl http://localhost:8000/api/v1/heartbeat
```

### 임베딩 생성 실패 시
- application.yaml에서 `rag.embedding.provider`가 `ollama`인지 확인
- Ollama 모델이 다운로드되었는지 확인: `ollama list`
