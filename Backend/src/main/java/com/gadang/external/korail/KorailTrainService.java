package com.gadang.external.korail;

import com.gadang.external.transit.TransitRoute;
import com.gadang.external.transit.TransitRouteMapper;
import com.gadang.external.transit.TransportScheduleEntry;
import com.gadang.external.transit.TransportScheduleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 한국철도공사(코레일) 열차운행정보 API
 *
 * 공공데이터포털 서비스: apis.data.go.kr/1613000/TrainInfoService
 *   getCtyCodeList                → 도시코드 목록
 *   getCtyAcctoTrainSttnList      → 도시별 역 목록 (nodeid 형식: NAT…)
 *   getStrtpntAlocFndTrainInfo    → 출발/도착 기준 오늘 열차 시간표
 *
 * DB 저장 포맷은 TRANSIT_ROUTE + TRANSPORT_SCHEDULE (기존 스키마 유지)
 */
@Slf4j
@Service
public class KorailTrainService {

    @Qualifier("korailTrainRestClient")
    private final RestClient korail;

    private final TransitRouteMapper routeMapper;
    private final TransportScheduleMapper scheduleMapper;

    @Value("${datago.api.key}")
    private String apiKey;

    private static final java.time.Duration RAIL_TTL = java.time.Duration.ofHours(20);

    public KorailTrainService(@Qualifier("korailTrainRestClient") RestClient korail,
                              TransitRouteMapper routeMapper,
                              TransportScheduleMapper scheduleMapper) {
        this.korail = korail;
        this.routeMapper = routeMapper;
        this.scheduleMapper = scheduleMapper;
    }

    // ── 반환 타입 (TagoTrainService와 동일 구조 유지) ──────────────────────

    public record GradeStat(int trips, int fare, int durationMin) {
        public static final GradeStat EMPTY = new GradeStat(-1, -1, -1);
    }

    public record TripCounts(GradeStat ktx, GradeStat mugungwha, GradeStat itx) {
        public static final TripCounts EMPTY =
                new TripCounts(GradeStat.EMPTY, GradeStat.EMPTY, GradeStat.EMPTY);
    }

    // ── 역 ID 캐시 ─────────────────────────────────────────────────────────

    private volatile Map<String, String> stationIds = null;

    private static final Map<String, String> NAME_ALIAS = Map.of(
            "여수엑스포", "여수EXPO"
    );

    // ── Public API ─────────────────────────────────────────────────────────

    @Cacheable(value = "trainServiceTime", key = "'korail:' + #fromStation + '->' + #toStation")
    public TripCounts getTripCounts(String fromStation, String toStation) {
        var dbRows = routeMapper.findFresh(fromStation, toStation,
                LocalDateTime.now().minus(RAIL_TTL));
        if (!dbRows.isEmpty()) {
            log.debug("[Korail/L2] {} → {} DB 적중 ({}행)", fromStation, toStation, dbRows.size());
            return fromDbRows(dbRows);
        }
        return fetchAndStore(fromStation, toStation);
    }

    /** 배치·외부에서 직접 호출 가능 */
    public TripCounts fetchAndStore(String fromStation, String toStation) {
        TripCounts counts = fetchFromApi(fromStation, toStation);
        store(fromStation, toStation, "KTX",      counts.ktx());
        store(fromStation, toStation, "무궁화",    counts.mugungwha());
        store(fromStation, toStation, "ITX-청춘",  counts.itx());
        return counts;
    }

    // ── 내부 구현 ───────────────────────────────────────────────────────────

