package com.group5.interview.module.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * F06 综合报告对外视图（docs/06 F06/F07 口径）。
 * dimensions 顺序固定：job_match/professional/expression/logic/adaptability/learning。
 *
 * @param analysis    面试结果详细分析（report-analysis，docs/07 §5），历史旧报告或离线兜底为空/为本地规则结果
 * @param resumeAdvice 详细简历修改建议（分组、按优先级），同上
 */
public record ReportResponse(String rating, String mode, BigDecimal matchScore, Map<String, Integer> dimensions,
                             List<QuestionReplay> questions, List<String> strengths,
                             List<String> improvements, String recommendation,
                             ReportDeepOut analysis, ReportDeepOut.ResumeAdvice resumeAdvice) {

    /** 逐题回放：题目/作答/得分/点评/对简历的修改建议/参考回答/提示/追问。 */
    public record QuestionReplay(int orderIndex, String question, String answer, Integer score,
                                 List<String> strong, List<String> weak, String suggestion,
                                 String resumeAdvice, String referenceAnswer, String hint,
                                 FollowUpView followUp) {}

    /** 追问视图（未产生追问则为 null）。 */
    public record FollowUpView(String question, String answer, Integer score) {}
}
