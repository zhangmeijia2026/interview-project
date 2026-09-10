package com.group5.interview.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.ai.rag.RagInjector;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 真实 DeepSeek 接入（OpenAI 兼容，docs/07 §3）。双模式：
 * <ul>
 *   <li>resume-parse / jd-parse 一律委托离线本地算法——不消耗模型额度（团队决策）；</li>
 *   <li>match / focus / question / grade / follow-up / report-analysis 走模型，读 prompts/{key}.md 组消息；</li>
 *   <li>任一步失败（未配置密钥、超预算、网络错误、JSON 解析失败且重试仍失败）均自动回退离线引擎，保证演示不断。</li>
 *   <li>每次真实调用路径都经 {@link LlmCallLogService} 落库，供管理端统计（R9）。</li>
 * </ul>
 * 由 {@code ai.mock-enabled=false} 激活；为 {@code true}（默认）时只装配 {@link LocalMockAIService}。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.mock-enabled", havingValue = "false")
public class DeepSeekAIService implements AIService {

    private static final List<String> OFFLINE_KEYS = List.of("resume-parse", "jd-parse");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
    private static final String STRICT_JSON =
            "你只输出一个合法 JSON 对象，不输出 Markdown 代码块，不附加任何解释文字。严格按照模板给出的字段与类型输出。";

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final JsonSupport jsonSupport;
    private final CostGuard costGuard;
    private final LlmCallLogService callLogService;
    private final ObjectMapper objectMapper;
    /** RAG 检索注入（R10）：懒加载，避免与仓储初始化顺序耦合。 */
    private final ObjectProvider<RagInjector> ragInjectorProvider;
    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String modelName;
    /** 离线兜底：本地算法同构实现，供未配置密钥/超预算/调用失败时复用。 */
    private final LocalMockAIService offline = new LocalMockAIService();

    private final ConcurrentMap<String, String> promptCache = new ConcurrentHashMap<>();
    private volatile ChatClient chatClient;

    public DeepSeekAIService(ObjectProvider<ChatModel> chatModelProvider, JsonSupport jsonSupport,
                             CostGuard costGuard, LlmCallLogService callLogService, ObjectMapper objectMapper,
                             ObjectProvider<RagInjector> ragInjectorProvider) {
        this.chatModelProvider = chatModelProvider;
        this.jsonSupport = jsonSupport;
        this.costGuard = costGuard;
        this.callLogService = callLogService;
        this.objectMapper = objectMapper;
        this.ragInjectorProvider = ragInjectorProvider;
    }

