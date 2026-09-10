# 09 · 测试策略

> 目标：让"答辩能演示、指标能自证"。分 单元 → 集成 → LLM → 前端 → 性能/验收。
> 最后更新：2026-09-02。

## 1. 测试金字塔与本项目取舍

| 层 | 工具 | 范围 | 取舍说明 |
|----|------|------|---------|
| 单元 | JUnit5 + Mockito | Service 纯逻辑、状态机、工具类 | 不联网、不连库 |
| 集成(DB) | JUnit5 + 真 MySQL(本地) | Repository、事务边界 | Windows 上 **不用 Testcontainers/Docker**，直连本地 `interview_system`，测试库建议 `interview_system_test` |
| AI 层 | Mockito 打桩 `ChatClient`/`AIService` | Prompt 拼装、JSON 容错修复 | 不真调 DeepSeek（贵、慢、不稳定），改打桩返回固定文本 |
| 契约 | springdoc + 手写冒烟 | `/api/ping`、登录、主流程 | 前后端按 `06` 人工核对 |
| 前端 | Vitest(纯函数) + 可选 Playwright | utils、sse 解析、核心 E2E | E2E 作为加分项，非必须 |
| 性能/验收 | 脚本 + 秒表/日志耗时 | SRS §5.1 各指标 | 人工+日志时间戳即够，答辩自证 |

## 2. 分层测试写法

**Service 单测（Mock 掉 AIService 与 Repository）**
```java
class MatchServiceTest {
  @Mock AIService ai; @InjectMocks MatchService match;
  @Test void givenNoGap_whenMatch_thenFocusNotEmpty(){ ... }
  @Test void 用户越权_他人面试_抛403(){ when(repo.findById(9)).thenReturn(interviewOf(user2));
     assertThrows(ForbiddenException.class, ()-> match.run(9L, user1Id)); }
  @Test void 简历未定稿_不得匹配(){ assertThrows(StateConflictException.class, ...); }
}
```

**JSON 容错（`JsonSupport` 单元，高价值）**：喂入 ①标准 JSON ②带```代码块``` ③字段顺序打乱 ④缺字段 → 断言能解析/修复/按 schema 校验失败。**这是 AI 层最该测的部分。**

**AI 层打桩技巧**：把 `chatModel.stream/chat` 打成返回**预录的坏/好输出**，专门跑"重试修复"分支；另写一个 `PromptCaptureTest`，断言送出的 prompt 含关键约束词（防后续改模板把 JSON 约束丢了）。

**状态机测试**：`InterviewStatusTest` 覆盖 `canTransitionTo(ABANDONED)` 与 `InterviewStatus.from()`（评审时以实际 `backend/src/test/.../InterviewStatusTest.java` 为准）。**非法迁移拦截不以集中式 `canTransitionTo` 实现**，而是各 Service 端点显式前置校验（如仅 `draft` 且已有简历才置 `resume_uploaded`、仅 `resume_uploaded` 才置 `jd_uploaded`），逐端点的越序用例随功能冒烟覆盖（见各 Phase E2E），不再重复维护"全路径迁移表"。

## 3. LLM 打桩方案（不真烧 key）

- 定义 `resources/test/llm-stubs/` 下固化返回 JSON（resume-parse/jd-parse/match/grade/report 各一例）。Service 测试时由 `StubAIService` 提供。
- 每个 AI 调用点都要有**坏输出用例**：非法 JSON / 分数>100 / gap 缺字段 → 走 repair 或抛可重试 `AiCallException`。
- 预留 `ai.mock-enabled=true` 开关：`AIService` 实现切换到桩，方便**离线演示/CI**。
- **评分纯函数单测（2026-09-07，R11）** `ai/ReferenceAwareGradeTest`：离线评分改参照参考答案（要点覆盖度）。断言：照抄参考答案→高分(≥85)；乱写 `111`→低分(≤30)；空作答→0；不相干长文→低分(<40) 且 suggestFollowup=true；无参考答案→纯函数返回 -1、调度回退旧启发式结果非 0；`normForGrade`/`gradeOverlap` 纯函数。用 `new LocalMockAIService().structured("grade", new GradeInput(..., referenceAnswer), GradeOut.class)` 免 Spring 直测。

