package com.gadang.community;

import java.time.LocalDateTime;

public record CommentResponse(
        Long commentId,
        Long postId,
        Long userId,
        String authorNickname,
        String content,
        LocalDateTime createdAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getCommentId(),
                comment.getPostId(),
                comment.getUserId(),
                comment.getAuthorNickname(),
                comment.getContent(),
                comment.getCreatedAt());
    }
}
