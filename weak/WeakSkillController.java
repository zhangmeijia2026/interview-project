package com.group5.interview.module.weak;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.common.PageResult;
import com.group5.interview.module.weak.dto.WeakSkillView;
import com.group5.interview.module.weak.dto.WrongQuestionView;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "个人薄弱技能库 / 错题本")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WeakSkillController {

    private final WeakSkillService weakSkillService;

    @Operation(summary = "个人薄弱技能库（severity 升序=最弱在前）")
    @GetMapping("/weak-skills")
    public ApiResponse<List<WeakSkillView>> list() {
        return ApiResponse.ok(weakSkillService.list(UserContext.currentUserId()));
    }

    @Operation(summary = "标记薄弱技能为已攻克")
    @PostMapping("/weak-skills/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long id) {
        weakSkillService.resolve(UserContext.currentUserId(), id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "错题本：低分(<60)题目快照")
    @GetMapping("/weak/wrong-questions")
    public ApiResponse<PageResult<WrongQuestionView>> wrongQuestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(weakSkillService.wrongQuestions(UserContext.currentUserId(), page, size));
    }
}
