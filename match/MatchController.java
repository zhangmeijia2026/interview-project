package com.group5.interview.module.match;

import com.group5.interview.common.ApiResponse;
import com.group5.interview.module.match.dto.FocusItem;
import com.group5.interview.module.match.dto.MatchResultResponse;
import com.group5.interview.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "JD 与匹配")
@RestController
@RequestMapping("/api/interviews/{interviewId}")
@RequiredArgsConstructor
public class MatchController {
    private final MatchService matchService;

    @Operation(summary = "触发匹配（幂等：已有则重算）")
    @PostMapping("/match")
    public ApiResponse<MatchResultResponse> match(@PathVariable Long interviewId) {
        return ApiResponse.ok(matchService.runMatch(interviewId, UserContext.currentUserId()));
    }

    @Operation(summary = "匹配详情")
    @GetMapping("/match")
    public ApiResponse<MatchResultResponse> get(@PathVariable Long interviewId) {
        return ApiResponse.ok(matchService.get(interviewId, UserContext.currentUserId()));
    }

    @Operation(summary = "重新生成面试重点")
    @PostMapping("/focus/regenerate")
    public ApiResponse<List<FocusItem>> regenerate(@PathVariable Long interviewId) {
        return ApiResponse.ok(matchService.regenerateFocus(interviewId, UserContext.currentUserId()));
    }
}
