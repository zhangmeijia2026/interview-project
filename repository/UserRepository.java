package com.group5.interview.repository;

import com.group5.interview.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findFirstByRoleOrderByIdAsc(String role);

    /** 管理端用户检索（2026-09-07 扩展）：昵称/邮箱模糊 + 可选 role/active 过滤，keyword 为空返回全部。 */
    @Query("select u from User u where (:kw = '' " +
            "or lower(u.nickname) like lower(concat('%', :kw, '%')) " +
            "or lower(u.email) like lower(concat('%', :kw, '%'))) " +
            "and (:role is null or u.role = :role) " +
            "and (:active is null or u.active = :active) " +
            "order by u.id desc")
    Page<User> search(@Param("kw") String kw, @Param("role") String role,
                      @Param("active") Boolean active, Pageable pageable);

    /** 启用中的指定角色人数（守卫：停用后系统仍须至少保留一位启用管理员）。 */
    long countByRoleAndActiveTrue(String role);

    /** 已停用（黑名单）账号数。 */
    long countByActiveFalse();
}
