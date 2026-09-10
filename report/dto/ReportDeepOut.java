package com.group5.interview.module.report.dto;

import java.util.List;

/**
 * 深度报告分析（report-analysis）的统一返回结构。
 * 真实模型与离线兜底必须输出同一结构，保证前后端契约稳定。
 */
public record ReportDeepOut(
        String overallAnalysis,
        List<DimensionAnalysis> dimensionAnalyses,
        String conclusion,
        ResumeAdvice resumeAdvice) {

    /** 单维深度点评：score 必须等于输入维度分，不得改写。 */
    public record DimensionAnalysis(String dimension, int score, String analysis, String advice) {
    }

    /** 综合简历修改建议：分优先级分组。 */
    public record ResumeAdvice(String intro, List<AdviceGroup> groups) {
    }

    /** 一组简历修改建议。priority 仅允许 HIGH/MED/LOW。 */
    public record AdviceGroup(String title, String priority, List<String> items) {
    }
}
