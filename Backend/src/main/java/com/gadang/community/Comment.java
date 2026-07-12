package com.gadang.community;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Comment {
    private Long commentId;
    private Long postId;
    private Long userId;
    private String authorNickname;
    private String content;
    private LocalDateTime createdAt;
}
