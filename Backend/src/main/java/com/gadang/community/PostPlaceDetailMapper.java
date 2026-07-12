package com.gadang.community;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PostPlaceDetailMapper {

    @Insert("""
            INSERT INTO POST_PLACE_DETAIL
                (post_id, seq, place_id, place_name, text_content, cost, duration_min, images)
            VALUES
                (#{postId}, #{seq}, #{placeId}, #{placeName}, #{textContent}, #{cost}, #{durationMin}, #{images})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "detailId")
    void insert(PostPlaceDetail detail);

    @Select("SELECT * FROM POST_PLACE_DETAIL WHERE post_id = #{postId} ORDER BY seq")
    List<PostPlaceDetail> findByPostId(Long postId);

    @Delete("DELETE FROM POST_PLACE_DETAIL WHERE post_id = #{postId}")
    void deleteByPostId(Long postId);
}
