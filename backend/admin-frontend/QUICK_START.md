# 어드민 프로젝트 빠른 시작 가이드

## 1. 프로젝트 생성

```bash
# admin-frontend 디렉토리로 이동
cd admin-frontend

# 의존성 설치
npm install
```

## 2. 개발 서버 실행

```bash
npm run dev
```

## 3. 접속

브라우저에서 **http://localhost:3000** 접속

**중요**: 백엔드 서버가 `http://localhost:8080`에서 실행 중이어야 합니다.

## 4. 백엔드 서버 실행 확인

백엔드가 실행 중인지 확인:
```bash
curl http://localhost:8080/actuator/health
```

## 5. CORS 설정 (필요시)

백엔드에서 CORS가 허용되어 있어야 합니다. `SecurityConfig`에서 확인하세요.

## 문제 해결

### 포트 충돌
- 기본 포트 3000이 사용 중이면 Vite가 자동으로 다른 포트 사용
- 콘솔에 표시된 포트로 접속

### API 연결 실패
- 백엔드 서버가 실행 중인지 확인
- `vite.config.ts`의 proxy 설정 확인
- 브라우저 개발자 도구 Network 탭에서 에러 확인
