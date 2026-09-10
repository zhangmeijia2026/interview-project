package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "interview_questions")
public class InterviewQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "interview_id", nullable = false) private Long interviewId;
    @Column(name = "focus_index", nullable = false) private int focusIndex;
    @Column(name = "order_index", nullable = false) private int orderIndex;
    @Column(name = "question_bank_id") private Long questionBankId;
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT") private String content;
    @Column(name = "reference_answer", columnDefinition = "LONGTEXT") private String referenceAnswer;
    @Column(name = "hint", columnDefinition = "LONGTEXT") private String hint;
    @Column(name = "answered_seconds") private Integer answeredSeconds;
    @Column(name = "user_answer", columnDefinition = "LONGTEXT") private String userAnswer;
    @Column(name = "score", precision = 5, scale = 2) private BigDecimal score;
    @Column(name = "feedback_strong", columnDefinition = "json") private String feedbackStrong;
    @Column(name = "feedback_weak", columnDefinition = "json") private String feedbackWeak;
    @Column(name = "feedback_suggestion", columnDefinition = "LONGTEXT") private String feedbackSuggestion;
    @Column(name = "resume_advice", columnDefinition = "LONGTEXT") private String resumeAdvice;
    @Column(name = "follow_up_question", columnDefinition = "LONGTEXT") private String followUpQuestion;
    @Column(name = "follow_up_answer", columnDefinition = "LONGTEXT") private String followUpAnswer;
    @Column(name = "follow_up_score", precision = 5, scale = 2) private BigDecimal followUpScore;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
