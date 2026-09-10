package com.group5.interview.module.compare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.ai.AIService;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.Resume;
import com.group5.interview.entity.ResumeComparison;
import com.group5.interview.module.compare.dto.CompareInput;
import com.group5.interview.module.compare.dto.CompareListItem;
import com.group5.interview.module.compare.dto.CompareResumeRequest;
import com.group5.interview.module.compare.dto.CompareResumesOut;
import com.group5.interview.module.compare.dto.CompareView;
import com.group5.interview.module.resume.ResumeService;
import com.group5.interview.module.resume.dto.ResumeParsed;
import com.group5.interview.repository.ResumeComparisonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 两份简历 AI 对比（R3）。两者须同属当前用户且解析完成（ready）；
 * 对比结果落 resume_comparisons，支持列表 / 详情 / 删除。
 * 契约见 docs/06 §简历对比。
 */
@Service
@RequiredArgsConstructor
public class ResumeCompareService {

    private static final String COMPARE_KEY = "compare-resumes";

    private final ResumeService resumeService;
    private final ResumeComparisonRepository comparisonRepository;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CompareView compare(CompareResumeRequest request, Long userId) {
        Resume resumeA = requireReady(request.resumeAId(), userId);
        Resume resumeB = requireReady(request.resumeBId(), userId);
        if (resumeA.getId().equals(resumeB.getId())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请选择两份不同的简历进行对比");
        }
        ResumeParsed parsedA = parseParsed(resumeA);
        ResumeParsed parsedB = parseParsed(resumeB);
        String target = request.targetPosition() == null ? null : request.targetPosition().trim();
        CompareInput input = new CompareInput(resumeA.getFileName(), resumeB.getFileName(),
                parsedA, parsedB, target == null || target.isEmpty() ? "（未提供目标岗位）" : target);
        CompareResumesOut result = aiService.structured(COMPARE_KEY, input, CompareResumesOut.class);

        ResumeComparison comparison = new ResumeComparison();
        comparison.setUserId(userId);
        comparison.setResumeAId(resumeA.getId());
        comparison.setResumeBId(resumeB.getId());
        comparison.setTitle(resumeA.getFileName() + " vs " + resumeB.getFileName());
        comparison.setResultJson(writeResult(result));
        comparison.setSummary(result.differenceSummary());
        comparison.setCreatedAt(LocalDateTime.now());
        comparisonRepository.save(comparison);
        return toView(comparison, result);
    }

    @Transactional(readOnly = true)
    public List<CompareListItem> list(Long userId) {
        return comparisonRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(c -> new CompareListItem(c.getId(), c.getResumeAId(), c.getResumeBId(),
                        c.getTitle(), c.getSummary(), c.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CompareView detail(Long id, Long userId) {
        ResumeComparison comparison = required(id, userId);
        return toView(comparison, parseResult(comparison.getResultJson()));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        ResumeComparison comparison = required(id, userId);
        comparisonRepository.delete(comparison);
    }

    private Resume requireReady(Long resumeId, Long userId) {
        Resume resume = resumeService.requireOwned(resumeId, userId);
        if (!"ready".equals(resume.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该简历尚未解析完成，不能参与对比");
        }
        return resume;
    }

    private ResumeParsed parseParsed(Resume resume) {
        if (resume.getParsedData() == null || resume.getParsedData().isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "该简历缺少结构化解析数据，无法对比");
        }
        try {
            return objectMapper.readValue(resume.getParsedData(), ResumeParsed.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL, "读取简历结构化数据失败");
        }
    }

    private ResumeComparison required(Long id, Long userId) {
        return comparisonRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "对比记录不存在"));
    }

    private String writeResult(CompareResumesOut result) {
        try { return objectMapper.writeValueAsString(result); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "对比结果保存失败"); }
    }

    private CompareResumesOut parseResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) return null;
        try { return objectMapper.readValue(resultJson, CompareResumesOut.class); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "对比结果读取失败"); }
    }

    private CompareView toView(ResumeComparison comparison, CompareResumesOut result) {
        return new CompareView(comparison.getId(), comparison.getResumeAId(), comparison.getResumeBId(),
                comparison.getTitle(), result, comparison.getCreatedAt());
    }
}
