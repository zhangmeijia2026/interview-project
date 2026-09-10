package com.group5.interview.module.admin.dto;

/** 账号启停请求（停用=黑名单，复用 users.is_active，2026-09-07）。 */
public record AdminSetActiveRequest(Boolean active) {
}
