package com.group5.interview.module.engine.dto;

import java.util.List;

/**
 * 提交作答后的返回：评分 + 对简历的修改建议 + （可能的）追问 + （若有）下一题。
 * interviewStatus=in_progress/completed；finished=true 表示整场面试已结束。
 *
 * @param mode            本场模式（formal/practice）
 * @param referenceAnswer 本题参考回答（R8；practice 作答后即可见；formal 作答后可见）
 * @param hint            本题提示（practice 随时可见；formal 作答后可见；未答时前端走 /reference 端点判断可见性）
 */
public record AnswerResponse(int orderIndex, int score, List<String> strong, List<String> weak,
                             String suggestion, boolean suggestFollowup, FollowUpSummary followUp,
                             QuestionSummary nextQuestion, String interviewStatus, boolean finished,
                             String resumeAdvice, String mode, String referenceAnswer, String hint) {
}
