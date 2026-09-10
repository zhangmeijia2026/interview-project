package com.group5.interview.module.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FollowUpRequest(@NotBlank @Size(max = 4000) String answer) {
}
