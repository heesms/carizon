# RAG 기반 차량 추천 시스템 설정 가이드

## 개요

이 시스템은 중고차 매물 데이터를 크롤링하여 벡터 DB에 저장하고, RAG(Retrieval-Augmented Generation) 기반으로 LLM이 차량을 추천해주는 시스템입니다.

**모든 구성 요소는 무료로 사용 가능합니다!**

## 아키텍처

```
[크롤러] → [MySQL 원장] → [임베딩 생성] → [Chroma 벡터 DB] → [RAG 검색] → [Ollama LLM] → [추천 API]
```

## 필수 구성 요소

### 0. Docker 설치 (Windows 사용자)

**Windows에서 Docker 실행:**
1. Docker Desktop for Windows 설치: https://www.docker.com/products/docker-desktop/
2. WSL 2 설치 (필수): PowerShell 관리자 권한에서 `wsl --install` 실행 후 재부팅
3. 자세한 가이드: [DOCKER_WINDOWS_GUIDE.md](./DOCKER_WINDOWS_GUIDE.md) 참조

**docker-compose 사용 (권장):**
프로젝트 루트에서 다음 명령어로 MySQL과 Chroma를 한 번에 실행:

```powershell
# PowerShell에서
docker-compose up -d

# 또는 개별 실행
docker-compose up -d mysql    # MySQL만
docker-compose up -d chroma   # Chroma만
```

### 1. Chroma 벡터 DB (무료, 오픈소스)

**docker-compose 사용 (권장):**
```powershell
docker-compose up -d chroma
```

**또는 개별 Docker 컨테이너로 실행:**
```bash
docker run -d -p 8000:8000 chromadb/chroma
```

**또는 Python으로 직접 설치:**
```bash
pip install chromadb
chroma run --host localhost --port 8000
```

### 2. Ollama LLM (무료, 로컬 실행)

Ollama 설치: https://ollama.ai/

```bash
# Ollama 설치 후
ollama pull llama3.1:8b
ollama pull nomic-embed-text  # 임베딩 모델 (선택사항)
```

### 3. Hugging Face API (선택사항, 무료 티어)

Hugging Face API 키가 있으면 사용 가능 (환경변수로 설정):
- 무료 티어: 월 30,000 요청
- API 키 발급: https://huggingface.co/settings/tokens

## 설정

### application.yaml 설정

기본 설정은 이미 되어 있습니다. 필요시 수정:

```yaml
rag:
  chroma:
    base-url: http://localhost:8000
    collection-name: car_listings
  
  embedding:
    provider: ollama  # ollama 또는 huggingface
    ollama:
      base-url: http://localhost:11434
      model: nomic-embed-text
  
  llm:
    provider: ollama  # ollama 또는 huggingface
    ollama:
      base-url: http://localhost:11434
      model: llama3.1:8b
```

### Hugging Face 사용 시

**Windows PowerShell에서 환경변수 설정:**
```powershell
$env:HUGGINGFACE_API_KEY="your_api_key_here"
```

**Linux/Mac에서 환경변수 설정:**
```bash
export HUGGINGFACE_API_KEY=your_api_key_here
```

또는 application.yaml에서 직접 설정 (보안상 권장하지 않음)

## 사용 방법

### 1. 차량 데이터 임베딩 생성

먼저 크롤링된 차량 데이터를 벡터 DB에 임베딩으로 저장해야 합니다.

**전체 차량 임베딩:**
```bash
POST /admin/embedding/all
```

**단일 차량 임베딩:**
```bash
POST /admin/embedding/car/{carId}
```

**범위 차량 임베딩:**
```bash
POST /admin/embedding/range?fromCarId=1&toCarId=1000
```

### 2. 차량 추천 API 사용

```bash
POST /api/recommendations
Content-Type: application/json

{
  "query": "가족용 SUV, 3000만원 이하, 연비 좋은 차",
  "maxResults": 5,
  "maxPrice": 3000,
  "fuel": "가솔린"
}
```

