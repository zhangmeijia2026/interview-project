package com.group5.interview.module.jd;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.FileResponse;
import com.group5.interview.module.jd.dto.JdDetailView;
import com.group5.interview.module.jd.dto.JdFileView;
import com.group5.interview.module.jd.dto.JdLibraryItem;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 历史岗位 JD 保留库（与具体面试无关）：新建面试时"选择历史 JD"，并可查看
 * 原图/原文档（/file）或解析文本（/{id}）。契约见 docs/06 §JD 库。
 */
@Tag(name = "JD 库")
@RestController
@RequestMapping("/api/jds")
@RequiredArgsConstructor
public class JdLibraryController {

    private final JdService jdService;

    @Operation(summary = "我的 JD 列表（含来源类型/文本摘要/解析状态）")
    @GetMapping
    public ApiResponse<List<JdLibraryItem>> list() {
        return ApiResponse.ok(jdService.library(UserContext.currentUserId()));
    }

    @Operation(summary = "JD 详情（解析后文本；file 类型可另看原图/原文档）")
    @GetMapping("/{id}")
    public ApiResponse<JdDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(jdService.detail(id, UserContext.currentUserId()));
    }

    @Operation(summary = "拉回原始 JD 文件（图片 inline 预览 / PDF / DOCX）")
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        JdFileView view = jdService.fileView(id, UserContext.currentUserId());
        return FileResponse.inline(view.path(), view.fileName());
    }
}