    private TripCounts fetchFromApi(String fromStation, String toStation) {
        String depId = stationId(fromStation);
        String arrId = stationId(toStation);
        if (depId == null || arrId == null) {
            log.debug("[Korail] 역ID 미발견: {}({}) → {}({})", fromStation, depId, toStation, arrId);
            return TripCounts.EMPTY;
        }
        try {
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            Map<?, ?> resp = korail.get()
                    .uri(u -> u.path("/getStrtpntAlocFndTrainInfo")
                            .queryParam("serviceKey", apiKey)
                            .queryParam("depPlaceId", depId)
                            .queryParam("arrPlaceId", arrId)
                            .queryParam("depPlandTime", today)
                            .queryParam("numOfRows", 500)
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(Map.class);

            List<Map<?, ?>> items = extractItems(resp);
            if (items.isEmpty()) return TripCounts.EMPTY;

            LocalDate travelDate = LocalDate.now();
            for (String t : List.of("KTX", "무궁화", "ITX-청춘")) {
                try { scheduleMapper.deleteByRoute(fromStation, toStation, t, travelDate); }
                catch (Exception ignored) {}
            }

            Agg ktx = new Agg(), mu = new Agg(), itx = new Agg();
            for (Map<?, ?> item : items) {
                String grade = String.valueOf(item.get("traingradename"));
                String type;
                Agg target;
                if (grade.startsWith("KTX"))        { type = "KTX";     target = ktx; }
                else if (grade.contains("무궁화"))   { type = "무궁화";   target = mu;  }
                else if (grade.contains("ITX-청춘")) { type = "ITX-청춘"; target = itx; }
                else continue;

                int fare = parseFare(item);
                int dur  = parseDuration(item);
                target.add(fare, dur);

                String depTime = parseHHmm(item.get("depplandtime"));
                String arrTime = parseHHmm(item.get("arrplandtime"));
                String trainNo = item.get("trainno") instanceof String s ? s : null;
                if (depTime != null) {
                    try {
                        scheduleMapper.upsert(TransportScheduleEntry.builder()
                                .fromHub(fromStation).toHub(toStation).type(type)
                                .depTime(depTime).arrTime(arrTime)
                                .durationMin(dur).fare(fare).trainNo(trainNo)
                                .travelDate(travelDate)
                                .build());
                    } catch (Exception e) {
                        log.debug("[Korail/sched] 저장 실패 {} {}: {}", fromStation, depTime, e.getMessage());
                    }
                }
            }
            TripCounts counts = new TripCounts(ktx.toStat(), mu.toStat(), itx.toStat());
            log.debug("[Korail] {} → {} KTX {}회/{}원/{}분, 무궁화 {}회, ITX {}회",
                    fromStation, toStation,
                    counts.ktx().trips(), counts.ktx().fare(), counts.ktx().durationMin(),
                    counts.mugungwha().trips(), counts.itx().trips());
            return counts;

        } catch (Exception e) {
            log.warn("[Korail] {} → {} 조회 실패: {}", fromStation, toStation, e.getMessage());
            return TripCounts.EMPTY;
        }
    }

    private void store(String from, String to, String type, GradeStat s) {
        try {
            routeMapper.upsert(TransitRoute.builder()
                    .fromHub(from).toHub(to).type(type)
                    .durationMin(s.durationMin()).fare(s.fare()).dailyTrips(s.trips())
                    .build());
        } catch (Exception e) {
            log.warn("[Korail/L2] 저장 실패 {}→{} {}: {}", from, to, type, e.getMessage());
        }
    }

    private static TripCounts fromDbRows(List<TransitRoute> rows) {
        GradeStat ktx = GradeStat.EMPTY, mu = GradeStat.EMPTY, itx = GradeStat.EMPTY;
        for (var r : rows) {
            GradeStat s = r.getDailyTrips() > 0
                    ? new GradeStat(r.getDailyTrips(), r.getFare(), r.getDurationMin())
                    : GradeStat.EMPTY;
            switch (r.getType()) {
                case "KTX"      -> ktx = s;
                case "무궁화"    -> mu  = s;
                case "ITX-청춘"  -> itx = s;
            }
        }
        return new TripCounts(ktx, mu, itx);
    }

    // ── 집계 헬퍼 ──────────────────────────────────────────────────────────

    private static class Agg {
        int trips = 0, minFare = -1, minDuration = -1;

        void add(int fare, int durationMin) {
            trips++;
            if (fare > 0 && (minFare < 0 || fare < minFare)) minFare = fare;
            if (durationMin > 0 && (minDuration < 0 || durationMin < minDuration)) minDuration = durationMin;
        }

        GradeStat toStat() {
            return trips > 0 ? new GradeStat(trips, minFare, minDuration) : GradeStat.EMPTY;
        }
    }

    private static int parseFare(Map<?, ?> item) {
        try { return Integer.parseInt(String.valueOf(item.get("adultcharge"))); }
        catch (Exception e) { return -1; }
    }

    private static int parseDuration(Map<?, ?> item) {
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            long min = java.time.Duration.between(
                    java.time.LocalDateTime.parse(String.valueOf(item.get("depplandtime")), f),
                    java.time.LocalDateTime.parse(String.valueOf(item.get("arrplandtime")),  f)
            ).toMinutes();
            return min > 0 ? (int) min : -1;
        } catch (Exception e) { return -1; }
    }

