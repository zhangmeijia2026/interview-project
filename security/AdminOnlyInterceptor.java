package com.group5.interview.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.User;
import com.group5.interview.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 管理端门禁：拦截 /api/admin/**，仅 role=admin 可通过，否则 403。
 * 认证已由 JwtAuthFilter 完成（UserContext 已注入 userId），此处仅做角色校验（读库，简单可靠）。
 */
@Component
@RequiredArgsConstructor
public class AdminOnlyInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = UserContext.currentUserIdOrNull();
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResponse.error(ErrorCode.UNAUTHORIZED));
            return false;
        }
        if (!user.isActive()) {
            // 停用的管理员不能再进管理端（2026-09-07，与 JwtAuthFilter 同口径）
            write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), "账号已被停用，请联系管理员"));
            return false;
        }
        if (!user.isAdmin()) {
            write(response, HttpServletResponse.SC_FORBIDDEN, ApiResponse.error(ErrorCode.FORBIDDEN));
            return false;
        }
        return true;
    }

    private void write(HttpServletResponse response, int status, ApiResponse<?> body) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
