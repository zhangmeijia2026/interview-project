package com.group5.interview.module.questionbank.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 管理员新增/修改题库条目。 */
public record BankUpsertRequest(@NotBlank @Size(max = 32) String category,
                                @NotBlank @Size(max = 16) String questionType,
                                @Min(1) @Max(5) Integer difficulty,
                                @NotBlank String content,
                                String answer,
                                String hint,
                                List<String> knowledgePoints) {
}
