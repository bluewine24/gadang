package com.gadang.algorithm;

import java.util.*;

public final class RegionSeedData {

    private RegionSeedData() {}

    public static final Map<String, RegionMeta> REGION_META;

    static {
        Map<String, RegionMeta> m = new LinkedHashMap<>();
        //                  lat       lng       sido              transport  tags                                            blurb
        //                  hubLat    hubLng    hubToDestMin hubToDestFare
        m.put("서울",  new RegionMeta(37.5665, 126.9780, "서울특별시",   "지하철",
                List.of("궁궐", "한강", "야경"), "고궁·한강·핫플 밀집. 자기 동네 재발견",
                37.5541, 126.9707, 30, 1500, "서울역"));

        m.put("부산",  new RegionMeta(35.1796, 129.0756, "부산광역시",   "KTX",
                List.of("바다", "해수욕장", "야경"), "해운대·광안대교. 바다 감성 최강",
                35.1152, 129.0415, 40, 1700, "부산역"));

        m.put("강릉",  new RegionMeta(37.7519, 128.8761, "강원특별자치도","KTX",
                List.of("바다", "카페거리", "드라이브"), "경포 바다 + 안목 커피거리. 당일치기 인기 1위",
                37.7645, 128.8996, 15, 1000, "강릉역"));

        m.put("경주",  new RegionMeta(35.8562, 129.2247, "경상북도",     "KTX",
                List.of("황리단길", "유적", "야경"), "황리단길·불국사. 야간 유적 조명이 압도적",
                35.7984, 129.1390, 30, 1500, "신경주역"));

        m.put("전주",  new RegionMeta(35.8242, 127.1480, "전라북도",     "KTX",
                List.of("한옥", "맛집", "전통"), "한옥 골목 + 가맥집. 먹거리 끝판왕",
                35.8499, 127.1618, 20, 1000, "전주역"));

        m.put("여수",  new RegionMeta(34.7604, 127.6622, "전라남도",     "KTX",
                List.of("바다", "야경", "케이블카"), "밤바다 야경 + 케이블카. 낭만 1위",
                34.7531, 127.7486, 20, 1000, "여수엑스포역"));

        m.put("인천",  new RegionMeta(37.4563, 126.7052, "인천광역시",   "지하철",
                List.of("근대건축", "차이나타운", "도보"), "걸어서 도는 근대 거리 + 월미도 노을",
                37.4742, 126.6161, 30, 1500, "인천역"));

        m.put("춘천",  new RegionMeta(37.8747, 127.7342, "강원특별자치도","ITX",
                List.of("호수", "닭갈비", "레일바이크"), "의암호 자전거길 + 명동 닭갈비골목",
                37.8845, 127.7167, 20, 1000, "춘천역"));

        m.put("속초",  new RegionMeta(38.2070, 128.5919, "강원특별자치도","버스",
                List.of("바다", "설악산", "아바이"), "청초호 뷰 + 설악산 단풍. 서울서 2.5시간",
                38.1905, 128.5988, 15, 800, "속초버스터미널"));

        m.put("대구",  new RegionMeta(35.8714, 128.6014, "대구광역시",   "KTX",
                List.of("동성로", "근대골목", "치맥"), "근대골목 + 치맥 페스티벌 도시",
                35.8793, 128.6284, 20, 1500, "동대구역"));

        m.put("광주",  new RegionMeta(35.1595, 126.8526, "광주광역시",   "KTX",
                List.of("5·18", "맛집", "문화전당"), "예향 광주. 국립아시아문화전당 + 남도 음식",
                35.1377, 126.7908, 30, 1500, "광주송정역"));

        m.put("통영",  new RegionMeta(34.8544, 128.4330, "경상남도",     "버스",
                List.of("바다", "케이블카", "굴"), "한국의 나폴리. 케이블카 뷰 + 굴 해산물",
                34.8851, 128.4169, 15, 800, "통영버스터미널"));

        m.put("순천",  new RegionMeta(34.9506, 127.4878, "전라남도",     "KTX",
                List.of("순천만", "낙안읍성", "국가정원"), "순천만 국가정원 + 낙안읍성. 생태 당일치기 1순위",
                34.9458, 127.5031, 15, 800, "순천역"));

        m.put("담양",  new RegionMeta(35.3213, 126.9880, "전라남도",     "버스",
                List.of("죽녹원", "메타세쿼이아", "카페"), "대나무 숲길 + 메타세쿼이아 가로수길",
                35.3152, 126.9838, 10, 700, "담양버스터미널"));

        m.put("안동",  new RegionMeta(36.5684, 128.7294, "경상북도",     "KTX",
                List.of("하회마을", "찜닭", "탈춤"), "하회마을 유네스코 + 안동찜닭골목",
                36.5745, 128.6749, 20, 1000, "안동역"));

        m.put("제주",  new RegionMeta(33.4996, 126.5312, "제주특별자치도","비행기",
                List.of("한라산", "오름", "해변"), "성산일출봉·협재해변. 비행기 필요 지역",
                33.5068, 126.4927, 30, 2000, "제주공항"));

        REGION_META = Collections.unmodifiableMap(m);
    }

