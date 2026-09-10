package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(nullable = false, length = 50)
    private String nickname;
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Column(name = "role", nullable = false, length = 16)
    private String role = "user";

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
