package com.group5.interview.module.engine.dto;

/** 面试官提问（R5）输出；basis 为出题依据，MVP 前端展示"依据"用。 */
public record QuestionOut(String content, String basis, String referenceAnswer, String hint) {

    public QuestionOut(String content, String basis) {
        this(content, basis, null, null);
    }
}
