# 智能面试问答系统（5班-5组）

基于 **Spring Boot 3 + Spring AI(DeepSeek) + MySQL 8.3 + Redis + Vue 3** 的简历-JD 匹配 + 多轮 AI 模拟面试 + 综合报告系统（毕业设计）。

> 详细文档与开发路线见 **`docs/00-INDEX.md`**；Claude Code 会话自动加载 **`CLAUDE.md`**。
> 本文档体系 = 唯一需求/设计事实源（SRS 与实装差异裁决见 `docs/01`）。

## 目录

```
backend/   Spring Boot 后端（db/schema.sql 建库脚本；F01–F08 + R1–R10 已落地）
frontend/  Vue3 前端（题库/薄弱库/个人中心/反馈/对比/管理后台等全量页面）
docs/      Claude Code CLI 文档体系（00 索引 ~ 10 路线图）
```

## 快速开始（后端）

```bash
# 0) 先按 docs/03 §3 完成一次环境初始化（建库、跑 Redis）

# 1) 复制本地配置并填入密码
cd backend
cp src/main/resources/application-local.example.yml src/main/resources/application-local.yml

# 2) 运行（需先设置 MYSQL_ROOT_PASSWORD 或直接编辑 local 文件）
export JAVA_HOME="D:/生产实习Claude/jdk17"
"/d/生产实习Claude/maven/apache-maven-3.9.16/bin/mvn.cmd" spring-boot:run
#   接口文档: http://localhost:8080/swagger-ui.html
#   健康检查: http://localhost:8080/api/ping
```

## 开发分工（docs/10 附录 C）

- 后端：张帆、刘小娴
- 前端：张美佳
- Prompt/测试/验收：彭雅奇
- 改动表或接口前，先改 `docs/04` / `docs/06` 再动手。
