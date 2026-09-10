package com.group5.interview.module.feedback;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.module.feedback.dto.FeedbackSubmitRequest;
import com.group5.interview.module.feedback.dto.FeedbackView;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 用户反馈（普通用户端）：提交与查看我的反馈及处理状态。 */
@Tag(name = "用户反馈")
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "提交反馈")
    @PostMapping
    public ApiResponse<FeedbackView> submit(@Valid @RequestBody FeedbackSubmitRequest request) {
        return ApiResponse.ok(feedbackService.submit(UserContext.currentUserId(), request));
    }

    @Operation(summary = "我的反馈列表（含管理员回复状态）")
    @GetMapping
    public ApiResponse<List<FeedbackView>> mine() {
        return ApiResponse.ok(feedbackService.listMine(UserContext.currentUserId()));
    }
}
