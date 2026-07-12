package com.gadang.trip;

import com.gadang.mypage.TripSummaryResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TripMapper {

    @Insert("""
            INSERT INTO TRIP_PLAN
                (user_id, region_id, title, trip_date, start_point, end_point,
                 departure_time, return_time, budget_guide, total_cost, food_cost_est, course_json)
            VALUES
                (#{userId}, #{regionId}, #{title}, #{tripDate}, #{startPoint}, #{endPoint},
                 #{departureTime}, #{returnTime}, #{budgetGuide}, #{totalCost}, #{foodCostEst}, #{courseJson})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "tripId")
    void insert(TripPlan plan);

    @Select("""
            SELECT trip_id, title, trip_date, start_point, end_point,
                   departure_time, return_time, total_cost, food_cost_est
            FROM TRIP_PLAN
            WHERE user_id = #{userId}
            ORDER BY created_at DESC
            """)
    List<TripSummaryResponse> findByUser(Long userId);

    @Select("SELECT * FROM TRIP_PLAN WHERE trip_id = #{tripId}")
    TripPlan findById(Long tripId);

    @Delete("DELETE FROM TRIP_PLAN WHERE trip_id = #{tripId} AND user_id = #{userId}")
    int delete(@Param("tripId") Long tripId, @Param("userId") Long userId);

    @Select("SELECT place_id FROM PLACE WHERE kakao_place_id = #{kakaoPlaceId}")
    Long findPlaceIdByKakaoPlaceId(String kakaoPlaceId);

    @Insert("""
            INSERT INTO PLACE
                (kakao_place_id, name, category_code, category_name, address, lat, lng)
            VALUES
                (#{kakaoPlaceId}, #{name}, #{categoryCode}, #{categoryName}, #{address}, #{lat}, #{lng})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "placeId")
    void insertPlace(TripPlace place);

    @Insert("""
            INSERT INTO TRIP_ITEM
                (trip_id, place_id, visit_order, arrival_time, stay_minutes, admission_fee, food_cost)
            VALUES
                (#{tripId}, #{placeId}, #{visitOrder}, #{arrivalTime}, #{stayMinutes}, #{admissionFee}, #{foodCost})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "itemId")
    void insertItem(TripItemRow item);

    @Insert("""
            INSERT INTO TRIP_ROUTE
                (trip_id, from_item_id, to_item_id, transport_type, duration_minutes, fare)
            VALUES
                (#{tripId}, #{fromItemId}, #{toItemId}, #{transportType}, #{durationMinutes}, #{fare})
            """)
    void insertRoute(TripRouteRow route);
}
