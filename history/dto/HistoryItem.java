package com.group5.interview.module.history.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 历史会话行视图（面试主记录 + 报告摘要字段）。mode=formal|practice。 */
public record HistoryItem(Long id, String title, String targetPosition, String status,
                          int questionCount, int completedQuestionCount, BigDecimal matchScore,
                          String overallRating, String mode, boolean weakBoost,
                          LocalDateTime createdAt, LocalDateTime completedAt,
                          boolean hasReport) {
}

