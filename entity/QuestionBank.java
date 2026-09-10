package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 题库题目（Java/Python 等分类）。 */
@Getter
@Setter
@Entity
@Table(name = "question_bank")
public class QuestionBank {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32) private String category;
    @Column(name = "question_type", nullable = false, length = 16) private String questionType = "qa";
    @Column(nullable = false) private int difficulty = 3;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String content;
    @Column(columnDefinition = "LONGTEXT") private String answer;
    @Column(columnDefinition = "LONGTEXT") private String hint;
    @Column(name = "knowledge_points", columnDefinition = "json") private String knowledgePoints;
    @Column(name = "source_ai", nullable = false) private boolean sourceAi = false;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "usage_count", nullable = false) private int usageCount = 0;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
