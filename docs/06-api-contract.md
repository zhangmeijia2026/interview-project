# 06 · REST/SSE 接口契约

> 前后端**唯一事实源**：改接口先改本文件再改代码。地址前缀 `/api`，JSON(UTF-8)，`Authorization: Bearer <access>`。
> 最后更新：2026-09-07（R1–R10 / P1–P7 大扩展收口：分页口径、题库/薄弱库/反馈/对比/管理端、文件流、练习模式报告、统计字段、fallbackRate 换算均已按代码校准）。
> 本文件与 `08`(前端取用口径)、`07`(RAG/LLM)、`04`(表) 配套阅读。

## 1. 通用约定

- 统一返回体：`{ "code":0, "message":"ok", "data": {...} }`（code 语义见 `05` §3）。
- 时间：ISO-8601 字符串 `2026-09-02T15:00:00`（Asia/Shanghai）。
- 金额/分数：`number`；匹配分/均分等 0–100（匹配分可能为小数，展示 `toFixed(0)` 即可）。
- 认证：`401` token 缺失/失效/黑名单；`403` 越权（非本人资源）；`429` 限流/额度；`4xx` LLM 依赖失败带 `retryable`。
- 流式：`text/event-stream; charset=utf-8`。
- 管理端：所有 `/api/admin/**` 需 `role=admin`（`AdminOnlyInterceptor` 门禁），非管理员返回 `403`。
- **文件流端点不包 JSON 信封**：`GET /api/resumes/{id}/file`、`GET /api/jds/{id}/file` 返回原始文件字节（`Content-Disposition: inline`），仍需带 Bearer；前端用 `fetch`/`axios` 的 `responseType:'blob'` 取流（见 `08` §8）。除这两个端点外，其余一律 JSON 信封。

### 1.1 分页口径（两种，前后端必须区分，勿混用）

| 形态 | 返回 data | page 起始 | 用途 |
|------|-----------|-----------|------|
| **A · 历史记录** | `{ "list":[], "total":n, "page":1, "size":20 }` | **从 1 起** | `GET /api/history` |
| **B · PageResult 通用** | `{ "items":[], "total":n }`（无 page/size 回显） | **从 0 起** | 题库 `/api/bank/questions`、错题本 `/api/weak/wrong-questions`、后台 `/api/admin/*` 分页列表、`/api/admin/llm/calls` |

> 前端封装差异：Type A 用 `Paged<T>{list,total,page,size}`，首页传 `page=1`；Type B 用 `ItemPage<T>{items,total}`，首页传 `page=0`。见 `08` §3。单次 `size` 上限：Type A 50、Type B 一般 100。

### 1.2 错误码表（业务相关节选）

| code | HTTP | 含义 |
|------|------|------|
| 0 | 200 | 成功 |
| 1001 | 400 | 参数校验失败（message 含字段） |
| 2001 | 401 | 未认证/Token 失效 |
| 2002 | 403 | 无权限/越权 |
| 2003 | 429 | 登录锁定/接口限流/额度用尽 |
| 3001 | 404 | 资源不存在（邮箱不存在、面试不存在…） |
| 3002 | 409 | 状态冲突（如面试不在可答题状态、越序作答、重复作答） |
| 4001 | 502 | AI 调用失败（可重试） |
| 4002 | 504 | AI 超时 |
| 5000 | 500 | 系统错误 |

## 2. 模块接口清单

> 标注 🔒=需登录；标注 ⭐=管理员（`/api/admin/**`）。未标注鉴权的按「🔒」理解（除登录/注册/ping）。

