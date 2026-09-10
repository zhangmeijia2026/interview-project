package com.group5.interview.module.jd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.ai.AIService;
import com.group5.interview.ai.rag.TextChunkService;
import com.group5.interview.entity.Interview;
import com.group5.interview.entity.JobDescription;
import com.group5.interview.module.interview.InterviewService;
import com.group5.interview.module.jd.dto.JdParsed;
import com.group5.interview.module.resume.TikaTextExtractor;
import com.group5.interview.repository.InterviewRepository;
import com.group5.interview.repository.JdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * JD 异步解析：文字类文件（PDF/DOCX）走 Tika 抽文、图片走 Tesseract OCR，
 * 得到 raw_text 后一律经 AIService(jd-parse) 结构化。
 *
 * <p>jd-parse 在双模式下都保证为本地关键词算法（mock 直连本地实现；真实模式把
 * jd-parse 路由回离线引擎，不消耗模型额度——团队决策，docs/07），
 * 完成后推进面试状态 resume_uploaded -&gt; jd_uploaded。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JdAsyncProcessor {

    /** 图片走 OCR 的扩展名（与 JdService.EXTENSIONS 交集即图片）。 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "bmp", "webp");

    private final JdRepository jdRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewService interviewService;
    private final TikaTextExtractor tikaTextExtractor;
    private final JdOcrService jdOcrService;
    private final AIService aiService;
    private final TextChunkService textChunkService;
    private final ObjectMapper objectMapper;

    @Async("taskExecutor")
    @Transactional
    public void process(Long jdId) {
        long started = System.nanoTime();
        JobDescription jd = jdRepository.findById(jdId).orElse(null);
        if (jd == null) return;
        try {
            if (jd.getFilePath() != null) {
                jd.setRawText(extractText(jd.getFilePath()));
            }
            if (jd.getRawText() == null || jd.getRawText().isBlank()) {
                throw new IllegalArgumentException("JD 未提取到可用文本");
            }
            String text = jd.getRawText().length() > 6000 ? jd.getRawText().substring(0, 6000) : jd.getRawText();
            JdParsed parsed = aiService.structured("jd-parse", text, JdParsed.class);
            jd.setParsedData(objectMapper.writeValueAsString(parsed));
            jd.setStatus("ready");
            jdRepository.save(jd);
            textChunkService.indexSource("jd", jdId, jd.getUserId(), jd.getRawText()); // R10：JD 全文进检索语料
            advanceInterview(jdId);
            log.info("[ai] jd-parse jdId={} cost={}ms", jdId, (System.nanoTime() - started) / 1_000_000);
        } catch (Exception e) {
            jd.setStatus("failed");
            jdRepository.save(jd);
            log.warn("JD 解析失败 jdId={}: {}", jdId, e.getMessage());
        }
    }

    /** 按落盘扩展名分流：图片→OCR，PDF/DOCX→Tika。 */
    private String extractText(String filePath) {
        String ext = extension(filePath);
        Path path = Path.of(filePath);
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return jdOcrService.ocrText(path);
        }
        try {
            return tikaTextExtractor.extract(path);
        } catch (Exception e) {
            throw new IllegalStateException("文档抽文失败（PDF/DOCX）: " + e.getMessage());
        }
    }

    private String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void advanceInterview(Long jdId) {
        Interview interview = interviewRepository.findByJdId(jdId).orElse(null);
        if (interview != null) {
            interviewService.onJdReady(interview.getId(), interview.getUserId());
        }
    }
}
