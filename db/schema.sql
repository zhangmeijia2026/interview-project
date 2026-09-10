-- ============================================================
-- 智能面试问答系统  schema.sql  (MySQL 8.3)
-- 依据: docs/04-database.md §4   ·   2026-09-02
-- 用法: mysql -u root -p < schema.sql    (需先有 root 权限)
-- 全部 utf8mb4 / utf8mb4_0900_ai_ci / InnoDB
-- 幂等: 仅 CREATE TABLE IF NOT EXISTS；删库请手动执行 DROP
-- ============================================================

CREATE DATABASE IF NOT EXISTS interview_system
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE interview_system;

-- ---------- 1. users ----------
CREATE TABLE IF NOT EXISTS users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email         VARCHAR(100)  NOT NULL,
  password_hash VARCHAR(255)  NOT NULL,               -- bcrypt cost>=12
  nickname      VARCHAR(50)   NOT NULL,
  avatar_url    VARCHAR(500)  NULL,
  created_at    DATETIME(3)   NOT NULL,
  last_login_at DATETIME(3)   NULL,
  is_active     TINYINT(1)    NOT NULL DEFAULT 1,
  role          VARCHAR(16)   NOT NULL DEFAULT 'user',    -- user / admin
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';

-- ---------- 2. user_settings (F08) ----------
CREATE TABLE IF NOT EXISTS user_settings (
  user_id        BIGINT UNSIGNED NOT NULL,
  model_provider VARCHAR(32)  NOT NULL DEFAULT 'deepseek',
  model_name     VARCHAR(64)  NOT NULL DEFAULT 'deepseek-chat',
  language       VARCHAR(8)   NOT NULL DEFAULT 'zh',
  notify_enabled TINYINT(1)   NOT NULL DEFAULT 1,
  theme          VARCHAR(16)  NOT NULL DEFAULT 'light',
  updated_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_us_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户设置';

-- ---------- 3. interviews ----------
CREATE TABLE IF NOT EXISTS interviews (
  id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id                  BIGINT UNSIGNED NOT NULL,
  title                    VARCHAR(200) NOT NULL,
  target_position          VARCHAR(100) NOT NULL,
  status                   VARCHAR(24)  NOT NULL DEFAULT 'draft',
  resume_id                BIGINT UNSIGNED NULL,
  jd_id                    BIGINT UNSIGNED NULL,
  match_score              DECIMAL(5,2) NULL,
  question_count           INT NOT NULL DEFAULT 10,
  completed_question_count INT NOT NULL DEFAULT 0,
  mode                     VARCHAR(16)  NOT NULL DEFAULT 'formal', -- formal=正式(评级) / practice=练习(无评级)
  weak_boost               TINYINT(1)   NOT NULL DEFAULT 0,        -- 针对薄弱技能加强提问
  practice_category        VARCHAR(32)  NULL,                      -- 练习模式的题库分类(可选)
  overall_rating           VARCHAR(1) NULL,
  started_at               DATETIME(3) NULL,
  completed_at             DATETIME(3) NULL,
  created_at               DATETIME(3) NOT NULL,
  updated_at               DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_interviews_user (user_id, created_at),
  KEY idx_interviews_status (status),
  CONSTRAINT fk_it_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试会话';

-- ---------- 4. resumes ----------
CREATE TABLE IF NOT EXISTS resumes (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  file_name   VARCHAR(200) NOT NULL,
  file_path   VARCHAR(500) NOT NULL,
  file_md5    VARCHAR(32)  NOT NULL,
  full_text   LONGTEXT     NULL,
  parsed_data JSON         NULL,
  status      VARCHAR(24)  NOT NULL DEFAULT 'processing',
  created_at  DATETIME(3)  NOT NULL,
  updated_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_res_user (user_id, created_at),
  UNIQUE KEY uk_resume_md5_user (user_id, file_md5),
  CONSTRAINT fk_rs_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历';

-- ---------- 5. job_descriptions ----------
CREATE TABLE IF NOT EXISTS job_descriptions (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  file_name   VARCHAR(200) NULL,
  file_path   VARCHAR(500) NULL,
  raw_text    LONGTEXT     NOT NULL,
  parsed_data JSON         NOT NULL,
  status      VARCHAR(24)  NOT NULL DEFAULT 'processing',
  created_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_jd_user (user_id, created_at),
  CONSTRAINT fk_jd_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位描述';

-- ---------- 6. match_results ----------
CREATE TABLE IF NOT EXISTS match_results (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  interview_id     BIGINT UNSIGNED NOT NULL,
  overall_score    DECIMAL(5,2) NOT NULL,
  skill_match      DECIMAL(5,2) NOT NULL,
  experience_match DECIMAL(5,2) NOT NULL,
  education_match  DECIMAL(5,2) NOT NULL,
  gap_analysis     JSON NOT NULL,
  interview_focus  JSON NOT NULL,
  created_at       DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_interview (interview_id),
  CONSTRAINT fk_mc_interviews FOREIGN KEY (interview_id) REFERENCES interviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配结果';

-- ---------- 7. interview_questions ----------
CREATE TABLE IF NOT EXISTS interview_questions (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  interview_id        BIGINT UNSIGNED NOT NULL,
  focus_index         INT NOT NULL,
  order_index         INT NOT NULL,
  question_bank_id    BIGINT UNSIGNED NULL,   -- 来自题库的出题来源(练习/薄弱训练)
  content             LONGTEXT NOT NULL,
  reference_answer    LONGTEXT NULL,          -- AI 每题额外输出的参考回答(复盘对照)
  hint                LONGTEXT NULL,          -- 提示(练习模式随时可见/正式模式作答后可见)
  answered_seconds    INT NULL,               -- 正式模式本题作答耗时(秒)
  user_answer         LONGTEXT NULL,
  score               DECIMAL(5,2) NULL,
  feedback_strong     JSON NULL,
  feedback_weak       JSON NULL,
  feedback_suggestion LONGTEXT NULL,
  resume_advice       LONGTEXT NULL,
  follow_up_question  LONGTEXT NULL,
  follow_up_answer    LONGTEXT NULL,
  follow_up_score     DECIMAL(5,2) NULL,
  created_at          DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_q_interview (interview_id, order_index),
  CONSTRAINT fk_q_interviews FOREIGN KEY (interview_id) REFERENCES interviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试题目';

-- ---------- 8. interview_reports (F06) ----------
CREATE TABLE IF NOT EXISTS interview_reports (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  interview_id   BIGINT UNSIGNED NOT NULL,
  overall_rating VARCHAR(1) NULL,
  dimensions     JSON      NULL,
  review_summary LONGTEXT  NULL,
  strengths      JSON      NULL,
  improvements   JSON      NULL,
  recommendation   LONGTEXT  NULL,
  raw_llm_json     JSON      NULL,
  analysis_json    JSON      NULL,   -- 深度报告分析(report-analysis)，docs/07 §5
  resume_advice_json JSON    NULL,   -- 详细简历修改建议(分组优先级)
  created_at       DATETIME(3) NOT NULL,
  updated_at       DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_interview (interview_id),
  CONSTRAINT fk_rp_interviews FOREIGN KEY (interview_id) REFERENCES interviews(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='综合报告';

-- ---------- 9. text_chunks (二期向量预留) ----------
CREATE TABLE IF NOT EXISTS text_chunks (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT UNSIGNED NULL,     -- 可空=全局知识(题库知识点等，跨用户检索)
  source_type   VARCHAR(16)  NOT NULL,    -- resume|jd|knowledge|question_bank
  source_id     BIGINT UNSIGNED NOT NULL,
  chunk_index   INT NOT NULL,
  content       LONGTEXT NOT NULL,
  embedding     JSON      NULL,           -- float 数组(本地 ONNX bge 512 维)
  created_at    DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chunks_owner (owner_user_id),
  KEY idx_chunks_src (source_type, source_id),
  CONSTRAINT fk_chunks_users FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文本向量块(向量检索)';

-- ---------- 10. question_bank (题库，R1) ----------
CREATE TABLE IF NOT EXISTS question_bank (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  category         VARCHAR(32)  NOT NULL,        -- Java/Python/前端/算法/数据库/网络/OS/软技能…
  question_type    VARCHAR(16)  NOT NULL DEFAULT 'qa',   -- qa(问答)/scene(场景)/design(设计)
  difficulty       INT          NOT NULL DEFAULT 3,      -- 1~5
  content          LONGTEXT     NOT NULL,                -- 题目内容
  answer           LONGTEXT     NULL,                    -- 参考答案
  hint             LONGTEXT     NULL,                    -- 提示
  knowledge_points JSON         NULL,                    -- 知识点标签数组
  source_ai        TINYINT(1)   NOT NULL DEFAULT 0,      -- 是否 AI 生成
  enabled          TINYINT(1)   NOT NULL DEFAULT 1,
  usage_count      INT          NOT NULL DEFAULT 0,      -- 被用于练习/面试次数
  created_by       BIGINT UNSIGNED NOT NULL,             -- 创建者(管理员)
  created_at       DATETIME(3)  NOT NULL,
  updated_at       DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_qb_category (category, enabled),
  KEY idx_qb_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题库';

-- ---------- 11. user_feedback (用户反馈，R4) ----------
CREATE TABLE IF NOT EXISTS user_feedback (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  category   VARCHAR(32)  NOT NULL DEFAULT 'suggestion', -- bug/suggestion/complaint/other
  content    LONGTEXT     NOT NULL,
  contact    VARCHAR(100) NULL,                           -- 联系方式(邮箱等，可选)
  status     VARCHAR(16)  NOT NULL DEFAULT 'new',         -- new/processing/done
  reply      LONGTEXT     NULL,                           -- 管理员回复
  replied_at DATETIME(3)  NULL,
  created_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_fb_user (user_id, created_at),
  KEY idx_fb_status (status),
  CONSTRAINT fk_fb_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户反馈';

-- ---------- 12. user_weak_skills (薄弱技能库，R2) ----------
CREATE TABLE IF NOT EXISTS user_weak_skills (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id        BIGINT UNSIGNED NOT NULL,
  skill_tag      VARCHAR(64)  NOT NULL,          -- 归一化技能名(如 Java-集合)
  category       VARCHAR(32)  NOT NULL DEFAULT 'general',
  severity       INT          NOT NULL DEFAULT 50,  -- 0~100 越低调越弱(由 AI 依报告给出)
  latest_score   INT          NULL,                  -- 最近一次相关作答均分
  interview_count INT         NOT NULL DEFAULT 0,    -- 命中面试/练习次数
  first_seen     DATETIME(3)  NOT NULL,
  last_seen      DATETIME(3)  NOT NULL,
  evidence_json  JSON         NULL,                  -- 来源(面试/题目/维度/低分证据)摘要
  status         VARCHAR(16)  NOT NULL DEFAULT 'tracking', -- tracking/improving/resolved
  updated_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_weak_user_skill (user_id, skill_tag),
  CONSTRAINT fk_wk_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人薄弱技能库';

-- ---------- 13. resume_comparisons (简历对比，R3) ----------
CREATE TABLE IF NOT EXISTS resume_comparisons (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  resume_a_id  BIGINT UNSIGNED NOT NULL,
  resume_b_id  BIGINT UNSIGNED NOT NULL,
  title        VARCHAR(200) NOT NULL,
  result_json  JSON         NULL,     -- 对比报告(AI/离线同构)
  summary      LONGTEXT     NULL,
  created_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cmp_user (user_id, created_at),
  CONSTRAINT fk_cmp_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历对比';

-- ---------- 14. llm_call_logs (DeepSeek 调用统计，R9) ----------
CREATE TABLE IF NOT EXISTS llm_call_logs (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NULL,     -- 发起用户(离线匿名等可空)
  prompt_key  VARCHAR(32)  NOT NULL,    -- grade/match/report-analysis/…
  model       VARCHAR(64)  NULL,
  in_chars    INT NOT NULL DEFAULT 0,   -- 输入字符近似量
  out_chars   INT NOT NULL DEFAULT 0,   -- 输出字符近似量
  est_cost    DECIMAL(8,4) NOT NULL DEFAULT 0,  -- 估算费用(元)
  latency_ms  INT NOT NULL DEFAULT 0,
  fallback    TINYINT(1)  NOT NULL DEFAULT 0,   -- 是否回退离线引擎
  status      VARCHAR(16) NOT NULL DEFAULT 'ok',-- ok/parse_fail/fallback/error
  created_at  DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_llm_key (prompt_key, created_at),
  KEY idx_llm_created (created_at),
  KEY idx_llm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型调用日志(管理统计)';
