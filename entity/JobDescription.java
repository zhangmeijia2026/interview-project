package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "job_descriptions")
public class JobDescription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "file_name", length = 200) private String fileName;
    @Column(name = "file_path", length = 500) private String filePath;
    @Column(name = "raw_text", nullable = false, columnDefinition = "LONGTEXT") private String rawText;
    @Column(name = "parsed_data", nullable = false, columnDefinition = "json") private String parsedData;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
