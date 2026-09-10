package com.group5.interview.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动期把已就绪语料（ready 简历/JD + 启用题库）全量重建进 text_chunks。
 *
 * <p>仅为幂等兜底：正常路径已在 resume/JD ready 与题库入库时增量建索引；
 * 这里统一重建一次，保证旧库/中断后数据也齐全。失败只记日志，不阻断启动。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagStartupIndexer {

    private final TextChunkService textChunkService;
    private final RagProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        if (!properties.isEnabled()) {
            log.info("[rag] RAG 已关闭（app.rag.enabled=false），跳过启动重建");
            return;
        }
        try {
            textChunkService.purgeAndRebuildAll();
        } catch (Exception e) {
            // RAG 重建失败不影响业务接口可用；真实模式下仅检索注入会少命中
            log.warn("[rag] 启动重建失败（不影响服务启动）: {}", e.getMessage());
        }
    }
}
