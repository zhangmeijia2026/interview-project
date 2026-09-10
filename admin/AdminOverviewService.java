package com.group5.interview.module.admin;

import com.group5.interview.module.interview.InterviewStatus;
import com.group5.interview.repository.InterviewRepository;
import com.group5.interview.repository.QuestionBankRepository;
import com.group5.interview.repository.UserFeedbackRepository;
import com.group5.interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端概览统计（2026-09-07）：后台新首页的汇总卡片数据源。
 */
@Service
@RequiredArgsConstructor
public class AdminOverviewService {

    private final UserRepository userRepository;
    private final InterviewRepository interviewRepository;
    private final UserFeedbackRepository feedbackRepository;
    private final QuestionBankRepository questionBankRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        long completed = interviewRepository.countByStatus(InterviewStatus.COMPLETED.value());
        long inProgress = interviewRepository.countByStatus(InterviewStatus.IN_PROGRESS.value());
        long disabled = userRepository.countByActiveFalse();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userCount", userRepository.count());
        body.put("adminCount", userRepository.countByRoleAndActiveTrue("admin"));
        body.put("disabledUserCount", disabled);
        body.put("interviewCount", interviewRepository.count());
        body.put("completedInterviewCount", completed);
        body.put("inProgressInterviewCount", inProgress);
        body.put("pendingFeedbackCount", feedbackRepository.countByStatus("new"));
        body.put("questionCount", questionBankRepository.countByEnabledTrue());
        return body;
    }
}
