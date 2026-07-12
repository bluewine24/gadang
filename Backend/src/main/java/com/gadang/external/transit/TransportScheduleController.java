package com.gadang.external.transit;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

/**
 * 편별 시간표 조회 API.
 *
 * GET /api/transport/schedule?fromHub=서울역&toHub=신경주역&type=KTX
 *   → 당일 시간표 목록 (dep_time 순)
 *
 * GET /api/transport/schedule/all?fromHub=서울역&toHub=신경주역
 *   → 모든 교통 수단 시간표 (type+dep_time 순)
 *
 * 코스 탭에서 "어떤 열차/버스를 탈지" 선택할 때 사용.
 * 기차 TTL: 20시간 (당일 시간표), 버스 TTL: 6일 (정적 시간표).
 */
@RestController
@RequestMapping("/api/transport/schedule")
@RequiredArgsConstructor
public class TransportScheduleController {

    private final TransportScheduleMapper mapper;

    private static final Duration RAIL_TTL = Duration.ofHours(20);
    private static final Duration BUS_TTL  = Duration.ofDays(6);
    private static final LocalDate BUS_FIXED_DATE = LocalDate.of(1970, 1, 1);

    @GetMapping
    public List<TransportScheduleEntry> getSchedule(
            @RequestParam String fromHub,
            @RequestParam String toHub,
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        boolean isBus = "시외버스".equals(type);
        LocalDate travelDate = isBus ? BUS_FIXED_DATE : (date != null ? date : LocalDate.now());
        Duration ttl = isBus ? BUS_TTL : RAIL_TTL;

        return mapper.findFresh(fromHub, toHub, type, travelDate, LocalDateTime.now().minus(ttl));
    }

    @GetMapping("/all")
    public List<TransportScheduleEntry> getAllSchedule(
            @RequestParam String fromHub,
            @RequestParam String toHub,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        // 기차는 당일, 버스는 고정 날짜 — 두 번 조회해서 합침
        LocalDate trainDate = date != null ? date : LocalDate.now();
        LocalDateTime trainFresh = LocalDateTime.now().minus(RAIL_TTL);
        LocalDateTime busFresh   = LocalDateTime.now().minus(BUS_TTL);

        // 기차
        List<TransportScheduleEntry> results = new java.util.ArrayList<>(
                mapper.findAllTypes(fromHub, toHub, trainDate, trainFresh));
        // 버스 (별도 날짜)
        results.addAll(mapper.findAllTypes(fromHub, toHub, BUS_FIXED_DATE, busFresh));
        results.sort(java.util.Comparator.comparing(TransportScheduleEntry::getType)
                .thenComparing(TransportScheduleEntry::getDepTime));
        return results;
    }
}
