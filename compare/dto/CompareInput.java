package com.group5.interview.module.compare.dto;

import com.group5.interview.module.resume.dto.ResumeParsed;

/**
 * compare-resumes prompt 上下文：两份结构化简历 + 可选的求职意向岗位。
 * 顶层字段名即 prompt 模板占位符，故命名 resumeAName/resumeBName/resumeA/resumeB/targetPosition。
 */
public record CompareInput(
        String resumeAName,
        String resumeBName,
        ResumeParsed resumeA,
        ResumeParsed resumeB,
        String targetPosition) {
}
