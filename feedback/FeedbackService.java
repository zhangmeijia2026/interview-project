package com.group5.interview.module.feedback;

import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.common.PageResult;
import com.group5.interview.entity.User;
import com.group5.interview.entity.UserFeedback;
import com.group5.interview.module.feedback.dto.FeedbackSubmitRequest;
import com.group5.interview.module.feedback.dto.FeedbackView;
import com.group5.interview.repository.UserFeedbackRepository;
import com.group5.interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户反馈（P3，docs/06 §用户反馈）：用户提交/查看自己反馈，管理端列表与回复处理。
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Set<String> STATUSES = Set.of("new", "processing", "done");

    private final UserFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @Transactional
    public FeedbackView submit(Long userId, FeedbackSubmitRequest request) {
        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setCategory(request.category());
        feedback.setContent(request.content().trim());
        feedback.setContact(blankToNull(request.contact()));
        feedback.setStatus("new");
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);
        return toView(feedback, Map.of(userId, emailOf(userId)));
    }

    @Transactional(readOnly = true)
    public List<FeedbackView> listMine(Long userId) {
        Map<Long, String> emails = Map.of(userId, emailOf(userId));
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> toView(f, emails))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<FeedbackView> adminPage(String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        // 2026-09-07：后台反馈列表仅显示普通用户提交（隐藏管理员账号自己提交的反馈行）
        Page<UserFeedback> result = (status == null || status.isBlank())
                ? feedbackRepository.findAllNonAdminByOrderByCreatedAtDesc(pageable)
                : feedbackRepository.findByStatusNonAdminByOrderByCreatedAtDesc(status, pageable);
        Map<Long, String> emails = emailsOf(result.getContent());
        return new PageResult<>(result.getContent().stream().map(f -> toView(f, emails)).toList(),
                result.getTotalElements());
    }

    @Transactional
    public FeedbackView reply(Long id, String status, String reply) {
        UserFeedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "反馈不存在"));
        if (reply != null) {
            String trimmed = reply.trim();
            if (!trimmed.isEmpty()) {
                feedback.setReply(trimmed);
                feedback.setRepliedAt(LocalDateTime.now());
                feedback.setStatus("done");
            }
        }
        if (status != null && !status.isBlank()) {
            if (!STATUSES.contains(status)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "status 仅支持 new/processing/done");
            }
            feedback.setStatus(status);
        }
        feedbackRepository.save(feedback);
        return toView(feedback, Map.of(feedback.getUserId(), emailOf(feedback.getUserId())));
    }

    private Map<Long, String> emailsOf(List<UserFeedback> feedbacks) {
        Set<Long> ids = feedbacks.stream().map(UserFeedback::getUserId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));
    }

    private String emailOf(Long userId) {
        return userRepository.findById(userId).map(User::getEmail).orElse(null);
    }

    private FeedbackView toView(UserFeedback f, Map<Long, String> emails) {
        return new FeedbackView(f.getId(), f.getUserId(), emails.get(f.getUserId()), f.getCategory(),
                f.getContent(), f.getContact(), f.getStatus(), f.getReply(), f.getRepliedAt(), f.getCreatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
