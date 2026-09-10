package com.group5.interview.module.admin;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.PageResult;
import com.group5.interview.module.admin.dto.LlmCallView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理端 DeepSeek 调用统计（/api/admin/** 由 AdminOnlyInterceptor 门禁）。 */
@Tag(name = "模型调用统计（管理员）")
@RestController
@RequestMapping("/api/admin/llm")
@RequiredArgsConstructor
public class AdminLlmController {

    private final AdminLlmService adminLlmService;

    @Operation(summary = "近 N 天聚合统计（调用/费用/回退/prompt 占比，供 ECharts）")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(adminLlmService.stats(days));
    }

    @Operation(summary = "调用日志明细分页")
    @GetMapping("/calls")
    public ApiResponse<PageResult<LlmCallView>> calls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminLlmService.calls(page, size));
    }
}
