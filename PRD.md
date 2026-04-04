# PRD — 캐치잇 (CatchEat) 레스토랑 예약 플랫폼

> 작성일: 2026-04-01
> 버전: v1.0
> 기반 문서: catchtable-notion-v4.md, catchtable-project-plan.md, catchtable-api-spec.md, catchtable-tech-deep-dive.md, catchtable-feature-spec-qna.md, catchtable.sql

---

## 1. 제품 개요

### 1.1 제품명
캐치잇 (CatchEat)

### 1.2 한 줄 요약
캐치테이블 클론코딩 기반의 레스토랑 예약/빈자리 알림 플랫폼 + Slack 연동 즐겨찾기 + AI 자연어 예약·추천

### 1.3 프로젝트 목적
- 백엔드 엔지니어로서 **동시성 제어, 이벤트 기반 아키텍처, 실시간 통신, AI 연동** 등 실무 핵심 기술을 경험
- 캐치테이블의 크리스마스 예약 오픈 서버 과부하 사례처럼 현실적인 **부하 테스트 시나리오** 설계 및 검증
- 면접 단골 질문("동시에 N명이 몰리면?")에 **실전 경험과 수치 기반으로 답변** 가능한 포트폴리오 구축

### 1.4 타겟 사용자
| 사용자 유형 | 설명 |
|---|---|
| 일반 사용자 | 레스토랑을 검색하고 예약/빈자리 알림을 이용하는 소비자 |
| Slack 사용자 | Slack 채널에서 음식점 링크를 공유하여 즐겨찾기에 자동 추가하는 팀 |
| AI 챗봇 사용자 | 자연어로 예약을 요청하거나 맛집 추천을 받는 사용자 |

---

## 2. 핵심 문제 정의

| # | 문제 | 상황 | 해결 방향 |
|---|---|---|---|
| P1 | 동시 예약 충돌 | 크리스마스 이브 인기 매장에 100명이 동시 예약 → 초과 예약 발생 | Redis 분산 락(Redisson)으로 매장×날짜×시간대별 좌석 점유 원자적 처리 |
| P2 | 빈자리 알림 경쟁 | 취소/노쇼로 빈자리 발생 → 구독자들이 동시에 예약 시도 | 기존 예약 분산 락 재활용 — 동일한 경합 구조 |
| P3 | 노쇼 처리 | 예약 후 미방문 → 좌석 낭비 | 예약 시간 +15분 경과 시 자동 취소 스케줄러 + 노쇼 3회 시 30일간 예약 제한 + 빈자리 알림 트리거 |
| P4 | 기념일 트래픽 폭증 | 크리스마스 예약 오픈 시점에 평소 대비 10배 이상 트래픽 | Redis 캐시 + 분산 락 + Rate Limiting |
| P5 | 조회 성능 | 수천 명이 매장 검색 동시 조회 → DB 과부하 | Redis 캐시(Cache-Aside, TTL 5분) + PostGIS Spatial Index |
| P6 | 자연어 매장 매칭 | 사용자가 부르는 이름 ≠ DB 실제 매장명 ("모수 용산" vs "모수 서울") | pgvector 벡터 유사도 검색(Embedding) + PostGIS 복합 쿼리 |
| P7 | 선착순 쿠폰 정합성 | 수천 명 동시 요청 시 초과/중복 발급 + DB 병목 | Redis Lua 스크립트로 원자적 처리, Kafka로 DB 비동기 저장 (Write-Behind) |
| P8 | Slack 매장 매칭 | 공유된 링크가 플랫폼에 없는 매장 | 다단계 매칭(외부 ID → OG 매장명 → pgvector 유사도 검색), 실패 시 안내 |
| P9 | 알림 스팸 방지 | 같은 매장에서 취소가 연속 발생 시 알림 폭탄 | Redis 쿨다운 (SET NX + TTL 5분)으로 알림 간격 제한 |

---

## 3. 기능 요구사항

### 3.1 MVP (필수, 6주 내 완성)

#### F1. 회원/인증
- Google OAuth 2.0 로그인 (최초 로그인 시 자동 회원가입)
- JWT 발급 (Access Token + Refresh Token)
- 토큰 갱신, 로그아웃, 내 정보 조회
- 노쇼 3회 이상 시 30일간 예약 제한

