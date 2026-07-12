package com.gadang.community;

import com.gadang.common.response.ApiResponse;
import com.gadang.common.response.PageResponse;
import com.gadang.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/posts")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /** 공개 목록 (블라인드 제외). placeName 파라미터로 지도 탭 연동 검색 가능 */
    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String placeName) {
        if (placeName != null && !placeName.isBlank()) {
            List<PostResponse> posts = communityService.findPostsByPlaceName(placeName);
            return ApiResponse.ok(new PageResponse<>(posts, 1, posts.size(), posts.size()));
        }
        return ApiResponse.ok(communityService.findPostPage(page, size));
    }

    /** 관리자 전용 목록 (블라인드 포함 전체) */
    @GetMapping("/admin")
    public ApiResponse<PageResponse<PostResponse>> adminList(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (currentUser == null || !currentUser.isAdmin()) {
            return ApiResponse.ok(communityService.findPostPage(page, size));
        }
        return ApiResponse.ok(communityService.findAllPostPage(page, size));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostResponse> detail(
            @PathVariable Long postId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        boolean isAdmin = currentUser != null && currentUser.isAdmin();
        return ApiResponse.ok(communityService.findPost(postId, isAdmin));
    }

    @PostMapping
    public ApiResponse<PostResponse> create(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestBody PostRequest request) {
        return ApiResponse.ok("게시글이 등록되었습니다.", communityService.createPost(currentUser.userId(), request));
    }

    @PatchMapping("/{postId}")
    public ApiResponse<PostResponse> update(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long postId,
            @RequestBody PostRequest request) {
        return ApiResponse.ok("게시글이 수정되었습니다.", communityService.updatePost(currentUser.userId(), postId, request));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long postId) {
        communityService.deletePost(currentUser.userId(), currentUser.isAdmin(), postId);
        return ApiResponse.ok("게시글이 삭제되었습니다.", null);
    }

    /** 관리자 블라인드 처리 */
    @PatchMapping("/{postId}/blind")
    public ApiResponse<Void> blind(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long postId,
            @RequestParam boolean blind) {
        communityService.setBlinded(currentUser.isAdmin(), postId, blind);
        return ApiResponse.ok(blind ? "블라인드 처리되었습니다." : "블라인드가 해제되었습니다.", null);
    }

    @PostMapping("/{postId}/comments")
    public ApiResponse<CommentResponse> createComment(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long postId,
            @RequestBody CommentRequest request) {
        return ApiResponse.ok("댓글이 등록되었습니다.", communityService.createComment(currentUser.userId(), postId, request));
    }

    @PatchMapping("/comments/{commentId}")
    public ApiResponse<CommentResponse> updateComment(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long commentId,
            @RequestBody CommentRequest request) {
        return ApiResponse.ok("댓글이 수정되었습니다.", communityService.updateComment(currentUser.userId(), commentId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long commentId) {
        communityService.deleteComment(currentUser.userId(), currentUser.isAdmin(), commentId);
        return ApiResponse.ok("댓글이 삭제되었습니다.", null);
    }
}
