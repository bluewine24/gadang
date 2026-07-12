# 가당 — 알고리즘 개발 문서

> A·B 공동 개발 문서. 설계 변경·개발 진행·일정은 이 문서에서 관리한다.

---

## 1. 담당 기능

| ID | 기능 | 상태 |
|----|------|------|
| F111 | 당일치기 가능 지역 추천 | ✅ 완료 |
| F112 | 지역별 당일치기 정보 표시 (교통수단·요금·소요시간·하루 운행 횟수) | ✅ 완료 |
| F113 | 지역 선택·탭 연동 (지역 허브 기준 장소/코스 진입) | ✅ 완료 |
| F120 | 코스 조건 입력 (기본 → 확장) | 🔲 미시작 |
| F121 | 비용 초과 알림 + 무료 장소 우선 | 🔲 미시작 |
| F122 | 시간 기반 방문 가능 판단 | 🔲 미시작 |
| F123 | ODsay 교통 경로·요금 (도시내/도시간) | ✅ 완료 |
| F124 | 총 예상 비용 계산 | ✅ 완료 (지역 카드 요금 표시) |
| F125 | 코스 자동 추천 (기본 → 고도화 → 3종) | 🔲 미시작 |
| F126 | 여행 일정 저장 | 🔲 미시작 |
| F127 | 일정 수정·재계산 | 🔲 미시작 |
| F128 | 대체 장소 추천 | 🔲 미시작 |
| F132 | 공유 코스 가져오기 + 재계산 | 🔲 미시작 |
| F139 | DataLab 배치 수집 + 트렌드 반영 | ✅ 완료 (네이버 DataLab 트렌드 점수 반영) |
| NF-C | API 결과 캐싱 (`@Cacheable` + Caffeine) | ✅ 완료 |
| NF-P | 병렬 API 호출 (`CompletableFuture`) | ✅ 완료 (이미지 병렬 로딩) |
| NF-DB | DB 인덱스 설계 | 🔲 미시작 |

---

## 2. 알고리즘 설계

### 2.1 공통 처리 흐름

```
입력 파싱
  → 후보 수집 (Kakao Local API)
  → 후보 필터링 및 스코어링
  → 동선 최적화 (Greedy)
  → 식사·카페 슬롯 삽입
  → 교통 경로 조회 (ODsay)
  → 시간 제약 검사
  → 타임라인 생성
  → 비용 계산
  → 결과 출력
```

| 코스 조건 입력 | 생성 결과 타임라인 |
|---|---|
| ![코스 조건 입력 화면](../images/screen_course.png) | ![코스 생성 결과 화면](../images/screen_itinerary.png) |

### 2.2 단계별 구현 범위

| 구현 항목 | Phase 1 | Phase 2 | Phase 3 |
| --------- | ------- | ------- | ------- |
| 입력 | 출발지·시간·활동 유형·식사 | + 이동 범위·고정 장소·트렌드·소프트 가이드 | 동일 |
| 검색 반경 | 5km 고정 | 이동 범위 따라 동적 (5·15·50km) | 동일 |
| 후보 필터 | 없음 | FRANCHISE_BLACKLIST 제거 | 동일 |
| 후보 정렬 | 거리 오름차순 | 트렌드·거리 가중치 스코어링 | 동일 |
| Top-K 선정 | 카테고리별 거리 상위 3개 | 스코어 상위 K개 | 동일 |
| 동선 최적화 | Greedy nearest-neighbor | 고정 장소 anchor + 구간별 Greedy | 동일 |
| 교통 조회 | ODsay 도시 내 + fallback | + ODsay 도시간 (KTX·고속버스·항공) | 동일 |
| API 처리 | 순차 호출 | 병렬 호출 + 캐싱 | 동일 |
| 여유 시간 | 없음 | 90분 이상 시 장소 자동 추가 | 동일 |
| 비용 알림 | 없음 | 소프트 가이드 초과 시 경고 | 동일 |
| 출력 | 코스 1개 | 코스 1개 | 코스 3개 (가중치 다르게 3회 실행) |

### 2.3 스코어링 공식

