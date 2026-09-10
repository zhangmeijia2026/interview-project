package com.group5.interview.module.user.dto;

public record MeResponse(Long id, String email, String nickname, String avatarUrl, String role,
                         SettingsResponse settings) {
}
