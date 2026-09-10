package com.group5.interview.module.compare.dto;

import java.util.List;

/**
 * 两份简历对比结果（与 compare-resumes prompt 输出 JSON 对齐）。
 * 逐条结论都须能从两份结构化简历直接回溯，禁止编造。
 */
public record CompareResumesOut(
        String resumeASummary,
        String resumeBSummary,
        List<CompareRow> fields,
        List<String> skillOverlap,
        List<String> onlyA,
        List<String> onlyB,
        List<String> strengthsA,
        List<String> strengthsB,
        String differenceSummary,
        List<String> advice) {

    public CompareResumesOut {
        fields = fields == null ? List.of() : fields;
        skillOverlap = skillOverlap == null ? List.of() : skillOverlap;
        onlyA = onlyA == null ? List.of() : onlyA;
        onlyB = onlyB == null ? List.of() : onlyB;
        strengthsA = strengthsA == null ? List.of() : strengthsA;
        strengthsB = strengthsB == null ? List.of() : strengthsB;
        advice = advice == null ? List.of() : advice;
    }

    public record CompareRow(String field, String aValue, String bValue, String note) {
    }
}
