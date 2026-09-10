package com.group5.interview.module.questionbank.dto;

import java.util.List;

/** 题库浏览卡片（不含 answer/hint，避免未作答前剧透）。 */
public record BankQuestionView(Long id, String category, String questionType, int difficulty,
                               String content, List<String> knowledgePoints) {
}
