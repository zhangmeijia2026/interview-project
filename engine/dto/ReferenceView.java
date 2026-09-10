package com.group5.interview.module.engine.dto;

/**
 * 单题参考回答/提示视图（R8）。可见性规则：
 * practice 模式任何时候可见；formal 模式仅本题已作答后可见。
 * 未到时 referenceAnswer/hint 为 null，由前端据此隐藏。
 */
public record ReferenceView(String mode, boolean answered, String hint, String referenceAnswer) {
}
