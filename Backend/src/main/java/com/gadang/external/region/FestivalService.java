package com.gadang.external.region;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadang.external.region.dto.FestivalDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalService {

    @Qualifier("tourRestClient")
    private final RestClient tourRestClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tour.api.key}")
    private String serviceKey;

    private static final Map<String, String> REGION_AREA_CODE = Map.ofEntries(
            Map.entry("서울", "1"),
            Map.entry("인천", "2"),
            Map.entry("대전", "3"),
            Map.entry("대구", "4"),
            Map.entry("광주", "5"),
            Map.entry("부산", "6"),
            Map.entry("울산", "7"),
            Map.entry("세종", "8"),
            Map.entry("경기", "31"),
            Map.entry("수원", "31"),
            Map.entry("강원", "32"),
            Map.entry("강릉", "32"),
            Map.entry("춘천", "32"),
            Map.entry("충북", "33"),
            Map.entry("충남", "34"),
            Map.entry("아산", "34"),
            Map.entry("경북", "35"),
            Map.entry("경주", "35"),
            Map.entry("포항", "35"),
            Map.entry("경남", "36"),
            Map.entry("창원", "36"),
            Map.entry("전북", "37"),
            Map.entry("전주", "37"),
            Map.entry("전남", "38"),
            Map.entry("여수", "38"),
            Map.entry("제주", "39")
    );

    @Cacheable(value = "festivals", key = "#region")
    public List<FestivalDto> fetchFestivals(String region) {
        try {
            // searchFestival2의 areaCode 필터가 신뢰할 수 없어(대부분 0건 반환) →
            // 한 달 전부터 시작한 진행중 축제를 전국으로 받아 주소(addr1)로 직접 필터링
            String from = LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            String body = tourRestClient.get()
                    .uri(u -> u.path("/searchFestival2")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "GaDang")
                            .queryParam("eventStartDate", from)
                            .queryParam("numOfRows", 300)
                            .queryParam("arrange", "P")
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode items = objectMapper.readTree(body)
                    .path("response").path("body").path("items").path("item");

            List<FestivalDto> result = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String addr = item.path("addr1").asText("");
                    if (region != null && !region.isBlank() && !addr.contains(region)) continue;
                    // 이미 끝난 축제 제외 (종료일이 오늘 이전)
                    String end = item.path("eventenddate").asText("");
                    if (!end.isBlank() && end.compareTo(today) < 0) continue;
                    result.add(new FestivalDto(
                            item.path("title").asText(""),
                            item.path("eventstartdate").asText(""),
                            item.path("eventenddate").asText(""),
                            addr,
                            item.path("firstimage").asText("")
                    ));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[Festival] fetchFestivals failed for '{}': {}", region, e.getMessage());
            return List.of();
        }
    }

}
