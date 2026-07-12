package com.gadang.community;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gadang.common.exception.GadangException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityMapper communityMapper;

    @InjectMocks
    private CommunityService communityService;

    @Test
    void updatePostRejectsNonOwner() {
        Post post = new Post();
        post.setPostId(10L);
        post.setUserId(1L);
        when(communityMapper.findPostById(10L)).thenReturn(post);

        assertThatThrownBy(() -> communityService.updatePost(2L, 10L, new PostRequest(null, "title", null, null, "content", null)))
                .isInstanceOf(GadangException.class)
                .hasMessage("권한이 없습니다.");
    }

    @Test
    void createPostRejectsTripOwnedByAnotherUser() {
        when(communityMapper.countOwnedTrip(30L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> communityService.createPost(1L, new PostRequest(30L, "title", null, null, "content", null)))
                .isInstanceOf(GadangException.class)
                .hasMessage("본인의 여행 일정만 공유할 수 있습니다.");
    }

    @Test
    void deleteCommentAllowsAdminModeration() {
        Comment comment = new Comment();
        comment.setCommentId(7L);
        comment.setUserId(1L);
        when(communityMapper.findCommentById(7L)).thenReturn(comment);

        communityService.deleteComment(99L, true, 7L);
    }
}
