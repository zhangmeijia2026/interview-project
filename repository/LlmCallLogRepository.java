package com.group5.interview.repository;

import com.group5.interview.entity.LlmCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {
    Page<LlmCallLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 按天+prompt 聚合调用统计（原生查询，供管理端 ECharts）。 */
    @Query(value = "select date(created_at) as d, prompt_key as k, count(*) as calls, " +
            "sum(in_chars) as inC, sum(out_chars) as outC, sum(est_cost) as cost, sum(fallback) as fb " +
            "from llm_call_logs where created_at >= :since group by d, k order by d", nativeQuery = true)
    List<Object[]> aggregateSince(@Param("since") LocalDateTime since);

    long countByCreatedAtAfter(LocalDateTime since);
}