### F01 认证 / 用户
| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/auth/register` | 否 | 注册 `{email,password,verifyCode}` → `user:{id,email,nickname,role}`（**不含 token**，前端随后调 login） |
| POST | `/api/auth/login` | 否 | `{email,password}` → `{accessToken,refreshToken,user:{id,email,nickname,role}}` |
| POST | `/api/auth/refresh` | 否 | `{refreshToken}` → 新 access/refresh（user 不变，前端不回写） |
| POST | `/api/auth/logout` | 🔒 | 登出（删 refresh、黑名单 access） |
| GET | `/api/user/me` | 🔒 | 当前用户信息 + settings：`{id,email,nickname,avatarUrl,role,settings}` |
| PUT | `/api/user/me` | 🔒 | `{nickname, avatarUrl}` → 更新后 me |
| PUT | `/api/user/password` | 🔒 | `{oldPassword,newPassword}` |
| PUT | `/api/user/settings` | 🔒 | `{modelProvider,modelName,language,notifyEnabled}` → `settings` |
| GET | `/api/user/stats` | 🔒 | 个人中心学习统计（P3），字段见 §2.9 |

> 说明：`register` 响应为 `UserSummary`（有 `role`），`login`/`refresh` 响应为 `TokenResponse`（内嵌 `user` 也有 `role`）；前端登录态 `user.role` 用于是否渲染管理后台入口与路由守卫。

### F02 面试会话
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/interviews` | 创建 `{title,targetPosition,questionCount?,mode?,weakBoost?,practiceCategory?}` → interview(id,status=draft) |
| GET | `/api/interviews?status=all` | 列表（status: all/ongoing/completed），按创建倒序 |
| GET | `/api/interviews/{id}` | 详情 |
| DELETE | `/api/interviews/{id}` | 删除（含级联，规则见 04 §5） |

> `mode`：`formal`=正式（默认，需 简历→JD→匹配，动态 AI 出题，报告出评级）｜`practice`=练习（draft 即可开始，从题库抽题，报告无评级）。`weakBoost`=针对薄弱技能加强出题；`practiceCategory`=练习分类（空=全题库/薄弱偏置抽题）。返回行含 `mode/weakBoost/practiceCategory`，不含 matchScore/overallRating（历史行的这两字段见 `/api/history`）。

### F03 简历
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/interviews/{id}/resume` | multipart 上传(PDF/DOCX≤10MB)，触发异步解析 |
| GET | `/api/interviews/{id}/resume` | 当前面试绑定简历行（含 status、parsed_data）轮询用 |
| PUT | `/api/interviews/{id}/resume/confirm` | 预览确认（body 可空；也可带修正后的 parsed JSON） |
| POST | `/api/interviews/{id}/resume/reuse` | 复用历史简历 `{resumeId}`：绑定并确认（该简历须 ready） |

> 简历库（保留复用，P3）：`GET /api/resumes` → 本人全部 `ready` 简历 `[{id,fileName,status,parsedData,createdAt}]`（按更新时间倒序）；`GET /api/resumes/{id}` 单条详情；`GET /api/resumes/{id}/file` **原文件流**（PDF inline / DOCX 下载，带 Bearer blob）。

### F04 JD / 匹配
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/interviews/{id}/jd` | multipart 上传 JD 文件（PDF/DOCX≤10MB；图片 jpg/jpeg/png/bmp/webp 走 Tesseract OCR），异步解析 |
| POST | `/api/interviews/{id}/jd/text` | 直接贴 JD 原文 `{rawText}` |
| GET | `/api/interviews/{id}/jd` | JD 行（status/parsed_data）；failed 提示改粘贴文字 |
| POST | `/api/interviews/{id}/jd/reuse` | 复用历史 JD `{jdId}`（须解析成功） |
| POST | `/api/interviews/{id}/match` | 触发匹配 → match_results；幂等（已有则重算） |
| GET | `/api/interviews/{id}/match` | 匹配详情：`{overall,skill,experience,education,gap[],focus[],summary}` |
| POST | `/api/interviews/{id}/focus/regenerate` | 重新生成面试重点（返回 `focus[]`） |

> JD 库（历史岗位保留，P3）：`GET /api/jds` → `[{id,type,fileName,status,textPreview,createdAt}]`（type=`text`|`file`）；`GET /api/jds/{id}` → `{id,type,fileName,status,rawText,createdAt}`；`GET /api/jds/{id}/file` **原文件流**（图片 inline 预览 / PDF / DOCX，带 Bearer blob）。新建面试向导可从此库「选择历史 JD」再调 `/jd/reuse`。

