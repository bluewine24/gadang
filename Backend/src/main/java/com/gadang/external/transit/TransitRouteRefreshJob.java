package com.gadang.external.transit;

import com.gadang.algorithm.RegionSeedData;
import com.gadang.external.korail.KorailTrainService;
import com.gadang.external.odsay.OdsayBusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TRANSIT_ROUTE(L2) + TRANSPORT_SCHEDULE 주기 갱신 배치.
 *
 * 우선순위:
 *   1) Seed 데이터(KTX_STATIONS / BUS_TERMINALS)에 정의된 노선 중 DB에 없는 것 (초기 워밍)
 *   2) DB에 있지만 오래된 것 (오래된 순)
 *
 * 쿼터 보호:
 *   기차(코레일, 일 10,000건): RAIL_DAILY_LIMIT 쌍/일
 *   버스(Odsay, 일 1,000건):  BUS_DAILY_LIMIT 쌍/일
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransitRouteRefreshJob {

    private final TransitRouteMapper routeMapper;
    private final TransportScheduleMapper scheduleMapper;
    private final KorailTrainService korailTrainService;
    private final OdsayBusService odsayBusService;

    private static final int RAIL_DAILY_LIMIT = 200;  // 코레일 쿼터 10,000 중 2% 사용
    private static final int BUS_DAILY_LIMIT  = 80;   // ODsay 쿼터 1,000 중 8% 사용

    /** 매일 04:00 */
    @Scheduled(cron = "0 0 4 * * *")
    public void refreshStaleRoutes() {
        log.info("[배치] 갱신 시작 {}", LocalDateTime.now());
        refreshRail();
        refreshBus();
        purgeOldSchedules();
        log.info("[배치] 갱신 완료 {}", LocalDateTime.now());
    }

    // ── 기차 갱신 ─────────────────────────────────────────────────────

    private void refreshRail() {
        // 1. DB에 있는 기존 노선 쌍 수집
        Set<String> existing = routeMapper.findAllExistingRailPairs().stream()
                .map(r -> r.getFromHub() + "→" + r.getToHub())
                .collect(Collectors.toSet());

        // 2. Seed 전체 쌍 생성 (A→B, B→A 양방향)
        RegionSeedData.KtxStation[] stations = RegionSeedData.KTX_STATIONS;
        List<String[]> allPairs = new ArrayList<>();
        for (RegionSeedData.KtxStation from : stations) {
            for (RegionSeedData.KtxStation to : stations) {
                if (!from.name.equals(to.name)) allPairs.add(new String[]{from.name, to.name});
            }
        }

        // 3. 미등록 쌍 먼저
        List<String[]> missing = allPairs.stream()
                .filter(p -> !existing.contains(p[0] + "→" + p[1]))
                .collect(Collectors.toList());

        // 4. 기존 중 오래된 것
        List<TransitRoute> stale = routeMapper.findStaleRailPairs(
                LocalDateTime.now().minusHours(20), RAIL_DAILY_LIMIT);

        int ok = 0, fail = 0;
        int budget = RAIL_DAILY_LIMIT;

        // 미등록 우선
        for (String[] p : missing) {
            if (budget <= 0) break;
            try {
                korailTrainService.fetchAndStore(p[0], p[1]);
                ok++; budget--;
            } catch (Exception e) {
                fail++;
                log.warn("[배치/기차/신규] {}→{} 실패: {}", p[0], p[1], e.getMessage());
            }
        }

        // 남은 예산으로 오래된 것 갱신
        for (TransitRoute pair : stale) {
            if (budget <= 0) break;
            try {
                korailTrainService.fetchAndStore(pair.getFromHub(), pair.getToHub());
                ok++; budget--;
            } catch (Exception e) {
                fail++;
                log.warn("[배치/기차/갱신] {}→{} 실패: {}", pair.getFromHub(), pair.getToHub(), e.getMessage());
            }
        }

        log.info("[배치/기차] 완료 — 미등록 {}쌍 처리 / 갱신 대상 {}쌍 / 성공 {} / 실패 {}",
                missing.size(), stale.size(), ok, fail);
    }

    // ── 버스 갱신 ─────────────────────────────────────────────────────

    private void refreshBus() {
        Set<String> existing = routeMapper.findAllExistingBusPairs().stream()
                .map(r -> r.getFromHub() + "→" + r.getToHub())
                .collect(Collectors.toSet());

        // Seed 버스 터미널 이름 조합 (미등록 탐색용)
        // 버스는 OdsayBusService.listDestinations()가 동적으로 찾으므로
        // 여기서는 이미 DB에 있는 ODSAY: 키 쌍 중 오래된 것만 갱신
        // (새 터미널은 홈 탭 조회 시 write-through로 자동 등록됨)
        List<TransitRoute> stale = routeMapper.findStaleBusPairs(
                LocalDateTime.now().minusDays(6), BUS_DAILY_LIMIT);

        int ok = 0, fail = 0;
        for (TransitRoute row : stale) {
            try {
                int sid = parseOdsayId(row.getFromHub());
                int eid = parseOdsayId(row.getToHub());
                if (sid > 0 && eid > 0) {
                    odsayBusService.fetchAndStoreSchedule(sid, eid);
                    ok++;
                }
            } catch (Exception e) {
                fail++;
                log.warn("[배치/버스] {}→{} 실패: {}", row.getFromHub(), row.getToHub(), e.getMessage());
            }
        }

        int unusedBudget = BUS_DAILY_LIMIT - ok - fail;
        log.info("[배치/버스] 완료 — 갱신 {}쌍 / 성공 {} / 실패 {} / 남은 예산 {}",
                stale.size(), ok, fail, unusedBudget);
    }

    // ── 오래된 기차 시간표 정리 ──────────────────────────────────────

    private void purgeOldSchedules() {
        try {
            LocalDate cutoff = LocalDate.now().minusDays(1);
            int deleted = scheduleMapper.deleteOldTrainSchedules(cutoff);
            if (deleted > 0)
                log.info("[배치/시간표] 만료 기차 시간표 {}건 삭제 ({}일 이전)", deleted, cutoff);
        } catch (Exception e) {
            log.warn("[배치/시간표] 만료 시간표 삭제 실패: {}", e.getMessage());
        }
    }

    private static int parseOdsayId(String hub) {
        try { return Integer.parseInt(hub.replace("ODSAY:", "")); }
        catch (Exception e) { return -1; }
    }
}
