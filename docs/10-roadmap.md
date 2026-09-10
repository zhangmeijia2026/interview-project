# 10 · 交付路线图（Phase 0–8，step-by-step）

> 主推进文件：**每天开工前读"当前阶段"，收工前更新 `# 进度状态` 小节**。
> 每个 Phase 有：产出物 / 验收口径 / 给 Claude Code 的指令模板 / 里程碑演示。
> 除明确标注外，所有 "告诉 Claude Code" 的话都粘贴到 **interview 目录**（或对应 backend/、frontend/ 子目录）的 Claude Code 会话里。
> 时间按每周约可投入 8–12h 估（16 周口径，可压缩；组内 4 人可并行前后端）。

## 进度状态（每天更新）

- [x] Phase 0 环境与仓库　（2026-09-02：git init；interview_system 建库成功，9 张表；schema.sql 在 backend/db/）
- [x] Phase 1 后端骨架 + 数据库 + 横切　（2026-09-02：Boot 3.5.16 编译+启动 2.7s；/api/ping 200；无 token 401；swagger 200；JWT 基建就绪）
- [x] Phase 2 F01 用户认证　（2026-09-03：注册/登录/刷新/登出/资料与改密接口完成；bcrypt cost=12；Redis 5 次失败锁 15 分钟；本机冒烟通过）
- [x] Phase 3 F02/F03 面试会话 + 简历解析　（2026-09-03：会话 CRUD、状态机、PDF/DOCX 上传、Tika 异步提取、AIService 结构化解析与确认接口完成；E2E 实测通过——上传 backend/db/sample_resume.docx 约 2s 解析为 ready，抽出 姓名/邮箱/手机/技能，confirm 后会话转 resume_uploaded，列表过滤/删除/401/越权守卫/格式守卫全绿）
- [x] Phase 4 F04 JD + 匹配 + 面试重点　（2026-09-03：JD 上传/粘贴/异步解析、match+focus JSON 落 match_results、接口全通；E2E 里简历×JD 匹配 overall=84，gap/focus 可展示/可重生成）
- [x] Phase 5 F05 AI 面试引擎（核心）　（2026-09-03：EngineService + SSE 注册表就绪；**MVP 采用「同步核心 + SSE 广播」**：answer 直接同步返回评分/追问/下一题（便于 curl/最小前端），事件格式与 docs/06 §3 一致；3 题全程跑通含追问，中断可断点续传）
- [x] Phase 6 F06 报告 + F07 历史 + F08 设置　（2026-09-03：ReportService **确定性聚合**评级/六维雷达（无需 report.md，见 docs/07 §5「R8 不脑补」口径）；history 列表/详情/单删/清空；/api/settings/models；实测完成一场后报告 rating=B、dims 六维齐、逐题回放完整）
- [ ] Phase 7 前端完整对接 + 联调打磨　← 当前阶段
  （2026-09-03：已完成**最小前端联调**——frontend/ Vue3+Vite+TS+Element Plus+ECharts，登录/注册→建会话→简历→JD→匹配→逐题作答+追问→报告(雷达)→历史可看可删，构建通过、dev+代理通 /api/ping 200；**待做**：SSE 打字机、P01–P11 全页面、错误/断网降级打磨）
  （2026-09-03：追加打磨——每题作答后先展示「AI 反馈」（回答分析+对简历的修改建议 resumeAdvice）再进下一题；面试中可「返回首页」；历史记录里未完成会话可「继续面试」断点续答；修复二次登录后历史报告内容空白的问题。后端 interview_questions 增 resume_advice 列，见 04/06/07。）
