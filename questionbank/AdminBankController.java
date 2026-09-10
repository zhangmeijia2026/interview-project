package com.group5.interview.module.questionbank;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.PageResult;
import com.group5.interview.module.questionbank.dto.BankAdminView;
import com.group5.interview.module.questionbank.dto.BankGenRequest;
import com.group5.interview.module.questionbank.dto.BankUpsertRequest;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "题库管理（管理员）")
@RestController
@RequestMapping("/api/admin/bank")
@RequiredArgsConstructor
public class AdminBankController {

    private final BankService bankService;

    @Operation(summary = "题库管理列表（含答案/启用态）")
    @GetMapping("/questions")
    public ApiResponse<PageResult<BankAdminView>> page(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(bankService.adminPage(category, keyword, page, size));
    }

    @Operation(summary = "新增题目")
    @PostMapping("/questions")
    public ApiResponse<BankAdminView> create(@RequestBody @Valid BankUpsertRequest request) {
        return ApiResponse.ok(bankService.create(request, UserContext.currentUserId()));
    }

    @Operation(summary = "修改题目")
    @PutMapping("/questions/{id}")
    public ApiResponse<BankAdminView> update(@PathVariable Long id, @RequestBody @Valid BankUpsertRequest request) {
        return ApiResponse.ok(bankService.update(id, request));
    }

    @Operation(summary = "删除（逻辑删除）")
    @DeleteMapping("/questions/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        bankService.delete(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "AI 一键扩充题库")
    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@RequestBody @Valid BankGenRequest request) {
        int inserted = bankService.generate(request, UserContext.currentUserId());
        return ApiResponse.ok(Map.of("inserted", inserted));
    }
}
