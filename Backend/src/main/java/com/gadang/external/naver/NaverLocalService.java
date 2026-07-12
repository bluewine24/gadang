package com.gadang.external.naver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 네이버 지역 검색 API — 장소 카테고리 교차검증
 *
 * Naver Local Search API는 장소명으로 검색하면 네이버가 자체 분류한 category를 반환한다.
 * 이를 Kakao 카테고리 분류의 교차검증으로 활용한다.
 *
 * 주의: API 결과가 동일 장소를 가리키는지 확인하기 위해 제목 유사도 매칭 필수.
 *       (예: "불국사" 검색 시 "불국사밀면"(식당)이 1위로 나올 수 있음)
 *
 * 카테고리 계층: "대분류,대분류2>소분류" 형태
 *   여행,명소     → TOURIST     weight 1.3
 *   문화,예술,교육 → CULTURE     weight 1.2
 *   스포츠,레저   → LEISURE     weight 1.1
 *   쇼핑,유통>시장 → MARKET      weight 1.0
 *   쇼핑,유통     → SHOP        weight 0.75
 *   음식점 / 한식 / 일식 등 → FOOD weight 0.6
 *   카페,디저트   → CAFE        weight 0.65
 *   교통,운수     → TRANSPORT   → 제외 후보
 *   기타          → null (Kakao 분류 fallback)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverLocalService {

    @Qualifier("naverRestClient")
    private final RestClient naverRestClient;

    public enum NaverCategory {
        TOURIST(1.3),
        CULTURE(1.2),
        LEISURE(1.1),
        MARKET(1.0),
        SHOP(0.75),
        FOOD(0.6),
        CAFE(0.65),
        TRANSPORT(-1.0),  // -1 = 필터 대상
        UNKNOWN(1.0);     // 분류 불명 → Kakao fallback

        public final double weight;
        NaverCategory(double w) { this.weight = w; }
    }

    /**
     * 장소명으로 Naver Local 검색 후 카테고리 분류 반환.
     * 제목 매칭 실패 시 UNKNOWN 반환 → 호출부에서 Kakao 카테고리 fallback.
     *
     * @param query      검색어 (예: "부산 해운대" 또는 "해운대")
     * @param placeName  원본 장소명 (제목 매칭용)
     */
    @Cacheable(value = "naverLocal", key = "#query")
    public NaverCategory getCategory(String query, String placeName) {
        try {
            Map<?, ?> body = fetchWithRetry(query);

            if (body == null) return NaverCategory.UNKNOWN;

            @SuppressWarnings("unchecked")
            List<Map<String, String>> items = (List<Map<String, String>>) body.get("items");
            if (items == null || items.isEmpty()) return NaverCategory.UNKNOWN;

            // 제목이 장소명과 충분히 겹치는 결과 탐색
            String normalPlaceName = normalize(placeName);
            for (Map<String, String> item : items) {
                String title = normalize(stripHtml(item.getOrDefault("title", "")));
                if (isTitleMatch(title, normalPlaceName)) {
                    String rawCat = item.getOrDefault("category", "");
                    NaverCategory cat = mapCategory(rawCat);
                    log.debug("[NaverLocal] '{}' → title='{}' cat='{}' → {}", query, title, rawCat, cat);
                    return cat;
                }
            }
            log.debug("[NaverLocal] '{}' — 제목 매칭 실패 (UNKNOWN)", query);
            return NaverCategory.UNKNOWN;

        } catch (Exception e) {
            log.warn("[NaverLocal] 조회 실패 [{}]: {}", query, e.getMessage());
            return NaverCategory.UNKNOWN;
        }
    }

    // ──────────────────────────────────────────────

    /** 429 Rate Limit 시 최대 2회 재시도 (100ms 간격) */
    private Map<?, ?> fetchWithRetry(String query) throws Exception {
        int attempts = 3;
        for (int i = 0; i < attempts; i++) {
            try {
                return naverRestClient
                        .get()
                        .uri(ub -> ub.path("/search/local.json")
                                .queryParam("query", query)
                                .queryParam("display", 3)
                                .build())
                        .retrieve()
                        .body(Map.class);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("429") && i < attempts - 1) {
                    Thread.sleep(150L * (i + 1));
                } else {
                    throw e;
                }
            }
        }
        return null;
    }

    private static String stripHtml(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", "");
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").replaceAll("[\\(\\)\\[\\]·\\-]", "").toLowerCase();
    }

    /**
     * 제목이 장소명과 일치하는지 확인.
     *
     * 규칙:
     *  1. 완전 일치 (공백·기호 제거 후)
     *  2. placeName이 title에 포함 (예: "자갈치시장" ⊂ "부산자갈치시장")
     *  3. title이 placeName에 포함 — 단, title이 너무 짧으면 가게이름에 장소명 들어간 경우
     *     (예: "광안리" ⊂ "무쇠김치삼겹 광안리점")를 걸러야 하므로
     *     title 길이가 placeName 길이의 70% 이상일 때만 허용
     *  4. LCS(최장 공통 부분 문자열) ≥ min(len) × 0.7
     */
    private static boolean isTitleMatch(String title, String placeName) {
        if (title.isEmpty() || placeName.isEmpty()) return false;
        // 1. 완전 일치
        if (title.equals(placeName)) return true;
        // 2. place가 title에 포함 (예: 부산자갈치시장 contains 자갈치시장)
        if (title.contains(placeName)) return true;
        // 3. title이 place에 포함 — title이 place의 70% 이상 길어야 허용
        if (placeName.contains(title) && title.length() >= placeName.length() * 0.7) return true;
        // 4. LCS 기반 유사도 (더 엄격하게 0.7)
        int lcs = longestCommonSubstring(title, placeName);
        int minLen = Math.min(title.length(), placeName.length());
        return minLen >= 3 && lcs >= minLen * 0.7;
    }

    private static int longestCommonSubstring(String a, String b) {
        int max = 0;
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    max = Math.max(max, dp[i][j]);
                }
            }
        }
        return max;
    }

    private static NaverCategory mapCategory(String raw) {
        if (raw == null || raw.isBlank()) return NaverCategory.UNKNOWN;
        String r = raw.toLowerCase();

        // 여행/명소 계열 — 동네 공원은 TOURIST가 아닌 LEISURE로 처리
        if (r.contains("근린공원") || r.contains("어린이공원") || r.contains("소공원")
                || r.contains("지구공원") || r.contains("도시공원") || r.contains("체육공원")) return NaverCategory.LEISURE;
        if (r.startsWith("여행") || r.contains("명소") || r.contains("관광")) return NaverCategory.TOURIST;
        // 종교/사찰/성당/교회 — 여행 명소로 분류
        if (r.startsWith("불교") || r.contains("절") || r.contains("사찰") || r.contains("성당")
                || r.contains("교회") || r.startsWith("종교")) return NaverCategory.CULTURE;
        // 문화/예술 (영화관 포함: 영화의전당 같은 문화예술센터 vs CGV는 이미 EXCLUDED_KEYWORDS로 제거됨)
        if (r.startsWith("문화") || r.contains("박물관") || r.contains("미술관")
                || r.contains("공연") || r.contains("전시") || r.contains("영화관")) return NaverCategory.CULTURE;
        // 스포츠/레저
        if (r.startsWith("스포츠") || r.contains("레저") || r.contains("해수욕장")
                || r.contains("스키") || r.contains("골프")) return NaverCategory.LEISURE;
        // 시장 (전통시장은 여행 가치 있음)
        if (r.contains("시장")) return NaverCategory.MARKET;
        // 쇼핑
        if (r.startsWith("쇼핑") || r.contains("유통")) return NaverCategory.SHOP;
        // 카페/디저트
        if (r.startsWith("카페") || r.contains("디저트") || r.contains("빙수") || r.contains("베이커리")) return NaverCategory.CAFE;
        // 음식점 계열
        if (r.startsWith("음식점") || r.startsWith("한식") || r.startsWith("중식") || r.startsWith("일식")
                || r.startsWith("양식") || r.startsWith("분식") || r.startsWith("패스트푸드")
                || r.startsWith("치킨") || r.startsWith("피자") || r.startsWith("육류")
                || r.startsWith("술집") || r.startsWith("주점")) return NaverCategory.FOOD;
        // 교통/운수 → 제외
        if (r.startsWith("교통") || r.contains("지하철") || r.contains("버스") || r.contains("기차")) return NaverCategory.TRANSPORT;
        // 의료/병원 → 제외
        if (r.startsWith("의료") || r.contains("병원") || r.contains("약국") || r.contains("클리닉")
                || r.contains("한의원")) return NaverCategory.TRANSPORT;

        return NaverCategory.UNKNOWN;
    }
}