```
관광명소·문화시설 (AT4, CT1)
  score = 거리점수 × 0.4 + 트렌드점수 × 0.6
  거리점수  = 1 - (distance / radius)
  트렌드점수 = PLACE_TREND.trend_score / 100

카페·음식점 (CE7, FD6)
  score = 거리점수 × 0.5 + accuracy × 0.5
  accuracy = Kakao 검색 정확도 순위 기반 0~1
```

### 2.4 Phase 3 — 코스 3종 제시

```
동일 후보 풀에서 가중치만 바꿔 3회 실행 (추가 API 호출 없음)

코스 A "요즘 뜨는 코스" : 트렌드 70% + 거리 30%
코스 B "추천 코스"       : 트렌드 50% + 거리 50%  ← 기본 선택
코스 C "동선 효율 코스" : 트렌드 30% + 거리 70%
```

---

## 3. 일정

| 날짜 | 목표 | 담당 | 상태 |
|------|------|------|------|
| 2026-05-18 ~ | Phase 1 MVP — 지역 추천·교통 계산 백엔드 | 공동 | ✅ 완료 |
| 2026-06 ~ | Phase 2 — 이미지·트렌드·운행횟수·UI 개선 | 공동 | 🔄 진행중 |

> 일정 추가 방법: 위 표에 행 추가. 완료 시 상태를 ✅로 변경.

---

## 4. 개발 일지

> 작업할 때마다 아래 형식으로 추가. 설계 변경·삽질·해결 방법 모두 기록.

---

### 2026-06-09 — Odsay 버스/열차 운행횟수 + 이미지 슬라이더 + UI 개선

**작업자**: 공동

**작업 내용**
- `OdsayBusService` 전면 재작성: 잘못된 엔드포인트 → 올바른 엔드포인트로 교체
  - `/intercityBusTerminals` + `/expressBusTerminals` 터미널 검색
  - `/searchInterBusSchedule` 시간표 조회 → `count`(하루 편수) / `wasteTime`(분) / `fare`(원)
  - `BusRouteResult(travelMin, fare, dailyTrips)` record 도입
- `OdsayTrainService` 신규 작성
  - `/searchStation?stationClass=3` → 역 ID 조회 (캐시: `trainStation`)
  - `/trainServiceTime` → `station[]` 배열 길이 = 하루 운행 편수 (캐시: `trainServiceTime`)
- `BusFrequencyTable` 신규: Odsay 접근 불가 시 하드코딩 fallback (50+ 노선 양방향)
- `TransportOption`에 `dailyTrips` 필드 추가 (-1 = 미제공, 0+ = 실제 편수)
- `RegionSearchService`
  - KTX·ITX·무궁화 옵션에 `odsayTrainService.getDailyTrips()` 연결
  - TourAPI 이미지 로딩 `CompletableFuture.allOf()` 병렬화
- `HomeView.vue`
  - 이미지 슬라이더: 개별 setInterval → `watch(regions)` + 단일 타이머로 교체 (Vue 반응성 수정)
  - 이미지 높이 150px → 188px
  - 하루 운행 횟수 표시: `편도 XX분 · XX원 · 하루 N회`
  - 장소 둘러보기/코스 짜기 버튼: 출발 허브(`toHub`) 기준으로 진입
- `AppMasthead.vue`: 헤더 검색바 제거
- `gadang.css`: 로그인 정보 오른쪽 정렬 (`margin-left: auto`)
- `application.properties`: `trainStation`, `trainServiceTime` 캐시 이름 추가

**결정 사항**
- `application.properties`에 실 API 키 직접 기입 (application-local.properties 병행 유지)
- 버스 Odsay 실패 시 `BusFrequencyTable` 정적 fallback → `-1` 대신 실측값 제공
- 무궁화 하루 편수: trainServiceTime 값의 2/3 추정 (KTX 포함 전체 편수 기준)

**다음 할 일**
- Odsay trainServiceTime API 정상 응답 여부 확인 (역명 매칭 이슈 가능성)
- 코스 추천 로직 (F125) 착수
- 지도 페이지에서 허브 기준 장소 탐색 연동 완성

---

## 5. 인터페이스 합의

> A·B 간 공유가 필요한 DTO·서비스 메서드 시그니처를 여기에 기록.

### PlaceDto

```java
// 합의 필요 — 작성 전
```

### CourseRecommendationService

```java
// 합의 필요 — 작성 전
```
