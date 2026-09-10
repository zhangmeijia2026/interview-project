package com.group5.interview.module.engine.dto;

/** 面试进度快照（断点续传用）。 */
public record SessionResponse(String status, int questionCount, int completedCount, QuestionSummary current) {
}