**API 엔드포인트:**
| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/v1/auth/google` | Google OAuth 로그인 페이지 이동 |
| GET | `/api/v1/auth/google/callback` | OAuth 콜백 (토큰 발급) |
| POST | `/api/v1/auth/refresh` | 토큰 갱신 |
| POST | `/api/v1/auth/logout` | 로그아웃 |
| GET | `/api/v1/members/me` | 내 정보 조회 |

#### F2. 매장 검색/조회
- 매장 등록 (매장명, 위치, 카테고리, 수용 팀 수, 영업시간)
- **PostGIS 기반 위치 검색**: 위도/경도 + 반경(기본 3km)으로 주변 매장 조회
  - 위치 허용 시: 사용자 실제 좌표 사용
  - 위치 거부 시: 강남구 중심 좌표(37.4979, 127.0276) 기본값
- 매장명 키워드 검색
- **커서 기반 무한스크롤 페이징**
- 인기 매장 목록 조회 (같은 구 내 예약 수 기준, Redis 캐싱 TTL 5분)
- 매장 상세 조회 (메뉴 포함)
- 매장 비활성화 (Soft Delete)

**API 엔드포인트:**
| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/v1/stores` | 매장 등록 |
| GET | `/api/v1/stores` | 매장 목록/검색 (위치 or 키워드) |
| GET | `/api/v1/stores/popular` | 인기 매장 목록 |
| GET | `/api/v1/stores/{storeId}` | 매장 상세 |
| PATCH | `/api/v1/stores/{storeId}` | 매장 수정 |
| DELETE | `/api/v1/stores/{storeId}` | 매장 비활성화 |
| GET | `/api/v1/si` | 시 목록 조회 |
| GET | `/api/v1/si/{siId}/gu` | 구 목록 조회 |

#### F3. 메뉴 관리
- 매장별 메뉴 CRUD (메뉴명, 이미지, 가격, 설명)

**API 엔드포인트:**
| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/v1/stores/{storeId}/menus` | 메뉴 등록 |
| GET | `/api/v1/stores/{storeId}/menus` | 메뉴 목록 |
| PATCH | `/api/v1/stores/{storeId}/menus/{menuId}` | 메뉴 수정 |
| DELETE | `/api/v1/stores/{storeId}/menus/{menuId}` | 메뉴 삭제 |

#### F4. 예약 (동시성 제어 핵심)
- **예약 가능 시간대 조회**: Redis Hash(`seat:{storeId}:{date}`) 기반 Cache-Aside 패턴
- **예약 생성**: Redis 분산 락(`lock:reservation:{storeId}:{date}:{time}`)으로 원자적 처리
  - 락 내 처리: ① 잔여 좌석 확인 → ② 좌석 차감 → ③ 예약 저장 (단일 @Transactional)
  - waitTime 3초, leaseTime 5초
  - 예약 확정 시 좌석 캐시 무효화 + Kafka 이벤트 발행
- **예약 취소**: 좌석 반환 + 빈자리 알림 이벤트 트리거
- **예약 변경**: 기존 취소 + 새 예약을 단일 트랜잭션 처리, `storeId:date:time` 사전순 오름차순으로 락 획득하여 데드락 방지
- **노쇼 자동 취소**: 예약 시간 +15분 경과 시 Spring Scheduler로 자동 취소 + noshow_count 증가 + 빈자리 이벤트 발행

**API 엔드포인트:**
| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/v1/stores/{storeId}/available-times` | 예약 가능 시간대 조회 |
| POST | `/api/v1/reservations` | 예약 생성 |
| GET | `/api/v1/reservations/me` | 내 예약 목록 |
| PATCH | `/api/v1/reservations/{reservationId}` | 예약 변경 |
| DELETE | `/api/v1/reservations/{reservationId}` | 예약 취소 |

#### F5. 빈자리 알림
- 예약 마감된 매장×시간대에 **알림 구독** 등록 (매장×시간대 중복 불가)
- 취소/노쇼 발생 시 **Kafka 이벤트 기반** 비동기 알림 발송
- **Redis 쿨다운** (SET NX + TTL 5분): 같은 매장×시간대에서 연속 취소 시 알림 중복 방지
- 구독자 전원에게 **일괄 알림** (순번 없이 동시 수신 → 선착순 경쟁)
- 예약 성공 시 해당 유저 구독 자동 해제
- 구독자 관리: Redis Set (`wait:store:{id}:date:time → {userId}`)

