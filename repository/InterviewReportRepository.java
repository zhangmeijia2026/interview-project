package com.group5.interview.repository;

import com.group5.interview.entity.InterviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewReportRepository extends JpaRepository<InterviewReport, Long> {
    Optional<InterviewReport> findByInterviewId(Long interviewId);
    boolean existsByInterviewId(Long interviewId);
}
