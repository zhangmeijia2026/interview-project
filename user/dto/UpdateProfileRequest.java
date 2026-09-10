package com.group5.interview.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 50) String nickname,
        @Size(max = 500) String avatarUrl) {
}
