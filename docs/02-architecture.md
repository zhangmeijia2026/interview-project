# 02 · 总体架构

> 分层、模块、关键流程与横切设计。配套：`05`(后端工程)、`06`(接口)、`07`(LLM/RAG)。
> 最后更新：2026-09-07（R1–R10/P1–P7 收口 + **管理端改版（R11）**：用户管理/新增管理员/黑名单=停用即时生效/后台左侧栏；评分改参照参考答案口径）。

## 1. 架构总览

```
┌───────────────────────────── 浏览器 (Vue3 SPA) ─────────────────────────────┐
│  Element Plus · Pinia · Vue Router · axios · fetch(SSE) · ECharts          │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                  │ HTTPS REST (JSON) / SSE (text/event-stream)
                                  │ JWT: Authorization: Bearer <token>
┌──────────────────────────────────▼──────────────────────────────────────────┐
│ Spring Boot 3 (Servlet/Tomcat, JDK17)                                        │
│                                                                              │
│  表现层  Controller ─────────────── 统一响应 / 全局异常 / JWT 过滤器          │
│  应用层  ApplicationService(F01..F08 编排) + AIService(唯一LLM出口)          │
│  领域层  实体 + 状态机 + 匹配/面试/报告 领域逻辑                              │
│  基础设施  JPA Repository(MySQL8.3) · RedisTemplate · Tika · LocalFS · SSE   │
│           注册表 · 线程池Executor · DEEPSEEK(经Spring AI, OpenAI兼容)        │
└───────┬──────────────┬──────────────┬──────────────┬───────────┬────────────┘
        │              │              │              │           │
   MySQL8.3        Redis5.0       Apache Tika   本地文件系统   DeepSeek API
   (关系+JSON)      (会话/锁/        (PDF/DOCX)    (upload/)   (https://api.deepseek.com)
                   SSE注册表)
```

## 2. 分层与包结构（后端）

