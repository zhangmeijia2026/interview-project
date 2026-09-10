package com.group5.interview.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group5.interview.entity.QuestionBank;
import com.group5.interview.entity.User;
import com.group5.interview.entity.UserSettings;
import com.group5.interview.repository.QuestionBankRepository;
import com.group5.interview.repository.UserRepository;
import com.group5.interview.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 启动种子数据：
 * <ul>
 *   <li>确保存在一个管理员账号（R9）admin@interview.local / Admin@12345（bcrypt cost=12 入库），幂等；</li>
 *   <li>题库为空时预置少量 Java/Python/数据库/前端 种子题（source_ai=false），保证首页题库与练习模式开箱可用。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    public static final String ADMIN_EMAIL = "admin@interview.local";
    public static final String ADMIN_DEFAULT_PASSWORD = "Admin@12345";

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final QuestionBankRepository questionBankRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<User> existingAdmin = userRepository.findFirstByRoleOrderByIdAsc("admin");
        if (existingAdmin.isPresent()) {
            log.info("[seed] 管理员已存在 id={} email={}", existingAdmin.get().getId(), existingAdmin.get().getEmail());
        } else {
            User admin = new User();
            admin.setEmail(ADMIN_EMAIL);
            admin.setPasswordHash(PASSWORD_ENCODER.encode(ADMIN_DEFAULT_PASSWORD));
            admin.setNickname("系统管理员");
            admin.setRole("admin");
            admin.setActive(true);
            admin.setCreatedAt(LocalDateTime.now());
            User saved = userRepository.save(admin);

            UserSettings settings = new UserSettings();
            settings.setUserId(saved.getId());
            settings.setUpdatedAt(LocalDateTime.now());
            userSettingsRepository.save(settings);

            log.warn("[seed] 已创建管理员 {} / {}（演示默认密码，请登录后尽快修改）",
                    ADMIN_EMAIL, ADMIN_DEFAULT_PASSWORD);
        }
        seedBankIfEmpty();
    }

    /** 题库为空时预置种子题（人工精选，source_ai=false）。 */
    private void seedBankIfEmpty() {
        if (questionBankRepository.count() > 0) {
            log.info("[seed] 题库已有 {} 条，跳过预置", questionBankRepository.count());
            return;
        }
        List<Object[]> rows = seedRows();
        LocalDateTime now = LocalDateTime.now();
        for (Object[] r : rows) {
            QuestionBank q = new QuestionBank();
            q.setCategory((String) r[0]);
            q.setQuestionType("qa");
            q.setDifficulty((Integer) r[1]);
            q.setContent((String) r[2]);
            q.setAnswer((String) r[3]);
            q.setHint((String) r[4]);
            q.setKnowledgePoints(writeList((List<String>) r[5]));
            q.setSourceAi(false);
            q.setEnabled(true);
            q.setCreatedBy(0L); // 系统预置
            q.setCreatedAt(now);
            q.setUpdatedAt(now);
            questionBankRepository.save(q);
        }
        log.info("[seed] 题库已预置 {} 条种子题", seedRows().size());
    }

    private String writeList(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 手工精选种子题：[分类, 难度, 题干, 答案, 提示, 知识点]。 */
    private List<Object[]> seedRows() {
        return List.of(
                new Object[]{"Java", 2,
                        "请谈谈 Java 中 == 与 equals() 的区别，并说明重写 equals() 时为何要同时重写 hashCode()。",
                        "== 比较引用是否指向同一对象（基本类型比较值）；equals() 是 Object 的方法，默认与 == 等价，子类常重写为按内容比较。重写 equals() 必须重写 hashCode()，否则违反 hashCode 一致性约定：相等对象必须有相同哈希码，否则 HashMap/HashSet 会出现查找不到或重复存放的问题。",
                        "从“引用比较 vs 内容比较”切入，再补充哈希集合为何依赖 hashCode 先定位后 equals 精确定位。",
                        List.of("Java基础", "Object", "集合", "equals/hashCode")},
                new Object[]{"Java", 4,
                        "一个接口 QPS 突然从 1 万掉到几百，你如何排查定位？请给出从现象到根因的系统性思路。",
                        "按“先看现象分类、再自上而下逐层排除”：1) 先确认是全部流量还是局部/单机问题，看监控（响应时间、错误率、GC、CPU/内存、连接池）；2) 依次排查：上游限流/熔断、网络/超时、应用线程池打满、慢 SQL/锁等待、缓存穿透击穿、依赖服务故障；3) 定位后用日志/链路追踪复现验证，再灰度修复并回归。关键是“先止血（限流/扩容）再定位”，避免在未分类前盲目查代码。",
                        "先说清“现象分类：全局限流？单机？依赖？”，再按 网关→应用→DB/缓存 逐层排查，答出监控指标与工具即可。",
                        List.of("性能排查", "高并发", "线上问题", "JVM", "慢SQL")},
                new Object[]{"Python", 1,
                        "Python 中列表(list)和元组(tuple)的区别是什么？",
                        "list 可变（可增删改）、支持动态长度；tuple 不可变（创建后不能修改），长度和元素引用固定，因此可用作 dict 的 key、可哈希。性能上 tuple 略轻量；语义上 tuple 常表示“结构固定的一组值”。",
                        "抓住关键词“可变 vs 不可变”，再补 tuple 可作为字典 key 的应用。",
                        List.of("Python基础", "list", "tuple")},
                new Object[]{"Python", 3,
                        "解释 Python 的 GIL 是什么，多线程做 CPU 密集任务为什么慢？如何绕过？",
                        "GIL 是 CPython 解释器级的全局锁，保证同一时刻只有一个线程执行字节码，简化内存管理。后果：多线程无法并行利用多核跑 CPU 密集任务。绕过方案：CPU 密集用多进程 multiprocessing/ProcessPoolExecutor，或把计算下沉到 C 扩展/numpy；I/O 密集用多线程/协程（asyncio）即可获得并发收益。",
                        "先讲清 GIL 限制的是“并行”而非“并发”，再区分 CPU 密集（多进程）与 I/O 密集（多线程/协程）两条路线。",
                        List.of("Python并发", "GIL", "多进程", "asyncio")},
                new Object[]{"数据库", 2,
                        "什么是索引？为什么用 B+ 树而不是哈希或平衡二叉树？",
                        "索引是加速查询的数据结构，牺牲写性能换读性能。MySQL InnoDB 用 B+ 树：相比哈希，B+ 树支持范围查询与排序（哈希只支持等值）；相比二叉/平衡树，B+ 树扇出大、层数低（3-4 层可覆盖千万行），且叶子节点用链表串联天然支持范围扫描与顺序 IO。",
                        "从“支持范围查询 + 磁盘 IO 友好”两个角度对比，说出层数低与叶子链表即可。",
                        List.of("索引", "B+树", "InnoDB", "查询优化")},
                new Object[]{"数据库", 4,
                        "一个查询很慢，你会怎么优化？请给出分析步骤与常见手段。",
                        "1) 先用 EXPLAIN 看执行计划（是否走索引、扫描行数、type/ref）；2) 定位是否因未命中索引——检查 WHERE/JOIN/ORDER BY 列、函数包裹列导致索引失效、隐式类型转换；3) 针对大表/低区分度可加覆盖索引、复合索引调整顺序、避免 select *；4) 若索引已正确仍慢，考虑数据量过大做分页优化/归档、读写分离、缓存热点，或重写 SQL 去掉不必要的关联/子查询。全程以 EXPLAIN 的 rows 下降为目标验证。",
                        "以 EXPLAIN 为主线：先看有没有走索引，再看扫描行数，从 SQL→索引→结构/缓存逐级给出手段。",
                        List.of("慢查询", "EXPLAIN", "索引优化", "SQL优化")},
                new Object[]{"前端", 1,
                        "Vue 中 v-if 与 v-show 的区别？",
                        "v-if 是真正的条件渲染：为假时元素不渲染/销毁，切换有创建与销毁开销，适合低频切换；v-show 只是切换 CSS 的 display，元素始终在 DOM，初始就会渲染，适合高频切换但首屏有隐藏节点开销。",
                        "核心区别是“是否渲染到 DOM”：v-if 控制渲染，v-show 只控制显示。",
                        List.of("Vue", "条件渲染")},
                new Object[]{"前端", 3,
                        "浏览器输入 URL 到页面显示，中间发生了什么？（简要讲清关键环节）",
                        "1) DNS 解析域名到 IP；2) 建立 TCP 连接（HTTPS 额外 TLS 握手）；3) 发送 HTTP 请求，服务器处理后返回 HTML；4) 浏览器解析 HTML 构建 DOM，解析 CSS 构建 CSSOM，合成为渲染树；5) 遇到脚本默认阻塞解析（可 defer/async）；6) 布局(Layout)→绘制(Paint)→合成(Composite) 呈现；期间会并行加载静态资源、可能触发重排重绘。",
                        "按“网络层（DNS/TCP/HTTP）→ 解析构建（DOM/CSSOM）→ 渲染（布局/绘制）”三段讲，再补资源加载与阻塞即可。",
                        List.of("浏览器原理", "HTTP", "渲染流程")});
    }
}
