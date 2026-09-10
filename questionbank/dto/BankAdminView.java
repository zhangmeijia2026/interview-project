package com.group5.interview.module.questionbank.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 管理员题库管理视图（含答案/启用态）。 */
public record BankAdminView(Long id, String category, String questionType, int difficulty,
                            String content, String answer, String hint, List<String> knowledgePoints,
                            boolean sourceAi, boolean enabled, int usageCount, LocalDateTime updatedAt) {
}
