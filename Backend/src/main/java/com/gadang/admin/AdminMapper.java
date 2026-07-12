package com.gadang.admin;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminMapper {

    @Select("SELECT COUNT(*) FROM TRIP_PLAN")
    long countTrips();

    @Select("SELECT COUNT(*) FROM COMMUNITY_POST")
    long countPosts();

    @Select("SELECT COUNT(*) FROM NOTICE")
    long countNotices();

    @Select("SELECT COUNT(*) FROM PLACE")
    long countPlaces();

    @Select("""
            SELECT fee_id, af.place_id, p.name AS place_name, fee, fee_type, af.updated_at
            FROM ADMISSION_FEE af
            JOIN PLACE p ON p.place_id = af.place_id
            ORDER BY af.updated_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<AdmissionFee> findAdmissionFees(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM ADMISSION_FEE")
    long countAdmissionFees();

    @Select("""
            SELECT fee_id, af.place_id, p.name AS place_name, fee, fee_type, af.updated_at
            FROM ADMISSION_FEE af
            JOIN PLACE p ON p.place_id = af.place_id
            WHERE fee_id = #{feeId}
            """)
    AdmissionFee findAdmissionFee(Long feeId);

    @Insert("""
            INSERT INTO ADMISSION_FEE (place_id, fee, fee_type)
            VALUES (#{placeId}, #{fee}, #{feeType})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "feeId")
    void insertAdmissionFee(AdmissionFee fee);

    @Update("""
            UPDATE ADMISSION_FEE
            SET place_id = #{placeId}, fee = #{fee}, fee_type = #{feeType}
            WHERE fee_id = #{feeId}
            """)
    void updateAdmissionFee(AdmissionFee fee);

    @Delete("DELETE FROM ADMISSION_FEE WHERE fee_id = #{feeId}")
    void deleteAdmissionFee(Long feeId);

    @Select("SELECT COUNT(*) FROM PLACE WHERE place_id = #{placeId}")
    int countPlace(Long placeId);

    @Select("""
            SELECT id, brand_name, registered_at
            FROM FRANCHISE_BLACKLIST
            ORDER BY brand_name ASC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<BlacklistBrand> findBlacklist(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM FRANCHISE_BLACKLIST")
    long countBlacklist();

    /** 프랜차이즈 브랜드명 전체 (장소 스코어링 필터용) */
    @Select("SELECT brand_name FROM FRANCHISE_BLACKLIST")
    List<String> findAllBrandNames();

    @Select("""
            SELECT id, brand_name, registered_at
            FROM FRANCHISE_BLACKLIST
            WHERE id = #{id}
            """)
    BlacklistBrand findBlacklistBrand(Long id);

    @Insert("""
            INSERT INTO FRANCHISE_BLACKLIST (brand_name)
            VALUES (#{brandName})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertBlacklistBrand(BlacklistBrand brand);

    @Update("""
            UPDATE FRANCHISE_BLACKLIST
            SET brand_name = #{brandName}
            WHERE id = #{id}
            """)
    void updateBlacklistBrand(BlacklistBrand brand);

    @Delete("DELETE FROM FRANCHISE_BLACKLIST WHERE id = #{id}")
    void deleteBlacklistBrand(Long id);

    @Select("""
            SELECT p.place_id,
                   p.name AS place_name,
                   p.category_code,
                   p.category_name,
                   CASE WHEN ti.food_cost > 0 THEN ti.food_cost ELSE ti.admission_fee END AS cost,
                   ti.stay_minutes AS duration_min
            FROM TRIP_ITEM ti
            JOIN PLACE p ON p.place_id = ti.place_id
            WHERE ti.admission_fee > 0 OR ti.food_cost > 0 OR ti.stay_minutes > 0
            UNION ALL
            SELECT ppd.place_id,
                   COALESCE(p.name, ppd.place_name) AS place_name,
                   p.category_code,
                   p.category_name,
                   ppd.cost AS cost,
                   ppd.duration_min AS duration_min
            FROM POST_PLACE_DETAIL ppd
            LEFT JOIN PLACE p ON p.place_id = ppd.place_id
            WHERE ppd.cost > 0 OR ppd.duration_min > 0
            """)
    List<PlaceMetricSample> findPlaceMetricSamples();
}
