package com.gadang.external.transit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 교통 노선 L2 캐시 행 (TRANSIT_ROUTE 테이블).
 * duration_min = -1 이면 "조회했으나 운행 없음" (부정 캐시 — 같은 노선 재조회 방지)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitRoute {
    private String fromHub;     // 역명 or "ODSAY:터미널ID"
    private String toHub;
    private String type;        // KTX / 무궁화 / ITX-청춘 / 시외버스
    private int durationMin;
    private int fare;
    private int dailyTrips;
    private LocalDateTime updatedAt;
}
