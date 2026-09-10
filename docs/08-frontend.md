# 08 · 前端设计（Vue3 + Vite + TS + Element Plus + ECharts）

> 与 `06`(接口)、`02`(架构)配套；页面映射 SRS P01–P11 + R1–R10 大扩展页面。
> 最后更新：2026-09-07（R1–R10/P1–P7 收口 + **R11 管理端改版**：AdminLayout 左侧栏/概览/用户管理/黑名单、admin 直达与路由守卫、新增管理端 API 均已按代码校准）。

## 1. 技术栈与工程（真实落地）

依赖：`vue@3 vue-router pinia element-plus axios echarts @element-plus/icons-vue`。
UI 基调：主色 `#1A237E`、成功 `#00C853`、警示 `#FF9100`；正文 14px/标题 18px/副标题 16px（SRS §3.1.2）。
`vite.config.ts` 代理 `/api → http://localhost:8080`（开发期免 CORS）；端口 5173。

```
frontend/
├─ vite.config.ts
└─ src/
   ├─ main.ts                      # createApp + Pinia + Router 挂载
   ├─ App.vue                      # AppNavbar + <router-view :key="route.fullPath">（切路由即重建页面）
   ├─ store.ts                     # Pinia 鉴权 store：token/refreshToken/user(含 role)、loggedIn、isAdmin
   ├─ api.ts                       # 全部后端接口封装 + axios 拦截器 + TS 类型 + fetchBlob（见 §3/§8）
   ├─ utils.ts                     # 状态/评级/模式中英互译、时间格式化、六维中文标签、labelOf
   ├─ router/index.ts              # 路由表 + 全局鉴权守卫 + admin 守卫（见 §2）
   ├─ components/
   │  ├─ AppNavbar.vue             # 顶栏品牌 + 菜单（普通用户区用；admin 进后台后不渲染——见 App.vue）
   │  ├─ MatchSummary.vue          # 匹配总览：总分/技能/经验/学历 + gap/focus 折叠
   │  ├─ DimensionRadar.vue        # ECharts 六维雷达（卸载 dispose、空数据占位）
   │  └─ QuestionReplayCard.vue    # 报告逐题回放卡片（含参考回答/提示/追问）
   └─ views/
      ├─ LoginView.vue             # 登录/注册（登录成功按角色落地：admin→/admin，user→/home）
      ├─ HomeView.vue              # 工作台：欢迎 CTA + 功能入口 + 题库概览 + 最近面试卡片
      ├─ InterviewWizardView.vue   # 创建/续办向导（/interviews/new 与 /:id/setup 共用；模式选择 formal/practice）
      ├─ InterviewEngineView.vue   # 答题独立页（正式动态出题 / 练习题库抽题）
      ├─ ReportView.vue            # 报告页（评级卡+雷达+强/弱项+深析+简历建议+逐题回放）
      ├─ HistoryView.vue           # 历史列表（Type A 分页/删除/清空/继续入口）
      ├─ QuestionBankView.vue      # 题库浏览 + 发起练习（category 筛选，Type B 分页）
      ├─ WeakSkillLibraryView.vue  # 薄弱技能库 / 错题本（tab 切换）
      ├─ PersonalCenterView.vue    # 个人中心：资料/设置/改密 + 学习统计（/user/stats）
      ├─ FeedbackView.vue          # 用户反馈提交 + 我的反馈列表
      ├─ ResumeCompareView.vue     # 简历对比（选两份简历 → AI 对比 → 结果/历史）
      └─ admin/
         ├─ AdminLayout.vue        # 后台独立布局（左侧栏导航 + 顶栏[个人中心/退出]），承载下方全部子页
         ├─ AdminOverviewView.vue  # 概览首页（统计卡 + 快捷入口）
         ├─ AdminUsersView.vue     # 用户管理（筛选/查看每用户面试与只读报告/新增管理员/停用启用）
         ├─ AdminBlacklistView.vue # 黑名单管理（active=false 账号列表 + 启用）
         ├─ AdminBankView.vue      # 题库管理（CRUD + AI 一键扩充）
         ├─ AdminFeedbackView.vue  # 反馈处理
         └─ AdminLlmView.vue       # DeepSeek 调用统计图表 + 明细
```

## 2. 路由与守卫（router/index.ts）

