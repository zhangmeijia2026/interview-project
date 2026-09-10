package com.group5.interview.module.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理端"某用户面试"列表行视图（用户管理→查看面试）。 */
public record AdminInterviewView(
        Long id,
        String title,
        String targetPosition,
        String status,
        String mode,
        int questionCount,
        int completedQuestionCount,
        BigDecimal matchScore,
        String overallRating,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        boolean hasReport) {
}
