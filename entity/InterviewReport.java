package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "interview_reports")
public class InterviewReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "interview_id", nullable = false) private Long interviewId;
    @Column(name = "overall_rating", length = 1) private String overallRating;
    @Column(name = "dimensions", columnDefinition = "json") private String dimensions;
    @Column(name = "review_summary", columnDefinition = "LONGTEXT") private String reviewSummary;
    @Column(name = "strengths", columnDefinition = "json") private String strengths;
    @Column(name = "improvements", columnDefinition = "json") private String improvements;
    @Column(name = "recommendation", columnDefinition = "LONGTEXT") private String recommendation;
    @Column(name = "raw_llm_json", columnDefinition = "json") private String rawLlmJson;
    @Column(name = "analysis_json", columnDefinition = "json") private String analysisJson;
    @Column(name = "resume_advice_json", columnDefinition = "json") private String resumeAdviceJson;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