| 页面 | 路由 | 说明 |
|------|------|------|
| 登录/注册 | `/login` | 独立页，无导航栏；已登录访问自动跳 `/home` |
| 工作台 | `/home` | 欢迎 + 新建 CTA + 功能入口（薄弱库/错题本/对比/反馈/个人中心）+ 题库概览 + 最近面试 |
| 题库 | `/bank` | 题库浏览/发起练习（支持 `?category=`/`?tab=`） |
| 创建向导 | `/interviews/new` | 创建面试（①建会话(选模式)→②简历→③JD→④匹配；practice 直启答题） |
| 续办向导 | `/interviews/:id/setup` | 未完成资料准备的会话断点续 |
| 答题独立页 | `/interviews/:id/engine` | **开始答题后跳转至此**；断点续面 |
| 报告页 | `/interviews/:id/report` | 综合报告（formal=评级；practice=复盘无评级） |
| 历史 | `/history` | 历史列表 |
| 薄弱库/错题本 | `/weak` | `?tab=skills`（薄弱技能）｜`?tab=wrong`（错题本） |
| 个人中心 | `/center` | 资料/设置/改密 + 学习统计 |
| 用户反馈 | `/feedback` | 提交/我的反馈 |
| 简历对比 | `/compare` | 对比 + 历史 |
| 管理后台（控制台） | `/admin`（父路由 `AdminLayout`） | `meta.admin=true`，仅 `role=admin` 可进；`/admin**` 下不渲染 `AppNavbar` |
| ├─ 概览 | `/admin`（子路由 `''`） | 概览统计：用户/面试/待处理反馈/题库 8 卡 + 快捷入口 |
| ├─ 用户管理 | `/admin/users` | 用户筛选 + 查看每用户面试 & 只读报告 + 新增管理员 + 停用/启用 |
| ├─ 黑名单管理 | `/admin/blacklist` | 停用（`active=false`）账号列表，可启用 |
| ├─ 题库管理 | `/admin/bank` | CRUD + AI 一键扩充（URL 兼容旧路径） |
| ├─ 反馈处理 | `/admin/feedback` | 回复/改状态（列表仅普通用户提交） |
| └─ 调用统计 | `/admin/llm` | ECharts + 明细 |
| 兜底 | `/` → `/home`；`/:pathMatch(.*)*` → `/home` | |

**守卫**：未登录除 `/login` 外一律 `→ /login`；已登录访问 `/login` → 按角色 `admin→/admin`、`user→/home`；`meta.admin` 且 `store.isAdmin=false` → `/home`（store.ts `auth.isAdmin = user?.role === 'admin'`）；**admin 访问非（`/admin**`、`/center`、`/login`）的用户区路由 → 重定向 `/admin`**——管理员只看到「后台管理 + 个人中心」，不进入用户工作台/题库/历史/对比/反馈等页。

## 3. axios 封装、401 单次刷新、两类分页与文件流（api.ts）

- `http = axios.create({ baseURL:'/api', timeout:60000 })`；请求拦截器统一加 `Authorization: Bearer <accessToken>`。
- 响应拦截器**自动解包信封**：`code===0` 直接返回 `data`（TS 以 `http.get<never, T>` 标注）；非 0 抛 `Error`；**`responseType:'blob'` 的响应不包信封，直接返回 Blob**。
- **401 → 单次 refresh（轮换 refresh token）**：`refreshing` 单例防并发；登录/注册请求不参与重试；刷新失败清 token 跳 `/login`。token/user 存 **sessionStorage**（不进 localStorage；`api.ts` 顶部 `tokenStorage` 封装）。
- 文件上传用 `FormData`（`uploadResume`/`uploadJdFile`），**不手设 Content-Type**（浏览器自动带 boundary）。

**分页类型（务必按接口区分，见 `docs/06 §1.1`）**：
```ts
export interface Paged<T>    { list: T[]; total: number; page: number; size: number }   // Type A：/history，page 从 1
export interface ItemPage<T> { items: T[]; total: number }                             // Type B：bank/weak/admin，page 从 0
```
- 历史：`listHistory(page=1, size)`、返回 `Paged<HistoryItem>`（`HomeView` 取 `.list`）。
- 题库/错题本/后台列表：`listBankQuestions({page:0})`、`adminBankPage`、`listWrongQuestions(0,10)`、`adminFeedbackPage`、`listLlmCalls(0,20)`、`listAdminUsers`，返回 `ItemPage<T>`，取 `.items`。