    @Override
    public <T> T structured(String promptKey, Object context, Class<T> clazz) {
        if (OFFLINE_KEYS.contains(promptKey)) {
            log.info("[ai] {} 走本地算法（JD 岗位本地提关键词，不调模型）", promptKey);
            return offline.structured(promptKey, context, clazz);
        }
        ChatClient client = chatClient();
        Long uid = UserContext.currentUserIdOrNull();
        long start = System.currentTimeMillis();
        if (client == null) {
            log.warn("[ai] {} 未检测到可用的 ChatModel（未配置 DeepSeek 密钥？），自动回退离线引擎", promptKey);
            callLogService.record(promptKey, modelName, uid, 0, 0,
                    System.currentTimeMillis() - start, true, "no_model");
            return offline.structured(promptKey, context, clazz);
        }
        if (costGuard.overBudget()) {
            log.warn("[ai] {} 今日模型额度已用尽，自动回退离线引擎", promptKey);
            callLogService.record(promptKey, modelName, uid, 0, 0,
                    System.currentTimeMillis() - start, true, "budget_block");
            return offline.structured(promptKey, context, clazz);
        }
        String prompt = render(loadPrompt(promptKey), context);
        // R10：真实模型路径下，出题/评分/追问前把当前用户语料检索片段注入 {knowledge}
        RagInjector ragInjector = ragInjectorProvider.getIfAvailable();
        if (ragInjector != null) {
            try {
                prompt = ragInjector.inject(prompt, promptKey, context, uid);
            } catch (Exception ragError) {
                // 检索失败不阻断调用：以无资料上下文继续
                log.warn("[ai] {} RAG 注入跳过（{}），按无资料继续", promptKey, ragError.getMessage());
            }
        }
        try {
            T result = call(client, prompt, clazz);
            charge(prompt, result);
            callLogService.record(promptKey, modelName, uid,
                    prompt == null ? 0 : prompt.length(),
                    result == null ? 0 : result.toString().length(),
                    System.currentTimeMillis() - start, false, "ok");
            return result;
        } catch (BusinessException be) {
            if (be.getErrorCode() != ErrorCode.AI_FAILED) throw be;
            // JSON 解析失败：带着"必须只输出 JSON"的提示重试一次
            log.warn("[ai] {} 首次返回非合法 JSON，重试一次", promptKey);
            try {
                T result = call(client,
                        "你上一次的输出不是合法 JSON。请只输出符合既定 JSON schema 的合法 JSON，不要 Markdown、不要解释：\n" + prompt,
                        clazz);
                charge(prompt, result);
                callLogService.record(promptKey, modelName, uid,
                        prompt == null ? 0 : prompt.length(),
                        result == null ? 0 : result.toString().length(),
                        System.currentTimeMillis() - start, false, "ok_retry");
                return result;
            } catch (Exception retryFail) {
                log.error("[ai] {} 重试仍失败，回退离线引擎: {}", promptKey, retryFail.getMessage());
                callLogService.record(promptKey, modelName, uid,
                        prompt == null ? 0 : prompt.length(), 0,
                        System.currentTimeMillis() - start, true, "parse_fail");
                return offline.structured(promptKey, context, clazz);
            }
        } catch (Exception e) {
            log.error("[ai] {} 调用 DeepSeek 失败，回退离线引擎: {}", promptKey, e.getMessage());
            callLogService.record(promptKey, modelName, uid,
                    prompt == null ? 0 : prompt.length(), 0,
                    System.currentTimeMillis() - start, true, "error");
            return offline.structured(promptKey, context, clazz);
        }
    }

    private <T> T call(ChatClient client, String user, Class<T> clazz) {
        String content = client.prompt().system(STRICT_JSON).user(user).call().content();
        return jsonSupport.parse(content, clazz);
    }

    private void charge(String prompt, Object result) {
        String out = result == null ? "" : result.toString();
        costGuard.charge(prompt == null ? 0 : prompt.length(), out.length());
    }

    /** 把模板中的 {字段} 替换为上下文对象对应字段的 JSON 值（顶层字段名与组件一致）。 */
    private String render(String template, Object context) {
        if (template == null || context == null) return template;
        try {
            JsonNode node = objectMapper.valueToTree(context);
            Matcher m = PLACEHOLDER.matcher(template);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String key = m.group(1);
                JsonNode value = node.get(key);
                String replacement = value == null ? m.group() // 保留原占位符（如可选的 knowledge/recentHistory）
                        : (value.isContainerNode() || !value.isValueNode() ? value.toString() : value.asText());
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            log.warn("[ai] prompt 模板渲染失败，退回原模板: {}", e.getMessage());
            return template;
        }
    }

    private String loadPrompt(String promptKey) {
        return promptCache.computeIfAbsent(promptKey, key -> {
            ClassPathResource resource = new ClassPathResource("prompts/" + key + ".md");
            try {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.INTERNAL, "缺少 prompt 模板 prompts/" + key + ".md");
            }
        });
    }

    private ChatClient chatClient() {
        ChatClient cached = this.chatClient;
        if (cached != null) return cached;
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) return null;
        synchronized (this) {
            if (this.chatClient == null) this.chatClient = ChatClient.builder(model).build();
            return this.chatClient;
        }
    }
}
