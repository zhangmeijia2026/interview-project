package com.group5.interview.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSettingsRequest(
        @NotBlank @Size(max = 32) String modelProvider,
        @NotBlank @Size(max = 64) String modelName,
        @NotBlank @Size(max = 8) String language,
        boolean notifyEnabled) {
}
