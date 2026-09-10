package com.group5.interview.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.group5.interview.entity.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Qdrant REST 适配（P6 R10 扩展，2026-09-07 落地）。
 *
 * <p>Qdrant 仅是 {@code text_chunks}(MySQL) 的镜像检索索引：写入以 MySQL 为准，
 * provider=qdrant 时由 {@link TextChunkService} 写库后镜像本类，检索改走 Qdrant；
 * 集合名 interview_chunks（512 维、Cosine）。owner 以 payload.owner 表达：
 * 0=全局知识（题库等），userId=该用户本人的简历/JD；检索用
 * {@code should:[owner=0, owner=uid], min_should=1} 过滤（uid 为空则不过滤=全量，
 * 仅供管理员演示接口用）。</p>
 *
 * <p>零新依赖：JDK {@link HttpClient} + Jackson 组 JSON。任何异常向上抛由调用方
 * try/catch 优雅回退 local（不阻断 MySQL 写入 / 接口）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantClient {

    /** 与 stats / 管理接口共用的向量集合名。 */
    public static final String COLLECTION = "interview_chunks";

    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    private volatile HttpClient http;

    /** provider=qdrant 且已配 qdrant-url 才算启用（否则本类应被 local 路径忽略）。 */
    public boolean enabled() {
        return "qdrant".equalsIgnoreCase(properties.getProvider())
                && properties.getQdrantUrl() != null
                && !properties.getQdrantUrl().isBlank();
    }

    /** 服务是否可达且集合已存在（= ping）。 */
    public boolean ping() {
        try {
            return exchange("GET", "/collections/" + COLLECTION, null).status == 200;
        } catch (Exception e) {
            log.debug("[rag][qdrant] ping 失败: {}", e.getMessage());
            return false;
        }
    }

    /** 集合不存在则按 512/Cosine 创建（幂等）。 */
    public void ensureCollection() {
        if (ping()) return;
        ObjectNode cfg = objectMapper.createObjectNode();
        ObjectNode vectors = cfg.putObject("vectors");
        vectors.put("size", properties.getDimension());
        vectors.put("distance", "Cosine");
        Response r = exchange("PUT", "/collections/" + COLLECTION, cfg);
        if (r.status != 200) {
            throw new IllegalStateException("创建集合失败 http=" + r.status);
        }
        log.info("[rag][qdrant] 集合 {} 就绪（dim={}, Cosine）", COLLECTION, properties.getDimension());
    }

    /** 重建集合：删掉重建（清空全部镜像点，purgeAndRebuildAll 用）。 */
    public void recreate() {
        exchange("DELETE", "/collections/" + COLLECTION, null);
        ensureCollection();
    }

    /** 批量镜像：把一批已落 MySQL 的行写入 Qdrant（id=DB 行 id）。 */
    public void upsert(List<TextChunk> rows) {
        if (rows == null || rows.isEmpty()) return;
        ArrayNode points = objectMapper.createArrayNode();
        for (TextChunk row : rows) {
            float[] vector = parseVector(row.getEmbedding());
            if (vector == null) continue;
            ObjectNode p = points.addObject();
            p.put("id", row.getId());
            p.set("vector", objectMapper.valueToTree(vector));
            ObjectNode payload = p.putObject("payload");
            payload.put("owner", row.getOwnerUserId() == null ? 0L : row.getOwnerUserId());
            payload.put("source_type", row.getSourceType());
            payload.put("source_id", row.getSourceId());
            payload.put("chunk_index", row.getChunkIndex());
            payload.put("content", row.getContent());
        }
        if (points.isEmpty()) return;
        Response r = exchange("PUT", "/collections/" + COLLECTION + "/points?wait=true",
                objectMapper.createObjectNode().set("points", points));
        if (r.status != 200) throw new IllegalStateException("upsert 失败 http=" + r.status);
    }

    /** 删除某来源的全部镜像点（与 DB 侧 deleteSource 对应）。 */
    public void deleteBySource(String sourceType, Long sourceId) {
        ObjectNode body = objectMapper.createObjectNode().set("filter", sourceFilter(sourceType, sourceId));
        Response r = exchange("POST", "/collections/" + COLLECTION + "/points/delete", body);
        if (r.status != 200) throw new IllegalStateException("delete 失败 http=" + r.status);
    }

    /**
     * 向量检索。uid 为空 = 不过滤 owner（全量，仅管理员演示接口）；否则只命中
     * 「owner=0 全局知识 + owner=uid 本人语料」。
     */
    public List<Hit> search(float[] query, Long uid, int topK, double minScore) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("vector", objectMapper.valueToTree(query));
        body.put("limit", Math.max(1, topK));
        body.put("with_payload", true);
        if (uid != null) body.set("filter", ownerFilter(uid));
        if (minScore > 0) body.put("score_threshold", minScore);
        Response r = exchange("POST", "/collections/" + COLLECTION + "/points/search", body);
        if (r.status != 200) throw new IllegalStateException("search 失败 http=" + r.status);
        List<Hit> hits = new ArrayList<>();
        JsonNode result = r.body == null ? null : r.body.path("result");
        if (result == null || !result.isArray()) return hits;
        for (JsonNode item : result) {
            JsonNode payload = item.path("payload");
            hits.add(new Hit(
                    payload.path("source_type").asText(""),
                    payload.path("content").asText(""),
                    item.path("score").asDouble()));
        }
        return hits;
    }

    /** 当前集合内镜像点数。 */
    public long count() {
        Response r = exchange("GET", "/collections/" + COLLECTION, null);
        if (r.status != 200 || r.body == null) return 0;
        JsonNode points = r.body.path("result").path("points_count");
        return points.isMissingNode() ? 0 : points.asLong();
    }

    /** owner 检索过滤：全局(0) 或 本人(uid)。 */
    static JsonNode ownerFilter(Long uid) {
        ObjectNode filter = STATIC_MAPPER.createObjectNode();
        ArrayNode should = filter.putArray("should");
        should.add(match("owner", 0));
        if (uid != null) should.add(match("owner", uid));
        filter.put("min_should", 1);
        return filter;
    }

    /** 来源删除过滤：source_type + source_id 精确匹配。 */
    static JsonNode sourceFilter(String sourceType, Long sourceId) {
        ObjectNode filter = STATIC_MAPPER.createObjectNode();
        ArrayNode must = filter.putArray("must");
        must.add(match("source_type", sourceType));
        must.add(match("source_id", sourceId));
        return filter;
    }

    private static ObjectNode match(String key, Object value) {
        ObjectNode node = STATIC_MAPPER.createObjectNode();
        node.put("key", key);
        node.putObject("match").putPOJO("value", value);
        return node;
    }

    private static final ObjectMapper STATIC_MAPPER = new ObjectMapper();

    private float[] parseVector(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            log.warn("[rag][qdrant] 跳过坏向量块: {}", e.getMessage());
            return null;
        }
    }

    private HttpClient http() {
        HttpClient h = http;
        if (h == null) {
            synchronized (this) {
                h = http;
                if (h == null) {
                    h = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                    http = h;
                }
            }
        }
        return h;
    }

    private String base() {
        String url = properties.getQdrantUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private Response exchange(String method, String path, JsonNode body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path));
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body.toString()));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> resp = http().send(
                    builder.timeout(Duration.ofSeconds(15)).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode parsed = (resp.body() == null || resp.body().isBlank())
                    ? null : objectMapper.readTree(resp.body());
            return new Response(resp.statusCode(), parsed);
        } catch (Exception e) {
            throw new IllegalStateException("qdrant " + method + " " + path + " 调用失败: " + e.getMessage(), e);
        }
    }

    /** 检索命中（镜像点快照：source_type/content + 相似度分）。 */
    public record Hit(String sourceType, String content, double score) {
    }

    private record Response(int status, JsonNode body) {
    }
}
