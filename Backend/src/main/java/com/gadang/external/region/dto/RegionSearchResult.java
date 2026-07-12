package com.gadang.external.region.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 당일치기 가능 지역 검색 결과 DTO
 */
@Data
@Builder
public class RegionSearchResult {
    private String id;           // 지역 키 (예: "강릉")
    private String name;         // 표시 이름
    private String sido;         // 시도
    private String transport;    // 주요 교통수단
    private double trend;        // DataLab 트렌드 점수 (0~100)
    private int    roundTrip;    // 왕복 이동 시간 (분)
    private int    fare;         // 왕복 교통비 (원)
    private int    stay;         // 체류 가능 시간 (분)
    private List<String> tags;
    private String blurb;
    private boolean hot;
    private boolean self;
    private List<TransportOption> options; // 교통수단별 편도 옵션
    private List<String> images;           // 관광공사 대표 이미지 URL 목록
}
