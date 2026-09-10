package com.group5.interview.module.weak.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 个人薄弱技能库条目视图。severity 越低越弱，列表按 severity 升序。 */
public record WeakSkillView(Long id, String skillTag, String category, int severity, Integer latestScore,
                            int interviewCount, LocalDateTime lastSeen, String status, List<String> evidence) {
}
