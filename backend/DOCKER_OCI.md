# Docker 이미지 빌드 & OCI(오라클 클라우드) 레지스트리 푸시

## 1. 준비물

- Docker Desktop (또는 Docker Engine) 설치
- OCI 계정 + **Container Registry**에 저장소 생성
- OCI **Auth Token** (레지스트리 로그인용)

---

## 2. OCI에서 할 일

1. **컨테이너 레지스트리 저장소 만들기**
   - OCI 콘솔 → **개발자 서비스** → **컨테이너 레지스트리**
   - 저장소 생성 (예: `carizon/backend`, `carizon/frontend`)
   - **테넌시 네임스페이스** 확인 (예: `axxxxxxxxxx`, 레지스트리 로그인에 필요)

2. **Auth Token 발급**
   - 프로필 아이콘 → **내 프로필** → **Auth Token** → 토큰 생성
   - 생성된 토큰을 복사해 두기 (다시 안 보임)

3. **리전 확인**
   - 사용할 리전 코드 (예: `ap-seoul-1`, `ap-chuncheon-1`)

---

## 3. 로컬에서 할 일

### 3.1 백엔드 이미지 빌드

```powershell
# backend 폴더에서
cd c:\git\carizon\carizon\backend
docker build -t carizon-backend:latest .
```

### 3.2 OCI 레지스트리 로그인

형식: `docker login <리전>.ocir.io`  
사용자명: **테넌시 네임스페이스/OCI 사용자명** (예: `axxxxxxxxxx/oracleidentitycloudservice/your@email.com`)  
비밀번호: **Auth Token**

```powershell
docker login ap-seoul-1.ocir.io -u axxxxxxxxxx/oracleidentitycloudservice/your@email.com
# 비밀번호 입력 시 Auth Token 붙여넣기
```

### 3.3 이미지 태그 & 푸시

```powershell
# 태그 (테넌시네임스페이스/저장소이름:태그)
docker tag carizon-backend:latest ap-seoul-1.ocir.io/axxxxxxxxxx/carizon/backend:latest

# 푸시
docker push ap-seoul-1.ocir.io/axxxxxxxxxx/carizon/backend:latest
```

---

## 4. 프론트엔드(선택)

프론트는 별도 이미지로 올리거나, 백엔드에 정적 파일 포함 방식이면 백엔드만 올리면 됨.

```powershell
cd c:\git\carizon\carizon\frontend
docker build -t carizon-frontend:latest .
docker tag carizon-frontend:latest ap-seoul-1.ocir.io/axxxxxxxxxx/carizon/frontend:latest
docker push ap-seoul-1.ocir.io/axxxxxxxxxx/carizon/frontend:latest
```

---

## 5. 한 번에 하기 (PowerShell 스크립트)

아래 변수만 본인 값으로 바꾼 뒤 실행하면 됨.

```powershell
# === 본인 환경에 맞게 수정 ===
$OCI_REGION   = "ap-seoul-1"
$OCI_NAMESPACE = "axxxxxxxxxx"   # 테넌시 네임스ACE
$OCI_REPO     = "carizon/backend"
$IMAGE_TAG    = "latest"
# =============================

$IMAGE = "${OCI_REGION}.ocir.io/${OCI_NAMESPACE}/${OCI_REPO}:${IMAGE_TAG}"

Set-Location $PSScriptRoot
docker build -t "carizon-backend:${IMAGE_TAG}" .
docker tag "carizon-backend:${IMAGE_TAG}" $IMAGE
docker push $IMAGE
```

---

## 6. OCI에서 이미지 실행 시

MySQL, Redis, Chroma, Elasticsearch 등은 OCI 내 DB/서비스 또는 별도 컨테이너로 띄우고,  
백엔드 컨테이너는 **환경 변수**로 DB URL 등을 넘기면 됨.

```yaml
# 예: OCI Compute / Container Instances에서
environment:
  SPRING_DATASOURCE_URL: jdbc:mysql://your-mysql-host:3306/carizon
  SPRING_DATA_REDIS_HOST: your-redis-host
  # ... application.yaml에서 읽는 변수들
```

---

## 요약 체크리스트

- [ ] OCI Container Registry 저장소 생성
- [ ] Auth Token 발급
- [ ] `docker login <리전>.ocir.io`
- [ ] `backend`에서 `docker build` → `docker tag` → `docker push`
