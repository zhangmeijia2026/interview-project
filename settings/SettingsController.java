package com.group5.interview.module.settings;

import com.group5.interview.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * F08 杂项：可选模型列表（MVP 只接 DeepSeek 系；切换入口在 /api/user/settings 已建）。
 */
@Tag(name = "设置与杂项")
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    @Operation(summary = "可选模型列表")
    @GetMapping("/models")
    public ApiResponse<List<Map<String, String>>> models() {
        return ApiResponse.ok(List.of(
                Map.of("id", "deepseek-chat", "name", "DeepSeek Chat（通用对话）"),
                Map.of("id", "deepseek-reasoner", "name", "DeepSeek Reasoner（深度思考）")));
    }
}
