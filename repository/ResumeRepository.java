package com.group5.interview.repository;

import com.group5.interview.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    Optional<Resume> findByIdAndUserId(Long id, Long userId);
    Optional<Resume> findByUserIdAndFileMd5(Long userId, String fileMd5);
    /** 我的可复用简历库：仅解析完成的，按更新时间倒序。 */
    List<Resume> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, String status);

    /** RAG 全量重建用：按解析状态取（P6）。 */
    List<Resume> findByStatus(String status);
}
