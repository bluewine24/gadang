package com.gadang.external.region.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransportOption {
    private final String type;
    private final int oneWayMin;        // 편도 전체 소요 시간 (분) — originToHubMin + 구간 시간
    private final int fare;             // 편도 요금 (원)
    private final String fromHub;       // 출발 역/터미널 이름
    private final String toHub;         // 도착 역/터미널 이름
    private final int dailyTrips;       // 하루 운행 횟수 (-1 = 미제공)
    private final int originToHubMin;   // 현재위치 → fromHub 이동 시간 (분), 0이면 직통/미표시
    private final int originToHubFare;  // 현재위치 → fromHub 이동 요금 (원), 0이면 미표시
}