**文件流（携带 Bearer 的 fetch/blob）**：`getResumeFileBlob(id)`/`getJdFileBlob(id)` 用 `http.get(url, {responseType:'blob'})`（见 §8 取用方式）。

**接口清单与 TS 类型**（与 `docs/06` 一一对齐，均在本文件导出）：
- 认证：`login/register/logout`；**`user.role` 登录即带**（`UserSummary.role?`）。
- 用户：`getMe/updateMe/changePassword/updateSettings/getModels`、**`getUserStats`**（`UserStatsResponse{interviewCount,formalCount,practiceCount,completedCount,wrongQuestionCount,weakSkillCount,averageScore,latestMode?,latestRating?}`）。
- 会话：`createInterview`（支持 `mode/weakBoost/practiceCategory`）、`getInterview/listInterviews`。
- 简历/JD：`uploadResume/getResume/confirmResume/reuseResume`、`listResumes/getResumeDetail/getResumeFileBlob`；`submitJdText/uploadJdFile/getJd/reuseJd`、`listJdLibrary/getJdDetail/getJdFileBlob`。
- 匹配：`runMatch/getMatch/regenerateFocus`。
- 引擎：`startSession/getSession/submitAnswer/submitFollowUp/skipFollowUp/getReference`。
- 题库：`listBankCategories/listBankQuestions`；管理 `adminBankPage/adminBankCreate/adminBankUpdate/adminBankDelete/adminBankGenerate`。
- 薄弱/错题：`listWeakSkills/resolveWeakSkill/listWrongQuestions`。
- 反馈：`submitFeedback/listMyFeedback`；管理 `adminFeedbackPage/adminFeedbackReply`。
- 简历对比：`runCompare/listCompareHistory/getCompareDetail/deleteCompare`。
- 报告/历史：`getReport/getHistoryDetail(=ReportResponse)/deleteHistory/clearHistory`。
- 管理端：`getLlmStats/listLlmCalls`、`getRagStats/rebuildRag`（`/api/admin/rag/stats|rebuild`）、**`getAdminOverview`**（概览 8 项）；用户/账号：**`listAdminUsers(keyword,role,active,page,size)`、`adminSetActive(id,active)`、`adminCreateAdmin({email,password,nickname?})`、`adminUserInterviews(userId)`、`adminUserInterviewReport(userId,interviewId)`**（类型 `AdminInterviewView`、`AdminOverviewStats` 与 docs/06 字段对齐）。

## 4. 页面要点（与后端状态的强一致）

**向导 InterviewWizardView**：步骤由后端 `interview.status` 驱动；正式模式走 简历→JD→匹配→开始；**练习模式**在向导首步选 `mode=practice`（可勾 weakBoost、选 practiceCategory），到可开始状态后直入答题页，无需简历/JD/匹配。

**答题独立页 InterviewEngineView**：进入时校验状态——`completed` → 重定向报告；资料未齐 → 重定向向导。formal：作答后可见「参考回答/提示」（`getReference`，`answered=true` 才返回）；**practice：hint/referenceAnswer 随时可见**。`submitAnswer` 同步返回点评 + `mode`；`referenceAnswer` 在每题「查看参考答案」折叠区展示。

**报告页 ReportView**：
- 评级卡 + 六维雷达（`DimensionRadar`）+ 强/弱项 + 推荐。
- **formal**：显示评级（`rating`）、综合匹配分、深析 A 块（`report.analysis`）、详细简历建议 B 块（`report.resumeAdvice`）。
- **practice（`report.mode==='practice'`）**：**隐藏评级卡**，显示「练习复盘 · 无评级」警示条 + 复盘要点（`analysis/resumeAdvice/matchScore/rating` 均为 null，用确定性子段拼兜底，不白屏）。
- 逐题回放 `QuestionReplayCard`：题目/回答/评分/strong/weak/suggestion/resumeAdvice/**参考回答/hint**/追问。
- `analysis`/`resumeAdvice` 为 null 时用 `fallbackAnalysisLines`/`fallbackResumeAdviceText` 兜底文案（已实现）。

**题库 QuestionBankView**：分类卡 + 题目列表（Type B 分页，page 0）；「去练习」→ 携带 category 创建 practice 面试。**浏览视图无答案/提示**（防剧透）。

