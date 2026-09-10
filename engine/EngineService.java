package com.group5.interview.module.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.ai.AIService;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.Interview;
import com.group5.interview.entity.InterviewQuestion;
import com.group5.interview.entity.QuestionBank;
import com.group5.interview.entity.UserWeakSkill;
import com.group5.interview.module.engine.dto.*;
import com.group5.interview.module.interview.InterviewService;
import com.group5.interview.module.interview.InterviewStatus;
import com.group5.interview.module.match.dto.FocusItem;
import com.group5.interview.module.report.ReportService;
import com.group5.interview.module.resume.dto.ResumeParsed;
import com.group5.interview.repository.InterviewQuestionRepository;
import com.group5.interview.repository.InterviewRepository;
import com.group5.interview.repository.MatchResultRepository;
import com.group5.interview.repository.QuestionBankRepository;
import com.group5.interview.repository.ResumeRepository;
import com.group5.interview.repository.UserWeakSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * F05 面试引擎：状态机推进 + 逐题出题 + 作答评分 + 追问 + 断点续传。
 *
 * <p>双模式（R7）：
 * <ul>
 *   <li>formal 正式模式：需 简历→JD→匹配 后开始，动态 AI 出题，每题计时上报，报告出评级；</li>
 *   <li>practice 练习模式：draft 直启，从题库抽题（可选分类 / 薄弱点偏置），可随时看提示与参考答案，
 *       报告为"复盘（无评级）"。</li>
 * </ul>
 * R8 参考回答：form 动态出题一次 AI 调用同时产出 content+basis+referenceAnswer+hint；
 * practice 直接引用题库答案。可见性由 /reference 端点统一裁决。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EngineService {
    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository questionRepository;
    private final MatchResultRepository matchResultRepository;
    private final ResumeRepository resumeRepository;
    private final QuestionBankRepository bankRepository;
    private final UserWeakSkillRepository weakRepository;
    private final InterviewService interviewService;
    private final AIService aiService;
    private final SseEmitterRegistry sseRegistry;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    /** 开始/恢复面试：置 in_progress；practice 直启并从题库物化全部题目。 */
    @Transactional
    public SessionResponse start(Long interviewId, Long userId) {
        Interview interview = interviewService.required(interviewId, userId);
        InterviewStatus status = InterviewStatus.from(interview.getStatus());
        if (status == InterviewStatus.COMPLETED || status == InterviewStatus.ABANDONED) {
            throw new BusinessException(ErrorCode.CONFLICT, "面试已结束，不能再次开始");
        }
        boolean practice = isPractice(interview);
        if (status != InterviewStatus.IN_PROGRESS) {
            if (practice) {
                if (status != InterviewStatus.DRAFT) {
                    throw new BusinessException(ErrorCode.CONFLICT, "练习面试由新建状态直接开始，无需简历/JD/匹配");
                }
            } else if (status != InterviewStatus.MATCHED && status != InterviewStatus.READY) {
                throw new BusinessException(ErrorCode.CONFLICT, "请先完成简历、JD 与匹配后再开始面试");
            }
            interview.setStatus(InterviewStatus.IN_PROGRESS.value());
            interview.setStartedAt(LocalDateTime.now());
            interview.setUpdatedAt(LocalDateTime.now());
            interviewRepository.save(interview);
            log.info("[engine] 开始面试 interviewId={} mode={}", interviewId, interview.getMode());
            if (practice) {
                materializePractice(interview);
            }
        }
        QuestionSummary current = currentQuestion(interview);
        if (current != null) push(interviewId, "question", current);
        return toSession(interview, current);
    }

    @Transactional(readOnly = true)
    public SessionResponse session(Long interviewId, Long userId) {
        Interview interview = interviewService.required(interviewId, userId);
        QuestionSummary current = (InterviewStatus.from(interview.getStatus()) == InterviewStatus.IN_PROGRESS)
                ? currentQuestion(interview) : null;
        return toSession(interview, current);
    }

    /** 单题提示/参考回答（R8），可见性规则见 {@link ReferenceView}。 */
    @Transactional(readOnly = true)
    public ReferenceView reference(Long interviewId, Long userId, int orderIndex) {
        Interview interview = interviewService.required(interviewId, userId);
        InterviewQuestion q = requireQuestion(interviewId, userId, orderIndex);
        boolean practice = isPractice(interview);
        boolean answered = q.getUserAnswer() != null && !q.getUserAnswer().isBlank();
        boolean visible = practice || answered;
        return new ReferenceView(interview.getMode(), answered,
                visible ? q.getHint() : null, visible ? q.getReferenceAnswer() : null);
    }

    /** 提交当前题目作答：落库 → 评分 → 进度+1 → （追问 / 下一题 / 收尾报告）。 */
    @Transactional
    public AnswerResponse answer(Long interviewId, Long userId, int orderIndex, String answerText, Integer elapsedSeconds) {
        Interview interview = interviewService.required(interviewId, userId);
        if (InterviewStatus.from(interview.getStatus()) != InterviewStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.CONFLICT, "面试尚未开始或已结束");
        }
        String mode = interview.getMode();
        int currentOrder = interview.getCompletedQuestionCount() + 1;
        if (orderIndex != currentOrder) {
            throw new BusinessException(ErrorCode.CONFLICT, "请按顺序作答当前题目（当前应为第 " + currentOrder + " 题）");
        }
        InterviewQuestion question = questionRepository
                .findByInterviewIdAndOrderIndex(interviewId, orderIndex)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        if (question.getUserAnswer() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "本题已作答，请勿重复提交");
        }
        question.setUserAnswer(answerText);
        if ("formal".equals(mode) && elapsedSeconds != null) {
            question.setAnsweredSeconds(Math.max(0, Math.min(3600, elapsedSeconds)));
        }
        questionRepository.save(question);

        GradeOut grade = aiService.structured("grade", new GradeInput(question.getContent(),
                examinePointOf(interview, orderIndex), answerText, orderIndex,
                interview.getTargetPosition(), mode, question.getReferenceAnswer()), GradeOut.class);

        question.setScore(BigDecimal.valueOf(grade.score()));
        question.setFeedbackStrong(write(grade.strong()));
        question.setFeedbackWeak(write(grade.weak()));
        question.setFeedbackSuggestion(grade.suggestion());
        question.setResumeAdvice(grade.resumeAdvice());
        questionRepository.save(question);

        boolean finished = interview.getCompletedQuestionCount() + 1 >= interview.getQuestionCount();
        interview.setCompletedQuestionCount(interview.getCompletedQuestionCount() + 1);
        interview.setUpdatedAt(LocalDateTime.now());
        interviewRepository.save(interview);
        push(interviewId, "grade", gradePayload(orderIndex, grade, question));

        FollowUpSummary followUp = null;
        if (grade.suggestFollowup()) {
            String weak = grade.weak().isEmpty() ? "作答深度不足" : grade.weak().get(0);
            FollowUpOut out = aiService.structured("follow-up",
                    new FollowUpInput(question.getContent(), weak, answerText), FollowUpOut.class);
            question.setFollowUpQuestion(out.content());
            questionRepository.save(question);
            followUp = new FollowUpSummary(out.content());
            push(interviewId, "followup", followUp);
        }

        QuestionSummary next = null;
        if (!finished) {
            next = ensureQuestionRow(interview, interview.getCompletedQuestionCount() + 1);
            push(interviewId, "question", next);
        } else {
            complete(interview);
            Long reportId = reportService.generate(interview.getId(), interview.getUserId());
            push(interviewId, "report_done", Map.of("reportId", reportId == null ? -1L : reportId));
        }
        push(interviewId, "done", Map.of("kind", finished ? "report" : "question"));
        log.info("[engine] 作答 interviewId={} order={} score={} finished={} mode={}", interviewId,
                orderIndex, grade.score(), finished, mode);
        return new AnswerResponse(orderIndex, grade.score(), grade.strong(), grade.weak(),
                grade.suggestion(), grade.suggestFollowup(), followUp, next,
                interview.getStatus(), finished, grade.resumeAdvice(),
                mode, question.getReferenceAnswer(), question.getHint());
    }

    @Transactional
    public FollowUpResult answerFollowUp(Long interviewId, Long userId, int orderIndex, String answerText) {
        InterviewQuestion question = requireQuestion(interviewId, userId, orderIndex);
        if (question.getFollowUpQuestion() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "本题没有待回答的追问");
        }
        question.setFollowUpAnswer(answerText);
        int length = answerText == null ? 0 : answerText.trim().length();
        int score = length < 10 ? 20 : Math.min(98, 30 + length / 3);
        question.setFollowUpScore(BigDecimal.valueOf(score));
        questionRepository.save(question);
        push(interviewId, "done", Map.of("kind", "followup", "orderIndex", orderIndex));
        return new FollowUpResult(score, length < 10 ? "追问回答过短，请补充具体思路与案例。" : "追问已记录，表现不错。");
    }

    @Transactional
    public void skipFollowUp(Long interviewId, Long userId, int orderIndex) {
        InterviewQuestion question = requireQuestion(interviewId, userId, orderIndex);
        if (question.getFollowUpAnswer() == null) {
            question.setFollowUpAnswer("（用户跳过追问）");
            questionRepository.save(question);
        }
    }

    // ---------------------------------------------------------------- internal

    private boolean isPractice(Interview interview) {
        return "practice".equals(interview.getMode());
    }

    /** 练习模式：从题库抽题（分类/薄弱点偏置），一次性物化全部题目行（content/ref/hint/来源）。 */
    private void materializePractice(Interview interview) {
        List<QuestionBank> picked = pickPracticeQuestions(interview);
        if (picked.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "题库暂无可用题目，请联系管理员扩充题库后再练习");
        }
        int idx = 1;
        for (QuestionBank bank : picked) {
            InterviewQuestion row = new InterviewQuestion();
            row.setInterviewId(interview.getId());
            row.setOrderIndex(idx);
            row.setFocusIndex(0); // 0=题库来源
            row.setQuestionBankId(bank.getId());
            row.setContent(bank.getContent());
            row.setReferenceAnswer(bank.getAnswer());
            row.setHint(bank.getHint());
            row.setCreatedAt(LocalDateTime.now());
            questionRepository.save(row);
            bank.setUsageCount(bank.getUsageCount() + 1);
            bankRepository.save(bank);
            idx++;
        }
        if (picked.size() != interview.getQuestionCount()) {
            interview.setQuestionCount(picked.size());
            interviewRepository.save(interview);
        }
        log.info("[engine] 练习出题完成 interviewId={} mode=practice 共 {} 题", interview.getId(), picked.size());
    }

    /** 题库抽题：分类过滤 + 薄弱点偏置 + 以面试 id 为种子的稳定随机；数量不足时用其余分类补齐。 */
    private List<QuestionBank> pickPracticeQuestions(Interview interview) {
        int want = Math.max(1, interview.getQuestionCount());
        String category = interview.getPracticeCategory();
        List<String> weakTags = interview.isWeakBoost() ? weakSkillTags(interview.getUserId()) : List.of();
        boolean useCategory = category != null && !category.isBlank();

        List<QuestionBank> candidates = useCategory
                ? bankRepository.findByEnabledTrueAndCategory(category)
                : bankRepository.findByEnabledTrue();

        List<QuestionBank> chosen = new ArrayList<>();
        Random random = new Random(interview.getId() == null ? System.nanoTime() : interview.getId());
        List<QuestionBank> rest = new ArrayList<>(candidates);

        // 薄弱点偏置：先取命中薄弱标签的题目（分类限制内优先）
        if (!weakTags.isEmpty()) {
            List<QuestionBank> matched = new ArrayList<>();
            for (QuestionBank q : rest) {
                if (matchesAnyWeak(q, weakTags)) matched.add(q);
            }
            matched.sort((a, b) -> Integer.compare(a.getDifficulty(), b.getDifficulty()));
            for (QuestionBank q : matched) {
                if (chosen.size() >= want) break;
                chosen.add(q);
                rest.remove(q);
            }
        }
        // 稳定随机补充
        java.util.Collections.shuffle(rest, random);
        for (QuestionBank q : rest) {
            if (chosen.size() >= want) break;
            chosen.add(q);
        }
        // 分类题目不足：放宽到全题库（其它分类）补齐，保证练习可跑满目标题数
        if (chosen.size() < want && useCategory) {
            List<QuestionBank> other = bankRepository.findByEnabledTrue();
            java.util.Collections.shuffle(other, random);
            for (QuestionBank q : other) {
                if (chosen.size() >= want) break;
                if (chosen.contains(q)) continue;
                chosen.add(q);
            }
        }
        return chosen;
    }

    private List<String> weakSkillTags(Long userId) {
        return weakRepository.findByUserIdOrderBySeverityAsc(userId).stream()
                .limit(8).map(UserWeakSkill::getSkillTag).toList();
    }

    private boolean matchesAnyWeak(QuestionBank q, List<String> tags) {
        String hay = norm(q.getContent() + " " + orEmpty(q.getAnswer()) + " "
                + orEmpty(q.getKnowledgePoints()) + " " + q.getCategory());
        for (String tag : tags) {
            if (!tag.isBlank() && hay.contains(norm(tag))) return true;
        }
        return false;
    }

    private String norm(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。、；：·\\-]", "");
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private void complete(Interview interview) {
        interview.setStatus(InterviewStatus.COMPLETED.value());
        interview.setCompletedAt(LocalDateTime.now());
        interview.setUpdatedAt(LocalDateTime.now());
        interviewRepository.save(interview);
    }

    /** 取"当前该答"的那道题：若还没生成则先生成（断点续传）。 */
    private QuestionSummary currentQuestion(Interview interview) {
        int order = interview.getCompletedQuestionCount() + 1;
        if (order > interview.getQuestionCount()) return null;
        return ensureQuestionRow(interview, order);
    }

    private QuestionSummary ensureQuestionRow(Interview interview, int order) {
        return questionRepository.findByInterviewIdAndOrderIndex(interview.getId(), order)
                .map(q -> new QuestionSummary(q.getOrderIndex(), q.getFocusIndex(), q.getContent()))
                .orElseGet(() -> {
                    FocusItem focus = focusFor(interview, order);
                    ResumeParsed resume = resumeParsed(interview);
                    List<String> weakSkills = interview.isWeakBoost() ? weakSkillTags(interview.getUserId())
                            : List.of();
                    QuestionOut out = aiService.structured("question", new QuestionInput(
                            focus, resume, order, interview.getTargetPosition(), weakSkills), QuestionOut.class);
                    InterviewQuestion q = new InterviewQuestion();
                    q.setInterviewId(interview.getId());
                    q.setOrderIndex(order);
                    q.setFocusIndex(focus.index());
                    q.setContent(out.content());
                    q.setReferenceAnswer(out.referenceAnswer());
                    q.setHint(out.hint());
                    q.setCreatedAt(LocalDateTime.now());
                    questionRepository.save(q);
                    log.info("[engine] 出题 interviewId={} order={}", interview.getId(), order);
                    return new QuestionSummary(q.getOrderIndex(), q.getFocusIndex(), q.getContent());
                });
    }

    private FocusItem focusFor(Interview interview, int order) {
        List<FocusItem> focus = matchFocus(interview);
        if (!focus.isEmpty() && order <= focus.size()) return focus.get(order - 1);
        return new FocusItem(order, "围绕目标岗位的核心能力考察",
                "考察对 " + interview.getTargetPosition() + " 所需核心能力的掌握程度", "结合项目经历具体阐述");
    }

    private List<FocusItem> matchFocus(Interview interview) {
        return matchResultRepository.findByInterviewId(interview.getId())
                .map(row -> {
                    try { return objectMapper.readValue(row.getInterviewFocus(), new TypeReference<List<FocusItem>>() {}); }
                    catch (Exception e) { return List.<FocusItem>of(); }
                }).orElse(List.of());
    }

    private String examinePointOf(Interview interview, int order) {
        List<FocusItem> focus = matchFocus(interview);
        if (!focus.isEmpty() && order <= focus.size()) return focus.get(order - 1).examinePoint();
        return isPractice(interview) ? "本题考察点（练习，可对照参考答案复盘）" : "本题考察点";
    }

    private ResumeParsed resumeParsed(Interview interview) {
        if (interview.getResumeId() == null) return null;
        return resumeRepository.findByIdAndUserId(interview.getResumeId(), interview.getUserId())
                .map(r -> {
                    try { return objectMapper.readValue(r.getParsedData(), ResumeParsed.class); }
                    catch (Exception e) { return null; }
                }).orElse(null);
    }

    private InterviewQuestion requireQuestion(Long interviewId, Long userId, int orderIndex) {
        interviewService.required(interviewId, userId);
        return questionRepository.findByInterviewIdAndOrderIndex(interviewId, orderIndex)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
    }

    private SessionResponse toSession(Interview interview, QuestionSummary current) {
        return new SessionResponse(interview.getStatus(), interview.getQuestionCount(),
                interview.getCompletedQuestionCount(), current);
    }

    private Map<String, Object> gradePayload(int orderIndex, GradeOut grade, InterviewQuestion question) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderIndex", orderIndex);
        payload.put("score", grade.score());
        payload.put("strong", grade.strong());
        payload.put("weak", grade.weak());
        payload.put("suggestion", grade.suggestion());
        payload.put("resumeAdvice", grade.resumeAdvice());
        payload.put("suggestFollowup", grade.suggestFollowup());
        payload.put("referenceAnswer", question.getReferenceAnswer());
        payload.put("hint", question.getHint());
        return payload;
    }

    private void push(Long interviewId, String type, Object payload) {
        sseRegistry.push(interviewId, SseEvent.of(type, write(payload)));
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "JSON 序列化失败"); }
    }
}
