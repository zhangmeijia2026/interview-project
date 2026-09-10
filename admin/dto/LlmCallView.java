package com.group5.interview.module.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DeepSeek 调用日志明细视图（管理端，docs/06 §模型调用统计）。 */
public record LlmCallView(
        Long id,
        Long userId,
        String promptKey,
        String model,
        int inChars,
        int outChars,
        BigDecimal estCost,
        int latencyMs,
        boolean fallback,
        String status,
        LocalDateTime createdAt) {
}
