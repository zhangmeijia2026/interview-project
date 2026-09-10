package com.group5.interview.module.engine.dto;

import com.group5.interview.module.match.dto.FocusItem;
import com.group5.interview.module.resume.dto.ResumeParsed;

import java.util.List;

/** 出题步骤（R5）上下文。weakSkills=个人薄弱技能标签（weakBoost 时注入，偏置深挖），可为 null。 */
public record QuestionInput(FocusItem focus, ResumeParsed resume, int orderIndex, String targetPosition,
                            List<String> weakSkills) {

    public QuestionInput(FocusItem focus, ResumeParsed resume, int orderIndex, String targetPosition) {
        this(focus, resume, orderIndex, targetPosition, null);
    }
}