**API 엔드포인트:**
| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/v1/vacancies` | 빈자리 알림 구독 |
| GET | `/api/v1/vacancies/me` | 내 구독 목록 |
| PATCH | `/api/v1/vacancies/{vacancyId}` | 구독 수정 |
| DELETE | `/api/v1/vacancies/{vacancyId}` | 구독 취소 |

#### F6. 알림 서비스
- Kafka Consumer로 알림 발송 (예약 확정/변경/취소/리마인드/빈자리)
- **트랜잭션 분리**: 예약이 DB에 완전히 커밋된 이후 알림 발송 → 알림 실패가 메인 로직에 영향 없음

---

### 3.2 고도화 (선택, 7~10주)

#### F7. 즐겨찾기
- 폴더별 즐겨찾기 관리 (기본 폴더, 사용자 정의 폴더, Slack 폴더)
- 폴더 CRUD + 폴더 내 매장 추가/삭제

**API 엔드포인트:**
| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/v1/bookmark-folders` | 폴더 생성 |
| GET | `/api/v1/bookmark-folders` | 내 폴더 목록 |
| PATCH | `/api/v1/bookmark-folders/{folderId}` | 폴더 수정 |
| DELETE | `/api/v1/bookmark-folders/{folderId}` | 폴더 삭제 |
| POST | `/api/v1/bookmark-folders/{folderId}/bookmarks` | 즐겨찾기 추가 |
| DELETE | `/api/v1/bookmark-folders/{folderId}/bookmarks/{bookmarkId}` | 즐겨찾기 삭제 |

#### F8. 선착순 쿠폰
- 매일 아침 10시, 선착순 100명에게 할인 쿠폰 발급
- **Redis Lua 스크립트**: SISMEMBER(중복 체크) + GET(잔여 수량) + DECR(차감) + SADD(유저 기록)을 단일 원자적 실행
- **Kafka Write-Behind**: 발급 성공 즉시 응답 → Kafka 이벤트 발행 → PostgreSQL 비동기 저장
- DLQ(Dead Letter Queue)로 실패 시 재처리
- 쿠폰 사용: 낙관적 락 (JPA @Version)
- 예약 취소 시 쿠폰 반환