- [x] 增强① DeepSeek 真实接入（2026-09-05）　双模式 `ai.mock-enabled`：默认离线确定性引擎 / false 走真实 `deepseek-chat`（Spring AI **1.1.8** `starter-model-openai`）；`OFFLINE_KEYS=[resume-parse,jd-parse]` 硬路由本地、不烧额度；`CostGuard` 日预算 ¥2 超限自动回落；`JsonSupport` 剥围栏+尾逗号修复。见 `docs/05 §5`、`docs/07 §10`。
- [x] 增强② JD 图片 OCR + 本地关键词（2026-09-05）　JD 上传白名单扩到图片(jpg/jpeg/png/bmp/webp)；`JdOcrService`(Tess4J `chi_sim+eng`，自带 Windows 原生库，数据在 `backend/ocr-tessdata/`)；OCR 失败 → JD 行 failed 提示改粘贴文字。
- [x] 增强③ 简历保留库（2026-09-05）　`GET /api/resumes` 列出本人 ready 简历；新建面试可选「上传新简历 / 从历史简历复用」，复用=`bind+confirm` 免重新解析（`POST /api/interviews/{id}/resume/reuse`）。
- [x] 增强④ 深度报告分析 + 详细简历修改建议（2026-09-05）　`interview_reports` 增 `analysis_json`/`resume_advice_json`（schema.sql+实体+ALTER 已同步）；`ReportService` 落库后调一次 `report-analysis`（仅 analysis_json 为空才生成），真实/离线同构，历史旧行读取时懒补。
- [x] 增强⑤ 前端多页面重构（2026-09-05；**2026-09-07 大扩展 P7 收口**）　vue-router 多页面 + 顶部导航栏 + 答题独立页 `/interviews/:id/engine` + 简历复用/上传二选一 + JD 文字/图片(OCR)双 Tab + 报告页「详细分析+简历修改建议+完整逐题回放」；P7 再扩展题库/薄弱库/错题本/个人中心/反馈/简历对比/管理后台等页面（见下"大型扩展 P1–P7"）。
- [x] 增强⑥ 测试与文档同步（2026-09-05；2026-09-07 收尾）　后端单测已全绿（含 report-analysis 离线同构）；`docs/07、03、05、04、06、08`、本文档与 CLAUDE.md 技术基线均已同步至 R1–R10/P1–P7 口径。
- [x] 增强⑦ E2E 验收 + 真实 AI 冒烟（2026-09-05）　离线全链路 HTTP 冒烟 **ALL PASS**（注册→建会话→简历解析→简历库/复用免上传→JD 本地提词→匹配→2 题作答含追问→报告深析 A/B 块+逐题回放→历史→级联清理）；前端 `npm run build` 通过。**真实 DeepSeek 冒烟 PASS**：api.deepseek.com 当日可达，`ai.mock-enabled=false` 起 8091 实调 match/grade/report-analysis 均出自然语言结果；过程中发现并修复 **R8b 被全局 `max-tokens:1500` 截断成非法 JSON → 真实模式静默回退离线** 的缺陷（上限提到 4000，docs/07 §2/§10 记录），复测无回退。演示口径不变：本机默认 mock，`AI_MOCK_ENABLED=false`（或改 `ai.mock-enabled`）即切真实 DeepSeek。
- [x] **扩展 P1 题库 + 管理模式（R1）**（2026-09-07）　question_bank 表/实体、`DataSeeder` 种子题、`GET /api/bank/categories|questions`、管理端 CRUD + `POST /api/admin/bank/generate`（AI 一键扩充，question-bank-gen prompt）。
- [x] **扩展 P2 个人薄弱技能库 + 错题本（R2）**（2026-09-07）　user_weak_skills 表、报告/题目低分聚合、`GET /api/weak-skills`、`GET /api/weak/wrong-questions`、`POST /weak-skills/{id}/resolve`。
- [x] **扩展 P3 个人中心统计 + 历史 JD/简历保留复用 + 前台收纳**（2026-09-07）　`GET /api/user/stats`（8 统计字段）、`/api/resumes`、`/api/jds` 保留库 + `/{id}/file` 原文件流 + 新建面试复用（resume/jd reuse）。
- [x] **扩展 P4 用户反馈（R4）**（2026-09-07）　user_feedback 表、`/api/feedback` 提交与我的列表、`/api/admin/feedback` 分页/回复。
- [x] **扩展 P5 简历对比（R3）**（2026-09-07）　resume_comparisons 表、`/api/compare/*`（compare-resumes prompt，离线同构）、前端 ResumeCompareView。
- [x] **扩展 P6 RAG（R10）**（2026-09-07）　text_chunks 启用（owner 可空=全局知识）、`ai/rag/*`（RagProperties/HashEmbeddingService 512维/TextChunkService/RagInjector/RagStartupIndexer）；resume/jd ready + 题库增改删/生成 + 启动 + `POST /api/admin/rag/rebuild` 写索引；question/grade/follow-up 模板加 `{knowledge}`；`/api/admin/rag/stats`+`/rebuild`；docs/07 §8/§11、04 §6 更新。
- [x] **扩展 P7 前端全量页面 + 前后端契约收口（2026-09-07）**　题库浏览/练习、薄弱库/错题本、个人中心、反馈、简历对比、历史 JD/简历复用、管理后台（概览/题库/反馈/LLM 统计）全部页面就位；`docs/06/08` 按代码校准 8 处契约口径（分页两类、question_type=qa、user.role、/user/stats 字段、fallbackRate=百分数、history/{id}=ReportResponse、文件流 blob、practice 报告无评级）。
- [x] **扩展收口验收（2026-09-07）**　后端编译+单测通过；前端 `npm run build` 通过；docs/04/05/06/07/08、CLAUDE.md 与代码一致（一致性验收代理本轮完成，仅文档修订与问题点名，无代码改动）。
- [x] **Qdrant 真连接 + 题库批量扩充（2026-09-07 晚）**　本机 `qdrant.exe` v1.19.1 起于 `:6333`，后端新增 `ai/rag/QdrantClient`（JDK HttpClient+Jackson 零依赖）：集合 `interview_chunks` 512/Cosine 自动建，`provider=qdrant` 时 MySQL 写库后镜像 upsert/删除、`rebuild` 整库重建；检索走 Qdrant，缺服务/空集自动回退 local（已实测停库→`provider=local`→重启即恢复）。`RagInjector` 统一检索入口 + `GET /api/admin/rag/search`、`/qdrant/health`（验收 `provider` 字段）；修复语料为空时 `{knowledge}` 残留隐患。题库 `BankService.generate` 严谨化：题干≥12 字/答案非空校验 + 归一化去重（等值/一方含另一方且较短≥较长 80%）。**真实 DeepSeek 批量扩充 8 类共 +112 题**（0 重复 0 残缺，llm/calls 24 次 fallback=false），题库 8 类合计 120 题、Qdrant 镜像 370/370 同步。docs/07 §8/§11、04 §6、05 §9、06 管理端段、本文、CLAUDE.md 已同步。
- [ ] **R11 管理端改版 + 评分参照参考答案（2026-09-07）**　后台改左侧栏控制台（概览/用户管理/黑名单管理/题库管理/反馈处理/调用统计），管理员登录直达、用户界面不变；用户管理内"查看每用户面试/只读报告" + 新增管理员；黑名单=`users.is_active=false` 停用并**逐请求即时生效**（守卫：不停自己/不停最后启用管理员）；反馈列表仅显示普通用户；评分改**参照参考答案要点覆盖度**（离线 bigram 重合 + grade.md 语义锚，`ReferenceAwareGradeTest` 已绿）。后端接口已完成（`/api/admin/stats/overview`、`/accounts/{id}/active`、`/accounts/admins`、`/users/{id}/interviews[/…/report]`，users 列表支持 role/active 过滤）。docs/02/04/05/06/09 已同步；前端/构建/联调收尾后勾选。
- [ ] Phase 8 测试验收 + 文档 + 答辩

