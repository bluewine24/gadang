package com.gadang.external.odsay;

import java.util.HashMap;
import java.util.Map;

/**
 * Odsay trainServiceTime API 접근 불가 시 사용하는 하드코딩 열차 운행 편수 테이블.
 * 코레일 공식 시간표 기반 하루 편도 운행 횟수 (KTX + 무궁화 합산 기준).
 *
 * KTX 편수는 KTX 단독 편수이며, 무궁화는 별도 조회.
 * lookup(from, to) 는 KTX + SRT + 무궁화 합산 기준이므로
 * 수단별로 나누려면 KTX_ONLY, MU_ONLY 테이블을 각각 참조한다.
 */
public final class TrainFrequencyTable {

    /** KTX 편도 하루 편수 (코레일+SRT 합산, 주요 노선) */
    private static final Map<String, Integer> KTX = new HashMap<>();

    /** 무궁화/새마을 편도 하루 편수 */
    private static final Map<String, Integer> MUGUNGWHA = new HashMap<>();

    /** ITX-청춘 편도 하루 편수 */
    private static final Map<String, Integer> ITX = new HashMap<>();

    static {
        // ── KTX ────────────────────────────────────────────────
        // 경부선 (서울역 기준)
        k("서울역", "부산역",       80);
        k("서울역", "동대구역",      90);
        k("서울역", "신경주역",      45);
        k("서울역", "울산역",        35);
        k("서울역", "천안아산역",    110);

        // 호남선 (서울역/용산역 기준)
        k("서울역", "광주송정역",    40);
        k("용산역", "광주송정역",    40);
        k("서울역", "목포역",        20);
        k("용산역", "목포역",        20);
        k("서울역", "여수엑스포역",  12);
        k("용산역", "여수엑스포역",  12);
        k("서울역", "순천역",        10);
        k("용산역", "순천역",        10);

        // 전라선
        k("서울역", "전주역",        18);
        k("용산역", "전주역",        18);

        // 강릉선
        k("서울역", "강릉역",        15);
        k("청량리역", "강릉역",      12);

        // 중앙선·동해선
        k("서울역",   "안동역",       8);
        k("청량리역", "안동역",       8);

        // 경전선
        k("서울역", "창원중앙역",    18);

        // 지역 간
        k("부산역", "동대구역",      60);
        k("부산역", "광주송정역",    15);
        k("동대구역", "광주송정역",  10);

        // ── 무궁화/새마을 ──────────────────────────────────────
        // 경부선
        m("서울역", "부산역",        20);
        m("서울역", "동대구역",      25);
        m("서울역", "신경주역",      12);

        // 호남선
        m("용산역", "광주역",        18);
        m("용산역", "목포역",        10);
        m("용산역", "여수엑스포역",   8);
        m("용산역", "순천역",         8);

        // 전라선
        m("용산역", "전주역",        14);

        // 중앙선
        m("청량리역", "부전역",      10);

        // ── ITX-청춘 ───────────────────────────────────────────
        i("서울역",   "춘천역", 12);
        i("용산역",   "춘천역", 12);
        i("청량리역", "춘천역", 12);
    }

    private static void k(String a, String b, int trips) {
        KTX.put(a + "->" + b, trips);
        KTX.put(b + "->" + a, trips);
    }

    private static void m(String a, String b, int trips) {
        MUGUNGWHA.put(a + "->" + b, trips);
        MUGUNGWHA.put(b + "->" + a, trips);
    }

    private static void i(String a, String b, int trips) {
        ITX.put(a + "->" + b, trips);
        ITX.put(b + "->" + a, trips);
    }

    /** KTX 하루 운행 편수 조회 (-1 = 없음) */
    public static int lookupKtx(String from, String to) {
        return KTX.getOrDefault(from + "->" + to, -1);
    }

    /** 무궁화/새마을 하루 운행 편수 조회 (-1 = 없음) */
    public static int lookupMugungwha(String from, String to) {
        return MUGUNGWHA.getOrDefault(from + "->" + to, -1);
    }

    /** ITX-청춘 하루 운행 편수 조회 (-1 = 없음) */
    public static int lookupItx(String from, String to) {
        return ITX.getOrDefault(from + "->" + to, -1);
    }

    private TrainFrequencyTable() {}
}
