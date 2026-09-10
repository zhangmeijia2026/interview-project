package com.group5.interview.module.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.ai.AIService;
import com.group5.interview.ai.rag.TextChunkService;
import com.group5.interview.entity.Resume;
import com.group5.interview.module.resume.dto.ResumeParsed;
import com.group5.interview.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAsyncProcessor {
    private final ResumeRepository resumeRepository;
    private final TikaTextExtractor tikaTextExtractor;
    private final AIService aiService;
    private final TextChunkService textChunkService;
    private final ObjectMapper objectMapper;

    @Async("taskExecutor")
    @Transactional
    public void process(Long resumeId) {
        long startedAt = System.nanoTime();
        Resume resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) return;
        try {
            String text = tikaTextExtractor.extract(Path.of(resume.getFilePath()));
            if (text.isBlank()) throw new IllegalArgumentException("文件未提取到可用文本");
            resume.setFullText(text);
            ResumeParsed parsed = aiService.structured("resume-parse", truncate(text), ResumeParsed.class);
            resume.setParsedData(objectMapper.writeValueAsString(parsed));
            resume.setStatus("ready");
            textChunkService.indexSource("resume", resumeId, resume.getUserId(), text); // R10：简历全文进检索语料
            log.info("[ai] resume-parse resumeId={} cost={}ms", resumeId, (System.nanoTime() - startedAt) / 1_000_000);
        } catch (Exception exception) {
            resume.setStatus("failed");
            log.warn("简历解析失败 resumeId={}: {}", resumeId, exception.getMessage());
        }
        resume.setUpdatedAt(LocalDateTime.now());
    }

    private String truncate(String text) {
        return text.length() <= 8000 ? text : text.substring(0, 8000);
    }
}
