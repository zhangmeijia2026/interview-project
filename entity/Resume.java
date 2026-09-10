package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "resumes")
public class Resume {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "file_name", nullable = false, length = 200) private String fileName;
    @Column(name = "file_path", nullable = false, length = 500) private String filePath;
    @Column(name = "file_md5", nullable = false, length = 32) private String fileMd5;
    @Column(name = "full_text", columnDefinition = "LONGTEXT") private String fullText;
    @Column(name = "parsed_data", columnDefinition = "json") private String parsedData;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