### F05 面试引擎
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/interviews/{id}/start` | 开始/恢复：formal 校验已匹配；practice 由 draft 直启并从题库物化题目 → SessionResponse |
| GET | `/api/interviews/{id}/session` | 当前进度 `{status,questionCount,completedCount,current?{orderIndex,focusIndex,content}}`（断点续传） |
| POST | `/api/interviews/{id}/events` | **SSE 订阅**（见 §3） |
| POST | `/api/interviews/{id}/questions/{orderIndex}/answer` | 提交作答 `{answer,elapsedSeconds?}` → `AnswerResponse`（见下） |
| GET | `/api/interviews/{id}/questions/{orderIndex}/reference` | 查看本题提示/参考答案（R8）：`{mode,answered,hint,referenceAnswer}`——**practice 随时可见、formal 作答后才可见**（未到可见时长为 null） |
| POST | `/api/interviews/{id}/followup/{orderIndex}/answer` | 回答追问 `{answer}`（追问不计主进度）→ `{score,suggestion}` |
| POST | `/api/interviews/{id}/questions/{orderIndex}/skip` | 跳过追问 |

**AnswerResponse**（同步返回，字段随模式/题目来源略有差异）：
```
{ orderIndex, score, strong[], weak[], suggestion, suggestFollowup,
  followUp?: {content}, nextQuestion?: {orderIndex,focusIndex,content},
  interviewStatus, finished, resumeAdvice?, mode, referenceAnswer?, hint? }
```
> `mode`=formal/practice；`referenceAnswer/hint` 由 R8 出题时产生并随作答返回（formal 出题时即带，练习模式来自题库）。
> 最后一题答完 `finished=true` 且 `interviewStatus=completed`，服务端同步生成报告（formal 深度分析可能较慢）并推 `report_done`。

### F06/F07 报告与历史
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/interviews/{id}/report` | 完整报告（结构见下） |
| GET | `/api/history?keyword=&page=&size=` | 历史列表（Type A 分页，keyword 对标题/岗位名模糊搜） |
| GET | `/api/history/{interviewId}` | **同报告详情**（F06 复用 ReportResponse，非独立 DTO） |
| DELETE | `/api/history/{interviewId}` | 单条删除 |
| DELETE | `/api/history` | 清空 |

**历史行 HistoryItem**：`{id,title,targetPosition,status,questionCount,completedQuestionCount,matchScore?,overallRating?,mode,weakBoost,createdAt,completedAt?,hasReport}`。

**ReportResponse**（`GET /report` 与 `GET /history/{id}` 同构）：
```
{ rating, mode, matchScore?, dimensions{job_match,professional,expression,logic,adaptability,learning},
  questions[{orderIndex,question,answer,score,strong[],weak[],suggestion,resumeAdvice,referenceAnswer,hint,followUp?{question,answer,score}}],
  strengths[], improvements[], recommendation, analysis?, resumeAdvice? }
```
- `analysis`=面试结果详细分析（report-analysis）`{overallAnalysis, dimensionAnalyses[{dimension,score,analysis,advice}], conclusion}`；`resumeAdvice`=`{intro, groups[{title,priority:HIGH/MED/LOW,items[]}]}`；两者仅 **formal** 生成，旧报告/生成失败时为 null（前端用确定性子段拼兜底文案）。
- **practice（练习）模式口径**：`rating=null`、`matchScore=null`（无简历/JD 匹配）、`analysis/resumeAdvice=null`；`dimensions` 仍按作答均分产出、`recommendation` 为练习复盘文案。前端据此隐藏评级卡并显示「练习复盘 · 无评级」。

### F08 设置 / 杂项
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/settings/models` | 可选模型列表 `[{id,name}]`（当前 DeepSeek 系） |
| GET | `/api/ping` | 健康探测（无鉴权） |

### 2.9 个人中心统计（P3，F01 扩展）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/user/stats` | 学习统计：`{interviewCount,formalCount,practiceCount,completedCount,wrongQuestionCount,weakSkillCount,averageScore,latestMode?,latestRating?}` |

> `averageScore`=已完成会话中已作答目的平均分（0–100，保留两位）；`latestRating`=最近一次完成会话的评级（formal），**若最近一次完成的是练习则为 null**；`wrongQuestionCount/weakSkillCount` 分别来自错题本与薄弱技能库（tracking/improving）。前端在个人中心据此渲染 8 张统计卡（见 `08` §4）。

