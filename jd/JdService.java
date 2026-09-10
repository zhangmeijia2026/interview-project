package com.group5.interview.module.jd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.Interview;
import com.group5.interview.entity.JobDescription;
import com.group5.interview.module.interview.InterviewService;
import com.group5.interview.module.jd.dto.JdDetailView;
import com.group5.interview.module.jd.dto.JdFileView;
import com.group5.interview.module.jd.dto.JdLibraryItem;
import com.group5.interview.module.jd.dto.JdResponse;
import com.group5.interview.repository.JdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JdService {
    private static final long MAX_SIZE = 10 * 1024 * 1024;
    /** 允许上传的 JD 文件：PDF/DOCX 走 Tika 抽文，图片走 Tesseract OCR（docs/03）。 */
    private static final Set<String> EXTENSIONS = Set.of("pdf", "docx", "jpg", "jpeg", "png", "bmp", "webp");
    private final JdRepository jdRepository;
    private final InterviewService interviewService;
    private final JdAsyncProcessor jdAsyncProcessor;
    private final ObjectMapper objectMapper;
    @Value("${app.upload-dir:./uploads}") private String uploadDir;

    /** 直接粘贴 JD 原文。 */
    public JdResponse addText(Long interviewId, Long userId, String rawText) {
        interviewService.required(interviewId, userId);
        if (rawText == null || rawText.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "JD 原文不能为空");
        }
        JobDescription jd = new JobDescription();
        jd.setUserId(userId);
        jd.setRawText(rawText.trim());
        jd.setParsedData("{}");
        jd.setStatus("processing");
        jd.setCreatedAt(LocalDateTime.now());
        jd = jdRepository.save(jd);
        bindAndProcess(interviewId, userId, jd.getId());
        return toResponse(jd);
    }

    /** multipart 上传 JD 文件（PDF/DOCX/图片）。图片走 OCR，文字类走 Tika。 */
    public JdResponse uploadFile(Long interviewId, Long userId, MultipartFile file) {
        interviewService.required(interviewId, userId);
        validate(file);
        String extension = extension(file.getOriginalFilename());
        Path dir = Path.of(uploadDir, String.valueOf(userId), DateTimeFormatter.ofPattern("yyyyMM").format(LocalDateTime.now()));
        JobDescription jd;
        try {
            Files.createDirectories(dir);
            Path destination = dir.resolve(UUID.randomUUID() + "." + extension);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            jd = new JobDescription();
            jd.setUserId(userId);
            jd.setFileName(file.getOriginalFilename());
            jd.setFilePath(destination.toAbsolutePath().toString());
            jd.setRawText("");
            jd.setParsedData("{}");
            jd.setStatus("processing");
            jd.setCreatedAt(LocalDateTime.now());
            jd = jdRepository.save(jd);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL, "保存 JD 文件失败");
        }
        bindAndProcess(interviewId, userId, jd.getId());
        return toResponse(jd);
    }

    /** 当前面试绑定的 JD 行（轮询解析进度用）。 */
    public JdResponse get(Long interviewId, Long userId) {
        Interview interview = interviewService.required(interviewId, userId);
        if (interview.getJdId() == null) throw new BusinessException(ErrorCode.NOT_FOUND, "尚未上传 JD");
        return toResponse(requiredJd(interview.getJdId(), userId));
    }

    /** 我的 JD 保留库列表：含来源类型与文本摘要，供新建面试"选择历史 JD"（P3）。 */
    @Transactional(readOnly = true)
    public List<JdLibraryItem> library(Long userId) {
        return jdRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(jd -> new JdLibraryItem(jd.getId(), typeOf(jd), jd.getFileName(), jd.getStatus(),
                        preview(jd.getRawText()), jd.getCreatedAt()))
                .toList();
    }

    /** JD 详情（解析文本查看）：file 类型可另经 /file 拉回原图/原文档。 */
    @Transactional(readOnly = true)
    public JdDetailView detail(Long id, Long userId) {
        JobDescription jd = requiredJd(id, userId);
        return new JdDetailView(jd.getId(), typeOf(jd), jd.getFileName(), jd.getStatus(),
                jd.getRawText(), jd.getCreatedAt());
    }

    /** 校验归属后返回原文件（文件型 JD；文本型 JD 无原文件，走 detail 看文本）。 */
    public JdFileView fileView(Long id, Long userId) {
        JobDescription jd = requiredJd(id, userId);
        if (jd.getFilePath() == null || jd.getFilePath().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该 JD 为文本录入，无原始文件");
        }
        return new JdFileView(Path.of(jd.getFilePath()), jd.getFileName() == null ? "jd-" + jd.getId() : jd.getFileName());
    }

    /** 复用历史 JD：绑定到当前面试并同步推进状态（免重上传/解析），前提是已 ready。 */
    @Transactional
    public JdResponse reuse(Long interviewId, Long userId, Long jdId) {
        JobDescription jd = requiredJd(jdId, userId);
        if (!"ready".equals(jd.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该 JD 尚未解析完成，暂不能复用");
        }
        interviewService.bindJd(interviewId, userId, jdId);
        // 同步已完成解析：若简历已确认，直接推进 resume_uploaded -> jd_uploaded。
        interviewService.onJdReady(interviewId, userId);
        return toResponse(jd);
    }

    private String typeOf(JobDescription jd) {
        return jd.getFilePath() == null || jd.getFilePath().isBlank() ? "text" : "file";
    }

    /** 列表摘要：取首个非空行（通常是职位名）前 60 字。 */
    private String preview(String rawText) {
        if (rawText == null || rawText.isBlank()) return "";
        String line = rawText.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst().orElse("");
        return line.length() <= 60 ? line : line.substring(0, 60);
    }

    private void bindAndProcess(Long interviewId, Long userId, Long jdId) {
        interviewService.bindJd(interviewId, userId, jdId);
        jdAsyncProcessor.process(jdId);
    }

    private JobDescription requiredJd(Long id, Long userId) {
        return jdRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "JD 不存在"));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请选择 JD 文件");
        if (file.getSize() > MAX_SIZE) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        if (!EXTENSIONS.contains(extension(file.getOriginalFilename()))) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_SUPPORTED, "仅支持 PDF、DOCX 或图片（jpg/jpeg/png/bmp/webp）");
        }
    }

    private String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private JdResponse toResponse(JobDescription jd) {
        try {
            JsonNode parsed = objectMapper.readTree(jd.getParsedData());
            return new JdResponse(jd.getId(), jd.getFileName(), jd.getStatus(), parsed, jd.getCreatedAt());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL, "JD 数据读取失败");
        }
    }
}