```
com.group5.interview
├─ InterviewApplication.java
├─ config/        SecurityConfig, WebMvcConfig, RedisConfig, AsyncConfig,
│                 ThreadPoolConfig, AiConfig, SseConfig, OpenApiConfig, UploadConfig,
│                 DataSeeder(R1 题库种子/管理员), SchedulingConfig
├─ common/        ApiResponse<T>, PageResult<T>, ErrorCode, BusinessException,
│                 FileResponse(文件流原样输出), GlobalExceptionHandler, constants/, utils/(JwtUtil, Md5Util)
├─ web/           通用 Web 装配
├─ security/      JwtAuthFilter, JwtProperties, JwtUtil, TokenStore(内存 token),
│                 UserContext(ThreadLocal), LoginLockService, AdminOnlyInterceptor(/api/admin/**)
│                 （R11：JwtAuthFilter/AdminOnlyInterceptor 每请求校验 is_active，
│                   停用/拉黑即时生效——登录/刷新也已过滤非启用账号）
├─ module/
│  ├─ auth/       注册/登录/刷新/登出/锁定（含 role）
│  ├─ user/       个人信息/改密/设置(模型、语言)、学习统计 UserStatsService
│  ├─ interview/  会话CRUD + 状态机 InterviewService（mode/weakBoost/practiceCategory）
│  ├─ resume/     上传/Tika/解析/预览确认 ResumeService + 保留库/复用/原文件流
│  ├─ jd/         JD 上传/粘贴/异步解析 JdService + JdAsyncProcessor + JdOcrService(图片OCR)
│  ├─ match/      MatchService(纯LLM匹配+面试重点，JSON 落 match_results)
│  ├─ engine/     EngineService(提问/评分/追问/流程)、SseEmitterRegistry、SseEvent
│  ├─ report/     ReportService(确定性聚合+评级+雷达+analysis_json/resume_advice_json)
│  ├─ history/    HistoryService(列表/删除/清空)
│  ├─ questionbank/  题库(R1)：BankService 分类/题目/管理模式 + BankUpsertRequest
│  ├─ weak/       薄弱技能库+错题本(R2)：WeakSkillService
│  ├─ feedback/   用户反馈(R4)：FeedbackService
│  ├─ compare/    简历对比(R3)：CompareService
│  ├─ admin/      管理端(R9–R11)：AdminUserController/Service(用户管理/查看某用户面试/只读报告)、
│  │              AdminAccountController/Service(新增管理员/停用=黑名单)、AdminOverviewController/Service(概览)、
│  │              AdminLlmService(调用统计/审计)、AdminRagController（题库/反馈管理端在 questionbank/feedback 包）
│  └─ settings/   杂项(模型列表等)
├─ ai/            AIService(唯一LLM出口) + DeepSeekAIService(真实, mock-enabled=false) +
│                 LocalMockAIService(离线确定性, true) + CostGuard + JsonSupport + LlmCallLogService
│                 rag/  R10：RagProperties, EmbeddingService, HashEmbeddingService(512维哈希),
│                       TextChunkService, RagInjector({knowledge}注入), RagStartupIndexer
│                 prompts/(resume-parse.md, jd-parse.md, match.md, focus.md,
│                          question.md, grade.md, follow-up.md, report-analysis.md,
│                          question-bank-gen.md, compare-resumes.md)
├─ repository/    JPA 仓库
├─ entity/        与 `04` 表一一对应
└─ exception/
```
> 当前口径（2026-09-07）：AIService **双模式**——`ai.mock-enabled=true`（默认，演示/CI）走 `LocalMockAIService` 确定性规则，无需模型 key；`false` 走真实 DeepSeek（`DeepSeekAIService`），任一步失败自动回退离线，输出同构。报告=确定性聚合 + 一次 `report-analysis`（真实/离线同构，`docs/07 §5`）。R10 起真实 DeepSeek 的 question/grade/follow-up 会注入本地向量检索 `{knowledge}`（`ai/rag/RagInjector`，默认 `provider=local`）。

## 3. 模块职责（映射 SRS §7.2）

| 模块 | 职责 | 关键类（落点） |
|------|------|----------------|
| 用户管理 | 注册/登录/JWT/锁定/改密/设置 | `module/auth`、`module/user` |
| 简历管理 | 上传、Tika、LLM 结构化、预览确认 | `module/resume` + `ai/AIService` |
| JD 管理 | 上传/粘贴、LLM 要求提取 | `module/jobdesc` |
| 智能匹配 | 纯 LLM 多维匹配、差距、面试重点 | `module/match` + `ai` |
| 面试引擎 | 状态机、逐题提问、评分、追问裁决、流程 | `module/engine` + `sse` |
| 报告生成 | 聚合、雷达、评级、建议、深度分析、简历修改建议 | `module/report` |
| 题库（R1） | 分类浏览、练习取题、管理 CRUD/AI 扩充 | `module/questionbank` |
| 薄弱库/错题本（R2） | 低分技能聚合、标记掌握、错题回溯 | `module/weak` |
| 简历对比（R3） | 两份简历 AI 对比 + 历史 | `module/compare` |
| 用户反馈（R4） | 提交/我的反馈/后台处理（后台列表仅显示普通用户提交，隐藏管理员账号自己提交的行，2026-09-07） | `module/feedback` |
| 管理后台（R9–R11） | 用户管理+查看每用户面试+新增管理员、黑名单=账号停用(即时生效)、概览统计、LLM 调用统计/审计、题库/反馈管理、RAG 重建 | `module/admin` + `security/AdminOnlyInterceptor` + `ai/LlmCallLogService` |
| 持久层 | JPA → MySQL | `repository` + `entity` |
| 缓存/会话 | Redis：状态缓存、登录锁、refresh、SSE 注册表 | `config/RedisConfig` + `sse` |
| AI 出口 | 双模式 AIService、Prompt、结构化输出、成本护栏、调用审计 | `ai/*` + `ai/rag/*`（**业务层禁止直连 ChatClient**） |

