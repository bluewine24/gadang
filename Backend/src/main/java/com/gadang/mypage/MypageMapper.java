package com.gadang.mypage;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MypageMapper {

    @Select("SELECT COUNT(*) FROM TRIP_PLAN WHERE user_id = #{userId}")
    long countTrips(Long userId);

    @Select("""
            SELECT COALESCE(SUM(total_cost + food_cost_est), 0)
            FROM TRIP_PLAN
            WHERE user_id = #{userId}
            """)
    long sumTripCost(Long userId);

    @Select("""
            SELECT trip_id, title, trip_date, start_point, end_point,
                   departure_time, return_time, total_cost, food_cost_est
            FROM TRIP_PLAN
            WHERE user_id = #{userId}
            ORDER BY trip_date DESC, created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<TripSummaryResponse> findTripsByUser(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);
}
