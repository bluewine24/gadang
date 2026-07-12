package com.gadang.notice;

import com.gadang.common.exception.GadangException;
import com.gadang.common.response.PageResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeService {

    private final NoticeMapper noticeMapper;

    public NoticeService(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    public PageResponse<NoticeResponse> findPage(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<NoticeResponse> items = noticeMapper.findPage((safePage - 1) * safeSize, safeSize)
                .stream()
                .map(NoticeResponse::from)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, noticeMapper.countAll());
    }

    public NoticeResponse findById(Long noticeId) {
        return NoticeResponse.from(requireNotice(noticeId));
    }

    @Transactional
    public NoticeResponse create(Long adminUserId, NoticeRequest request) {
        Notice notice = new Notice();
        notice.setUserId(adminUserId);
        notice.setTitle(requireText(request.title(), "공지 제목을 입력해 주세요."));
        notice.setContent(requireText(request.content(), "공지 내용을 입력해 주세요."));
        noticeMapper.insert(notice);
        return findById(notice.getNoticeId());
    }

    @Transactional
    public NoticeResponse update(Long noticeId, NoticeRequest request) {
        Notice notice = requireNotice(noticeId);
        notice.setTitle(requireText(request.title(), "공지 제목을 입력해 주세요."));
        notice.setContent(requireText(request.content(), "공지 내용을 입력해 주세요."));
        noticeMapper.update(notice);
        return findById(noticeId);
    }

    @Transactional
    public void delete(Long noticeId) {
        requireNotice(noticeId);
        noticeMapper.deleteById(noticeId);
    }

    private Notice requireNotice(Long noticeId) {
        Notice notice = noticeMapper.findById(noticeId);
        if (notice == null) {
            throw GadangException.notFound("공지사항을 찾을 수 없습니다.");
        }
        return notice;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw GadangException.badRequest(message);
        }
        return value.trim();
    }
}
