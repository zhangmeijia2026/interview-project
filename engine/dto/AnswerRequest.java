package com.group5.interview.module.engine.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交当前题目作答。elapsedSeconds=本题作答耗时（秒，仅正式模式前端计时上报，服务端校验 0~3600）。
 */
public record AnswerRequest(@NotBlank @Size(max = 8000) String answer,
                            @Min(0) @Max(3600) Integer elapsedSeconds) {

    public AnswerRequest(String answer) {
        this(answer, null);
    }
}