> 读到这里时，"当前阶段" = 第一个未勾选项。本节内把勾选状态顶格更新即可。
> ⚠️ 运行口径（2026-09-05 更新）：`ai.mock-enabled=true`（默认）走离线确定性引擎（LocalMockAIService，无 key 也可全链路演示/CI）；`false` 走真实 DeepSeek，任一步失败自动回退离线，输出同构界面不崩。R1 简历解析/R2 JD 解析/岗位关键词 一律本地算法，不消耗额度。报告=确定性聚合 + 一次 `report-analysis` 深度文本（R8b，见 `docs/07 §2/§5`）。

---

## Phase 0 · 环境与仓库（0.5–1 天）

**产出**：`interview/` 完成 `git init`；三个子目录就位；本机 MySQL 建库；所有工具命令跑通。
**动作**（照抄 `03` §3 初始化步骤）：
```
git init
mkdir backend frontend   # docs/ 已存在
```
**告诉 Claude Code**：`按 docs/03-environment.md 核对并写通本机初始化清单，生成 backend/db/schema.sql（来自 docs/04 §4），并跑一遍 SHOW TABLES 确认可建库。`
**验收**：`mvn -v`、`java -version`、`mysql -u root -p`、`redis-cli ping` 全过；schema 可执行。

## Phase 1 · 后端骨架 + 横切（2–3 天）