### 题库（R1）
| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/bank/categories` | 🔒 | 分类+题量 `[{category,count}]`（仅 enabled） |
| GET | `/api/bank/questions?category=&keyword=&page=&size=` | 🔒 | 用户浏览（Type B 分页，page 从 0）；`BankQuestionView{id,category,questionType,difficulty,content,knowledgePoints[]}`（**不含 answer/hint**，防剧透） |
| GET | `/api/admin/bank/questions` | ⭐ | 管理分页（Type B）：`BankAdminView{id,category,questionType,difficulty,content,answer?,hint?,knowledgePoints[],sourceAi,enabled,usageCount,updatedAt}` |
| POST | `/api/admin/bank/questions` | ⭐ | 新增 `BankUpsertRequest` |
| PUT | `/api/admin/bank/questions/{id}` | ⭐ | 修改 |
| DELETE | `/api/admin/bank/questions/{id}` | ⭐ | **逻辑删除**（置 enabled=false，保留历史引用） |
| POST | `/api/admin/bank/generate` | ⭐ | AI 一键扩充 `{category,count,difficulty}` → `{inserted}`（写入 source_ai=true） |

> ⚠️ `question_type` 口径（已按实现校准）：表与 DTO 保留 `qa/scene/design` 三枚举注释，但**实现只落地 `qa` 问答内容**——种子题与 AI 生成一律写 `qa`，练习引擎把题库行当"题干+参考答案"处理，未实现 scene/design 题型分支；管理员手动写入非 `qa` 值仅能存储、不会获得额外分支逻辑。前端/文档一律按 `qa` 处理。

### 薄弱技能库 / 错题本（R2）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/weak-skills` | 我的薄弱技能列表（severity 升序）：`WeakSkillView{id,skillTag,category,severity,latestScore?,interviewCount,lastSeen,status,evidence[]}` |
| POST | `/api/weak-skills/{id}/resolve` | 标记 resolved |
| GET | `/api/weak/wrong-questions?page=&size=` | 错题本（Type B，page 从 0）：`WrongQuestionView{interviewId,title,mode,orderIndex,questionBankId?,category?,question,myAnswer,score,referenceAnswer?,hint?}`（低分<60 快照） |

### 用户反馈（R4）
| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/feedback` | 🔒 | 提交 `{category:bug|suggestion|complaint|question|praise|other, content, contact?}` |
| GET | `/api/feedback` | 🔒 | 我提交的反馈列表 |
| GET | `/api/admin/feedback?status=&page=&size=` | ⭐ | 管理分页（Type B）；**仅返回普通用户提交的反馈（2026-09-07：隐藏提交人 role=admin 的行）** |
| PUT | `/api/admin/feedback/{id}` | ⭐ | 回复/改状态 `{status?, reply?}` |

### 简历对比（R3）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/compare/resumes` | 对比两份简历 `{resumeAId,resumeBId,targetPosition?}` → `CompareView` |
| GET | `/api/compare/list` | 我的对比历史 `[CompareListItem{id,resumeAId,resumeBId,title,differenceSummary?,createdAt}]` |
| GET | `/api/compare/{id}` | 单次对比详情（含 result） |
| DELETE | `/api/compare/{id}` | 删除 |

`CompareView{id,resumeAId,resumeBId,title,result:{resumeASummary?,resumeBSummary?,fields[{field,aValue,bValue,note}],skillOverlap[],onlyA[],onlyB[],strengthsA[],strengthsB[],differenceSummary?,advice[]},createdAt}`。

