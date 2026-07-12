"""
가당 코스 추천 알고리즘 검증 스크립트
실제 API 없이 mock 데이터로 알고리즘 로직 테스트
"""

import math
from dataclasses import dataclass, field
from typing import Optional
from datetime import datetime, timedelta
from enum import Enum

# ──────────────────────────────────────────
# 상수
# ──────────────────────────────────────────
WALK_SPEED_MPS = 1.1          # 도보 속도 m/s (약 4km/h)
BUS_SPEED_MPS  = 7.0          # 버스 평균 속도 m/s (약 25km/h)
BUS_WAIT_MIN   = 5            # 버스 평균 대기 시간
WALK_THRESHOLD = 700          # 700m 이하는 도보

DEFAULT_STAY = {
    "AT4": 90,   # 관광명소
    "CT1": 60,   # 문화시설
    "CE7": 45,   # 카페
    "FD6": 60,   # 식사
}

MOBILITY_RADIUS = {
    "LOW":  5_000,
    "MID":  15_000,
    "HIGH": 50_000,
}

TREND_THRESHOLD = {
    "AT4": {"LOW": 20, "MID": 40, "HIGH": 60},
    "CT1": {"LOW": 10, "MID": 15, "HIGH": 20},  # 문화시설은 검색량 자체가 낮음
    "CE7": {"LOW":  0, "MID":  0, "HIGH":  0},  # 카페는 트렌드 임계값 없음
    "FD6": {"LOW":  0, "MID":  0, "HIGH":  0},  # 식당도 없음
}

FRANCHISE_BLACKLIST = {
    "파리바게뜨", "뚜레쥬르", "이디야", "스타벅스", "투썸플레이스",
    "메가커피", "맥도날드", "버거킹", "롯데리아", "서브웨이",
    "빽다방", "컴포즈", "할리스", "크리스피크림", "던킨", "베스킨라빈스",
    "CU", "GS25", "세븐일레븐",
}

# ──────────────────────────────────────────
# 데이터 클래스
# ──────────────────────────────────────────
@dataclass
class Place:
    id: str
    name: str
    category_code: str
    lat: float
    lng: float
    trend_score: float = 0.0      # DataLab 점수 (0~100)
    kakao_accuracy: float = 0.0   # Kakao accuracy 순위 기반 (0~1)
    admission_fee: int = 0
    fee_confirmed: bool = False
    stay_minutes: int = 0          # 0이면 category 기본값 사용

    def effective_stay(self) -> int:
        return self.stay_minutes or DEFAULT_STAY.get(self.category_code, 60)

@dataclass
class TransitInfo:
    from_place: str
    to_place: str
    duration_min: int
    fare: int
    mode: str  # WALK / BUS / KTX

@dataclass
class TimelineSlot:
    place: Place
    arrive_at: datetime
    depart_at: datetime
    transit_before: Optional[TransitInfo] = None

# ──────────────────────────────────────────
# 유틸
# ──────────────────────────────────────────
def haversine(lat1, lng1, lat2, lng2) -> int:
    R = 6_371_000
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = math.sin(dphi/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlng/2)**2
    return int(R * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a)))

def is_franchise(name: str) -> bool:
    return any(brand in name for brand in FRANCHISE_BLACKLIST)

def estimate_transit(dist_m: int) -> TransitInfo:
    if dist_m <= WALK_THRESHOLD:
        duration = int(dist_m / WALK_SPEED_MPS / 60)
        return TransitInfo("", "", duration, 0, "WALK")
    else:
        duration = int(dist_m / BUS_SPEED_MPS / 60) + BUS_WAIT_MIN
        return TransitInfo("", "", duration, 1_250, "BUS")

# ──────────────────────────────────────────
# 채점 알고리즘
# ──────────────────────────────────────────
def score_place(place: Place, dist_m: int, radius_m: int, mobility: str) -> float:
    if dist_m > radius_m:
        return -1.0

    threshold = TREND_THRESHOLD.get(place.category_code, {}).get(mobility, 0)
    if place.trend_score < threshold:
        return -1.0

    dist_score = max(0, 1.0 - dist_m / radius_m)

    if place.category_code in ("AT4", "CT1"):
        return dist_score * 0.4 + (place.trend_score / 100) * 0.6
    else:
        return dist_score * 0.5 + place.kakao_accuracy * 0.5

