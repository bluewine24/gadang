package com.gadang.community;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class PostPlaceDetail {
    private Long   detailId;
    private Long   postId;
    private int    seq;
    private Long   placeId;
    private String placeName;
    private String textContent;
    private int    cost;          // 원
    private int    durationMin;   // 분
    private String images;        // JSON 배열 문자열 ["url1","url2"]
}
