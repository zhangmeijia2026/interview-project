package com.group5.interview.module.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建面试请求（docs/06 F01）。
 *
 * @param mode            面试模式：formal=正式（完整计时/严格评分/出评级）｜practice=练习（题库抽题/随时看提示与参考答案/出复盘无评级）
 * @param weakBoost       是否针对个人薄弱技能加强提问（practice 常与练习搭配；formal 也会把薄弱技能注入出题偏置）
 * @param practiceCategory 练习模式的题库分类（可选；为空=按薄弱点/随机从全题库抽题）
 */
public record CreateInterviewRequest(@NotBlank @Size(max = 200) String title,
                                     @NotBlank @Size(max = 100) String targetPosition,
                                     @Min(1) @Max(20) Integer questionCount,
                                     @Pattern(regexp = "formal|practice", message = "mode 仅支持 formal 或 practice")
                                     String mode,
                                     Boolean weakBoost,
                                     @Size(max = 32) String practiceCategory) {}
