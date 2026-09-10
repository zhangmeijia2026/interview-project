package com.group5.interview.module.report;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.module.report.dto.ReportResponse;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "综合报告")
@RestController
@RequestMapping("/api/interviews/{interviewId}")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @Operation(summary = "获取综合报告（completed 且缺失时自动补生成）")
    @GetMapping("/report")
    public ApiResponse<ReportResponse> report(@PathVariable Long interviewId) {
        return ApiResponse.ok(reportService.get(interviewId, UserContext.currentUserId()));
    }
}
