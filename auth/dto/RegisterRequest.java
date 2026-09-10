package com.group5.interview.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 32) String verifyCode) {
}
