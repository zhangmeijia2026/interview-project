package com.group5.interview.module.weak.dto;

/** 错题本条目：低分(<60)题目快照（含作答与参考答案，供复盘/重练）。 */
public record WrongQuestionView(Long interviewId, String title, String mode, int orderIndex,
                                Long questionBankId, String category, String question, String myAnswer,
                                Integer score, String referenceAnswer, String hint) {
}
