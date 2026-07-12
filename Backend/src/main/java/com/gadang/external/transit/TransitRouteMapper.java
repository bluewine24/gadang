package com.gadang.external.transit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 교통 노선 L2 캐시 (영속).
 *
 * 계층: L1 Caffeine(슬라이딩, 휘발) → L2 TRANSIT_ROUTE(영속) → L3 외부 API
 * 요청 경로: miss 시 API 호출 후 upsert (write-through)
 * 배치: TransitRouteRefreshJob — 미등록 노선 우선 → 오래된 노선 순
 */
@Mapper
public interface TransitRouteMapper {

    /** 노선 쌍의 신선한 행 조회 (freshAfter 이후 갱신된 것만) */
    @Select("""
            SELECT from_hub, to_hub, type, duration_min, fare, daily_trips, updated_at
            FROM TRANSIT_ROUTE
            WHERE from_hub = #{fromHub} AND to_hub = #{toHub} AND updated_at > #{freshAfter}
            """)
    List<TransitRoute> findFresh(@Param("fromHub") String fromHub,
                                 @Param("toHub") String toHub,
                                 @Param("freshAfter") LocalDateTime freshAfter);

    @Insert("""
            INSERT INTO TRANSIT_ROUTE (from_hub, to_hub, type, duration_min, fare, daily_trips, updated_at)
            VALUES (#{fromHub}, #{toHub}, #{type}, #{durationMin}, #{fare}, #{dailyTrips}, NOW())
            ON DUPLICATE KEY UPDATE
                duration_min = #{durationMin}, fare = #{fare},
                daily_trips = #{dailyTrips}, updated_at = NOW()
            """)
    void upsert(TransitRoute route);

    /** 배치: DB에 한 번도 등록된 적 없는 기차 노선 쌍(기준: KTX 행 존재 여부)을 확인용 */
    @Select("""
            SELECT DISTINCT from_hub, to_hub
            FROM TRANSIT_ROUTE
            WHERE type IN ('KTX', '무궁화', 'ITX-청춘')
            """)
    List<TransitRoute> findAllExistingRailPairs();

    /** 배치: DB에 한 번도 등록된 적 없는 버스 노선 쌍 확인용 */
    @Select("""
            SELECT DISTINCT from_hub, to_hub
            FROM TRANSIT_ROUTE
            WHERE type = '시외버스'
            """)
    List<TransitRoute> findAllExistingBusPairs();

    /** 배치 갱신 대상: 기차 중 olderThan 이전 갱신 노선 (오래된 순) */
    @Select("""
            SELECT from_hub, to_hub, MIN(type) AS type,
                   -1 AS duration_min, -1 AS fare, -1 AS daily_trips, MIN(updated_at) AS updated_at
            FROM TRANSIT_ROUTE
            WHERE type IN ('KTX', '무궁화', 'ITX-청춘') AND updated_at < #{olderThan}
            GROUP BY from_hub, to_hub
            ORDER BY MIN(updated_at)
            LIMIT #{limit}
            """)
    List<TransitRoute> findStaleRailPairs(@Param("olderThan") LocalDateTime olderThan,
                                          @Param("limit") int limit);

    /** 배치 갱신 대상: 버스 중 olderThan 이전 갱신 노선 (오래된 순) */
    @Select("""
            SELECT from_hub, to_hub, type, duration_min, fare, daily_trips, updated_at
            FROM TRANSIT_ROUTE
            WHERE type = '시외버스' AND updated_at < #{olderThan}
            ORDER BY updated_at
            LIMIT #{limit}
            """)
    List<TransitRoute> findStaleBusPairs(@Param("olderThan") LocalDateTime olderThan,
                                         @Param("limit") int limit);
}
