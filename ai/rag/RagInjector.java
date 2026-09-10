package com.group5.interview.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.entity.TextChunk;
import com.group5.interview.repository.TextChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 注入（R10）：真实 DeepSeek 出题/评分/追问前，把当前用户可检索语料
 * （本人简历/JD + 全局题库知识点）按向量 top-k 召回，替换模板里的 {knowledge}。
 *
 * <p>provider=qdrant（2026-09-07 落地）时检索走 Qdrant 镜像（owner=0/uid 过滤）；
 * Qdrant 不可用/空集时自动回退 MySQL 本地余弦（现网默认 local）。两种实现同口径：
 * hash 向量 → 余弦 → 取 top-k（docs/07 §10/§11）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagInjector {

    private final RagProperties properties;
    private final TextChunkRepository chunkRepository;
    private final TextChunkService textChunkService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final QdrantClient qdrantClient;

    private static final List<String> PLACEHOLDER_KEYS = List.of("question", "grade", "follow-up");

    /** 在真实模型路径中把 prompt 模板里的 {knowledge} 替换为检索到的资料块（无则替换为空串）。 */
    public String inject(String prompt, String promptKey, Object context, Long userId) {
        if (!properties.isEnabled() || userId == null || !PLACEHOLDER_KEYS.contains(promptKey)) {
            return prompt;
        }
        if (!prompt.contains("{knowledge}")) return prompt;
        String query = queryText(context);
        if (query.isBlank()) return prompt.replace("{knowledge}", ""); // 无查询时清占位符，防字面量漏给模型
        try {
            Retrieval retrieval = searchCorpus(query, userId, properties.getTopK(), properties.getMinScore());
            return prompt.replace("{knowledge}", render(retrieval.hits()));
        } catch (Exception e) {
            log.warn("[rag] 检索注入失败，knowledge 置空: {}", e.getMessage());
            return prompt.replace("{knowledge}", "");
        }
    }

    /**
     * 管理端演示/验收检索：可选 uid（null=不过滤 owner，看全量 resume/jd/question_bank）。
     * 返回 provider 供断言"检索真走 Qdrant 还是回退 local"。
     */
    public Map<String, Object> adminSearch(String query, int topK, Long uid) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query == null ? "" : query);
        if (query == null || query.isBlank()) {
            out.put("provider", "none");
            out.put("hits", List.of());
            return out;
        }
        Retrieval retrieval = searchCorpus(query, uid, Math.max(1, topK), 0.0);
        out.put("provider", retrieval.provider());
        List<Map<String, Object>> hits = new ArrayList<>();
        for (RetrievalHit h : retrieval.hits()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceType", h.sourceType());
            item.put("label", label(h.sourceType()));
            item.put("score", Math.round(h.score() * 10000.0) / 10000.0);
            item.put("content", h.content());
            hits.add(item);
        }
        out.put("hits", hits);
        return out;
    }

    /**
     * 统一检索入口：provider=qdrant 且可达→Qdrant（uid 过滤 owner 0/uid）；
     * 否则本地 MySQL 语料余弦 top-k（uid 为空=全量，仅管理接口用）。
     */
    private Retrieval searchCorpus(String query, Long uid, int topK, double minScore) {
        float[] queryVector = embeddingService.embed(query);
        if (qdrantClient != null && qdrantClient.enabled()) {
            try {
                qdrantClient.ensureCollection();
                List<QdrantClient.Hit> qh = qdrantClient.search(queryVector, uid, topK, minScore);
                if (!qh.isEmpty()) {
                    List<RetrievalHit> hits = qh.stream()
                            .map(h -> new RetrievalHit(h.sourceType(), h.content(), h.score()))
                            .toList();
                    return new Retrieval("qdrant", hits);
                }
                // Qdrant 空集（可能未同步完）→ 落到 local，避免漏召回
            } catch (Exception e) {
                log.warn("[rag] Qdrant 检索失败，回退 local: {}", e.getMessage());
            }
        }
        List<TextChunk> corpus = (uid == null)
                ? chunkRepository.findAll()
                : chunkRepository.findUserCorpus(uid);
        List<RetrievalHit> hits = new ArrayList<>();
        for (TextChunk chunk : corpus) {
            float[] chunkVector = textChunkService.parseVector(chunk.getEmbedding());
            if (chunkVector == null) continue;
            double score = cosine(queryVector, chunkVector);
            if (score < minScore) continue;
            hits.add(new RetrievalHit(chunk.getSourceType(), chunk.getContent(), score));
        }
        hits.sort(Comparator.comparingDouble(RetrievalHit::score).reversed());
        List<RetrievalHit> top = hits.size() > topK ? hits.subList(0, topK) : hits;
        return new Retrieval("local", top);
    }

    /** 把命中块格式化为注入文本块（空集返回空串）。 */
    private String render(List<RetrievalHit> hits) {
        if (hits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("以下是本场可参考的简历/JD/知识点检索片段，仅供作答与点评参考。请勿在回答中提到“检索到资料”，也不要编造片段之外的事实。");
        for (RetrievalHit hit : hits) {
            sb.append("\n- [").append(label(hit.sourceType())).append("] ")
                    .append(truncate(hit.content(), 320));
        }
        return sb.toString();
    }

    /** 从上下文对象提取检索查询：汇总叶子文本（尽量以题目/考察点/目标岗位文本开头）。 */
    private String queryText(Object context) {
        try {
            JsonNode node = objectMapper.valueToTree(context);
            StringBuilder sb = new StringBuilder();
            for (String lead : List.of("question", "examinePoint", "direction", "weakPoint", "targetPosition", "weakSkills")) {
                JsonNode value = node.get(lead);
                if (value != null && !value.isNull()) sb.append(textOf(value)).append(' ');
            }
            collectStrings(node, sb, 600);
            String query = sb.toString().trim();
            return query.length() <= 1200 ? query : query.substring(0, 1200);
        } catch (Exception e) {
            log.warn("[rag] 检索查询构建失败: {}", e.getMessage());
            return "";
        }
    }

    private void collectStrings(JsonNode node, StringBuilder sb, int cap) {
        if (node == null || sb.length() >= cap) return;
        if (node.isValueNode()) {
            if (node.isTextual() && !node.asText().isBlank()) sb.append(node.asText()).append(' ');
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) collectStrings(child, sb, cap);
            return;
        }
        if (node.isObject()) {
            node.forEach(child -> collectStrings(child, sb, cap));
        }
    }

    private String textOf(JsonNode value) {
        if (value.isValueNode()) return value.isTextual() ? value.asText() : value.toString();
        StringBuilder sb = new StringBuilder();
        collectStrings(value, sb, 200);
        return sb.toString().trim();
    }

    private static double cosine(float[] a, float[] b) {
        return HashEmbeddingService.cosine(a, b);
    }

    private static String label(String sourceType) {
        return switch (sourceType == null ? "" : sourceType) {
            case "resume" -> "简历";
            case "jd" -> "岗位JD";
            case "question_bank" -> "题库知识点";
            case "knowledge" -> "知识点";
            default -> "资料";
        };
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private record RetrievalHit(String sourceType, String content, double score) {
    }

    private record Retrieval(String provider, List<RetrievalHit> hits) {
    }
}
