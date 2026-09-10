package com.group5.interview.module.engine.dto;

import java.util.List;

/** 本题点评（R6）输出。resumeAdvice=对简历的修改建议（每题给出，落 interview_questions.resume_advice）。 */
public record GradeOut(int score, List<String> strong, List<String> weak,
                       String suggestion, boolean suggestFollowup, String resumeAdvice) {
    public GradeOut {
        if (strong == null) strong = List.of();
        if (weak == null) weak = List.of();
    }
}
