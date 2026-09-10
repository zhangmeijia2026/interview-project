package com.group5.interview.module.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.module.resume.dto.ResumeResponse;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "简历")
@RestController
@RequestMapping("/api/interviews/{interviewId}/resume")
@RequiredArgsConstructor
public class ResumeController {
    private final ResumeService resumeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResumeResponse> upload(@PathVariable Long interviewId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(resumeService.upload(interviewId, UserContext.currentUserId(), file));
    }
    @GetMapping
    public ApiResponse<ResumeResponse> get(@PathVariable Long interviewId) {
        return ApiResponse.ok(resumeService.get(interviewId, UserContext.currentUserId()));
    }
    @PutMapping("/confirm")
    public ApiResponse<ResumeResponse> confirm(@PathVariable Long interviewId,
                                                @RequestBody(required = false) JsonNode parsedData) {
        return ApiResponse.ok(resumeService.confirm(interviewId, UserContext.currentUserId(), parsedData));
    }

    @Operation(summary = "复用历史简历：绑定并确认，免重新上传解析")
    @PostMapping("/reuse")
    public ApiResponse<ResumeResponse> reuse(@PathVariable Long interviewId, @RequestBody Map<String, Long> body) {
        Long resumeId = body.get("resumeId");
        if (resumeId == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "缺少 resumeId");
        return ApiResponse.ok(resumeService.reuse(interviewId, UserContext.currentUserId(), resumeId));
    }
}
