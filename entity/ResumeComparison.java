package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 两份简历 AI 对比记录。 */
@Getter
@Setter
@Entity
@Table(name = "resume_comparisons")
public class ResumeComparison {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "resume_a_id", nullable = false) private Long resumeAId;
    @Column(name = "resume_b_id", nullable = false) private Long resumeBId;
    @Column(nullable = false, length = 200) private String title;
    @Column(name = "result_json", columnDefinition = "json") private String resultJson;
    @Column(columnDefinition = "LONGTEXT") private String summary;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
