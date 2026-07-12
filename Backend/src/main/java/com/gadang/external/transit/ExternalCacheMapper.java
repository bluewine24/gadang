package com.gadang.external.transit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 트렌드·이미지 L2 캐시 (TREND_CACHE / REGION_IMAGE).
 * TRANSIT_ROUTE와 같은 패턴 — 요청 시 miss면 API 호출 후 저장(write-through).
 */
@Mapper
public interface ExternalCacheMapper {

    // ── 트렌드 (TTL 24시간 권장) ─────────────────────────

    @Select("""
            SELECT score FROM TREND_CACHE
            WHERE keyword = #{keyword} AND updated_at > #{freshAfter}
            """)
    Double findTrend(@Param("keyword") String keyword,
                     @Param("freshAfter") LocalDateTime freshAfter);

    @Insert("""
            INSERT INTO TREND_CACHE (keyword, score, updated_at)
            VALUES (#{keyword}, #{score}, NOW())
            ON DUPLICATE KEY UPDATE score = #{score}, updated_at = NOW()
            """)
    void upsertTrend(@Param("keyword") String keyword, @Param("score") double score);

    // ── 지역 이미지 (TTL 30일 권장, 개행 구분 URL 목록) ──

    @Select("""
            SELECT urls FROM REGION_IMAGE
            WHERE region_name = #{regionName} AND updated_at > #{freshAfter}
            """)
    String findImages(@Param("regionName") String regionName,
                      @Param("freshAfter") LocalDateTime freshAfter);

    @Insert("""
            INSERT INTO REGION_IMAGE (region_name, urls, updated_at)
            VALUES (#{regionName}, #{urls}, NOW())
            ON DUPLICATE KEY UPDATE urls = #{urls}, updated_at = NOW()
            """)
    void upsertImages(@Param("regionName") String regionName, @Param("urls") String urls);

    // ── 지역 장소 후보 (TTL 7일 권장, 인기 필터링 결과 JSON) ──

    @Select("""
            SELECT payload FROM REGION_PLACES
            WHERE cache_key = #{cacheKey} AND updated_at > #{freshAfter}
            """)
    String findPlaces(@Param("cacheKey") String cacheKey,
                      @Param("freshAfter") LocalDateTime freshAfter);

    @Insert("""
            INSERT INTO REGION_PLACES (cache_key, payload, updated_at)
            VALUES (#{cacheKey}, #{payload}, NOW())
            ON DUPLICATE KEY UPDATE payload = #{payload}, updated_at = NOW()
            """)
    void upsertPlaces(@Param("cacheKey") String cacheKey, @Param("payload") String payload);
}
