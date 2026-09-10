package com.group5.interview.module.resume.dto;

import java.util.List;

/** 与 docs/07 resume-parse schema 对齐的结构化简历。 */
public record ResumeParsed(String name, String email, String phone, List<Education> education,
                           List<Experience> experience, List<Project> projects, List<Skill> skills) {
    public record Education(String school, String degree, String major, String period) {}
    public record Experience(String company, String title, String period, String summary, List<String> tech) {}
    public record Project(String name, String period, String description, String role, List<String> tech) {}
    public record Skill(String name, String level) {}
}
