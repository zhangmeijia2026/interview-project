package com.group5.interview.module.engine.dto;

/** 当前/下一道题的对外视图。 */
public record QuestionSummary(int orderIndex, int focusIndex, String content) {
}
