package com.group5.interview.module.weak.dto;

import java.util.List;

/** weak-extract 输出：识别出的薄弱技能列表（真实/离线同构）。severity 0~100，越低越弱。 */
public record WeakExtractOut(List<SkillItem> skills) {

    public WeakExtractOut {
        if (skills == null) skills = List.of();
    }

    public record SkillItem(String skillTag, String category, int severity,
                            List<String> evidence, String suggestion) {
        public SkillItem {
            if (evidence == null) evidence = List.of();
            severity = Math.max(0, Math.min(100, severity));
        }
    }
}
