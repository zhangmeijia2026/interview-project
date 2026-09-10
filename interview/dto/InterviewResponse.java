package com.group5.interview.module.interview.dto;

import java.time.LocalDateTime;

/** 面试会话对外视图。practiceCategory 为 null 表示未限定分类（按薄弱/随机抽题）。 */
public record InterviewResponse(Long id, String title, String targetPosition, String status, Long resumeId,
                                Long jdId, int questionCount, int completedQuestionCount, LocalDateTime createdAt,
                                String mode, boolean weakBoost, String practiceCategory) {}
