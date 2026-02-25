# CODEX Context (Carizon)

최종 업데이트: 2026-02-25

## 1) 사용자 커뮤니케이션 성향

- 한국어 선호, 짧고 바로 실행형 응답 선호
- 제안/설명보다 즉시 수정, 즉시 검증, 즉시 커밋을 선호
- "지금 당장", "바로", "커밋해줘" 형태 요청이 많음
- 운영 환경 트러블슈팅(서버/도커/ES/API) 중심으로 빠른 원인 파악을 기대

## 2) 서비스/환경 기본값

- 서비스 도메인: `carizon.shop`
- 검색엔진: Elasticsearch 사용
- 포트 운영 규칙: 로컬 테스트 `127.0.0.1:19200` (요청 기준), 서버 운영 `127.0.0.1:9200`
- 코드베이스: `backend` + `carizon-frontend` + `frontend` + `mobile/carizon_app`

## 3) 누적 핵심 요구사항 (기억해야 할 고정 룰)

### 3-1. 검색/ES

- 집계(aggregation) 필드는 `text`가 아니라 `keyword` 사용
- maker/model/modelGroup 등 집계성 필드는 `.keyword` 우선
- Fielddata 에러 방지: `terms.field = xxx.keyword`
- 텍스트 검색 조건에 차량번호 검색 포함
- 100만원 이하 삭제 쿼리 요구 이력 있음 (필드 매핑 확인 후 실행)

### 3-2. 캐시

- 이미지(제조사/모델) 캐시만 허용
- 이미지 외 API/코드성 데이터 캐시는 비활성 선호

### 3-3. 매물 상세 UI

- "사고이력 3건 의심"에서 건수 텍스트 제거 요청 이력
- 느낌표 아이콘 대신 빨간 경광등(구급차 상단 느낌) 아이콘 선호
- 상세 모달 좌우 여백 확대 요청 이력
- 모달 스크롤 잘림/미동작 문제를 매우 민감하게 체크

### 3-4. SEO/색인

- 메인 외 페이지 색인 강화가 최우선 관심사
- `sitemap.xml` 운영 필요
- 상세 페이지 `/cars/{listingId}`는 전량 sitemap 포함 선호
- 리스트는 대표 URL(브랜드/차종/인기모델)만 20~200개 제한 포함
- 기타 무한 필터 조합은 sitemap 제외 + `noindex/canonical`
- 검색엔진 노출용 이름 보강: `car_master`의 제조사/모델그룹/모델/트림 비어 있으면 `platform_car` 값으로 대체
- favicon 노출(검색결과/탭 아이콘) 중요

### 3-5. ID/데이터 정책

- SEO 측면에서 `CAR_ID`/상세 식별자는 재사용 없이 지속 증가(영구 ID) 선호
- truncate 이후에도 외부 노출 식별자 안정성 유지 요구

### 3-6. 찜(Like) 정책

- `car_id` 기준보다 차량번호 기준 저장 선호
- 머지/재색인/ID변경에도 찜 유지되도록 요구
- 검색엔진에서 차량번호 미존재 시 찜 정리 + "판매완료로 좋아요에서 제외" 안내 문구 선호

### 3-7. 플랫폼/코드매핑

- `ENCAR_TRUCK`은 `ENCAR`와 분리된 코드매핑 필요
- `PLATFORM_CAR.PLATFORM_NAME = ENCAR_TRUCK`으로 별도 처리
- 화면 표시는 통합 브랜드로 `ENCAR` 노출 선호

### 3-8. LLM/추천 정책

- LLM은 결정이 아니라 해석 전용
- 파싱 출력은 JSON 스키마 강제 + 근거 span 포함
- 2단계 프롬프트(파서/설명) 선호
- ENUM 강제 매핑(연료/색상/차종)
- Ollama 장애/지연 기본 가정: 짧은 timeout, 실패 시 룰기반 fallback, 서킷브레이커/스킵모드 필요
- 추천에서 "기타 제조사" 제외 요구 이력

### 3-9. 배치 스케줄 요구 이력

- 매주 금요일 22:00 실행
- 순차 작업: `/admin/crawl/runAll` -> `/admin/pipeline/rebuild-platform-car` -> `/admin/pipeline/code-mapping-only?scope=FULL` -> `/admin/pipeline/rebuild-car-master`
- 순차 완료 후 병렬 작업: `/admin/search/reindex` + `/admin/embedding/reset-and-all`

## 4) 자주 나온 장애 패턴

- `Fielddata is disabled on [makerCode]`: 원인 `text` 필드 집계 시도, 대응 `.keyword` 필드 집계
- `NoClassDefFoundError: ApiResponse$ApiResponseBuilder`: 원인 후보 빌드 산출물 불일치/의존성 꼬임, 대응 클린빌드/배포 아티팩트 정합성 확인
- `Invalid character found in method name [0x16 0x03 ...]`: 원인 HTTPS 트래픽을 HTTP 포트로 요청

## 5) 재시작 시 Codex 작업 루틴

1. `docs/CODEX_CONTEXT.md` 먼저 읽기
2. 현재 브랜치/변경분 확인 (`git status`)
3. 사용자 최신 요청 1개를 가장 먼저 실행
4. 결과 검증 후 바로 공유
5. 요청 시 즉시 커밋/푸시

## 6) 권장 초기 프롬프트 (사용자 재접속 시)

`docs/CODEX_CONTEXT.md 읽고 현재 브랜치 상태 확인 후, 마지막 요청부터 바로 이어서 처리해줘.`
