# 크롤링 속도에 영향을 주는 요소 (로직 변경 없이 튜닝 가능)

로직을 바꾸지 않고, 상수·설정만 조정해 첫차 크롤링 속도를 올릴 때 참고할 수 있는 요소들입니다.  
**주의**: 값을 너무 공격적으로 올리면 429/403 등 차단 위험이 있으니, 서버 응답을 보면서 소폭씩 조정하는 것을 권장합니다.

---

## 1. 페이지/요청 간 대기 시간 (Thread.sleep)

| 크롤러 | 위치 | 현재값 | 설명 |
|--------|------|--------|------|
| **EncarCrawler** | 목록 페이지 성공 후 | 500ms | `Thread.sleep(500)` |
| **EncarCrawler** | 상세 배치 간 | 250ms | `handleDetails` 내 `i += 20` 후 250ms |
| **EncarCrawler** | 재시도 시 | 900ms × attempt | BACKOFF_MS |
| **TcarCrawler** | 페이지 간 | 600ms | "부하 완화" |
| **CharanchaCrawler** | 페이지 간 | 600ms | "서버 부하 완화" |
| **ChachachaCrawler** | 페이지 간 | 600ms | "서버 부하 완화" |
| **KcarCrawler** | 페이지 성공 후 | 350ms | |
| **KcarCrawler** | 빈 페이지 시 | 500ms | |
| **ChutchaCrawler** | 페이지 간 | 200ms | `PAGE_DELAY_MS` |

**속도 개선 포인트**:  
- 600ms → 400~500ms, 350ms → 250ms 등으로 **소폭 감소** 시도 가능.  
- Encar 상세 250ms는 상세 호출 횟수가 많아 전체 시간에 영향이 크므로, 200ms 등으로 약간만 줄여보는 것도 가능.

---

## 2. 페이지 크기 / 한 번에 가져오는 건수

| 크롤러 | 상수/변수 | 현재값 | 비고 |
|--------|-----------|--------|------|
| **EncarCrawler** | PAGE_SIZE | 200 | 목록 API `offset/sr` 에 사용 |
| **EncarCrawler** | 상세 배치 크기 | 20 | `handleDetails` 에서 20개 ID씩 상세 요청 |
| **TcarCrawler** | perPage | 100 | 주석: "서버가 15만 허용하면 15로 낮춰" |
| **CharanchaCrawler** | perPage | 100 | 주석: "필요시 15로 낮출 수 있음" |
| **ChachachaCrawler** | pageSize | 1000 | "천 개씩 처리" |
| **KcarCrawler** | LIMIT | 30 | "KCar 기본 페이지 크기" |
| **ChutchaCrawler** | PAGE_SIZE | 50 | 목록 body `page_size` |

**속도 개선 포인트**:  
- **Encar**: 상세 20개 → 30~50개로 늘리면 상세 요청 횟수 감소 (API가 허용하는 상한 확인 필요).  
- **Kcar**: LIMIT 30 → 50 등 API 상한까지 올려보기.  
- **Chutcha**: PAGE_SIZE 50 → 100 등 (API/응답 크기 허용 시).

---

## 3. HTTP 클라이언트 설정

| 크롤러 | 현재 설정 | 개선 여지 |
|--------|-----------|-----------|
| **EncarCrawler** | `OkHttpClient` 기본값, timeout 미설정 | `callTimeout`/`readTimeout` 설정 + `ConnectionPool` 추가 시 재연결/대기 감소 가능 |
| **TcarCrawler** | call/read 30초 | 유지해도 무방 |
| **CharanchaCrawler** | call/read 30초 | 유지해도 무방 |
| **ChachachaCrawler** | `new OkHttpClient()` 기본값 | timeout + connectionPool 설정 시 일관성·안정성 향상 |
| **KcarCrawler** | `Proxy.NO_PROXY` 만 설정 | timeout + connectionPool |
| **ChutchaCrawler** | ConnectionPool(100, 60s), Dispatcher(64 max) | 이미 튜닝됨 |

**속도 개선 포인트**:  
- Encar/Chachacha/Kcar에 **ConnectionPool** (예: 20~50, 5분 유지) 적용 시 커넥션 재사용으로 지연 감소.  
- **readTimeout** 15~20초 등으로 적당히 주면 느린 응답에서만 빠르게 실패하고 재시도 가능.

---

## 4. 배치 실행 방식 (전체 “첫차” 크롤링 시간)

| 방식 | 메서드 | 동작 |
|------|--------|------|
| **순차** | `runDaily()` | 차차차 → Kcar → Tcar → 차란차 → 첫차 → 엔카 순서로 한 플랫폼씩 |
| **병렬** | `runDailyAsync()` | 6개 플랫폼 동시 실행 (CompletableFuture + 스레드풀 6) |

**속도 개선 포인트**:  
- 스케줄에서 `runDaily()` 대신 **`runDailyAsync()`** 를 사용하면, 플랫폼 간 대기 없이 병렬로 돌아가서 **전체 첫차 크롤링 시간**이 크게 줄어듦.  
- (DB/네트워크 동시 접속이 부담이면 스레드풀 크기만 4 등으로 줄이는 식으로 조절 가능.)

---

## 5. DB 배치

- 모든 크롤러가 **`jdbc.batchUpdate(sql, params)`** 로 한 페이지 단위 INSERT/UPDATE 수행.  
- JdbcTemplate 기본 배치 크기 제한은 없고, 한 번에 넘기는 리스트 크기가 곧 배치 크기.  
- **페이지 크기(perPage/PAGE_SIZE)** 를 키우면 요청 횟수와 DB 배치 횟수가 동시에 줄어듦.

---

## 6. Chutcha 상세 처리

- **detailPool** (12 스레드)가 선언되어 있으나, **실제 상세 요청(`fetchDetailSlim`)은 for 루프 안에서 동기 호출**로 한 건씩 처리됨.  
- 로직을 바꾸지 않는 범위에서는 “상세를 병렬로 부르는 구조”로 바꾸는 것은 제외하고,  
  **PAGE_DELAY_MS(200)** 나 **PAGE_SIZE(50)** 만 소폭 조정**하는 쪽으로 속도 개선을 고려할 수 있음.

---

## 요약: 우선 손댈 만한 것 (로직 유지)

1. **스케줄**: `runDaily()` → `runDailyAsync()` 로 바꿔 전체 경과 시간 단축.  
2. **대기 시간**: 600ms → 400~500ms, 350ms → 250ms, Encar 250ms → 200ms 등 **소폭 감소** 테스트.  
3. **페이지/배치 크기**: Encar 상세 20 → 30, Kcar LIMIT 30 → 50, Chutcha PAGE_SIZE 50 → 100 등 **API 한도 내에서 증가** 검토.  
4. **HTTP**: Encar/Chachacha/Kcar에 **ConnectionPool + timeout** 설정해 커넥션 재사용 및 타임아웃으로 지연 감소.

위 항목만으로도 로직 변경 없이 첫차 크롤링 속도를 올릴 여지가 있습니다.
