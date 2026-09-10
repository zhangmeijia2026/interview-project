package com.group5.interview.module.admin;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.PageResult;
import com.group5.interview.module.admin.dto.AdminInterviewView;
import com.group5.interview.module.admin.dto.AdminUserView;
import com.group5.interview.module.report.dto.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 管理端用户管理（/api/admin/** 由 AdminOnlyInterceptor 门禁）。 */
@Tag(name = "用户管理（管理员）")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户检索/列表（昵称/邮箱模糊 + 可选 role/active 过滤，分页按注册倒序）")
    @GetMapping
    public ApiResponse<PageResult<AdminUserView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(adminUserService.list(keyword, role, active, page, size));
    }

    @Operation(summary = "查看某用户的全部面试")
    @GetMapping("/{userId}/interviews")
    public ApiResponse<List<AdminInterviewView>> interviews(@PathVariable Long userId) {
        return ApiResponse.ok(adminUserService.interviewsOf(userId));
    }

    @Operation(summary = "只读查看某用户某场面试的综合报告")
    @GetMapping("/{userId}/interviews/{interviewId}/report")
    public ApiResponse<ReportResponse> report(@PathVariable Long userId,
                                              @PathVariable Long interviewId) {
        return ApiResponse.ok(adminUserService.reportOf(userId, interviewId));
    }
}
