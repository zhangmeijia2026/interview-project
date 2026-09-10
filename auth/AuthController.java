package com.group5.interview.module.auth;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.module.auth.dto.LoginRequest;
import com.group5.interview.module.auth.dto.RefreshRequest;
import com.group5.interview.module.auth.dto.RegisterRequest;
import com.group5.interview.module.auth.dto.TokenResponse;
import com.group5.interview.module.auth.dto.UserSummary;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public ApiResponse<UserSummary> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "刷新令牌（轮换 refresh token）")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authService.logout(UserContext.currentUserId(), authorization.substring("Bearer ".length()));
        return ApiResponse.ok();
    }
}
