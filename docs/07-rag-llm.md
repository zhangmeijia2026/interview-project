# 07 · LLM / RAG 设计（DeepSeek 双模式 + R10 向量检索：local 与真 Qdrant 均已落地）

> 本文是"AI 层"的唯一权威：每个业务步骤用哪个模型、什么 Prompt、输出什么 JSON、怎么控成本、防幻觉。
> 配套：`02`(落点)、`05`(AIService)、`06`(接口)。最后更新：2026-09-07（§8/§11 已并入 R10 落地，§1/§6 同步校准）。

## 1. 结论先行（已裁决）

- **运行时模型**：DeepSeek `deepseek-chat`（OpenAI 兼容，经 Spring AI **1.1.8** starter-model-openai）。默认温度与 max_tokens 见 §2。
- **双模式接入（2026-09-05 落地）**：`ai.mock-enabled` 默认 `true` 走离线确定性引擎（无 key 也能全链路演示/CI）；置 `false` 走真实 DeepSeek；真实调用任一步失败（未配 key/超预算/网络/JSON 解析失败且重试失败）**自动回退离线**，输出结构一致，界面不崩。实现：`ai/LocalMockAIService`、`ai/DeepSeekAIService`、`ai/JsonSupport`、`ai/CostGuard`。
- **R1 简历解析 / R2 JD 解析一律本地算法（团队决策，降本且可测）**：无论 mock 与否都走离线实现（`DeepSeekAIService.OFFLINE_KEYS` 硬路由），不消耗模型额度。
- **MVP（截至 2026-09-03）= 纯 LLM**：匹配/出题/评分/报告读"结构化 JSON + 必要原文"，当时不做向量检索。语义来源=LLM 对简历/JD 的结构化提纯，检索范围=用户自己的这组数据 → 天然无跨用户污染。
- **R10（2026-09-07）向量检索已落地**：`text_chunks` 启用（简历/JD/题库知识点），**512 维哈希向量**（`HashEmbeddingService`，无外部模型）+ Top-k 召回 → 把命中块以 `{knowledge}` 注入 **question/grade/follow-up** 的 prompt（真实 DeepSeek 路径，见 §8）。**provider=local（MySQL 余弦，默认/CI）与 provider=qdrant（真 Qdrant 镜像，2026-09-07 本机已连）均可用**，缺 Qdrant 服务自动回退 local（§11）。
- DeepSeek **无 embedding 端点**，故 R10 默认用**本地特征哈希向量**（确定性、可离线、零成本）；未来换更强 embedding（如 bge）只需在 `ai/rag/` 新增 `EmbeddingService` 实现，业务与契约不动（见 §8/§11）。

## 2. LLM 调用点总表（一期）

