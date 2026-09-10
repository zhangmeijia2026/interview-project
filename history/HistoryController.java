package com.group5.interview.module.history;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.module.report.dto.ReportResponse;
import com.group5.interview.module.report.ReportService;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "历史记录")
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {
    private final HistoryService historyService;
    private final ReportService reportService;

    @Operation(summary = "历史列表（名称/岗位模糊搜索）")
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(historyService.list(UserContext.currentUserId(), keyword, page, size));
    }

    @Operation(summary = "历史会话报告详情（F06 复用）")
    @GetMapping("/{interviewId}")
    public ApiResponse<ReportResponse> detail(@PathVariable Long interviewId) {
        return ApiResponse.ok(reportService.get(interviewId, UserContext.currentUserId()));
    }

    @Operation(summary = "删除单条历史")
    @DeleteMapping("/{interviewId}")
    public ApiResponse<Void> delete(@PathVariable Long interviewId) {
        historyService.deleteOne(UserContext.currentUserId(), interviewId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "清空历史")
    @DeleteMapping
    public ApiResponse<Void> clear() {
        historyService.clear(UserContext.currentUserId());
        return ApiResponse.ok(null);
    }
}
