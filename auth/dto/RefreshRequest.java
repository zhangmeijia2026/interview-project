package com.group5.interview.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {
}