## 4. 验收用例 ↔ SRS 指标（traceability）

| SRS 指标 | 如何自证 | 工具 |
|----------|----------|------|
| JD 分析 ≤8s | 打日志 `[ai] jd-parse cost=…ms` | 日志统计（跑 3 次取均值） |
| 匹配 ≤10s | 同上 `match cost` | 日志 |
| 首 Token ≤5s(P95) | 前端计时 `首字时间` 收集到控制台 | 手动/脚本跑 10 题取 P95 |
| 单题点评 ≤4s | 同上点评首字 | 手动 5 题 |
| 简历解析 ≤15s | 上传后日志 `resume-parse cost` | 日志 |
| 冷启动 ≤10s | `java -jar` 到"Started … in Xs" | spring boot 日志自带 |
| 50 并发 | JMeter/自写并发 POST `/ping`+登录+出题压测 100 请求 | 可选；答辩以"原理+少量实测"讲 |
| 密码 bcrypt≥12 | 测试断言 hash 前缀/成本 | 单测 |
| 登录 5 次锁 15min | 集成测试循环错密码断言锁 | JUnit 真库 |
| 面试限流 10/min | 集成：连打 11 次断言 429 | JUnit |
| 数据隔离 | 上面"越权"单测 + 抽查 | JUnit |
| Token 控制 | CostGuard 单测（构造超预算场景） | JUnit |

## 5. 端到端冒烟（答辩前必须过一遍的"黄金路径"）

```
1 注册(固定码) → 登录 → 首页空态
2 创建"Java后端一面" → 上传 sample_resume.pdf → 看解析进度→结构化预览→确认
3 粘贴 JD → 开始匹配 → 看 P06 环形+差距+重点
4 开始面试 → 收流式问题 → 作答 → 看点评与追问(答/跳) → 连做至完成
5 报告页：评级/雷达/逐题回放/建议 均可见
6 历史页：记录在列，详情可看，删除生效
7 中断恢复：做一半退出再进，可从断点续
8 错误分支：上传超 10MB/非法格式、密码错 5 次、额度用尽提示
```
> `sample_resume.pdf` 放 `backend/src/test/resources/` 与 `docs/` 备查（样例 PDF 用 Word/LaTeX 导出一份含明确教育/项目/技能的即可）。

### 5.1 R11 管理端改版冒烟（2026-09-07 追加）
```
9  admin 登录(admin@interview.local/Admin@12345) → 直达左侧栏后台（概览/用户/黑名单/题库/反馈/调用统计）；
   无"工作台/最近面试墙/简历对比"
10 用户管理：新增管理员(邮箱+密码≥8) → 新管理员可登录进后台；搜 role=admin 可查
11 停用某普通用户 → 其已登录 token 再请求普通接口返回 401；重新登录被拒 → 黑名单页出现该用户 → 启用后恢复
12 用户管理 → 某用户"查看面试"→ 面试列表可见 → completed 点开只读报告
13 反馈：普通账号提交 → 后台反馈列表可见；admin 账号自己提交的反馈不出现
14 评分（离线）：题库练习照抄参考答案 → ≥85 分；答"随便写/111" → ≤30 分并提示离题；空作答 0 分
```
> 守卫断言（可选 JUnit）：停用自己 / 停用最后一位启用管理员 → 返回业务冲突；`JwtAuthFilter` 对 `is_active=false` 返回 401。

## 6. 前端测试

- `utils/sse.ts` 的逐行解析用 Vitest 喂合成帧断言事件序列（高价值）。
- 数字格式化/评级映射纯函数测试。
- Playwright（可选加分）：登录→创建→上传简历 主路径一条脚本，留到 Phase 8 时间富余再做。
