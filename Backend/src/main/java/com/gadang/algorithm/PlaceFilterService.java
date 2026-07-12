package com.gadang.algorithm;

import com.gadang.external.kakao.KakaoPlaceDto;
import com.gadang.external.kakao.KakaoPlacesService;
import com.gadang.external.naver.NaverBlogService;
import com.gadang.external.naver.NaverDataLabService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Set;

/**
 * 장소 후보 스코어링 서비스 — 카카오 수집 + 네이버 블로그(지역명 결합) 인기 평가.
 *
 *   후보 풀  : Kakao Local 카테고리 검색 (관광·문화·음식·카페) — 신상 핫플까지 포함
 *   인기 신호: 네이버 블로그 "지역명 + 장소명" 언급 수 (실시간 + 동음이의어 제거)
 *             → "경주 옥고개골"(1건)·"경주 소금강산"(3천건) 같은 노이즈는 1만 컷으로 탈락,
 *               "경주 황리단길"(85만)·"경주 천마총"(9만) 같은 명소는 상위
 *
 *   특정 외부 큐레이션(TourAPI 등)에 묶이지 않아 신상 장소·실시간 인기를 반영한다.
 *   finalScore = trend*0.8 (블로그 인기) + accuracyBonus*0.2 (카카오 검색순위)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceFilterService {

    private final KakaoPlacesService  kakaoPlacesService;
    private final NaverBlogService    naverBlogService;
    private final NaverDataLabService naverDataLabService;
    private final com.gadang.admin.AdminMapper adminMapper;

    /** 프랜차이즈 브랜드명 (최초 1회 로드, 로컬 장소 우선 정책 F125) */
    private volatile List<String> franchiseBrands = null;

    private boolean isFranchise(String placeName) {
        List<String> brands = franchiseBrands;
        if (brands == null) {
            synchronized (this) {
                if (franchiseBrands == null) {
                    try { franchiseBrands = adminMapper.findAllBrandNames(); }
                    catch (Exception e) { franchiseBrands = List.of(); }
                }
                brands = franchiseBrands;
            }
        }
        String n = placeName == null ? "" : placeName.replaceAll("\\s+", "");
        for (String b : brands) {
            if (!b.isBlank() && n.contains(b.replaceAll("\\s+", ""))) return true;
        }
        return false;
    }

    /** 어느 동네에나 똑같이 있어 그 지역만의 여행 매력이 없는 부대시설·생활편의시설 키워드.
     *  주차장/출입구/화장실 같은 시설물부터 PC방·노래방·만화카페·영화관처럼 전국 어디서나
     *  볼 수 있는 실내 유흥업종까지 — "이 지역이라서" 갈 이유가 없는 곳은 후보에서 제외 */
    private static final List<String> EXCLUDED_KEYWORDS = List.of(
            "주차장", "출입구", "화장실", "공중화장실",
            "영화관", "시네마", "CGV", "롯데시네마", "메가박스",
            "만화카페", "만화방", "북카페", "웹툰",
            "PC방", "피씨방",
            "노래방", "코인노래방",
            "보드게임카페", "보드게임",
            // 전국 어디에나 있는 대형 프랜차이즈 — DB 시드 누락 방어선
            "스타벅스", "이디야", "투썸플레이스", "커피빈", "파스쿠찌", "공차", "메가커피", "컴포즈커피",
            "맥도날드", "버거킹", "롯데리아", "KFC", "맘스터치", "서브웨이",
            "이마트", "롯데마트", "홈플러스", "GS더프레시", "트레이더스", "메가마트",
            "노브랜드", "농협하나로마트", "하나로마트",
            "파리바게뜨", "뚜레쥬르", "던킨", "배스킨라빈스",
            "올리브영", "다이소", "CU편의점", "GS25", "세븐일레븐", "이마트24",
            "CGV", "롯데시네마", "메가박스",
            // 동네 생활공원 — 여행 목적지가 아님
            "어린이공원", "근린공원", "소공원",
            // 트레킹 코스 인증대 — 체크포인트 시설이지 목적지가 아님
            "인증대", "스탬프",
            // 임시·계절 행사 — 관광공사 API로 별도 제공
            "축제", "눈썰매장", "썰매장", "얼음낚시"
    );

    /** 단독 일반명사 장소명 — 블로그 쿼리 시 지역 내 동일 단어 모두 합산되는 노이즈 유발 */
    private static final Set<String> GENERIC_NAMES = Set.of(
            "공원", "광장", "산", "바다", "해변", "해수욕장", "항구", "항",
            "시장", "마트", "백화점", "터미널", "역", "버스터미널",
            "근린공원"
    );

    /** 놀러갈 곳이 아닌 부대시설·전국 어디나 있는 실내 유흥업종 제외 */
    private boolean isExcludedPlace(KakaoPlaceDto p) {
        // 주차장·지하철역·버스터미널 — 카카오 코드로 제외
        String groupCode = p.getCategoryGroupCode();
        if ("PK6".equals(groupCode) || "SW8".equals(groupCode) || "BK9".equals(groupCode)) return true;
        String name = p.getPlaceName() == null ? "" : p.getPlaceName();
        String cat = p.getCategoryName() == null ? "" : p.getCategoryName();
        // 이름이 단독 일반명사(예: "공원", "광장")이면 블로그 수가 지역 전체를 합산해 노이즈 유발
        if (GENERIC_NAMES.contains(name.trim())) return true;
        for (String kw : EXCLUDED_KEYWORDS) {
            if (name.contains(kw) || cat.contains(kw)) return true;
        }
        return false;
    }

    /** 컨트롤러에서 DataLab 서비스 직접 접근용 */
    public NaverDataLabService getNaverDataLabService() { return naverDataLabService; }

    /** 1차 필터: 카테고리당 후보 상한 (카카오 카테고리 검색 최대치 = 45 = 15×3페이지) */
    private static final int TOP_PER_CATEGORY = 45;
    /** 2차 필터: 블로그 언급 최소 건수 */
    private static final int MIN_BLOG_COUNT   = 10_000;
    /** Naver Rate Limit 방지 딜레이 (ms) */
    private static final long NAVER_DELAY_MS  = 150;

    /** 대상 카테고리: 관광명소, 문화시설, 음식점, 카페, 대형마트(쇼핑) */
    private static final List<String> TARGET_CATEGORIES = List.of("AT4", "CT1", "FD6", "CE7", "MT1");
    /** Kakao 그룹코드로 못 잡는 분류(공원·쇼핑) 보강용 키워드 검색 */
    private static final List<String> SUB_KEYWORDS = List.of("공원", "전통시장", "아울렛");

    // ────────────────────────────────────────────────────────────
    // 메인 API
    // ────────────────────────────────────────────────────────────

    /**
     * 좌표 기반 장소 필터링
     *
     * @param lat          중심 위도
     * @param lng          중심 경도
     * @param radiusMeters 반경 (m)
     * @param categories   null이면 전체 카테고리
     * @return finalScore 내림차순 PlaceCandidate 리스트
     */
    /** 서브지역별 카카오 카테고리 검색 반경 (서브지역은 좁게) */
    private static final int SUB_RADIUS = 4000;
    /** 서브지역 검색 시 카테고리당 상한 (좌표가 여럿이라 작게) */
    private static final int SUB_PER_CATEGORY = 15;
    /** 블로그 호출 상한 (극단적 후보 수 방어). 병렬 처리라 넉넉 */
    private static final int MAX_BLOG = 600;

    /** 블로그 병렬 조회 전용 풀. 네이버 rate limit(429) 때문에 동시 3으로 제한 — 8은 대량 429 */
    private static final java.util.concurrent.ExecutorService BLOG_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "blog-count");
                t.setDaemon(true);
                return t;
            });

    /** 구역(zone)별 카카오 수집 병렬 풀. 그리드 보강으로 구역이 최대 22개까지 늘어나
     *  순차 처리 시 구역당 최대 18회(5카테고리×3페이지+키워드3) HTTP 호출이 그대로 누적돼
     *  체감 대기시간의 대부분을 차지했음 — 카카오는 네이버보다 한도가 넉넉해 동시 8로 설정 */
    private static final java.util.concurrent.ExecutorService ZONE_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "zone-collect");
                t.setDaemon(true);
                return t;
            });

    public List<PlaceCandidate> filterByCoord(double lat, double lng,
                                               int radiusMeters,
                                               List<String> categories) {
        return filterByCoord(lat, lng, radiusMeters, categories, null);
    }

    /**
     * @param regionHint 호출부가 이미 지역명을 알고 있을 때(지역명 검색) 그대로 전달 —
     *                    좌표 역지오코딩(coordToSiGun)이 가끔 빈 결과를 반환해 서브지역
     *                    Seed가 통째로 누락되는 문제를 피한다. null이면 좌표로 자동 판정(Map 좌표 검색).
     */
    public List<PlaceCandidate> filterByCoord(double lat, double lng,
                                               int radiusMeters,
                                               List<String> categories,
                                               String regionHint) {
        return filterByCoord(lat, lng, radiusMeters, categories, regionHint, null);
    }

    /**
     * @param regionHint 호출부가 이미 지역명을 알고 있을 때(지역명 검색) 그대로 전달 —
     *                    좌표 역지오코딩(coordToSiGun)이 가끔 빈 결과를 반환해 서브지역
     *                    Seed가 통째로 누락되는 문제를 피한다. null이면 좌표로 자동 판정(Map 좌표 검색).
     * @param onZoneFound SSE 스트리밍용 — 구역 하나의 카카오 수집이 끝날 때마다(아직 미채점)
     *                    즉시 통지. null이면 무시(기존 동기 호출과 동일하게 동작).
     */
    public List<PlaceCandidate> filterByCoord(double lat, double lng,
                                               int radiusMeters,
                                               List<String> categories,
                                               String regionHint,
                                               java.util.function.Consumer<List<PlaceCandidate>> onZoneFound) {
        List<String> cats = (categories == null || categories.isEmpty())
                ? TARGET_CATEGORIES : categories;

        String regionPrefix = (regionHint != null && !regionHint.isBlank())
                ? regionHint : kakaoPlacesService.coordToSiGun(lat, lng);
        final String prefix = regionPrefix != null ? regionPrefix + " " : "";

        // 검색 구역(zone) 목록 구성: 입력좌표 + 역/터미널 + 서브지역 동네
        List<double[]> zones = buildZones(regionPrefix, lat, lng);
        boolean multi = zones.size() > 1;
        int perCat = multi ? SUB_PER_CATEGORY : TOP_PER_CATEGORY;
        int searchRadius = multi ? SUB_RADIUS : radiusMeters;
        log.info("[1차] 지역 '{}' 검색 구역 {} 곳", regionPrefix, zones.size());

        // 구역 가중치 없이 하나의 풀로 합산 — 해운대처럼 검색량 큰 동네를 인위적으로
        // 깎거나 영도처럼 작은 동네를 인위적으로 띄우지 않는다. 인기는 인기대로 반영.
        // 구역별 카카오 수집은 서로 독립적이라 병렬 처리 — 구역이 22개까지 늘어난 뒤로
        // 순차 처리(구역당 최대 18회 호출)가 체감 대기시간의 대부분을 차지했음.
        final int fPerCat = perCat, fSearchRadius = searchRadius;
        List<java.util.concurrent.CompletableFuture<List<PlaceCandidate>>> zoneFutures = new ArrayList<>();
        for (double[] z : zones) {
            java.util.concurrent.CompletableFuture<List<PlaceCandidate>> f =
                    java.util.concurrent.CompletableFuture.supplyAsync(
                            () -> collectZoneCandidates(z, cats, fPerCat, fSearchRadius), ZONE_POOL);
            if (onZoneFound != null) {
                f = f.whenComplete((list, err) -> {
                    if (list != null && !list.isEmpty()) onZoneFound.accept(list);
                });
            }
            zoneFutures.add(f);
        }

        // 전역 중복 제거: Kakao Place ID 우선, 이름 기반 보조
        Set<String> seenIds   = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        List<PlaceCandidate> pool = new ArrayList<>();
        for (var f : zoneFutures) {
            for (PlaceCandidate c : f.join()) {
                String placeId = c.getKakaoPlaceId();
                if (placeId != null && !placeId.isBlank() && !seenIds.add(placeId)) continue;
                if (!seenNames.add(dedupeKey(c.getName(), regionPrefix))) continue;
                pool.add(c);
            }
        }
        if (pool.isEmpty()) return List.of();

        // 주소 기반 필터: 검색 지역명이 주소에 포함된 장소만 유지
        // (인접 도시 장소가 반경 검색으로 섞이는 것을 차단)
        pool = addressFilter(pool, regionPrefix);

        // 블로그 count 병렬 조회
        // buildBlogQuery: 장소명에 이미 지역명이 포함된 경우 중복 추가 방지
        final String regionName = prefix.trim();
        int[] counts = new int[pool.size()];
        List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < pool.size(); i++) {
            final int idx = i;
            final String blogQuery = buildBlogQuery(regionName, pool.get(idx).getName());
            futures.add(java.util.concurrent.CompletableFuture.runAsync(
                    () -> counts[idx] = naverBlogService.getBlogCount(blogQuery), BLOG_POOL));
        }
        java.util.concurrent.CompletableFuture
                .allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();

        // 카테고리별 순위 산출
        Map<String, List<Integer>> idxBySub = new HashMap<>();
        int globalMaxCount = 0;
        for (int i = 0; i < pool.size(); i++) {
            if (counts[i] <= 0) continue;
            idxBySub.computeIfAbsent(pool.get(i).getSubCategory(), k -> new ArrayList<>()).add(i);
            globalMaxCount = Math.max(globalMaxCount, counts[i]);
        }
        double globalLogMax = Math.log10(globalMaxCount + 1.0);

        List<PlaceCandidate> result = new ArrayList<>();
        for (List<Integer> idxs : idxBySub.values()) {
            idxs.sort((a, b) -> Integer.compare(counts[b], counts[a]));
            int n = idxs.size();
            for (int rank = 0; rank < n; rank++) {
                int idx = idxs.get(rank);
                PlaceCandidate c = pool.get(idx);
                double norm = globalLogMax == 0 ? 0.0 : Math.log10(counts[idx] + 1.0) / globalLogMax * 100.0;
                c.setTrendScore(Math.round(norm * 10) / 10.0);
                c.setCategoryPercentile(Math.round((rank + 1) * 1000.0 / n) / 10.0);
                calcFinalScore(c);
                result.add(c);
            }
        }

        result.sort(Comparator.comparingDouble(PlaceCandidate::getFinalScore).reversed());
        log.info("[완료] 스코어링 {} 개 (구역 {} 곳)", result.size(), zones.size());
        return result;
    }

    /** 구역 1곳의 카카오 수집(카테고리 + 보강 키워드) — ZONE_POOL에서 구역별 병렬 실행 */
    private List<PlaceCandidate> collectZoneCandidates(double[] z, List<String> cats,
                                                         int perCat, int searchRadius) {
        List<PlaceCandidate> zonePool = new ArrayList<>();
        for (String cat : cats) {
            for (KakaoPlaceDto p : kakaoPlacesService.searchByCategory(z[0], z[1], searchRadius, cat)
                    .stream().limit(perCat).collect(Collectors.toList())) {
                if (isFranchise(p.getPlaceName())) continue;
                if (isExcludedPlace(p)) continue;
                zonePool.add(toCandidate(p, 0, classifySub(cat, p.getCategoryName())));
            }
        }
        // 그룹코드로 못 잡는 분류(공원/쇼핑) 보강 — 키워드 검색
        for (String kw : SUB_KEYWORDS) {
            for (KakaoPlaceDto p : kakaoPlacesService.searchByKeywordNear(kw, z[0], z[1], searchRadius)) {
                if (isFranchise(p.getPlaceName())) continue;
                if (isExcludedPlace(p)) continue;
                // resolveGroupCode 경유 — "공원앞빙수집"(카페)이 "공원" 키워드로 검색돼도 cafe로 올바르게 분류
                String code = resolveGroupCode(p);
                String sub = classifySub(code, p.getCategoryName());
                // 키워드가 "공원"인데 실제 분류가 park이 아니면(카페·음식점) 제외
                if ("공원".equals(kw) && !"park".equals(sub)) continue;
                zonePool.add(toCandidate(p, 0, sub));
            }
        }
        return zonePool;
    }

    /** 그리드 보강 간격(km) — 큐레이션된 서브지역 사이 빈 공간을 듬성듬성 채움.
     *  SUB_RADIUS(4km) 원끼리 살짝 겹치도록 spacing < 2*radius로 설정 */
    private static final double GRID_FILL_KM = 7.5;
    /** 구역 총 상한 — 그리드 보강이 API 호출을 무한정 늘리지 않도록 방어 */
    private static final int MAX_ZONES = 22;

    /**
     * 검색 구역 좌표 목록: 입력좌표 + 가장 가까운 역·터미널 + 서브지역 동네 + 빈 공간 그리드 보강.
     * 역/터미널을 항상 포함 → 출발 hub가 역이든 터미널이든 그 주변 장소가 후보에 들어감.
     *
     * 큐레이션된 서브지역 리스트만으로는 지역 안의 정해진 동네 몇 곳만 검색되어 지도에
     * 마커가 안 찍히는 빈 공간이 많았다 — 서브지역 커버 반경 안을 GRID_FILL_KM 간격
     * 격자로 듬성듬성 보강해 "검색 자체를 안 한 공간"을 줄인다 (커버리지 ≠ 인기도 컷).
     */
    private List<double[]> buildZones(String regionPrefix, double lat, double lng) {
        List<double[]> zones = new ArrayList<>();
        zones.add(new double[]{lat, lng});

        // 가장 가까운 KTX역·버스터미널 (30km 이내) 추가
        addNearestHub(zones, lat, lng,
                java.util.Arrays.stream(RegionSeedData.KTX_STATIONS)
                        .map(s -> new double[]{s.lat, s.lng}).collect(Collectors.toList()));
        addNearestHub(zones, lat, lng,
                java.util.Arrays.stream(RegionSeedData.BUS_TERMINALS)
                        .map(t -> new double[]{t.lat, t.lng}).collect(Collectors.toList()));

        // 서브지역 동네 좌표 (카카오 키워드 → 좌표)
        if (regionPrefix != null) {
            for (String kw : RegionSeedData.get(regionPrefix)) {
                kakaoPlacesService.searchByKeyword(kw)
                        .ifPresent(p -> zones.add(new double[]{p.getLat(), p.getLng()}));
            }
        }
        // 너무 가까운 구역 병합 (3km 이내 중복 제거)
        List<double[]> merged = mergeNearby(zones, 3.0);

        // 그리드 보강: 서브지역이 퍼진 범위(중심→가장 먼 구역 거리) 안을 격자로 채운다.
        // 담양처럼 서브지역이 좁게 몰린 지역은 extent가 작아 보강이 거의/전혀 추가되지 않는다.
        double extentKm = merged.stream()
                .mapToDouble(z -> haversineKm(lat, lng, z[0], z[1]))
                .max().orElse(0);
        if (extentKm > GRID_FILL_KM) {
            for (double[] g : generateGrid(lat, lng, extentKm, GRID_FILL_KM)) {
                if (merged.size() >= MAX_ZONES) break;
                boolean dup = merged.stream()
                        .anyMatch(m -> haversineKm(m[0], m[1], g[0], g[1]) < GRID_FILL_KM * 0.6);
                if (!dup) merged.add(g);
            }
        }
        return merged;
    }

    /** 중심 기준 반경 extentKm 안을 spacingKm 간격 정사각 격자로 채움 (원형으로 잘라냄) */
    private List<double[]> generateGrid(double centerLat, double centerLng, double extentKm, double spacingKm) {
        List<double[]> pts = new ArrayList<>();
        double kmPerDegLat = 111.0;
        double kmPerDegLng = 111.0 * Math.cos(Math.toRadians(centerLat));
        int steps = (int) Math.ceil(extentKm / spacingKm);
        for (int dy = -steps; dy <= steps; dy++) {
            for (int dx = -steps; dx <= steps; dx++) {
                double plat = centerLat + dy * spacingKm / kmPerDegLat;
                double plng = centerLng + dx * spacingKm / kmPerDegLng;
                if (haversineKm(centerLat, centerLng, plat, plng) <= extentKm) {
                    pts.add(new double[]{plat, plng});
                }
            }
        }
        return pts;
    }

    private List<double[]> mergeNearby(List<double[]> zones, double minKm) {
        List<double[]> merged = new ArrayList<>();
        for (double[] z : zones) {
            boolean dup = merged.stream().anyMatch(m -> haversineKm(m[0], m[1], z[0], z[1]) < minKm);
            if (!dup) merged.add(z);
        }
        return merged;
    }

    private void addNearestHub(List<double[]> zones, double lat, double lng, List<double[]> hubs) {
        double[] best = null;
        double min = 30.0;   // 30km 이내만
        for (double[] h : hubs) {
            double km = haversineKm(lat, lng, h[0], h[1]);
            if (km < min) { min = km; best = h; }
        }
        if (best != null) zones.add(best);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 이름 정규화 — 중복 제거용. 공백·괄호 제거 (지역 접두어는 호출부에서 처리) */
    private String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").replaceAll("[()]", "");
    }

    /** 지역 접두어까지 떼고 정규화 ("경주황리단길"/"황리단길" → 동일) */
    private String dedupeKey(String name, String regionPrefix) {
        String n = normalizeName(name);
        if (regionPrefix != null && !regionPrefix.isBlank()) {
            n = n.replace(normalizeName(regionPrefix), "");
        }
        return n;
    }

    /**
     * 지역명 → 중심 좌표 변환 (Kakao 키워드 검색).
     * 좌표 확보 후에는 {@code ScoredPlaceProvider.getScoredPlaces()} (캐시 경유)로
     * 넘겨 필터링한다 — filterByCoord()의 buildZones()가 RegionSeedData 기반 서브지역
     * 확장(서울 → 홍대/강남/...)을 이미 내부에서 처리하므로, 지역명 검색·좌표 검색·
     * 서브지역 Seed가 없는 행정구역(포항 등)까지 모두 동일한 3계층 캐시(L1 메모리 + L2 DB)를
     * 공유하게 된다.
     */
    public Optional<double[]> resolveRegionCenter(String regionName) {
        // REGION_META 있으면 도시 중심 좌표 사용 → 코스/지도/GPS 탭 캐시 키 통일
        RegionSeedData.RegionMeta meta = RegionSeedData.REGION_META.get(regionName);
        if (meta != null) return Optional.of(new double[]{meta.lat, meta.lng});
        // 미등록 도시는 카카오 검색으로 fallback
        return kakaoPlacesService.searchByKeyword(regionName)
                .map(p -> new double[]{p.getLat(), p.getLng()});
    }

    /**
     * 프론트에서 수집한 Kakao 장소 목록 → Naver 블로그 2차 필터 + finalScore 계산
     *
     * @param rawPlaces 프론트(Kakao JS SDK)에서 넘긴 장소 목록
     */
    public List<PlaceCandidate> filterRaw(List<PlaceFilterRequest.RawPlace> rawPlaces) {
        log.info("[filterRaw] 입력 {} 개", rawPlaces.size());
        List<PlaceCandidate> candidates = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean stopNaverSearch = new java.util.concurrent.atomic.AtomicBoolean(false);

        for (int i = 0; i < rawPlaces.size(); i++) {
            PlaceFilterRequest.RawPlace p = rawPlaces.get(i);
            if (isFranchise(p.getName())) continue;
            String rawCat = p.getCategoryName() == null ? "" : p.getCategoryName();
            String rawName = p.getName() == null ? "" : p.getName();
            if (EXCLUDED_KEYWORDS.stream().anyMatch(kw -> rawName.contains(kw) || rawCat.contains(kw))) continue;

            int count = naverBlogService.getBlogCount(p.getName(), stopNaverSearch);
            double score = naverBlogService.calcTrendScore(count);

            log.debug("  [{}/{}] {} → 블로그 {}건, score={}", i + 1, rawPlaces.size(), p.getName(), count, score);

            if (count >= MIN_BLOG_COUNT) {
                String catCode = p.getCategoryCode();
                int stay = KakaoPlacesService.DEFAULT_STAY_MINUTES.getOrDefault(catCode, 60);
                double accuracy = 1.0 / Math.max(1, p.getRank());

                PlaceCandidate c = PlaceCandidate.builder()
                        .kakaoPlaceId(p.getId())
                        .name(p.getName())
                        .categoryCode(catCode)
                        .categoryName(p.getCategoryName())
                        .subCategory(classifySub(catCode, p.getCategoryName()))
                        .lat(p.getLat())
                        .lng(p.getLng())
                        .address(p.getAddress())
                        .trendScore(score)
                        .kakaoAccuracy(accuracy)
                        .defaultStayMinutes(stay)
                        .build();
                calcFinalScore(c);
                candidates.add(c);
            }

            if (i < rawPlaces.size() - 1) {
                try { Thread.sleep(NAVER_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }

        candidates.sort(Comparator.comparingDouble(PlaceCandidate::getFinalScore).reversed());
        log.info("[filterRaw] 필터 후 {} 개", candidates.size());
        return candidates;
    }

    // ────────────────────────────────────────────────────────────
    // 내부 로직
    // ────────────────────────────────────────────────────────────

    /** Kakao 수집: 카테고리별 accuracy 상위 TOP_PER_CATEGORY 개 */
    private List<KakaoPlaceDto> collectKakaoPlaces(double lat, double lng,
                                                    int radius, List<String> cats) {
        Set<String> seenIds = new HashSet<>();
        List<KakaoPlaceDto> result = new ArrayList<>();
        int globalRank = 1;

        for (String cat : cats) {
            List<KakaoPlaceDto> places =
                    kakaoPlacesService.searchByCategory(lat, lng, radius, cat);

            // 상위 TOP_PER_CATEGORY 개만 사용
            List<KakaoPlaceDto> top = places.stream()
                    .limit(TOP_PER_CATEGORY)
                    .collect(Collectors.toList());

            for (KakaoPlaceDto p : top) {
                if (seenIds.add(p.getId())) {
                    p.setRank(globalRank++);
                    result.add(p);
                }
            }
        }
        return result;
    }

    /** Naver 블로그 count 순차 조회 → MIN_BLOG_COUNT 이상만 PlaceCandidate 변환 */
    private List<PlaceCandidate> naverFilter(List<KakaoPlaceDto> places) {
        List<PlaceCandidate> passed = new ArrayList<>();
        int total = places.size();
        java.util.concurrent.atomic.AtomicBoolean stopNaverSearch = new java.util.concurrent.atomic.AtomicBoolean(false);

        for (int i = 0; i < total; i++) {
            KakaoPlaceDto p = places.get(i);

            int count = naverBlogService.getBlogCount(p.getPlaceName(), stopNaverSearch);
            double score = naverBlogService.calcTrendScore(count);

            log.debug("  [{}/{}] {} → 블로그 {}건, score={}", i + 1, total, p.getPlaceName(), count, score);

            if (count >= MIN_BLOG_COUNT) {
                passed.add(toCandidate(p, score));
            }

            // Rate Limit 방지 (마지막 아이템은 딜레이 불필요)
            if (i < total - 1) {
                try { Thread.sleep(NAVER_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        return passed;
    }

    /**
     * categoryGroupCode가 "" 인 경우 categoryGroupName으로 보완.
     * Kakao API는 간혹 groupCode를 비워두고 groupName만 채우는 경우가 있음.
     */
    private static String resolveGroupCode(KakaoPlaceDto dto) {
        String code = dto.getCategoryGroupCode();
        if (code != null && !code.isBlank()) return code;
        String name = dto.getCategoryGroupName();
        if (name == null) return "";
        return switch (name.trim()) {
            case "카페" -> "CE7";
            case "음식점" -> "FD6";
            case "대형마트" -> "MT1";
            case "문화시설" -> "CT1";
            case "관광명소" -> "AT4";
            default -> "";
        };
    }

    /** KakaoPlaceDto → PlaceCandidate 변환 */
    private PlaceCandidate toCandidate(KakaoPlaceDto dto, double trendScore) {
        String code = resolveGroupCode(dto);
        return toCandidate(dto, trendScore, classifySub(code, dto.getCategoryName()));
    }

    private PlaceCandidate toCandidate(KakaoPlaceDto dto, double trendScore, String subCategory) {
        String catCode = dto.getCategoryGroupCode();
        int stay = catCode == null ? 60 : KakaoPlacesService.DEFAULT_STAY_MINUTES.getOrDefault(catCode, 60);

        // 인기 보너스 0~100 스케일 통일 (TourAPI popBonus와 같은 의미) — rank1=100, 2=50, 3=33…
        double bonus = Math.min(100.0, 100.0 / Math.max(1, dto.getRank()));

        return PlaceCandidate.builder()
                .kakaoPlaceId(dto.getId())
                .name(dto.getPlaceName())
                .categoryCode(catCode)
                .categoryName(dto.getCategoryName() != null && !dto.getCategoryName().isBlank()
                        ? dto.getCategoryName()
                        : dto.getCategoryGroupName())
                .subCategory(subCategory)
                .lat(dto.getLat())
                .lng(dto.getLng())
                .address(dto.getAddressName())
                .trendScore(trendScore)
                .kakaoAccuracy(bonus)
                .defaultStayMinutes(stay)
                .build();
    }

    /**
     * Kakao 그룹코드 + category_name 텍스트로 프론트 7분류 결정.
     *
     * 그룹코드가 명확한 카테고리(FD6=음식점, CE7=카페, MT1=대형마트)는
     * 그룹코드를 FIRST로 체크해 category_name 키워드 오분류를 막는다.
     * (예: "공원카페"라는 이름의 CE7 카페를 "park"로 잘못 분류하는 것 방지)
     */
    private static String classifySub(String groupCode, String categoryName) {
        // 1. 명확한 그룹코드 우선 처리
        if ("FD6".equals(groupCode)) return "food";
        if ("CE7".equals(groupCode)) return "cafe";
        if ("MT1".equals(groupCode)) return "shop";
        if ("CT1".equals(groupCode)) return "culture";

        // 2. AT4(관광명소) + 빈 groupCode는 category_name으로 세분류
        String n = categoryName == null ? "" : categoryName;
        if (n.contains("공원")) return "park";
        if (n.contains("쇼핑") || n.contains("시장") || n.contains("아울렛") || n.contains("백화점")) return "shop";
        if (n.contains("전망대") || n.contains("출렁다리") || n.contains("포토")) return "photo";
        if (n.contains("카페") || n.contains("디저트")) return "cafe";
        if (n.contains("음식") || n.contains("식당") || n.contains("맛집")) return "food";
        return "sight";
    }

    /**
     * finalScore = trendScore × catWeight × 0.8 + accuracyBonus × 0.2
     *
     * catWeight 결정 우선순위:
     *   1. Naver Local API 교차검증 카테고리 (제목 매칭 성공 시)
     *   2. Kakao 카테고리 기반 subCategory (UNKNOWN 또는 미매칭 시)
     *
     * 목적: 네이버 블로그 생태계에서 음식점/카페가 관광명소보다 발행량이 압도적으로 많아
     *       blog count 기반 점수에서 여행지가 과소평가되는 현상 보정.
     */
    private void calcFinalScore(PlaceCandidate c) {
        double bonus = Math.min(100.0, c.getKakaoAccuracy());
        double catWeight = resolveWeight(c);
        double final_ = Math.min(100.0, c.getTrendScore() * catWeight) * 0.8 + bonus * 0.2;
        c.setFinalScore(Math.round(final_ * 10) / 10.0);
    }

    private static double resolveWeight(PlaceCandidate c) {
        return subCategoryWeight(c.getSubCategory());
    }

    private static double subCategoryWeight(String sub) {
        if (sub == null) return 1.0;
        return switch (sub) {
            case "sight", "culture", "photo" -> 1.3;
            case "park" -> 0.9;
            case "food"    -> 0.6;
            case "cafe"    -> 0.65;
            case "shop"    -> 0.8;
            default        -> 1.0;
        };
    }

    /**
     * 블로그 검색 쿼리 생성.
     * 장소명에 이미 지역명이 포함된 경우(예: "부산역") 중복 추가하지 않음.
     */
    private static String buildBlogQuery(String regionName, String placeName) {
        if (regionName == null || regionName.isBlank()) return placeName;
        if (placeName.contains(regionName)) return placeName;
        return regionName + " " + placeName;
    }

    /**
     * 주소 기반 필터 — 인접 도시 장소 차단.
     *
     * Kakao addressName은 "부산광역시 해운대구..." 형태로 시도·시군구가 포함되므로
     * regionPrefix("부산", "경주" 등)를 포함하는 주소만 통과.
     * 주소가 없는 장소는 통과(방어적 허용).
     *
     * CROSS_BOUNDARY_ALLOWED: 행정경계를 넘는 관광지(남이섬=가평군 등)는 예외로 허용.
     */
    private static final Map<String, List<String>> CROSS_BOUNDARY_ALLOWED = Map.of(
            "춘천", List.of("가평"),            // 남이섬 (경기도 가평군)
            "속초", List.of("고성", "양양"),     // 설악산 (속초·고성·양양 걸침)
            "여수", List.of("고흥", "순천"),      // 여수 인근 관광지
            "통영", List.of("고성"),             // 통영 인근 고성군
            "하동", List.of("광양", "구례")       // 섬진강 일대 (광양·구례 걸침)
    );

    private List<PlaceCandidate> addressFilter(List<PlaceCandidate> candidates, String regionPrefix) {
        if (regionPrefix == null || regionPrefix.isBlank()) return candidates;

        List<String> allowed = new ArrayList<>();
        allowed.add(regionPrefix);
        List<String> extras = CROSS_BOUNDARY_ALLOWED.get(regionPrefix);
        if (extras != null) allowed.addAll(extras);

        return candidates.stream()
                .filter(c -> {
                    String addr = c.getAddress();
                    if (addr == null || addr.isBlank()) return true;
                    return allowed.stream().anyMatch(addr::contains);
                })
                .toList();
    }
}
