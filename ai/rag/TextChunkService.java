package com.group5.interview.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.JobDescription;
import com.group5.interview.entity.QuestionBank;
import com.group5.interview.entity.Resume;
import com.group5.interview.entity.TextChunk;
import com.group5.interview.repository.JdRepository;
import com.group5.interview.repository.QuestionBankRepository;
import com.group5.interview.repository.ResumeRepository;
import com.group5.interview.repository.TextChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本分块与向量索引（P6 R10）。
 *
 * <p>把「用户本人的简历全文 + 岗位 JD 全文（owner=用户）」与「题库知识点
 * （owner=null 的全局知识）」分块后计算向量存入 text_chunks（embedding JSON 列）。
 * 三种写入时机：resume/jd 解析 ready 后、题库增删改后、启动/手动全量重建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextChunkService {

    private final TextChunkRepository chunkRepository;
    private final ResumeRepository resumeRepository;
    private final JdRepository jdRepository;
    private final QuestionBankRepository questionBankRepository;
    private final EmbeddingService embeddingService;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final QdrantClient qdrantClient;

    /** 供题库/简历/JD ready 后增量建索引。text 为空时仅清掉旧块。 */
    @Transactional
    public void indexSource(String sourceType, Long sourceId, Long ownerUserId, String text) {
        deleteSource(sourceType, sourceId);
        if (!properties.isEnabled() || text == null || text.isBlank()) return;
        List<String> pieces = chunk(text);
        if (pieces.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        List<TextChunk> saved = new ArrayList<>();
        int index = 0;
        for (String piece : pieces) {
            TextChunk row = new TextChunk();
            row.setOwnerUserId(ownerUserId);
            row.setSourceType(sourceType);
            row.setSourceId(sourceId);
            row.setChunkIndex(index++);
            row.setContent(piece);
            row.setEmbedding(writeVector(embeddingService.embed(piece)));
            row.setCreatedAt(now);
            saved.add(chunkRepository.save(row));
        }
        log.debug("[rag] index source={} id={} owner={} chunks={}", sourceType, sourceId, ownerUserId, pieces.size());
        qdrantUpsert(saved);
    }

    @Transactional
    public void indexQuestionBank(QuestionBank q) {
        if (q == null || !q.isEnabled()) {
            if (q != null) deleteSource("question_bank", q.getId());
            return;
        }
        indexSource("question_bank", q.getId(), null, questionBankText(q));
    }

    /** 移除某来源全部旧块（重建/软删/禁用时调用），provider=qdrant 时同步镜像删除。 */
    @Transactional
    public void deleteSource(String sourceType, Long sourceId) {
        List<TextChunk> rows = chunkRepository.findBySourceTypeAndSourceId(sourceType, sourceId);
        if (!rows.isEmpty()) chunkRepository.deleteAll(rows);
        qdrantDeleteSource(sourceType, sourceId);
    }

    /**
     * 全量重建：清空 text_chunks 后按「全部 ready 简历 + 全部 ready JD + 全部启用题库」重建。
     * 供启动初始化与管理端 POST /api/admin/rag/rebuild。
     */
    @Transactional
    public Map<String, Object> purgeAndRebuildAll() {
        if (!properties.isEnabled()) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "RAG 已关闭（app.rag.enabled=false）");
        chunkRepository.deleteAll();
        // Qdrant 镜像整库重建（删集合再建，避免残留已删除来源的点）
        if (qdrantClient != null && qdrantClient.enabled()) {
            try {
                qdrantClient.recreate();
            } catch (Exception e) {
                log.warn("[rag][qdrant] 全量重建前重置集合失败（将逐源同步并回退 local）: {}", e.getMessage());
            }
        }
        int resume = 0, jd = 0, qb = 0;
        for (Resume resumeRow : resumeRepository.findByStatus("ready")) {
            indexSource("resume", resumeRow.getId(), resumeRow.getUserId(), resumeRow.getFullText());
            resume++;
        }
        for (JobDescription jdRow : jdRepository.findByStatus("ready")) {
            indexSource("jd", jdRow.getId(), jdRow.getUserId(), jdRow.getRawText());
            jd++;
        }
        for (QuestionBank questionBank : questionBankRepository.findByEnabledTrue()) {
            indexQuestionBank(questionBank);
            qb++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rebuild", true);
        result.put("resumes", resume);
        result.put("jds", jd);
        result.put("questionBank", qb);
        result.put("totalChunks", chunkRepository.count());
        log.info("[rag] 全量重建完成 resume={} jd={} questionBank={} totalChunks={}", resume, jd, qb, result.get("totalChunks"));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("provider", properties.getProvider());
        result.put("embedding", properties.getEmbedding());
        result.put("dimension", embeddingService.dimension());
        result.put("totalChunks", chunkRepository.count());
        result.put("userChunks", chunkRepository.countByOwnerUserIdIsNotNull());
        result.put("knowledgeChunks", chunkRepository.countByOwnerUserIdIsNull());
        result.put("qdrant", qdrantHealth());
        return result;
    }

    /** Qdrant 适配段：enabled=provider 是否切到 qdrant；connected=集合可达；chunks=镜像点数。 */
    @Transactional(readOnly = true)
    public Map<String, Object> qdrantHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean configured = qdrantClient != null && qdrantClient.enabled();
        result.put("configured", configured);
        result.put("collection", QdrantClient.COLLECTION);
        boolean connected = false;
        long chunks = 0;
        if (configured) {
            try {
                connected = qdrantClient.ping();
                if (connected) chunks = qdrantClient.count();
            } catch (Exception e) {
                log.warn("[rag][qdrant] health 探测失败: {}", e.getMessage());
            }
        }
        result.put("connected", connected);
        result.put("chunks", chunks);
        return result;
    }

    // ===== Qdrant 镜像辅助：镜像失败只 warn，不阻断 MySQL 写入/接口（现网鲁棒，docs/07 §11）=====

    private void qdrantUpsert(List<TextChunk> saved) {
        if (saved == null || saved.isEmpty() || qdrantClient == null || !qdrantClient.enabled()) return;
        try {
            qdrantClient.ensureCollection();
            qdrantClient.upsert(saved);
        } catch (Exception e) {
            log.warn("[rag][qdrant] 镜像 upsert 失败（检索将回退 local）: {}", e.getMessage());
        }
    }

    private void qdrantDeleteSource(String sourceType, Long sourceId) {
        if (qdrantClient == null || !qdrantClient.enabled()) return;
        try {
            qdrantClient.ensureCollection();
            qdrantClient.deleteBySource(sourceType, sourceId);
        } catch (Exception e) {
            log.warn("[rag][qdrant] 镜像删除失败 source={} id={}: {}", sourceType, sourceId, e.getMessage());
        }
    }

    /** 反序列化 chunk.embedding JSON → float[]。 */
    public float[] parseVector(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL, "向量块数据损坏");
        }
    }

    private String writeVector(float[] vector) {
        try { return objectMapper.writeValueAsString(vector); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "向量序列化失败"); }
    }

    private String questionBankText(QuestionBank q) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(q.getCategory()).append("】").append(q.getContent());
        if (q.getAnswer() != null && !q.getAnswer().isBlank()) sb.append("\n参考答案：").append(q.getAnswer());
        if (q.getHint() != null && !q.getHint().isBlank()) sb.append("\n提示：").append(q.getHint());
        return sb.toString();
    }

    /**
     * 近似按段分块：优先按空行/整行断开，累计到每块 ~600 字；块长不宜过小。
     */
    static List<String> chunk(String text) {
        String normalized = text.replace("\r\n", "\n").trim();
        List<String> result = new ArrayList<>();
        if (normalized.isEmpty()) return result;
        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;
            if (current.length() + trimmed.length() <= 600) {
                if (current.length() > 0) current.append("\n");
                current.append(trimmed);
            } else {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                flushLong(current, trimmed, 600, result);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    private static void flushLong(StringBuilder current, String text, int max, List<String> result) {
        int remaining = text.length();
        int from = 0;
        while (remaining > max) {
            int cut = max;
            // 尽量在近段边界的中文句号/换行处断开
            for (int j = max; j > max - 40; j--) {
                if (j < text.length() && (text.charAt(j) == '。' || text.charAt(j) == '；' || text.charAt(j) == '，')) {
                    cut = j + 1;
                    break;
                }
            }
            result.add(text.substring(from, from + cut));
            from += cut;
            remaining -= cut;
        }
        result.add(text.substring(from));
    }
}
