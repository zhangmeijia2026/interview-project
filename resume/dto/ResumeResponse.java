package com.group5.interview.module.resume.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record ResumeResponse(Long id, String fileName, String status, JsonNode parsedData, LocalDateTime createdAt) {}
