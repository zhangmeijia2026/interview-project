package com.group5.interview.module.admin;

import com.group5.interview.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端概览统计（后台新首页，/api/admin/** 由 AdminOnlyInterceptor 门禁）。 */
@Tag(name = "后台概览（管理员）")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    @Operation(summary = "概览统计（用户/面试/待处理反馈/题库等汇总卡片）")
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(adminOverviewService.overview());
    }
}
