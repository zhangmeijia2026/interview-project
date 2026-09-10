package com.group5.interview.module.history;

import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.Interview;
import com.group5.interview.module.history.dto.HistoryItem;
import com.group5.interview.repository.InterviewReportRepository;
import com.group5.interview.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F07 历史：按用户列出/检索/删除历史会话。
 * 删除只删面试主记录，JD/简历等素材保留（供其他会话复用）。
 */
@Service
@RequiredArgsConstructor
public class HistoryService {
    private final InterviewRepository interviewRepository;
    private final InterviewReportRepository reportRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> list(Long userId, String keyword, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        List<Interview> all = interviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Interview> filtered = kw.isEmpty() ? all : all.stream()
                .filter(i -> (i.getTitle() != null && i.getTitle().toLowerCase().contains(kw))
                        || (i.getTargetPosition() != null && i.getTargetPosition().toLowerCase().contains(kw)))
                .toList();
        int total = filtered.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<HistoryItem> pageItems = filtered.subList(from, to).stream()
                .map(this::toItem)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("list", pageItems);
        body.put("total", total);
        body.put("page", safePage);
        body.put("size", safeSize);
        return body;
    }

    @Transactional
    public void deleteOne(Long userId, Long interviewId) {
        Interview interview = interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在或无权访问"));
        interviewRepository.delete(interview);
    }

    @Transactional
    public int clear(Long userId) {
        List<Interview> mine = interviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (mine.isEmpty()) return 0;
        interviewRepository.deleteAll(mine);
        return mine.size();
    }

    private HistoryItem toItem(Interview i) {
        boolean hasReport = reportRepository.findByInterviewId(i.getId()).isPresent();
        return new HistoryItem(i.getId(), i.getTitle(), i.getTargetPosition(), i.getStatus(),
                i.getQuestionCount(), i.getCompletedQuestionCount(), i.getMatchScore(),
                i.getOverallRating(), i.getMode(), i.isWeakBoost(),
                i.getCreatedAt(), i.getCompletedAt(), hasReport);
    }
}
