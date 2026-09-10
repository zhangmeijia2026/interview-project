package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 用户反馈。 */
@Getter
@Setter
@Entity
@Table(name = "user_feedback")
public class UserFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 32) private String category = "suggestion";
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String content;
    @Column(length = 100) private String contact;
    @Column(nullable = false, length = 16) private String status = "new";
    @Column(columnDefinition = "LONGTEXT") private String reply;
    @Column(name = "replied_at") private LocalDateTime repliedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
