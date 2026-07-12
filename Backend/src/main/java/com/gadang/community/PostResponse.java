package com.gadang.community;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
        Long            postId,
        Long            userId,
        String          authorNickname,
        Long            tripId,
        String          title,
        String          intro,
        String          outro,
        String          content,
        int             totalCost,
        int             totalDurationMin,
        boolean         blinded,
        List<PlaceDetailResponse> places,
        int             commentCount,
        LocalDateTime   createdAt,
        LocalDateTime   updatedAt,
        List<CommentResponse> comments
) {
    private static final ObjectMapper OM = new ObjectMapper();

    public record PlaceDetailResponse(
            Long         detailId,
            int          seq,
            Long         placeId,
            String       placeName,
            String       textContent,
            int          cost,
            int          durationMin,
            List<String> images
    ) {
        public static PlaceDetailResponse from(PostPlaceDetail d) {
            List<String> imgs = List.of();
            if (d.getImages() != null && !d.getImages().isBlank()) {
                try { imgs = OM.readValue(d.getImages(), new TypeReference<>() {}); }
                catch (Exception ignored) {}
            }
            return new PlaceDetailResponse(
                    d.getDetailId(), d.getSeq(), d.getPlaceId(),
                    d.getPlaceName(), d.getTextContent(),
                    d.getCost(), d.getDurationMin(), imgs);
        }
    }

    public static PostResponse from(Post post, List<CommentResponse> comments, List<PostPlaceDetail> details) {
        List<PlaceDetailResponse> places = details == null ? List.of()
                : details.stream().map(PlaceDetailResponse::from).toList();
        return new PostResponse(
                post.getPostId(), post.getUserId(), post.getAuthorNickname(),
                post.getTripId(), post.getTitle(),
                post.getIntro(), post.getOutro(), post.getContent(),
                post.getTotalCost(), post.getTotalDurationMin(),
                post.isBlinded(), places, post.getCommentCount(),
                post.getCreatedAt(), post.getUpdatedAt(), comments);
    }

    /** 기존 코드 호환 — 장소 상세 없이 호출 시 */
    public static PostResponse from(Post post, List<CommentResponse> comments) {
        return from(post, comments, List.of());
    }
}
