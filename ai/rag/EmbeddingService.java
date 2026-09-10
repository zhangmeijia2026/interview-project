package com.group5.interview.ai.rag;

/**
 * 文本 → 稠密向量（dimension 维）。本机演示用确定性哈希向量（无外部模型、
 * 无网络），保证离线/CI 稳定可复现；预留 onnx-bge 升档位（docs/07 §10）。
 */
public interface EmbeddingService {
    /** 计算归一化向量（L2=1），便于余弦相似度。 */
    float[] embed(String text);

    int dimension();
}
