package com.group5.interview.module.match.dto;

import java.util.List;

/** 面试重点的结构化结果（R4）。 */
public record FocusOut(List<FocusItem> focus) {
    public FocusOut {
        if (focus == null) focus = List.of();
    }
}
