package com.group5.interview.module.questionbank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.ai.AIService;
import com.group5.interview.ai.rag.TextChunkService;
import com.group5.interview.common.BusinessException;
import com.group5.interview.common.ErrorCode;
import com.group5.interview.common.PageResult;
import com.group5.interview.entity.QuestionBank;
import com.group5.interview.module.questionbank.dto.*;
import com.group5.interview.repository.QuestionBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 题库（R1）服务：普通用户浏览/分类；管理员 CRUD + AI 一键扩充（question-bank-gen）。
 * 浏览视图不含答案/提示，避免未作答前剧透；答案仅在进入练习后由面试引擎按题揭示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankService {

    private final QuestionBankRepository bankRepository;
    private final AIService aiService;
    private final TextChunkService textChunkService;
    private final ObjectMapper objectMapper;

    /** 分类 + 题量（首页/练习入口卡片）。 */
    @Transactional(readOnly = true)
    public List<BankCategoryCount> categories() {
        List<Object[]> rows = bankRepository.countByEnabledCategory();
        List<BankCategoryCount> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new BankCategoryCount((String) r[0],
                    r[1] == null ? 0 : ((Number) r[1]).longValue()));
        }
        return out;
    }

    /** 普通用户浏览（不含 answer/hint）。 */
    @Transactional(readOnly = true)
    public PageResult<BankQuestionView> publicPage(String category, String keyword, int page, int size) {
        List<QuestionBank> matched = match(category, keyword);
        List<QuestionBank> sliced = slice(matched, page, size);
        List<BankQuestionView> items = sliced.stream().map(q -> new BankQuestionView(q.getId(), q.getCategory(),
                q.getQuestionType(), q.getDifficulty(), q.getContent(), knowledgeOf(q))).toList();
        return new PageResult<>(items, matched.size());
    }

    /** 管理员管理视图（含答案/hint/启用态）。 */
    @Transactional(readOnly = true)
    public PageResult<BankAdminView> adminPage(String category, String keyword, int page, int size) {
        List<QuestionBank> matched = match(category, keyword);
        List<QuestionBank> sliced = slice(matched, page, size);
        List<BankAdminView> items = sliced.stream().map(q -> new BankAdminView(q.getId(), q.getCategory(),
                q.getQuestionType(), q.getDifficulty(), q.getContent(), q.getAnswer(), q.getHint(),
                knowledgeOf(q), q.isSourceAi(), q.isEnabled(), q.getUsageCount(), q.getUpdatedAt())).toList();
        return new PageResult<>(items, matched.size());
    }

    @Transactional
    public BankAdminView create(BankUpsertRequest req, Long adminId) {
        QuestionBank q = new QuestionBank();
        apply(q, req);
        q.setSourceAi(false);
        q.setEnabled(true);
        q.setCreatedBy(adminId);
        LocalDateTime now = LocalDateTime.now();
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        QuestionBank saved = bankRepository.save(q);
        textChunkService.indexQuestionBank(saved); // R10：新题同步进入检索语料
        return toAdmin(saved);
    }

    @Transactional
    public BankAdminView update(Long id, BankUpsertRequest req) {
        QuestionBank q = bankRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        apply(q, req);
        q.setUpdatedAt(LocalDateTime.now());
        QuestionBank saved = bankRepository.save(q);
        textChunkService.indexQuestionBank(saved); // R10：改题后刷新检索切片
        return toAdmin(saved);
    }

    /** 逻辑删除：置 enabled=false（保留引用，避免练习历史断链）。 */
    @Transactional
    public void delete(Long id) {
        QuestionBank q = bankRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "题目不存在"));
        q.setEnabled(false);
        q.setUpdatedAt(LocalDateTime.now());
        QuestionBank saved = bankRepository.save(q);
        textChunkService.indexQuestionBank(saved); // R10：软删 → indexQuestionBank 清掉其检索块
    }

    /** AI 生成题目标题最短字数（剔除"残缺/一句话凑数"条目）。 */
    static final int MIN_CONTENT_LEN = 12;

    /**
     * 管理员一键 AI 扩充题库（source_ai=true）。2026-09-07 严谨化：
     * 逐条先校验（题干/参考答案非空且题干不短于 {@link #MIN_CONTENT_LEN} 字），
     * 再与该分类已启用题 + 本批已收做归一化去重（等值 / 一方含另一方且较短者≥较长者的 80% 视为重复），
     * 只入库真实新增；返回入库条数（重复/残缺只在日志体现，不报错）。
     */
    @Transactional
    public int generate(BankGenRequest req, Long adminId) {
        String category = req.category().trim();
        int count = req.count() == null ? 5 : Math.max(1, Math.min(10, req.count()));
        int difficulty = req.difficulty() == null ? 3 : Math.max(1, Math.min(5, req.difficulty()));
        BankGenOut out = aiService.structured("question-bank-gen",
                new BankGenInput(category, count, difficulty), BankGenOut.class);
        List<BankGenOut.Item> items = out == null || out.questions() == null ? List.of() : out.questions();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_FAILED, "AI 未生成有效题目，请重试");
        }
        // 去重基准 = 该分类现有全部启用题题干（归一化） + 本批已收
        List<String> existing = bankRepository.findByEnabledTrueAndCategory(category).stream()
                .map(QuestionBank::getContent)
                .filter(c -> c != null && !c.isBlank())
                .map(BankService::normDup)
                .toList();
        Set<String> seen = new HashSet<>(existing);
        List<String> order = new ArrayList<>(existing);
        LocalDateTime now = LocalDateTime.now();
        int inserted = 0, skipped = 0;
        for (BankGenOut.Item item : items) {
            String content = item.content();
            String answer = item.answer();
            if (content == null || answer == null || content.isBlank() || answer.isBlank()
                    || content.trim().length() < MIN_CONTENT_LEN) {
                skipped++; // 残缺条目不入库（AI 偶发输出空答案/凑数题干）
                continue;
            }
            String norm = normDup(content.trim());
            if (isDuplicate(seen, order, norm)) {
                skipped++; // 与库内/本批重复
                continue;
            }
            seen.add(norm);
            order.add(norm);
            QuestionBank q = new QuestionBank();
            q.setCategory(category);
            q.setQuestionType("qa");
            q.setDifficulty(difficulty);
            q.setContent(content.trim());
            q.setAnswer(answer);
            q.setHint(item.hint());
            q.setKnowledgePoints(writeList(item.knowledgePoints()));
            q.setSourceAi(true);
            q.setEnabled(true);
            q.setCreatedBy(adminId);
            q.setCreatedAt(now);
            q.setUpdatedAt(now);
            QuestionBank saved = bankRepository.save(q);
            textChunkService.indexQuestionBank(saved); // R10：AI 生成的题同步进检索语料
            inserted++;
        }
        log.info("[bank] 管理员 AI 扩充题库 category={} 请求={} 入库={} 跳过={}（残缺/重复）",
                category, count, inserted, skipped);
        return inserted;
    }

    // ---- 去重纯逻辑（抽离便于单测） ------------------------------------

    /** 题干归一化：去两端/折叠空白 + 小写（英文大小写差异不算新增）。 */
    static String normDup(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 重复判定：归一化后等值，或一方包含另一方且较短文本长度 ≥ 较长者的 80%（近重复）。 */
    static boolean sameQuestion(String a, String b) {
        if (a.equals(b)) return true;
        String longer = a.length() >= b.length() ? a : b;
        String shorter = a.length() >= b.length() ? b : a;
        return longer.contains(shorter) && shorter.length() >= longer.length() * 0.8;
    }

    private boolean isDuplicate(Set<String> seen, List<String> order, String norm) {
        if (seen.contains(norm)) return true;
        for (String prev : order) {
            if (sameQuestion(prev, norm)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- internal

    private List<QuestionBank> match(String category, String keyword) {
        boolean useCat = category != null && !category.isBlank();
        boolean useKw = keyword != null && !keyword.isBlank();
        List<QuestionBank> matched = useCat ? bankRepository.findByEnabledTrueAndCategory(category)
                : bankRepository.findByEnabledTrue();
        if (!useKw) return matched;
        String kw = keyword.toLowerCase();
        return matched.stream().filter(q -> q.getContent() != null
                && q.getContent().toLowerCase().contains(kw)).toList();
    }

    private List<QuestionBank> slice(List<QuestionBank> all, int page, int size) {
        int pageSize = Math.max(1, Math.min(50, size));
        int pageNo = Math.max(0, page);
        if (all.isEmpty()) return List.of();
        int from = Math.min(all.size(), pageNo * pageSize);
        int to = Math.min(all.size(), from + pageSize);
        return new ArrayList<>(all.subList(from, to));
    }

    private void apply(QuestionBank q, BankUpsertRequest req) {
        q.setCategory(req.category().trim());
        q.setQuestionType(req.questionType() == null || req.questionType().isBlank() ? "qa" : req.questionType().trim());
        q.setDifficulty(req.difficulty() == null ? 3 : Math.max(1, Math.min(5, req.difficulty())));
        q.setContent(req.content());
        q.setAnswer(req.answer());
        q.setHint(req.hint());
        q.setKnowledgePoints(writeList(req.knowledgePoints()));
    }

    private BankAdminView toAdmin(QuestionBank q) {
        return new BankAdminView(q.getId(), q.getCategory(), q.getQuestionType(), q.getDifficulty(),
                q.getContent(), q.getAnswer(), q.getHint(), knowledgeOf(q), q.isSourceAi(), q.isEnabled(),
                q.getUsageCount(), q.getUpdatedAt());
    }

    private List<String> knowledgeOf(QuestionBank q) {
        List<String> kp = readList(q.getKnowledgePoints());
        return kp == null ? List.of() : kp;
    }

    private String writeList(List<String> list) {
        try { return objectMapper.writeValueAsString(list == null ? List.of() : list); }
        catch (Exception e) { return "[]"; }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return null; }
    }
}
