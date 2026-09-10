package com.group5.interview.module.feedback;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.PageResult;
import com.group5.interview.module.feedback.dto.FeedbackReplyRequest;
import com.group5.interview.module.feedback.dto.FeedbackView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 用户反馈管理（管理员，/api/admin/** 由 AdminOnlyInterceptor 门禁）。 */
@Tag(name = "反馈管理（管理员）")
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "反馈列表（可按状态筛选）")
    @GetMapping
    public ApiResponse<PageResult<FeedbackView>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(feedbackService.adminPage(status, page, size));
    }

    @Operation(summary = "处理反馈（更新状态与回复）")
    @PutMapping("/{id}")
    public ApiResponse<FeedbackView> reply(@PathVariable Long id,
                                           @RequestBody @Valid FeedbackReplyRequest request) {
        return ApiResponse.ok(feedbackService.reply(id, request.status(), request.reply()));
    }
}