| # | 步骤 | 触发 | 方式 | 温度 | max_tokens | 输出 |
|---|------|------|------|------|-----------|------|
| R1 | 简历解析 resume-parse | 上传后异步 | **本地算法（不调模型）** | - | - | 结构化 JSON（`parsed_data`） |
| R2 | JD 解析 jd-parse | 上传/粘贴后异步 | **本地算法（不调模型）** | - | - | 结构化 JSON（`parsed_data`） |
| R3 | 匹配+差距 match | 点"开始匹配" | 非流式 | 0.2 | 2500 | JSON（overall/维度/gap[]） |
| R4 | 面试重点 focus | 匹配后 | 非流式 | 0.4 | 2000 | JSON（focus[10] 方向+考察点+准备） |
| R5 | 出题 question | 每题开始 | **流式** | 0.7 | 800 | 文本（模拟面试官口语） |
| R6 | 点评 grade | 每题作答后 | **流式**+末尾JSON | 0.3 | 1500 | 文本正文 + JSON（分数/优点/不足/建议/是否追问/**对简历的修改建议 resumeAdvice**） |
| R7 | 追问 follow-up | grade 判定需要 | 非流式 | 0.7 | 500 | 文本追问 |
| R8 | 报告聚合 report | 全部完成 | 本地确定性聚合 | - | - | 评级/六维/优点/建议（落 `interview_reports`） |
| R8b | 深度报告+简历建议 report-analysis | 报告落库后 | 非流式 | 0.3 | 1800 | JSON（总评/六维点评/结论 + 分组简历建议），失败回退离线同构 |

> 流式主要用于打字机效果（R5/R6）；R6 推荐"流式正文 + 结尾单独 structured() 拿分数 JSON"，两个独立调用避免解析流式半截 JSON。
> ⚠️ max_tokens 口径（2026-09-05 真调冒烟修正）：代码统一走 `spring.ai.openai.chat.options.max-tokens` **全局上限 4000**（见 §10），本表"max_tokens"列=该步预期输出量、非逐调用硬上限。原 1500 会把 R8b 约 2–3k token 的长 JSON **截断成非法 JSON** → 真实模式静默回退离线（已修）。

## 3. 纯 LLM 匹配方案（R3，核心）

**思想**：不用向量算相似度，而是把"简历 vs JD"交给模型按维度逐一裁决。对毕业设计语料（一份简历对一份 JD）它比向量更稳、更可解释，且天然带差距理由。

**输入**（拼进一次 Prompt）：
- 简历 `parsed_data`（完整结构化：教育/经历/项目/技能）
- JD `parsed_data`（硬技能/软技能/经验/学历/职责）
- 匹配评分基准提示（定义"技能匹配=JD 硬技能在简历中出现或等价"等）

**输出 schema（强制 JSON）**：
```jsonc
{
  "overall": 0, "skill": 0, "experience": 0, "education": 0,   // 0-100 整数
  "gap": [ {"dimension":"skill|experience|education","item":"缺失/薄弱点","severity":"HIGH|MED|LOW","evidence":"JD哪里要求 / 简历哪里缺"} ],
  "summary": "一段总体结论"
}
```

**打分护栏（写在系统提示里，防模型手松/手紧）**：
1. 分数必须**可归因**：每一项要有 evidence，无证据项不计分。
2. 相对标杆锚定：给出"满分类需完全满足 JD 全部硬技能且年限≥要求"的语义锚，让 70 分=合格线、90 分=几乎全满足，避免全部集中在 80±10。
3. 学历缺失/学历要求不满足时 `education` ≤ 60 且 gap 必出 HIGH 项。
4. 输出只允许一个 JSON 对象，无前言后语（JSON mode 辅助；最终以 `JsonSupport` 容错为准）。

**匹配分如何转"面试重点"(R4)**：把 R3 的 `gap[]`（尤其 HIGH/MED）作为 R4 的唯一输入源，让模型据此生成题目方向——这就是"差距驱动的个性化题库"，也是整个系统的差异化卖点。R4 输出：
```jsonc
{ "focus": [ {"index":1,"direction":"…","examinePoint":"考察…","prepare":"准备建议…"} ] }  // 默认10条
```

## 4. Prompt 工程体系（模板落点 `resources/prompts/*.md`）

**统一结构（每个模板都遵守）**：
```
[角色]   你是一位资深{简历解析|岗位分析师|招聘评估专家|面试官|面试评估官}…
[任务]   基于给定输入，产出{结构化JSON|面试问题文本}
[输入]   ===简历结构化数据=== / ===JD结构化数据=== / ===用户回答===
[评分锚/规则]  （仅打分任务）见 §3
[输出约束] 只输出合法 JSON（无 markdown 代码块），字段/取值范围如下:
          <给出 JSON Schema 或示例对象>
```

**防幻觉三原则**（写进所有含"简历事实"的模板）：
1. **不臆造**：模型中未出现的经历/技能不得出现在优点、匹配证据里。
2. **出处在上下文中**：凡是"你提到/你做过 X"，X 必须能在提供的 resume 原文或 structured 里找到。
3. **无信息时明说**：找不到就输出 `"note":"简历未体现该维度"`，而非猜一个分数。

**典型模板示例（resume-parse，示意即可，完整版写进模板文件）**：
```
[系统] 你是一位专业的简历解析专家。请从简历原文中提取结构化信息。
只输出一个合法 JSON 对象（不要代码块标记、不要解释）。
schema:
{
 "name":"string|null","email":"string|null","phone":"string|null",
 "education":[{"school":"string","degree":"string","major":"string","period":"string"}],
 "experience":[{"company":"string","title":"string","period":"string","summary":"string","tech":["string"]}],
 "projects":[{"name":"string","period":"string","description":"string","role":"string","tech":["string"]}],
 "skills":[{"name":"string","level":"string|未知"}]
}
约束：skills.level 只允许 精通/熟练/了解/未知 四值；无法识别写 null，禁止编造。
[输入] ===简历原文=== ...（≤约8000字符，超出先截断到最近标题边界）
```
（resume 原文若超长：Tika 抽取后先按"教育/工作/项目"标题切成 3–4 段，分段解析再合并，可降低漏字段。二期可换向量化分块。）

**grade 模板要点**（R6）：输入=当前题面、考察点、用户回答、前面 2 题历史（保一致性）；要求点评正文控制在 3–6 句；分数给出后落到 `suggestFollowup` 决策：回答空泛/有明显缺口 → true；答得充分 → false。输出另含 `resumeAdvice`（结合本题暴露的薄弱点与简历现状，给出一条"简历如何改/补更能命中目标 JD"的建议，落 `interview_questions.resume_advice`，报告逐题回放透出）。

## 5. 报告 / 雷达 / 评级（R8）

- **评级规则**：综合 `match.overall` 与 各题 `score` 均值加权（建议 4:6），映射 `S≥85 / A≥75 / B≥65 / C≥55 / D<55`；评级标签展示：S 卓越/A 优秀/B 良好/C 待加强/D 薄弱。
- **六维雷达**（`interview_reports.dimensions`，0-100）映射口径：
  - job_match ← match.overall
  - professional ← 题目含"专业/技术考察点"的均分
  - expression ← 作答文本长度与通顺度代理（一期由 grade 模型顺带输出 `expressionSub`，否则用人工归一：mean(answer 字数区间)）
  - logic ← 模型对逻辑维度的 sub 评分累加
  - adaptability ← 追问回答得分占比
  - learning ← 所有"不会→后答出/补救"信号（一期简化：追问分数）
- 实现建议：grade R6 顺带返回 6 个 0-100 的子维度，报告直接聚合平均——**不要在 R8 重新脑补**，否则两次调用对同一作答给分会漂移。
- R8 只做三件事：读全量数据 → 写"匹配度回顾 + 综合评级 + 已掌握/待加强 + 后续计划"→ 输出 JSON 落 `interview_reports`。
- **MVP（2026-09-03，离线 mock）口径**：grade 未返回子维度时，ReportService 用**已落库作答的确定性代理**算六维（professional=各题均分、expression=作答篇幅/结构化代理、adaptability/learning=追问作答情况，见 `02` §4），可稳定复算、不二次调模型。接入真实 grade 输出子维度后，改回直接聚合即可。
- **深度文本（2026-09-05，R8b）**：确定性字段落库后，`ReportService` 额外喂一次 `report-analysis`（输入=面试信息/六维/逐题简报 `ReportAnalysisInput`，见 `module/report/dto`），产出 `analysis_json`（总评+六维逐维 点评/建议+结论）与 `resume_advice_json`（intro + 按 HIGH/MED/LOW 分组的详细简历修改建议）两列；真实/离线输出同构（`ReportDeepOut`）。仅当 `analysis_json` 为空才生成一次（避免重复计费）；历史旧报告在读取时懒补。模型不得改动输入分数（prompt 显式约束，离线实现逐维校验）。

## 6. 检索与"RAG 感"（MVP 存档 + R10 升级说明）

> 本节为 MVP（2026-09-03，纯 LLM）口径的答辩话术存档；**R10（2026-09-07）已把第 3 条落地为真实向量召回**（§8），第 1/2 条继续成立。
1. **检索=有范围的上下文拼装**：每次 LLM 调用只携带该用户自己的简历/JD 结构化块 + 必要历史（R10 起再叠加命中片段 `{knowledge}`），天然是"检索增强"。
2. **出题带来源**：前端展示每题底部"依据：你在 XX 项目提到… / JD 要求…"（由 gap evidence / R10 命中块透出）。
3. ~~二期~~ **R10 已完成**：把"证据"升级为原文 chunk 命中（向量召回 Top-k，`ai/rag/`），替换成本仅在后端 `ai/`。
**演示话术**建议强调"差距驱动出题 + 全程带出处、防幻觉 + R10 已把业务知识（简历/JD/题库）向量化注入作答"。

## 7. 成本与限流预算（每场面试参考，DeepSeek 定价便宜但要有护栏）

| 步骤 | 输入tok(约) | 输出tok(约) | 合计/场 |
|------|------------|------------|--------|
| 简历解析 R1 | 本地算法 | 本地算法 | 0 |
| JD 解析 R2 | 本地算法 | 本地算法 | 0 |
| 匹配+差距 | 6k | 1k | ~7k |
| 面试重点 | 3k | 1k | ~4k |
| 提问×10 | 0.4k×10 | 0.15k×10 | ~6k |
| 点评×10(+子维) | 1.2k×10 | 0.4k×10 | ~16k |
| 追问≤10 | 0.3k×10 | 0.1k×10 | ~4k |
| 深度报告分析 R8b | 6k | 1.2k | ~7k |
| **合计** | | | **≈46k tokens/场** |

- `CostGuard` 每日预算默认 **¥2**（DeepSeek 约可跑数十场）；超限返回 2003/额度用尽。
- 降本手段：简历/JD 原文在拼 Prompt 前"截断到结构化所需"；question 历史只带最近 2 题；失败重试最多 1 次。
- 超时：单次 LLM 调用读超时 30s（连接 5s）；`R5 首token≤5s` 由流式保底。

## 8. 向量增强（R10：2026-09-07 本地实现与真 Qdrant 均落地）

R10 不再"只留表"：`text_chunks` 真正启用，真实 DeepSeek 的出题/评分/追问在调用前
把 **当前用户可检索语料**（本人简历/JD + 全局题库知识点）按向量 top-k 命中块
拼成 `{knowledge}` 注入 prompt（question/grade/follow-up 模板已加占位与"可引用不编造"约束）。

```
语料来源                 分块/向量                      注入
简历全文(owner=用户) ─┐
JD 全文  (owner=用户) ─┼→ 切块(段落聚合,≤600字/块)
题库知识点(owner=null)─┘   → HashEmbedding 512维特征哈希(双哈希+1+ln(freq)+L2归一, 无模型依赖)
                          → text_chunks.embedding(JSON，唯一源)  →  查询向量与块余弦 → topK(=4) 命中块拼 {knowledge}
                          → (可选 provider=qdrant) 镜像进 Qdrant interview_chunks(512/Cosine) 由向量库检索
写库时机: resume/jd ready 后、题库 增/改/软删 后、启动与 POST /api/admin/rag/rebuild 全量重建（含 Qdrant 镜像整库重建）
配置: application.yml app.rag.*（enabled/provider=local|qdrant/embedding=hash/top-k/min-score/dimension=512/qdrant-url）
运维: GET /api/admin/rag/stats · POST /api/admin/rag/rebuild · GET /api/admin/rag/search · GET /api/admin/rag/qdrant/health（AdminOnlyInterceptor 门禁）
代码: ai/rag/{RagProperties,EmbeddingService,HashEmbeddingService,TextChunkService,RagInjector,RagStartupIndexer,QdrantClient}
```
> provider=local 不需要 Redis/外部向量库/外部模型；同文本结果确定可复现，离线 mock/CI 亦可跑。
> **Qdrant（真向量库）落地与切换手册见 §11**——`provider=qdrant` 缺服务/空集时自动回退 local，界面与接口不崩。

## 9. DeepSeek 接入注意（给实现者的坑清单）

1. 接口：`POST https://api.deepseek.com/chat/completions`（OpenAI 兼容，base-url 通常写 `https://api.deepseek.com`），模型名 `deepseek-chat`（V3 系列）/`deepseek-reasoner`。**reasoner 不做 JSON/低延迟评分**，默认用 chat。
2. **无 embeddings、无 function-call 强保证** → 本设计不依赖 function calling，全靠 JSON 约束 + 容错修复（对）。
3. JSON 输出：用 system+user 双提示 + 输出约束；DeepSeek 支持 `response_format={"type":"json_object"}`（需提示含 json），**实现时按所选 Spring AI 版本是否透传**；不强依赖，`JsonSupport` 始终兜底。
4. 并发/限速：默认开 10 并发足够，触发 429 时指数退避重试一次。
5. 时区/字符：中文输入输出无碍；`max_tokens` 按 §2 配，过长截断由 `done`/`stop` 处理。
6. 密钥：仅 `DEEPSEEK_API_KEY` 环境变量；**任何日志不得打印完整 key 与完整简历原文**。

## 10. 真实接入落地（2026-09-05，Spring AI 1.1.8）

- **坐标/版本**：Boot 3.5.16 ↔ Spring AI `1.1.8`（BOM 引入），starter `org.springframework.ai:spring-ai-starter-model-openai`；模型默认 `deepseek-chat`。
- **属性（非密钥项在 `application.yml`，密钥只在 gitignore 的 `application-local.yml`）**：
  ```yaml
  spring.ai.openai:
    base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
    api-key: ${DEEPSEEK_API_KEY:}          # local 里填
    chat.options: { model: ${DEEPSEEK_MODEL:deepseek-chat}, temperature: 0.7, max-tokens: 4000 }  # 4000=全局上限，防 R8b 长 JSON 截断（§2）
  ai:
    mock-enabled: ${AI_MOCK_ENABLED:true}  # false=真实 DeepSeek；true=离线引擎（默认）
    cost: { enabled: true, daily-limit-yuan: 2.0, input-price-per-million: 2.0, output-price-per-million: 8.0 }
  app.ocr: { language: chi_sim+eng, datapath: ./ocr-tessdata }
  ```
  计价常量按 DeepSeek 官网核对后近似入参（`docs/03` 环境说明同步），`CostGuard.charge` 用字符数近似 tokens 计当日花费，`overBudget()` 超限后新请求回落离线引擎并 warn。
- **类与职责**：`DeepSeekAIService`（真实通道：读 `classpath:prompts/{key}.md` → 以上下文对象顶层字段替换 `{field}` 占位 → `ChatClient.prompt().system(systemJson约束).user(prompt)`；`OFFLINE_KEYS=[resume-parse, jd-parse]` 一律委托离线；失败重试一次后回退离线）；`JsonSupport`（剥 ```json 围栏、截首尾花括号、去尾逗号修复）；`CostGuard`；离线引擎仍是 `LocalMockAIService`（`@ConditionalOnProperty(mock-enabled=true, matchIfMissing=true)`）。
- **prompt 模板清单**：`resources/prompts/` 下 `resume-parse/jd-parse`（离线不读）、`match/focus/question/grade/follow-up/report-analysis`（真实模式读取）。缺模板抛 `ErrorCode.INTERNAL`。
- **RAG 相关表**：`text_chunks`（见 `docs/04` §6）在 R10 已启用；`interview_reports` 新增 `analysis_json`/`resume_advice_json` 两列，实体与 `schema.sql` 同步（ddl-auto=validate）。

## 11. R10 检索增强 · Qdrant 真连接落地与切换手册（2026-09-07 已启用）

### 11.1 现在的实现（=普通 DeepSeek 的差别）

| | 普通 DeepSeek | 本系统 R10 |
|---|---|---|
| 输入 | 只有结构化简历/JD/题目 | 检索命中当前用户简历/JD/题库知识点的**原文片段**，以 `{knowledge}` 注入 question/grade/follow-up |
| 防幻觉 | 靠 prompt 约束 | 资料片段可引、越片段不可编（模板已加规则） |
| 数据 | 无用户私有库 | text_chunks 分用户 + 全局知识两级检索（owner_user_id 区分） |
| 可观测 | 调用日志 | + `GET /api/admin/rag/stats`、`POST /api/admin/rag/rebuild`、`GET /api/admin/rag/search`、`GET /api/admin/rag/qdrant/health` |

默认 `app.rag.provider=local`：512 维特征哈希向量（确定性、无外部模型依赖）存 MySQL JSON 列，
命中块拼 Prompt，**无需装任何服务**即可演示"带业务知识的 DeepSeek"（CI/离线用）。
`provider=qdrant`（2026-09-07 本机已连）在 local 之上多了"真向量库镜像"档：检索改走 Qdrant、
命中语义一致（同一套 hash 向量与 Top-k 口径），Qdrant 不可达/空集自动回退 local。

**2026-09-07 实测验收**（本机 `qdrant.exe` v1.19.1 + `RAG_PROVIDER=qdrant QDRANT_URL=http://127.0.0.1:6333`）：
全量重建后 `totalChunks == qdrant.chunks`（370=370），`stats.qdrant.connected=true`；
`/api/admin/rag/search?q=...` 返回 `provider=qdrant` 且 resume/jd/question_bank 来源标签齐全；
停掉 Qdrant 后同一请求回退 `provider=local`、接口不崩，重启即恢复 qdrant。

### 11.2 连接与切换（真向量库）

原则：**MySQL `text_chunks` 仍是内容/向量唯一源；Qdrant 只是其镜像检索索引**。
owner 以镜像点 payload 表达：`owner=0` 哨兵=全局知识（题库），`owner=userId`=该用户本人简历/JD；
检索过滤 `should:[owner=0, owner=uid], min_should=1`（uid 为空=全量，仅管理员 search 用）。
写入一律先落 MySQL，成功后镜像（`TextChunkService` → `QdrantClient`）；镜像失败只 warn、不阻断入库/接口。

1. **起 Qdrant**：Windows 下载 `qdrant-x86_64-pc-windows-msvc.zip`（GitHub releases），解压运行
   `qdrant.exe`，默认 `http://127.0.0.1:6333`；验证 `curl http://127.0.0.1:6333/healthz`。
2. **后端切 provider**（环境变量，不改代码；committed 默认仍是 `local`）：
   ```bash
   export RAG_PROVIDER=qdrant QDRANT_URL=http://127.0.0.1:6333
   mvn spring-boot:run
   ```
   集合 `interview_chunks`（512/Cosine）由后端 `ensureCollection()` 自动创建，无需手工建。
3. **同步索引**：启动时 `RagStartupIndexer` 自动 `purgeAndRebuildAll`（local 全清 + Qdrant 删集合重建后逐源镜像）；
   之后简历/JD ready、题库增改软删也会增量镜像；也可手动 `POST /api/admin/rag/rebuild` 重刷。
   此后 question/grade/follow-up 检索即走 Qdrant（命中 Top-k 的注入逻辑与 local 完全一致）。
4. **回退与兜底**：`provider=qdrant` 但 Qdrant 没起/地址不可达/集合为空时，检索侧 warn 并**自动回退 local 向量实现**，
   接口正常、不崩、不阻塞出题/评分（真实模式仍以普通 DeepSeek 语义作答）。

### 11.3 与"只替换 embedding 档位"的关系

本系统的**分块、语料归属、命中注入、运维接口**与 embedding 选型解耦：
- 换更强 embedding（如 ONNX bge-small-zh，`backend/rag-models/`，gitignore）只需新增 `EmbeddingService`
  实现并在 `app.rag.embedding` 选择，切片与注入不变；
- 保留 1+ln(freq) 亚线性加权与 L2 归一化接口，切换后同库余弦语义应更准。
- 因此未来升级只发生在 `ai/rag/`，业务层与 `docs/06` 契约不动。
