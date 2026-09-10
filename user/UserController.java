package com.group5.interview.module.user;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.module.user.dto.*;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserStatsService userStatsService;

    @Operation(summary = "获取当前用户及设置")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(userService.me(UserContext.currentUserId()));
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/me")
    public ApiResponse<MeResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(UserContext.currentUserId(), request));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(UserContext.currentUserId(), request);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新用户设置")
    @PutMapping("/settings")
    public ApiResponse<SettingsResponse> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return ApiResponse.ok(userService.updateSettings(UserContext.currentUserId(), request));
    }

    @Operation(summary = "个人中心统计（面试/练习次数、均分、错题、薄弱技能、最近评级）")
    @GetMapping("/stats")
    public ApiResponse<UserStatsResponse> stats() {
        return ApiResponse.ok(userStatsService.stats(UserContext.currentUserId()));
    }
}
