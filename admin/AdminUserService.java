package com.group5.interview.module.admin;

import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.common.PageResult;
import com.group5.interview.entity.Interview;
import com.group5.interview.entity.User;
import com.group5.interview.module.admin.dto.AdminInterviewView;
import com.group5.interview.module.admin.dto.AdminUserView;
import com.group5.interview.module.report.ReportService;
import com.group5.interview.module.report.dto.ReportResponse;
import com.group5.interview.repository.InterviewReportRepository;
import com.group5.interview.repository.InterviewRepository;
import com.group5.interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端用户管理（R9，2026-09-07 扩展）：
 * 检索/列表（昵称/邮箱 + 可选 role/active 过滤）；"查看某用户面试"及其只读报告。
 * 账号停用/新增管理员见 {@link AdminAccountService}（黑名单复用 users.is_active，已即时生效）。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewReportRepository reportRepository;
    private final ReportService reportService;

    @Transactional(readOnly = true)
    public PageResult<AdminUserView> list(String keyword, String role, Boolean active, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String kw = keyword == null ? "" : keyword.trim();
        Page<User> result = userRepository.search(kw, role, active, PageRequest.of(safePage, safeSize));
        return new PageResult<>(result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements());
    }

    /** 查看某用户的全部面试（用户管理→查看面试，替代原后台混乱的"最近面试"墙）。 */
    @Transactional(readOnly = true)
    public List<AdminInterviewView> interviewsOf(Long userId) {
        requireUser(userId);
        return interviewRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toInterviewView).toList();
    }

    /** 只读查看某用户某场面试的报告：校验面试确属该用户后复用报告读取（completed 缺报告会自动补生成）。 */
    public ReportResponse reportOf(Long userId, Long interviewId) {
        requireUser(userId);
        return reportService.get(interviewId, userId);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private AdminInterviewView toInterviewView(Interview i) {
        return new AdminInterviewView(i.getId(), i.getTitle(), i.getTargetPosition(), i.getStatus(),
                i.getMode(), i.getQuestionCount(), i.getCompletedQuestionCount(), i.getMatchScore(),
                i.getOverallRating(), i.getStartedAt(), i.getCompletedAt(), i.getCreatedAt(),
                reportRepository.existsByInterviewId(i.getId()));
    }

    private AdminUserView toView(User u) {
        return new AdminUserView(u.getId(), u.getEmail(), u.getNickname(), u.getRole(),
                u.isActive(), u.getCreatedAt(), u.getLastLoginAt());
    }
}
