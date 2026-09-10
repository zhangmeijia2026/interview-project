package com.group5.interview.module.weak.dto;

import java.util.List;
import java.util.Map;

/**
 * weak-extract 输入：一场面试的逐题简报 + 六维 + 匹配差距，全部来自落库数据。
 * 防幻觉三原则：skillTag/evidence/suggestion 必须能从这些事实中回溯，不得捏造。
 */
public record WeakExtractInput(
        Long interviewId,
        String targetPosition,
        String mode,
        String rating,               // 练习模式为 null
        Map<String, Integer> dimensions,
        List<String> matchGaps,
        List<QuestionBrief> questions) {

    /** 逐题简报（answer 已由调用方截断）。 */
    public record QuestionBrief(int orderIndex, String question, int score, String answer,
                                List<String> weak, String suggestion, String resumeAdvice,
                                Integer followUpScore) {
    }
}
