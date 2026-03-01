# Carizon Docker 배포 매뉴얼

도커 이미지 **빌드 → 로컬 실행 → OCI 레지스트리 푸시 → OCI에서 풀·실행**까지 전 과정을 한 문서로 정리했습니다.

---

## 1. 전체 구성

### 1.1 도커로 올라가는 것

| 구분 | 서비스 | 역할 | 포트(호스트) |
|------|--------|------|----------------|
| 앱 | **app** | Spring Boot 백엔드 | 8080 |
| 앱 | **frontend** | 사용자용 웹 (검색·추천·상세) | 3000 |
| 앱 | **admin** | 관리자용 웹 (배치·크롤·설정) | 3001 |
| DB·캐시 | **mysql** | 메인 DB | 3306 |
| DB·캐시 | **redis** | 캐시 | 6379 |
| 검색 | **elasticsearch** | 차량 검색 인덱스 | 9200 |
| 검색 | **meilisearch** | (선택, ES 전환 후 미사용 가능) | 7700 |
| 메시징 | **kafka** | 차량 인덱스 동기화 | 9092 |
| RAG | **chroma** | 벡터 DB (임베딩·추천) | 8000 |
| RAG | **ollama** | LLM·임베딩 (추천/해석용) | 11434 |

### 1.2 Ollama는 꼭 도커로 말아야 하나?

**아니요. 두 가지 방식 중 선택하면 됩니다.**

| 방식 | 설명 |
|------|------|
| **도커에 Ollama 포함** | `docker compose up` 시 **ollama** 서비스까지 같이 올라감. 같은 네트워크에서 app이 `http://ollama:11434` 로 호출. **전부 한 번에 띄우고 싶을 때** 적합. |
| **호스트에서 Ollama 실행** | PC/서버에 Ollama를 직접 설치해 두고, app 컨테이너만 띄울 때는 환경 변수로 `http://host.docker.internal:11434`(Windows/Mac) 또는 호스트 IP 지정. **GPU를 쓰고 싶을 때** 보통 이렇게 함. |

- 컴포즈에는 **ollama 서비스를 이미 넣어 두었고**, 기본값은 **도커 안 Ollama**(`http://ollama:11434`)입니다.
- 호스트 Ollama를 쓰려면 실행 시 다음처럼 덮어쓰면 됩니다.
  ```bash
  RAG_LLM_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  RAG_EMBEDDING_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  docker compose up -d
  ```
- Ollama를 **컴포즈에서 빼고** 쓰고 싶다면, `docker-compose.yml`에서 `ollama` 서비스 블록과 app의 `depends_on` 안 `ollama`만 제거한 뒤, 위 환경 변수로 호스트 주소만 넘기면 됩니다.

---

## 2. 로컬에서 이미지 빌드

### 2.1 한 번에 전체 빌드 (권장)

프로젝트 루트 기준 **backend** 디렉터리에서 실행합니다.

```powershell
cd c:\git\carizon\carizon\backend
docker compose build
```

- **app**: `backend/Dockerfile`  
- **frontend**: `frontend/Dockerfile`  
- **admin**: `admin-frontend/Dockerfile`  
- 나머지(mysql, redis, ollama 등)는 공식 이미지 사용으로 별도 빌드 없음.

### 2.2 서비스별 따로 빌드

```powershell
cd c:\git\carizon\carizon\backend

# 백엔드만
docker compose build app

# 프론트만
docker compose build frontend

# 어드민만
docker compose build admin
```

---

## 3. 로컬에서 실행 (docker compose)

### 3.1 전체 스택 실행 (Ollama 포함)

```powershell
cd c:\git\carizon\carizon\backend
docker compose up -d
```

- MySQL → Redis → Elasticsearch → Kafka → Chroma → **Ollama** → app → frontend, admin 순으로 기동됩니다.
- Ollama는 첫 기동 후 모델이 없으면 `docker exec -it carizon-ollama ollama pull nomic-embed-text`, `ollama pull qwen2.5:14b-instruct` 등으로 받아야 RAG/추천이 동작합니다.

### 3.2 빌드하면서 실행

```powershell
docker compose up -d --build
```

### 3.3 접속 주소 (로컬)

| 용도 | URL |
|------|-----|
| 사용자 웹 | http://localhost:3000 |
| 관리자 웹 | http://localhost:3001 |
| 백엔드 API | http://localhost:8080 |
| Ollama | http://localhost:11434 |

### 3.4 Ollama 없이 실행 (호스트 Ollama 사용)

호스트에서 이미 Ollama를 띄워 두었다면:

```powershell
# Windows PowerShell
$env:RAG_LLM_OLLAMA_BASE_URL="http://host.docker.internal:11434"
$env:RAG_EMBEDDING_OLLAMA_BASE_URL="http://host.docker.internal:11434"
docker compose up -d
```

그리고 **ollama 서비스만 띄우지 않으려면**:

```powershell
docker compose up -d mysql redis elasticsearch kafka chroma app frontend admin
```

(또는 `docker-compose.yml`에서 ollama 서비스와 app의 ollama 의존성을 제거해 두고 `docker compose up -d` 해도 됩니다.)

### 3.5 중지·삭제

```powershell
docker compose down
# 볼륨까지 지우려면
docker compose down -v
```

---

## 4. OCI 레지스트리에 푸시

### 4.1 사전 준비 (OCI 콘솔)

