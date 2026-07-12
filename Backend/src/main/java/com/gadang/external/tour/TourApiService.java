package com.gadang.external.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadang.external.tour.dto.TourDetailDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiService {

    @Qualifier("tourRestClient")
    private final RestClient tourRestClient;

    private final com.gadang.external.transit.ExternalCacheMapper cacheMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tour.api.key}")
    private String serviceKey;

    private static final Map<String, Integer> CONTENT_TYPE = Map.of(
            "sight",   12,
            "culture", 14,
            "food",    39,
            "cafe",    39,
            "park",    28,
            "shop",    38,
            "photo",   12
    );

    /** 좌표기반 인기순 관광지/문화시설 — 검증된 후보 풀 (인기순 정렬, dist 포함) */
    public record TourPlace(String name, String category, String address,
                            double lat, double lng, int distMeters, int popRank) {}

    /**
     * 좌표 반경 내 인기순 장소 (contentTypeId: 12 관광지 / 14 문화시설 / 39 음식점).
     * arrange=P(인기순)로 받아 popRank(0=가장 인기)를 매긴다.
     */
    public List<TourPlace> locationBased(double lat, double lng, int radiusMeters,
                                         int contentTypeId, int limit) {
        List<TourPlace> out = new ArrayList<>();
        try {
            JsonNode body = parse(tourRestClient.get()
                    .uri(u -> u.path("/locationBasedList2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "GaDang")
                            .queryParam("mapX", lng)
                            .queryParam("mapY", lat)
                            .queryParam("radius", Math.min(radiusMeters, 20000))
                            .queryParam("contentTypeId", contentTypeId)
                            .queryParam("arrange", "P")
                            .queryParam("numOfRows", limit)
                            .queryParam("pageNo", 1)
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(String.class));

            JsonNode items = body.path("response").path("body").path("items").path("item");
            if (items.isArray()) {
                int rank = 0;
                for (JsonNode it : items) {
                    String name = it.path("title").asText("");
                    if (name.isBlank()) continue;
                    out.add(new TourPlace(
                            name,
                            String.valueOf(contentTypeId),
                            it.path("addr1").asText(""),
                            it.path("mapy").asDouble(lat),
                            it.path("mapx").asDouble(lng),
                            (int) it.path("dist").asDouble(0),
                            rank++));
                }
            }
        } catch (Exception e) {
            log.warn("[TourAPI] locationBased 실패 ({},{}) type{}: {}", lat, lng, contentTypeId, e.getMessage());
        }
        return out;
    }

    /**
     * 지역 대표 관광지 이미지 최대 4장 반환 (트렌드 높은 지역 카드 이미지 슬라이드용)
     * L1 Caffeine → L2 REGION_IMAGE(DB, TTL 30일) → L3 TourAPI (호출 후 DB 저장)
     */
    @Cacheable(value = "regionImages", key = "#regionName")
    public List<String> getRegionImages(String regionName) {
        // L2: DB에 신선한 행이 있으면 API 호출 없이 사용
        try {
            String cached = cacheMapper.findImages(regionName,
                    java.time.LocalDateTime.now().minusDays(30));
            if (cached != null) {
                return cached.isBlank() ? List.of() : List.of(cached.split("\n"));
            }
        } catch (Exception ignored) {}

        List<String> urls = new ArrayList<>();
        try {
            JsonNode body = parse(tourRestClient.get()
                    .uri(u -> u.path("/searchKeyword2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "GaDang")
                            .queryParam("keyword", regionName)
                            .queryParam("contentTypeId", 12)   // 관광지
                            .queryParam("arrange", "P")        // 인기순
                            .queryParam("numOfRows", 10)
                            .queryParam("pageNo", 1)
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(String.class));

            JsonNode items = body.path("response").path("body").path("items").path("item");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String img = item.path("firstimage").asText("");
                    if (!img.isBlank() && urls.size() < 4) urls.add(img);
                }
            }
        } catch (Exception e) {
            log.warn("[TourAPI] getRegionImages 실패 {}: {}", regionName, e.getMessage());
            return urls; // API 실패는 DB에 저장하지 않음 (다음 요청에서 재시도)
        }
        try {
            cacheMapper.upsertImages(regionName, String.join("\n", urls));
        } catch (Exception ignored) {}
        return urls;
    }

    @Cacheable(value = "tourDetail", key = "#name + '-' + #cat")
    public Optional<TourDetailDto> fetchDetail(String name, String cat) {
        try {
            int typeId = CONTENT_TYPE.getOrDefault(cat, 12);

            // 1) 키워드 검색 → contentId
            JsonNode searchBody = parse(tourRestClient.get()
                    .uri(u -> u.path("/searchKeyword2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "GaDang")
                            .queryParam("keyword", name)
                            .queryParam("contentTypeId", typeId)
                            .queryParam("numOfRows", 1)
                            .queryParam("pageNo", 1)
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(String.class));

            JsonNode firstItem = searchBody.path("response").path("body").path("items").path("item");
            if (!firstItem.isArray() || firstItem.isEmpty()) return Optional.empty();

            String contentId  = firstItem.get(0).path("contentid").asText();
            String firstImage = firstItem.get(0).path("firstimage").asText("");

            // 2) 공통정보 (overview)
            JsonNode commonBody = parse(tourRestClient.get()
                    .uri(u -> u.path("/detailCommon2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "GaDang")
                            .queryParam("contentId", contentId)
                            .queryParam("defaultYN", "Y")
                            .queryParam("overviewYN", "Y")
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(String.class));

            // 3) 이미지
            JsonNode imgBody = parse(tourRestClient.get()
                    .uri(u -> u.path("/detailImage2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "GaDang")
                            .queryParam("contentId", contentId)
                            .queryParam("imageYN", "Y")
                            .queryParam("numOfRows", 5)
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(String.class));

            // 4) 카테고리별 소개정보 (운영시간, 입장료 등)
            JsonNode introBody = parse(tourRestClient.get()
                    .uri(u -> u.path("/detailIntro2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "GaDang")
                            .queryParam("contentId", contentId)
                            .queryParam("contentTypeId", typeId)
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(String.class));

            JsonNode commonItem = unwrap(commonBody.path("response").path("body").path("items").path("item"));
            JsonNode imgItem    = imgBody.path("response").path("body").path("items").path("item");
            JsonNode introItem  = unwrap(introBody.path("response").path("body").path("items").path("item"));

            String img = firstImage;
            if (imgItem.isArray() && !imgItem.isEmpty()) {
                img = imgItem.get(0).path("originimgurl").asText(firstImage);
            }

            String homepage = strip(commonItem.path("homepage").asText(""));
            // <a href=...> 태그에서 URL만 추출
            if (homepage.contains("http")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\"\\s<>]+").matcher(homepage);
                homepage = m.find() ? m.group() : "";
            }

            return Optional.of(new TourDetailDto(
                    img,
                    strip(commonItem.path("overview").asText("")),
                    // 운영 (카테고리별 필드명 다름)
                    f(introItem, "usetime","usetimeculture","opentimefood","usetimeleports","opentime","usetimefestival"),
                    f(introItem, "usefee","usefeeleports","discountinfo","discountinfofood","discountinfofestival"),
                    f(introItem, "restdate","restdateculture","restdatefood","restdateleports","restdateshopping"),
                    f(introItem, "parking","parkingculture","parkingfood","parkingleports","parkingshopping"),
                    // 편의
                    homepage,
                    f(introItem, "infocenter","infocenterculture","infocenterfood","infocenterleports","infocentershopping"),
                    f(introItem, "chkpet","chkpetculture","chkpetleports","chkpetshopping"),
                    f(introItem, "chkbabycarriage","chkbabycarriageculture","chkbabycarriageleports","chkbabycarriageshopping"),
                    f(introItem, "chkcreditcard","chkcreditcardculture","chkcreditcardfood","chkcreditcardleports","chkcreditcardshopping"),
                    // 카테고리 특화
                    f(introItem, "firstmenu","treatmenu"),
                    strip(introItem.path("seat").asText("")),
                    f(introItem, "useseason"),
                    f(introItem, "expguide"),
                    f(introItem, "scale","scaleleports","scalefood","scaleshopping")
            ));

        } catch (Exception e) {
            log.warn("[TourAPI] fetchDetail failed for '{}': {} - {}", name, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode parse(String json) {
        try { return objectMapper.readTree(json); }
        catch (Exception e) { return objectMapper.createObjectNode(); }
    }

    private JsonNode unwrap(JsonNode node) {
        return node.isArray() && !node.isEmpty() ? node.get(0) : node;
    }

    private String field(JsonNode node, String... keys) {
        for (String k : keys) {
            String v = node.path(k).asText("");
            if (!v.isBlank()) return v;
        }
        return "";
    }

    // strip까지 포함한 단축 헬퍼
    private String f(JsonNode node, String... keys) {
        return strip(field(node, keys));
    }

    private String strip(String s) {
        return s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }
}