    public static class RegionMeta {
        public final double lat, lng;
        public final String sido, transport, blurb, hubName;
        public final List<String> tags;
        /** 목적지 교통 허브 좌표 (KTX역 / 공항 / 터미널) */
        public final double hubLat, hubLng;
        /** 허브 → 목적지 중심 편도 이동시간(분) */
        public final int hubToDestMin;
        /** 허브 → 목적지 중심 편도 요금(원) */
        public final int hubToDestFare;

        public RegionMeta(double lat, double lng, String sido, String transport,
                          List<String> tags, String blurb,
                          double hubLat, double hubLng, int hubToDestMin, int hubToDestFare,
                          String hubName) {
            this.lat = lat; this.lng = lng;
            this.sido = sido; this.transport = transport;
            this.tags = tags; this.blurb = blurb;
            this.hubLat = hubLat; this.hubLng = hubLng;
            this.hubToDestMin = hubToDestMin;
            this.hubToDestFare = hubToDestFare;
            this.hubName = hubName;
        }
    }

    public static class KtxStation {
        public final double lat, lng;
        public final String name;
        public KtxStation(double lat, double lng, String name) {
            this.lat = lat; this.lng = lng; this.name = name;
        }
    }

    // ── 전국 주요 KTX / ITX 역 ────────────────────────────────────
    public static final KtxStation[] KTX_STATIONS = {
        new KtxStation(37.5541, 126.9707, "서울역"),
        new KtxStation(37.4855, 127.1044, "수서역"),
        new KtxStation(36.7943, 127.1044, "천안아산역"),
        new KtxStation(36.6201, 127.3276, "오송역"),
        new KtxStation(36.3323, 127.4346, "대전역"),
        new KtxStation(36.1135, 128.1810, "김천구미역"),
        new KtxStation(35.8793, 128.6284, "동대구역"),
        new KtxStation(35.7984, 129.1390, "신경주역"),
        new KtxStation(35.5514, 129.1386, "울산역"),
        new KtxStation(35.1152, 129.0415, "부산역"),
        new KtxStation(35.1377, 126.7908, "광주송정역"),
        new KtxStation(35.8499, 127.1618, "전주역"),
        new KtxStation(34.9458, 127.5031, "순천역"),
        new KtxStation(34.7531, 127.7486, "여수엑스포역"),
        new KtxStation(37.7645, 128.8996, "강릉역"),
        new KtxStation(37.8845, 127.7167, "춘천역"),
        new KtxStation(36.5745, 128.6749, "안동역"),
        new KtxStation(37.5795, 128.5363, "진부(오대산)역"),  // 강릉선 — 평창 코스용
        new KtxStation(37.3443, 127.9519, "만종역"),          // 강릉선 — 원주/제천 코스용
    };

    // ── 전국 주요 공항 [lat, lng] ──────────────────────────────────
    public static final double[][] AIRPORTS = {
        {37.4492, 126.4509},  // 인천공항
        {37.5587, 126.8028},  // 김포공항
        {35.1725, 128.9468},  // 김해공항
        {34.8402, 127.6140},  // 여수공항
        {35.1399, 126.8107},  // 광주공항
        {35.8998, 128.6378},  // 대구공항
        {36.7220, 127.4959},  // 청주공항
        {33.5068, 126.4927},  // 제주공항
    };

    public static class BusTerminal {
        public final double lat, lng;
        public final String name;   // Odsay 터미널 검색에 사용할 이름
        public BusTerminal(double lat, double lng, String name) {
            this.lat = lat; this.lng = lng; this.name = name;
        }
    }