1. **컨테이너 레지스트리**  
   - OCI 콘솔 → 개발자 서비스 → 컨테이너 레지스트리  
   - 저장소 생성 (예: `carizon/backend`, `carizon/frontend`, `carizon/admin`)
2. **테넌시 네임스페이스** 확인 (예: `axxxxxxxxxx`)
3. **Auth Token** 발급 (프로필 → 내 프로필 → Auth Token)
4. **리전** 확인 (예: `ap-seoul-1`)

### 4.2 로그인

```powershell
docker login ap-seoul-1.ocir.io -u <테넌시네임스페이스>/<OCI사용자이메일>
# 비밀번호: Auth Token
```

예:

```powershell
docker login ap-seoul-1.ocir.io -u axxxxxxxxxx/oracleidentitycloudservice/your@email.com
```

### 4.3 이미지 태그·푸시

아래 `<테넌시네임스페이스>`, `<리전>`은 본인 값으로 바꿉니다.

```powershell
cd c:\git\carizon\carizon\backend

# 1) 로컬에서 이미지 빌드 (아직 안 했다면)
docker compose build app frontend admin

# 2) 백엔드
docker tag carizon-backend:latest ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/backend:latest
docker push ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/backend:latest

# 3) 프론트
docker tag carizon-frontend:latest ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/frontend:latest
docker push ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/frontend:latest

# 4) 어드민
docker tag carizon-admin:latest ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/admin:latest
docker push ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/admin:latest
```

---

## 5. OCI에서 이미지 풀해서 실행

### 5.1 레지스트리에서 풀

OCI Compute(VM) 또는 Container Instance에 Docker가 있다면:

```bash
# 로그인 (OCI에서 실행하는 경우 보통 Instance Principal 또는 API Key)
docker login ap-seoul-1.ocir.io -u <테넌시네임스페이스>/<OCI사용자이메일>
# 비밀번호: Auth Token

# 풀
docker pull ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/backend:latest
docker pull ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/frontend:latest
docker pull ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/admin:latest
```

### 5.2 OCI에서 실행하는 방식 (요지)

- **전체를 OCI 한 VM/컨테이너에서 돌리는 경우**  
  - 해당 서버에 `docker-compose.yml`을 옮긴 뒤, **이미지만** OCI 레지스트리 걸로 바꿔서 사용하면 됩니다.
  - 예: `image: ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/backend:latest`  
  - MySQL, Redis, ES, Kafka, Chroma, Ollama는 그대로 compose 서비스로 두거나, OCI 매니지드 서비스(MySQL, Redis 등)로 갈아끼우고 app만 환경 변수로 연결해도 됩니다.
- **app만 OCI Container Instances 등에서 돌리는 경우**  
  - DB·Redis·Chroma·Ollama는 별도 인프라(또는 다른 컴포즈)에 두고,  
  - 컨테이너 실행 시 `SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_HOST`, `RAG_CHROMA_BASE_URL`, `RAG_LLM_OLLAMA_BASE_URL` 등만 해당 호스트/URL로 설정해서 실행하면 됩니다.

### 5.3 compose 이미지만 OCI로 바꿔서 실행 (예시)

`docker-compose.oci.yml` 같은 파일을 하나 두고, `image`만 OCI 주소로 쓰는 방법입니다.

```yaml
# docker-compose.oci.yml (일부만 예시)
services:
  app:
    image: ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/backend:latest
    # build 대신 image만 사용
  frontend:
    image: ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/frontend:latest
  admin:
    image: ap-seoul-1.ocir.io/<테넌시네임스페이스>/carizon/admin:latest
  # mysql, redis, ollama 등은 기존 docker-compose.yml과 동일
```

실행:

```bash
docker compose -f docker-compose.yml -f docker-compose.oci.yml up -d
```

(실제로는 한 파일에 `image`만 OCI 주소로 통합해 두고 `docker compose up -d` 해도 됩니다.)

---

## 6. 요약 체크리스트

### 로컬

- [ ] `cd backend` 후 `docker compose build`
- [ ] `docker compose up -d` 로 전체 실행
- [ ] Ollama 사용 시: 컨테이너 Ollama 쓰면 `ollama pull nomic-embed-text`, `ollama pull qwen2.5:14b-instruct` 실행

### OCI 푸시

- [ ] OCI Container Registry 저장소 생성 (backend, frontend, admin)
- [ ] Auth Token 발급
- [ ] `docker login <리전>.ocir.io`
- [ ] `docker compose build app frontend admin` → `docker tag` → `docker push`

### OCI에서 실행

- [ ] `docker pull` 로 이미지 받기
- [ ] MySQL/Redis/Chroma 등 연결 정보를 환경 변수로 설정
- [ ] Ollama 사용 시: 같은 네트워크의 Ollama URL 또는 호스트 Ollama URL 지정

---

## 7. Ollama 정리

- **도커로 말아도 되고, 안 말아도 됩니다.**
- 컴포즈에는 **ollama 서비스를 넣어 두었고**, 기본은 **도커 안 Ollama** 사용입니다.
- GPU를 쓰거나 호스트에 이미 Ollama를 깔아 두었다면, **Ollama 서비스만 빼고** app에 `RAG_LLM_OLLAMA_BASE_URL`, `RAG_EMBEDDING_OLLAMA_BASE_URL`로 호스트 주소만 넘기면 됩니다.