## 4. 关键流程与时序

### 4.1 注册/登录
```
POST /api/auth/register → 校验码比对(开发期内置开关) → bcrypt 存库
POST /api/auth/login    → 查用户 → 校验密码 → 失败计数入 Redis(5次/15min锁)
                        → 成功签发 access(2h)/refresh(7d) → refresh 哈希存 Redis
请求鉴权: JwtAuthFilter 校验 Bearer → 注入 UserContext；登出时 access 加入 Redis 黑名单至过期
```

### 4.2 上传简历→解析（F02/F03）
```
创建面试(draft) → 上传简历(resume_uploaded)
  POST /api/interviews/{id}/resume (multipart ≤10MB)
   → 存本地文件 + MD5(去重) → 行状态 processing
   → @Async 异步管线: Tika 提取文本 → AIService 结构化(JSON) → 存 parsed_data → 状态 ready
   → 失败置 failed，可重试
  → GET 进度(或面试级 SSE) 供前端步骤条(上传→提取→AI解析→完成)
  前端预览 parsed_data，用户确认 → PUT 确认 → 状态 resume_uploaded(语义: 简历已定稿)
```

### 4.3 JD + 匹配 + 面试重点（F04）
```
上传/粘贴 JD(jd_uploaded) → Tika(文件)或原文 → 异步 LLM 分析要求 → 存 parsed_data
→ 调 MatchService(纯 LLM): 入参=简历 parsed_data + JD parsed_data
  输出 JSON: overall/skill/experience/education 匹配分 + gap_analysis[] + interview_focus[] (默认10)
→ 存 match_results → 面试状态 matched/ready
→ 前端展示 P06（环形匹配度 + 维度条 + 差距 + 重点），可“重新生成重点”
```

### 4.4 AI 面试循环（F05，核心）
```
用户点开始(in_progress)
for i in 1..N(question_count, 默认10):
  提问: AIService.chatStream(prompt=question) → 经 SSE 逐字推给前端(首token≤5s)
  用户提交回答(POST answer)
  点评: AIService.structured(grade) → 分数/优点/不足/建议/是否追问
        → 存 interview_questions(i) → SSE 推送点评
  若“适合追问”(且追问次数<上限, 默认每题≤1):
     追问问题推给前端 → 用户选“回答/跳过”(P1可后置, 但接口留好)
  进度 +1; 断线恢复: 读 interviews.completed_question_count 从未答题继续
N 题完成 → 状态 completed → 触发报告生成
```

### 4.5 报告生成（F06）
```
聚合 interviews+match_results+interview_questions → 确定性评级(S/A/B/C/D) + 六维雷达 + 建议（MVP 不调模型，见 `07` §5）
评级 = 0.4×match.overall + 0.6×各题均分 → S/A/B/C/D（阈值 85/75/65/55）
雷达图六维 = 岗位匹配(match.overall)/专业(均分)/表达/逻辑(篇幅与结构化代理)/应变/学习(追问作答情况) → 前端 ECharts 绘制
报告 JSON 落库 interview_reports → 历史页可见
```

## 5. 面试状态机（与 `04` 一致；R7 起含模式）

