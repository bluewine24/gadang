package com.gadang.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadang.common.exception.GadangException;
import com.gadang.common.response.PageResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {

    private final CommunityMapper communityMapper;
    private final PostPlaceDetailMapper detailMapper;
    private final ObjectMapper objectMapper;

    public CommunityService(CommunityMapper communityMapper,
                            PostPlaceDetailMapper detailMapper,
                            ObjectMapper objectMapper) {
        this.communityMapper = communityMapper;
        this.detailMapper    = detailMapper;
        this.objectMapper    = objectMapper;
    }

    // ── 공개 목록 (블라인드 제외) ─────────────────────────────
    public PageResponse<PostResponse> findPostPage(int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        List<PostResponse> items = communityMapper.findPostPage(offset(safePage, safeSize), safeSize)
                .stream()
                .map(post -> PostResponse.from(post, List.of()))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, communityMapper.countPosts());
    }

    // ── 관리자 목록 (블라인드 포함 전체) ─────────────────────
    public PageResponse<PostResponse> findAllPostPage(int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        List<PostResponse> items = communityMapper.findAllPostPage(offset(safePage, safeSize), safeSize)
                .stream()
                .map(post -> PostResponse.from(post, List.of()))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, communityMapper.countAllPosts());
    }

    // ── 단건 조회 ─────────────────────────────────────────────
    public PostResponse findPost(Long postId, boolean includeBlinded) {
        Post post = requirePost(postId);
        if (post.isBlinded() && !includeBlinded) {
            throw GadangException.notFound("게시글을 찾을 수 없습니다.");
        }
        List<CommentResponse> comments = communityMapper.findCommentsByPostId(postId)
                .stream().map(CommentResponse::from).toList();
        List<PostPlaceDetail> details = detailMapper.findByPostId(postId);
        return PostResponse.from(post, comments, details);
    }

    public PostResponse findPost(Long postId) {
        return findPost(postId, false);
    }

    // ── 장소명으로 게시글 검색 (지도 탭) ─────────────────────
    public List<PostResponse> findPostsByPlaceName(String placeName) {
        if (placeName == null || placeName.isBlank()) return List.of();
        String keyword = "%" + placeName.trim() + "%";
        return communityMapper.findPostsByPlaceName(keyword)
                .stream().map(post -> PostResponse.from(post, List.of()))
                .toList();
    }

    public PageResponse<PostResponse> findUserPosts(Long userId, int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        List<PostResponse> items = communityMapper.findPostsByUser(userId, offset(safePage, safeSize), safeSize)
                .stream()
                .map(post -> PostResponse.from(post, List.of()))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, communityMapper.countPostsByUser(userId));
    }

    @Transactional
    public PostResponse createPost(Long userId, PostRequest request) {
        validateOwnedTripIfPresent(userId, request.tripId());

        int totalCost = 0, totalMin = 0;
        if (request.places() != null) {
            for (var p : request.places()) { totalCost += p.cost(); totalMin += p.durationMin(); }
        }

        Post post = new Post();
        post.setUserId(userId);
        post.setTripId(request.tripId());
        post.setTitle(requireText(request.title(), "게시글 제목을 입력해 주세요."));
        post.setIntro(request.intro());
        post.setOutro(request.outro());
        post.setContent(buildContent(request));
        post.setTotalCost(totalCost);
        post.setTotalDurationMin(totalMin);
        communityMapper.insertPost(post);

        savePlaceDetails(post.getPostId(), request.places());
        return findPost(post.getPostId(), true);
    }

    @Transactional
    public PostResponse updatePost(Long userId, Long postId, PostRequest request) {
        Post post = requirePost(postId);
        requireOwner(post.getUserId(), userId, false);
        validateOwnedTripIfPresent(userId, request.tripId());

        int totalCost = 0, totalMin = 0;
        if (request.places() != null) {
            for (var p : request.places()) { totalCost += p.cost(); totalMin += p.durationMin(); }
        }

        post.setTripId(request.tripId());
        post.setTitle(requireText(request.title(), "게시글 제목을 입력해 주세요."));
        post.setIntro(request.intro());
        post.setOutro(request.outro());
        post.setContent(buildContent(request));
        post.setTotalCost(totalCost);
        post.setTotalDurationMin(totalMin);
        communityMapper.updatePost(post);

        detailMapper.deleteByPostId(postId);
        savePlaceDetails(postId, request.places());
        return findPost(postId, true);
    }

    // ── 블라인드 처리 (관리자 전용) ───────────────────────────
    @Transactional
    public void setBlinded(boolean admin, Long postId, boolean blinded) {
        if (!admin) throw GadangException.forbidden();
        requirePost(postId);
        communityMapper.setBlinded(postId, blinded);
    }

    private String buildContent(PostRequest request) {
        if (request.places() == null || request.places().isEmpty()) {
            return request.content() == null ? "" : request.content().trim();
        }
        var sb = new StringBuilder();
        if (request.intro() != null && !request.intro().isBlank()) sb.append(request.intro()).append("\n\n");
        for (var p : request.places()) {
            sb.append("## ").append(p.placeName()).append("\n");
            if (p.text() != null && !p.text().isBlank()) sb.append(p.text().trim()).append("\n");
            if (p.images() != null) p.images().forEach(u -> sb.append("[image:").append(u).append("]\n"));
            sb.append("\n");
        }
        if (request.outro() != null && !request.outro().isBlank()) sb.append(request.outro());
        return sb.toString().trim();
    }

    private void savePlaceDetails(Long postId, List<PostRequest.PlaceDetail> places) {
        if (places == null) return;
        for (int i = 0; i < places.size(); i++) {
            var p = places.get(i);
            PostPlaceDetail d = new PostPlaceDetail();
            d.setPostId(postId);
            d.setSeq(i);
            d.setPlaceId(p.placeId());
            d.setPlaceName(p.placeName());
            d.setTextContent(p.text());
            d.setCost(p.cost());
            d.setDurationMin(p.durationMin());
            d.setImages(toJson(p.images()));
            detailMapper.insert(d);
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return null; }
    }

    @Transactional
    public void deletePost(Long userId, boolean admin, Long postId) {
        Post post = requirePost(postId);
        requireOwner(post.getUserId(), userId, admin);
        communityMapper.deletePost(postId);
    }

    @Transactional
    public CommentResponse createComment(Long userId, Long postId, CommentRequest request) {
        requirePost(postId);
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(requireText(request.content(), "댓글 내용을 입력해 주세요."));
        communityMapper.insertComment(comment);
        return findPost(postId, true).comments().stream()
                .filter(item -> item.commentId().equals(comment.getCommentId()))
                .findFirst()
                .orElseThrow(() -> GadangException.notFound("댓글을 찾을 수 없습니다."));
    }

    @Transactional
    public CommentResponse updateComment(Long userId, Long commentId, CommentRequest request) {
        Comment comment = requireComment(commentId);
        requireOwner(comment.getUserId(), userId, false);
        comment.setContent(requireText(request.content(), "댓글 내용을 입력해 주세요."));
        communityMapper.updateComment(comment);
        return findPost(comment.getPostId(), true).comments().stream()
                .filter(item -> item.commentId().equals(commentId))
                .findFirst()
                .orElseThrow(() -> GadangException.notFound("댓글을 찾을 수 없습니다."));
    }

    @Transactional
    public void deleteComment(Long userId, boolean admin, Long commentId) {
        Comment comment = requireComment(commentId);
        requireOwner(comment.getUserId(), userId, admin);
        communityMapper.deleteComment(commentId);
    }

    private void validateOwnedTripIfPresent(Long userId, Long tripId) {
        if (tripId == null) return;
        if (communityMapper.countOwnedTrip(tripId, userId) == 0) {
            throw GadangException.badRequest("본인의 여행 일정만 공유할 수 있습니다.");
        }
    }

    private Post requirePost(Long postId) {
        Post post = communityMapper.findPostById(postId);
        if (post == null) throw GadangException.notFound("게시글을 찾을 수 없습니다.");
        return post;
    }

    private Comment requireComment(Long commentId) {
        Comment comment = communityMapper.findCommentById(commentId);
        if (comment == null) throw GadangException.notFound("댓글을 찾을 수 없습니다.");
        return comment;
    }

    private void requireOwner(Long ownerId, Long currentUserId, boolean admin) {
        if (!admin && !ownerId.equals(currentUserId)) throw GadangException.forbidden();
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw GadangException.badRequest(message);
        return value.trim();
    }

    private int safePage(int page)  { return Math.max(page, 1); }
    private int safeSize(int size)  { return Math.min(Math.max(size, 1), 50); }
    private int offset(int page, int size) { return (page - 1) * size; }
}
