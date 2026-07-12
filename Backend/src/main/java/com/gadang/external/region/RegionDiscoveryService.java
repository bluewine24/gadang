package com.gadang.external.region;

import com.gadang.algorithm.RegionSeedData;
import com.gadang.algorithm.RegionSeedData.BusTerminal;
import com.gadang.algorithm.RegionSeedData.KtxStation;
import com.gadang.external.kakao.KakaoPlaceDto;
import com.gadang.external.korail.KorailTrainService;
import com.gadang.external.odsay.OdsayBusService;
import com.gadang.external.odsay.OdsayTransitService;
import com.gadang.external.region.dto.TransportOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 교통 기반 지역 동적 발견.
 *
 * 고정 지역 목록을 거르는 방식이 아니라, 출발지 근처 역·터미널에서
 * "오늘 직통으로 갈 수 있는 모든 곳"을 역으로 수집해 지역 후보를 만든다.
 *
 *   버스: Odsay /intercityBusTerminals 의 destinationTerminals (직통 도착지 전체 1회 조회)
 *         → 도착지별 /searchInterBusSchedule 로 시간·요금·편수 (병렬, 캐시)
 *   기차: TAGO 출발역 ↔ 주요역 페어 조회 (당일 실편성, 캐시)
 *
 * 반환: 지역명 → 교통 옵션 목록 (빠른 순)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionDiscoveryService {

    private final OdsayBusService odsayBusService;
    private final KorailTrainService korailTrainService;
    private final com.gadang.external.kakao.KakaoPlacesService kakaoPlacesService;
    private final OdsayTransitService odsayTransitService;

    /** 좌표 → 시·군 판정 결과 캐시 (역·터미널 좌표는 불변) */
    private final Map<String, String> coordRegionCache = new ConcurrentHashMap<>();

    /** 지역명 → 시도 (카드의 행정구역 표기용, 좌표 판정 시 함께 채움) */
    private final Map<String, String> regionSidoCache = new ConcurrentHashMap<>();

    /** 지역의 시도 이름 ("전라남도" 등). 미발견 시 빈 문자열 */
    public String sidoOf(String region) {
        return regionSidoCache.getOrDefault(region, "");
    }

    /**
     * 좌표 기반 행정구역 자동 판정 — 수동 매핑 오류(광명→서울 같은) 원천 차단.
     * 좌표가 없거나 실패하면 fallback 이름 사용. 판정 시 시도(sido)도 같이 캐시.
     */
    private String regionOf(double lat, double lng, String fallback) {
        if (lat == 0 || lng == 0) return fallback;
        String key = Math.round(lat * 1000) + "," + Math.round(lng * 1000);
        return coordRegionCache.computeIfAbsent(key, k -> {
            String[] parts = kakaoPlacesService.coordToSidoSiGun(lat, lng);
            if (parts != null) {
                regionSidoCache.putIfAbsent(parts[1], parts[0]);
                return parts[1];
            }
            return fallback != null ? fallback : "";
        });
    }

    /** 버스 도착지 시간표 조회 상한 (Odsay 일일 호출 한도 보호) */
    private static final int MAX_BUS_DEST = 60;

    /** 출발 허브 다중 선정 — 부산(부산역·부전역·사상·종합터미널)처럼 허브가 여러 개인 도시 대응 */
    private static final int    MAX_ORIGIN_HUBS = 3;     // 역/터미널 각각 최대 N개
    private static final double ORIGIN_HUB_KM   = 25.0;  // 출발지에서 이 반경 내 허브 사용

    /** 발견 전용 스레드풀 — commonPool 고갈로 인한 교착 방지 (블로킹 HTTP 다수) */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(12, r -> {
        Thread t = new Thread(r, "region-discovery");
        t.setDaemon(true);
        return t;
    });

    /** 기차 발견 대상 역 → 지역명 (KTX_STATIONS + 대체역 + 전라·경전·동해선 주요역) */
    private static final Map<String, String> STATION_REGION = Map.ofEntries(
            Map.entry("서울역", "서울"),     Map.entry("용산역", "서울"),
            Map.entry("청량리역", "서울"),   Map.entry("수서역", "서울"),
            Map.entry("광명역", "광명"),
            Map.entry("부산역", "부산"),     Map.entry("부전역", "부산"),
            Map.entry("동대구역", "대구"),   Map.entry("대구역", "대구"),
            Map.entry("신경주역", "경주"),   Map.entry("울산역", "울산"),
            Map.entry("광주송정역", "광주"), Map.entry("광주역", "광주"),
            Map.entry("전주역", "전주"),     Map.entry("순천역", "순천"),
            Map.entry("여수엑스포역", "여수"),
            Map.entry("강릉역", "강릉"),     Map.entry("춘천역", "춘천"),
            Map.entry("남춘천역", "춘천"),   Map.entry("안동역", "안동"),
            Map.entry("대전역", "대전"),     Map.entry("서대전역", "대전"),
            Map.entry("천안아산역", "천안"), Map.entry("오송역", "청주"),
            Map.entry("김천구미역", "구미"), Map.entry("목포역", "목포"),
            Map.entry("익산역", "익산"),     Map.entry("정읍역", "정읍"),
            Map.entry("남원역", "남원"),     Map.entry("곡성역", "곡성"),
            Map.entry("구례구역", "구례"),   Map.entry("나주역", "나주"),
            Map.entry("포항역", "포항"),     Map.entry("진주역", "진주"),
            Map.entry("마산역", "창원"),     Map.entry("창원중앙역", "창원"),
            Map.entry("밀양역", "밀양"),     Map.entry("동해역", "동해"),
            Map.entry("원주역", "원주"),     Map.entry("제천역", "제천")
    );

    /** 발견 대상 역 좌표 (출발역 선정용) — KTX_STATIONS에 없는 역 포함 */
    private static final KtxStation[] DISCOVERY_STATIONS;
    static {
        List<KtxStation> list = new ArrayList<>(Arrays.asList(RegionSeedData.KTX_STATIONS));
        list.add(new KtxStation(37.5298, 126.9648, "용산역"));
        list.add(new KtxStation(37.5801, 127.0489, "청량리역"));
        list.add(new KtxStation(37.4163, 126.8847, "광명역"));
        list.add(new KtxStation(35.1631, 129.0606, "부전역"));
        list.add(new KtxStation(35.8869, 128.5988, "대구역"));
        list.add(new KtxStation(35.1653, 126.9092, "광주역"));
        list.add(new KtxStation(34.7936, 126.3886, "목포역"));
        list.add(new KtxStation(36.3323, 127.4346, "대전역"));
        list.add(new KtxStation(36.3239, 127.4044, "서대전역"));
        list.add(new KtxStation(35.9398, 126.9446, "익산역"));
        list.add(new KtxStation(35.5764, 126.8517, "정읍역"));
        list.add(new KtxStation(35.4203, 127.3845, "남원역"));
        list.add(new KtxStation(35.2820, 127.2920, "곡성역"));
        list.add(new KtxStation(35.1740, 127.4630, "구례구역"));
        list.add(new KtxStation(35.0269, 126.7140, "나주역"));
        list.add(new KtxStation(36.0731, 129.3399, "포항역"));
        list.add(new KtxStation(35.1500, 128.1207, "진주역"));
        list.add(new KtxStation(35.2225, 128.5827, "마산역"));
        list.add(new KtxStation(35.2287, 128.6811, "창원중앙역"));
        list.add(new KtxStation(35.4920, 128.7860, "밀양역"));
        list.add(new KtxStation(37.5446, 129.1077, "동해역"));
        list.add(new KtxStation(37.3387, 127.9743, "원주역"));
        list.add(new KtxStation(37.1410, 128.1940, "제천역"));
        DISCOVERY_STATIONS = list.toArray(new KtxStation[0]);
    }

    /**
     * 출발 좌표 기준 직통 가능 지역 발견.
     *
     * @return 지역명 → 옵션 목록 (oneWayMin 오름차순)
     */
    public Map<String, List<TransportOption>> discover(double[] origin) {
        Map<String, List<TransportOption>> regions = new ConcurrentHashMap<>();

        // 출발 허브를 1개가 아니라 반경 내 상위 N개로 — 부산역·부전역 등 멀티허브 도시 대응
        String originRegion = regionOf(origin[0], origin[1], null);
        List<KtxStation>  originStations  = nearestStations(origin, MAX_ORIGIN_HUBS, ORIGIN_HUB_KM);
        List<BusTerminal> originTerminals = nearestTerminals(origin, MAX_ORIGIN_HUBS, ORIGIN_HUB_KM);
        log.info("[지역발견] 출발 허브 역={} 터미널={} (자기지역={})",
                originStations.stream().map(s -> s.name).toList(),
                originTerminals.stream().map(t -> t.name).toList(), originRegion);

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (KtxStation s : originStations) {
            tasks.add(CompletableFuture.runAsync(() -> discoverByRail(origin, s, originRegion, regions), POOL));
        }
        for (BusTerminal t : originTerminals) {
            tasks.add(CompletableFuture.runAsync(() -> discoverByBus(origin, t, originRegion, regions), POOL));
        }
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();

        // 지역별 옵션 정리: 같은 (수단·도착허브) 중복 제거 후 빠른 순 정렬
        regions.replaceAll((region, list) -> dedupOptions(list));

        log.info("[지역발견] 직통 가능 지역 {}곳: {}", regions.size(), regions.keySet());
        return regions;
    }

    /** 같은 수단+도착허브 옵션 중 가장 빠른 것만 남기고 oneWayMin 오름차순 정렬 */
    private static List<TransportOption> dedupOptions(List<TransportOption> list) {
        Map<String, TransportOption> best = new LinkedHashMap<>();
        for (TransportOption o : list) {
            String key = o.getType() + "|" + o.getToHub();
            TransportOption cur = best.get(key);
            if (cur == null || o.getOneWayMin() < cur.getOneWayMin()) best.put(key, o);
        }
        List<TransportOption> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparingInt(TransportOption::getOneWayMin));
        return out;
    }

    // ─────────────────────────────────────────────────────────────────
    // 버스
    // ─────────────────────────────────────────────────────────────────

    private void discoverByBus(double[] origin, BusTerminal fromT, String originRegion, Map<String, List<TransportOption>> regions) {
        int[] hubMetrics = originToHubMetrics(origin, fromT.lat, fromT.lng);
        int hubMin = hubMetrics[0]; int hubFare = hubMetrics[1];
        log.info("[지역발견/버스] 출발터미널={} (자기지역={}, 접근 {}분) 조회 시작", fromT.name, originRegion, hubMin);

        List<OdsayBusService.Destination> dests = odsayBusService.listDestinations(fromT);
        log.info("[지역발견/버스] 직통 도착지 {}곳", dests.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        int count = 0;
        for (OdsayBusService.Destination d : dests) {
            if (count++ >= MAX_BUS_DEST) break;
            // 좌표 기반 행정구역 판정 (좌표 없으면 이름 규칙 fallback) + 시·군 화이트리스트
            String byCoord = regionOf(d.lat(), d.lng(), null);
            String region = (byCoord != null && KNOWN_REGIONS.contains(byCoord))
                    ? byCoord : regionNameFromTerminal(d.name());
            if (region == null || region.equals(originRegion)) continue;

            futures.add(CompletableFuture.runAsync(() -> {
                OdsayBusService.BusRouteResult r =
                        odsayBusService.getScheduleByIds(d.fromStationId(), d.stationId());
                if (r != null && r.travelMin() > 0) {
                    addOption(regions, region, new TransportOption(
                            "시외버스", hubMin + r.travelMin(), r.fare(), fromT.name, d.name(),
                            r.dailyTrips(), hubMin, hubFare));
                }
            }, POOL));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /** 터미널명 → 지역명. "부산서부(사상)" → 부산, "동서울" → 서울, "대전복합" → 대전 … */
    static String regionNameFromTerminal(String terminalName) {
        String n = terminalName.replaceAll("\\(.*?\\)", "")
                .replace("터미널", "").replace("정류장", "").replace("정류소", "")
                .replace("시외버스", "").replace("고속버스", "").replace("직행버스", "")
                .replace("공용버스", "").replace("종합버스", "").replace("버스", "")
                .replace("공용", "").replace("종합", "").trim();

        // 방위·별칭 접두어 정리
        String[][] alias = {
                {"동서울", "서울"}, {"서울경부", "서울"}, {"서울호남", "서울"}, {"센트럴", "서울"},
                {"부산서부", "부산"}, {"부산동부", "부산"}, {"부산사상", "부산"},
                {"해운대", "부산"}, {"동래", "부산"},
                {"동대구", "대구"}, {"대구북부", "대구"}, {"대구서부", "대구"}, {"서부", "대구"},
                {"대전복합", "대전"}, {"대전청사", "대전"},
                {"유스퀘어", "광주"}, {"광주유스퀘어", "광주"},
                {"인천종합", "인천"},
                {"중마", "광양"}, {"고창", "고창"}, {"연무대", "논산"}, {"남악", "무안"},
        };
        for (String[] a : alias) {
            if (n.startsWith(a[0]) || n.contains(a[0])) {
                n = a[1];
                break;
            }
        }
        // 숫자·공백 정리
        n = n.replaceAll("[0-9]", "").trim();
        // 시·군 화이트리스트 + 블랙리스트 필터
        if (!KNOWN_REGIONS.contains(n) || DESTINATION_BLACKLIST.contains(n)) return null;
        return n;
    }

    /**
     * 지역 카드로 표시하지 않을 시·군 (KNOWN_REGIONS에 있어도 목적지 카드에서 제외).
     * 인접 대도시에 흡수되거나 단독 여행지로 의미가 없는 지역.
     */
    private static final Set<String> DESTINATION_BLACKLIST = Set.of(
            "김해"   // 부산 생활권 — 단독 여행지로 잘 검색되지 않음
    );

    /** 지역 카드로 인정하는 전국 시·군 명단 (행정구역 단위) */
    private static final Set<String> KNOWN_REGIONS = Set.of(
            // 수도권
            "서울", "인천", "수원", "성남", "고양", "용인", "부천", "안산", "안양", "평택",
            "시흥", "김포", "광명", "의정부", "파주", "구리", "남양주", "오산", "이천", "여주",
            "양평", "동두천", "안성", "포천", "가평", "강화",
            // 강원
            "춘천", "원주", "강릉", "동해", "속초", "삼척", "태백", "홍천", "횡성", "영월",
            "평창", "정선", "철원", "화천", "양구", "인제", "고성", "양양",
            // 충청
            "대전", "세종", "청주", "충주", "제천", "보은", "옥천", "영동", "진천", "괴산",
            "음성", "단양", "증평", "천안", "공주", "보령", "아산", "서산", "논산", "계룡",
            "당진", "금산", "부여", "서천", "청양", "홍성", "예산", "태안",
            // 전북
            "전주", "군산", "익산", "정읍", "남원", "김제", "완주", "진안", "무주", "장수",
            "임실", "순창", "고창", "부안",
            // 전남·광주
            "광주", "목포", "여수", "순천", "나주", "광양", "담양", "곡성", "구례", "고흥",
            "보성", "화순", "장흥", "강진", "해남", "영암", "무안", "함평", "영광", "장성",
            "완도", "진도", "신안",
            // 경북·대구
            "대구", "포항", "경주", "김천", "안동", "구미", "영주", "영천", "상주", "문경",
            "경산", "의성", "청송", "영양", "영덕", "청도", "고령", "성주", "칠곡", "예천",
            "봉화", "울진", "울릉",
            // 경남·부산·울산
            "부산", "울산", "창원", "마산", "진주", "통영", "사천", "김해", "밀양", "거제",
            "양산", "의령", "함안", "창녕", "거창", "합천", "남해", "하동", "산청", "함양"
    );

    // ─────────────────────────────────────────────────────────────────
    // 기차
    // ─────────────────────────────────────────────────────────────────

    private void discoverByRail(double[] origin, KtxStation from, String originRegion, Map<String, List<TransportOption>> regions) {
        int[] hubMetrics = originToHubMetrics(origin, from.lat, from.lng);
        int hubMin = hubMetrics[0]; int hubFare = hubMetrics[1];
        log.info("[지역발견/기차] 출발역={} (자기지역={}, 접근 {}분) 조회 시작", from.name, originRegion, hubMin);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (KtxStation dest : DISCOVERY_STATIONS) {
            if (dest.name.equals(from.name)) continue;
            // 좌표 기반 행정구역 판정 — 광명역→광명, 김천구미역→김천, 천안아산역→아산 자동 처리
            String region = regionOf(dest.lat, dest.lng, STATION_REGION.get(dest.name));
            if (region == null || region.isEmpty() || region.equals(originRegion)) continue;

            futures.add(CompletableFuture.runAsync(() -> {
                KorailTrainService.TripCounts tc = korailTrainService.getTripCounts(from.name, dest.name);
                addRail(regions, region, "KTX",      tc.ktx(),       from, dest, hubMin, hubFare);
                addRail(regions, region, "무궁화",    tc.mugungwha(), from, dest, hubMin, hubFare);
                addRail(regions, region, "ITX-청춘",  tc.itx(),       from, dest, hubMin, hubFare);
            }, POOL));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void addRail(Map<String, List<TransportOption>> regions, String region, String label,
                         KorailTrainService.GradeStat stat, KtxStation from, KtxStation dest, int hubMin, int hubFare) {
        if (stat.trips() <= 0 || stat.durationMin() <= 0) return;
        addOption(regions, region, new TransportOption(
                label, hubMin + stat.durationMin(), Math.max(0, stat.fare()),
                from.name, dest.name, stat.trips(), hubMin, hubFare));
    }

    /**
     * 현재위치 → 허브(역/터미널) 편도 [이동시간(분), 요금(원)].
     * ODsay 대중교통 경로(왕복값을 절반으로)를 우선 사용하고, 실패 시 직선거리 추정으로 대체한다.
     */
    private int[] originToHubMetrics(double[] origin, double hubLat, double hubLng) {
        try {
            int[] t = odsayTransitService.getTransit(origin[0], origin[1], hubLat, hubLng);
            if (t != null && t[0] > 0) return new int[]{ t[0] / 2, Math.max(0, t[1] / 2) };
        } catch (Exception e) {
            log.debug("[지역발견] 허브 접근시간/요금 조회 실패, 추정값 사용: {}", e.getMessage());
        }
        double km = haversineKm(origin[0], origin[1], hubLat, hubLng);
        int min = (int) Math.round(km / 25.0 * 60);
        int fare = estimateTransitFare(km);
        return new int[]{ min, fare };
    }

    private static int estimateTransitFare(double km) {
        if (km <= 0) return 0;
        // 대중교통 기본요금 1,400원 + 10km 초과분 100원/5km
        int extra = (int) Math.max(0, (km - 10) / 5);
        return 1400 + extra * 100;
    }

    // ─────────────────────────────────────────────────────────────────

    private static void addOption(Map<String, List<TransportOption>> regions,
                                  String region, TransportOption opt) {
        regions.computeIfAbsent(region, k -> Collections.synchronizedList(new ArrayList<>())).add(opt);
    }

    /**
     * 출발지에서 가까운 터미널 상위 maxCount개 (maxKm 반경, 최소 1개는 보장).
     *
     * 고정 시드 목록(RegionSeedData.BUS_TERMINALS, 전국 16곳)만으로는 시드에 없는 지역
     * (신규 도시, 명칭이 다른 터미널 등)에서 허브를 못 찾는 문제가 재발하므로, Kakao 키워드
     * 근접 검색으로 동적 발견 결과를 함께 합쳐 모든 지역에서 동작하도록 한다.
     */
    private List<BusTerminal> nearestTerminals(double[] origin, int maxCount, double maxKm) {
        Map<String, BusTerminal> candidates = new LinkedHashMap<>();
        for (BusTerminal t : RegionSeedData.BUS_TERMINALS) {
            candidates.putIfAbsent(t.name, t);
        }
        int kakaoRadiusM = (int) Math.min(maxKm * 1000, 20000); // Kakao Local API 반경 상한 20km
        for (String kw : List.of("고속버스터미널", "시외버스터미널")) {
            for (KakaoPlaceDto p : kakaoPlacesService.searchByKeywordNear(kw, origin[0], origin[1], kakaoRadiusM)) {
                double lat = p.getLat(), lng = p.getLng();
                if (lat == 0 || lng == 0 || p.getPlaceName() == null) continue;
                candidates.putIfAbsent(p.getPlaceName(), new BusTerminal(lat, lng, p.getPlaceName()));
            }
        }

        List<BusTerminal> sorted = candidates.values().stream()
                .sorted(Comparator.comparingDouble(t -> haversineKm(origin[0], origin[1], t.lat, t.lng)))
                .toList();
        List<BusTerminal> picked = sorted.stream()
                .filter(t -> haversineKm(origin[0], origin[1], t.lat, t.lng) <= maxKm)
                .limit(maxCount)
                .collect(Collectors.toList());
        if (picked.isEmpty() && !sorted.isEmpty()) picked.add(sorted.get(0)); // 반경 밖이어도 최근접 1개
        return picked;
    }

    /** 출발지에서 가까운 역 상위 maxCount개 (maxKm 반경, 최소 1개는 보장). */
    private List<KtxStation> nearestStations(double[] origin, int maxCount, double maxKm) {
        List<KtxStation> sorted = Arrays.stream(DISCOVERY_STATIONS)
                .sorted(Comparator.comparingDouble(s -> haversineKm(origin[0], origin[1], s.lat, s.lng)))
                .toList();
        List<KtxStation> picked = sorted.stream()
                .filter(s -> haversineKm(origin[0], origin[1], s.lat, s.lng) <= maxKm)
                .limit(maxCount)
                .collect(Collectors.toList());
        if (picked.isEmpty() && !sorted.isEmpty()) picked.add(sorted.get(0));
        return picked;
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
}
