-- ============================================================
-- 2026-09-07 功能扩展迁移（题库/模式/参考回答/薄弱库/反馈/对比/调用日志/角色）
-- 适用: 已有 interview_system 库（schema.sql 是 CREATE IF NOT EXISTS，不会给旧表加列）。
-- 幂等: 新表 CREATE TABLE IF NOT EXISTS；加列用 information_schema 判存在（MySQL 无 ADD COLUMN IF NOT EXISTS）。
-- 用法: mysql -u root -p interview_system < 2026-09-07_features.sql
-- ============================================================

USE interview_system;

-- ---------- 辅助过程：缺列才加 ----------
DROP PROCEDURE IF EXISTS _add_col;
DELIMITER $$
CREATE PROCEDURE _add_col(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(300))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$
DELIMITER ;

CALL _add_col('users','role', "role VARCHAR(16) NOT NULL DEFAULT 'user'");

CALL _add_col('interviews','mode', "mode VARCHAR(16) NOT NULL DEFAULT 'formal'");
CALL _add_col('interviews','weak_boost', "weak_boost TINYINT(1) NOT NULL DEFAULT 0");
CALL _add_col('interviews','practice_category', "practice_category VARCHAR(32) NULL");

CALL _add_col('interview_questions','question_bank_id', "question_bank_id BIGINT UNSIGNED NULL");
CALL _add_col('interview_questions','reference_answer', "reference_answer LONGTEXT NULL");
CALL _add_col('interview_questions','hint', "hint LONGTEXT NULL");
CALL _add_col('interview_questions','answered_seconds', "answered_seconds INT NULL");

-- text_chunks.owner_user_id 改为可空（全局知识块，如题库知识点跨用户检索）
DROP PROCEDURE IF EXISTS _mod_owner;
DELIMITER $$
CREATE PROCEDURE _mod_owner()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'text_chunks'
               AND COLUMN_NAME = 'owner_user_id' AND IS_NULLABLE = 'NO') THEN
    ALTER TABLE text_chunks MODIFY COLUMN owner_user_id BIGINT UNSIGNED NULL;
  END IF;
END$$
DELIMITER ;
CALL _mod_owner();

DROP PROCEDURE IF EXISTS _add_col;
DROP PROCEDURE IF EXISTS _mod_owner;

-- ---------- 新增 5 张表 ----------
CREATE TABLE IF NOT EXISTS question_bank (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  category         VARCHAR(32)  NOT NULL,
  question_type    VARCHAR(16)  NOT NULL DEFAULT 'qa',
  difficulty       INT          NOT NULL DEFAULT 3,
  content          LONGTEXT     NOT NULL,
  answer           LONGTEXT     NULL,
  hint             LONGTEXT     NULL,
  knowledge_points JSON         NULL,
  source_ai        TINYINT(1)   NOT NULL DEFAULT 0,
  enabled          TINYINT(1)   NOT NULL DEFAULT 1,
  usage_count      INT          NOT NULL DEFAULT 0,
  created_by       BIGINT UNSIGNED NOT NULL,
  created_at       DATETIME(3)  NOT NULL,
  updated_at       DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_qb_category (category, enabled),
  KEY idx_qb_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题库';

CREATE TABLE IF NOT EXISTS user_feedback (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  category   VARCHAR(32)  NOT NULL DEFAULT 'suggestion',
  content    LONGTEXT     NOT NULL,
  contact    VARCHAR(100) NULL,
  status     VARCHAR(16)  NOT NULL DEFAULT 'new',
  reply      LONGTEXT     NULL,
  replied_at DATETIME(3)  NULL,
  created_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_fb_user (user_id, created_at),
  KEY idx_fb_status (status),
  CONSTRAINT fk_fb_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户反馈';

CREATE TABLE IF NOT EXISTS user_weak_skills (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id         BIGINT UNSIGNED NOT NULL,
  skill_tag       VARCHAR(64)  NOT NULL,
  category        VARCHAR(32)  NOT NULL DEFAULT 'general',
  severity        INT          NOT NULL DEFAULT 50,
  latest_score    INT          NULL,
  interview_count INT          NOT NULL DEFAULT 0,
  first_seen      DATETIME(3)  NOT NULL,
  last_seen       DATETIME(3)  NOT NULL,
  evidence_json   JSON         NULL,
  status          VARCHAR(16)  NOT NULL DEFAULT 'tracking',
  updated_at      DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_weak_user_skill (user_id, skill_tag),
  CONSTRAINT fk_wk_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人薄弱技能库';

CREATE TABLE IF NOT EXISTS resume_comparisons (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  resume_a_id  BIGINT UNSIGNED NOT NULL,
  resume_b_id  BIGINT UNSIGNED NOT NULL,
  title        VARCHAR(200) NOT NULL,
  result_json  JSON         NULL,
  summary      LONGTEXT     NULL,
  created_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cmp_user (user_id, created_at),
  CONSTRAINT fk_cmp_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历对比';

CREATE TABLE IF NOT EXISTS llm_call_logs (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NULL,
  prompt_key  VARCHAR(32)  NOT NULL,
  model       VARCHAR(64)  NULL,
  in_chars    INT NOT NULL DEFAULT 0,
  out_chars   INT NOT NULL DEFAULT 0,
  est_cost    DECIMAL(8,4) NOT NULL DEFAULT 0,
  latency_ms  INT NOT NULL DEFAULT 0,
  fallback    TINYINT(1)  NOT NULL DEFAULT 0,
  status      VARCHAR(16) NOT NULL DEFAULT 'ok',
  created_at  DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_llm_key (prompt_key, created_at),
  KEY idx_llm_created (created_at),
  KEY idx_llm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型调用日志(管理统计)';
