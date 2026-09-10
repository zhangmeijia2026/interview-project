package com.group5.interview.module.match.dto;

/** 简历与 JD 的单条差距（R3 输出）。 */
public record GapItem(String dimension, String item, String severity, String evidence) {
}
