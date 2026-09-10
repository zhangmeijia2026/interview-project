package com.group5.interview.repository;

import com.group5.interview.entity.QuestionBank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {
    Page<QuestionBank> findByEnabledTrueAndCategory(String category, Pageable pageable);
    List<QuestionBank> findByEnabledTrueAndCategory(String category);
    List<QuestionBank> findByEnabledTrue();
    Optional<QuestionBank> findByIdAndEnabledTrue(Long id);
    long countByEnabledTrue();

    @Query("select q.category from QuestionBank q where q.enabled = true group by q.category order by count(q) desc")
    List<String> listEnabledCategories();

    @Query("select q.category, count(q) from QuestionBank q where q.enabled = true group by q.category order by count(q) desc")
    List<Object[]> countByEnabledCategory();
}
