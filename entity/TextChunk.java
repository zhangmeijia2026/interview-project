package com.group5.interview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文本向量块（二期向量检索，docs/04 §9 / docs/07 §10 落地）。
 * owner_user_id 可空 = 全局知识块（题库知识点等，跨用户检索）。
 */
@Getter
@Setter
@Entity
@Table(name = "text_chunks")
public class TextChunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 可空：null=全局知识（题库知识点等）。 */
    @Column(name = "owner_user_id") private Long ownerUserId;
    /** resume | jd | question_bank（预留 knowledge 通用段）。 */
    @Column(name = "source_type", nullable = false, length = 16) private String sourceType;
    @Column(name = "source_id", nullable = false) private Long sourceId;
    @Column(name = "chunk_index", nullable = false) private int chunkIndex;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String content;
    /** float[] 序列化 JSON（本机构建维度=512）。 */
    @Column(columnDefinition = "json") private String embedding;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
