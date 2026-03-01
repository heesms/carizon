# Carizon Admin Frontend

관리자 페이지 프론트엔드 프로젝트

## 빠른 시작

### 1. 프로젝트 생성 및 설치

```bash
# 프로젝트 디렉토리로 이동
cd admin-frontend

# 의존성 설치
npm install

# 환경 변수 설정 (선택사항)
cp .env.example .env
# .env 파일에서 VITE_API_BASE_URL 수정
```

### 2. 개발 서버 실행

```bash
npm run dev
```

### 3. 접속

브라우저에서 `http://localhost:3000` 접속

**주의**: 백엔드 서버(`http://localhost:8080`)가 실행 중이어야 합니다.

## 기술 스택

- **프레임워크**: React 18 + TypeScript
- **빌드 도구**: Vite
- **UI 라이브러리**: Ant Design
- **상태 관리**: React Query (서버 상태) + Zustand (클라이언트 상태)
- **HTTP 클라이언트**: Axios
- **차트**: Recharts

## 프로젝트 구조

```
admin-frontend/
├── src/
│   ├── api/              # API 클라이언트
│   │   ├── client.ts
│   │   ├── crawl.ts
│   │   ├── data.ts
│   │   ├── embedding.ts
│   │   ├── search.ts
│   │   ├── dashboard.ts
│   │   └── config.ts
│   ├── components/        # 공통 컴포넌트
│   │   ├── Layout/
│   │   ├── Table/
│   │   └── Chart/
│   ├── pages/            # 페이지 컴포넌트
│   │   ├── Dashboard/
│   │   ├── Crawl/
│   │   ├── Data/
│   │   ├── Embedding/
│   │   ├── Search/
│   │   └── Config/
│   ├── hooks/            # Custom Hooks
│   ├── store/            # 상태 관리
│   ├── types/            # TypeScript 타입
│   └── utils/            # 유틸리티
├── package.json
├── vite.config.ts
└── .env
```

## 주요 기능 페이지

### 1. 대시보드 (`/dashboard`)
- 시스템 통계 (차량 수, 크롤링 현황, 임베딩 현황)
- 최근 크롤링 이력
- 차트 (플랫폼별 통계)

### 2. 크롤링 관리 (`/crawl`)
- 크롤링 실행 (전체/플랫폼별)
- 크롤링 현황 조회
- 데이터 머지 실행
- 크롤링 이력 테이블

### 3. 데이터 조회 (`/data`)
- car_master 조회 (필터링, 페이징)
- platform_car 조회 (필터링, 페이징)
- 상세 정보 모달

### 4. 임베딩 관리 (`/embedding`)
- 임베딩 현황 (Chroma 통계)
- 전체/증분/범위 임베딩 실행
- 샘플 데이터 조회

### 5. 검색 관리 (`/search`)
- Meilisearch 인덱싱 (전체/증분/배치)
- 검색 테스트
- 인덱스 상태 조회

### 6. 설정 관리 (`/config`)
- 검색 모드 설정 (Meilisearch/DB)
- 코드 관리 (Maker, Model 등)
- LLM 프롬프트 관리
- LLM 환경 설정 (일치율, 가중치 등)

## 빌드 및 배포

```bash
# 프로덕션 빌드
npm run build

# 빌드 결과물은 dist/ 디렉토리에 생성됨
# 이를 정적 파일 서버(Nginx 등)에 배포하거나
# 백엔드의 static 리소스로 서빙 가능
```

## 백엔드 연동

프록시 설정이 되어 있어서 개발 중에는 자동으로 백엔드 API로 요청이 전달됩니다.

프로덕션에서는:
1. 같은 도메인에서 서빙: 프록시 불필요
2. 다른 도메인: CORS 설정 필요 또는 프록시 서버 사용
