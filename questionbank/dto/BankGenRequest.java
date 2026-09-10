package com.group5.interview.module.questionbank.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员一键 AI 扩充题库。 */
public record BankGenRequest(@NotBlank @Size(max = 32) String category,
                             @Min(1) @Max(20) Integer count,
                             @Min(1) @Max(5) Integer difficulty) {
}
