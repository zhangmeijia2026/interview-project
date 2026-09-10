package com.group5.interview.module.match.dto;

/** 一条"面试重点"（R4 输出），驱动逐题出题。 */
public record FocusItem(int index, String direction, String examinePoint, String prepare) {
}
