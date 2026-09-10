package com.group5.interview.repository;

import com.group5.interview.entity.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JdRepository extends JpaRepository<JobDescription, Long> {
    Optional<JobDescription> findByIdAndUserId(Long id, Long userId);

    /** 我的 JD 保留库：按创建时间倒序（历史岗位 JD 选择，P3）。 */
    List<JobDescription> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** RAG 全量重建用：按解析状态取（P6）。 */
    List<JobDescription> findByStatus(String status);
}