**产出**：`backend/` Spring Boot 工程跑起 `/api/ping`；JWT 基建；统一返回/异常；配置多 profile；日志 requestId。
**告诉 Claude Code**：
```
在 backend/ 按 docs/05 建工程（start.spring.io 参数见 docs/03 §4）。
先做可运行最小集：application.yml(+local)、ApiResponse/ErrorCode/GlobalExceptionHandler、
JwtUtil+JwtAuthFilter+UserContext、配置类、/api/ping。不接业务表逻辑。
```
**验收**：`mvn spring-boot:run` 启动 ≤10s；swagger-ui 可开；`/api/ping` 通；未带 token 访问 `/api/user/me` 返回 401。

## Phase 2 · F01 用户认证（2–3 天）

**产出**：注册/登录/刷新/登出/改密；登录锁定；Redis 接入。
**告诉 Claude Code**：`实现 docs/06 中 F01 全部接口，按 docs/04 users/user_settings 建实体。校验码用 app.verify-code 固定值（开发期）。密码 bcrypt≥12。失败 5 次锁 15 分钟（Redis）。` 
**验收**：curl/前端可注册登录拿到 token；错密码 5 次锁定；`09` §4 对应单测绿。

## Phase 3 · F02/F03 面试会话 + 简历解析（3–4 天）

**产出**：创建面试、状态机；上传 PDF/DOCX → Tika → LLM(resume-parse) → 预览确认。
**依赖**：先把 `resources/prompts/resume-parse.md` 按 `07` §4 写出来。
**告诉 Claude Code**：`实现 interviews CRUD + 状态机与 resumes 上传/异步解析/确认接口（docs/06 F02/F03）。Tika 提取→ AIService.structured(resume-parse)→parsed_data。异步用 @Async，进度三态 status。` 
**验收**：上传 `sample_resume.pdf` 15s 内出结构化；格式错误/超 10MB 友好报错；状态机迁移正确。

## Phase 4 · F04 JD + 匹配 + 重点（2–3 天）

**产出**：JD 上传/粘贴解析；纯 LLM 匹配；面试重点生成与展示/重生成。
**依赖**：`prompts/jd-parse.md、match.md、focus.md`（`07` §3/§4）。
**告诉 Claude Code**：`实现 jd 接口与 match（docs/06 F04），MatchService 按 docs/07 §3 纯LLM打分 + 差距 + 重点，JSON 落 match_results。` 
**验收**：P06 数据齐全且分数有 evidence（抽查 1 份简历×1 份 JD 人工读一遍匹配理由是否合理）；重新生成可用。

## Phase 5 · F05 AI 面试引擎（核心，4–6 天）★

