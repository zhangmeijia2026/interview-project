package com.group5.interview.module.jd.dto;

import java.time.LocalDateTime;

/**
 * 我的 JD 保留库行视图（P3，docs/06 §JD 库）。
 * type=file 表示有原始文件（可 /file 拉回原图/原 PDF/DOCX），type=text 为直接粘贴的原文。
 * textPreview 为首行职位名/摘要，供列表展示与选择。
 */
public record JdLibraryItem(Long id, String type, String fileName, String status,
                            String textPreview, LocalDateTime createdAt) {
}
