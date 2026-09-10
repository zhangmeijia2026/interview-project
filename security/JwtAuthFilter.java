package com.group5.interview.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.User;
import com.group5.interview.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 认证过滤器（docs/05 §4）：
 * 除放行路径外，/api/** 默认必须携带有效 Bearer access token，
 * 否则返回 401；校验通过后把 userId 写入 UserContext。
 * 注：OPTIONS 预检请求直接放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /** swagger / 文档静态资源放行前缀 */
    private static final List<String> ALWAYS_PUBLIC_PREFIXES = List.of(
            "/swagger-ui", "/v3/api-docs", "/error"
    );

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method) || isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        Long userId = null;
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = jwtUtil.parse(header.substring(BEARER_PREFIX.length()));
                if (jwtUtil.isAccessToken(claims) && !tokenStore.isAccessBlacklisted(claims)) {
                    userId = JwtUtil.userIdOf(claims);
                }
            } catch (Exception e) {
                log.debug("JWT 解析失败: {}", e.getMessage());
            }
        }

        if (userId == null) {
            writeUnauthorized(response);
            return;
        }

        // 停用/黑名单即时生效（2026-09-07）：每次请求按账号启停状态复核，
        // 已签发 access 令牌对已停用账号即刻失效（前端 401→refresh 也被拒→回登录页）。
        User account = userRepository.findById(userId).orElse(null);
        if (account == null) {
            writeUnauthorized(response);
            return;
        }
        if (!account.isActive()) {
            writeUnauthorized(response, "账号已被停用，请联系管理员");
            return;
        }

        UserContext.set(userId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private boolean isPublicPath(String path) {
        for (String prefix : ALWAYS_PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return jwtProperties.getPublicPaths().stream().anyMatch(p -> path.equals(p) || path.equals(p + "/"));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        writeUnauthorized(response, ErrorCode.UNAUTHORIZED.getMessage());
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), message)));
    }
}