**薄弱库/错题本 WeakSkillLibraryView**：`?tab=skills` 拉 `listWeakSkills`（severity 升序，低分在前），支持「标记已掌握」(resolve)；`?tab=wrong` 拉 `listWrongQuestions` 分页，展示低分题 + 我的作答 + 参考答案，可跳回对应历史报告。

**个人中心 PersonalCenterView**：资料/设置/改密 + 学习统计卡（8 张：总场/正式/练习/完成/错题/薄弱/均分/最近评级）。`stats.latestRating` 为 null 时显示「无」；角色展示 admin → 管理员。保存资料后用 `auth.setUser` 回写昵称；`getMe().role` 回写角色（保证刷新后 `isAdmin` 正确）。

**工作台 HomeView**：`listHistory(1,6)` 最近面试；卡片按 `status/hasReport/mode` 决定去向与角标（`modeTagType` 蓝=正式/黄=练习；`overallRating` 无则显示「未评级」）。

**管理后台（AdminLayout，2026-09-07 改版）**：左侧栏【概览/用户管理/黑名单管理/题库管理/反馈处理/调用统计】+ 顶栏【个人中心 `/center`｜退出】。概览 `/admin` = `getAdminOverview` 统计卡；用户管理 `/admin/users`（筛选 + 查看每用户面试抽屉 → completed 只读报告弹窗 + 新增管理员 + 停用/启用，停用拉黑即时生效）；黑名单 `/admin/blacklist`；`/admin/bank`（CRUD + AI 扩充）、`/admin/feedback`（回复/改状态，**仅普通用户提交**）、`/admin/llm`（ECharts）。**fallbackRate 显示**：后端返回已是百分数，直接 `{{ stats.fallbackRate }}%`；判定偏高用 `fallbackCalls/totalCalls < 0.5`（见 `docs/06` 2.9）。

## 5. 关键组件

- **AppNavbar**：品牌 + el-menu（工作台/题库/历史…）+ 管理后台（仅 `isAdmin`）+ 用户邮箱 + 退出登录；`/login` 下不渲染。
- **MatchSummary**：匹配总览 + 差距(gap) 折叠 + 面试重点(focus) 折叠。
- **DimensionRadar**：ECharts 六维雷达；`onUnmounted` 时 `dispose()`；空数据占位。
- **QuestionReplayCard**：报告逐题回放卡片，供报告页/历史详情复用。

## 6. 关于 SSE 的落地口径（重要）

**当前前端为「同步核心」交互，不消费 SSE**：`submitAnswer`/`submitFollowUp` 均同步返回评分与下一题，`AnswerResponse` 即含全部反馈字段；SSE 通道 `POST /events` 与 `delta` 事件仍保留，供后续做打字机增强；断线/续面用 `GET /session` 拉快照补齐（已实现）。

## 7. 与后端的强一致清单（易踩坑）

- 页面步骤、可点击性永远以**后端 status** 为准，不信任前端 state 单边推进。
- 匹配分/分数是 `number`（可能有小数）；显示时 `toFixed(0)`；评级/模式映射中文用 `utils.ts`。
- 所有耗时按钮带 loading + 禁止连点；401 单次刷新、429 提示、4001 提供重试（`ElMessage`）。
- ECharts 实例在组件卸载时 `dispose()`；雷达数据为空给占位。
- 接口变更先改 `docs/06` 再同步 `api.ts` 类型。

## 8. 历史 JD/简历"查看原文"文件流取用方式

后端 `GET /api/resumes/{id}/file` 与 `GET /api/jds/{id}/file` 返回**原始文件字节**（`Content-Disposition: inline`，不包 JSON 信封，需带 Bearer）。前端统一用 axios blob 取：

```ts
// api.ts
function fetchBlob(url: string): Promise<Blob> {
  return http.get<never, Blob>(url, { responseType: 'blob' });
}
export const getResumeFileBlob = (resumeId: number) => fetchBlob(`/resumes/${resumeId}/file`);
export const getJdFileBlob     = (jdId: number)     => fetchBlob(`/jds/${jdId}/file`);
```

页面拿到 Blob 后生成临时 URL 预览/下载（PDF/图片可直接 `<iframe>`/`<img>`，DOCX 降级下载）：

```ts
const url = URL.createObjectURL(blob);      // 预览：window.open(url) / <a href download>
URL.revokeObjectURL(url);                    // 用完释放
```

> 拦截器对 `responseType:'blob'` 直接返回 Blob、不做信封解包；错误（401 等）仍走 axios error 分支统一提示。
