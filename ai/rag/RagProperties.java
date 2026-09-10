package com.group5.interview.ai.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG/向量检索配置（docs/07 §10）。全部可缺省，缺配不报错：
 * <ul>
 *   <li>enabled=false 关闭检索注入与索引（完全退化普通 DeepSeek）；</li>
 *   <li>provider=local 现网向量实现（库内语料 + 余弦 top-k）；provider=qdrant 连真实 Qdrant
 *       镜像库（2026-09-07 落地，docs/07 §11），不可用/空集时优雅回退 local；</li>
 *   <li>embedding=hash 本机确定性 512 维哈希向量（无模型依赖）；预留 onnx-bge 升档位。</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    /** 总开关。 */
    private boolean enabled = true;
    /** local（现网默认，MySQL 余弦） | qdrant（真实镜像库，缺服务回退 local）。 */
    private String provider = "local";
    /** hash（默认，确定性本地向量） | onnx-bge（预留升档）。 */
    private String embedding = "hash";
    /** 检索注入的最大命中块数。 */
    private int topK = 4;
    /** 余弦相似度阈值（0 表示只要语料非空即按 topK 注入）。 */
    private double minScore = 0.0;
    /** 向量维度（须与 text_chunks.embedding 一致，本机构建为 512）。 */
    private int dimension = 512;
    /** Qdrant 地址（provider=qdrant 时使用，如 http://127.0.0.1:6333）。 */
    private String qdrantUrl = "";
}
