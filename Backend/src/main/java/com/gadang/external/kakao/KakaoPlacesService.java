package com.gadang.external.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Kakao Local API — 카테고리/키워드 장소 검색
 *
 * 카테고리 코드:
 *   AT4 관광명소 | CT1 문화시설 | FD6 음식점 | CE7 카페
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPlacesService {

    @Qualifier("kakaoRestClient")
    private final RestClient kakaoRestClient;

    // 카테고리별 기본 체류 시간(분)
    public static final Map<String, Integer> DEFAULT_STAY_MINUTES = Map.of(
            "AT4", 90, "CT1", 60, "FD6", 60, "CE7", 30, "MT1", 60
    );

    /**
     * 반경 내 카테고리 검색 — 최대 45개 (15개 × 3페이지), accuracy 순
     *
     * @param lat          중심 위도
     * @param lng          중심 경도
     * @param radiusMeters 반경 (최대 20000)
     * @param categoryCode AT4 / CT1 / FD6 / CE7 …
     */
    public List<KakaoPlaceDto> searchByCategory(double lat, double lng,
                                                int radiusMeters, String categoryCode) {
        List<KakaoPlaceDto> all = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            List<KakaoPlaceDto> batch = fetchCategoryPage(lat, lng, radiusMeters, categoryCode, page);
            int startRank = all.size() + 1;
            for (int i = 0; i < batch.size(); i++) {
                KakaoPlaceDto dto = batch.get(i);
                dto.setRank(startRank + i);
            }
            all.addAll(batch);
            if (batch.size() < 15) break;
        }
        return all;
    }

    /**
     * 좌표 → 행정구역명 (시군구 + 읍면동)
     */
    public String coordToRegionName(double lat, double lng) {
        try {
            Map<?, ?> body = kakaoRestClient.get()
                    .uri(ub -> ub.path("/v2/local/geo/coord2address.json")
                            .queryParam("x", lng)
                            .queryParam("y", lat)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (body == null) return null;
            List<?> docs = (List<?>) body.get("documents");
            if (docs == null || docs.isEmpty()) return null;

            Map<?, ?> addr = (Map<?, ?>) ((Map<?, ?>) docs.get(0)).get("address");
            if (addr == null) return null;

            String region2 = str(addr, "region_2depth_name"); // 시군구
            String region3 = str(addr, "region_3depth_name"); // 읍면동
            String name = (region2 + " " + region3).trim();
            return name.isEmpty() ? null : name;

        } catch (Exception e) {
            log.warn("Kakao coord2address 실패 ({},{}): {}", lat, lng, e.getMessage());
            return null;
        }
    }

    /**
     * 좌표 → 시·군 이름 (지역 카드 단위).
     * 광역·특별시는 시 이름("부산광역시"→"부산"), 그 외는 시군 이름("광명시"→"광명", "전주시 덕진구"→"전주").
     * 역·터미널 좌표로 행정구역을 자동 판정해 수동 매핑 오류를 차단한다.
     */
    public String coordToSiGun(double lat, double lng) {
        String[] parts = coordToSidoSiGun(lat, lng);
        return parts != null ? parts[1] : null;
    }

    /** 시도 표기 정규화 — Kakao가 "전남"/"전북특별자치도" 등 변형으로 반환하는 것을 한 형태로 통일 */
    private static final Map<String, String> SIDO_CANONICAL = Map.ofEntries(
            Map.entry("서울", "서울특별시"),   Map.entry("부산", "부산광역시"),
            Map.entry("대구", "대구광역시"),   Map.entry("인천", "인천광역시"),
            Map.entry("광주", "광주광역시"),   Map.entry("대전", "대전광역시"),
            Map.entry("울산", "울산광역시"),   Map.entry("세종", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("강원", "강원특별자치도"), Map.entry("강원도", "강원특별자치도"),
            Map.entry("충북", "충청북도"),     Map.entry("충남", "충청남도"),
            Map.entry("전북", "전라북도"),     Map.entry("전북특별자치도", "전라북도"),
            Map.entry("전남", "전라남도"),
            Map.entry("경북", "경상북도"),     Map.entry("경남", "경상남도"),
            Map.entry("제주", "제주특별자치도"), Map.entry("제주도", "제주특별자치도")
    );

    public static String normalizeSido(String sido) {
        if (sido == null) return "";
        return SIDO_CANONICAL.getOrDefault(sido.trim(), sido.trim());
    }

    /**
     * 좌표 → [시도, 시·군] (예: ["전라남도", "여수"], ["부산광역시", "부산"]).
     */
    public String[] coordToSidoSiGun(double lat, double lng) {
        try {
            Map<?, ?> body = kakaoRestClient.get()
                    .uri(ub -> ub.path("/v2/local/geo/coord2address.json")
                            .queryParam("x", lng)
                            .queryParam("y", lat)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (body == null) return null;
            List<?> docs = (List<?>) body.get("documents");
            if (docs == null || docs.isEmpty()) return null;

            Map<?, ?> addr = (Map<?, ?>) ((Map<?, ?>) docs.get(0)).get("address");
            if (addr == null) return null;

            String d1 = str(addr, "region_1depth_name"); // 시도
            String d2 = str(addr, "region_2depth_name"); // 시군구

            String sido = normalizeSido(d1);
            if (sido.endsWith("광역시") || sido.endsWith("특별시") || sido.endsWith("특별자치시")) {
                String name = sido.replace("광역시", "").replace("특별시", "").replace("특별자치시", "");
                return new String[]{sido, name};
            }
            String siGun = d2.split(" ")[0]; // "전주시 덕진구" → "전주시"
            return new String[]{sido, siGun.replaceAll("[시군]$", "")};

        } catch (Exception e) {
            log.warn("Kakao coord2sigun 실패 ({},{}): {}", lat, lng, e.getMessage());
            return null;
        }
    }

    /**
     * 키워드 검색 — 지역 중심 좌표 획득용
     */
    public Optional<KakaoPlaceDto> searchByKeyword(String keyword) {
        try {
            Map<?, ?> body = kakaoRestClient.get()
                    .uri(ub -> ub.path("/v2/local/search/keyword.json")
                            .queryParam("query", keyword)
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (body == null) return Optional.empty();
            List<?> docs = (List<?>) body.get("documents");
            if (docs == null || docs.isEmpty()) return Optional.empty();

            Map<?, ?> first = (Map<?, ?>) docs.get(0);
            KakaoPlaceDto dto = new KakaoPlaceDto();
            dto.setPlaceName(str(first, "place_name"));
            dto.setAddressName(str(first, "address_name"));
            dto.setX(str(first, "x"));
            dto.setY(str(first, "y"));
            return Optional.of(dto);

        } catch (Exception e) {
            log.warn("Kakao 키워드 검색 실패 [{}]: {}", keyword, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 좌표 기반 키워드 검색 — 반경 내 거리순 (역·터미널 등 교통허브 동적 발견용).
     * 고정 좌표 목록에 없는 지역(신규 도시, 표기가 다른 터미널 등)도 누락 없이 찾기 위해 사용.
     */
    public List<KakaoPlaceDto> searchByKeywordNear(String keyword, double lat, double lng, int radiusMeters) {
        try {
            Map<?, ?> body = kakaoRestClient.get()
                    .uri(ub -> ub.path("/v2/local/search/keyword.json")
                            .queryParam("query", keyword)
                            .queryParam("x", lng)
                            .queryParam("y", lat)
                            .queryParam("radius", radiusMeters)
                            .queryParam("sort", "distance")
                            .queryParam("size", 15)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (body == null) return List.of();
            List<?> docs = (List<?>) body.get("documents");
            if (docs == null) return List.of();

            List<KakaoPlaceDto> result = new ArrayList<>();
            for (Object doc : docs) {
                Map<?, ?> m = (Map<?, ?>) doc;
                KakaoPlaceDto dto = new KakaoPlaceDto();
                dto.setId(str(m, "id"));
                dto.setPlaceName(str(m, "place_name"));
                dto.setCategoryGroupName(str(m, "category_group_name"));
                dto.setCategoryName(str(m, "category_name"));
                dto.setAddressName(str(m, "address_name"));
                dto.setRoadAddressName(str(m, "road_address_name"));
                dto.setX(str(m, "x"));
                dto.setY(str(m, "y"));
                dto.setDistance(str(m, "distance"));
                result.add(dto);
            }
            return result;

        } catch (Exception e) {
            log.warn("Kakao 근접 키워드 검색 실패 [{}] ({},{}): {}", keyword, lat, lng, e.getMessage());
            return List.of();
        }
    }

    // ────────────────────────────────────────
    private List<KakaoPlaceDto> fetchCategoryPage(double lat, double lng,
                                                   int radius, String category, int page) {
        try {
            Map<?, ?> body = kakaoRestClient.get()
                    .uri(ub -> ub.path("/v2/local/search/category.json")
                            .queryParam("category_group_code", category)
                            .queryParam("y", lat)
                            .queryParam("x", lng)
                            .queryParam("radius", radius)
                            .queryParam("sort", "accuracy")
                            .queryParam("size", 15)
                            .queryParam("page", page)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (body == null) return List.of();
            List<?> docs = (List<?>) body.get("documents");
            if (docs == null) return List.of();

            List<KakaoPlaceDto> result = new ArrayList<>();
            for (Object doc : docs) {
                Map<?, ?> m = (Map<?, ?>) doc;
                KakaoPlaceDto dto = new KakaoPlaceDto();
                dto.setId(str(m, "id"));
                dto.setPlaceName(str(m, "place_name"));
                dto.setCategoryGroupCode(str(m, "category_group_code"));
                dto.setCategoryGroupName(str(m, "category_group_name"));
                dto.setCategoryName(str(m, "category_name"));
                dto.setPhone(str(m, "phone"));
                dto.setAddressName(str(m, "address_name"));
                dto.setRoadAddressName(str(m, "road_address_name"));
                dto.setX(str(m, "x"));
                dto.setY(str(m, "y"));
                dto.setPlaceUrl(str(m, "place_url"));
                dto.setDistance(str(m, "distance"));
                result.add(dto);
            }
            return result;

        } catch (Exception e) {
            log.warn("Kakao 카테고리 검색 실패 [{}] page={}: {}", category, page, e.getMessage());
            return List.of();
        }
    }

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }
}
