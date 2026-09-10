package com.group5.interview.module.admin;

import com.group5.interview.ai.rag.RagInjector;
import com.group5.interview.ai.rag.TextChunkService;
import com.group5.interview.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端 RAG/向量检索运维（/api/admin/** 由 AdminOnlyInterceptor 门禁）。
 *
 * <p>stats=看当前切分/命中配置与 Qdrant 适配段；rebuild=清空 text_chunks 后按
 * 「全部 ready 简历 + 全部 ready JD + 全部启用题库」全量重建，provider=qdrant 时同步
 * 重建 Qdrant 镜像集合（text_chunks→向量库同步入口，docs/07 §10/§11）。
 * search=无 token 成本的验收检索（返回 provider 便于断言真走 Qdrant 还是回退 local）；
 * qdrant/health=看镜像库连通与点数。</p>
 */
@Tag(name = "RAG 运维（管理员）")
@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
public class AdminRagController {

    private final TextChunkService textChunkService;
    private final RagInjector ragInjector;

    @Operation(summary = "RAG 统计：开关/实现/维度/块数分布 + qdrant 适配段")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(textChunkService.stats());
    }

    @Operation(summary = "全量重建 text_chunks（provider=qdrant 时含 Qdrant 镜像重建）")
    @PostMapping("/rebuild")
    public ApiResponse<Map<String, Object>> rebuild() {
        return ApiResponse.ok(textChunkService.purgeAndRebuildAll());
    }

    @Operation(summary = "RAG 检索验收：q=查询词，topK=最多命中数；uid 留空=看全量（resume/jd/question_bank 均含）")
    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam("q") String q,
            @RequestParam(value = "topK", defaultValue = "4") int topK) {
        return ApiResponse.ok(ragInjector.adminSearch(q, topK, null));
    }

    @Operation(summary = "Qdrant 健康：configured=是否切 qdrant；connected=集合可达；chunks=镜像点数")
    @GetMapping("/qdrant/health")
    public ApiResponse<Map<String, Object>> qdrantHealth() {
        return ApiResponse.ok(textChunkService.qdrantHealth());
    }
}