# ──────────────────────────────────────────
# Top-K 선택
# ──────────────────────────────────────────
def top_k(places: list[Place], start_lat: float, start_lng: float,
          radius_m: int, mobility: str, k: int = 3) -> list[tuple[Place, float]]:
    scored = []
    for p in places:
        if is_franchise(p.name):
            continue
        dist = haversine(start_lat, start_lng, p.lat, p.lng)
        s = score_place(p, dist, radius_m, mobility)
        if s >= 0:
            scored.append((p, s, dist))

    scored.sort(key=lambda x: x[1], reverse=True)
    return [(p, s) for p, s, _ in scored[:k]]

# ──────────────────────────────────────────
# 시간 체크 & 타임라인 생성
# ──────────────────────────────────────────
def build_timeline(
    route: list[Place],
    start_lat: float, start_lng: float,
    departure: datetime, return_time: datetime
) -> tuple[list[TimelineSlot], list[str]]:

    warnings = []
    timeline = []
    current_time = departure
    current_lat, current_lng = start_lat, start_lng

    for place in route:
        dist = haversine(current_lat, current_lng, place.lat, place.lng)
        transit = estimate_transit(dist)
        transit.from_place = "prev"
        transit.to_place = place.name

        arrive = current_time + timedelta(minutes=transit.duration_min)
        depart = arrive + timedelta(minutes=place.effective_stay())

        if depart > return_time:
            warnings.append(f"⚠️  '{place.name}' 포함 시 귀가 시간 초과 → 제외")
            continue

        timeline.append(TimelineSlot(
            place=place,
            arrive_at=arrive,
            depart_at=depart,
            transit_before=transit
        ))
        current_time = depart
        current_lat, current_lng = place.lat, place.lng

    return timeline, warnings

# ──────────────────────────────────────────
# Greedy nearest-neighbor 경로 최적화
# ──────────────────────────────────────────
def optimize_route(places: list[Place], start_lat: float, start_lng: float) -> list[Place]:
    """가장 가까운 장소를 우선으로 경로 재정렬 (식당/카페는 식사 시간대에 맞게 고정)"""
    remaining = list(places)
    route = []
    cur_lat, cur_lng = start_lat, start_lng

    while remaining:
        nearest = min(remaining, key=lambda p: haversine(cur_lat, cur_lng, p.lat, p.lng))
        route.append(nearest)
        remaining.remove(nearest)
        cur_lat, cur_lng = nearest.lat, nearest.lng

    return route

# ──────────────────────────────────────────
# 비용 계산
# ──────────────────────────────────────────
def calculate_cost(timeline: list[TimelineSlot]) -> dict:
    transport = sum(s.transit_before.fare for s in timeline if s.transit_before)
    admission = sum(
        s.place.admission_fee for s in timeline
        if s.place.category_code not in ("FD6", "CE7")
    )
    meal_est = sum(
        s.place.admission_fee for s in timeline
        if s.place.category_code == "FD6"
    )
    cafe_est = sum(
        s.place.admission_fee for s in timeline
        if s.place.category_code == "CE7"
    )
    return {
        "transport": transport,
        "admission": admission,
        "subtotal":  transport + admission,
        "meal_est":  meal_est,
        "cafe_est":  cafe_est,
        "total_est": transport + admission + meal_est + cafe_est,
    }

# ──────────────────────────────────────────
# Mock 데이터 (순천 시나리오)
# ──────────────────────────────────────────
START_LAT, START_LNG = 34.9506, 127.4701

MOCK_AT4 = [
    Place("at1", "순천만국가정원",    "AT4", 34.9342, 127.4808, trend_score=72.4, kakao_accuracy=0.95, admission_fee=8000, fee_confirmed=True),
    Place("at2", "순천만습지",        "AT4", 34.9082, 127.5012, trend_score=48.1, kakao_accuracy=0.88, admission_fee=0,    fee_confirmed=True),
    Place("at3", "순천 드라마촬영장", "AT4", 34.9701, 127.5231, trend_score=55.3, kakao_accuracy=0.82, admission_fee=3000, fee_confirmed=True),
    Place("at4", "낙안읍성",          "AT4", 34.8901, 127.3912, trend_score=61.2, kakao_accuracy=0.90, admission_fee=4000, fee_confirmed=True),
]

MOCK_CT1 = [
    Place("ct1", "순천시립박물관",      "CT1", 34.9501, 127.4756, trend_score=31.0, kakao_accuracy=0.70, admission_fee=0, fee_confirmed=True),
    Place("ct2", "순천문화예술회관",    "CT1", 34.9489, 127.4812, trend_score=18.0, kakao_accuracy=0.55, admission_fee=0, fee_confirmed=True),
]

