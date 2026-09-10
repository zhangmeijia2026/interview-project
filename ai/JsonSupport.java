package com.group5.interview.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模型输出 JSON 的宽容解析：剥掉 Markdown 代码围栏、截取首尾花括号内的对象、
 * 尝试修复尾逗号等常见瑕疵后反序列化；仍失败则抛 {@link ErrorCode#AI_FAILED} 由上层兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonSupport {

    private final ObjectMapper objectMapper;

    public <T> T parse(String content, Class<T> clazz) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.AI_FAILED, "模型返回为空");
        }
        String cleaned = stripFences(content);
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException(ErrorCode.AI_FAILED, "模型返回中未找到 JSON 对象");
        }
        String candidate = cleaned.substring(start, end + 1);
        try {
            return objectMapper.readValue(candidate, clazz);
        } catch (Exception first) {
            String repaired = repair(candidate);
            try {
                return objectMapper.readValue(repaired, clazz);
            } catch (Exception second) {
                log.warn("[ai] 模型 JSON 解析失败，candidate={}", clip(candidate, 300));
                throw new BusinessException(ErrorCode.AI_FAILED, "模型返回无法解析为 JSON");
            }
        }
    }

    private String stripFences(String content) {
        String noFence = content.replaceAll("(?s)```(?:json)?\\s*|\\s*```", "");
        return noFence.trim();
    }

    /** 常见修复：去掉对象/数组尾部多余逗号。 */
    private String repair(String json) {
        return json.replaceAll(",\\s*([]}])", "$1");
    }

    private String clip(String text, int max) {
        return text == null ? "" : (text.length() <= max ? text : text.substring(0, max) + "…");
    }
}