    // ── 전국 주요 버스터미널 ────────────────────────────────────────
    public static final BusTerminal[] BUS_TERMINALS = {
        new BusTerminal(37.5062, 127.0075, "서울고속버스터미널"),
        new BusTerminal(37.5346, 127.0942, "동서울터미널"),
        new BusTerminal(37.4843, 127.0164, "서울남부터미널"),
        new BusTerminal(35.2848, 129.0955, "부산종합버스터미널"),
        new BusTerminal(35.1632, 128.9825, "부산서부버스터미널"),
        new BusTerminal(35.8842, 128.5552, "대구북부터미널"),
        new BusTerminal(35.8801, 128.6286, "동대구터미널"),
        new BusTerminal(35.1604, 126.8793, "광주유스퀘어"),
        new BusTerminal(35.8345, 127.1292, "전주고속버스터미널"),
        new BusTerminal(34.9476, 127.4914, "순천종합버스터미널"),
        new BusTerminal(34.7582, 127.7170, "여수버스터미널"),
        new BusTerminal(38.1905, 128.5988, "속초버스터미널"),
        new BusTerminal(34.8851, 128.4169, "통영버스터미널"),
        new BusTerminal(35.3152, 126.9838, "담양버스터미널"),
        new BusTerminal(37.4377, 126.6975, "인천종합터미널"),
        new BusTerminal(35.5384, 129.3309, "울산고속버스터미널"),
        // ── 추가 터미널 (30km 내 허브 없는 지역 보완) ──
        new BusTerminal(35.9967, 129.3663, "포항고속버스터미널"),
        new BusTerminal(34.8974, 128.6256, "거제고속버스터미널"),
        new BusTerminal(35.1872, 128.0935, "진주고속버스터미널"),
        new BusTerminal(35.2580, 128.6521, "창원고속버스터미널"),
        new BusTerminal(34.8003, 126.3875, "목포공용버스터미널"),
        new BusTerminal(35.9714, 126.7117, "군산고속버스터미널"),
        new BusTerminal(36.7473, 126.2982, "태안버스터미널"),
        new BusTerminal(36.3274, 126.6098, "보령종합버스터미널"),
        new BusTerminal(37.1328, 128.1895, "제천고속버스터미널"),
        new BusTerminal(37.2731, 127.0441, "수원종합버스터미널"),
        new BusTerminal(37.3715, 128.3893, "평창버스터미널"),
        new BusTerminal(36.6378, 127.4821, "청주고속버스터미널"),
    };

