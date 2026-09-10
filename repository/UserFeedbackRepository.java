package com.group5.interview.repository;

import com.group5.interview.entity.UserFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {
    List<UserFeedback> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<UserFeedback> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Page<UserFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(String status);

    // ---- 管理端仅看普通用户提交的反馈（2026-09-07）：排除提交人 role=admin 的行 ----

    @Query(value = "select f from UserFeedback f where f.userId in " +
            "(select u.id from User u where u.role <> 'admin') order by f.createdAt desc",
            countQuery = "select count(f) from UserFeedback f where f.userId in " +
                    "(select u.id from User u where u.role <> 'admin')")
    Page<UserFeedback> findAllNonAdminByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = "select f from UserFeedback f where f.status = :status and f.userId in " +
            "(select u.id from User u where u.role <> 'admin') order by f.createdAt desc",
            countQuery = "select count(f) from UserFeedback f where f.status = :status and f.userId in " +
                    "(select u.id from User u where u.role <> 'admin')")
    Page<UserFeedback> findByStatusNonAdminByOrderByCreatedAtDesc(@Param("status") String status, Pageable pageable);
}
