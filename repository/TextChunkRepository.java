package com.group5.interview.repository;

import com.group5.interview.entity.TextChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TextChunkRepository extends JpaRepository<TextChunk, Long> {

    List<TextChunk> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    long countByOwnerUserIdIsNotNull();

    long countByOwnerUserIdIsNull();

    /** 用户可检索语料：本人简历/JD 块 + 全局知识（题库等），用于向量 top-k。 */
    @Query("select t from TextChunk t where t.ownerUserId = :uid or t.ownerUserId is null")
    List<TextChunk> findUserCorpus(@Param("uid") Long userId);
}
