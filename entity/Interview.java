package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "interviews")
public class Interview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 200) private String title;
    @Column(name = "target_position", nullable = false, length = 100) private String targetPosition;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "resume_id") private Long resumeId;
    @Column(name = "jd_id") private Long jdId;
    @Column(name = "match_score", precision = 5, scale = 2) private BigDecimal matchScore;
    @Column(name = "question_count", nullable = false) private int questionCount = 10;
    @Column(name = "completed_question_count", nullable = false) private int completedQuestionCount = 0;
    @Column(name = "mode", nullable = false, length = 16) private String mode = "formal";
    @Column(name = "weak_boost", nullable = false) private boolean weakBoost = false;
    @Column(name = "practice_category", length = 32) private String practiceCategory;
    @Column(name = "overall_rating", length = 1) private String overallRating;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
