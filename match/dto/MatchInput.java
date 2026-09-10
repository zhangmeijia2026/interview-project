package com.group5.interview.module.match.dto;

import com.group5.interview.module.jd.dto.JdParsed;
import com.group5.interview.module.resume.dto.ResumeParsed;

/** 匹配步骤（R3）喂给 AIService 的上下文。 */
public record MatchInput(ResumeParsed resume, JdParsed jd, int questionCount, String targetPosition) {
}
