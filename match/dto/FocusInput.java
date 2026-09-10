package com.group5.interview.module.match.dto;

import com.group5.interview.module.resume.dto.ResumeParsed;

import java.util.List;

/** 面试重点步骤（R4）喂给 AIService 的上下文。 */
public record FocusInput(List<GapItem> gap, int questionCount, String targetPosition, ResumeParsed resume) {
}
