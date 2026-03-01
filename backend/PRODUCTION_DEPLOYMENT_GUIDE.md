# 프로덕션 배포 가이드

## 엔터프라이즈 환경에서의 프론트엔드 배포

### ❌ 개발 환경 (로컬 개발용)
```bash
npm run dev  # Vite 개발 서버 (포트 3000)
```
- 핫 리로드 지원
- 소스맵 포함
- 최적화 없음
- **프로덕션에서는 사용하지 않음**

---

## ✅ 프로덕션 배포 방법

### 방법 1: Spring Boot 정적 리소스로 서빙 (권장)

프론트엔드를 빌드해서 Spring Boot의 정적 리소스로 서빙합니다.

#### 1. 프론트엔드 빌드
```bash
cd admin-frontend
npm run build
```
빌드 결과물: `admin-frontend/dist/` 폴더에 생성

#### 2. 빌드 결과물을 Spring Boot에 복사
```bash
# Windows
xcopy /E /I admin-frontend\dist\* src\main\resources\static\

# Linux/Mac
cp -r admin-frontend/dist/* src/main/resources/static/
```

#### 3. Spring Boot 설정
`src/main/resources/application.yaml`:
```yaml
spring:
  web:
    resources:
      static-locations: classpath:/static/
      add-mappings: true
```

#### 4. 빌드 스크립트 자동화
`package.json`에 추가:
```json
{
  "scripts": {
    "build:prod": "npm run build && xcopy /E /I dist\\* ..\\src\\main\\resources\\static\\ /Y"
  }
}
```

#### 장점
- ✅ 단일 JAR 파일로 배포 가능
- ✅ CORS 문제 없음
- ✅ 추가 웹 서버 불필요
- ✅ 간단한 배포

#### 단점
- ❌ 프론트엔드 변경 시 백엔드 재빌드 필요

---

### 방법 2: Nginx로 정적 파일 서빙 (대규모 환경)

Nginx로 프론트엔드를 서빙하고, API는 Spring Boot로 연결합니다.

#### 1. 프론트엔드 빌드
```bash
cd admin-frontend
npm run build
```

#### 2. Nginx 설정
`/etc/nginx/sites-available/carizon-admin`:
```nginx
server {
    listen 80;
    server_name admin.carizon.com;
    
    # 정적 파일 서빙
    root /var/www/carizon-admin;
    index index.html;
    
    # SPA 라우팅 지원
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # API 프록시
    location /admin {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### 3. 빌드 결과물 배포
```bash
sudo cp -r admin-frontend/dist/* /var/www/carizon-admin/
sudo systemctl reload nginx
```

#### 장점
- ✅ 프론트엔드/백엔드 독립 배포
- ✅ Nginx의 고성능 정적 파일 서빙
- ✅ CDN 연동 용이
- ✅ SSL/TLS 설정 용이

#### 단점
- ❌ 추가 인프라 필요 (Nginx)

---

### 방법 3: Docker 컨테이너로 배포

#### Dockerfile (Multi-stage build)
```dockerfile
# Stage 1: 프론트엔드 빌드
FROM node:20-alpine AS frontend-builder
WORKDIR /app
COPY admin-frontend/package*.json ./
RUN npm ci
COPY admin-frontend/ .
RUN npm run build

# Stage 2: Spring Boot + 프론트엔드
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/*.jar app.jar
COPY --from=frontend-builder /app/dist ./static
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 빌드 및 실행
```bash
# 빌드
docker build -t carizon-backend:latest .

# 실행
docker run -p 8080:8080 carizon-backend:latest
```

---

### 방법 4: CI/CD 파이프라인 (GitHub Actions 예시)

`.github/workflows/deploy.yml`:
```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      # 프론트엔드 빌드
      - name: Build frontend
        run: |
          cd admin-frontend
          npm ci
          npm run build
          cp -r dist/* ../src/main/resources/static/
      
      # 백엔드 빌드
      - name: Build backend
        run: |
          mvn clean package -DskipTests
      
      # 배포
      - name: Deploy
        run: |
          # 서버에 배포하는 스크립트
          scp target/*.jar user@server:/app/
          ssh user@server "sudo systemctl restart carizon-backend"
```

---

## 권장 배포 전략

### 개발 환경
- 프론트엔드: `npm run dev` (Vite 개발 서버)
- 백엔드: Spring Boot 직접 실행
- 포트: 프론트엔드 3000, 백엔드 8080

### 스테이징 환경
- 프론트엔드: 빌드 후 Spring Boot 정적 리소스로 서빙
- 배포: 단일 JAR 파일

### 프로덕션 환경
- **소규모**: Spring Boot 정적 리소스 (방법 1)
- **중규모**: Nginx + Spring Boot (방법 2)
- **대규모**: Docker + Kubernetes (방법 3 + 오케스트레이션)

---

## 빌드 스크립트 예시

### Windows (build.bat)
```batch
@echo off
echo Building frontend...
cd admin-frontend
call npm run build
echo Copying to Spring Boot...
xcopy /E /I /Y dist\* ..\src\main\resources\static\
cd ..
echo Building Spring Boot...
call mvn clean package -DskipTests
echo Done!
```

### Linux/Mac (build.sh)
```bash
#!/bin/bash
echo "Building frontend..."
cd admin-frontend
npm run build
echo "Copying to Spring Boot..."
cp -r dist/* ../src/main/resources/static/
cd ..
echo "Building Spring Boot..."
mvn clean package -DskipTests
echo "Done!"
```

---

## 체크리스트

### 프로덕션 배포 전 확인사항

- [ ] 프론트엔드 빌드 성공 (`npm run build`)
- [ ] 환경 변수 설정 (API URL 등)
- [ ] 정적 파일이 `src/main/resources/static/`에 복사됨
- [ ] Spring Boot 빌드 성공 (`mvn package`)
- [ ] JAR 파일이 정상 실행됨
- [ ] CORS 설정 확인 (필요시)
- [ ] 로그 레벨 조정 (DEBUG → INFO)
- [ ] 보안 설정 확인

---

## 환경별 설정

### 개발 환경
```yaml
# application-dev.yaml
logging:
  level:
    com.carizon: DEBUG
```

### 프로덕션 환경
```yaml
# application-prod.yaml
logging:
  level:
    com.carizon: INFO
    org.springframework: WARN
```

실행 시:
```bash
java -jar app.jar --spring.profiles.active=prod
```