    // ── 서브지역 ───────────────────────────────────────────────────
    public static final Map<String, List<String>> SUBREGIONS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("서울", List.of("서울 홍대", "서울 강남", "서울 명동", "서울 이태원", "서울 북촌한옥마을", "서울 인사동", "서울 성수동", "서울 한남동", "경복궁", "서울 동대문", "서울역"));
        m.put("부산", List.of("부산 해운대", "부산 광안리", "부산 남포동", "부산 서면", "부산 기장", "부산 송도해수욕장", "부산 감천문화마을", "부산 태종대", "부산 영도", "부산 자갈치시장", "부산 국제시장", "부산역"));
        m.put("경주", List.of("경주 황리단길", "경주 불국사", "경주 첨성대", "경주 보문단지", "경주 동궁과 월지", "경주 대릉원", "신경주역"));
        m.put("제주", List.of("제주 성산일출봉", "제주 협재해수욕장", "제주시내", "제주 서귀포", "제주 우도", "제주 한라산", "제주 비자림", "제주공항"));
        m.put("강릉", List.of("강릉 카페거리", "강릉 경포대", "강릉 안목해변", "강릉 오죽헌", "강릉 정동진", "강릉역"));
        m.put("전주", List.of("전주 한옥마을", "전주 남부시장", "전주 전동성당", "전주 덕진공원", "전주역"));
        m.put("인천", List.of("인천 차이나타운", "인천 송도", "인천 월미도", "강화도", "인천 을왕리해수욕장", "인천역"));
        m.put("여수", List.of("여수 밤바다", "여수 돌산도", "여수 오동도", "여수 향일암", "여수 케이블카", "여수엑스포역"));
        m.put("대구", List.of("대구 동성로", "대구 수성못", "대구 앞산", "대구 근대골목", "대구 달성공원", "동대구역"));
        m.put("광주", List.of("광주 양림동", "광주 국립아시아문화전당", "광주 무등산", "광주 충장로", "광주송정역"));
        m.put("속초", List.of("속초 영랑호", "속초 청초호", "속초 해수욕장", "속초 아바이마을", "설악산", "속초버스터미널"));
        m.put("춘천", List.of("춘천 남이섬", "춘천 의암호", "춘천 김유정역", "춘천역"));
        m.put("통영", List.of("통영 동피랑", "통영 미륵도", "통영 케이블카", "통영 한려수도", "통영버스터미널"));
        m.put("순천", List.of("순천만 국가정원", "순천만 습지", "낙안읍성", "순천 드라마촬영장", "순천 조계산", "순천역"));
        m.put("담양", List.of("담양 죽녹원", "담양 메타세쿼이아길", "담양 소쇄원", "담양버스터미널"));
        m.put("안동", List.of("안동 하회마을", "안동 찜닭골목", "안동 월영교", "안동 봉정사", "안동역"));
        m.put("대전", List.of("대전 성심당", "대전 한밭수목원", "대전 엑스포과학공원", "대전 유성온천", "대전 원도심", "대전 계족산", "대전 보문산", "대전역"));
        m.put("울산", List.of("울산 태화강국가정원", "울산 간절곶", "울산 대왕암공원", "울산 장생포고래문화마을", "울산 영남알프스", "울산역"));
        m.put("수원", List.of("수원 화성", "수원 행궁동", "수원 광교호수공원", "수원 팔달문시장", "수원역"));
        m.put("포항", List.of("포항 구룡포", "포항 영일대해수욕장", "포항 호미곶", "포항 죽도시장", "포항 스페이스워크", "포항고속버스터미널"));
        m.put("거제", List.of("거제 외도보타니아", "거제 해금강", "거제 바람의언덕", "거제 지심도", "거제 학동해수욕장", "거제고속버스터미널"));
        m.put("남해", List.of("남해 독일마을", "남해 보리암", "남해 가천다랭이마을", "남해 상주은모래비치", "남해공용버스터미널"));
        m.put("하동", List.of("하동 쌍계사", "하동 화개장터", "하동 최참판댁", "하동 악양면", "하동버스터미널"));
        m.put("목포", List.of("목포 해상케이블카", "목포 1897개항문화거리", "목포 유달산", "목포 근대역사관", "목포공용버스터미널"));
        m.put("군산", List.of("군산 근대문화거리", "군산 이성당", "군산 선유도", "군산 내항", "군산고속버스터미널"));
        m.put("태안", List.of("태안 안면도", "태안 꽃지해수욕장", "태안 만리포해수욕장", "태안 청포대해수욕장", "태안버스터미널"));
        m.put("보령", List.of("보령 대천해수욕장", "보령 무창포해수욕장", "보령 성주산", "보령종합버스터미널"));
        m.put("청주", List.of("청주 성안길", "청주 청남대", "청주 상당산성", "청주 고인쇄박물관", "청주고속버스터미널"));
        m.put("제천", List.of("제천 청풍호", "제천 의림지", "제천 청풍문화재단지", "제천 박달재", "제천고속버스터미널"));
        m.put("진주", List.of("진주 진주성", "진주 촉석루", "진주 남강", "진주 비봉산", "진주고속버스터미널"));
        m.put("창원", List.of("창원 마산어시장", "창원 주남저수지", "창원 진해 여좌천", "창원 돝섬", "창원고속버스터미널"));
        m.put("평창", List.of("평창 대관령", "평창 이효석문화예술촌", "평창 월정사", "평창 알펜시아", "진부(오대산)역", "평창버스터미널"));
        m.put("__default__", List.of());
        SUBREGIONS = Collections.unmodifiableMap(m);
    }

    public static List<String> get(String region) {
        if (SUBREGIONS.containsKey(region)) return SUBREGIONS.get(region);
        for (Map.Entry<String, List<String>> e : SUBREGIONS.entrySet()) {
            if (!e.getKey().startsWith("__") && region.contains(e.getKey())) return e.getValue();
        }
        return List.of();
    }

    public static Set<String> regions() {
        Set<String> keys = new LinkedHashSet<>(SUBREGIONS.keySet());
        keys.remove("__default__");
        return Collections.unmodifiableSet(keys);
    }
}
