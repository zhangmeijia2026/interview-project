package com.group5.interview.ai;

import com.group5.interview.module.compare.dto.CompareInput;
import com.group5.interview.module.compare.dto.CompareResumesOut;
import com.group5.interview.module.engine.dto.FollowUpInput;
import com.group5.interview.module.engine.dto.FollowUpOut;
import com.group5.interview.module.engine.dto.GradeInput;
import com.group5.interview.module.engine.dto.GradeOut;
import com.group5.interview.module.engine.dto.QuestionInput;
import com.group5.interview.module.engine.dto.QuestionOut;
import com.group5.interview.module.jd.dto.JdParsed;
import com.group5.interview.module.match.dto.FocusInput;
import com.group5.interview.module.match.dto.FocusItem;
import com.group5.interview.module.match.dto.FocusOut;
import com.group5.interview.module.match.dto.GapItem;
import com.group5.interview.module.match.dto.MatchInput;
import com.group5.interview.module.match.dto.MatchOut;
import com.group5.interview.module.questionbank.dto.BankGenInput;
import com.group5.interview.module.questionbank.dto.BankGenOut;
import com.group5.interview.module.report.dto.ReportAnalysisInput;
import com.group5.interview.module.report.dto.ReportDeepOut;
import com.group5.interview.module.resume.dto.ResumeParsed;
import com.group5.interview.module.weak.dto.WeakExtractInput;
import com.group5.interview.module.weak.dto.WeakExtractOut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无 API Key 时的全链路离线实现（MVP / 演示 / CI 用）。
 *
 * <p>与真实 DeepSeek 实现实现同一 {@link AIService} 契约、返回同一批 DTO：
 * resume-parse / jd-parse / match / focus / question / grade / follow-up。
 * 所有结论都**只从输入原文/结构化数据推导**，绝不补造不存在的事实，
 * 因此换真实模型时接口与存储结构无需改动。
 */
@ConditionalOnProperty(name = "ai.mock-enabled", havingValue = "true", matchIfMissing = true)
@Service
public class LocalMockAIService implements AIService {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(?:1[3-9]\\d{9}|\\+?86[- ]?1[3-9]\\d{9})(?!\\d)");
    private static final Pattern YEAR_RANGE =
            Pattern.compile("(20\\d{2})\\s*[-~—至]{1,3}\\s*(20\\d{2}|至今|现在)");

    /** 技能词池（越靠前越常被简历/JD 使用）；长词先匹配，避免 Spring/Spring Boot 重复。 */
    private static final List<String> SKILL_POOL = List.of(
            "Spring Boot", "Spring Cloud", "Spring MVC", "MyBatis-Plus", "MyBatis", "Kubernetes",
            "Elasticsearch", "RabbitMQ", "RocketMQ", "Redis", "MySQL", "MongoDB", "Docker",
            "JavaScript", "TypeScript", "Spring", "Java", "Python", "Vue", "React", "Linux",
            "Git", "Kafka", "Go", "SQL", "JVM", "Nginx", "Zookeeper", "Dubbo", "Netty", "K8s",
            "Maven", "Gradle", "分布式", "微服务", "消息队列", "高并发");

    private static final List<String> SOFT_SKILL_POOL = List.of(
            "沟通", "团队协作", "抗压", "学习能力", "责任心", "主动性", "解决问题", "英语阅读");

    private static final List<String> DEGREES = List.of("博士", "硕士", "本科", "大专");
    private static final List<String> UNIVERSITY_HINT = List.of("大学", "学院", "学校");
    private static final List<String> COMPANY_HINT =
            List.of("公司", "集团", "科技", "网络", "软件", "有限", "工作室");
    private static final List<String> ROLE_HINT = List.of("工程师", "架构师", "开发", "经理", "负责人", "实习生");
    private static final List<String> PROJECT_HINT =
            List.of("系统", "平台", "服务", "商城", "订单", "引擎", "后台", "中台", "App", "小程序");

    /** 六维中文名与顺序（与 docs/07 §5 / ReportService 口径一致）。 */
    private static final List<String> DIM_ORDER = List.of(
            "job_match", "professional", "expression", "logic", "adaptability", "learning");
    private static final Map<String, String> DIM_CN = Map.of(
            "job_match", "岗位匹配", "professional", "专业技能", "expression", "表达流畅",
            "logic", "逻辑条理", "adaptability", "应变追问", "learning", "学习改进");

    @Override
    @SuppressWarnings("unchecked")
    public <T> T structured(String promptKey, Object context, Class<T> clazz) {
        Object result = switch (promptKey) {
            case "resume-parse" -> parseResume(require(String.class, context, promptKey));
            case "jd-parse" -> parseJd(require(String.class, context, promptKey));
            case "match" -> runMatch(require(MatchInput.class, context, promptKey));
            case "focus" -> buildFocus(require(FocusInput.class, context, promptKey));
            case "question" -> askQuestion(require(QuestionInput.class, context, promptKey));
            case "grade" -> gradeAnswer(require(GradeInput.class, context, promptKey));
            case "follow-up" -> buildFollowUp(require(FollowUpInput.class, context, promptKey));
            case "report-analysis" -> buildReportAnalysis(require(ReportAnalysisInput.class, context, promptKey));
            case "weak-extract" -> extractWeak(require(WeakExtractInput.class, context, promptKey));
            case "question-bank-gen" -> genBank(require(BankGenInput.class, context, promptKey));
            case "compare-resumes" -> compareResumes(require(CompareInput.class, context, promptKey));
            default -> throw new IllegalArgumentException("当前离线 AI 服务不支持的 promptKey: " + promptKey);
        };
        return (T) result;
    }

    private <T> T require(Class<T> type, Object context, String promptKey) {
        if (!type.isInstance(context)) {
            throw new IllegalArgumentException("promptKey[" + promptKey + "] 上下文类型必须是 "
                    + type.getSimpleName() + "，实际: " + context.getClass().getSimpleName());
        }
        return type.cast(context);
    }

    // ---------------------------------------------------------------- R1 resume-parse