拆两步并行可控：
- **5a 引擎后端**：状态机进入 in_progress、逐题出题(流式)、作答落库、评分(grade)、追问决策、断点恢复。
- **5b 流式通道**：SSE 注册表 + 事件流（`06` §3），`question_delta/grade/followup` 推送。
**依赖**：`prompts/question.md、grade.md、follow-up.md`。
**告诉 Claude Code**：`实现 InterviewEngine + SseEmitterRegistry（docs/05 §7/§8，docs/06 F05）。先做可离线演示版：grade 用打桩也行，但接口与事件格式必须与 docs/06 一致。` 
**验收**：10 题全程可跑；首字 ≤5s；点评含分+优缺点+建议+追问；中断再进从断点续；`09` §2 状态机/评分测试绿。

## Phase 6 · F06 报告 + F07 历史 + F08 设置（3–4 天）

**依赖**：`prompts/report.md`（`07` §5）；grade 时把六维 sub 一并存好再聚合。
**告诉 Claude Code**：`实现 ReportService（docs/07 §5 评级/雷达口径）、history 接口（docs/06 F06/F07）、user settings 与模型列表。` 
**验收**：完成一场面试后报告页六维雷达 + 评级 + 逐题回放完整；历史可搜/可删；切换语言/模型设置生效（模型目前只 DeepSeek）。

## Phase 7 · 前端完整对接 + 打磨（并行启动，2–3 周）

**产出**：Vue3 全部页面（P01–P11），路由守卫、axios、SSE 打字机、ECharts 全部打通。
**告诉 Claude Code**：`按 docs/08 建前端工程并实现 P02→P04→P05→P06→P07→P08→P09/P10/P11 主链路，与后端联调（前后端并行时可先跑 mock）。` 
**验收**：`09` §5 黄金路径 1–8 全通；断网/错误提示友好；UI 对齐 SRS 主色与交互。

## Phase 8 · 测试验收 + 文档 + 答辩（1 周收尾）

- 全量跑 `09` §4 traceability 与 §5 黄金路径，记录耗时自证 SRS 指标。
- 补 README（运行步骤、默认账号演示稿）、答辩 PPT 材料清单、可能的 Dockerfile（加分项）。
- 代码走查：`05` §9 清单逐项核对。
**告诉 Claude Code**：`按 docs/09 跑验收，生成《验收记录.md》到 docs/，标注每项 SRS 指标实测值；检查并修复 CLAUDE.md/文档与代码不一致处。` 
**验收**：答辩前一天做完整演练 3 次（含断网降级、额度提示等彩排）。

---

## 附录 A · 每 Phase 通用节奏（给 Claude Code 会话的固定开场）

> "本次任务是 docs/10 的 Phase N。<贴 Phase 指令>。开始前先读 CLAUDE.md 与相关 docs（XX），
> 按 05/06/07 规约实现，本阶段完成后跑对应测试并更新 docs/10 进度状态，向我汇报验收点。"

## 附录 B · 里程碑演示口径

| 里程碑 | 演示点（答辩话术锚） |
|--------|----------------------|
| M1(Phase2) | 注册→登录→JWT 刷新→锁定策略 |
| M2(Phase4) | 上传真实简历+目标 JD → 10s 内给出匹配度与"为什么会扣分"的 evidence |
| M3(Phase5) | 流式面试首字 <1s 打字机、评分+追问闭环、中途退出恢复 |
| M4(Phase6) | 完整报告六维雷达 + 评级 S/A/B/C/D + 提升建议 |
| M5(Phase8) | 黄金路径一气呵成 + 性能指标自证表 |

## 附录 C · 分工建议（4 人）

- A/B：后端 Phase1–6（A：认证/简历/JD；B：匹配/引擎/报告）
- C：前端 Phase7（与 A/B 并行，先按 06 mock 联调）
- D：Prompt 与测试（写 `07` 各模板 + `09` 用例 + 验收记录），串起里程碑演示与答辩材料
> 任何接口/表结构改动都同步更新 `06`/`04`——这是四人的共同锚点。
