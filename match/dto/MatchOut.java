package com.group5.interview.module.match.dto;

import java.util.List;

/** 纯 LLM 匹配的结构化结果（R3）。分数 0-100。 */
public record MatchOut(int overall, int skill, int experience, int education,
                       List<GapItem> gap, String summary) {
    public MatchOut {
        if (gap == null) gap = List.of();
    }
}
