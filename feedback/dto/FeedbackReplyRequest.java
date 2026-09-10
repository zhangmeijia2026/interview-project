package com.group5.interview.module.feedback.dto;

import jakarta.validation.constraints.Size;

/**
 * 管理员处理反馈：status=new|processing|done（可空=保持），reply=回复内容（非空时置 done）。
 */
public record FeedbackReplyRequest(
        @Size(max = 16, message = "status 过长")
        String status,
        @Size(max = 1000, message = "回复内容不能超过 1000 字")
        String reply) {
}
