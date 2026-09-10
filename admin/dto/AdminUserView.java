package com.group5.interview.module.admin.dto;

import java.time.LocalDateTime;

/** 管理端用户列表行视图。 */
public record AdminUserView(
        Long id,
        String email,
        String nickname,
        String role,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt) {
}
