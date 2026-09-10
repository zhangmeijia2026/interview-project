package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 大模型(DeepSeek)调用日志，供管理端统计。 */
@Getter
@Setter
@Entity
@Table(name = "llm_call_logs")
public class LlmCallLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id") private Long userId;
    @Column(name = "prompt_key", nullable = false, length = 32) private String promptKey;
    @Column(length = 64) private String model;
    @Column(name = "in_chars", nullable = false) private int inChars;
    @Column(name = "out_chars", nullable = false) private int outChars;
    @Column(name = "est_cost", nullable = false, precision = 8, scale = 4) private BigDecimal estCost;
    @Column(name = "latency_ms", nullable = false) private int latencyMs;
    @Column(nullable = false) private boolean fallback;
    @Column(nullable = false, length = 16) private String status = "ok";
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
