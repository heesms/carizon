# Git 사용 가이드

## 현재 상태
- 저장소: `https://github.com/heesms/carizon.git`
- 브랜치: `gpt_code`
- 변경된 파일들이 있음

## 수정한 내용 반영하기

### 1. 변경사항 확인
```bash
git status
```

### 2. 변경사항 스테이징 (커밋할 파일 선택)

**모든 변경사항 추가:**
```bash
git add .
```

**특정 파일만 추가:**
```bash
git add pom.xml
git add src/main/resources/application.yaml
git add src/main/java/com/carizon/
```

**새로 추가된 파일들:**
```bash
git add ARCHITECTURE.md
git add RAG_SETUP.md
git add docker-compose.yml
git add src/main/java/com/carizon/common/
git add src/main/java/com/carizon/rag/
git add src/main/java/com/carizon/admin/EmbeddingAdminController.java
```

### 3. 커밋 (변경사항 저장)
```bash
git commit -m "feat: RAG 기반 차량 추천 시스템 추가 및 리팩토링

- RAG 서비스 모듈 추가 (Chroma 벡터 DB, Ollama LLM 연동)
- 공통 응답 형식 및 전역 예외 처리 추가
- HTTP 클라이언트 통합
- 컨트롤러 표준화
- 아키텍처 문서 추가"
```

### 4. 원격 저장소에 푸시
```bash
git push origin gpt_code
```

## .gitignore 확인

`target/` 폴더는 빌드 결과물이므로 커밋하지 않는 것이 좋습니다.
`.gitignore` 파일에 이미 포함되어 있는지 확인하세요.

## 주의사항

### 커밋하지 말아야 할 것들:
- `target/` (빌드 결과물)
- `node_modules/` (프론트엔드 의존성)
- 개인 설정 파일
- API 키가 포함된 파일

### 커밋해야 할 것들:
- 소스 코드 (`.java` 파일)
- 설정 파일 (`application.yaml`, `pom.xml`)
- 문서 파일 (`.md` 파일)
- `docker-compose.yml`

## 빠른 명령어 모음

```bash
# 상태 확인
git status

# 모든 변경사항 추가
git add .

# 커밋
git commit -m "커밋 메시지"

# 푸시
git push origin gpt_code

# 풀 (다른 사람이 푸시한 내용 가져오기)
git pull origin gpt_code
```
