package com.gadang.external.odsay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 열차 하루 운행 편수 조회.
 *
 * 1순위: 코레일(한국철도공사) 열차운행정보 API — 실제 당일 시간표 집계 (KorailTrainService)
 * 2순위: Odsay trainServiceTime API — 현재 키 등급에서 {} 반환, 사실상 미동작
 * 3순위: TrainFrequencyTable 정적 테이블 (코레일 공식 시간표 기반)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OdsayTrainService {

    @Qualifier("odsayRestClient")
    private final RestClient odsayRestClient;

    private final com.gadang.external.korail.KorailTrainService korailTrainService;

    @Value("${odsay.api.key}")
    private String apiKey;

    /** "서울역" → "서울" — Odsay 검색용 이름 정규화 */
    private static String normalize(String name) {
        return name.replaceAll("역$", "").trim();
    }

    /** 역명으로 Odsay 내부 station ID 조회 (stationClass=3: 기차역) */
    @Cacheable(value = "trainStation", key = "#stationName")
    @SuppressWarnings("unchecked")
    public Integer searchStationId(String stationName) {
        String query = normalize(stationName);
        try {
            Map<?, ?> resp = odsayRestClient.get()
                    .uri(u -> u.path("/searchStation")
                            .queryParam("stationName", query)
                            .queryParam("stationClass", 3)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (resp == null || resp.get("result") == null) return null;

            Object resultRaw = resp.get("result");
            Map<?, ?> result;
            if (resultRaw instanceof List) {
                List<?> list = (List<?>) resultRaw;
                if (list.isEmpty()) return null;
                result = (Map<?, ?>) list.get(0);
            } else if (resultRaw instanceof Map) {
                result = (Map<?, ?>) resultRaw;
            } else {
                return null;
            }

            List<?> stations = (List<?>) result.get("station");
            if (stations == null || stations.isEmpty()) return null;

            Object id = ((Map<?, ?>) stations.get(0)).get("stationID");
            return id instanceof Number ? ((Number) id).intValue() : null;

        } catch (Exception e) {
            log.warn("[Train/search] {} 검색 실패: {}", query, e.getMessage());
            return null;
        }
    }

    /**
     * KTX 하루 운행 편수 반환.
     * Odsay → TrainFrequencyTable 순으로 fallback.
     */
    @Cacheable(value = "trainServiceTime", key = "#fromStation + '->' + #toStation")
    @SuppressWarnings("unchecked")
    public int getDailyTrips(String fromStation, String toStation) {
        // 1. TAGO 실제 시간표
        int tagoKtx = korailTrainService.getTripCounts(fromStation, toStation).ktx().trips();
        if (tagoKtx > 0) {
            log.debug("[Train/TAGO] {} → {} : KTX 하루{}회", fromStation, toStation, tagoKtx);
            return tagoKtx;
        }

        // 2. Odsay trainServiceTime 시도
        try {
            Integer sid = searchStationId(fromStation);
            Integer eid = searchStationId(toStation);
            if (sid != null && eid != null) {
                final int sidF = sid, eidF = eid;
                Map<?, ?> resp = odsayRestClient.get()
                        .uri(u -> u.path("/trainServiceTime")
                                .queryParam("startStationID", sidF)
                                .queryParam("endStationID", eidF)
                                .queryParam("apiKey", apiKey)
                                .build())
                        .retrieve()
                        .body(Map.class);

                if (resp != null && resp.get("result") != null) {
                    Map<?, ?> result = (Map<?, ?>) resp.get("result");
                    List<?> station = (List<?>) result.get("station");
                    if (station != null && !station.isEmpty()) {
                        int trips = station.size();
                        log.debug("[Train/Odsay] {} → {} : 하루{}회", fromStation, toStation, trips);
                        return trips;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Train/Odsay] {} → {} 실패: {}", fromStation, toStation, e.getMessage());
        }

        // 2. 정적 테이블 fallback
        int ktx = TrainFrequencyTable.lookupKtx(fromStation, toStation);
        if (ktx > 0) {
            log.debug("[Train/Table] {} → {} : KTX 하루{}회", fromStation, toStation, ktx);
            return ktx;
        }

        int mu = TrainFrequencyTable.lookupMugungwha(fromStation, toStation);
        if (mu > 0) {
            log.debug("[Train/Table] {} → {} : 무궁화 하루{}회", fromStation, toStation, mu);
            return mu;
        }

        log.debug("[Train] {} → {} : 편수 없음", fromStation, toStation);
        return -1;
    }

    /**
     * 무궁화 하루 운행 편수 반환 (TAGO → 정적 테이블).
     */
    public int getMugungwhaTrips(String fromStation, String toStation) {
        int tago = korailTrainService.getTripCounts(fromStation, toStation).mugungwha().trips();
        if (tago > 0) {
            log.debug("[무궁화/TAGO] {} → {} : 하루{}회", fromStation, toStation, tago);
            return tago;
        }
        int trips = TrainFrequencyTable.lookupMugungwha(fromStation, toStation);
        log.debug("[무궁화/Table] {} → {} : 하루{}회", fromStation, toStation, trips);
        return trips;
    }

    /**
     * ITX-청춘 하루 운행 편수 반환 (TAGO → 정적 테이블).
     */
    public int getItxTrips(String fromStation, String toStation) {
        int tago = korailTrainService.getTripCounts(fromStation, toStation).itx().trips();
        if (tago > 0) {
            log.debug("[ITX/TAGO] {} → {} : 하루{}회", fromStation, toStation, tago);
            return tago;
        }
        int trips = TrainFrequencyTable.lookupItx(fromStation, toStation);
        log.debug("[ITX/Table] {} → {} : 하루{}회", fromStation, toStation, trips);
        return trips;
    }
}
