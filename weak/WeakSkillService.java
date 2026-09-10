package com.group5.interview.module.weak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.ai.AIService;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.common.PageResult;
import com.group5.interview.entity.Interview;
import com.group5.interview.entity.InterviewQuestion;
import com.group5.interview.entity.QuestionBank;
import com.group5.interview.entity.UserWeakSkill;
import com.group5.interview.module.weak.dto.WeakExtractInput;
import com.group5.interview.module.weak.dto.WeakExtractOut;
import com.group5.interview.module.weak.dto.WeakSkillView;
import com.group5.interview.module.weak.dto.WrongQuestionView;
import com.group5.interview.repository.InterviewQuestionRepository;
import com.group5.interview.repository.InterviewRepository;
import com.group5.interview.repository.QuestionBankRepository;
import com.group5.interview.repository.UserWeakSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人薄弱技能库（R2）：
 * 报告/练习完成后由 {@link #aggregate} 调 AI(weak-extract) 识别薄弱技能并跨场次 upsert；
 * 同时派生"错题本"（低分题目快照，含参考答案）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeakSkillService {

    private final UserWeakSkillRepository weakRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository questionRepository;
    private final QuestionBankRepository bankRepository;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    /**
     * 聚合一场面试/练习的薄弱技能（完成态触发）。由 ReportService 在报告落库后调用；
     * matchGaps 可为空（练习模式）。rating 为空=练习模式（不写死评级依赖）。
     */
    @Transactional
    public void aggregate(Interview interview, List<InterviewQuestion> questions,
                          Map<String, Integer> dims, List<String> matchGaps, String rating) {
        try {
            List<InterviewQuestion> answered = new ArrayList<>();
            if (questions != null) {
                for (InterviewQuestion q : questions) {
                    if (q.getUserAnswer() != null && !q.getUserAnswer().isBlank()) answered.add(q);
                }
            }
            List<WeakExtractInput.QuestionBrief> briefs = answered.stream().map(q -> new WeakExtractInput.QuestionBrief(
                    q.getOrderIndex(), truncate(q.getContent(), 160),
                    q.getScore() == null ? 0 : q.getScore().intValue(),
                    truncate(q.getUserAnswer(), 200),
                    readLines(q.getFeedbackWeak()), q.getFeedbackSuggestion(), q.getResumeAdvice(),
                    q.getFollowUpScore() == null ? null : q.getFollowUpScore().intValue())).toList();
            WeakExtractInput input = new WeakExtractInput(interview.getId(), interview.getTargetPosition(),
                    interview.getMode(), rating, dims == null ? Map.of() : dims,
                    matchGaps == null ? List.of() : matchGaps, briefs);
            WeakExtractOut out = aiService.structured("weak-extract", input, WeakExtractOut.class);
            if (out == null || out.skills() == null) return;
            upsertSkills(interview.getUserId(), out.skills());
            log.info("[weak] interviewId={} 聚合薄弱技能 {} 条", interview.getId(), out.skills().size());
        } catch (Exception e) {
            log.warn("[weak] 薄弱技能聚合失败(不影响报告): {}", e.getMessage());
        }
    }

    private void upsertSkills(Long userId, List<WeakExtractOut.SkillItem> skills) {
        LocalDateTime now = LocalDateTime.now();
        for (WeakExtractOut.SkillItem item : skills) {
            if (item.skillTag() == null || item.skillTag().isBlank()) continue;
            String tag = item.skillTag().trim();
            if (tag.length() > 64) tag = tag.substring(0, 64);
            String category = item.category() == null || item.category().isBlank() ? "general" : item.category().trim();
            if (category.length() > 32) category = category.substring(0, 32);
            int newSeverity = Math.max(5, Math.min(95, item.severity()));
            List<String> evidence = cap(item.evidence(), 4, 120);

            UserWeakSkill row = weakRepository.findByUserIdAndSkillTag(userId, tag).orElse(null);
            if (row == null) {
                row = new UserWeakSkill();
                row.setUserId(userId);
                row.setSkillTag(tag);
                row.setCategory(category);
                row.setSeverity(newSeverity);
                row.setInterviewCount(1);
                row.setFirstSeen(now);
                row.setLastSeen(now);
                row.setStatus("tracking");
                row.setEvidenceJson(write(evidence));
            } else {
                row.setSeverity(Math.max(5, Math.min(95, (row.getSeverity() * 6 + newSeverity * 4) / 10)));
                row.setInterviewCount(row.getInterviewCount() + 1);
                row.setLastSeen(now);
                // resolved 又被识别为薄弱 => 回到 tracking
                if ("resolved".equals(row.getStatus())) row.setStatus("tracking");
                List<String> merged = new ArrayList<>(cap(readLines(row.getEvidenceJson()), 3, 120));
                for (String e : evidence) if (!merged.contains(e)) merged.add(e);
                row.setEvidenceJson(write(cap(merged, 6, 120)));
                row.setCategory(category);
            }
            row.setUpdatedAt(now);
            weakRepository.save(row);
        }
    }

    /** 当前用户薄弱技能列表（severity 升序=最弱在前）。 */
    @Transactional(readOnly = true)
    public List<WeakSkillView> list(Long userId) {
        return weakRepository.findByUserIdOrderBySeverityAsc(userId).stream()
                .map(r -> new WeakSkillView(r.getId(), r.getSkillTag(), r.getCategory(), r.getSeverity(),
                        r.getLatestScore(), r.getInterviewCount(), r.getLastSeen(), r.getStatus(),
                        readLines(r.getEvidenceJson()))).toList();
    }

    /** 标记一项薄弱技能为已攻克（从训练关注中移除但保留记录）。 */
    @Transactional
    public void resolve(Long userId, Long id) {
        UserWeakSkill row = weakRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "薄弱技能不存在"));
        row.setStatus("resolved");
        row.setUpdatedAt(LocalDateTime.now());
        weakRepository.save(row);
    }

    /** 错题本：本人已完成的正式/练习面试中得分 <60 的题目快照（含作答与参考答案）。 */
    @Transactional(readOnly = true)
    public PageResult<WrongQuestionView> wrongQuestions(Long userId, int page, int size) {
        List<Interview> done = interviewRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                userId, "completed");
        List<WrongQuestionView> all = new ArrayList<>();
        for (Interview interview : done) {
            List<InterviewQuestion> questions =
                    questionRepository.findByInterviewIdOrderByOrderIndexAsc(interview.getId());
            for (InterviewQuestion q : questions) {
                if (q.getUserAnswer() == null || q.getUserAnswer().isBlank()) continue;
                if (q.getScore() == null || q.getScore().intValue() >= 60) continue;
                all.add(new WrongQuestionView(interview.getId(), interview.getTitle(), interview.getMode(),
                        q.getOrderIndex(), q.getQuestionBankId(), categoryOf(q.getQuestionBankId()),
                        q.getContent(), q.getUserAnswer(), q.getScore().intValue(),
                        q.getReferenceAnswer(), q.getHint()));
            }
        }
        int from = Math.max(0, Math.min(page, all.size() / Math.max(1, size)) * size);
        int to = Math.min(all.size(), from + Math.max(1, size));
        List<WrongQuestionView> items = all.isEmpty() ? List.of()
                : new ArrayList<>(all.subList(from, to));
        return new PageResult<>(items, all.size());
    }

    private String categoryOf(Long bankId) {
        if (bankId == null) return null;
        return bankRepository.findById(bankId).map(QuestionBank::getCategory).orElse(null);
    }

    private List<String> cap(List<String> items, int max, int len) {
        if (items == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : items) {
            if (s == null || s.isBlank()) continue;
            if (out.size() >= max) break;
            out.add(truncate(s.trim(), len));
        }
        return out;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private List<String> readLines(String json) {
        List<String> lines = read(json, new TypeReference<List<String>>() {});
        return lines == null ? List.of() : lines;
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "JSON 序列化失败"); }
    }

    private <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, type); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "JSON 解析失败"); }
    }
}
