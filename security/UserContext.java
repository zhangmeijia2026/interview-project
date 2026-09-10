package com.group5.interview.security;

import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 由 JwtAuthFilter 注入、finally 中清除，防止线程池复用串号。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        USER_ID.set(userId);
    }

    /** 未登录/上下文为空时抛 401。 */
    public static Long currentUserId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    public static Long currentUserIdOrNull() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
