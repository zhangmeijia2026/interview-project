package com.group5.interview.module.questionbank;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.PageResult;
import com.group5.interview.module.questionbank.dto.BankCategoryCount;
import com.group5.interview.module.questionbank.dto.BankQuestionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "题库（浏览/练习入口）")
@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;

    @Operation(summary = "题库分类 + 题量")
    @GetMapping("/categories")
    public ApiResponse<List<BankCategoryCount>> categories() {
        return ApiResponse.ok(bankService.categories());
    }

    @Operation(summary = "题库浏览（不含答案/提示，避免剧透）")
    @GetMapping("/questions")
    public ApiResponse<PageResult<BankQuestionView>> questions(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(bankService.publicPage(category, keyword, page, size));
    }
}