```
draft ──传简历──▶ resume_uploaded ──传/贴JD──▶ jd_uploaded ──匹配完成──▶ matched
   matched ──用户确认重点/开始──▶ in_progress ──答完N题──▶ completed
       任意非终态 ──用户放弃/超时──▶ abandoned
resume 解析中: processing/failed 为简历子状态，不入 interviews.status
继续面试: completed_question_count 断点续做（恢复 to in_progress）
```
- **mode=formal**（默认）：按上面走 简历→JD→匹配→答题。
- **mode=practice**（R7 练习模式）：创建后可跳步骤直入 `in_progress`，题目来自题库抽题（可指定 `practice_category`/`weak_boost`），不要求简历/JD/匹配；报告为"复盘"形态（无评级/匹配分，见 `06` F06）。
- 状态迁移在 Service 层逐端点做**显式前置校验**（如 `InterviewService`：仅 `DRAFT` 且已有 resume 才能 `RESUME_UPLOADED`；仅 `RESUME_UPLOADED` 才能 `JD_UPLOADED`；`MatchService`/`EngineService`/`ReportService` 各自校验当前态），非法/越序迁移抛业务异常或 409。`InterviewStatus` 枚举还带 `canTransitionTo`（目前仅定义"任意非终态→ABANDONED"规则，尚未在业务点调用），`status` 均来自 `InterviewStatus.from(...)` 统一解析。

## 6. SSE 设计（流式打字机 + 进度）

- **通道**：`GET/POST /api/interviews/{id}/events` → `text/event-stream`，由 `SseEmitterRegistry` 维护 `map<interviewId, SseEmitter>`（会话级）。
- **事件帧**格式与**事件类型以 `06` §3 为准**（MVP 实际推送：`question/grade/followup/report_done/done/ping`）。
- **实现要点**：SseEmitter 注册在请求线程，事件推送放业务线程；前端 fetch + ReadableStream 解析（POST 型 SSE，见 `08`）。心跳 15s。
- LLM 流式经 `AIService.chatStream(...).subscribe(delta -> sink.send("delta", ...))`，**注意订阅不阻塞 Tomcat 线程**（用独立 executor，见 `05` §7）。

## 7. 事务、线程与并发（JDK17，无虚拟线程）

| 关注点 | 决策 |
|--------|------|
| LLM 耗时调用 | 一律不在事务内同步跑。耗时管线(`resume/jd 解析`)用 `@Async`（独立 `TaskExecutor`），DB 状态先置 `processing` |
| 面试评分 | 同步请求内跑 LLM(≤4s 目标)，写库用短事务包 Repository 层 |
| 长事务 | `@Transactional` 只包写库；不要在事务里调 DeepSeek |
| 50 并发 | Tomcat `server.tomcat.threads.max=200`(默认够)；面试接口 + `RateLimitInterceptor`(每用户10/min) |
| SSE 推送线程 | 注册表线程安全用 `ConcurrentHashMap`；SseEmitter 发送异常→清理注册表 |
| 异步线程池 | 核心 8/最大 16/队列 200；`@Async("taskExecutor")` |

## 8. RAG 落点（R10 已落地；MVP 纯 LLM 为历史口径）

- 早期（MVP，2026-09-03）检索即"面试重点 + 逐题上下文"全部来自**结构化 JSON**（简历/JD 经 LLM 提纯），无向量检索。R10（2026-09-07）在 `ai/rag/*` 落地**本地向量检索**：简历/JD ready 与题库增改删/生成/启动/`POST /api/admin/rag/rebuild` 时写 `text_chunks`（按 ≤600 字分块，`HashEmbeddingService` 512 维特征哈希，无外部模型）。
- 真实 DeepSeek 的 question/grade/follow-up 调用前，`RagInjector` 取"当前用户本人简历/JD + 全局题库知识点"做 Top-k（默认 4，local=MySQL 余弦 / qdrant=真向量库镜像），命中块以 `{knowledge}` 注入 prompt（带"不得编造语料外事实"约束，见 `07` §8）。默认 `provider=local`；真 Qdrant（`QdrantClient`，2026-09-07 已连）经 `RAG_PROVIDER=qdrant` 切，缺服务自动回退 local（见 `07` §11）。
- **架构上只改了 `ai/` 与少量 Service 接线，对外 REST 接口不变**（新增运维端点 `GET/POST /api/admin/rag/*`）；离线引擎不注入 `{knowledge}`，双模式输出同构。
