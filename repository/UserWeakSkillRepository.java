package com.group5.interview.repository;

import com.group5.interview.entity.UserWeakSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserWeakSkillRepository extends JpaRepository<UserWeakSkill, Long> {
    List<UserWeakSkill> findByUserIdOrderBySeverityAsc(Long userId);
    Optional<UserWeakSkill> findByUserIdAndSkillTag(Long userId, String skillTag);
    Optional<UserWeakSkill> findByIdAndUserId(Long id, Long userId);
    List<UserWeakSkill> findByUserIdAndStatus(Long userId, String status);
    long countByUserId(Long userId);

    /** 未攻克（tracking/improving）的薄弱技能数，用于个人中心统计。 */
    long countByUserIdAndStatusIn(Long userId, Collection<String> statuses);
}
