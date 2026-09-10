package com.group5.interview.module.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.Interview;
import com.group5.interview.entity.Resume;
import com.group5.interview.module.interview.InterviewStatus;
import com.group5.interview.module.interview.InterviewService;
import com.group5.interview.module.resume.dto.ResumeResponse;
import com.group5.interview.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumeService {
    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("pdf", "docx");
    private final ResumeRepository resumeRepository;
    private final InterviewService interviewService;
    private final ResumeAsyncProcessor resumeAsyncProcessor;
    private final ObjectMapper objectMapper;
    @Value("${app.upload-dir:./uploads}") private String uploadDir;

    public ResumeResponse upload(Long interviewId, Long userId, MultipartFile file) {
        Interview interview = interviewService.required(interviewId, userId);
        validate(file);
        String md5 = md5(file);
        Resume resume = resumeRepository.findByUserIdAndFileMd5(userId, md5).orElseGet(() -> saveNew(userId, file, md5));
        interviewService.bindResume(interview.getId(), userId, resume.getId());
        if ("processing".equals(resume.getStatus())) resumeAsyncProcessor.process(resume.getId());
        return toResponse(resume);
    }

    /** 我的简历保留库：解析完成的历史简历，供新建面试时选择复用（docs/06 §简历复用）。 */
    public List<ResumeResponse> listReady(Long userId) {
        return resumeRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, "ready")
                .stream().map(this::toResponse).toList();
    }

    /** 复用历史简历：绑定到当前面试并立即确认（免重复上传/解析），前提是该简历已 ready。 */
    @Transactional
    public ResumeResponse reuse(Long interviewId, Long userId, Long resumeId) {
        Resume resume = requiredResume(resumeId, userId);
        if (!"ready".equals(resume.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该简历尚未解析完成，暂不能复用");
        }
        interviewService.bindResume(interviewId, userId, resumeId);
        Interview interview = interviewService.required(interviewId, userId);
        if (InterviewStatus.from(interview.getStatus()) == InterviewStatus.DRAFT) {
            interviewService.confirmResume(interviewId, userId);
        }
        return toResponse(resume);
    }

    /** 简历库详情：本人某份简历（任意状态均可查看已保存文件）。 */
    @Transactional(readOnly = true)
    public ResumeResponse detail(Long resumeId, Long userId) {
        return toResponse(requiredResume(resumeId, userId));
    }

    /** 简历库原文件预览：归属校验后返回实体（controller 据此流式返回原始 PDF/DOCX）。 */
    @Transactional(readOnly = true)
    public Resume requireOwned(Long resumeId, Long userId) {
        return requiredResume(resumeId, userId);
    }

    public ResumeResponse get(Long interviewId, Long userId) {
        Interview interview = interviewService.required(interviewId, userId);
        if (interview.getResumeId() == null) throw new BusinessException(ErrorCode.NOT_FOUND, "尚未上传简历");
        return toResponse(requiredResume(interview.getResumeId(), userId));
    }

    public ResumeResponse confirm(Long interviewId, Long userId, JsonNode editedData) {
        Interview interview = interviewService.required(interviewId, userId);
        if (interview.getResumeId() == null) throw new BusinessException(ErrorCode.NOT_FOUND, "尚未上传简历");
        Resume resume = requiredResume(interview.getResumeId(), userId);
        if (!"ready".equals(resume.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "简历尚未解析完成，无法确认");
        if (editedData != null && !editedData.isNull()) {
            if (!editedData.isObject()) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "parsedData 必须是 JSON 对象");
            try { resume.setParsedData(objectMapper.writeValueAsString(editedData)); }
            catch (Exception exception) { throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "parsedData 格式错误"); }
            resume.setUpdatedAt(LocalDateTime.now());
            resumeRepository.save(resume);
        }
        interviewService.confirmResume(interviewId, userId);
        return toResponse(resume);
    }

    private Resume saveNew(Long userId, MultipartFile file, String md5) {
        String extension = extension(file.getOriginalFilename());
        Path dir = Path.of(uploadDir, String.valueOf(userId), DateTimeFormatter.ofPattern("yyyyMM").format(LocalDateTime.now()));
        try {
            Files.createDirectories(dir);
            Path destination = dir.resolve(UUID.randomUUID() + "." + extension);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            Resume resume = new Resume();
            resume.setUserId(userId); resume.setFileName(file.getOriginalFilename()); resume.setFilePath(destination.toAbsolutePath().toString());
            resume.setFileMd5(md5); resume.setStatus("processing"); resume.setCreatedAt(LocalDateTime.now()); resume.setUpdatedAt(LocalDateTime.now());
            return resumeRepository.save(resume);
        } catch (IOException exception) { throw new BusinessException(ErrorCode.INTERNAL, "保存上传文件失败"); }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请选择简历文件");
        if (file.getSize() > MAX_SIZE) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        if (!EXTENSIONS.contains(extension(file.getOriginalFilename()))) throw new BusinessException(ErrorCode.MEDIA_NOT_SUPPORTED, "仅支持 PDF 或 DOCX 文件");
    }
    private String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
    private String md5(MultipartFile file) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(file.getBytes())); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.INTERNAL, "读取上传文件失败"); }
    }
    private Resume requiredResume(Long id, Long userId) { return resumeRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "简历不存在")); }
    private ResumeResponse toResponse(Resume resume) {
        try { return new ResumeResponse(resume.getId(), resume.getFileName(), resume.getStatus(),
                resume.getParsedData() == null ? null : objectMapper.readTree(resume.getParsedData()), resume.getCreatedAt()); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.INTERNAL, "简历数据读取失败"); }
    }
}
