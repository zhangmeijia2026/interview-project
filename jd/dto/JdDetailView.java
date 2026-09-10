package com.group5.interview.module.jd.dto;

import java.time.LocalDateTime;

/**
 * JD 详情视图：返回解析后文本（rawText），file 类型也可叠加 /file 看原图/原文档。
 * 用于"历史岗位 JD 查看其文本/原文"，docs/06 §JD 库。
 */
public record JdDetailView(Long id, String type, String fileName, String status,
                           String rawText, LocalDateTime createdAt) {
}
