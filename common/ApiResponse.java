package com.group5.interview.common;

/**
 * 统一返回体。code 语义见 docs/05 §3 / docs/06 §1：
 * 0=成功；1xxx=参数；2xxx=认证；3xxx=业务；4xxx=LLM/外部；5xxx=系统。
 *
 * @param code    业务码
 * @param message 提示信息
 * @param data    数据
 * @param <T>     数据类型
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> error(ErrorCode ec) {
        return new ApiResponse<>(ec.getCode(), ec.getMessage(), null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