**응답 예시:**
```json
{
  "recommendation": "요청하신 조건에 맞는 차량을 추천드립니다...",
  "cars": [
    {
      "carId": 123,
      "maker": "현대",
      "model": "싼타페",
      "trim": "디젤 2.2",
      "year": 2020,
      "mileage": 45000,
      "price": 2800,
      "fuel": "디젤",
      "transmission": "자동",
      "color": "흰색",
      "url": "https://...",
      "relevanceScore": 0.85,
      "reason": "요구사항과 높은 유사도(85%)를 보입니다..."
    }
  ]
}
```

## 무료 대안 옵션

### 임베딩 모델

1. **Ollama (로컬)**: `nomic-embed-text` - 완전 무료, 로컬 실행
2. **Hugging Face Inference API**: 무료 티어 사용 가능
   - 모델: `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` (한국어 지원)

### LLM 모델

1. **Ollama (로컬)**: 
   - `llama3.1:8b` - **추천** ✅ (안정적, 전반적 성능 우수)
   - `qwen2.5:7b` - 한국어 성능 우수하나 일부 사용자 경험에서 불안정
   - `gemma2:9b` - 경량화, 한국어 지원 제한적
   
   **모델 선택 및 파인튜닝 가이드:** [LLM_MODEL_GUIDE.md](./LLM_MODEL_GUIDE.md) 참조
   
2. **Hugging Face Inference API**: 무료 티어 사용 가능
   - `meta-llama/Llama-3.1-8B-Instruct`

## 주의사항

1. **첫 실행 시**: 전체 차량 임베딩 생성에 시간이 걸릴 수 있습니다 (차량 수에 따라)
2. **Ollama 모델 다운로드**: 첫 실행 시 모델을 다운로드하므로 시간이 걸릴 수 있습니다
3. **메모리 요구사항**: Ollama는 모델 크기에 따라 메모리가 필요합니다 (8B 모델 기준 약 8GB RAM)
4. **Chroma DB**: 벡터 데이터가 쌓이면 디스크 공간이 필요합니다

## 트러블슈팅

### Docker 관련 문제 (Windows)
- **WSL 2 미설치**: PowerShell 관리자 권한에서 `wsl --install` 실행 후 재부팅
- **Docker Desktop 시작 안 됨**: Hyper-V 활성화 및 가상화(BIOS) 확인
- **포트 충돌**: `netstat -ano | findstr :8000` 또는 `netstat -ano | findstr :3306`로 확인
- 자세한 내용: [DOCKER_WINDOWS_GUIDE.md](./DOCKER_WINDOWS_GUIDE.md) 참조

### Chroma 연결 실패
- Chroma가 실행 중인지 확인: 
  - Windows: `curl http://localhost:8000/api/v1/heartbeat` 또는 브라우저에서 접속
  - Linux/Mac: `curl http://localhost:8000/api/v1/heartbeat`
- 포트가 8000인지 확인
- Docker 컨테이너 상태 확인: `docker ps` 또는 `docker-compose ps`

### MySQL 연결 실패
- MySQL이 실행 중인지 확인: `docker ps | findstr mysql` (Windows) 또는 `docker ps | grep mysql` (Linux/Mac)
- 컨테이너 로그 확인: `docker logs carizon-mysql`
- 데이터베이스가 생성되었는지 확인: `docker exec -it carizon-mysql mysql -u carizon -p`

### Ollama 연결 실패
- Ollama가 실행 중인지 확인: `curl http://localhost:11434/api/tags`
- 모델이 설치되어 있는지 확인: `ollama list`

### 임베딩 생성 실패
- Hugging Face API 키가 올바른지 확인
- 네트워크 연결 확인
- API 할당량 확인 (무료 티어 제한)

## 성능 최적화

1. **배치 임베딩**: 대량 데이터는 범위별로 나눠서 처리
2. **증분 업데이트**: 새로 크롤링된 차량만 임베딩 업데이트
3. **캐싱**: 자주 검색되는 쿼리 결과 캐싱 고려

## 다음 단계

- [ ] 스케줄러로 주기적 임베딩 업데이트
- [ ] 검색 결과 캐싱 추가
- [ ] 더 정교한 프롬프트 엔지니어링
- [ ] 사용자 피드백 기반 학습
