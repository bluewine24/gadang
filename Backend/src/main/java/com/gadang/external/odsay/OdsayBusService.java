package com.gadang.external.odsay;

import com.gadang.algorithm.RegionSeedData.BusTerminal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Odsay 시외버스/고속버스 운행정보 조회 서비스
 *
 * 올바른 엔드포인트:
 *   터미널 검색: /intercityBusTerminals?terminalName=xxx
 *   고속버스 터미널: /expressBusTerminals?terminalName=xxx
 *   시간표 조회: /searchInterBusSchedule?startStationID=xxx&endStationID=yyy
 *
 * result.count → 하루 운행 편수
 * result.schedule[0].wasteTime → 소요시간(분)
 * result.schedule[0].fare → 요금(원)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OdsayBusService {

    @Qualifier("odsayRestClient")
    private final RestClient odsayRestClient;

    private final com.gadang.external.transit.TransitRouteMapper routeMapper;
    private final com.gadang.external.transit.TransportScheduleMapper scheduleMapper;

    @Value("${odsay.api.key}")
    private String apiKey;

    /** L2(DB) 신선 기준 — 시외버스 시간표는 거의 불변이라 6일 (Odsay 일 1,000건 쿼터 보호) */
    private static final java.time.Duration BUS_TTL = java.time.Duration.ofDays(6);
    /** 버스 시간표는 정적이므로 고정 날짜 사용 */
    private static final java.time.LocalDate BUS_FIXED_DATE = java.time.LocalDate.of(1970, 1, 1);

    /** 버스 노선 조회 결과 */
    public record BusRouteResult(int travelMin, int fare, int dailyTrips) {}

    /** 출발 터미널에서 직통으로 갈 수 있는 도착 터미널 (lat/lng = 0이면 좌표 미제공) */
    public record Destination(int fromStationId, int stationId, String name, double lat, double lng) {}

    /**
     * 출발 터미널의 직통 도착지 전체 목록 (시외 + 고속 합산, 이름 중복 제거).
     * 지역 동적 발견(RegionDiscoveryService)의 핵심 입력.
     */
    @Cacheable(value = "intercityBus", key = "'dests:' + #fromT.name")
    public List<Destination> listDestinations(BusTerminal fromT) {
        List<Destination> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String type : List.of("intercityBusTerminals", "expressBusTerminals")) {
            collectDestinations(fromT, type, out, seen);
        }
        log.debug("[Bus/dests] {} → 직통 {}곳", fromT.name, out.size());
        return out;
    }

    /**
     * 터미널 ID 쌍으로 시간표 조회.
     * L1 Caffeine → L2 TRANSIT_ROUTE(DB) → L3 Odsay API (호출 후 DB에 write-through)
     * duration_min = -1 행은 "운행 없음" 부정 캐시 — 없는 노선을 매번 재조회하지 않음
     */
    @Cacheable(value = "intercityBus", key = "'sched:' + #sid + '-' + #eid")
    public BusRouteResult getScheduleByIds(int sid, int eid) {
        String fromHub = "ODSAY:" + sid, toHub = "ODSAY:" + eid;

        var dbRows = routeMapper.findFresh(fromHub, toHub,
                java.time.LocalDateTime.now().minus(BUS_TTL));
        if (!dbRows.isEmpty()) {
            var r = dbRows.get(0);
            log.debug("[Bus/L2] {} → {} DB 적중", fromHub, toHub);
            return r.getDurationMin() > 0
                    ? new BusRouteResult(r.getDurationMin(), r.getFare(), r.getDailyTrips())
                    : null;
        }
        return fetchAndStoreSchedule(sid, eid);
    }

    /** L3: Odsay API 호출 후 DB upsert. 배치 갱신에서도 직접 호출 */
    public BusRouteResult fetchAndStoreSchedule(int sid, int eid) {
        BusRouteResult r = fetchSchedule(sid, eid, "id:" + sid, "id:" + eid);
        try {
            routeMapper.upsert(com.gadang.external.transit.TransitRoute.builder()
                    .fromHub("ODSAY:" + sid).toHub("ODSAY:" + eid).type("시외버스")
                    .durationMin(r != null ? r.travelMin() : -1)
                    .fare(r != null ? r.fare() : -1)
                    .dailyTrips(r != null ? r.dailyTrips() : -1)
                    .build());
        } catch (Exception e) {
            log.warn("[Bus/L2] 저장 실패 {}→{}: {}", sid, eid, e.getMessage());
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private void collectDestinations(BusTerminal fromT, String terminalType,
                                     List<Destination> out, Set<String> seen) {
        try {
            String query = stripTerminalSuffix(fromT.name);
            Map<?, ?> resp = odsayRestClient.get()
                    .uri(u -> u.path("/" + terminalType)
                            .queryParam("terminalName", query)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (resp == null || resp.get("result") == null) return;

            Map<?, ?> fromResult = pickMatchingTerminal(resp.get("result"), query, fromT.lat, fromT.lng);
            if (fromResult == null) {
                log.debug("[{}] '{}' 이름/좌표 일치 터미널 없음 — 스킵", terminalType, query);
                return;
            }

            Object sidObj = fromResult.get("stationID");
            if (!(sidObj instanceof Number)) return;
            int sid = ((Number) sidObj).intValue();

            List<?> destinations = (List<?>) fromResult.get("destinationTerminals");
            if (destinations == null) return;

            for (Object d : destinations) {
                Map<?, ?> dm = (Map<?, ?>) d;
                Object eidObj = dm.get("stationID");
                String name = String.valueOf(dm.get("stationName"));
                if (eidObj instanceof Number && seen.add(name)) {
                    out.add(new Destination(sid, ((Number) eidObj).intValue(), name,
                            num(dm.get("y")), num(dm.get("x"))));
                }
            }
        } catch (Exception e) {
            log.warn("[{}] 도착지 목록 실패 {}: {}", terminalType, fromT.name, e.getMessage());
        }
    }

    private static String stripTerminalSuffix(String name) {
        return name.replace("종합버스터미널", "").replace("고속버스터미널", "")
                .replace("버스터미널", "").replace("터미널", "").trim();
    }

    private static double num(Object v) {
        try {
            return v instanceof Number ? ((Number) v).doubleValue() : Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 터미널 검색 결과에서 실제로 query 터미널인 항목 선택.
     * Odsay가 무관한 항목을 첫 번째로 줄 수 있다:
     *   "순천" 고속 검색 → 1번째 "정안휴게소(순천방향)고속버스환승정류소" (공주 소재!)
     * contains 매칭도 휴게소 환승정류소에 걸리므로, 이름이 query로 시작하는 것만 1차로 인정.
     *
     * 이름 접두 매칭이 실패할 때(Odsay 공식명이 통상명과 다른 경우 — 예: "부산종합버스터미널"의
     * Odsay 등록명이 "노포동"인 경우)는 알고 있는 좌표와 5km 이내 최근접 후보로 대체한다.
     * 이렇게 하면 터미널 이름이 지역마다 제각각이어도(유스퀘어, 노포동 등) 좌표만 맞으면 항상 찾는다.
     */
    private static Map<?, ?> pickMatchingTerminal(Object resultRaw, String query, double targetLat, double targetLng) {
        List<?> candidates;
        if (resultRaw instanceof List) {
            candidates = (List<?>) resultRaw;
        } else if (resultRaw instanceof Map) {
            candidates = List.of(resultRaw);
        } else {
            return null;
        }
        Map<?, ?> nearest = null;
        double nearestKm = Double.MAX_VALUE;
        for (Object c : candidates) {
            if (!(c instanceof Map<?, ?> m)) continue;
            String name = String.valueOf(m.get("stationName"));
            if (name.contains("휴게소")) continue;
            if (name.startsWith(query)) return m;

            if (targetLat != 0 && targetLng != 0) {
                double y = num(m.get("y")), x = num(m.get("x"));
                if (y != 0 && x != 0) {
                    double km = haversineKm(targetLat, targetLng, y, x);
                    if (km < nearestKm) { nearestKm = km; nearest = m; }
                }
            }
        }
        return (nearest != null && nearestKm <= 5.0) ? nearest : null;
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * 두 버스터미널 간 편도 소요시간·요금·하루 운행 편수.
     * 시외버스 우선, 없으면 고속버스 시도.
     */
    @Cacheable(value = "intercityBus", key = "#from.name + '->' + #dest.name")
    public BusRouteResult getRoute(BusTerminal from, BusTerminal dest) {
        log.debug("[Bus] {} → {}", from.name, dest.name);

        BusRouteResult result = fetchRoute(from, dest.name, "intercityBusTerminals");
        if (result == null) {
            result = fetchRoute(from, dest.name, "expressBusTerminals");
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private BusRouteResult fetchRoute(BusTerminal from, String toName, String terminalType) {
        try {
            // 1. 출발터미널 검색 → 이름 일치 항목만 사용 (첫 항목이 엉뚱한 터미널일 수 있음)
            String query = stripTerminalSuffix(from.name);
            Map<?, ?> resp = odsayRestClient.get()
                    .uri(u -> u.path("/" + terminalType)
                            .queryParam("terminalName", query)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (resp == null || resp.get("result") == null) return null;

            Map<?, ?> fromResult = pickMatchingTerminal(resp.get("result"), query, from.lat, from.lng);
            if (fromResult == null) return null;

            Object sidObj = fromResult.get("stationID");
            if (!(sidObj instanceof Number)) return null;
            int sid = ((Number) sidObj).intValue();

            // 2. 도착터미널 ID: destinationTerminals 목록에서 퍼지 매칭
            List<?> destinations = (List<?>) fromResult.get("destinationTerminals");
            if (destinations == null || destinations.isEmpty()) return null;

            String toStripped = toName.replace("종합버스터미널","").replace("고속버스터미널","")
                    .replace("버스터미널","").replace("터미널","").trim();

            Integer eid = null;
            for (Object d : destinations) {
                Map<?, ?> dm = (Map<?, ?>) d;
                String odsayName = String.valueOf(dm.get("stationName"));
                if (odsayName.contains(toStripped) || toStripped.contains(odsayName)) {
                    Object eidObj = dm.get("stationID");
                    if (eidObj instanceof Number) {
                        eid = ((Number) eidObj).intValue();
                        break;
                    }
                }
            }

            if (eid == null) {
                log.debug("[{}] {} → {} : 도착터미널 없음", terminalType, from.name, toName);
                return null;
            }

            // 3. 시간표 조회
            return fetchSchedule(sid, eid, from.name, toName);

        } catch (Exception e) {
            log.warn("[{}] 조회 실패 {} → {}: {}", terminalType, from.name, toName, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private BusRouteResult fetchSchedule(int sid, int eid, String fromName, String toName) {
        try {
            final int sidF = sid, eidF = eid;
            Map<?, ?> resp = odsayRestClient.get()
                    .uri(u -> u.path("/searchInterBusSchedule")
                            .queryParam("startStationID", sidF)
                            .queryParam("endStationID", eidF)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (resp == null || resp.get("result") == null) return null;
            Map<?, ?> result = (Map<?, ?>) resp.get("result");

            // count = 하루 운행 편수
            Object countObj = result.get("count");
            int dailyTrips = countObj instanceof Number ? ((Number) countObj).intValue() : 0;
            if (dailyTrips == 0) return null;

            // 편별 시간표 파싱
            List<?> schedule = (List<?>) result.get("schedule");
            if (schedule == null || schedule.isEmpty()) return null;

            Map<?, ?> first = (Map<?, ?>) schedule.get(0);
            int travelMin = first.get("wasteTime") instanceof Number
                    ? ((Number) first.get("wasteTime")).intValue() : 0;
            int fare = first.get("fare") instanceof Number
                    ? ((Number) first.get("fare")).intValue() : 0;

            if (travelMin <= 0) return null;

            // 편별 시각 저장 (fromName/toName이 터미널 이름)
            saveSchedule(fromName, toName, schedule, travelMin, fare);

            log.debug("[Bus/OK] {} → {} : {}분 {}원 하루{}회", fromName, toName, travelMin, fare, dailyTrips);
            return new BusRouteResult(travelMin, fare, dailyTrips);

        } catch (Exception e) {
            log.warn("[Bus/schedule] 실패 {}: {}", fromName + "→" + toName, e.getMessage());
            return null;
        }
    }

    /** Odsay schedule 배열 → TRANSPORT_SCHEDULE 저장 (HHMM → HH:mm 변환) */
    private void saveSchedule(String fromHub, String toHub, List<?> schedule, int defaultMin, int defaultFare) {
        try {
            scheduleMapper.deleteByRoute(fromHub, toHub, "시외버스", BUS_FIXED_DATE);
            for (Object raw : schedule) {
                if (!(raw instanceof Map<?, ?> s)) continue;
                String depTime = busHHmm(s.get("startTime"));
                if (depTime == null) continue;
                String arrTime = busHHmm(s.get("endTime"));
                int dur  = s.get("wasteTime") instanceof Number n ? n.intValue() : defaultMin;
                int fare = s.get("fare")      instanceof Number n ? n.intValue() : defaultFare;
                scheduleMapper.upsert(
                    com.gadang.external.transit.TransportScheduleEntry.builder()
                        .fromHub(fromHub).toHub(toHub).type("시외버스")
                        .depTime(depTime).arrTime(arrTime)
                        .durationMin(dur).fare(fare)
                        .travelDate(BUS_FIXED_DATE)
                        .build());
            }
        } catch (Exception e) {
            log.debug("[Bus/sched] 저장 실패 {}→{}: {}", fromHub, toHub, e.getMessage());
        }
    }

    /** Odsay "HHMM" → "HH:mm". null/빈 값이면 null 반환 */
    private static String busHHmm(Object raw) {
        try {
            String s = String.valueOf(raw).trim();
            if (s.length() < 4 || s.equals("null")) return null;
            return s.substring(0, 2) + ":" + s.substring(2, 4);
        } catch (Exception e) {
            return null;
        }
    }
}
