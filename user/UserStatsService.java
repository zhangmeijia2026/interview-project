package com.group5.interview.module.user;

import com.group5.interview.entity.Interview;
import com.group5.interview.module.interview.InterviewStatus;
import com.group5.interview.module.user.dto.UserStatsResponse;
import com.group5.interview.repository.InterviewQuestionRepository;
import com.group5.interview.repository.InterviewRepository;
import com.group5.interview.repository.UserWeakSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 个人中心统计（P3）：面试/练习次数、完成数、均分、错题数、薄弱技能数、最近评级。
 * 一次拉取本人的面试列表在内存统计，个人规模下代价可忽略，避免多条 count 查询。
 */
@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository questionRepository;
    private final UserWeakSkillRepository weakSkillRepository;

    @Transactional(readOnly = true)
    public UserStatsResponse stats(Long userId) {
        List<Interview> all = interviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int formal = 0, practice = 0, completed = 0;
        String latestMode = null, latestRating = null;
        for (Interview interview : all) {
            if ("practice".equals(interview.getMode())) practice++;
            else formal++;
            if (InterviewStatus.COMPLETED.value().equals(interview.getStatus())) {
                completed++;
                if (latestMode == null) { // 列表按创建倒序，取最近完成的一条
                    latestMode = interview.getMode();
                    latestRating = interview.getOverallRating();
                }
            }
        }
        List<Object[]> aggRows = questionRepository.aggregateUserCompleted(userId);
        double averageScore = aggRows.isEmpty() ? 0.0 : round(((Number) aggRows.get(0)[0]).doubleValue());
        long wrong = questionRepository.countWrongByUserCompleted(userId);
        long weak = weakSkillRepository.countByUserIdAndStatusIn(userId, List.of("tracking", "improving"));
        return new UserStatsResponse(all.size(), formal, practice, completed,
                (int) wrong, (int) weak, averageScore, latestMode, latestRating);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
