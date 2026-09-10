package com.group5.interview.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务错误码（与 docs/06 §1 错误码表一致）。
 */
@Getter
public enum ErrorCode {

    // 1xxx 参数/校验
    INVALID_ARGUMENT(1001, HttpStatus.BAD_REQUEST, "参数校验失败"),
    VERIFY_CODE_INVALID(1001, HttpStatus.BAD_REQUEST, "校验码错误"),
    MEDIA_NOT_SUPPORTED(1002, HttpStatus.BAD_REQUEST, "不支持的文件格式"),
    FILE_TOO_LARGE(1003, HttpStatus.BAD_REQUEST, "文件超过大小限制"),

    // 2xxx 认证/授权
    UNAUTHORIZED(2001, HttpStatus.UNAUTHORIZED, "未认证或登录已过期"),
    FORBIDDEN(2002, HttpStatus.FORBIDDEN, "无权限访问"),
    ACCOUNT_LOCKED(2003, HttpStatus.TOO_MANY_REQUESTS, "账号已锁定，请稍后再试"),
    INVALID_CREDENTIALS(2001, HttpStatus.UNAUTHORIZED, "邮箱或密码错误"),
    RATE_LIMITED(2004, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    QUOTA_EXCEEDED(2005, HttpStatus.TOO_MANY_REQUESTS, "今日 AI 额度已用尽"),

    // 3xxx 业务
    NOT_FOUND(3001, HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(3002, HttpStatus.CONFLICT, "状态冲突，无法执行该操作"),

    // 4xxx AI/外部依赖
    AI_FAILED(4001, HttpStatus.BAD_GATEWAY, "AI 服务调用失败，请重试"),
    AI_TIMEOUT(4002, HttpStatus.GATEWAY_TIMEOUT, "AI 服务响应超时"),

    // 5xxx 系统
    INTERNAL(5000, HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
