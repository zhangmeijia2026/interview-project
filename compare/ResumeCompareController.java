package com.group5.interview.module.compare;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.module.compare.dto.CompareListItem;
import com.group5.interview.module.compare.dto.CompareResumeRequest;
import com.group5.interview.module.compare.dto.CompareView;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 简历对比（R3）：从简历库选两份 ready 简历做 AI 对比，支持历史查看与删除。
 * 契约见 docs/06 §简历对比。
 */
@Tag(name = "简历对比")
@RestController
@RequestMapping("/api/compare")
@RequiredArgsConstructor
public class ResumeCompareController {

    private final ResumeCompareService compareService;

    @Operation(summary = "对比两份简历（须同属本人且解析完成）")
    @PostMapping("/resumes")
    public ApiResponse<CompareView> compare(@Valid @RequestBody CompareResumeRequest request) {
        return ApiResponse.ok(compareService.compare(request, UserContext.currentUserId()));
    }

    @Operation(summary = "我的对比历史列表")
    @GetMapping("/list")
    public ApiResponse<List<CompareListItem>> list() {
        return ApiResponse.ok(compareService.list(UserContext.currentUserId()));
    }

    @Operation(summary = "某次对比完整结果")
    @GetMapping("/{id}")
    public ApiResponse<CompareView> detail(@PathVariable Long id) {
        return ApiResponse.ok(compareService.detail(id, UserContext.currentUserId()));
    }

    @Operation(summary = "删除某次对比记录")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        compareService.delete(id, UserContext.currentUserId());
        return ApiResponse.ok();
    }
}
