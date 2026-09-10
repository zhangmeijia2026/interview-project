package com.group5.interview.module.jd.dto;

import java.util.List;

/**
 * JD 结构化结果（jd-parse），与 docs/07 §2 R2 schema 对齐。
 * 序列化为 JSON 后落在 job_descriptions.parsed_data。
 *
 * @param hardSkills            JD 要求的硬技能清单
 * @param softSkills            软素质清单
 * @param minExperienceYears    最低经验年限（无法识别为 null）
 * @param educationRequirement  学历要求（如 "本科"、"硕士及以上"，未知为 null）
 * @param responsibilities      岗位职责要点
 */
public record JdParsed(List<String> hardSkills, List<String> softSkills, Integer minExperienceYears,
                       String educationRequirement, List<String> responsibilities) {
    public JdParsed {
        hardSkills = hardSkills == null ? List.of() : hardSkills;
        softSkills = softSkills == null ? List.of() : softSkills;
        responsibilities = responsibilities == null ? List.of() : responsibilities;
    }
}
