package com.group5.interview.module.jd.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record JdResponse(Long id, String fileName, String status, JsonNode parsedData, LocalDateTime createdAt) {
}
