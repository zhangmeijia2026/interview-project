# CLAUDE.md — 智能面试问答系统 项目工作手册（Claude Code 版）

> 本文件是 **Claude Code** 在本仓库中每个会话自动加载的"工作手册"，也是团队（5班-5组）的协作入口。
> 遇到任何与本项目相关的任务，**先读本文，再按 `docs/10-roadmap.md` 的当前阶段推进**。

## 0. 项目一句话

基于 **Spring Boot 3 + Spring AI + DeepSeek + MySQL 8.3 + Redis + Vue 3** 的「简历-JD 智能匹配 + 多轮 AI 模拟面试 + 综合报告」全栈 RAG 应用（5班-5组：张帆、刘小娴、张美佳、彭雅奇）。

## 1. 技术基线（已与团队确认，2026-09-02；2026-09-05 六项增强校对；2026-09-07 R1–R10/P1–P7 大扩展收口校对）

| 层 | 选型 | 说明 / 裁决 |
|----|------|------------|
| 语言 | Java **17**（Temurin 17.0.20.1，已装） | JDK17 无虚拟线程 → 走默认 Tomcat 线程池 |
| 后端 | Spring Boot **3.x**（以 start.spring.io 当前 GA 为准） | Servlet + Spring MVC，不用 WebFlux |
| AI | Spring AI **1.1.8**（`spring-ai-starter-model-openai`，2026-09-05 落地） | 运行时统一走 **DeepSeek**（`deepseek-chat`）；**双模式**：`ai.mock-enabled=true`=离线确定性引擎（默认演示/CI）、`false`=真实 DeepSeek，真实调用任一步失败**自动回退离线**（`ai/DeepSeekAIService`+`LocalMockAIService`+`JsonSupport`+`CostGuard`，见 `docs/05 §5`、`docs/07 §10`）；"Claude"指本开发工具 Claude Code |
| DB | **MySQL 8.3**（服务名 `MySQL83`，运行中） | 关系库 + JSON 列；**不加 pgvector** |
| 向量 | **R10 已落地**（2026-09-07）：本地 512 维**特征哈希向量**（无外部模型），`text_chunks` 启用，检索把命中块以 `{knowledge}` 注入 question/grade/follow-up；**真 Qdrant 已连**（`QdrantClient` 镜像 `interview_chunks` 512/Cosine，2026-09-07） | 默认 `provider=local`（CI/离线）；`provider=qdrant` 经环境变量 `RAG_PROVIDER=qdrant`+`QDRANT_URL` 切换，缺服务/空集自动回退 local（见 `docs/07 §8/§11`、`docs/04 §6`、`docs/06` admin rag 端点）；换 embedding 只需加 `EmbeddingService` 实现 |
| 缓存 | Redis 5.0.14.1（Windows，已装） | 会话缓存 / 登录失败锁 / 刷新令牌 / SSE 注册表 |
| 解析 | Apache Tika + **Tess4J OCR** | PDF/DOCX 简历与 JD；**JD 图片(jpg/jpeg/png/bmp/webp) 走 Tess4J `chi_sim+eng` OCR**——tess4j jar 自带 Windows 原生库、无需系统装 Tesseract，语言数据放 gitignore 的 `backend/ocr-tessdata/`（见 `docs/03 §3.5`、`docs/05 §6`） |
| 前端 | Vue 3 + Vite + TS + Pinia + Element Plus + ECharts | UI 规范主色 `#1A237E` |
| 存储 | 本地文件系统 | 上传目录可配置 |
| 文档 | docs/ 中文 Markdown | 本仓库非 ASCII 父路径，见 `docs/03` 的路径注意 |

## 2. 仓库布局（monorepo，本目录即根）

```
D:\生产实习Claude\interview\          ← 本仓库根（git init 于此）
├─ CLAUDE.md            本文件
├─ docs\                文档体系（先读 00-INDEX）
├─ backend\             后端工程（10-roadmap Phase 1 起生成）
├─ frontend\            前端工程（Phase 7 起生成）
└─ .claude\
   ├─ settings.local.json   本机权限白名单
   └─ agents\*.md           子代理定义（backend / frontend / rag / reviewer）
```

## 3. 硬性规约（Claude Code 必须遵守）

1. **语言**：本仓库文档、代码注释、提交信息一律中文为主；标识符用英文。
2. **密钥**：`DEEPSEEK_API_KEY`、MySQL 密码等一律走**环境变量 / `application-local.yml`（不提交）**，禁止硬编码进源码、日志、README。
3. **版本诚实**：本文档给出的版本是"下限/参考"。真正落库版本以 `start.spring.io` 生成的 `pom.xml` 与所选 Spring AI 版本文档为准；不确定的属性名、坐标，先查官方文档再写，不要编造。
4. **路径校验**：执行任何涉及 JDK/Maven/MySQL/Redis 的命令前，先用 `ls`/`where` 核实存在再执行（Windows 路径含中文）。
5. **数据库**：一律 utf8mb4；DDL 以 `docs/04-database.md` 为准，改动先改文档后改代码。
6. **REST 契约**：接口实现必须以 `docs/06-api-contract.md` 为准，前后端不各自定义。
7. **LLM 调用**：只允许经 `AIService`（`docs/05` 定义）封装，业务层不得直接 new ChatClient；Prompt 模板统一放 `docs/07` 约定目录。
8. **不提交 .env、上传文件、target/、node_modules/**。

## 4. 常用命令（Windows，git-bash 可用）

```bash
# 后端
export JAVA_HOME="D:/生产实习Claude/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
"/d/生产实习Claude/maven/apache-maven-3.9.16/bin/mvn.cmd" -v          # 验证
mvn spring-boot:run                                                   # 启动（在 backend/ 下）
mvn clean test                                                        # 跑测试

# 数据库
"/c/Program Files/MySQL/MySQL Server 8.3/bin/mysql.exe" -u root -p    # 登录

# Redis
"/d/生产实习Claude/redis/redis-server.exe"                            # 启动 Redis

# 前端
"/d/生产实习Claude/node/npm.cmd" run dev                              # 在 frontend/ 下
```

## 5. 文档地图（先读顺序）

| 目的 | 文档 |
|------|------|
| 我从哪开始？当前做到哪一步？ | `docs/10-roadmap.md` |
| 全部文档清单 | `docs/00-INDEX.md` |
| 需求原文的精炼 + 与 SRS 的差异裁决 | `docs/01-requirements.md` |
| 架构、模块、关键流程 | `docs/02-architecture.md` |
| 本机环境与初始化命令 | `docs/03-environment.md` |
| 建表与数据设计 | `docs/04-database.md` |
| 后端工程结构与规范 | `docs/05-backend.md` |
| 接口契约（REST + SSE） | `docs/06-api-contract.md` |
| RAG / LLM / Prompt / 成本 | `docs/07-rag-llm.md` |
| 前端结构与对接 | `docs/08-frontend.md` |
| 测试策略 | `docs/09-testing.md` |

## 6. 给 Claude Code 的工作提示

- 大任务拆阶段：一次性开发量超过"一个功能模块"时，遵循 `docs/10-roadmap.md` 的阶段与验收；每个阶段先和用户确认范围。
- 需要分工并行（如前端、后端同时开工）时，用 `.claude/agents/` 下的子代理并分配任务。
- 本仓库由多人协作：**每个功能提交前跑相关测试**；修改接口契约或数据表时提醒团队同步。
- 文档即代码：发现文档与实际不一致，先改文档再修实现，并在 commit message 注明。
