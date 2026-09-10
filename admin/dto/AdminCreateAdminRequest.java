package com.group5.interview.module.admin.dto;

/** 新增管理员请求。 */
public record AdminCreateAdminRequest(String email, String password, String nickname) {
}
