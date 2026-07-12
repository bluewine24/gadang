package com.gadang.external.transit;

import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TransportScheduleMapper {

    /** 특정 노선·날짜의 신선한 시간표 조회 (dep_time 순) */
    @Select("""
            SELECT id, from_hub, to_hub, type, dep_time, arr_time,
                   duration_min, fare, train_no, travel_date, updated_at
            FROM TRANSPORT_SCHEDULE
            WHERE from_hub = #{fromHub} AND to_hub = #{toHub}
              AND type = #{type}
              AND travel_date = #{travelDate}
              AND updated_at > #{freshAfter}
            ORDER BY dep_time
            """)
    List<TransportScheduleEntry> findFresh(@Param("fromHub") String fromHub,
                                           @Param("toHub") String toHub,
                                           @Param("type") String type,
                                           @Param("travelDate") LocalDate travelDate,
                                           @Param("freshAfter") LocalDateTime freshAfter);

    /** 노선의 모든 타입 시간표 일괄 조회 (코스 탭 초기화 시 사용) */
    @Select("""
            SELECT id, from_hub, to_hub, type, dep_time, arr_time,
                   duration_min, fare, train_no, travel_date, updated_at
            FROM TRANSPORT_SCHEDULE
            WHERE from_hub = #{fromHub} AND to_hub = #{toHub}
              AND travel_date = #{travelDate}
              AND updated_at > #{freshAfter}
            ORDER BY type, dep_time
            """)
    List<TransportScheduleEntry> findAllTypes(@Param("fromHub") String fromHub,
                                              @Param("toHub") String toHub,
                                              @Param("travelDate") LocalDate travelDate,
                                              @Param("freshAfter") LocalDateTime freshAfter);

    @Insert("""
            INSERT INTO TRANSPORT_SCHEDULE
              (from_hub, to_hub, type, dep_time, arr_time, duration_min, fare, train_no, travel_date)
            VALUES
              (#{fromHub}, #{toHub}, #{type}, #{depTime}, #{arrTime},
               #{durationMin}, #{fare}, #{trainNo}, #{travelDate})
            ON DUPLICATE KEY UPDATE
              arr_time = #{arrTime}, duration_min = #{durationMin},
              fare = #{fare}, train_no = #{trainNo}, updated_at = NOW()
            """)
    void upsert(TransportScheduleEntry entry);

    /** 특정 노선·타입의 기존 시간표 전체 삭제 (새 fetch 전 정리) */
    @Delete("""
            DELETE FROM TRANSPORT_SCHEDULE
            WHERE from_hub = #{fromHub} AND to_hub = #{toHub}
              AND type = #{type} AND travel_date = #{travelDate}
            """)
    void deleteByRoute(@Param("fromHub") String fromHub,
                       @Param("toHub") String toHub,
                       @Param("type") String type,
                       @Param("travelDate") LocalDate travelDate);

    /** 배치 정리: 기차 시간표 중 cutoff 날짜 이전의 레코드 삭제 (버스 1970-01-01 은 제외) */
    @Delete("""
            DELETE FROM TRANSPORT_SCHEDULE
            WHERE travel_date < #{cutoff}
              AND travel_date > '1970-01-01'
            """)
    int deleteOldTrainSchedules(@Param("cutoff") LocalDate cutoff);
}
