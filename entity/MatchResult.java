package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "match_results")
public class MatchResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "interview_id", nullable = false) private Long interviewId;
    @Column(name = "overall_score", nullable = false, precision = 5, scale = 2) private BigDecimal overallScore;
    @Column(name = "skill_match", nullable = false, precision = 5, scale = 2) private BigDecimal skillMatch;
    @Column(name = "experience_match", nullable = false, precision = 5, scale = 2) private BigDecimal experienceMatch;
    @Column(name = "education_match", nullable = false, precision = 5, scale = 2) private BigDecimal educationMatch;
    @Column(name = "gap_analysis", nullable = false, columnDefinition = "json") private String gapAnalysis;
    @Column(name = "interview_focus", nullable = false, columnDefinition = "json") private String interviewFocus;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
