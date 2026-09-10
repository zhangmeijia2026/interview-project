package com.group5.interview.module.match;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.ai.AIService;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.Interview;
import com.group5.interview.entity.JobDescription;
import com.group5.interview.entity.MatchResult;
import com.group5.interview.entity.Resume;
import com.group5.interview.module.interview.InterviewService;
import com.group5.interview.module.interview.InterviewStatus;
import com.group5.interview.module.jd.dto.JdParsed;
import com.group5.interview.module.match.dto.*;
import com.group5.interview.module.resume.dto.ResumeParsed;
import com.group5.interview.repository.JdRepository;
import com.group5.interview.repository.MatchResultRepository;
import com.group5.interview.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * F04 匹配：简历(ready) + JD(ready) → 纯 LLM 口径(离线规则)算分/差距/面试重点，
 * JSON 落 match_results，并把面试推进到 matched。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {
    private final MatchResultRepository matchResultRepository;
    private final ResumeRepository resumeRepository;
    private final JdRepository jdRepository;
    private final InterviewService interviewService;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    @Transactional
    public MatchResultResponse runMatch(Long interviewId, Long userId) {
        Interview interview = requireMatchable(interviewId, userId);
        ResumeParsed resume = readyResume(interview);
        JdParsed jd = readyJd(interview);

        MatchOut out = aiService.structured("match",
                new MatchInput(resume, jd, interview.getQuestionCount(), interview.getTargetPosition()), MatchOut.class);
        FocusOut focus = aiService.structured("focus",
                new FocusInput(out.gap(), interview.getQuestionCount(), interview.getTargetPosition(), resume),
                FocusOut.class);

        persist(interview, out, focus.focus());
        interviewService.markMatched(interviewId, userId, BigDecimal.valueOf(out.overall()));
        log.info("[match] interviewId={} overall={} gap={} focus={}", interviewId, out.overall(),
                out.gap().size(), focus.focus().size());
        return view(out.overall(), out.skill(), out.experience(), out.education(), out.gap(), focus.focus());
    }

    @Transactional(readOnly = true)
    public MatchResultResponse get(Long interviewId, Long userId) {
        Interview interview = interviewService.required(interviewId, userId);
        MatchResult row = matchResultRepository.findByInterviewId(interviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "尚未生成匹配结果，请先执行匹配"));
        List<GapItem> gap = read(row.getGapAnalysis(), new TypeReference<>() {});
        List<FocusItem> focus = read(row.getInterviewFocus(), new TypeReference<>() {});
        return view(row.getOverallScore().intValue(), row.getSkillMatch().intValue(),
                row.getExperienceMatch().intValue(), row.getEducationMatch().intValue(), gap, focus);
    }

    @Transactional
    public List<FocusItem> regenerateFocus(Long interviewId, Long userId) {
        Interview interview = interviewService.required(interviewId, userId);
        MatchResult row = matchResultRepository.findByInterviewId(interviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "请先执行匹配"));
        List<GapItem> gap = read(row.getGapAnalysis(), new TypeReference<>() {});
        ResumeParsed resume = readyResume(interview);
        FocusOut focus = aiService.structured("focus",
                new FocusInput(gap, interview.getQuestionCount(), interview.getTargetPosition(), resume),
                FocusOut.class);
        row.setInterviewFocus(write(focus.focus()));
        matchResultRepository.save(row);
        return focus.focus();
    }

    private void persist(Interview interview, MatchOut out, List<FocusItem> focus) {
        MatchResult row = matchResultRepository.findByInterviewId(interview.getId()).orElseGet(MatchResult::new);
        row.setInterviewId(interview.getId());
        row.setOverallScore(BigDecimal.valueOf(out.overall()));
        row.setSkillMatch(BigDecimal.valueOf(out.skill()));
        row.setExperienceMatch(BigDecimal.valueOf(out.experience()));
        row.setEducationMatch(BigDecimal.valueOf(out.education()));
        row.setGapAnalysis(write(out.gap()));
        row.setInterviewFocus(write(focus));
        if (row.getCreatedAt() == null) row.setCreatedAt(LocalDateTime.now());
        matchResultRepository.save(row);
    }

    private Interview requireMatchable(Long interviewId, Long userId) {
        Interview interview = interviewService.required(interviewId, userId);
        InterviewStatus status = InterviewStatus.from(interview.getStatus());
        if (status == InterviewStatus.IN_PROGRESS || status == InterviewStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONFLICT, "面试已开始或完成，不能重新匹配");
        }
        if (interview.getResumeId() == null || interview.getJdId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先上传并确认简历与 JD");
        }
        return interview;
    }

    private ResumeParsed readyResume(Interview interview) {
        Resume resume = resumeRepository.findByIdAndUserId(interview.getResumeId(), interview.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "简历不存在"));
        if (!"ready".equals(resume.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "简历尚未解析完成");
        }
        try {
            return objectMapper.readValue(resume.getParsedData(), ResumeParsed.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL, "简历结构化数据读取失败");
        }
    }

    private JdParsed readyJd(Interview interview) {
        JobDescription jd = jdRepository.findByIdAndUserId(interview.getJdId(), interview.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "JD 不存在"));
        if (!"ready".equals(jd.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "JD 尚未解析完成，请稍后再试");
        }
        try {
            return objectMapper.readValue(jd.getParsedData(), JdParsed.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL, "JD 结构化数据读取失败");
        }
    }

    private MatchResultResponse view(int overall, int skill, int experience, int education,
                                     List<GapItem> gap, List<FocusItem> focus) {
        return new MatchResultResponse(overall, skill, experience, education, gap, focus, summary(gap));
    }

    private String summary(List<GapItem> gap) {
        return gap == null || gap.isEmpty()
                ? "简历与该岗位匹配度高，技能、经验、学历基本覆盖 JD 要求。"
                : "整体存在 " + gap.size() + " 处明显差距（多为 JD 硬性要求与简历差异），建议围绕面试重点针对性准备。";
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "JSON 序列化失败"); }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try { return objectMapper.readValue(json, type); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "JSON 解析失败"); }
    }
}
