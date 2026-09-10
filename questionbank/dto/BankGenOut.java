package com.group5.interview.module.questionbank.dto;

import java.util.List;

/** question-bank-gen 输出（真实/离线同构）。 */
public record BankGenOut(List<Item> questions) {

    public BankGenOut {
        if (questions == null) questions = List.of();
    }

    public record Item(String content, String answer, String hint, List<String> knowledgePoints) {
        public Item {
            if (knowledgePoints == null) knowledgePoints = List.of();
        }
    }
}
