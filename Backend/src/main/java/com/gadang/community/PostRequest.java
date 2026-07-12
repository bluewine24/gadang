package com.gadang.community;

import java.util.List;

public record PostRequest(
        Long   tripId,
        String title,
        String intro,    // 머리말
        String outro,    // 꼬리말
        String content,  // 전체 마크다운 (하위 호환 — places 없을 때 사용)
        List<PlaceDetail> places  // 장소별 상세 데이터 (null 허용)
) {
    public record PlaceDetail(
            Long         placeId,
            String       placeName,
            String       text,
            int          cost,         // 원
            int          durationMin,  // 분
            List<String> images        // 업로드된 이미지 URL
    ) {}
}
