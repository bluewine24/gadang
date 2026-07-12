package com.gadang.user;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT user_id, email, password, nickname, role, provider, social_id, created_at, updated_at
            FROM `USER`
            WHERE user_id = #{userId}
            """)
    User findById(Long userId);

    @Select("""
            SELECT user_id, email, password, nickname, role, provider, social_id, created_at, updated_at
            FROM `USER`
            WHERE email = #{email}
            """)
    User findByEmail(String email);

    @Select("""
            SELECT user_id, email, password, nickname, role, provider, social_id, created_at, updated_at
            FROM `USER`
            WHERE provider = #{provider} AND social_id = #{socialId}
            """)
    User findBySocialId(@Param("provider") String provider, @Param("socialId") String socialId);

    @Insert("""
            INSERT INTO `USER` (email, password, nickname, role, provider, social_id)
            VALUES (#{email}, #{password}, #{nickname}, #{role}, #{provider}, #{socialId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    void insert(User user);

    @Update("""
            UPDATE `USER`
            SET nickname = #{nickname}
            WHERE user_id = #{userId}
            """)
    void updateNickname(@Param("userId") Long userId, @Param("nickname") String nickname);

    @Update("""
            UPDATE `USER`
            SET nickname = #{nickname}, password = #{password}
            WHERE user_id = #{userId}
            """)
    void updateNicknameAndPassword(
            @Param("userId") Long userId,
            @Param("nickname") String nickname,
            @Param("password") String password);

    @Delete("DELETE FROM `USER` WHERE user_id = #{userId}")
    void deleteById(Long userId);

    @Select("""
            SELECT user_id, email, password, nickname, role, provider, social_id, created_at, updated_at
            FROM `USER`
            ORDER BY created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<User> findPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM `USER`")
    long countAll();

    @Select("""
            SELECT user_id, email, password, nickname, role, provider, social_id, created_at, updated_at
            FROM `USER`
            WHERE LOWER(COALESCE(email, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
               OR LOWER(COALESCE(nickname, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
            ORDER BY created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<User> searchPage(
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("""
            SELECT COUNT(*)
            FROM `USER`
            WHERE LOWER(COALESCE(email, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
               OR LOWER(COALESCE(nickname, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')
            """)
    long countSearch(@Param("keyword") String keyword);

    @Update("""
            UPDATE `USER`
            SET role = #{role}
            WHERE user_id = #{userId}
            """)
    void updateRole(@Param("userId") Long userId, @Param("role") String role);
}
