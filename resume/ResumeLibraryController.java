package com.group5.interview.module.resume;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.FileResponse;
import com.group5.interview.entity.Resume;
import com.group5.interview.module.resume.dto.ResumeResponse;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

/**
 * 我的简历保留库（与具体面试无关）：新建面试时下拉选择复用；可查看解析结果与
 * 原始文件。契约见 docs/06 §简历库。
 */
@Tag(name = "简历库")
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeLibraryController {
    private final ResumeService resumeService;

    @Operation(summary = "我的可复用简历列表（仅解析完成，按更新时间倒序）")
    @GetMapping
    public ApiResponse<List<ResumeResponse>> list() {
        return ApiResponse.ok(resumeService.listReady(UserContext.currentUserId()));
    }

    @Operation(summary = "某份简历解析详情")
    @GetMapping("/{id}")
    public ApiResponse<ResumeResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.detail(id, UserContext.currentUserId()));
    }

    @Operation(summary = "查看简历原始文件（PDF inline 预览 / DOCX 下载）")
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        Resume resume = resumeService.requireOwned(id, UserContext.currentUserId());
        return FileResponse.inline(Path.of(resume.getFilePath()), resume.getFileName());
    }
}