### 管理端：用户 / 账号 / 概览 / LLM 统计 / RAG（R9–R11）
| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/admin/users?keyword=&role=&active=&page=&size=` | ⭐ | 用户分页（Type B，R11 起支持 role=user\|admin、active=true\|false 过滤）：`AdminUserView{id,email,nickname?,role,active,createdAt?,lastLoginAt?}` |
| GET | `/api/admin/users/{userId}/interviews` | ⭐ | 该用户全部面试：`[AdminInterviewView{id,title,targetPosition,status,mode,questionCount,completedQuestionCount,matchScore?,overallRating?,startedAt?,completedAt?,createdAt,hasReport}]` |
| GET | `/api/admin/users/{userId}/interviews/{interviewId}/report` | ⭐ | 只读查看该用户某场面试的综合报告（返回体同 F06 `ReportResponse`；completed 缺报告会自动补生成） |
| PUT | `/api/admin/accounts/{id}/active` | ⭐ | 账号停用/拉黑(`{"active":false}`)/启用(true)：即时生效（逐请求校验 `is_active`）；**守卫：不能停用自己、不能停用最后一位启用中的管理员**（违反返回业务 message）→ `AdminUserView` |
| POST | `/api/admin/accounts/admins` | ⭐ | 新增管理员 `{email,password,nickname?}`（邮箱唯一、密码≥8 位）→ `AdminUserView` |
| GET | `/api/admin/stats/overview` | ⭐ | 后台概览卡片：`{userCount,adminCount,disabledUserCount,interviewCount,completedInterviewCount,inProgressInterviewCount,pendingFeedbackCount,questionCount}` |
| GET | `/api/admin/llm/stats?days=7` | ⭐ | DeepSeek 调用统计（近 N 天，≤90）：`{since,days,totalCalls,totalInChars,totalOutChars,totalCost,fallbackCalls,fallbackRate,promptBreakdown[],daily[]}` |
| GET | `/api/admin/llm/calls?page=&size=` | ⭐ | 调用明细分页（Type B）：`LlmCallView{id,userId,promptKey,model,inChars,outChars,estCost,latencyMs,fallback,status,createdAt}` |
| GET | `/api/admin/rag/stats` | ⭐ | RAG 索引统计：`{enabled,provider,embedding,dimension,totalChunks,userChunks,knowledgeChunks,qdrant:{configured,collection,connected,chunks}}` |
| POST | `/api/admin/rag/rebuild` | ⭐ | 全量重建 text_chunks（R10）；`provider=qdrant` 时含 Qdrant 镜像整库重建 → 返回 `{rebuild,resumes,jds,questionBank,totalChunks}` |
| GET | `/api/admin/rag/search?q=&topK=` | ⭐ | RAG 检索验收（无 token 成本）：`{provider:"qdrant"\|"local",query,hits:[{sourceType,label,score,content}]}`；`provider` 用于断言检索真走 Qdrant 还是回退 local；uid 缺省=全量（resume/jd/question_bank 均含）；**q 为空时返回 `provider="none"`、`hits=[]`** |
| GET | `/api/admin/rag/qdrant/health` | ⭐ | Qdrant 镜像健康：`{configured,collection,connected,chunks}` |

> ⚠️ **fallbackRate 换算口径（按代码校准）**：`GET /api/admin/llm/stats` 的 `fallbackRate` 后端已乘 100，**本身就是百分比数值**（如 `12.5` 表示 12.5%，非 0.125）。前端**直接展示 `{{fallbackRate}}%`，不要再 ×100**；若要判定"回退率偏高"，用 `fallbackCalls/totalCalls`（0~1 小数）比较。`daily[]` 每项含 `{date,calls,inChars,outChars,fallback,cost,prompts[]}`，`promptBreakdown[]` 含 `{promptKey,calls,inChars,outChars,fallback,cost}`。

## 3. SSE 事件格式（面试通道）

订阅 `POST /api/interviews/{id}/events`，事件行：
```
event: <type>
data: {"...": ...}
```
服务器 15s 心跳 `event: ping`。事件类型：

| type | data | 触发 |
|------|------|------|
| `status` | `{status}` | 面试/解析状态变化 |
| `parse_progress` | `{stage:"uploading|extracting|ai_parsing|done", resumeId}` | 简历/JD 异步解析 |
| `question` | `{orderIndex, content}` | 开始提问 |
| `delta` | `{key:"question", text:"增量片段"}` | LLM 流式 token（key 区分 question/grade/followup；当前 MVP 主链路未消费，见 §6） |
| `grade` | `{orderIndex,score,strong[],weak[],suggestion,resumeAdvice,suggestFollowup,referenceAnswer,hint}` | 本题点评完成 |
| `followup` | `{content}` | 追问问题已生成 |
| `report_done` | `{reportId}` | 报告生成完成 |
| `error` | `{code,message,retryable}` | 失败 |
| `done` | `{kind:"question"|"grade"|"followup"|"report", orderIndex?}` | 某段结束（前端收尾） |

**前端接入范式（POST 型 SSE）**：普通 `fetch` + `ReadableStream` 逐行解析 `data:` 行（原生 `EventSource` 只支持 GET，故用 fetch）。断线自动重订阅，通过 `GET /api/interviews/{id}/session` 拉当前快照补齐状态。

## 4. 代表性请求/响应示例

**登录（user 含 role）**
```jsonc
POST /api/auth/login
{"email":"a@b.com","password":"abc12345"}
// 200
{"code":0,"message":"ok","data":{"accessToken":"eyJ...","refreshToken":"eyJ...",
  "user":{"id":1,"email":"a@b.com","nickname":"张","role":"user"}}}
