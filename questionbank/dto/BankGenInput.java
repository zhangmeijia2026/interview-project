package com.group5.interview.module.questionbank.dto;

/** question-bank-gen 输入：分类 + 数量 + 难度区间。 */
public record BankGenInput(String category, int count, int difficulty) {
}
