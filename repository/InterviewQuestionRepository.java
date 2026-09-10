package com.group5.interview.repository;

import com.group5.interview.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    List<InterviewQuestion> findByInterviewIdOrderByOrderIndexAsc(Long interviewId);
    Optional<InterviewQuestion> findByInterviewIdAndOrderIndex(Long interviewId, int orderIndex);

    /** 用户已完成的会话中已作答题目的均分与作答数（个人中心统计，P3，恒单行）。 */
    @Query("select coalesce(avg(q.score), 0), count(q) from InterviewQuestion q " +
            "where q.score is not null and q.interviewId in " +
            "(select i.id from Interview i where i.userId = :uid and i.status = 'completed')")
    List<Object[]> aggregateUserCompleted(@Param("uid") Long userId);

    /** 用户已完成会话中得分 <60 的错题数（与错题本同口径）。 */
    @Query("select count(q) from InterviewQuestion q " +
            "where q.score is not null and q.score < 60 and q.interviewId in " +
            "(select i.id from Interview i where i.userId = :uid and i.status = 'completed')")
    long countWrongByUserCompleted(@Param("uid") Long userId);
}