MOCK_CE7 = [
    Place("ce1", "파리바게뜨 순천점",   "CE7", 34.9510, 127.4720, trend_score=0,    kakao_accuracy=0.90),  # 프랜차이즈
    Place("ce2", "오월의 종 베이커리",  "CE7", 34.9480, 127.4650, trend_score=22.0, kakao_accuracy=0.82, admission_fee=7000),
    Place("ce3", "브레드박스 순천",     "CE7", 34.9420, 127.4810, trend_score=18.0, kakao_accuracy=0.75, admission_fee=7000),
    Place("ce4", "뚜레쥬르 순천신대점","CE7", 34.9380, 127.4900, trend_score=0,    kakao_accuracy=0.70, admission_fee=7000),  # 프랜차이즈
    Place("ce5", "로컬베이커리 A",      "CE7", 34.9350, 127.4750, trend_score=15.0, kakao_accuracy=0.68, admission_fee=7000),
]

MOCK_FD6_LUNCH = [
    Place("fd1", "맥도날드 순천점",   "FD6", 34.9512, 127.4715, trend_score=0,    kakao_accuracy=0.95, admission_fee=10000),  # 프랜차이즈, fee=식비
    Place("fd2", "꼬막비빔밥 순천집", "FD6", 34.9498, 127.4698, trend_score=35.0, kakao_accuracy=0.88, admission_fee=12000),
    Place("fd3", "남도한정식",        "FD6", 34.9505, 127.4712, trend_score=28.0, kakao_accuracy=0.80, admission_fee=15000),
    Place("fd4", "순천 장어구이",     "FD6", 34.9430, 127.4780, trend_score=42.0, kakao_accuracy=0.77, admission_fee=18000),
]

MOCK_FD6_DINNER = [
    Place("fd5", "롯데리아 순천점",   "FD6", 34.9360, 127.4820, trend_score=0,    kakao_accuracy=0.91, admission_fee=8000),   # 프랜차이즈
    Place("fd6", "순천만 꼬막탕",     "FD6", 34.9340, 127.4830, trend_score=38.0, kakao_accuracy=0.85, admission_fee=15000),
    Place("fd7", "남도밥상 순천",     "FD6", 34.9360, 127.4750, trend_score=31.0, kakao_accuracy=0.79, admission_fee=13000),
]

