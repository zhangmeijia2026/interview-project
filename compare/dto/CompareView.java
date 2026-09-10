package com.group5.interview.module.compare.dto;

import java.time.LocalDateTime;

/** 单次对比的完整视图（含 AI 结果，逐条可回溯到两份简历）。 */
public record CompareView(
        Long id,
        Long resumeAId,
        Long resumeBId,
        String title,
        CompareResumesOut result,
        LocalDateTime createdAt) {
}
