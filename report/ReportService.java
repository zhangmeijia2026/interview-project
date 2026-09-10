package com.group5.interview.module.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.ai.AIService;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.entity.Interview;
import com.group5.interview.entity.InterviewQuestion;
import com.group5.interview.entity.InterviewReport;
import com.group5.interview.module.report.dto.ReportAnalysisInput;
import com.group5.interview.module.report.dto.ReportDeepOut;
import com.group5.interview.module.report.dto.ReportResponse;
import com.group5.interview.module.interview.InterviewStatus;
import com.group5.interview.module.weak.WeakSkillService;
import com.group5.interview.repository.InterviewQuestionRepository;
import com.group5.interview.repository.InterviewReportRepository;
import com.group5.interview.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * F06 报告：只做聚合，不脑补模型能力（docs/07 §5）。
 *
 * <p>评分口径：评级 = 0.4×match.overall + 0.6×各题均分；
 * 六维雷达按 docs/07 §5 映射，其中缺少模型逐题子维度输出的部分，MVP 采用确定性代理指标
 * （作答篇幅、结构化程度、追问作答情况），全部来自落库数据，可稳定复算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    private static final List<String> DIM_ORDER = List.of(
            "job_match", "professional", "expression", "logic", "adaptability", "learning");
    private static final Map<String, String> DIM_CN = Map.of(
            "job_match", "岗位匹配", "professional", "专业技能", "expression", "表达流畅",
            "logic", "逻辑条理", "adaptability", "应变追问", "learning", "学习改进");
    /** 结构化表达信号词。 */
    private static final List<String> CONNECTORS = List.of(
            "首先", "其次", "再次", "最后", "第一", "第二", "第三",
            "一方面", "另一方面", "因为", "所以", "因此", "综上", "同时", "另外", "此外", "总体");

    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewReportRepository reportRepository;
    private final AIService aiService;
    private final WeakSkillService weakSkillService;
    private final ObjectMapper objectMapper;

    /** 生成报告（完成收尾路径）：formal 出评级 + 深度分析；practice 出"复盘(无评级)"。两者都会聚合薄弱技能。 */
    @Transactional
    public Long generate(Long interviewId, Long userId) {
        return doGenerate(interviewId, userId, true);
    }

    /** 供 report get() 补生成：不重复累计 interview_count/不重复计费（聚合幂等由调用方控制）。 */
    private Long doGenerate(Long interviewId, Long userId, boolean aggregateWeak) {
        Interview interview = require(interviewId, userId);
        if (InterviewStatus.from(interview.getStatus()) != InterviewStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONFLICT, "面试尚未完成，无法生成报告");
        }
        boolean practice = "practice".equals(interview.getMode());
        List<InterviewQuestion> questions = questionRepository.findByInterviewIdOrderByOrderIndexAsc(interviewId);

        List<InterviewQuestion> answered = questions.stream()
                .filter(q -> q.getUserAnswer() != null && !q.getUserAnswer().isBlank())
                .toList();
        BigDecimal matchOverall = interview.getMatchScore();
        BigDecimal avgScore = answered.isEmpty() ? matchOverall
                : BigDecimal.valueOf(answered.stream().mapToInt(q -> q.getScore() == null ? 0 : q.getScore().intValue()).average().orElse(0));

        int overall = (matchOverall == null ? 0 : matchOverall.intValue());
        int avg = avgScore == null ? 0 : avgScore.setScale(0, RoundingMode.HALF_UP).intValue();
        // 练习模式无简历/JD：job_match 维度以作答均分代偿，避免出现 15 分地板伪低
        int dimMatch = practice ? avg : overall;
        double composite = practice ? avg : 0.4 * overall + 0.6 * avg;

        LinkedHashMap<String, Integer> dims = computeDimensions(answered, dimMatch, avg);
        String rating = practice ? null : rating(composite);
        List<String> strengths = topLines(dims, true);
        List<String> improvements = topLines(dims, false);
        String recommendation = practice ? practiceRecommendation(interview, dims, avg)
                : recommend(rating, dims, composite);

        InterviewReport report = reportRepository.findByInterviewId(interviewId).orElseGet(InterviewReport::new);
        report.setInterviewId(interviewId);
        report.setOverallRating(rating);
        report.setDimensions(write(dims));
        report.setReviewSummary(practice ? practiceSummary(interview, dims)
                : summary(interview, rating, dims));
        report.setStrengths(write(strengths));
        report.setImprovements(write(improvements));
        report.setRecommendation(recommendation);
        LocalDateTime now = LocalDateTime.now();
        if (report.getCreatedAt() == null) report.setCreatedAt(now);
        report.setUpdatedAt(now);
        reportRepository.save(report);

        if (!practice && (interview.getOverallRating() == null || !rating.equals(interview.getOverallRating()))) {
            interview.setOverallRating(rating);
            interview.setUpdatedAt(now);
            interviewRepository.save(interview);
        }
        log.info("[report] interviewId={} mode={} rating={} composite={}", interviewId,
                practice ? "practice" : "formal", rating, composite);

        if (!practice) {
            enrichDeep(interview, questions, answered, composite, rating, dims,
                    strengths, improvements, recommendation);
        }
        if (aggregateWeak) {
            weakSkillService.aggregate(interview, questions, dims, matchGaps(dims), rating);
        }
        return report.getId();
    }

    /** 练习复盘说明（无评级）。 */
    private String practiceSummary(Interview interview, LinkedHashMap<String, Integer> dims) {
        return "练习复盘：针对「" + interview.getTargetPosition() + "」共完成 " + interview.getCompletedQuestionCount()
                + " 题（无正式评级），作答均分 "
                + dims.getOrDefault("professional", 0) + "。最需加强维度："
                + DIM_CN.get(dims.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElse("professional"));
    }

    /** 练习模式建议文案（鼓励 + 下一步可执行）。 */
    private String practiceRecommendation(Interview interview, LinkedHashMap<String, Integer> dims, int avg) {
        List<String> advice = new ArrayList<>();
        for (Map.Entry<String, Integer> e : dims.entrySet()) {
            if (e.getValue() < 70 && advice.size() < 3) {
                advice.add(DIM_CN.getOrDefault(e.getKey(), e.getKey()) + "(" + e.getValue() + " 分)建议补强");
            }
        }
        String head = "练习复盘（无评级），作答均分 " + avg + " 分。";
        return advice.isEmpty()
                ? head + "表现稳定，可回到题库换更高难度分类继续练习。"
                : head + "下一步：针对 " + String.join("、", advice)
                + "，回到题库对应分类（或在新建练习时勾选“针对薄弱点加强提问”）反复练到熟练。";
    }

    /**
     * 深度报告分析 + 详细简历修改建议（docs/07 §5，report-analysis）。
     * 真实模式由 DeepSeek 生成、离线/失败时由本地规则兜底，两者输出同构；
     * 仅在 analysis_json 为空时执行一次，避免重复计费。
     */
    private void enrichDeep(Interview interview, List<InterviewQuestion> questions, List<InterviewQuestion> answered,
                            double composite, String rating, LinkedHashMap<String, Integer> dims,
                            List<String> strengths, List<String> improvements, String recommendation) {
        try {
            InterviewReport report = reportRepository.findByInterviewId(interview.getId()).orElse(null);
            if (report == null || report.getAnalysisJson() != null && !report.getAnalysisJson().isBlank()) return;
            int overall = interview.getMatchScore() == null ? 0 : interview.getMatchScore().intValue();
            int avg = answered.isEmpty() ? overall : (int) Math.round(answered.stream()
                    .mapToInt(q -> q.getScore() == null ? 0 : q.getScore().intValue()).average().orElse(0));
            List<ReportAnalysisInput.QuestionBrief> briefs = answered.stream().map(q ->
                    new ReportAnalysisInput.QuestionBrief(q.getOrderIndex(), q.getContent(),
                            q.getScore() == null ? 0 : q.getScore().intValue(),
                            truncate(q.getUserAnswer(), 300),
                            readLines(q.getFeedbackStrong()), readLines(q.getFeedbackWeak()),
                            q.getFeedbackSuggestion(), q.getResumeAdvice(),
                            q.getFollowUpScore() == null ? null : q.getFollowUpScore().intValue())).toList();
            ReportAnalysisInput input = new ReportAnalysisInput(
                    interview.getId(), interview.getTitle(), interview.getTargetPosition(),
                    questions.size(), answered.size(), rating, (int) Math.round(composite), dims,
                    matchSummary(interview, overall), matchGaps(dims), strengths, improvements,
                    recommendation, briefs);
            ReportDeepOut out = aiService.structured("report-analysis", input, ReportDeepOut.class);
            if (out == null) return;
            report.setAnalysisJson(write(new ReportDeepOut(out.overallAnalysis(), out.dimensionAnalyses(),
                    out.conclusion(), null)));
            report.setResumeAdviceJson(write(out.resumeAdvice()));
            report.setUpdatedAt(LocalDateTime.now());
            reportRepository.save(report);
            log.info("[report] 深度分析完成 interviewId={}", interview.getId());
        } catch (Exception e) {
            log.warn("[report] 深度分析生成失败（保留确定性字段兜底）: {}", e.getMessage());
        }
    }

    /** 匹配摘要（供深度分析）：只讲落库可核实的匹配分与岗位信息。 */
    private String matchSummary(Interview interview, int overall) {
        return "简历-JD 匹配分 " + overall + "/100（由匹配阶段得出）。目标岗位「"
                + interview.getTargetPosition() + "」；简历技能/经历对岗位关键词的覆盖程度即该分依据。";
    }

    /** 匹配差距要点：无独立 gap 表，用岗位匹配维度低分推断，避免编造。 */
    private List<String> matchGaps(Map<String, Integer> dims) {
        List<String> gaps = new ArrayList<>();
        if (dims.getOrDefault("job_match", 0) < 75) {
            gaps.add("简历技能与项目经历对目标 JD 要求覆盖不全（岗位匹配维度偏低）");
        }
        return gaps;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…（已截断）";
    }

    /** 读取报告详情：已生成则直接返回；completed 但缺报告则补生成；其余报错。 */
    @Transactional
    public ReportResponse get(Long interviewId, Long userId) {
        Interview interview = require(interviewId, userId);
        InterviewReport report = reportRepository.findByInterviewId(interviewId).orElse(null);
        if (report == null) {
            if (InterviewStatus.from(interview.getStatus()) == InterviewStatus.COMPLETED) {
                doGenerate(interviewId, userId, false);
                report = reportRepository.findByInterviewId(interviewId).orElse(null);
            }
            if (report == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "该面试暂无报告");
            }
        }
        // 旧报告/新列为空（历史数据）时即时补齐深度分析（analysis 为空才执行）
        if (report.getAnalysisJson() == null || report.getAnalysisJson().isBlank()) {
            if (InterviewStatus.from(interview.getStatus()) == InterviewStatus.COMPLETED) {
                doGenerate(interviewId, userId, false);
                report = reportRepository.findByInterviewId(interviewId).orElse(report);
            }
        }
        List<InterviewQuestion> questions = questionRepository.findByInterviewIdOrderByOrderIndexAsc(interviewId);
        List<ReportResponse.QuestionReplay> replay = questions.stream().map(q -> new ReportResponse.QuestionReplay(
                q.getOrderIndex(), q.getContent(), q.getUserAnswer(),
                q.getScore() == null ? null : q.getScore().intValue(),
                readLines(q.getFeedbackStrong()),
                readLines(q.getFeedbackWeak()),
                q.getFeedbackSuggestion(),
                q.getResumeAdvice(),
                q.getReferenceAnswer(),
                q.getHint(),
                q.getFollowUpQuestion() == null ? null
                        : new ReportResponse.FollowUpView(q.getFollowUpQuestion(), q.getFollowUpAnswer(),
                        q.getFollowUpScore() == null ? null : q.getFollowUpScore().intValue()))).toList();
        LinkedHashMap<String, Integer> dims = read(report.getDimensions(),
                new TypeReference<LinkedHashMap<String, Integer>>() {});
        if (dims == null) dims = new LinkedHashMap<>();
        for (String key : DIM_ORDER) dims.putIfAbsent(key, 0);
        ReportDeepOut analysis = read(report.getAnalysisJson(), new TypeReference<ReportDeepOut>() {});
        ReportDeepOut.ResumeAdvice resumeAdvice =
                read(report.getResumeAdviceJson(), new TypeReference<ReportDeepOut.ResumeAdvice>() {});
        return new ReportResponse(report.getOverallRating(), interview.getMode(), interview.getMatchScore(),
                dims, replay, readLines(report.getStrengths()),
                readLines(report.getImprovements()), report.getRecommendation(), analysis, resumeAdvice);
    }

    // ---------------------------------------------------------------- internal

    private LinkedHashMap<String, Integer> computeDimensions(List<InterviewQuestion> answered, int matchOverall, int avg) {
        LinkedHashMap<String, Integer> dims = new LinkedHashMap<>();
        if (answered.isEmpty()) {
            for (String key : DIM_ORDER) dims.put(key, clamp(matchOverall));
            return dims;
        }
        // 表达：作答篇幅与结构化程度代理（全部来自落库数据，可稳定复算）
        int shortCount = 0, structuredCount = 0;
        double lengthSum = 0;
        int followUpTotal = 0, followUpEffective = 0, fuScored = 0, fuScoreSum = 0;
        for (InterviewQuestion q : answered) {
            String text = q.getUserAnswer() == null ? "" : q.getUserAnswer();
            int len = text.trim().length();
            lengthSum += len;
            if (len < 60) shortCount++;
            if (containsAny(text, CONNECTORS)) structuredCount++;
            if (q.getFollowUpQuestion() != null) {
                followUpTotal++;
                if (q.getFollowUpAnswer() != null && !q.getFollowUpAnswer().isBlank()
                        && !"（用户跳过追问）".equals(q.getFollowUpAnswer())) {
                    followUpEffective++;
                    if (q.getFollowUpScore() != null) {
                        fuScored++;
                        fuScoreSum += q.getFollowUpScore().intValue();
                    }
                }
            }
        }
        double shortRatio = (double) shortCount / answered.size();
        double structRatio = (double) structuredCount / answered.size();
        double avgLen = lengthSum / answered.size();

        int expressionPenalty = shortRatio <= 0.2 ? 4 : shortRatio <= 0.5 ? 0 : shortRatio <= 0.8 ? -8 : -16;
        if (avgLen >= 200) expressionPenalty += 3;

        dims.put("job_match", clamp(matchOverall));
        dims.put("professional", clamp(avg));
        dims.put("expression", clamp(avg + expressionPenalty));
        dims.put("logic", clamp(avg + (structRatio >= 0.6 ? 8 : structRatio >= 0.3 ? 3 : -8)));
        // 应变：有追问则按有效追问作答占比；无追问取中性（给均分 0.6 权重，避免虚高/过低）
        dims.put("adaptability", followUpTotal == 0 ? clamp((int) (avg * 0.6 + 45))
                : clamp((int) Math.round(followUpEffective * 100.0 / followUpTotal)));
        // 学习：有追问得分则取均分，否则中性
        dims.put("learning", fuScored == 0 ? clamp((int) (avg * 0.5 + 50))
                : clamp((int) Math.round((double) fuScoreSum / fuScored)));
        return dims;
    }

    private String rating(double composite) {
        if (composite >= 85) return "S";
        if (composite >= 75) return "A";
        if (composite >= 65) return "B";
        if (composite >= 55) return "C";
        return "D";
    }

    /** 取最高/最低两维，生成"强项 / 待改进"要点。 */
    private List<String> topLines(LinkedHashMap<String, Integer> dims, boolean top) {
        List<Map.Entry<String, Integer>> sorted = dims.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue())
                        .reversed().thenComparing(Map.Entry::getKey))
                .toList();
        List<String> lines = new ArrayList<>();
        List<Map.Entry<String, Integer>> picked = top ? sorted.subList(0, Math.min(2, sorted.size()))
                : sorted.subList(Math.max(0, sorted.size() - 2), sorted.size());
        for (Map.Entry<String, Integer> e : picked) {
            int v = e.getValue();
            String grade = v >= 75 ? "掌握扎实" : v >= 55 ? "基本达标" : "仍有空间";
            lines.add(DIM_CN.getOrDefault(e.getKey(), e.getKey())
                    + (top ? "是你的相对强项（" : "建议重点加强（") + v + " 分，" + grade + "）");
        }
        return lines;
    }

    private String recommend(String rating, LinkedHashMap<String, Integer> dims, double composite) {
        String label = Map.of("S", "卓越", "A", "优秀", "B", "良好", "C", "待加强", "D", "薄弱").getOrDefault(rating, "待加强");
        List<String> advice = new ArrayList<>();
        if (dims.get("job_match") < 70) advice.add("结合 JD 要求补齐岗位关键技能与项目亮点，提升匹配度");
        if (dims.get("professional") < 70) advice.add("巩固目标岗位所需核心技术，作答时多结合项目细节");
        if (dims.get("expression") < 70) advice.add("控制语速、按要点分条作答，避免回答过短或堆砌术语");
        if (dims.get("logic") < 70) advice.add("采用“首先/其次/最后”的结构化表达，先说结论再展开");
        if (dims.get("adaptability") < 70) advice.add("主动应对追问，把“不会”转化为“我会如何补上”");
        String head = "综合评级 " + rating + "（" + label + "，综合分 " + (int) composite + "）。";
        return advice.isEmpty() ? head + "保持当前准备节奏，针对高频考点持续演练即可。"
                : head + "下一步建议：" + String.join("；", advice) + "。";
    }

    private String summary(Interview interview, String rating, LinkedHashMap<String, Integer> dims) {
        return "针对「" + interview.getTargetPosition() + "」共考察 " + interview.getCompletedQuestionCount()
                + " 题，综合评级 " + rating + "，最强维度："
                + DIM_CN.get(dims.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("job_match"));
    }

    private Interview require(Long interviewId, Long userId) {
        return interviewRepository.findByIdAndUserId(interviewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "面试不存在或无权访问"));
    }

    private boolean containsAny(String text, List<String> keys) {
        for (String k : keys) if (text.contains(k)) return true;
        return false;
    }

    private int clamp(int v) { return Math.max(15, Math.min(99, v)); }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "JSON 序列化失败"); }
    }

    private <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, type); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INTERNAL, "JSON 解析失败"); }
    }

    private List<String> readLines(String json) {
        List<String> lines = read(json, new TypeReference<List<String>>() {});
        return lines == null ? List.of() : lines;
    }
}
