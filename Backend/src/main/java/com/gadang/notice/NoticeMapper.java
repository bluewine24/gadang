package com.gadang.notice;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NoticeMapper {

    @Select("""
            SELECT n.notice_id, n.user_id, u.nickname AS author_nickname,
                   n.title, n.content, n.created_at, n.updated_at
            FROM NOTICE n
            JOIN `USER` u ON u.user_id = n.user_id
            ORDER BY n.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Notice> findPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM NOTICE")
    long countAll();

    @Select("""
            SELECT n.notice_id, n.user_id, u.nickname AS author_nickname,
                   n.title, n.content, n.created_at, n.updated_at
            FROM NOTICE n
            JOIN `USER` u ON u.user_id = n.user_id
            WHERE n.notice_id = #{noticeId}
            """)
    Notice findById(Long noticeId);

    @Insert("""
            INSERT INTO NOTICE (user_id, title, content)
            VALUES (#{userId}, #{title}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "noticeId")
    void insert(Notice notice);

    @Update("""
            UPDATE NOTICE
            SET title = #{title}, content = #{content}
            WHERE notice_id = #{noticeId}
            """)
    void update(Notice notice);

    @Delete("DELETE FROM NOTICE WHERE notice_id = #{noticeId}")
    void deleteById(Long noticeId);
}
