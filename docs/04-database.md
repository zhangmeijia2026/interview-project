# 04 · 数据库设计（MySQL 8.3）

> 关系库 + JSON 列。`text_chunks` 已随 **R10（2026-09-07）启用**（本地 512 维哈希向量检索，owner_user_id 可空=全局知识；真 Qdrant 镜像也已连，`provider=qdrant` 可切，见 `07` §8/§11）——不再是"仅预留"。
> 建表脚本最终落在 `backend/db/schema.sql`（CREATE TABLE IF NOT EXISTS）；**2026-09-07 扩展**新增 5 张表并对既有表加列，增量迁移脚本在 `backend/db/migrations/2026-09-07_features.sql`（新库直接跑 schema.sql 即含全部 14 张表）。Hibernate `ddl-auto=validate`（不自动建/改表）。
> 最后更新：2026-09-07。

## 1. 库与字符集

```sql
CREATE DATABASE IF NOT EXISTS interview_system
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

- 一律 InnoDB；主键 `BIGINT UNSIGNED AUTO_INCREMENT`。
- `utf8mb4_0900_ai_ci`（MySQL 8 默认，ai=口音不敏感，够用）。若需更严格排序可 `utf8mb4_bin`。
- 金额/分数用 `DECIMAL(5,2)`（0–100）；时间统一 `DATETIME(3)`，Java 侧 `Instant/LocalDateTime` 存 UTC 或统一 Asia/Shanghai（**全项目只选一种**，推荐本地时区 Asia/Shanghai 由 JVM 时区保证，连接串 `serverTimezone=Asia/Shanghai`）。

## 2. 实体关系总览（ER 文字版，SRS §6.2 + 本体系扩展）

```
users 1──< interviews 1──< interview_questions N
users 1──< resumes             interviews N──|1 resumes
users 1──< job_descriptions    interviews N──|1 job_descriptions
users 1──1 user_settings       interviews 1──|1 match_results
                               interviews 1──|1 interview_reports
users 1──< user_weak_skills    interviews 1──< text_chunks（R10 向量块,来源: resume/jd/knowledge）
users 1──< user_feedback       resumes 1──< resume_comparisons（对比A/B均为本人简历）
users 1──< llm_call_logs(可空) question_bank ──< text_chunks(owner 可空=全局知识)
```

> 删除规则：interview 删除时级联删 questions/match/report 与其简历引用**解绑或级联**（业务上"删除面试=删除它这份简历快照引用"，注意 `resumes` 可被多次面试复用的语义——见 §5 删除约定）。

## 3. 表清单

| # | 表 | 来源 | 说明 |
|---|-----|------|------|
| 1 | `users` | SRS §6.1 | 用户（`role` user/admin，R9 管理端） |
| 2 | `user_settings` | 本体系(F08) | 模型/语言/通知/主题 |
| 3 | `interviews` | SRS §6.1 | 面试会话（含状态机；`mode` formal/practice、`weak_boost`、`practice_category` R7） |
| 4 | `resumes` | SRS §6.1 | 简历（含 Tika 原文 + parsed JSON） |
| 5 | `job_descriptions` | SRS §6.1 | JD |
| 6 | `match_results` | SRS §6.1 | 匹配分 + 差距 + 面试重点 |
| 7 | `interview_questions` | SRS §6.1 | 逐题 + 追问（`question_bank_id/reference_answer/hint/answered_seconds` R7/R8） |
| 8 | `interview_reports` | 本体系(F06) | 综合报告持久化（`analysis_json`/`resume_advice_json` R8b） |
| 9 | `text_chunks` | 本体系(R10) | 向量块（resume/jd/题库知识点；owner 可空=全局知识，embedding JSON 512 维哈希向量） |
| 10 | `question_bank` | R1 | 题库（category/question_type/difficulty/answer/hint/knowledge_points/source_ai/enabled/usage_count） |
| 11 | `user_feedback` | R4 | 用户反馈（category/content/contact/status/reply） |
| 12 | `user_weak_skills` | R2 | 个人薄弱技能库（skill_tag/severity/latest_score/evidence/status） |
| 13 | `resume_comparisons` | R3 | 简历对比（result_json） |
| 14 | `llm_call_logs` | R9 | DeepSeek 调用日志（管理统计：chars/cost/latency/fallback/status） |

> 设计说明：把 `match_results`/`interview_reports` 与 `interviews` 分开（而非堆在 interview 一行），避免大 JSON 挤占高频更新的 interview 行、并让"历史列表"轻量查询只扫 `interviews`。

## 4. 建表 DDL（schema.sql）

> **2026-09-07 校准**：本文件 §4 下方仍保留 MVP 基线（1–9 表）的 DDL 快照以便对照历史；**现行全量 14 张表 DDL 以 `backend/db/schema.sql` 为唯一源**，旧库升级执行 `backend/db/migrations/2026-09-07_features.sql`。相对下方快照的增量如下，实体已同步（ddl-auto=validate）：
> - `users` 增 `role VARCHAR(16) NOT NULL DEFAULT 'user'`（索引 `idx_users_role`）。
> - `interviews` 增 `mode('formal'|'practice')`、`weak_boost TINYINT(1)`、`practice_category VARCHAR(32)`。
> - `interview_questions` 增 `question_bank_id`、`reference_answer LONGTEXT`、`hint LONGTEXT`、`answered_seconds INT`。
> - `interview_reports` 已在基线含 `analysis_json`/`resume_advice_json`（下方快照含）——现网已建。
> - `text_chunks` 的 `owner_user_id` **改为可空**（=全局知识/题库知识点跨用户检索），`source_type` 增加 `knowledge/question_bank`。
> - 新增 10–14 表（question_bank、user_feedback、user_weak_skills、resume_comparisons、llm_call_logs），DDL 见 schema.sql/迁移脚本。

```sql
-- ============ 1. users ============
CREATE TABLE users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email         VARCHAR(100)  NOT NULL,
  password_hash VARCHAR(255)  NOT NULL,             -- bcrypt cost>=12
  nickname      VARCHAR(50)   NOT NULL,
  avatar_url    VARCHAR(500)  NULL,
  created_at    DATETIME(3)   NOT NULL,
  last_login_at DATETIME(3)   NULL,
  is_active     TINYINT(1)    NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';

