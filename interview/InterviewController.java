package com.group5.interview.module.interview;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.module.interview.dto.CreateInterviewRequest;
import com.group5.interview.module.interview.dto.InterviewResponse;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "面试会话")
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {
    private final InterviewService interviewService;
    @PostMapping public ApiResponse<InterviewResponse> create(@Valid @RequestBody CreateInterviewRequest request) {
        return ApiResponse.ok(interviewService.create(UserContext.currentUserId(), request));
    }
    @GetMapping public ApiResponse<List<InterviewResponse>> list(@RequestParam(defaultValue = "all") String status) {
        return ApiResponse.ok(interviewService.list(UserContext.currentUserId(), status));
    }
    @GetMapping("/{id}") public ApiResponse<InterviewResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(interviewService.detail(id, UserContext.currentUserId()));
    }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) {
        interviewService.delete(id, UserContext.currentUserId()); return ApiResponse.ok();
    }
}
