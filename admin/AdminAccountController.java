package com.group5.interview.module.admin;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.module.admin.dto.AdminCreateAdminRequest;
import com.group5.interview.module.admin.dto.AdminSetActiveRequest;
import com.group5.interview.module.admin.dto.AdminUserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理端账号管理（新增管理员 + 启停/黑名单，/api/admin/** 由 AdminOnlyInterceptor 门禁）。 */
@Tag(name = "账号管理（管理员）")
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @Operation(summary = "账号停用(false=拉黑)/启用(true)")
    @PutMapping("/{id}/active")
    public ApiResponse<AdminUserView> setActive(@PathVariable Long id,
                                                @RequestBody AdminSetActiveRequest request) {
        if (request.active() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "active 不能为空");
        }
        return ApiResponse.ok(adminAccountService.setActive(id, request.active()));
    }

    @Operation(summary = "新增管理员账号")
    @PostMapping("/admins")
    public ApiResponse<AdminUserView> createAdmin(@RequestBody AdminCreateAdminRequest request) {
        return ApiResponse.ok(adminAccountService.createAdmin(request));
    }
}