-- ============ 2. user_settings (F08) ============
CREATE TABLE user_settings (
  user_id       BIGINT UNSIGNED NOT NULL,
  model_provider VARCHAR(32)  NOT NULL DEFAULT 'deepseek',  -- deepseek 预留多模型
  model_name     VARCHAR(64)  NOT NULL DEFAULT 'deepseek-chat',
  language       VARCHAR(8)   NOT NULL DEFAULT 'zh',        -- zh / en
  notify_enabled TINYINT(1)   NOT NULL DEFAULT 1,
  theme          VARCHAR(16)  NOT NULL DEFAULT 'light',
  updated_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_us_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户设置';

-- ============ 3. interviews ============
CREATE TABLE interviews (
  id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id                  BIGINT UNSIGNED NOT NULL,
  title                    VARCHAR(200) NOT NULL,
  target_position          VARCHAR(100) NOT NULL,
  status                   VARCHAR(24)  NOT NULL DEFAULT 'draft',
  -- 枚举: draft|resume_uploaded|jd_uploaded|matched|ready|in_progress|completed|abandoned
  resume_id                BIGINT UNSIGNED NULL,
  jd_id                    BIGINT UNSIGNED NULL,
  match_score              DECIMAL(5,2) NULL,
  question_count           INT NOT NULL DEFAULT 10,
  completed_question_count INT NOT NULL DEFAULT 0,
  overall_rating           VARCHAR(1) NULL,     -- S/A/B/C/D
  started_at               DATETIME(3) NULL,
  completed_at             DATETIME(3) NULL,
  created_at               DATETIME(3) NOT NULL,
  updated_at               DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_interviews_user (user_id, created_at),
  KEY idx_interviews_status (status),
  CONSTRAINT fk_it_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试会话';

-- ============ 4. resumes ============
CREATE TABLE resumes (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  file_name   VARCHAR(200) NOT NULL,
  file_path   VARCHAR(500) NOT NULL,
  file_md5    VARCHAR(32)  NOT NULL,
  full_text   LONGTEXT     NULL,
  parsed_data JSON         NULL,               -- {name,email,phone,education[],experience[],projects[],skills[]}
  status      VARCHAR(24)  NOT NULL DEFAULT 'processing', -- processing|ready|failed
  created_at  DATETIME(3)  NOT NULL,
  updated_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_res_user (user_id, created_at),
  UNIQUE KEY uk_resume_md5_user (user_id, file_md5),
  CONSTRAINT fk_rs_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历';

-- ============ 5. job_descriptions ============
CREATE TABLE job_descriptions (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  file_name   VARCHAR(200) NULL,
  file_path   VARCHAR(500) NULL,
  raw_text    LONGTEXT     NOT NULL,
  parsed_data JSON         NOT NULL,   -- {hard_skills[],soft_skills[],min_experience_years,education_requirement,responsibilities[]}
  status      VARCHAR(24)  NOT NULL DEFAULT 'processing',
  created_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_jd_user (user_id, created_at),
  CONSTRAINT fk_jd_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位描述';

-- ============ 6. match_results ============
CREATE TABLE match_results (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  interview_id     BIGINT UNSIGNED NOT NULL,
  overall_score    DECIMAL(5,2) NOT NULL,
  skill_match      DECIMAL(5,2) NOT NULL,
  experience_match DECIMAL(5,2) NOT NULL,
  education_match  DECIMAL(5,2) NOT NULL,
  gap_analysis     JSON NOT NULL,    -- [{dimension,item,severity,note}]
  interview_focus  JSON NOT NULL,    -- [{index,question_direction,examine_point,suggest_prepare}]
  created_at       DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_interview (interview_id),
  CONSTRAINT fk_mc_interviews FOREIGN KEY (interview_id) REFERENCES interviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配结果';

-- ============ 7. interview_questions ============
CREATE TABLE interview_questions (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  interview_id        BIGINT UNSIGNED NOT NULL,
  focus_index         INT NOT NULL,             -- 对应面试重点序号 1..N
  order_index         INT NOT NULL,             -- 题目顺序(同一面试内)
  content             LONGTEXT NOT NULL,        -- AI 生成的问题
  user_answer         LONGTEXT NULL,
  score               DECIMAL(5,2) NULL,
  feedback_strong     JSON NULL,                -- 优点列表
  feedback_weak       JSON NULL,                -- 不足列表
  feedback_suggestion LONGTEXT NULL,            -- 对作答的改进建议
  resume_advice       LONGTEXT NULL,            -- 对简历的修改建议(AI 每题给出)
  follow_up_question  LONGTEXT NULL,
  follow_up_answer    LONGTEXT NULL,
  follow_up_score     DECIMAL(5,2) NULL,
  created_at          DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_q_interview (interview_id, order_index),
  CONSTRAINT fk_q_interviews FOREIGN KEY (interview_id) REFERENCES interviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试题目';

-- ============ 8. interview_reports (F06, 本体系新增) ============
CREATE TABLE interview_reports (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  interview_id     BIGINT UNSIGNED NOT NULL,
  overall_rating   VARCHAR(1) NULL,              -- S/A/B/C/D
  dimensions       JSON      NULL,   -- {"job_match":..,"professional":..,"expression":..,"logic":..,"adaptability":..,"learning":..} 0-100
  review_summary   LONGTEXT  NULL,   -- 匹配度回顾文字
  strengths        JSON NULL,        -- 已掌握项
  improvements     JSON NULL,        -- 待加强项+计划
  recommendation   LONGTEXT NULL,    -- 综合建议
  raw_llm_json     JSON      NULL,   -- 原始 LLM 输出留档
  analysis_json    JSON      NULL,   -- 深度报告分析(report-analysis)：{overallAnalysis,dimensionAnalyses[{dimension,score,analysis,advice}],conclusion}（docs/07 §5，模型/离线同构）
  resume_advice_json JSON    NULL,   -- 详细简历修改建议：{intro,groups[{title,priority(HIGH/MED/LOW),items[]}]}
  created_at       DATETIME(3) NOT NULL,
  updated_at       DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_interview (interview_id),
  CONSTRAINT fk_rp_interviews FOREIGN KEY (interview_id) REFERENCES interviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='综合报告';

-- 2026-09-05 增强：面试结果详细分析 + 详细简历修改建议
-- ALTER TABLE interview_reports ADD COLUMN analysis_json JSON NULL AFTER raw_llm_json;
-- ALTER TABLE interview_reports ADD COLUMN resume_advice_json JSON NULL AFTER analysis_json;

-- ============ 9. text_chunks (R10 向量检索, 2026-09-07 启用) ============
-- owner_user_id 可空=全局知识(题库知识点, 跨用户检索)；embedding 以 JSON 数组存 512 维哈希向量；
-- 检索=Java 余弦 Top-k(见 07 §8/§11)。来源: resume|jd|knowledge|question_bank。
CREATE TABLE text_chunks (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT UNSIGNED NULL,    -- 可空=全局知识
  source_type VARCHAR(16)  NOT NULL,     -- resume|jd|knowledge|question_bank
  source_id   BIGINT UNSIGNED NOT NULL,  -- resumes.id / job_descriptions.id / question_bank.id
  chunk_index INT NOT NULL,
  content     LONGTEXT NOT NULL,
  embedding   JSON NULL,                 -- [0.012, -0.045, ...] 定长数组
  created_at  DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chunks_owner (owner_user_id),
  KEY idx_chunks_src (source_type, source_id),
  CONSTRAINT fk_chunks_users FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文本向量块(向量检索)';
```

> **书写纪律**：全库 COLLATE 统一 `utf8mb4_0900_ai_ci`。生成 `schema.sql` 前通读一遍，复制时不要带入任何"纠错注释"类残留。

## 5. 数据约定与删除策略

- **数据隔离**：一切查询首条件是 `user_id = 当前登录用户`（Service 层强制，勿只靠前端过滤）；多租户越权是安全验收红线。
- **黑名单/停用 = `users.is_active=false`（2026-09-07，本次无 DDL）**：管理端"黑名单管理/停用"即把该账号 `is_active` 置 false，**即时生效**——`JwtAuthFilter` 与 `AdminOnlyInterceptor` 每请求校验 active，已签发 access 立即失效；登录/刷新也已拒绝非启用账号。守卫：不能停用自己、不能停用最后一位启用中的管理员。
- **删除面试**（F02/F07）：`ON DELETE CASCADE` 会删 questions/match/report；`resumes`/`job_descriptions` 若被**多场面试引用**则只解绑 `interviews.resume_id/jd_id=NULL`，不物理删文件与行；单场引用、且该用户不再引用时可连同文件删（本期做"仅解绑 + 文件软保留"更安全）。
- **状态机**流转见 `02` §5；`status` 用 VARCHAR + 应用层枚举常量（不用 DB ENUM，便于扩展）。同名字段请与 Java `enum InterviewStatus` 保持单一事实源。
- **JSON 列**：写入前用 Jackson 序列化；JPA 里这些字段类型用 `String`（`@Column(columnDefinition="json")`）并在 Service 里做 `ObjectMapper` 转换，最稳、不依赖 Hibernate 对 MySQL JSON 的方言支持。

## 6. text_chunks 用法（R10 已启用，2026-09-07）

- **写库时机**：简历/JD 解析 ready 后、题库 新增/修改/软删 后、应用启动、`POST /api/admin/rag/rebuild` 全量重建（`ai/rag/RagStartupIndexer` + `TextChunkService`）。
- **分块**：按段落聚合 ≤600 字/块；**向量=本地哈希特征向量 512 维**（`HashEmbeddingService`，双哈希 + 1+ln(freq) + L2 归一，无外部模型依赖），embedding 存 `text_chunks.embedding`(JSON float[])。
- **检索**：`RagInjector` 对真实 DeepSeek 的 question/grade/follow-up 调用前，取"当前用户本人简历/JD + 全局题库知识点"余弦 Top-k（默认 4）拼 `{knowledge}` 注入 prompt（模板占位+防编造约束见 `07` §8）。
- **表结构不变**：`text_chunks` 始终是内容/向量**唯一源**；`provider=qdrant` 时其镜像进 Qdrant 集合 `interview_chunks`（512/Cosine，payload `owner=0` 全局/`owner=uid` 本人），检索改走镜像但命中口径与 local 一致（`QdrantClient`，2026-09-07 已连）。
- **provider=local 为默认**（无需 Redis/外部向量库/模型）；`provider=qdrant` 缺服务/空集自动回退 local（切启手册见 `07` §11）。
- 代码只在 `ai/rag/` 与少量 Service 接入，**对外 REST 接口不变**（`docs/06` 新增 `/api/admin/rag/*` 运维端点）。

## 7. 落地动作（Phase 1）

1. 按 §4 生成 `backend/db/schema.sql`（核对字符集与注释，确保干净可执行）。
2. 在 MySQL 执行；`SHOW CREATE TABLE users;` 复核字符集。
3. 实体类与表一一对应（entity 命名 snake_case↔camelCase 由 JPA 命名策略处理）。
4. `ddl-auto: validate` 启动一次确认无映射告警。
