package com.group5.interview.repository;

import com.group5.interview.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    Optional<Interview> findByIdAndUserId(Long id, Long userId);
    List<Interview> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Interview> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    Optional<Interview> findByJdId(Long jdId);
    long countByUserIdAndStatus(Long userId, String status);
    long countByStatus(String status);
}