**API 엔드포인트:**
| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/v1/coupons/claim/{templateId}` | 선착순 쿠폰 발급 |
| GET | `/api/v1/coupons/me` | 내 쿠폰 목록 |
| POST | `/api/v1/coupons/{couponId}/use` | 쿠폰 사용 |

#### F9. Slack 연동 즐겨찾기
- Slack Events API로 채널 내 음식점 링크 감지
- 즉시 200 OK 응답 후 **Kafka로 비동기 처리** (3초 응답 제한 대응)
- **다단계 매장 매칭**: URL 내 외부 ID → OG 메타태그 매장명 → pgvector 유사도 검색
- 매칭 성공 시 **채널 내 연동된 모든 회원**의 Slack 폴더 즐겨찾기에 자동 추가
- 멱등키(event_id) 기반 중복 제거
- Slack 사용자 ↔ 플랫폼 회원 연결: 최초 1회 Slack OAuth 계정 연동

#### F10. AI 에이전트 (자연어 예약·추천)
- **Spring AI + Gemini 2.0 Flash** 기반 Function Calling
- 자연어 예약: "3월 22일 모수 용산점 18시 3명 예약해줘" → 의도 파악 → 매장 매칭 → 예약 실행
- 맛집 추천: "내 주변에 예약자 수 많은 음식점 추천해줘" → PostGIS + 예약 통계 기반
- **멀티턴 대화**: 부족한 정보 추가 질문 → 정보 완성 후 예약 진행
- 대화 상태: Redis Hash (`session:{userId}:{sessionId}`, TTL 30분, 최대 10턴)
- **pgvector + PostGIS 복합 매장 매칭**: "3km 이내 + 매장명 유사도순" 단일 SQL 쿼리
- 임베딩: Gemini text-embedding-004 (768차원)
- 장애 대응: CircuitBreaker (5초 타임아웃), Rate Limiting (분당 20회)

---

## 4. 비기능 요구사항

### 4.1 성능 목표

| 항목 | 목표 |
|---|---|
| 예약 API 평균 응답시간 | 200ms 이하 |
| 예약 API P99 응답시간 | 500ms 이하 |
| 검색 API 평균 응답시간 | 100ms 이하 (캐시 히트 시) |
| 초과 예약 건수 | **0건** (정합성 보장) |
| 웨이팅 순번 중복 | **0건** |
| 쿠폰 초과 발급 | **0건** |
| 에러율 | 1% 미만 |

### 4.2 부하 테스트 시나리오 (k6)

| 시나리오 | 조건 | 핵심 검증 |
|---|---|---|
| 크리스마스 이브 예약 오픈 | 인기 매장 10곳, 18:00~21:00, 매장당 20석, 동시 500명 | 초과 예약 0건, TPS, 평균/P99 응답시간 |
| 빈자리 알림 후 재예약 경쟁 | 빈자리 발생 → 구독자 100명 동시 예약 시도 | 분산 락 재활용 정합성, 1명만 성공 |
| 선착순 쿠폰 발급 | 쿠폰 100개, 동시 1000명 요청 | 정확히 100개만 발급, 중복 0건 |
| 매장 검색 폭주 | "크리스마스 레스토랑" 키워드, 동시 1000명 | 캐시 히트율, 응답시간 |
| 복합 시나리오 | 검색 70% + 예약 20% + 빈자리 알림 10%, 동시 500명 | 전체 TPS, P99 응답시간, 에러율 |

### 4.3 테스트 비교 측정
1. **1차**: 분산 락 미적용 → 초과 예약 발생 확인
2. **2차**: Redis 분산 락 적용 → 초과 예약 0건 확인
3. **3차**: 캐시 적용 → 응답시간 개선 수치 측정

---

## 5. 기술 아키텍처

### 5.1 기술 스택

| 분류 | 기술 | 선정 이유 |
|---|---|---|
| 백엔드 | Java + Spring Boot | 채용 시장 필수 스택, JPA/Security 생태계 |
| DB | **PostgreSQL** | PostGIS(위치 검색) + pgvector(벡터 유사도 검색)를 하나의 DB에서 처리 |
| 캐시 | Redis | 분산 락, 캐시, 빈자리 알림 구독자 관리(Set), 쿠폰 수량 관리(Atomic Counter) |
| 메시지 큐 | Kafka | 예약 확정→알림, 취소/노쇼→빈자리 알림, Slack 이벤트, 쿠폰 이력 비동기 저장 |
| AI | Spring AI + Gemini 2.0 Flash | Function Calling 네이티브 통합, 속도/비용 효율 |
| 임베딩 | Gemini text-embedding-004 | 768차원, 매장명 유사도 검색용 |
| 벡터 검색 | pgvector | PostgreSQL 내에서 벡터 유사도 검색, 별도 Vector DB 불필요 |
| 공간 검색 | PostGIS | ST_DWithin, ST_Distance, GIST 인덱스 |
| 인프라 | Docker + AWS EC2 | 환경 통일, 실무 동일 구성 |
| CI/CD | GitHub Actions | PR 기반 자동 빌드/배포 |
| 부하 테스트 | k6 | 기념일 동시 예약 시나리오 검증 |
| Slack 연동 | Slack Bolt (Java SDK) | Slack 이벤트 구독 공식 SDK |
| 모니터링 | Grafana + Prometheus | TPS, 응답시간, 에러율 대시보드 (선택) |

### 5.2 PostgreSQL 선택 이유 (vs MySQL)

| 비교 항목 | MySQL + Redis Stack | PostgreSQL (선택) |
|---|---|---|
| 위치 검색 | Spatial Index (제한적) | PostGIS — 산업 표준 GIS |
| 벡터 검색 | Redis Stack 별도 운영 필요 | pgvector — 동일 DB에서 처리 |
| 복합 쿼리 | 애플리케이션 레벨 조합 필요 | **단일 SQL**로 "3km 이내 + 매장명 유사도순" 처리 |
| 운영 복잡도 | 2개 시스템 (MySQL + Redis Stack) | PostgreSQL 하나로 통합 |

### 5.3 시스템 구성도

```
클라이언트 (API / 채팅창)
  │
  ├─ 매장 검색 ──→ [매장 서비스] ──→ PostgreSQL (PostGIS) + Redis (캐시)
  │
  ├─ 예약 요청 ──→ [예약 서비스] ──→ Redis (분산 락) → PostgreSQL (예약 저장)
  │                                    │
  │                                    └─→ Kafka ──→ [알림 서비스] → 이메일/푸시
  │
  ├─ 예약 취소/노쇼 ──→ [예약 서비스] ──→ 좌석 반환
  │                                         │
  │                                         └─→ Kafka (빈자리 이벤트)
  │                                                │
  │                                                ▼
  │                                         [빈자리 알림 서비스]
  │                                           ├─ Redis (구독자 Set 조회)
  │                                           ├─ Redis (쿨다운 체크)
  │                                           └─→ [알림 서비스] → 구독자 일괄 푸시
  │
  ├─ 빈자리 알림 구독 ──→ [빈자리 알림 서비스] ──→ Redis Set
  │
  ├─ 즐겨찾기 ──→ [즐겨찾기 서비스] ──→ PostgreSQL
  │
  ├─ AI 채팅 ──→ [AI 에이전트 서비스]
  │                    ├─ LLM (Gemini, Function Calling)
  │                    ├─ [매장 매칭 서비스] → PostgreSQL (pgvector + PostGIS)
  │                    └─ [예약 서비스] (실제 예약 API 호출)
  │
  └─ 선착순 쿠폰 ──→ [쿠폰 서비스] ──→ Redis (Lua 원자 처리)
                                          └─→ Kafka → PostgreSQL (비동기 저장)

