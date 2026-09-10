package com.group5.interview.module.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户提交反馈（P3，docs/06 §用户反馈）。
 * category：suggestion(建议)/bug(缺陷)/question(咨询)/praise(表扬)/other(其他)。
 */
public record FeedbackSubmitRequest(
        @NotBlank(message = "反馈分类不能为空")
        @Pattern(regexp = "suggestion|bug|question|praise|other", message = "category 仅支持 suggestion/bug/question/praise/other")
        String category,
        @NotBlank(message = "反馈内容不能为空")
        @Size(max = 2000, message = "反馈内容不能超过 2000 字")
        String content,
        @Size(max = 100, message = "联系方式不能超过 100 字")
        String contact) {
}