    private ResumeParsed parseResume(String text) {
        String name = firstLikelyName(text);
        Matcher email = EMAIL.matcher(text);
        Matcher phone = PHONE.matcher(text);
        List<ResumeParsed.Education> education = new ArrayList<>();
        List<ResumeParsed.Experience> experience = new ArrayList<>();
        List<ResumeParsed.Project> projects = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String degree = firstDegree(trimmed);
            boolean hasUni = hasAny(trimmed, UNIVERSITY_HINT);
            boolean hasYear = YEAR_RANGE.matcher(trimmed).find();
            if (degree != null && (hasUni || hasYear)) {
                education.add(parseEducation(trimmed));
            } else if (isExperienceLine(trimmed)) {
                experience.add(parseExperience(trimmed));
            } else if (trimmed.length() > 10 && trimmed.indexOf('：') > 0 && hasAny(trimmed, PROJECT_HINT)) {
                projects.add(parseProject(trimmed));
            }
        }
        List<ResumeParsed.Skill> skills = detectSkills(text);
        return new ResumeParsed(name, email.find() ? email.group() : null,
                phone.find() ? phone.group() : null, education, experience, projects, skills);
    }

    private ResumeParsed.Education parseEducation(String line) {
        List<String> parts = splitParts(line);
        String school = null, major = null, degree = null;
        String period = yearRange(line);
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.length() <= 2 && !part.matches("[\\p{IsHan}]{2,4}")) continue;
            if (degree == null && partDegree(part) != null) { degree = partDegree(part); continue; }
            if (school == null && hasAny(part, UNIVERSITY_HINT)) { school = part; continue; }
            if (major == null && part.length() >= 2) { major = part; }
        }
        return new ResumeParsed.Education(school, degree, major, period);
    }

    private boolean isExperienceLine(String line) {
        if (!hasAny(line, COMPANY_HINT) && !hasAny(line, ROLE_HINT)) return false;
        boolean hasTime = line.contains("至今") || YEAR_RANGE.matcher(line).find() || line.contains("现在");
        return hasTime && hasAny(line, ROLE_HINT);
    }

    private ResumeParsed.Experience parseExperience(String line) {
        List<String> parts = splitParts(line);
        String company = null, title = null, period = yearRange(line);
        if (line.contains("至今") || line.contains("现在")) {
            Matcher m = Pattern.compile("(\\d{4})[-/.年]\\d{0,2}").matcher(line);
            if (m.find()) period = m.group(1) + " - 至今";
            else period = period == null ? "至今" : period;
        }
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (hasAny(part, COMPANY_HINT) && company == null) { company = part; continue; }
            if (hasAny(part, ROLE_HINT) && title == null) { title = part; continue; }
        }
        List<String> tech = detectSkillTokens(line).stream().limit(6).toList();
        return new ResumeParsed.Experience(company, title, period, null, tech);
    }

    private ResumeParsed.Project parseProject(String line) {
        int idx = line.indexOf('：');
        String name = line.substring(0, idx).trim();
        String desc = line.substring(idx + 1).trim();
        List<String> tech = detectSkillTokens(line).stream().limit(6).toList();
        return new ResumeParsed.Project(name, yearRange(line), desc, null, tech);
    }

    // ---------------------------------------------------------------- R2 jd-parse

    private JdParsed parseJd(String text) {
        List<String> hard = detectSkillTokens(text);
        List<String> soft = new ArrayList<>();
        for (String token : SOFT_SKILL_POOL) {
            if (text.contains(token) && !soft.contains(token)) soft.add(token);
        }
        Integer years = null;
        Matcher ym = Pattern.compile("(\\d{1,2})\\s*年(?:以上|及以下)?").matcher(text);
        if (ym.find()) years = Integer.valueOf(ym.group(1));
        String eduReq = null;
        for (String d : DEGREES) {
            if (text.contains(d)) {
                if (text.contains(d + "及以上") || text.contains(d + "以上") || d.equals("硕士") || d.equals("博士")) {
                    eduReq = d + "及以上";
                } else {
                    eduReq = d;
                }
                break;
            }
        }
        List<String> responsibilities = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.length() < 8) continue;
            if (hasAny(t, List.of("负责", "职责", "参与", "开发", "设计", "维护", "优化", "搭建", "推动", "编写"))) {
                responsibilities.add(t.replaceAll("^[\\s\\d.、\\-·]*", ""));
            }
            if (responsibilities.size() >= 6) break;
        }
        return new JdParsed(hard, soft, years, eduReq, responsibilities);
    }

    // ---------------------------------------------------------------- R3 match

    private MatchOut runMatch(MatchInput in) {
        ResumeParsed resume = in.resume();
        JdParsed jd = in.jd();
        List<String> resumeSkills = resume.skills().stream().map(ResumeParsed.Skill::name).toList();

        List<GapItem> gap = new ArrayList<>();
        for (String need : jd.hardSkills()) {
            if (!coversAny(need, resumeSkills)) {
                gap.add(new GapItem("skill", need, "HIGH",
                        "JD 要求 " + need + "，简历技能清单中未体现该技术"));
            }
        }
        if (jd.minExperienceYears() != null && resumeYears(resume) < jd.minExperienceYears()) {
            gap.add(new GapItem("experience", "经验年限不足", "HIGH",
                    "JD 要求 " + jd.minExperienceYears() + " 年以上经验，简历可识别的相关经验不足"));
        }
        if (!educationSatisfied(jd.educationRequirement(), resume)) {
            gap.add(new GapItem("education", "学历要求", "HIGH",
                    "JD 要求 " + (jd.educationRequirement() == null ? "学历" : jd.educationRequirement())
                            + "，简历教育信息缺失或未达要求"));
        }
        int skillScore = scoreSkill(jd, resumeSkills);
        int expScore = scoreExperience(jd, resume);
        int eduScore = scoreEducation(jd, resume);
        int overall = (int) Math.round(skillScore * 0.5 + expScore * 0.25 + eduScore * 0.25);
        String summary = gap.isEmpty()
                ? "简历与该岗位匹配度高，技能、经验、学历基本覆盖 JD 要求。"
                : "整体存在 " + gap.size() + " 处明显差距，建议围绕这些差距重点准备面试。";
        return new MatchOut(overall, skillScore, expScore, eduScore, gap, summary);
    }

    private int scoreSkill(JdParsed jd, List<String> resumeSkills) {
        if (jd.hardSkills().isEmpty()) return 80;
        int hit = 0;
        for (String need : jd.hardSkills()) if (coversAny(need, resumeSkills)) hit++;
        return (int) Math.round(hit * 100.0 / jd.hardSkills().size());
    }

    private int scoreExperience(JdParsed jd, ResumeParsed resume) {
        Integer need = jd.minExperienceYears();
        if (need == null || need <= 0) return 100;
        double years = resumeYears(resume);
        if (years >= need) return 100;
        return (int) Math.max(0, Math.round(years / need * 100));
    }

    private int scoreEducation(JdParsed jd, ResumeParsed resume) {
        String req = jd.educationRequirement();
        if (req == null || req.isBlank()) return 100;
        int reqRank = degreeRank(req);
        int best = resume.education().stream()
                .mapToInt(e -> degreeRank(e.degree() == null ? "" : e.degree())).max().orElse(0);
        if (best == 0) return 30;
        return best >= reqRank ? 100 : 55;
    }

    private boolean educationSatisfied(String req, ResumeParsed resume) {
        if (req == null || req.isBlank()) return true;
        int reqRank = degreeRank(req);
        int best = resume.education().stream()
                .mapToInt(e -> degreeRank(e.degree() == null ? "" : e.degree())).max().orElse(0);
        return best >= reqRank;
    }

    private int degreeRank(String degree) {
        if (degree.contains("博士")) return 4;
        if (degree.contains("硕士")) return 3;
        if (degree.contains("本科")) return 2;
        if (degree.contains("大专")) return 1;
        return 0;
    }

    private int resumeYears(ResumeParsed resume) {
        double total = 0;
        int currentYear = java.time.Year.now().getValue();
        for (ResumeParsed.Experience exp : resume.experience()) {
            if (exp.period() == null) continue;
            Matcher m = Pattern.compile("(20\\d{2})").matcher(exp.period());
            int from = 0;
            while (m.find()) { if (from == 0) from = Integer.parseInt(m.group(1)); }
            if (exp.period().contains("至今") || exp.period().contains("现在")) {
                total += (currentYear - from);
            } else {
                Matcher to = Pattern.compile("(20\\d{2})").matcher(exp.period());
                int last = from;
                while (to.find()) last = Integer.parseInt(to.group(1));
                total += (last - from);
            }
        }
        return (int) total;
    }

    private boolean coversAny(String need, List<String> have) {
        for (String h : have) {
            if (h.equalsIgnoreCase(need) || h.toLowerCase(Locale.ROOT).contains(need.toLowerCase(Locale.ROOT))
                    || need.toLowerCase(Locale.ROOT).contains(h.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- R4 focus

    private FocusOut buildFocus(FocusInput in) {
        List<String> topics = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        for (GapItem gap : in.gap()) {
            topics.add(gap.item());
            kinds.add(gap.dimension());
        }
        int count = Math.max(1, in.questionCount());
        List<FocusItem> focus = new ArrayList<>();
        int idx = 1;
        for (int i = 0; i < topics.size() && idx <= count; i++) {
            focus.add(focusOf(idx++, topics.get(i), kinds.get(i)));
        }
        while (idx <= count) { // 兜底问题：覆盖表达/项目真实性/岗位认知
            if (idx % 3 == 0) focus.add(focusOf(idx++, "岗位认知与职业规划",
                    "考察对 " + in.targetPosition() + " 的理解与稳定性"));
            else if (idx % 3 == 1) focus.add(focusOf(idx++, "最具代表性项目复盘",
                    "考察项目真实性、个人贡献与表达逻辑"));
            else focus.add(focusOf(idx++, "线上故障排查思路",
                    "考察问题定位与系统性思维"));
        }
        return new FocusOut(focus);
    }

    private FocusItem focusOf(int index, String item, String kind) {
        String direction;
        String examinePoint;
        switch (kind) {
            case "skill" -> {
                direction = "围绕「" + item + "」展开实战深挖";
                examinePoint = "考察 " + item + " 的核心原理、适用场景与真实项目落地细节";
            }
            case "experience" -> {
                direction = "经验与项目规模深挖";
                examinePoint = "考察项目量级、承担职责、性能与协作难点";
            }
            case "education" -> {
                direction = "基础与学习能力补强验证";
                examinePoint = "考察底层知识是否扎实、是否有自学与补齐计划";
            }
            default -> {
                direction = item;
                examinePoint = item;
            }
        }
        String prepare = "建议准备一个亲身案例，按「背景-行动-结果」讲清细节；若缺实战则如实说明并给出补齐计划。";
        return new FocusItem(index, direction, examinePoint, prepare);
    }

    // ---------------------------------------------------------------- R5 question

    private QuestionOut askQuestion(QuestionInput in) {
        FocusItem focus = in.focus();
        String content = "围绕面试重点「" + focus.direction() + "」——" + focus.examinePoint()
                + "。请结合你的真实经历具体作答（若有代表性项目请一并说明你的角色与关键取舍）。";
        String reference = "参考答案要点：先给出对「" + shorten(focus.direction(), 20)
                + "」的核心结论/定义，再按「背景—方案—取舍—结果」拆解，落到一个具体可验证的技术点或项目做法上，"
                + "最后用一句话总结踩过的坑与改进。此题的评判关键在：是否先结论、是否落到项目细节、是否可量化。";
        String hint = "先想清楚「" + shorten(focus.direction(), 16)
                + "」你最熟的一个真实场景，按 结论→原因→案例 展开，别急着堆术语。";
        if (in.weakSkills() != null && !in.weakSkills().isEmpty()) {
            reference += " 本题针对你的薄弱点「" + shorten(in.weakSkills().get(0), 18)
                    + "」特意深挖，参考答案里请补上对该薄弱点的自查与补强路径。";
            hint += " 本题刻意戳你薄弱点「" + shorten(in.weakSkills().get(0), 16) + "」，作答时主动说清现状与补强计划会更加分。";
        }
        return new QuestionOut(content, "出题依据：" + focus.direction(), reference, hint);
    }

    // ---------------------------------------------------------------- R6 grade

    private GradeOut gradeAnswer(GradeInput in) {
        String answer = in.answer() == null ? "" : in.answer().trim();
        boolean hasReference = in.referenceAnswer() != null && !in.referenceAnswer().isBlank();
        return hasReference ? gradeAgainstReference(in, answer) : gradeByHeuristics(in, answer);
    }

    /** 参照参考答案的确定性评分（离线路径，与真实模型同一口径：答题要点覆盖度）。 */
    private GradeOut gradeAgainstReference(GradeInput in, String answer) {
        int score = scoreAgainstReference(answer, in.referenceAnswer());
        if (score < 0) { // 理论上不会到达（有 reference 才走本方法），防御回退
            return gradeByHeuristics(in, answer);
        }
        boolean practice = "practice".equalsIgnoreCase(in.mode());
        String examine = in.examinePoint() == null ? "本题考察点" : in.examinePoint();
        String topic = shorten(examine, 18);
        boolean structured = containsAnyWord(answer, "首先", "其次", "然后", "最后", "一是", "二是", "步骤", "总结");
        boolean technical = containsAnyWord(answer, "接口", "SQL", "索引", "缓存", "Redis", "MySQL",
                "线程", "并发", "事务", "分布式", "算法", "设计模式", "QPS", "性能", "幂等", "消息队列");
        int normLen = normForGrade(answer).length();

        List<String> strong = new ArrayList<>();
        if (score >= 85) strong.add("作答覆盖了本题多数答题要点，内容较完整");
        if (normLen >= 60) strong.add("作答有足够篇幅，能把思路展开说明");
        if (structured) strong.add("条理较清晰，能按步骤/要点组织表达");
        if (technical) strong.add("能落到具体技术手段与设计考虑，而非泛泛而谈");

        List<String> weak = new ArrayList<>();
        if (score < 40) weak.add("作答与参考答案要点重合度低，疑似离题或要点覆盖不足");
        if (normLen < 25) weak.add("回答过于简短，缺少可评估的细节");
        if (!structured) weak.add("组织性一般，建议用「先结论-再拆解-后举例」的结构对照要点逐一作答");

        String suggestion;
        if (score < 40) {
            suggestion = "建议打开本题参考答案，先按「概念-关键点-落地细节」梳理需要覆盖的要点，再据此重答一遍，确保不漏点。";
        } else if (score >= 85) {
            suggestion = "要点覆盖比较全，可继续打磨：用自己的话更精炼地展开，并补上可量化的项目结果。";
        } else {
            suggestion = "已覆盖部分要点，仍可对照参考答案补上遗漏点（" + topic + " 的关键手段/取舍/落地例子）。";
        }
        if (practice) {
            suggestion = "(练习) " + suggestion + " 提交后可在「参考答案」里逐条对照差异。";
        }

        boolean suggestFollowup = normLen < 25 || score < 58;
        String resumeAdvice = weak.isEmpty()
                ? "本题要点覆盖扎实：建议回到简历把对应项目标注为「核心贡献/个人主导」，并补上量化结果（性能、耗时、QPS 提升等），让「"
                        + topic + "」相关优势在简历里一眼可见。"
                : "结合本题暴露的「" + shorten(weak.get(0), 16) + "」，回简历补强：在涉及「" + topic
                        + "」的项目/经历中写清你负责的模块、技术方案与可量化结果（如耗时/性能提升 N%）；若缺该经历，可补一项最接近的课程设计或开源项目。";
        return new GradeOut(score, strong, weak, suggestion, suggestFollowup, resumeAdvice);
    }

    /** 无参考答案时的旧确定性启发式（仅参考缺失时兜底，避免回归）。 */
    private GradeOut gradeByHeuristics(GradeInput in, String answer) {
        int length = answer.length();
        boolean structured = containsAnyWord(answer, "首先", "其次", "然后", "最后", "步骤", "方案", "分", "一是", "二是");
        boolean technical = containsAnyWord(answer, "接口", "SQL", "索引", "缓存", "Redis", "MySQL",
                "线程", "并发", "事务", "分布式", "算法", "设计模式", "QPS", "性能", "幂等", "消息队列");

        int score;
        if (length == 0) score = 0;
        else {
            score = 25 + length / 6;
            if (technical) score += 12;
            if (structured) score += 8;
            score = Math.min(score, 96);
        }

        List<String> strong = new ArrayList<>();
        if (length >= 60) strong.add("作答有足够篇幅，能把思路展开说明");
        if (structured) strong.add("条理较清晰，能按步骤/要点组织表达");
        if (technical) strong.add("能落到具体技术手段与设计考虑，而非泛泛而谈");

        List<String> weak = new ArrayList<>();
        if (length < 25) weak.add("回答过于简短，缺少可评估的细节");
        if (!technical) weak.add("未结合具体技术点/项目实例，说服力不足");
        if (!structured) weak.add("组织性一般，建议用「先结论-再拆解-后举例」的结构");

        boolean practice = "practice".equalsIgnoreCase(in.mode());
        String examine = in.examinePoint() == null ? "本题考察点" : in.examinePoint();
        String suggestion;
        if (score < 40) suggestion = "建议就「" + shorten(examine, 24)
                + "」补一次系统梳理，作答时先给结论再给论据。";
        else if (weak.isEmpty()) suggestion = "回答质量不错，可继续打磨：增加数字与量化结果，并准备一到两个追问预案。";
        else suggestion = "建议结合真实项目把考察点讲透：" + weak.get(0) + "；下次先抛结论，再展开细节。";
        if (practice) {
            suggestion = "(练习) 别灰心，对照参考答案复盘一下差异即可。改进方向：" + suggestion;
        }

        boolean suggestFollowup = length < 30 || (score >= 50 && score < 78);
        String topic = shorten(examine, 18);
        String resumeAdvice;
        if (weak.isEmpty()) {
            resumeAdvice = "本题表现扎实：建议回到简历把对应项目标注为「核心贡献/个人主导」，并补上量化结果（性能、耗时、QPS 提升等），让「"
                    + topic + "」相关优势在简历里一眼可见。";
        } else {
            String firstWeak = shorten(weak.get(0), 16);
            resumeAdvice = "结合本题暴露的「" + firstWeak + "」，回简历补强：在涉及「" + topic
                    + "」的项目/经历中写清你负责的模块、技术方案与可量化结果（如耗时/性能提升 N%）；若缺该经历，可补一项最接近的课程设计或开源项目。";
        }
        return new GradeOut(score, strong, weak, suggestion, suggestFollowup, resumeAdvice);
    }

    /** 归一化：去空白/常见标点并转小写（离线文本比对用）。 */
    static String normForGrade(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、！？!?（）()【】\\[\\]《》“”\"'‘’·…—–\\-]", "");
    }

    /** 作答对参考答案的覆盖重叠：作答含参考答案多少个相邻字符二元组（0..1）。 */
    static double gradeOverlap(String answer, String reference) {
        String a = normForGrade(answer);
        String r = normForGrade(reference);
        if (r.isEmpty()) return 0;
        if (r.length() < 2) return a.contains(r) ? 1.0 : 0.0;
        int total = r.length() - 1;
        int hit = 0;
        for (int i = 0; i < total; i++) {
            if (a.contains(r.substring(i, i + 2))) hit++;
        }
        return (double) hit / total;
    }

    /**
     * 参照参考答案的离线要点覆盖打分（0..98；无参考答案返回 -1，由调用方走启发式）。
     * 空作答=0；作答<10 字或几乎不与要点重叠（overlap<0.12）=压到 ≤30；
     * 照抄参考答案 ≈ 高分。
     */
    static int scoreAgainstReference(String answer, String reference) {
        String a = normForGrade(answer);
        String r = normForGrade(reference);
        if (a.isEmpty()) return 0;
        if (r.isEmpty()) return -1;
        double ov = gradeOverlap(a, r);
        if (a.length() < 10) return Math.min(30, (int) Math.round(ov * 30));
        if (ov < 0.12) return Math.min(30, 6 + (int) Math.round(ov * 24));
        boolean structured = answer.contains("首先") || answer.contains("其次") || answer.contains("然后")
                || answer.contains("最后") || answer.contains("一是") || answer.contains("二是")
                || answer.contains("步骤") || answer.contains("总结") || answer.contains("综上");
        int bonus = (structured ? 6 : 0) + (a.length() >= Math.max(20, r.length() / 2) ? 4 : 0);
        return Math.min(98, (int) Math.round(ov * 88) + bonus);
    }

    // ---------------------------------------------------------------- R7 follow-up

    private FollowUpOut buildFollowUp(FollowUpInput in) {
        String content;
        if (in.weakPoint() != null && !in.weakPoint().isBlank()) {
            content = "你刚才在「" + shorten(in.weakPoint(), 20)
                    + "」上说得还不够深入。追问：如果由你负责把它落地到生产，你会如何设计方案、预估风险并做验证？";
        } else {
            content = "追问：这个方案如果上线后出现明显性能回退，你会按什么顺序定位并优化？请给出排查思路。";
        }
        return new FollowUpOut(content);
    }

    // ---------------------------------------------------------------- R8 report-analysis（离线确定性兜底，与模型输出同构）

    private ReportDeepOut buildReportAnalysis(ReportAnalysisInput in) {
        Map<String, Integer> dims = in.dimensions() == null ? Map.of() : in.dimensions();
        List<ReportDeepOut.DimensionAnalysis> dimAnalyses = new ArrayList<>();
        for (String key : DIM_ORDER) {
            int score = dims.getOrDefault(key, 0);
            String cn = DIM_CN.getOrDefault(key, key);
            dimAnalyses.add(new ReportDeepOut.DimensionAnalysis(key, score,
                    dimAnalysis(key, cn, score), dimAdvice(key, cn, score)));
        }
        return new ReportDeepOut(overallAnalysis(in, dims), dimAnalyses, conclusion(in), resumeAdviceOf(in));
    }

    private String dimAnalysis(String key, String cn, int score) {
        String grade = score >= 75 ? "整体较好" : score >= 55 ? "基本达标但有波动" : "尚未达标";
        return switch (key) {
            case "job_match" -> "「" + cn + "」得分 " + score + "，" + grade
                    + "：主要反映简历技能/经验对目标 JD 要求的覆盖情况，低分通常来自 JD 有要求但简历未体现或匹配不足的方向。";
            case "professional" -> "「" + cn + "」得分 " + score + "，" + grade
                    + "：由各题得分的平均水平折算，反映技术概念与项目细节的掌握深度。";
            case "expression" -> "「" + cn + "」得分 " + score + "，" + grade
                    + "：综合作答篇幅与结构化程度评估，回答过短或缺少要点组织会拉低该维。";
            case "logic" -> "「" + cn + "」得分 " + score + "，" + grade
                    + "：考察是否用“先结论—再拆解—后举例”的方式组织答案。";
            case "adaptability" -> "「" + cn + "」得分 " + score + "，" + grade
                    + "：反映面对追问时的有效作答占比，占比越低说明应变与补全能力越需打磨。";
            default -> "「" + cn + "」得分 " + score + "，" + grade
                    + "：综合追问作答质量评估学习与改进意愿。";
        };
    }

    private String dimAdvice(String key, String cn, int score) {
        if (score >= 75) return "保持当前水平，向“能讲出设计取舍与可量化结果”的更高标准打磨，并把对应亮点同步到简历与自我介绍。";
        return switch (key) {
            case "job_match" -> "对照目标 JD 逐个核对岗位关键词，把简历中缺失/薄弱的技能补齐为真实经历中的证据，而不是堆砌术语。";
            case "professional" -> "针对目标岗位核心技术补一轮系统梳理，作答时结合真实项目讲清原理、选型与落地。";
            case "expression" -> "用“结论先行、分条展开、举例收尾”的结构练习回答，并控制单题篇幅达到可评估的长度。";
            case "logic" -> "答题前先在草稿写下 2-3 个要点顺序，再按“首先/其次/最后”展开。";
            case "adaptability" -> "主动把“不会”转成“我会怎么定位与补齐”的具体路径，并为常见追问准备 1-2 个预案。";
            default -> "复盘每题追问，归纳共性不足并专项补强，沉淀自己的追问预案库。";
        };
    }

    private String overallAnalysis(ReportAnalysisInput in, Map<String, Integer> dims) {
        Map.Entry<String, Integer> maxEntry = dims.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue)).orElse(null);
        Map.Entry<String, Integer> minEntry = dims.entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getValue)).orElse(null);
        String strongest = maxEntry == null ? "—"
                : DIM_CN.getOrDefault(maxEntry.getKey(), maxEntry.getKey()) + "（" + maxEntry.getValue() + " 分）";
        String weakest = minEntry == null ? "—"
                : DIM_CN.getOrDefault(minEntry.getKey(), minEntry.getKey()) + "（" + minEntry.getValue() + " 分）";
        int answered = 0;
        if (in.questions() != null) answered = (int) in.questions().stream()
                .filter(q -> q.answer() != null && !q.answer().isBlank()).count();
        String unansweredNote = answered < (in.completedCount() == 0 ? in.questionCount() : in.completedCount())
                ? "其中存在未作答或回答过短的题目，相关维度评估会相应受限。"
                : "";
        return "本场针对「" + in.targetPosition() + "」共 " + in.questionCount() + " 题（完成 " + in.completedCount()
                + " 题，有效作答 " + answered + " 题），评级 " + (in.rating() == null ? "—" : in.rating())
                + "、综合分 " + (in.compositeScore() == null ? "—" : in.compositeScore())
                + "。整体呈“" + strongest + " 为相对强项、" + weakest + " 偏弱”的态势。" + unansweredNote
                + " 总体建议口径：" + (in.recommendation() == null || in.recommendation().isBlank()
                ? "—" : in.recommendation());
    }

    private String conclusion(ReportAnalysisInput in) {
        Map<String, Integer> dims = in.dimensions() == null ? Map.of() : in.dimensions();
        Map.Entry<String, Integer> minEntry = dims.entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getValue)).orElse(null);
        String focus = minEntry == null ? "岗位匹配" : DIM_CN.getOrDefault(minEntry.getKey(), minEntry.getKey());
        return "总体而言，候选人围绕「" + in.targetPosition() + "」的模拟表现评级为 "
                + (in.rating() == null ? "—" : in.rating())
                + "。当前最值得优先补强的是「" + focus + "」维度：先把它对应的短板固化为可讲的经历，再配合逐题给出的简历修改建议迭代一版简历，即可形成“简历—回答—报告”的闭环提升。";
    }

    private ReportDeepOut.ResumeAdvice resumeAdviceOf(ReportAnalysisInput in) {
        List<String> perQuestion = new ArrayList<>();
        if (in.questions() != null) {
            for (ReportAnalysisInput.QuestionBrief q : in.questions()) {
                if (q.resumeAdvice() != null && !q.resumeAdvice().isBlank() && !perQuestion.contains(q.resumeAdvice())) {
                    perQuestion.add(q.resumeAdvice());
                }
            }
        }
        List<String> gaps = in.matchGaps() == null ? List.of() : in.matchGaps();
        List<String> improvements = in.improvements() == null ? List.of() : in.improvements();

        List<String> high = new ArrayList<>();
        for (String g : gaps) {
            if (high.size() >= 4) break;
            high.add("围绕“" + g + "”在简历相应技能/经历处补充可验证证据：写清你实际负责的模块、用到的技术、以及一个可量化的结果；若确实没有对应经历，就不要写入技能清单，改为补一项最接近的课程设计或开源项目。");
        }

        List<String> med = new ArrayList<>();
        for (String s : perQuestion.subList(0, Math.min(3, perQuestion.size()))) {
            if (med.size() < 3) med.add(s);
        }
        for (String im : improvements) {
            if (med.size() >= 3) break;
            med.add("针对面试反馈的“" + im + "”，回到简历对应位置把描述写得更具体并附上改进闭环。");
        }

        List<String> low = new ArrayList<>();
        if (perQuestion.size() > 3) low.addAll(perQuestion.subList(3, perQuestion.size()));
        if (low.size() > 2) low = low.subList(0, 2);
        if (low.isEmpty()) {
            low.add("整体结构可再打磨：统一时间线、量化表述与关键词密度，确保 HR/系统初筛时重点信息一眼可见。");
        }

        List<ReportDeepOut.AdviceGroup> groups = new ArrayList<>();
        if (!high.isEmpty()) groups.add(new ReportDeepOut.AdviceGroup("先解决硬伤：对齐岗位的硬性要求", "HIGH", high));
        if (!med.isEmpty()) groups.add(new ReportDeepOut.AdviceGroup("核心改动：把经历改到经得起深挖", "MED", med));
        if (!low.isEmpty()) groups.add(new ReportDeepOut.AdviceGroup("呈现优化：结构、措辞与细节", "LOW", low));

        String intro = "本建议由本场 " + (in.questions() == null ? 0 : in.questions().size())
                + " 道题的逐题简历建议与匹配差距共同归纳，逐条均可回溯到具体面试证据。";
        return new ReportDeepOut.ResumeAdvice(intro, groups);
    }

    // ---------------------------------------------------------------- R9 weak-extract（离线确定性兜底，与模型输出同构）

    private WeakExtractOut extractWeak(WeakExtractInput in) {
        List<WeakExtractOut.SkillItem> skills = new ArrayList<>();
        java.util.Set<String> seenTags = new java.util.HashSet<>();
        Map<String, Integer> dims = in.dimensions() == null ? Map.of() : in.dimensions();

        // 维度低分（<60）优先：如"岗位匹配-基础"
        for (String key : DIM_ORDER) {
            int val = dims.getOrDefault(key, 100);
            if (val >= 60) continue;
            String cn = DIM_CN.getOrDefault(key, key);
            String tag = cn + "不足";
            if (seenTags.add(tag)) {
                skills.add(new WeakExtractOut.SkillItem(tag, "general", clampWeak(val),
                        List.of("维度「" + cn + "」得分 " + val + "/100，低于 60 分阈值（源自本场落库数据）"),
                        "针对「" + cn + "」补一轮专项练习：先弄清该维度考察点，再结合真实项目反复演练。"));
            }
        }

        // 逐题低分弱项（按分从低到高，最多再取 5 条）
        List<WeakExtractInput.QuestionBrief> low = new ArrayList<>();
        if (in.questions() != null) {
            for (WeakExtractInput.QuestionBrief q : in.questions()) if (q.score() < 60) low.add(q);
        }
        low.sort(Comparator.comparingInt(WeakExtractInput.QuestionBrief::score));
        for (WeakExtractInput.QuestionBrief q : low) {
            if (skills.size() >= 8) break;
            String tag = q.weak() == null || q.weak().isEmpty()
                    ? "第" + q.orderIndex() + "题要点"
                    : shorten(q.weak().get(0), 20);
            if (!seenTags.add(tag)) continue;
            List<String> evidence = new ArrayList<>();
            evidence.add("第 " + q.orderIndex() + " 题得分 " + q.score()
                    + "（作答摘要：" + shorten(q.answer() == null ? "" : q.answer(), 40) + "）");
            if (q.weak() != null) for (String w : q.weak()) evidence.add(w);
            if (evidence.size() > 3) evidence = evidence.subList(0, 3);
            String suggestion = q.resumeAdvice() == null || q.resumeAdvice().isBlank()
                    ? "针对该弱项做一次专题梳理，并在下一场练习中主动暴露检验。"
                    : "回到简历：" + q.resumeAdvice();
            skills.add(new WeakExtractOut.SkillItem(tag, "general", clampWeak(q.score()), evidence, suggestion));
        }
        return new WeakExtractOut(skills);
    }

    private int clampWeak(int v) {
        return Math.max(5, Math.min(95, v));
    }

    // ---------------------------------------------------------------- question-bank-gen（离线确定性兜底，与模型输出同构）

    private static final List<String> BANK_TOPICS = List.of(
            "核心概念与适用场景", "底层原理", "常见坑点与最佳实践",
            "与相近技术/方案的选型对比", "实际项目中的落地细节");

    private BankGenOut genBank(BankGenInput in) {
        int count = Math.max(1, Math.min(in.count() <= 0 ? 5 : in.count(), 20));
        List<BankGenOut.Item> items = new ArrayList<>();
        String cat = in.category() == null || in.category().isBlank() ? "通用" : in.category().trim();
        for (int i = 1; i <= count; i++) {
            String topic = BANK_TOPICS.get((i - 1) % BANK_TOPICS.size());
            String content = "（离线示例）请围绕「" + cat + "」的「" + topic + "」谈一谈："
                    + "先给结论/定义，再拆解要点，最好结合你做过的一个真实场景说明取舍与效果。";
            String answer = "参考答案要点：对「" + cat + " · " + topic + "」，先一句话下结论，"
                    + "然后从原理、适用条件、局限与权衡三个层面展开；附一个实际案例说明选型与踩坑，最后给出可验证的结果或反思。";
            String hint = "从「结论 → 原理/条件 → 案例」三步组织；如果缺真实案例，可说明你准备如何补一段实践。";
            items.add(new BankGenOut.Item(content, answer, hint, List.of(cat, topic)));
        }
        return new BankGenOut(items);
    }

    // ---------------------------------------------------------------- compare-resumes（离线确定性兜底，与模型输出同构）

    /** 两份简历对比：所有行/结论只从两份结构化简历的字面字段推导，绝不补造。 */
    private CompareResumesOut compareResumes(CompareInput in) {
        ResumeParsed a = in.resumeA();
        ResumeParsed b = in.resumeB();
        String aName = in.resumeAName() == null ? "简历A" : in.resumeAName();
        String bName = in.resumeBName() == null ? "简历B" : in.resumeBName();
        String target = in.targetPosition() == null || in.targetPosition().isBlank() ? "" : in.targetPosition().trim();

        List<String> aSkills = skillNamesOf(a);
        List<String> bSkills = skillNamesOf(b);
        List<String> overlap = new ArrayList<>();
        List<String> onlyA = new ArrayList<>();
        for (String s : aSkills) {
            if (coversAny(s, bSkills)) overlap.add(s);
            else onlyA.add(s);
        }
        List<String> onlyB = new ArrayList<>();
        for (String s : bSkills) {
            if (!coversAny(s, aSkills)) onlyB.add(s);
        }

        DegreeInfo aDeg = highestDegree(a);
        DegreeInfo bDeg = highestDegree(b);
        int aYears = resumeYears(a), bYears = resumeYears(b);
        int aEdu = countOf(a.education()), bEdu = countOf(b.education());
        int aExp = countOf(a.experience()), bExp = countOf(b.experience());
        int aProj = countOf(a.projects()), bProj = countOf(b.projects());

        List<CompareResumesOut.CompareRow> fields = new ArrayList<>();
        addCompareRow(fields, "最高学历", aDeg.label(), bDeg.label(), aDeg.rank() - bDeg.rank(),
                aDeg.rank() == bDeg.rank() ? "" : (aDeg.rank() > bDeg.rank() ? "简历A学历更高，更满足硬性门槛" : "简历B学历更高，更满足硬性门槛"));
        addCompareRow(fields, "工作年限", aYears + " 年", bYears + " 年", aYears - bYears,
                aYears == bYears ? (aYears == 0 ? "两份简历均无可识别的年限数据" : "工作年限相当") : (aYears > bYears ? "简历A经验年限更长" : "简历B经验年限更长"));
        addCompareRow(fields, "技能清单", joinSkills(aSkills), joinSkills(bSkills), aSkills.size() - bSkills.size(),
                aSkills.size() == bSkills.size() ? "" : (aSkills.size() > bSkills.size() ? "简历A技能覆盖面更宽" : "简历B技能覆盖面更宽"));
        addCompareRow(fields, "教育经历", aEdu + " 条", bEdu + " 条", aEdu - bEdu,
                aEdu == bEdu ? "" : (aEdu > bEdu ? "简历A教育条目更多" : "简历B教育条目更多"));
        addCompareRow(fields, "工作经历", aExp + " 段", bExp + " 段", aExp - bExp,
                aExp == bExp ? "" : (aExp > bExp ? "简历A工作经历更丰富" : "简历B工作经历更丰富"));
        addCompareRow(fields, "项目经历", aProj + " 段", bProj + " 段", aProj - bProj,
                aProj == bProj ? "" : (aProj > bProj ? "简历A项目经历更丰富" : "简历B项目经历更丰富"));

        List<String> strengthsA = new ArrayList<>();
        List<String> strengthsB = new ArrayList<>();
        if (aYears != bYears) addEdge(aYears > bYears ? strengthsA : strengthsB,
                "经验年限更长（" + aYears + " 年 vs " + bYears + " 年）");
        if (aDeg.rank() != bDeg.rank()) addEdge(aDeg.rank() > bDeg.rank() ? strengthsA : strengthsB,
                "学历更高（" + aDeg.label() + " vs " + bDeg.label() + "）");
        if (aSkills.size() != bSkills.size()) addEdge(aSkills.size() > bSkills.size() ? strengthsA : strengthsB,
                "技能面更广（" + aSkills.size() + " 项 vs " + bSkills.size() + " 项）");
        if (aExp + aProj != bExp + bProj) addEdge(aExp + aProj > bExp + bProj ? strengthsA : strengthsB,
                "经历与项目积累更多（简历A " + aExp + " 段工作 + " + aProj + " 个项目 vs 简历B " + bExp + " 段工作 + " + bProj + " 个项目）");
        if (!onlyA.isEmpty() && onlyB.isEmpty()) addEdge(strengthsA, "持有独有技能清单（详见 onlyA）");
        if (!onlyB.isEmpty() && onlyA.isEmpty()) addEdge(strengthsB, "持有独有技能清单（详见 onlyB）");
        if (strengthsA.isEmpty()) addEdge(strengthsA, "与" + bName + "在可量化维度上基本持平，差异主要落在技能与经历细节（见字段表）");
        if (strengthsB.isEmpty()) addEdge(strengthsB, "与" + aName + "在可量化维度上基本持平，差异主要落在技能与经历细节（见字段表）");

        String differenceSummary = "简历A：" + aName + "（" + aDeg.label() + "、约 " + aYears + " 年、" + aSkills.size()
                + " 项技能）；简历B：" + bName + "（" + bDeg.label() + "、约 " + bYears + " 年、" + bSkills.size()
                + " 项技能）。双方技能重合 " + overlap.size() + " 项" + (overlap.isEmpty() ? "" : "（" + joinLimited(overlap, 6) + "）")
                + "。" + (aYears + aSkills.size() + aProj >= bYears + bSkills.size() + bProj ? aName : bName)
                + "在总量指标上整体略胜，各自优势与差距详见上表。";

        List<String> advice = new ArrayList<>();
        if (!target.isEmpty()) {
            advice.add("若目标是「" + target + "」，先核对岗位硬性技术要求落在哪一侧：技能、经验、学历中与岗位最贴近的一方便是主推版本，另一侧可作互补素材。");
        }
        if (!overlap.isEmpty()) advice.add("共同掌握的技能（" + joinLimited(overlap, 6)
                + "）是双方的基础盘：据此准备深挖问答，避免停留在概念层面。");
        if (!onlyA.isEmpty()) advice.add("「" + joinLimited(onlyA, 6) + "」为简历A独有：若岗位需要这些技能，重点围绕它们准备实战案例。");
        if (!onlyB.isEmpty()) advice.add("「" + joinLimited(onlyB, 6) + "」为简历B独有：若岗位需要这些技能，重点围绕它们准备实战案例。");
        if (aSkills.isEmpty() && bSkills.isEmpty()) {
            advice.add("两份简历的技能清单都为空：先为各自补齐结构化技能与可量化项目，再做对比才有参考价值。");
        }
        if (advice.isEmpty()) {
            advice.add("从两份简历看差异不大：建议结合目标岗位的真实要求，把更贴近岗位一方的项目补上量化结果后作为主投版本。");
        }

        return new CompareResumesOut(summarizeResume(a, aName), summarizeResume(b, bName),
                fields, overlap, onlyA, onlyB, strengthsA, strengthsB, differenceSummary, advice);
    }

    private record DegreeInfo(String label, int rank) {
    }

    private DegreeInfo highestDegree(ResumeParsed resume) {
        int best = -1;
        String label = "未填写";
        if (resume.education() != null) {
            for (ResumeParsed.Education e : resume.education()) {
                if (e.degree() == null || e.degree().isBlank()) continue;
                int rank = degreeRank(e.degree());
                if (rank > best) { best = rank; label = e.degree(); }
            }
        }
        return new DegreeInfo(best < 0 ? "未填写" : label, best);
    }

    private List<String> skillNamesOf(ResumeParsed resume) {
        if (resume.skills() == null) return List.of();
        return resume.skills().stream().map(ResumeParsed.Skill::name)
                .filter(n -> n != null && !n.isBlank()).toList();
    }

    private int countOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private String joinSkills(List<String> skills) {
        if (skills.isEmpty()) return "未填写技能";
        return joinLimited(skills, 10);
    }

    private String joinLimited(List<String> items, int limit) {
        if (items.isEmpty()) return "";
        List<String> head = items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
        String joined = String.join("、", head);
        return items.size() > limit ? joined + " 等" : joined;
    }

    private void addCompareRow(List<CompareResumesOut.CompareRow> fields, String field,
                               String aValue, String bValue, int delta, String note) {
        fields.add(new CompareResumesOut.CompareRow(field, aValue, bValue,
                delta == 0 && note.isBlank() ? "" : note));
    }

    private void addEdge(List<String> target, String text) {
        if (target.size() < 3) target.add(text);
    }

    private String summarizeResume(ResumeParsed resume, String label) {
        DegreeInfo deg = highestDegree(resume);
        int years = resumeYears(resume);
        int edu = countOf(resume.education());
        int exp = countOf(resume.experience());
        int proj = countOf(resume.projects());
        List<String> skills = skillNamesOf(resume);
        String head = resume.name() == null || resume.name().isBlank() ? "该候选人" : "候选人 " + resume.name();
        String schoolMajor = "";
        if (resume.education() != null && !resume.education().isEmpty()) {
            ResumeParsed.Education first = resume.education().get(0);
            String school = first.school() == null ? "" : first.school();
            String major = first.major() == null ? "" : first.major();
            schoolMajor = (school.isBlank() ? "" : school) + (major.isBlank() ? "" : "·" + major);
        }
        List<String> parts = new ArrayList<>();
        parts.add(head + "，" + deg.label());
        if (years > 0) parts.add("约 " + years + " 年经验");
        if (edu > 0 || !schoolMajor.isBlank()) parts.add("教育 " + (schoolMajor.isBlank() ? edu + " 条" : schoolMajor));
        if (exp + proj > 0) parts.add("工作 " + exp + " 段、项目 " + proj + " 段");
        if (!skills.isEmpty()) parts.add("技能重心：" + joinLimited(skills, 6));
        if (parts.size() == 1) parts.add("结构化信息较薄，建议先补齐教育/经历/技能");
        return String.join("；", parts) + "。";
    }

    // ---------------------------------------------------------------- tools

    private String firstLikelyName(String text) {
        for (String line : text.split("\\R")) {
            String candidate = line.trim();
            if (candidate.matches("[\\p{IsHan}]{2,4}")
                    || candidate.matches("[A-Z][a-z]+(?: [A-Z][a-z]+){1,2}")) return candidate;
        }
        return null;
    }

    private List<ResumeParsed.Skill> detectSkills(String text) {
        List<String> tokens = detectSkillTokens(text);
        List<ResumeParsed.Skill> skills = new ArrayList<>();
        for (String token : tokens) skills.add(new ResumeParsed.Skill(token, levelNear(text, token)));
        return skills;
    }

    /** 长词优先、且已被更长命中词覆盖的短词不再重复入列。 */
    private List<String> detectSkillTokens(String text) {
        List<String> sorted = new ArrayList<>(SKILL_POOL);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        List<String> found = new ArrayList<>();
        for (String token : sorted) {
            boolean covered = false;
            for (String have : found) {
                if (containsAsWholeWord(have, token)) { covered = true; break; }
            }
            if (covered) continue;
            if (matchesToken(text, token)) found.add(token);
        }
        return found;
    }

    private boolean matchesToken(String text, String token) {
        if (token.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
            return text.contains(token);
        }
        return Pattern.compile("(?i)(?<![A-Za-z0-9])" + Pattern.quote(token) + "(?![A-Za-z0-9])")
                .matcher(text).find();
    }

    private boolean containsAsWholeWord(String text, String token) {
        if (text.equalsIgnoreCase(token)) return true;
        String lower = text.toLowerCase(Locale.ROOT);
        String t = token.toLowerCase(Locale.ROOT);
        return lower.startsWith(t + " ") || lower.endsWith(" " + t) || lower.contains(" " + t + " ")
                || lower.contains("、" + t);
    }

    private String levelNear(String text, String token) {
        int idx = text.toLowerCase(Locale.ROOT).indexOf(token.toLowerCase(Locale.ROOT));
        if (idx < 0) return "未知";
        int start = Math.max(0, idx - 8);
        String near = text.substring(start, idx);
        if (near.contains("精通")) return "精通";
        if (near.contains("熟练") || near.contains("熟悉")) return "熟练";
        if (near.contains("了解")) return "了解";
        return "未知";
    }

    private String firstDegree(String line) {
        for (String d : DEGREES) if (line.contains(d)) return d;
        return null;
    }

    private String partDegree(String part) {
        for (String d : DEGREES) if (part.contains(d)) return part.replaceAll("及以上|及以下|以上", "").trim();
        return null;
    }

    private String yearRange(String line) {
        Matcher m = YEAR_RANGE.matcher(line);
        return m.find() ? m.group() : null;
    }

    /** 先按中文/英文标点切分；无分隔符时退回按空白切分。 */
    private List<String> splitParts(String line) {
        String[] bySep = line.split("[·|,，、;；：]+");
        List<String> parts = new ArrayList<>();
        for (String s : bySep) {
            String t = s.trim();
            if (!t.isEmpty()) parts.add(t);
        }
        if (parts.size() >= 2) return parts;
        parts.clear();
        for (String s : line.split("\\s+")) {
            String t = s.trim();
            if (!t.isEmpty()) parts.add(t);
        }
        return parts;
    }

    private boolean hasAny(String text, List<String> keys) {
        for (String key : keys) if (text.contains(key)) return true;
        return false;
    }

    private boolean containsAnyWord(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    private String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }
}
