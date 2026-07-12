package com.gadang.favorite;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FavoriteMapper {

    @Select("SELECT COUNT(*) FROM PLACE WHERE place_id = #{placeId}")
    int countPlace(Long placeId);

    @Select("SELECT COUNT(*) FROM FAVORITE WHERE user_id = #{userId} AND place_id = #{placeId}")
    int countFavorite(@Param("userId") Long userId, @Param("placeId") Long placeId);

    @Insert("""
            INSERT INTO FAVORITE (user_id, place_id)
            VALUES (#{userId}, #{placeId})
            """)
    void insert(@Param("userId") Long userId, @Param("placeId") Long placeId);

    @Delete("""
            DELETE FROM FAVORITE
            WHERE user_id = #{userId} AND place_id = #{placeId}
            """)
    void delete(@Param("userId") Long userId, @Param("placeId") Long placeId);

    @Select("""
            SELECT f.favorite_id, p.place_id, p.name, p.category_code, p.category_name,
                   p.address, p.lat, p.lng, p.place_url, f.created_at
            FROM FAVORITE f
            JOIN PLACE p ON p.place_id = f.place_id
            WHERE f.user_id = #{userId}
            ORDER BY f.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<FavoritePlaceResponse> findByUser(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM FAVORITE WHERE user_id = #{userId}")
    long countByUser(Long userId);
}