    private static String parseHHmm(Object raw) {
        try {
            String s = String.valueOf(raw);
            if (s.length() < 12) return null;
            return s.substring(8, 10) + ":" + s.substring(10, 12);
        } catch (Exception e) { return null; }
    }

    // ── 역 ID 해석 ─────────────────────────────────────────────────────────

    private String stationId(String stationName) {
        Map<String, String> ids = loadStationIds();
        if (ids.isEmpty()) return null;
        String name = stationName.replaceAll("역$", "").trim();
        name = NAME_ALIAS.getOrDefault(name, name);
        String exact = ids.get(name);
        if (exact != null) return exact;
        for (Map.Entry<String, String> e : ids.entrySet()) {
            if (e.getKey().contains(name) || name.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private Map<String, String> loadStationIds() {
        Map<String, String> local = stationIds;
        if (local != null) return local;
        synchronized (this) {
            if (stationIds != null) return stationIds;
            Map<String, String> map = new ConcurrentHashMap<>();
            try {
                Map<?, ?> cityResp = korail.get()
                        .uri(u -> u.path("/getCtyCodeList")
                                .queryParam("serviceKey", apiKey)
                                .queryParam("_type", "json")
                                .build())
                        .retrieve()
                        .body(Map.class);

                for (Map<?, ?> city : extractItems(cityResp)) {
                    String cityCode = String.valueOf(city.get("citycode"));
                    Map<?, ?> stResp = korail.get()
                            .uri(u -> u.path("/getCtyAcctoTrainSttnList")
                                    .queryParam("serviceKey", apiKey)
                                    .queryParam("cityCode", cityCode)
                                    .queryParam("numOfRows", 500)
                                    .queryParam("_type", "json")
                                    .build())
                            .retrieve()
                            .body(Map.class);

                    for (Map<?, ?> st : extractItems(stResp)) {
                        String name = String.valueOf(st.get("nodename")).trim();
                        String id   = String.valueOf(st.get("nodeid"));
                        if (!name.isEmpty() && id.startsWith("NAT")) map.putIfAbsent(name, id);
                    }
                }
                log.info("[Korail] 전국 기차역 {}개 로딩 완료", map.size());
            } catch (Exception e) {
                log.warn("[Korail] 역 목록 로딩 실패: {}", e.getMessage());
                return Map.of();
            }
            stationIds = map;
            return map;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> extractItems(Map<?, ?> resp) {
        if (resp == null) return List.of();
        Object response = resp.get("response");
        if (!(response instanceof Map<?, ?> r)) return List.of();
        Object body = r.get("body");
        if (!(body instanceof Map<?, ?> b)) return List.of();
        Object items = b.get("items");
        if (!(items instanceof Map<?, ?> i)) return List.of();
        Object item = i.get("item");
        if (item instanceof List<?> list) return (List<Map<?, ?>>) list;
        if (item instanceof Map<?, ?> single) return List.of(single);
        return List.of();
    }
}