Slack 채널
  └─ 음식점 링크 공유 ──→ Slack Events API ──→ [Slack 연동 서비스]
                                                  ├─ [매장 매칭 서비스] (pgvector)
                                                  └─ [즐겨찾기 서비스] (Slack 폴더 추가)
```

### 5.4 Redis 활용 맵

| 용도 | 데이터 구조 | 키 패턴 | 설명 |
|---|---|---|---|
| 좌석 캐시 | Hash | `seat:{storeId}:{date}` | 시간대별 잔여 좌석, TTL 5분, Cache-Aside |
| 분산 락 | Redisson Lock | `lock:reservation:{storeId}:{date}:{time}` | waitTime 3초, leaseTime 5초 |
| 빈자리 구독자 | Set | `wait:store:{id}:date:time` | 구독자 userId 집합 |
| 알림 쿨다운 | String (SET NX) | `cooldown:store:{id}:date:time` | TTL 5분, 연속 취소 알림 방지 |
| 인기 매장 캐시 | String | `popular:gu:{guId}` | TTL 5분 |
| Slack 멱등키 | String (SET NX) | `slack:event:{eventId}` | 중복 이벤트 방지 |
| 쿠폰 수량 | Counter + Set | `coupon:remain:{templateId}`, `coupon:issued:{templateId}` | Lua 원자 처리 |
| AI 대화 상태 | Hash | `session:{userId}:{sessionId}` | TTL 30분, 최대 10턴 |
| 임베딩 캐시 | String | `embed:store:{normalized_name}` | TTL 1시간 |

---

## 6. 데이터 모델 (ERD)

### 6.1 테이블 목록

| 테이블 | 설명 | 비고 |
|---|---|---|
| Users | 회원 | Google OAuth, noshow_count |
| si | 시 | 지역 상위 |
| gu | 구 | 지역 하위, FK → si |
| Stores | 매장 | PostGIS Point, pgvector(768), FK → gu |
| menu | 메뉴 | FK → Stores |
| Store-Remain | 예약 가능 시간대 (잔여 좌석) | FK → Stores |
| Reservations | 예약 | FK → Users, Store-Remain |
| Vacancies | 빈자리 알림 구독 | FK → Users, Store-Remain |
| Bookmark-Folders | 즐겨찾기 폴더 | FK → Users, folder_type (DEFAULT/SLACK/CUSTOM) |
| Bookmarks | 즐겨찾기 | FK → Bookmark-Folders, Stores |
| Coupons_templates | 쿠폰 템플릿 | 쿠폰명, 할인율, 수량, 유효기간 |
| Coupons | 쿠폰 발급 이력 | FK → Users, Coupons_templates |
| Chatbots | AI 대화 세션 | FK → Users, JSONB context |
| Review | 리뷰 | FK → Users, Stores (후순위) |
| Slack 회원 매핑 | Slack User ID ↔ 회원 ID | 계정 연동용 |
| 알림 발송 이력 | 알림 타입, 수신자, 발송 결과 | 이력 관리 |

### 6.2 핵심 관계
- `Reservations` → `Store-Remain` (예약은 특정 매장의 특정 날짜×시간대에 대해 생성)
- `Vacancies` → `Store-Remain` (빈자리 알림도 동일 구조)
- `Bookmarks` → `Bookmark-Folders` → `Users` (사용자별 폴더별 즐겨찾기)
- `Coupons` → `Coupons_templates` (쿠폰 발급 이력은 템플릿 참조)

---

## 7. 도메인 및 서비스 구조

### 7.1 도메인 (독립적 비즈니스 영역)

| 도메인 | 핵심 책임 |
|---|---|
| 회원 | 회원가입/로그인, OAuth, JWT |
| 매장 | 매장 CRUD, PostGIS 위치 기반 검색, 커서 페이징 |
| 예약 | 예약 CRUD, Redis 분산 락 동시성 제어, 노쇼 자동 취소 |
| 즐겨찾기 | 폴더별 관리, Slack 연동 자동 추가 |
| 쿠폰 | 선착순 발급(Redis Lua), 사용(낙관적 락), 취소 시 반환 |

### 7.2 서비스 (도메인 조합 / 외부 연동)

| 서비스 | 핵심 책임 |
|---|---|
| 알림 서비스 | Kafka Consumer, 예약 확정/변경/취소/리마인드/빈자리 알림 발송 |
| 빈자리 알림 서비스 | 구독 관리(Redis Set), 쿨다운 체크, 구독자 일괄 알림 |
| Slack 연동 서비스 | Slack 이벤트 수신, 매장 매칭, 즐겨찾기 위임 |
| AI 에이전트 서비스 | 자연어 의도 파악, Function Calling, 멀티턴 대화 관리 |
| 매장 매칭 서비스 | pgvector 유사도 검색 (Slack + AI 공통) |

---

## 8. 동시성 시나리오 비교

| 구분 | 기념일 예약 폭주 | 선착순 쿠폰 발급 |
|---|---|---|
| 핵심 문제 | 특정 자원(매장×시간대) 점유 경쟁 | 글로벌 수량(100개) 차감 경쟁 |
| 경합 지점 | 매장×시간대별로 분산 (수백 개 락) | 단일 카운터에 집중 (하나의 키) |
| Redis 패턴 | 분산 락 (Redisson Lock) | 원자적 연산 (Lua DECR) |
| 락 필요 여부 | 필요 — 복합 연산(조회→차감→저장) | 불필요 — DECR 자체가 원자적 |
| DB 저장 방식 | 동기 (Write-Through) | 비동기 (Write-Behind via Kafka) |

---

## 9. 개발 일정

| 주차 | 백엔드 | 인프라/기타 |
|---|---|---|
| 1주 | ERD 설계, API 명세, 도메인 모델링 | Docker 환경 세팅, GitHub 레포 구성 |
| 2주 | 회원/인증 개발 (JWT, Google OAuth) | GitHub Actions CI 구성 |
| 3주 | 매장 CRUD, PostGIS 위치 기반 검색 | AWS EC2 배포 환경 구성 |
| 4주 | **예약 기능 + 동시성 처리 (Redis 분산 락)** | 스테이징 서버 구성 |
| 5주 | **빈자리 알림 (Kafka + Redis Set + 쿨다운)** | 모니터링 세팅 (Prometheus + Grafana) |
| 6주 | 알림 발송 (Kafka + 이메일), 캐시 적용 | 로그 수집 환경 |
| 7주 | 즐겨찾기 (폴더 관리), 선착순 쿠폰 (Redis Lua + Kafka) | 부하 테스트 환경 구성 |
| 8주 | Slack 연동 (이벤트 수신, 매장 매칭, Slack 폴더) | Slack App 등록 및 테스트 |
| 9주 | AI 에이전트 (Spring AI + Gemini + pgvector) | k6 부하 테스트 수행 + 성능 개선 |
| 10주 | 예외 처리, 버그 수정, 통합 테스트, 문서화 | 프로덕션 배포, 최종 QA |

---

## 10. 리스크 및 대비책

| 리스크 | 대비책 |
|---|---|
| 기념일 동시 예약으로 좌석 초과 | Redis 분산 락(Redisson)으로 원자적 좌석 차감, 초과 시 즉시 반환 |
| 빈자리 알림 후 재예약 경쟁 | 기존 분산 락 100% 재활용, 별도 메커니즘 불필요 |
| 선착순 쿠폰 초과/중복 발급 | Redis Lua 원자 처리 + Kafka Write-Behind + DLQ 재처리 |
| Slack 3초 응답 제한 | 즉시 200 OK 응답 후 Kafka 비동기 처리 |
| AI 매장 매칭 실패 | 다단계 매칭(정확→유사도), 사용자 확인 프롬프트, CircuitBreaker |
| LLM 장애/지연 | CircuitBreaker (5초), Rate Limiting (분당 20회), Fallback 응답 |
| 알림 스팸 | Redis 쿨다운 (SET NX + TTL 5분) |
| 일정 지연 | 6주까지 MVP(예약/빈자리 알림) 우선, Slack/AI/쿠폰은 후순위 |
| 팀원 간 코드 충돌 | API 명세 선확정 후 도메인별 병렬 개발, PR 리뷰 필수 |

---

## 11. 성공 지표

| 지표 | 목표 | 측정 방법 |
|---|---|---|
| 초과 예약 0건 | 분산 락 적용 후 동시 500명 테스트에서 0건 | k6 부하 테스트 |
| 쿠폰 정확 발급 | 100개 한정 쿠폰, 1000명 동시 요청 시 정확히 100개만 발급 | k6 부하 테스트 |
| 예약 API P99 < 500ms | 복합 시나리오(500명) 하에서 유지 | k6 + Grafana |
| 검색 캐시 히트율 > 80% | 반복 검색 시 Redis 캐시 활용 | Prometheus 메트릭 |
| 부하 테스트 전/후 비교 수치 | 분산 락 적용 전/후, 캐시 적용 전/후 개선 수치 문서화 | k6 리포트 |

---

## 12. 면접 어필 포인트

| 예상 질문 | 답변 방향 |
|---|---|
| "같은 시간에 두 명이 동시에 예약하면?" | Redis 분산 락으로 매장×날짜×시간대별 원자적 처리. 비관적 락 대비 DB 커넥션 비점유 이점 설명 |
| "크리스마스에 트래픽이 10배 몰리면?" | 캐시로 읽기 부하 분산, 분산 락으로 쓰기 정합성. k6 테스트 수치 기반 답변 |
| "빈자리가 생기면 어떻게 알려주나?" | Kafka 이벤트 → 구독자 일괄 알림 → 선착순 재예약(기존 분산 락 재활용). 자동 예약 대신 직접 예약 방식 채택 이유 |
| "선착순 쿠폰 동시에 1000명이 요청하면?" | Redis Lua 원자 처리 vs 예약 분산 락 — 경합 구조 차이 설명 (분산 락 vs 단일 카운터) |
| "캐시와 DB 데이터 불일치는?" | Cache-Aside + TTL 5분. 예약 확정/취소 시 DEL로 즉시 무효화. TTL은 안전장치 |
| "AI가 매장 이름을 잘못 매칭하면?" | pgvector + PostGIS 복합 쿼리로 3km 이내 유사도순 검색, 사용자 확인 후 진행 |
| "Kafka를 왜 선택했나?" | 예약→알림이 동기적일 필요 없음. at-least-once 보장. 쿠폰 Write-Behind 패턴 |
| "PostgreSQL을 왜 선택했나?" | PostGIS + pgvector 복합 쿼리를 단일 SQL로 처리. MySQL+Redis Stack 대비 운영 단순화 |
