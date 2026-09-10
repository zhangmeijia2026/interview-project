package com.group5.interview.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 当日模型花费护栏（轻量版）。真实模型调用超预算后停止请求、回落离线引擎，
 * 避免"免费额度/低预算"下失控扣费。
 *
 * <p>计价仅为近似（docs/07 §6 说明按官方单价核对）：中文约 1 字符≈1 token，
 * 再按每百万 token 单价折算人民币。可在配置中覆盖单价或关闭。
 */
@Slf4j
@Component
public class CostGuard {

    private final Map<String, Double> daySpend = new ConcurrentHashMap<>();
    private final AtomicReference<String> warnedDay = new AtomicReference<>();

    @Value("${ai.cost.enabled:true}") private boolean enabled;
    @Value("${ai.cost.daily-limit-yuan:2.0}") private double dailyLimitYuan;
    @Value("${ai.cost.input-price-per-million:2.0}") private double inputPricePerMillion;
    @Value("${ai.cost.output-price-per-million:8.0}") private double outputPricePerMillion;

    public boolean overBudget() {
        if (!enabled) return false;
        return spendOf(today()) >= dailyLimitYuan;
    }

    /** 按输入/输出字符数近似计费入账（无真实 usage 时的最简估算）。 */
    public void charge(long inChars, long outChars) {
        if (!enabled) return;
        double cost = inChars / 1_000_000.0 * inputPricePerMillion
                + outChars / 1_000_000.0 * outputPricePerMillion;
        String day = today();
        double now = daySpend.merge(day, cost, Double::sum);
        if (now >= dailyLimitYuan && warnedDay.compareAndSet(null, day)) {
            log.warn("[ai] 当日模型花费已达 ¥{}（上限 ¥{}），后续请求自动回落离线引擎",
                    String.format("%.2f", now), String.format("%.2f", dailyLimitYuan));
        }
    }

    /** 仅供日志/测试查看。 */
    public double todaySpend() {
        return spendOf(today());
    }

    private double spendOf(String day) {
        return daySpend.getOrDefault(day, 0.0);
    }

    private String today() {
        return LocalDate.now().toString();
    }
}
