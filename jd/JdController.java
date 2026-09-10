package com.group5.interview.module.jd;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.module.jd.dto.JdResponse;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "JD 与匹配")
@RestController
@RequestMapping("/api/interviews/{interviewId}/jd")
@RequiredArgsConstructor
public class JdController {
    private final JdService jdService;

    @Operation(summary = "上传 JD 文件（PDF/DOCX/图片，图片自动 OCR）")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<JdResponse> upload(@PathVariable Long interviewId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(jdService.uploadFile(interviewId, UserContext.currentUserId(), file));
    }

    @Operation(summary = "直接粘贴 JD 原文")
    @PostMapping("/text")
    public ApiResponse<JdResponse> addText(@PathVariable Long interviewId, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(jdService.addText(interviewId, UserContext.currentUserId(), body.get("rawText")));
    }

    @Operation(summary = "JD 行（解析状态轮询）")
    @GetMapping
    public ApiResponse<JdResponse> get(@PathVariable Long interviewId) {
        return ApiResponse.ok(jdService.get(interviewId, UserContext.currentUserId()));
    }

    @Operation(summary = "复用历史 JD：绑定并同步推进（免重上传/解析）")
    @PostMapping("/reuse")
    public ApiResponse<JdResponse> reuse(@PathVariable Long interviewId, @RequestBody Map<String, Long> body) {
        Long jdId = body.get("jdId");
        if (jdId == null) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "缺少 jdId");
        return ApiResponse.ok(jdService.reuse(interviewId, UserContext.currentUserId(), jdId));
    }
}
