package com.group5.interview.module.report.dto;

import java.util.List;
import java.util.Map;

/**
 * 深度报告分析（report-analysis）喂给 AIService 的上下文。
 * 全部来自落库数据，模型不得修改分数，只能基于给定事实输出分析文字。
 */
public record ReportAnalysisInput(
        Long interviewId,
        String title,
        String targetPosition,
        int questionCount,
        int completedCount,
        String rating,
        Integer compositeScore,
        Map<String, Integer> dimensions,
        String matchSummary,
        List<String> matchGaps,
        List<String> strengths,
        List<String> improvements,
        String recommendation,
        List<QuestionBrief> questions) {

    /** 逐题简报：answer 由调用方先截断以控制 token。 */
    public record QuestionBrief(int orderIndex, String question, int score, String answer,
                                List<String> strong, List<String> weak, String suggestion,
                                String resumeAdvice, Integer followUpScore) {
    }
}
