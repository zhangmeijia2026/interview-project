package com.group5.interview.module.engine.dto;

/**
 * 评分步骤（R6）上下文。mode=formal|practice：正式严格打分，练习侧重鼓励与对照参考回答的复盘。
 * referenceAnswer（可空）：本题参考答案/答题要点。评分按"与参考答案的要点覆盖度"评判（离线与真实同一口径，见 docs/07 §6）。
 */
public record GradeInput(String question, String examinePoint, String answer,
                         int orderIndex, String targetPosition, String mode, String referenceAnswer) {

    public GradeInput(String question, String examinePoint, String answer, int orderIndex, String targetPosition) {
        this(question, examinePoint, answer, orderIndex, targetPosition, "formal", null);
    }

    public GradeInput(String question, String examinePoint, String answer,
                      int orderIndex, String targetPosition, String mode) {
        this(question, examinePoint, answer, orderIndex, targetPosition, mode, null);
    }
}
