package com.gadang.community;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Post {
    private Long postId;
    private Long userId;
    private String authorNickname;
    private Long tripId;
    private String title;
    private String intro;          // 머리말
    private String outro;          // 꼬리말
    private String content;        // 전체 마크다운 (하위 호환)
    private int     totalCost;        // 총 지출 (장소별 합계)
    private int     totalDurationMin; // 총 소요 시간 (분)
    private boolean blinded;          // 관리자 블라인드 처리 여부
    private int     commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
