package com.group5.interview.module.user.dto;

/**
 * 个人中心统计卡（P3，docs/06 §个人中心）：
 * averageScore 为已完成会话中已作答题目的平均分(0-100)；latestRating 为最近一次
 * 完成会话的评级（练习模式为 null，避免误导性评级）。
 */
public record UserStatsResponse(
        int interviewCount,
        int formalCount,
        int practiceCount,
        int completedCount,
        int wrongQuestionCount,
        int weakSkillCount,
        double averageScore,
        String latestMode,
        String latestRating) {
}
