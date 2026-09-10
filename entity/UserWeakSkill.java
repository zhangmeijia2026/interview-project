package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 个人薄弱技能（跨场次汇总，AI 依报告识别后 upsert）。 */
@Getter
@Setter
@Entity
@Table(name = "user_weak_skills")
public class UserWeakSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "skill_tag", nullable = false, length = 64) private String skillTag;
    @Column(nullable = false, length = 32) private String category = "general";
    /** 0~100，越低调越弱（AI 依报告给出）。 */
    @Column(nullable = false) private int severity = 50;
    @Column(name = "latest_score") private Integer latestScore;
    @Column(name = "interview_count", nullable = false) private int interviewCount = 0;
    @Column(name = "first_seen", nullable = false) private LocalDateTime firstSeen;
    @Column(name = "last_seen", nullable = false) private LocalDateTime lastSeen;
    @Column(name = "evidence_json", columnDefinition = "json") private String evidenceJson;
    @Column(nullable = false, length = 16) private String status = "tracking";
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
