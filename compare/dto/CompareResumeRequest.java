package com.group5.interview.module.compare.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 两份简历对比请求：两者须同属当前用户且解析完成（ready）。
 * targetPosition 可选——给定后对比结论会结合目标岗位倾向给出建议。
 */
public record CompareResumeRequest(
        @NotNull(message = "请选择简历 A")
        Long resumeAId,
        @NotNull(message = "请选择简历 B")
        Long resumeBId,
        @Size(max = 100, message = "目标岗位不能超过 100 字")
        String targetPosition) {
}
