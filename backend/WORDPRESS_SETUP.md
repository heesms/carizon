# WordPress 포스팅 설정 가이드

## 개요

모델코드를 입력하면 Best 매물을 자동으로 선정하여 WordPress에 포스팅하는 기능입니다.

## 설정 방법

### 1. application.properties 설정

```properties
# WordPress REST API 설정
blog.wordpress.api-url=https://your-wordpress-site.com
blog.wordpress.username=your-username
blog.wordpress.application-password=xxxx xxxx xxxx xxxx xxxx xxxx
```

또는 일반 비밀번호 사용:

```properties
blog.wordpress.api-url=https://your-wordpress-site.com
blog.wordpress.username=your-username
blog.wordpress.password=your-password
```

**주의:** Application Password 사용을 권장합니다 (보안상 더 안전).

### 2. WordPress Application Password 생성

1. WordPress 관리자 페이지 로그인
2. 사용자 → 프로필 → Application Passwords
3. "새 애플리케이션 비밀번호 추가" 클릭
4. 이름 입력 (예: "Carizon Blog Post")
5. 생성된 비밀번호 복사 (공백 포함, 그대로 사용)

### 3. WordPress REST API 활성화 확인

WordPress 4.7 이상에서는 REST API가 기본적으로 활성화되어 있습니다.

확인 방법:
```
https://your-wordpress-site.com/wp-json/wp/v2/posts
```

## 사용 방법

### 관리자 페이지

1. 관리자 페이지 접속
2. "블로그 포스팅" 메뉴 클릭
3. 모델 코드 입력 (예: `KIA_CORANDO_SPORT_2023`)
4. Best 매물 개수 설정 (기본: 10개)
5. 포스팅 상태 선택:
   - `draft`: 초안 (기본값)
   - `publish`: 즉시 발행
   - `pending`: 검토 대기
6. "WordPress에 포스팅" 버튼 클릭

### API 사용

#### 1. WordPress 포스팅 생성

```bash
POST /admin/recommendation/weekly-best/model/{modelCode}/post-to-wordpress
```

**파라미터:**
- `modelCode` (path): 모델 코드 (예: `KIA_CORANDO_SPORT_2023`)
- `limit` (query, 기본값: 10): Best 매물 개수
- `status` (query, 기본값: draft): 포스팅 상태 (draft, publish, pending)

**예시:**
```bash
curl -X POST "http://localhost:8080/admin/recommendation/weekly-best/model/KIA_CORANDO_SPORT_2023/post-to-wordpress?limit=10&status=draft"
```

**응답:**
```json
{
  "success": true,
  "data": {
    "postId": 12345,
    "title": "더 뉴 코란도 스포츠 주간 Best 매물 추천 (2024-01-01 ~ 2024-01-07)",
    "status": "draft",
    "carCount": 10,
    "message": "WordPress 포스팅이 성공적으로 생성되었습니다."
  }
}
```

#### 2. 포스팅 내용 미리보기 (WordPress 포스팅 없이)

```bash
POST /admin/recommendation/weekly-best/model/{modelCode}/generate
```

**파라미터:**
- `modelCode` (path): 모델 코드
- `limit` (query, 기본값: 10): Best 매물 개수

**예시:**
```bash
curl -X POST "http://localhost:8080/admin/recommendation/weekly-best/model/KIA_CORANDO_SPORT_2023/generate?limit=10"
```

**응답:**
```json
{
  "success": true,
  "data": {
    "title": "더 뉴 코란도 스포츠 주간 Best 매물 추천 (2024-01-01 ~ 2024-01-07)",
    "content": "<h2>...</h2>...",
    "carCount": "10"
  }
}
```

## 포스팅 내용 구조

자동 생성되는 포스팅은 다음 구조를 가집니다:

1. **제목**: "{모델명} 주간 Best 매물 추천 ({주간 범위})"
2. **소개**: 모델 소개 및 추천 기준 설명
3. **Best 매물 리스트**: 순위별 매물 정보
   - 순위
   - 차량명 (제조사 + 모델 + 트림 + 등급)
   - 매물 정보 테이블 (연식, 주행거리, 가격, 연료, 변속기, 지역)
   - 카리즌 스코어 및 평가 사유
   - 플랫폼 링크 (모바일/PC 자동 분기)
4. **주의사항**: 매물 선택 시 주의사항

## 트러블슈팅

### 1. "WordPress API URL이 설정되지 않았습니다" 오류

→ `application.properties`에 `blog.wordpress.api-url` 설정 확인

### 2. "WordPress 인증 정보가 설정되지 않았습니다" 오류

→ `blog.wordpress.application-password` 또는 `blog.wordpress.password` 설정 확인

### 3. "WordPress API 오류: 401" 오류

→ 인증 정보가 잘못되었습니다. Application Password를 다시 생성하세요.

### 4. "WordPress API 오류: 403" 오류

→ 사용자 권한이 부족합니다. WordPress 관리자 권한이 필요합니다.

### 5. "WordPress API 오류: 404" 오류

→ WordPress REST API가 비활성화되었거나 URL이 잘못되었습니다.

## 보안 고려사항

1. **Application Password 사용 권장**: 일반 비밀번호보다 안전합니다.
2. **HTTPS 사용**: WordPress 사이트는 HTTPS를 사용하세요.
3. **설정 파일 보안**: `application.properties`는 버전 관리에 포함하지 마세요.
4. **환경 변수 사용**: 프로덕션 환경에서는 환경 변수로 설정하세요.

```bash
export BLOG_WORDPRESS_API_URL=https://your-site.com
export BLOG_WORDPRESS_USERNAME=your-username
export BLOG_WORDPRESS_APPLICATION_PASSWORD=xxxx xxxx xxxx xxxx xxxx xxxx
```

## 관련 파일

- `WordPressService.java`: WordPress REST API 클라이언트
- `BlogPostService.java`: 블로그 포스팅 내용 생성
- `WeeklyBestAdminController.java`: 관리자 API 엔드포인트
- `admin-frontend/src/pages/BlogPost.tsx`: 관리자 페이지 UI
