package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_settings")
public class UserSettings {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "model_provider", nullable = false, length = 32)
    private String modelProvider = "deepseek";
    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName = "deepseek-chat";
    @Column(nullable = false, length = 8)
    private String language = "zh";
    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled = true;
    @Column(nullable = false, length = 16)
    private String theme = "light";
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
