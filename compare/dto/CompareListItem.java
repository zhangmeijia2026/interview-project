package com.group5.interview.module.compare.dto;

import java.time.LocalDateTime;

/** 对比历史列表项（轻量，不含完整结果）。 */
public record CompareListItem(
        Long id,
        Long resumeAId,
        Long resumeBId,
        String title,
        String differenceSummary,
        LocalDateTime createdAt) {
}
