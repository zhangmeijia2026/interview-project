package com.group5.interview.ai;

import com.group5.interview.entity.LlmCallLog;
import com.group5.interview.repository.LlmCallLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 大模型调用日志落库（管理端统计 DeepSeek 调用情况，R9）。
 * 埋点位置：DeepSeekAIService（真实调用路径）。离线/本地算法路径不产生模型调用，不落库。
 * 计价与 CostGuard 同口径：中文 1 字符≈1 token，按单价折算元（docs/07 §6）。
 */
@Slf4j
@Service
public class LlmCallLogService {

    private final LlmCallLogRepository repository;
    @Value("${ai.cost.input-price-per-million:2.0}") private double inPricePerMillion;
    @Value("${ai.cost.output-price-per-million:8.0}") private double outPricePerMillion;

    public LlmCallLogService(LlmCallLogRepository repository) {
        this.repository = repository;
    }

    /** 幂等记录一次真实模型调用结果；自身异常不向上抛，避免影响主流程。 */
    public void record(String promptKey, String model, Long userId,
                       int inChars, int outChars, long latencyMs, boolean fallback, String status) {
        try {
            LlmCallLog logRow = new LlmCallLog();
            logRow.setPromptKey(promptKey);
            logRow.setModel(model);
            logRow.setUserId(userId);
            logRow.setInChars(Math.max(0, inChars));
            logRow.setOutChars(Math.max(0, outChars));
            logRow.setEstCost(estimate(inChars, outChars));
            logRow.setLatencyMs((int) Math.min(Integer.MAX_VALUE, latencyMs));
            logRow.setFallback(fallback);
            logRow.setStatus(status);
            logRow.setCreatedAt(LocalDateTime.now());
            repository.save(logRow);
        } catch (Exception e) {
            log.warn("[ai] 调用日志落库失败(不影响主流程): {}", e.getMessage());
        }
    }

    private BigDecimal estimate(int inChars, int outChars) {
        double cost = inChars / 1_000_000.0 * inPricePerMillion
                + outChars / 1_000_000.0 * outPricePerMillion;
        return BigDecimal.valueOf(cost).setScale(4, RoundingMode.HALF_UP);
    }
}
