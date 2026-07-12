package com.gadang.community;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityMapper {

    // ── 공개 목록: 블라인드 제외 ──────────────────────────────
    @Select("""
            SELECT p.post_id, p.user_id, u.nickname AS author_nickname, p.trip_id,
                   p.title, p.intro, p.outro, p.content,
                   p.total_cost, p.total_duration_min, p.is_blinded, p.created_at, p.updated_at,
                   COUNT(c.comment_id) AS comment_count
            FROM COMMUNITY_POST p
            JOIN `USER` u ON u.user_id = p.user_id
            LEFT JOIN `COMMENT` c ON c.post_id = p.post_id
            WHERE p.is_blinded = 0
            GROUP BY p.post_id, p.user_id, u.nickname, p.trip_id,
                     p.title, p.intro, p.outro, p.content,
                     p.total_cost, p.total_duration_min, p.is_blinded, p.created_at, p.updated_at
            ORDER BY p.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Post> findPostPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM COMMUNITY_POST WHERE is_blinded = 0")
    long countPosts();

    // ── 관리자 목록: 블라인드 포함 전체 ──────────────────────
    @Select("""
            SELECT p.post_id, p.user_id, u.nickname AS author_nickname, p.trip_id,
                   p.title, p.intro, p.outro, p.content,
                   p.total_cost, p.total_duration_min, p.is_blinded, p.created_at, p.updated_at,
                   COUNT(c.comment_id) AS comment_count
            FROM COMMUNITY_POST p
            JOIN `USER` u ON u.user_id = p.user_id
            LEFT JOIN `COMMENT` c ON c.post_id = p.post_id
            GROUP BY p.post_id, p.user_id, u.nickname, p.trip_id,
                     p.title, p.intro, p.outro, p.content,
                     p.total_cost, p.total_duration_min, p.is_blinded, p.created_at, p.updated_at
            ORDER BY p.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Post> findAllPostPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM COMMUNITY_POST")
    long countAllPosts();

    @Select("""
            SELECT p.post_id, p.user_id, u.nickname AS author_nickname, p.trip_id,
                   p.title, p.intro, p.outro, p.content,
                   p.total_cost, p.total_duration_min, p.is_blinded, p.created_at, p.updated_at,
                   COUNT(c.comment_id) AS comment_count
            FROM COMMUNITY_POST p
            JOIN `USER` u ON u.user_id = p.user_id
            LEFT JOIN `COMMENT` c ON c.post_id = p.post_id
            WHERE p.post_id = #{postId}
            GROUP BY p.post_id, p.user_id, u.nickname, p.trip_id,
                     p.title, p.intro, p.outro, p.content,
                     p.total_cost, p.total_duration_min, p.is_blinded, p.created_at, p.updated_at
            """)
    Post findPostById(Long postId);

    @Insert("""
            INSERT INTO COMMUNITY_POST
                (user_id, trip_id, title, intro, outro, content, total_cost, total_duration_min)
            VALUES
                (#{userId}, #{tripId}, #{title}, #{intro}, #{outro}, #{content}, #{totalCost}, #{totalDurationMin})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "postId")
    void insertPost(Post post);

    @Update("""
            UPDATE COMMUNITY_POST
            SET trip_id = #{tripId}, title = #{title},
                intro = #{intro}, outro = #{outro}, content = #{content},
                total_cost = #{totalCost}, total_duration_min = #{totalDurationMin}
            WHERE post_id = #{postId}
            """)
    void updatePost(Post post);

    @Delete("DELETE FROM COMMUNITY_POST WHERE post_id = #{postId}")
    void deletePost(Long postId);

    // ── 블라인드 처리 ─────────────────────────────────────────
    @Update("UPDATE COMMUNITY_POST SET is_blinded = #{blinded} WHERE post_id = #{postId}")
    void setBlinded(@Param("postId") Long postId, @Param("blinded") boolean blinded);

    // ── 장소명으로 게시글 검색 (지도 탭 연동) ────────────────
    @Select("""
            SELECT DISTINCT p.post_id, p.user_id, u.nickname AS author_nickname, p.trip_id,
                   p.title, p.intro, p.outro, p.content,
                   p.total_cost, p.total_duration_min, p.is_blinded, p.created_at, p.updated_at,
                   (SELECT COUNT(*) FROM `COMMENT` c WHERE c.post_id = p.post_id) AS comment_count
            FROM COMMUNITY_POST p
            JOIN `USER` u ON u.user_id = p.user_id
            JOIN POST_PLACE_DETAIL d ON d.post_id = p.post_id
            WHERE d.place_name LIKE #{keyword}
              AND p.is_blinded = 0
            ORDER BY p.created_at DESC
            LIMIT 6
            """)
    List<Post> findPostsByPlaceName(@Param("keyword") String keyword);

    @Select("""
            SELECT c.comment_id, c.post_id, c.user_id, u.nickname AS author_nickname,
                   c.content, c.created_at
            FROM `COMMENT` c
            JOIN `USER` u ON u.user_id = c.user_id
            WHERE c.post_id = #{postId}
            ORDER BY c.created_at ASC
            """)
    List<Comment> findCommentsByPostId(Long postId);

    @Select("""
            SELECT comment_id, post_id, user_id, content, created_at
            FROM `COMMENT`
            WHERE comment_id = #{commentId}
            """)
    Comment findCommentById(Long commentId);

    @Insert("""
            INSERT INTO `COMMENT` (post_id, user_id, content)
            VALUES (#{postId}, #{userId}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "commentId")
    void insertComment(Comment comment);

    @Update("""
            UPDATE `COMMENT`
            SET content = #{content}
            WHERE comment_id = #{commentId}
            """)
    void updateComment(Comment comment);

    @Delete("DELETE FROM `COMMENT` WHERE comment_id = #{commentId}")
    void deleteComment(Long commentId);

    @Select("""
            SELECT COUNT(*)
            FROM TRIP_PLAN
            WHERE trip_id = #{tripId} AND user_id = #{userId}
            """)
    int countOwnedTrip(@Param("tripId") Long tripId, @Param("userId") Long userId);

    @Select("""
            SELECT p.post_id, p.user_id, u.nickname AS author_nickname, p.trip_id,
                   p.title, p.intro, p.outro, p.content,
                   p.total_cost, p.total_duration_min, p.is_blinded, p.created_at, p.updated_at,
                   COUNT(c.comment_id) AS comment_count
            FROM COMMUNITY_POST p
            JOIN `USER` u ON u.user_id = p.user_id
            LEFT JOIN `COMMENT` c ON c.post_id = p.post_id
            WHERE p.user_id = #{userId}
            GROUP BY p.post_id, p.user_id, u.nickname, p.trip_id,
                     p.title, p.intro, p.outro, p.content,
                     p.total_cost, p.total_duration_min, p.is_blinded, p.created_at, p.updated_at
            ORDER BY p.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Post> findPostsByUser(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM COMMUNITY_POST WHERE user_id = #{userId}")
    long countPostsByUser(Long userId);
}
