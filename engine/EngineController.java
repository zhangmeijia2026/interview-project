package com.group5.interview.module.engine;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.module.engine.dto.*;
import com.group5.interview.module.interview.InterviewService;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "模拟面试会话")
@RestController
@RequestMapping("/api/interviews/{interviewId}")
@RequiredArgsConstructor
public class EngineController {
    private final EngineService engineService;
    private final SseEmitterRegistry sseRegistry;
    private final InterviewService interviewService;

    @Operation(summary = "开始 / 恢复面试")
    @PostMapping("/start")
    public ApiResponse<SessionResponse> start(@PathVariable Long interviewId) {
        return ApiResponse.ok(engineService.start(interviewId, UserContext.currentUserId()));
    }

    @Operation(summary = "面试会话状态（断点续传）")
    @GetMapping("/session")
    public ApiResponse<SessionResponse> session(@PathVariable Long interviewId) {
        return ApiResponse.ok(engineService.session(interviewId, UserContext.currentUserId()));
    }

    @Operation(summary = "订阅面试 SSE 事件流（question/grade/followup/report_done）")
    @PostMapping("/events")
    public SseEmitter events(@PathVariable Long interviewId) {
        interviewService.required(interviewId, UserContext.currentUserId());
        return sseRegistry.register(interviewId);
    }

    @Operation(summary = "作答当前题目")
    @PostMapping("/questions/{orderIndex}/answer")
    public ApiResponse<AnswerResponse> answer(@PathVariable Long interviewId,
                                              @PathVariable int orderIndex,
                                              @RequestBody @Valid AnswerRequest request) {
        return ApiResponse.ok(engineService.answer(interviewId, UserContext.currentUserId(), orderIndex,
                request.answer(), request.elapsedSeconds()));
    }

    @Operation(summary = "查看本题提示/参考答案（practice 随时可见，formal 作答后可见）")
    @GetMapping("/questions/{orderIndex}/reference")
    public ApiResponse<ReferenceView> reference(@PathVariable Long interviewId, @PathVariable int orderIndex) {
        return ApiResponse.ok(engineService.reference(interviewId, UserContext.currentUserId(), orderIndex));
    }

    @Operation(summary = "回答追问")
    @PostMapping("/followup/{orderIndex}/answer")
    public ApiResponse<FollowUpResult> answerFollowUp(@PathVariable Long interviewId,
                                                      @PathVariable int orderIndex,
                                                      @RequestBody @Valid FollowUpRequest request) {
        return ApiResponse.ok(engineService.answerFollowUp(interviewId, UserContext.currentUserId(), orderIndex, request.answer()));
    }

    @Operation(summary = "跳过追问")
    @PostMapping("/questions/{orderIndex}/skip")
    public ApiResponse<Void> skipFollowUp(@PathVariable Long interviewId, @PathVariable int orderIndex) {
        engineService.skipFollowUp(interviewId, UserContext.currentUserId(), orderIndex);
        return ApiResponse.ok(null);
    }
}
