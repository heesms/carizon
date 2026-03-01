# Windows에서 Docker 실행 가이드

## 1. Docker Desktop for Windows 설치 (권장)

### 설치 방법

1. **Docker Desktop 다운로드**
   - 공식 사이트: https://www.docker.com/products/docker-desktop/
   - Windows용 설치 파일 다운로드

2. **WSL 2 설치 (필수)**
   - Docker Desktop은 WSL 2 백엔드를 사용합니다
   - PowerShell을 관리자 권한으로 실행 후:
   ```powershell
   wsl --install
   ```
   - 재부팅 후 WSL 2가 자동으로 설정됩니다

3. **Docker Desktop 설치**
   - 다운로드한 설치 파일 실행
   - 설치 중 "Use WSL 2 instead of Hyper-V" 옵션 선택 (권장)
   - 설치 완료 후 재부팅

4. **Docker Desktop 실행**
   - 시작 메뉴에서 Docker Desktop 실행
   - 시스템 트레이에 Docker 아이콘이 나타나면 준비 완료

### 확인 방법

PowerShell 또는 명령 프롬프트에서:

```powershell
docker --version
docker-compose --version
```

## 2. 프로젝트 실행 방법

### 방법 1: docker-compose 사용 (권장)

프로젝트 루트 디렉토리에서:

```powershell
# 모든 서비스 시작 (MySQL + Chroma)
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 서비스 중지
docker-compose down

# 데이터까지 삭제하려면
docker-compose down -v
```

### 방법 2: 개별 컨테이너 실행

**MySQL만 실행:**
```powershell
docker run -d `
  --name carizon-mysql `
  -e MYSQL_ROOT_PASSWORD=rootpassword `
  -e MYSQL_DATABASE=carizon `
  -e MYSQL_USER=carizon `
  -e MYSQL_PASSWORD=carizon!1 `
  -p 3306:3306 `
  -v mysql_data:/var/lib/mysql `
  mysql:8.0 `
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

**Chroma만 실행:**
```powershell
docker run -d `
  --name carizon-chroma `
  -p 8000:8000 `
  -v chroma_data:/chroma/chroma `
  -e IS_PERSISTENT=TRUE `
  -e ANONYMIZED_TELEMETRY=FALSE `
  chromadb/chroma:latest
```

## 3. 서비스 확인

### MySQL 연결 확인
```powershell
# 컨테이너 상태 확인
docker ps

# MySQL 로그 확인
docker logs carizon-mysql

# MySQL에 직접 연결 (선택사항)
docker exec -it carizon-mysql mysql -u carizon -p
# 비밀번호: carizon!1
```

### Chroma 연결 확인
```powershell
# 브라우저에서 접속
# http://localhost:8000/api/v1/heartbeat

# 또는 PowerShell에서
curl http://localhost:8000/api/v1/heartbeat
```

## 4. 백엔드 애플리케이션 실행

Docker 컨테이너들이 실행된 후, Spring Boot 애플리케이션을 실행합니다:

```powershell
# Maven으로 실행
mvn spring-boot:run

# 또는 JAR 파일로 실행
mvn package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

## 5. 트러블슈팅

### WSL 2 관련 문제

**WSL 2가 설치되지 않은 경우:**
```powershell
# 관리자 권한 PowerShell에서
wsl --install
# 재부팅 필요
```

**WSL 2 버전 확인:**
```powershell
wsl --list --verbose
```

### Docker Desktop이 시작되지 않는 경우

1. **Hyper-V 활성화 확인**
   - 제어판 > 프로그램 및 기능 > Windows 기능 켜기/끄기
   - Hyper-V 체크 확인

2. **가상화 활성화 확인**
   - BIOS/UEFI에서 가상화(VT-x/AMD-V) 활성화 확인

3. **방화벽 확인**
   - Windows Defender 방화벽에서 Docker 허용

### 포트 충돌 문제

**포트가 이미 사용 중인 경우:**
```powershell
# 포트 사용 확인
netstat -ano | findstr :3306
netstat -ano | findstr :8000

# 사용 중인 프로세스 종료 (PID 확인 후)
taskkill /PID <PID> /F
```

또는 `docker-compose.yml`에서 포트를 변경:
```yaml
ports:
  - "3307:3306"  # MySQL을 3307로 변경
  - "8001:8000"  # Chroma를 8001로 변경
```

### 컨테이너가 계속 재시작되는 경우

```powershell
# 로그 확인
docker logs carizon-mysql
docker logs carizon-chroma

# 컨테이너 상태 확인
docker ps -a
```

## 6. 데이터 백업 및 복원

### MySQL 데이터 백업
```powershell
docker exec carizon-mysql mysqldump -u carizon -pcarizon!1 carizon > backup.sql
```

### MySQL 데이터 복원
```powershell
docker exec -i carizon-mysql mysql -u carizon -pcarizon!1 carizon < backup.sql
```

### 볼륨 데이터 확인
```powershell
# 볼륨 목록 확인
docker volume ls

# 볼륨 상세 정보
docker volume inspect carizon-backend_mysql_data
docker volume inspect carizon-backend_chroma_data
```

## 7. 성능 최적화

### Docker Desktop 리소스 설정

1. Docker Desktop 열기
2. Settings > Resources
3. 메모리: 최소 4GB (8GB 권장)
4. CPU: 최소 2코어 (4코어 권장)
5. Apply & Restart

### WSL 2 리소스 제한 설정

`%USERPROFILE%\.wslconfig` 파일 생성:
```ini
[wsl2]
memory=8GB
processors=4
swap=2GB
```

## 8. 주의사항

1. **Windows 경로 문제**
   - Docker 컨테이너 내부는 Linux 환경이므로 경로 구분자가 다릅니다
   - 볼륨 마운트 시 상대 경로 사용 권장

2. **파일 권한**
   - Windows와 Linux 간 파일 권한 차이로 인한 문제 가능
   - 필요시 `chmod` 명령어 사용

3. **네트워크**
   - `localhost` 또는 `127.0.0.1` 사용 가능
   - Docker Desktop의 내부 네트워크 사용

4. **데이터 영속성**
   - `docker-compose down -v` 실행 시 데이터가 삭제됩니다
   - 중요한 데이터는 정기적으로 백업하세요

## 9. 추가 리소스

- Docker 공식 문서: https://docs.docker.com/desktop/windows/
- WSL 2 문서: https://docs.microsoft.com/windows/wsl/
- Docker Compose 문서: https://docs.docker.com/compose/
