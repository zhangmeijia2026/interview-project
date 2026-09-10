package com.group5.interview.module.feedback.dto;

import java.time.LocalDateTime;

/**
 * 反馈视图（用户本人列表与管理端列表共用）：userEmail 供管理端展示提交人；
 * 状态流转 new -> processing -> done，reply 为管理员回复。
 */
public record FeedbackView(
        Long id,
        Long userId,
        String userEmail,
        String category,
        String content,
        String contact,
        String status,
        String reply,
        LocalDateTime repliedAt,
        LocalDateTime createdAt) {
}
