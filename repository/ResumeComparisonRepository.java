package com.group5.interview.repository;

import com.group5.interview.entity.ResumeComparison;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeComparisonRepository extends JpaRepository<ResumeComparison, Long> {
    List<ResumeComparison> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<ResumeComparison> findByIdAndUserId(Long id, Long userId);
}
