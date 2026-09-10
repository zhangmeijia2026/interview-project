package com.group5.interview.module.admin;

import com.group5.interview.common.PageResult;
import com.group5.interview.entity.LlmCallLog;
import com.group5.interview.module.admin.dto.LlmCallView;
import com.group5.interview.repository.LlmCallLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端 DeepSeek 调用统计（R9，docs/06 §模型调用统计）：
 * 基于 llm_call_logs 的日维度聚合 + 分页明细，用于后台 ECharts。
 */
@Service
@RequiredArgsConstructor
public class AdminLlmService {

    private final LlmCallLogRepository llmRepository;

    /** 近 N 天统计：按天调用/费用/回退折线 + prompt 占比 + 总量。 */
    @Transactional(readOnly = true)
    public Map<String, Object> stats(int days) {
        int safeDays = Math.min(Math.max(days, 1), 90);
        LocalDateTime since = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        List<Object[]> rows = llmRepository.aggregateSince(since);

        Map<String, DayAgg> daily = new LinkedHashMap<>();
        Map<String, PromptAgg> promptAcc = new LinkedHashMap<>();

        for (Object[] r : rows) {
            Date date = (Date) r[0];
            String key = (String) r[1];
            long calls = num(r[2]);
            long inC = num(r[3]);
            long outC = num(r[4]);
            BigDecimal cost = numBig(r[5]);
            long fb = num(r[6]);
            String day = date.toLocalDate().toString();

            DayAgg dayAgg = daily.computeIfAbsent(day, DayAgg::new);
            dayAgg.calls += calls;
            dayAgg.inChars += inC;
            dayAgg.outChars += outC;
            dayAgg.fallback += fb;
            dayAgg.cost = dayAgg.cost.add(cost);
            dayAgg.addPrompt(key, calls, cost, fb);

            PromptAgg acc = promptAcc.computeIfAbsent(key, k -> new PromptAgg(key));
            acc.calls += calls;
            acc.inChars += inC;
            acc.outChars += outC;
            acc.fallback += fb;
            acc.cost = acc.cost.add(cost);
        }

        List<Map<String, Object>> promptBreakdown = new ArrayList<>();
        PromptAgg total = new PromptAgg("__total__");
        for (PromptAgg acc : promptAcc.values()) {
            total.calls += acc.calls;
            total.inChars += acc.inChars;
            total.outChars += acc.outChars;
            total.fallback += acc.fallback;
            total.cost = total.cost.add(acc.cost);
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("promptKey", acc.key);
            b.put("calls", acc.calls);
            b.put("inChars", acc.inChars);
            b.put("outChars", acc.outChars);
            b.put("fallback", acc.fallback);
            b.put("cost", money(acc.cost));
            promptBreakdown.add(b);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("since", since.toLocalDate());
        body.put("days", safeDays);
        body.put("totalCalls", total.calls);
        body.put("totalInChars", total.inChars);
        body.put("totalOutChars", total.outChars);
        body.put("totalCost", money(total.cost));
        body.put("fallbackCalls", total.fallback);
        body.put("fallbackRate", total.calls == 0 ? 0.0 : round(total.fallback * 100.0 / total.calls));
        body.put("promptBreakdown", promptBreakdown);
        body.put("daily", daily.values().stream().map(DayAgg::toMap).toList());
        return body;
    }

    @Transactional(readOnly = true)
    public PageResult<LlmCallView> calls(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<LlmCallLog> result = llmRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize));
        return new PageResult<>(result.getContent().stream().map(this::toView).toList(), result.getTotalElements());
    }

    private LlmCallView toView(LlmCallLog log) {
        return new LlmCallView(log.getId(), log.getUserId(), log.getPromptKey(), log.getModel(),
                log.getInChars(), log.getOutChars(), log.getEstCost(), log.getLatencyMs(),
                log.isFallback(), log.getStatus(), log.getCreatedAt());
    }

    private long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private BigDecimal numBig(Object o) {
        return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString());
    }

    private BigDecimal money(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP);
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** 单日聚合（含该日各 prompt 明细，供前端 tooltip）。 */
    private static final class DayAgg {
        final String date;
        long calls;
        long inChars;
        long outChars;
        long fallback;
        BigDecimal cost = BigDecimal.ZERO;
        final Map<String, PromptAgg> prompts = new LinkedHashMap<>();

        DayAgg(String date) {
            this.date = date;
        }

        void addPrompt(String key, long calls, BigDecimal cost, long fallback) {
            PromptAgg p = prompts.computeIfAbsent(key, PromptAgg::new);
            p.calls += calls;
            p.cost = p.cost.add(cost);
            p.fallback += fallback;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date);
            m.put("calls", calls);
            m.put("inChars", inChars);
            m.put("outChars", outChars);
            m.put("fallback", fallback);
            m.put("cost", cost.setScale(4, RoundingMode.HALF_UP));
            m.put("prompts", prompts.values().stream().map(p -> {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("promptKey", p.key);
                pm.put("calls", p.calls);
                pm.put("cost", p.cost.setScale(4, RoundingMode.HALF_UP));
                pm.put("fallback", p.fallback);
                return pm;
            }).toList());
            return m;
        }
    }

    /** 单 prompt 累计。 */
    private static final class PromptAgg {
        final String key;
        long calls;
        long inChars;
        long outChars;
        long fallback;
        BigDecimal cost = BigDecimal.ZERO;

        PromptAgg(String key) {
            this.key = key;
        }
    }
}
