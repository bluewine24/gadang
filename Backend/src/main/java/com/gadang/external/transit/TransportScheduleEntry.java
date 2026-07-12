package com.gadang.external.transit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * TRANSPORT_SCHEDULE 테이블 행.
 * 편별 출발·도착 시각을 저장한다.
 * - 기차: TAGO API의 depplandtime/arrplandtime에서 파싱, travel_date = 당일
 * - 버스: Odsay API의 startTime/endTime에서 파싱, travel_date = 1970-01-01 (정적)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportScheduleEntry {
    private Long id;
    private String fromHub;
    private String toHub;
    private String type;         // KTX / 무궁화 / ITX-청춘 / 시외버스
    private String depTime;      // HH:mm
    private String arrTime;      // HH:mm (null 허용)
    private int durationMin;
    private int fare;
    private String trainNo;      // 열차번호, 버스는 null
    private LocalDate travelDate;
    private LocalDateTime updatedAt;
}