# ──────────────────────────────────────────
# 메인 실행
# ──────────────────────────────────────────
def run(mobility="MID"):
    print(f"\n{'='*55}")
    print(f" 가당 코스 추천 알고리즘 테스트 (이동범위: {mobility})")
    print(f"{'='*55}")

    radius = MOBILITY_RADIUS[mobility]
    departure   = datetime(2026, 6, 1, 10, 0)
    return_time = datetime(2026, 6, 1, 21, 0)

    # ── 1. Top-K 선택 ──────────────────────
    print("\n[1단계] Top-K 후보 선정")

    top_at4 = top_k(MOCK_AT4, START_LAT, START_LNG, radius, mobility, k=3)
    top_ct1 = top_k(MOCK_CT1, START_LAT, START_LNG, radius, mobility, k=2)
    top_ce7 = top_k(MOCK_CE7, START_LAT, START_LNG, radius, mobility, k=3)
    top_lunch  = top_k(MOCK_FD6_LUNCH,  START_LAT, START_LNG, radius, mobility, k=2)
    top_dinner = top_k(MOCK_FD6_DINNER, START_LAT, START_LNG, radius, mobility, k=2)

    def show(label, results):
        print(f"\n  {label}")
        if not results:
            print("    ❌ 결과 없음")
        for p, s in results:
            dist = haversine(START_LAT, START_LNG, p.lat, p.lng)
            print(f"    {'✅' if not is_franchise(p.name) else '❌'} {p.name:20s}  점수:{s:.2f}  거리:{dist}m  트렌드:{p.trend_score}")

    show("관광명소 (AT4)", top_at4)
    show("문화시설 (CT1)", top_ct1)
    show("베이커리 카페 (CE7)", top_ce7)
    show("점심 (FD6)", top_lunch)
    show("저녁 (FD6)", top_dinner)

    # ── 2. 경로 구성 + Greedy 최적화 ─────
    print("\n[2단계] 경로 구성 (Greedy nearest-neighbor)")

    # 관광/문화 슬롯
    spots = []
    if top_ct1: spots.append(top_ct1[0][0])
    if top_at4: spots.append(top_at4[0][0])
    if len(top_at4) > 1: spots.append(top_at4[1][0])

    # 동선 최적화
    spots = optimize_route(spots, START_LAT, START_LNG)

    # 식사·카페를 시간대에 맞게 삽입
    # 점심: 3번째 슬롯 직전, 저녁: 마지막 전, 카페: 중간
    route = []
    for i, spot in enumerate(spots):
        if i == len(spots) // 2 and top_lunch:
            route.append(top_lunch[0][0])      # 점심 중간 삽입
        route.append(spot)
    if top_ce7:    route.append(top_ce7[0][0]) # 카페
    if top_dinner: route.append(top_dinner[0][0]) # 저녁

    print(f"  경로: {' → '.join(p.name for p in route)}")

    # ── 2-1. 여유 시간 체크 후 장소 추가 ──
    # 예상 총 소요 시간 계산
    total_est_min = sum(p.effective_stay() for p in route)
    for i in range(len(route) - 1):
        d = haversine(route[i].lat, route[i].lng, route[i+1].lat, route[i+1].lng)
        total_est_min += estimate_transit(d).duration_min
    first_d = haversine(START_LAT, START_LNG, route[0].lat, route[0].lng)
    total_est_min += estimate_transit(first_d).duration_min

    available_min = int((return_time - departure).total_seconds() / 60)
    spare_min = available_min - total_est_min

    print(f"  예상 소요: {total_est_min}분 / 가용: {available_min}분 / 여유: {spare_min}분")

    # 여유 60분 이상이면 Top-2 관광지 추가
    if spare_min >= 90 and len(top_at4) > 2:
        extra = top_at4[2][0]
        extra_min = extra.effective_stay() + 15  # 이동 포함
        if spare_min >= extra_min:
            route.insert(-2, extra)
            print(f"  + 여유 시간으로 '{extra.name}' 추가")
    elif spare_min >= 60 and len(top_ct1) > 1:
        extra = top_ct1[1][0]
        route.insert(1, extra)
        print(f"  + 여유 시간으로 '{extra.name}' 추가")

    # ── 3. 타임라인 생성 & 시간 체크 ─────
    print("\n[3단계] 타임라인 생성")
    timeline, warnings = build_timeline(route, START_LAT, START_LNG, departure, return_time)

    for w in warnings:
        print(f"  {w}")

    print()
    print(f"  {'시간':12s} {'장소':20s} {'체류':>6s} {'교통':>6s} {'수단'}")
    print(f"  {'-'*60}")
    print(f"  {departure.strftime('%H:%M'):12s} {'출발지':20s}")

    for slot in timeline:
        t = slot.transit_before
        print(f"  {slot.arrive_at.strftime('%H:%M'):12s} "
              f"{slot.place.name:20s} "
              f"{slot.place.effective_stay():>5}분 "
              f"{t.fare:>5,}원 "
              f"{t.mode}")
        print(f"  {slot.depart_at.strftime('%H:%M'):12s} {'  └ 출발':20s}")

    # ── 4. 비용 계산 ──────────────────────
    print("\n[4단계] 비용 계산")
    cost = calculate_cost(timeline)
    print(f"  교통비:       {cost['transport']:>7,}원  (확정)")
    print(f"  입장료:       {cost['admission']:>7,}원  (확정)")
    print(f"  ─────────────────────────")
    print(f"  예산 소계:    {cost['subtotal']:>7,}원")
    print(f"  식비 추정:    {cost['meal_est']:>7,}원  (추정)")
    print(f"  카페 추정:    {cost['cafe_est']:>7,}원  (추정)")
    print(f"  실제 예상:    {cost['total_est']:>7,}원")

    # ── 5. 검증 ───────────────────────────
    print("\n[5단계] 검증")
    issues = []

    if not timeline:
        issues.append("❌ 타임라인이 비어있음")
    if any(is_franchise(s.place.name) for s in timeline):
        issues.append("❌ 프랜차이즈가 포함됨")
    if timeline:
        last_depart = timeline[-1].depart_at
        if last_depart > return_time:
            issues.append(f"❌ 귀가 시간 초과: {last_depart.strftime('%H:%M')}")
        else:
            print(f"  ✅ 귀가 시간 준수: {last_depart.strftime('%H:%M')} (여유 {int((return_time - last_depart).seconds/60)}분)")
    if not issues:
        print("  ✅ 프랜차이즈 없음")
        print("  ✅ 시간 제약 통과")
    else:
        for i in issues: print(f"  {i}")

    return timeline, issues

# ──────────────────────────────────────────
# 세 가지 이동 범위로 테스트
# ──────────────────────────────────────────
if __name__ == "__main__":
    for level in ["LOW", "MID", "HIGH"]:
        timeline, issues = run(level)
        print()
