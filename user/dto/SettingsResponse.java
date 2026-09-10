package com.group5.interview.module.user.dto;

public record SettingsResponse(String modelProvider, String modelName, String language,
                               boolean notifyEnabled, String theme) {
}
