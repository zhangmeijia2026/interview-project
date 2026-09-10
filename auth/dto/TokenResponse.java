package com.group5.interview.module.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, UserSummary user) {
}