```

**创建正式面试 → 状态推进（示意）**
```jsonc
POST /api/interviews  {"title":"字节后端一面","targetPosition":"Java后端开发","questionCount":10,"mode":"formal"}
// data: {"id":101,"status":"draft","mode":"formal","weakBoost":false,"practiceCategory":null}

POST /api/interviews/101/resume   // multipart file=resume.pdf
// data: {"resumeId":9,"status":"processing"}   → 轮询 GET .../resume

POST /api/interviews/101/jd/text  {"rawText":"……JD 原文……"}
// data: {"jdId":7,"status":"processing"}

POST /api/interviews/101/match
// data: {"overall":72.5,"skill":80,"experience":60,"education":100,
//        "gap":[{"dimension":"经验","item":"高并发项目经历","severity":"HIGH","evidence":"..."}],
//        "focus":[{"index":1,"direction":"分布式事务设计","examinePoint":"...","prepare":"..."}],
//        "summary":"..."}
```

**练习面试（创建即开始，无需简历/JD/匹配）**
```jsonc
POST /api/interviews  {"title":"Java 集合练习","targetPosition":"Java后端","questionCount":5,"mode":"practice","practiceCategory":"Java"}
// data: {"id":202,"status":"draft","mode":"practice",...}
POST /api/interviews/202/start
// data: {"status":"in_progress","questionCount":5,"completedCount":0,"current":{"orderIndex":1,"focusIndex":0,"content":"请谈谈 Java 中 == 与 equals() 的区别..."}}
```

**作答 → 同步点评**
```jsonc
POST /api/interviews/101/questions/3/answer  {"answer":"我在 XX 项目里用 Redis 做了…","elapsedSeconds":88}
// 200（同步）：
// data: {"orderIndex":3,"score":78,"strong":["..."],"weak":["..."],"suggestion":"...","suggestFollowup":true,
//        "followUp":{"content":"追问：如果缓存雪崩了你怎么办？"},"nextQuestion":{"orderIndex":4,"focusIndex":4,"content":"..."},
//        "interviewStatus":"in_progress","finished":false,"resumeAdvice":"...","mode":"formal",
//        "referenceAnswer":"...","hint":"..."}
```

**文件流（不包 JSON 信封）**
```http
GET /api/jds/55/file
Authorization: Bearer <access>
# 200 → 原图/PDF/DOCX 字节，Content-Disposition: inline; filename="..."
```

## 5. 状态码与幂等约定

- 匹配/解析重复触发=重算（旧行替换），前端按钮带 loading 防连点即可。
- `answer`/`followup/answer`：服务端用 `orderIndex`+ 乐观锁（比对 `interview_questions.order_index` 与 `completed_question_count`）保证同一题只评一次，越序作答返回 409。
- `start`：已 completed/abandoned 再开始返回 409；formal 未匹配、practice 非 draft 直启都会 409。
- 删除/清空历史返回被删数量（DELETE /history 的 data=null，数量仅用于提示）。

## 6. 关于 SSE 的落地口径（重要）

**当前前端为「同步核心」交互，不消费 SSE**：`submitAnswer`/`submitFollowUp` 均同步返回评分与下一题，`AnswerResponse` 即含全部反馈字段；SSE 通道 `POST /events` 与 `delta` 事件仍保留，供后续做打字机增强与"多端推送"（如完成时广播 `report_done`）。断线/续面用 `GET /session` 拉快照补齐。
