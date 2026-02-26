# Carizon 백엔드 아키텍처

## 프로젝트 구조

현재 **모놀리식 아키텍처**로 구성되어 있으며, 향후 마이크로서비스로 분리 가능하도록 모듈화되어 있습니다.

```
carizon-backend/
├── crawler/          # 크롤러 모듈 (다양한 플랫폼)
├── merge/            # 데이터 병합 모듈
├── service/          # 비즈니스 로직 서비스
├── rag/              # RAG 서비스 모듈 (분리 가능)
│   ├── config/       # RAG 설정
│   ├── service/      # RAG 서비스 (인터페이스 기반)
│   ├── controller/   # RAG API
│   └── dto/          # RAG DTO
├── common/            # 공통 유틸리티
│   ├── dto/          # 공통 응답 DTO
│   ├── exception/    # 예외 처리
│   └── http/         # HTTP 클라이언트
└── admin/            # 관리자 API
```

## 아키텍처 결정: 모놀리식 vs 마이크로서비스

### 현재: 모놀리식 (Monolithic)

**장점:**
- ✅ 간단한 배포 (단일 JAR 파일)
- ✅ 빠른 개발 속도
- ✅ 공통 코드 재사용 용이
- ✅ 트랜잭션 관리 용이
- ✅ 작은 규모 프로젝트에 적합

**단점:**
- ❌ 서비스 간 결합도 증가 가능
- ❌ 독립적 스케일링 어려움
- ❌ 기술 스택 변경 어려움

### 향후 분리 가능성

RAG 서비스는 **인터페이스 기반**으로 설계되어 있어, 필요시 별도 서비스로 분리 가능:

```java
// 인터페이스 기반 설계
public interface EmbeddingServiceInterface {
    float[] generateEmbedding(String text) throws Exception;
}

// 구현체는 나중에 별도 서비스로 분리 가능
@Service
public class EmbeddingService implements EmbeddingServiceInterface { ... }
```

**분리 시 고려사항:**
- RAG 서비스는 독립적으로 스케일링 가능
- 벡터 DB와 LLM 리소스가 많을 경우 분리 고려
- API Gateway 패턴 적용 가능

## 리팩토링 완료 사항

### 1. 전역 예외 처리
- `GlobalExceptionHandler` 추가
- 일관된 에러 응답 형식

### 2. 공통 응답 형식
- `ApiResponse<T>` 공통 DTO
- 모든 API 응답 형식 통일

### 3. HTTP 클라이언트 통합
- `HttpClientService`로 OkHttp 통합 관리
- 크롤러와 RAG 서비스 모두 동일 클라이언트 사용

### 4. 인터페이스 기반 설계
- RAG 서비스 인터페이스화
- 향후 분리 용이

### 5. 컨트롤러 표준화
- 공통 응답 형식 사용
- Swagger 문서화 개선

## 모듈별 책임

### Crawler 모듈
- 다양한 중고차 플랫폼 크롤링
- 원시 데이터 수집 (`raw_*` 테이블)

### Merge 모듈
- 원시 데이터 정규화
- 플랫폼별 데이터 병합
- 마스터 데이터 생성 (`car_master`, `platform_car`)

### Service 모듈
- 비즈니스 로직 처리
- 차량 검색/조회 서비스

### RAG 모듈
- 차량 데이터 임베딩 생성
- 벡터 검색
- LLM 기반 추천

### Common 모듈
- 공통 유틸리티
- 예외 처리
- HTTP 클라이언트

## 데이터 흐름

```
1. 크롤링
   Crawler → raw_* 테이블

2. 데이터 병합
   MergeService → platform_car, car_master

3. 임베딩 생성 (RAG)
   CarEmbeddingBatchService → Chroma 벡터 DB

4. 추천 요청
   사용자 쿼리 → 임베딩 생성 → 벡터 검색 → LLM 추천 → 응답
```

## 향후 개선 방향

### 단기
- [ ] 스케줄러로 주기적 임베딩 업데이트
- [ ] 검색 결과 캐싱
- [ ] API 응답 시간 모니터링

### 중기
- [ ] RAG 서비스 독립 모듈화 (같은 프로젝트 내)
- [ ] 이벤트 기반 아키텍처 고려
- [ ] 메시지 큐 도입 검토

### 장기
- [ ] 필요시 RAG 서비스를 별도 마이크로서비스로 분리
- [ ] API Gateway 도입
- [ ] 서비스 메시 (Service Mesh) 고려

## 결론

**현재는 모놀리식이 적합**하며, 다음과 같은 이유로 같은 프로젝트에 유지하는 것을 권장합니다:

1. **개발 속도**: 빠른 프로토타이핑과 개발
2. **운영 단순성**: 단일 배포 단위
3. **코드 재사용**: 공통 도메인 모델과 서비스 재사용
4. **모듈화**: 이미 잘 분리되어 있어 필요시 분리 가능

**분리를 고려해야 하는 시점:**
- RAG 서비스의 트래픽이 크게 증가할 때
- 벡터 DB/LLM 리소스가 독립적으로 스케일링이 필요할 때
- 팀이 분리되어 독립적으로 개발이 필요할 때
