# 05 · 后端设计（Spring Boot 3 + Spring AI + DeepSeek）

> 面向实现者的工程规范：骨架、分层规约、关键难点（JWT / AIService / SSE / 异步 / 线程）落地方案。
> 配套：`02`(架构)、`04`(表)、`06`(接口契约)、`07`(LLM)。最后更新：2026-09-02。

## 1. 工程骨架（start.spring.io → `backend/`）

参数见 `03` §4。生成后调整：

```
backend/
├─ pom.xml                  # 核对 spring-ai BOM 版本；加 lombok/springdoc；mysql-connector-j 由 Boot 管理
├─ db/schema.sql            # 来自 docs/04 §4
├─ src/main/resources/
│  ├─ application.yml       # 公共配置（profile 激活 local）
│  ├─ application-local.yml # 本机密钥，gitignored（模板见 docs/03 §5）
│  └─ prompts/*.md          # LLM 系统提示模板（文本文件，避免 Java 字符串转义地狱）
└─ src/main/java/com/group5/interview/...   # 包结构见 docs/02 §2
```

- **Lombok** 用 @Slf4j/@Data；**别用 Lombok 的 `@Data` 在 JPA 实体上叠循环引用**，实体用 @Getter/@Setter 即可。
- 全局配置文件激活：`spring.profiles.active=local`。
- 三方版本锁定：`mysql-connector-j`、`spring-ai`、`springdoc-openapi-starter-webmvc-ui`、`tika-parsers-standard-package` 的坐标全部放进 `<dependencyManagement>` 或由 Boot BOM 管理，**版本以 Maven Central 当前稳定为准**，README 记录所用版本，便于复盘。

## 2. 分层规约（禁止越层）

```
Controller → ApplicationService(事务边界) → 领域/基础能力
     ↓ 唯一AI出口：ai/AIService
Repository ← Entity(贫血即可, 毕业设计不追求 DDD)
```
- Controller 只做参数校验与 `ApiResponse` 包装；不写业务。
- 业务编排放 ApplicationService；跨模块（如上传简历后要更新 interview 状态）也在此。
- **所有 LLM/DeepSeek 调用必须经过 `ai/AIService`**；不允许在 module 包内 new ChatClient（便于统一换模型、记日志、控成本）。
- 实体/DTO 分离：HTTP 出入参用 `dto/*`，不给前端暴露 JPA 实体。

## 3. 通用返回 / 错误码 / 异常

```java
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data){ return new ApiResponse<>(0,"ok",data); }
    public static <T> ApiResponse<T> error(int code,String msg){ return new ApiResponse<>(code,msg,null); }
}
// code 语义: 0=成功; 1xxx=参数/校验; 2xxx=认证; 3xxx=业务; 4xxx=LLM/外部依赖; 5xxx=系统
```

- 错误码集中 `common/ErrorCode` 枚举（含 HTTP 与业务码映射），`GlobalExceptionHandler` 统一转 `ApiResponse`：`BusinessException`(3xxx)、`MethodArgumentNotValidException`(1xxx)、JWT/权限(2xxx)、`AiCallException`(4xxx，带可重试标记)、兜底 5xxx。
- 日志：`@Slf4j`；入口 Filter 生成 `requestId` 写进 MDC，日志格式 `[requestId][userId] msg`；LLM 调用单独 `ai/` 内 logger，记录 模型/耗时/输入token/输出token/是否截断（**内容脱敏**，不含完整简历）。

## 4. 认证：JWT（无 Spring Security 也够，或引 Security 仅配过滤链）

推荐**不引入 Spring Security 全家桶**，手写组件更可控（毕业设计足够且少踩坑）：

| 组件 | 职责 |
|------|------|
| `JwtUtil` | HS256，`secret`≥64 随机字符（从 `app.jwt.secret` 注入），生成/校验 access(2h)、refresh(7d)；claim 含 userId |
| `JwtAuthFilter`(OncePerRequestFilter) | 白名单路径放行(`/api/auth/login`,`/api/auth/register`,`/swagger-ui/**`,`/v3/api-docs/**`)；其余解析 Bearer → 校验 → **按 userId 读库校验 `is_active`（R11：停用/拉黑即时生效，非启用直接 401「账号已被停用」）** → `UserContext.set(userId)`；失败 401 |
| `UserContext` | ThreadLocal<Long>；`finally` 清空（防线程池复用串号） |
| 登录锁定 | 失败计数 Redis `login:fail:{email}` INCR+EXPIRE 900s，≥5 锁定 15 分钟 |
| 刷新令牌 | refresh 成功换新 access；refresh 哈希存 Redis `refresh:{userId}` TTL 7d；登出删该 key，并把 access 放入黑名单 `black:{jti}` 至其过期 |
| `RateLimitInterceptor` | 面试相关接口每用户 10/min：Redis INCR+EXPIRE 60s，超限 429 |

> 越权防护：所有 `Service` 读取资源前用 `UserContext.userId()` 校验 `resource.userId`（写一个 `OwnershipGuard.check(userId, resourceUserId)`）。

> R11 管理端补充：`AdminOnlyInterceptor` 在读库判 `role=admin` 之前先判 `is_active`（停用的管理员同样 401）；`module/admin` 新增 `AdminAccountController/Service`（`PUT /api/admin/accounts/{id}/active`、`POST /api/admin/accounts/admins`）与 `AdminOverviewController/Service`（`GET /api/admin/stats/overview`）；`AdminUserService` 检索支持 role/active 过滤并提供"查看某用户面试及只读报告"。

## 5. AIService —— 唯一 AI 出口（设计）

目标：业务层只描述"要什么结构化结果"，AI 层管 Prompt、JSON、重试、成本、兜底。

**落地（2026-09-05）接口收敛为单方法**（流式/打字机由面试引擎自行走 SSE 通道，不经 AIService）：

```java
public interface AIService {
    /** 结构化 JSON：给定 promptKey + 上下文对象，返回解析后的目标类型（含修复重试与离线兜底） */
    <T> T structured(String promptKey, Object context, Class<T> clazz);
}
```

要点：
1. **Prompt 模板放 `resources/prompts/*.md`**（命名：`resume-parse/jd-parse/match/focus/question/grade/follow-up/report-analysis`；`resume-parse`/`jd-parse` 已改为本地算法、真实模式不再读模板，见 §6 与 `07` §10），内容见 `07` §4。
2. **双模式实现（二选一装配）**：`ai.mock-enabled=true`（默认）→ `LocalMockAIService` 离线确定性引擎（无 key 也可演示/CI，含 `report-analysis` 同构兜底）；`false` → `DeepSeekAIService` 走真实 `deepseek-chat`：读 `prompts/{key}.md` → 以上下文对象顶层字段替换 `{field}` 占位 → `ChatClient.prompt().system(JSON约束).user(prompt)`。`OFFLINE_KEYS=[resume-parse,jd-parse]` 硬路由离线，不烧额度。任一步失败（未配 key/超预算/网络/解析失败且重试一次仍失败）→ 记日志后回退离线引擎同构结果，界面不崩。
3. **`JsonSupport`**：剥 Markdown ```json 围栏 → 取首个 `{` 到末个 `}` → `ObjectMapper.readValue`；失败做尾逗号修复；仍失败抛 `ErrorCode.AI_FAILED`（由 DeepSeekAIService 重试一次）。
4. **`CostGuard`**：按字符数近似 token、按 `ai.cost.input/output-price-per-million` 记当日花费；`overBudget()` 后新请求不再走模型直接回退离线，防答辩现场失控。
5. **可换模型**：`spring.ai.openai.chat.options.model`（默认 `deepseek-chat`）；密钥仅 `DEEPSEEK_API_KEY`。

> ⚠️ Spring AI 的 **starter/坐标与属性名随版本变化较大**。本项目落地为 Spring AI **1.1.8** BOM + `org.springframework.ai:spring-ai-starter-model-openai`，属性走 `spring.ai.openai.*`；类初始化统一 `ChatClient.builder(ChatModel).build()`，避免依赖隐式属性绑定的歧义（核对方式见 `07` §10）。

## 6. 上传、Apache Tika 与图片 OCR

- `UploadConfig`：目录 `./uploads/{userId}/{yyyymm}/`；单文件 ≤10MB（`spring.servlet.multipart.max-file-size=10MB`）。
  - 简历：白名单 `.pdf/.docx`。
  - JD：白名单 `.pdf/.docx` + 图片 `.jpg/.jpeg/.png/.bmp/.webp`（图片进 OCR）。
- 简历幂等：`Md5Util` 算 `file_md5`，`uk_resume_md5_user` 冲突即直接复用旧解析（天然去重 + 简历库复用基础，见 §8"简历保留库"与 `06` F03）。
- **R1/R2 本地算法（不调模型，2026-09-05）**：`@Async("taskExecutor")` 处理器得到原文后走 `AIService.structured("resume-parse"/"jd-parse", …)`——该两 key 在双模式下都由 `LocalMockAIService` 的本地抽取实现产出 `parsed_data`，全程**不调 DeepSeek**（省额度、可测）。
- 文字抽取：`TikaTextExtractor`（PDF/DOCX）。图片：`module/jd/JdOcrService`（Tess4J `chi_sim+eng`；**tess4j jar 自带 Windows 原生库，无需系统安装**，仅需 `backend/ocr-tessdata/` 备好 `eng/chi_sim`，见 `03` §3.5 与 `app.ocr.*`）。`app.ocr.datapath`（默认 `./ocr-tessdata`）支持**自动定位**：配置值不可用则按 cwd → cwd/backend → 逐级父目录(+backend) 找 tessdata，找到后**转成相对 cwd 路径再交给 tess4j**——本机仓库父路径含中文（`D:\生产实习Claude\…`），传含中文的绝对路径会让原生库报 `couldn't load any languages`（2026-09-05 实测修复，见 `03` §2），相对路径由原生层按 cwd 解析则正常。OCR 缺语言包/失败 → JD 行 `status=failed` 并提示改"粘贴文字"，不影响其他功能。
- 解析进度：行 `status`（processing→ready|failed）+ 时间戳即"进度三态"；前端轮询 `GET` 该行（F03/F04）或走面试级 SSE（`06` §SSE）。

## 7. 异步 / SSE / 线程（JDK17，无虚拟线程）

```java
@Configuration
public class AsyncConfig {
  @Bean("taskExecutor")
  public Executor taskExecutor(){
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(8); e.setMaxPoolSize(16); e.setQueueCapacity(200);
    e.setThreadNamePrefix("ai-task-"); e.setWaitForTasksToCompleteOnShutdown(true);
    return e;
  }
  @Bean("ssePusher")
  public Executor ssePusher(){
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(2); e.setMaxPoolSize(4); e.setQueueCapacity(50);
    e.setThreadNamePrefix("sse-"); return e;
  }
}
```

**SseEmitterRegistry（会话级通道）**

```java
@Component
public class SseEmitterRegistry {
  private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
  public SseEmitter register(Long interviewId){ ... new SseEmitter(0L); 15s心跳; timeout=0; 完成/异常时 remove(interviewId, em) ... }
  public void push(Long interviewId, SseEvent ev){ SseEmitter em=emitters.get(interviewId);
    if(em!=null){ em.send(SseEmitter.event().name(ev.type()).data(ev.data())); } }
  public void remove(Long interviewId){ ... }
}
```

**流式问答串起来（伪码，关键在"别占请求线程"）**

```java
// POST answer 业务入口
public void answer(interviewId, userId, answer){
   // 1 同步写 user_answer + 进度（短事务）
   questionRepo.save(...);
   // 2 触发评分，经 ssePusher 推送
   ssePusher.execute(() ->
      aiService.stream("grade", ctx, delta -> registry.push(id, SseEvent.delta(delta)))
        .thenRun(() -> registry.push(id, SseEvent.done("grade"))));
   return; // 请求立即返回 202? 或走 SSE 订阅端
}
```
两种前端接入（`06`/`08` 二选一，推荐 B）：
- **A 同步**：POST 阻塞到点评完（≤4s），一次性 JSON 返回；打字机效果弱。
- **B 事件驱动（推荐）**：POST answer 落库即返 200；服务端把 `question_delta/grade/followup` 推到面试级 SSE；前端订阅该 channel 渲染。

> 事务注意：SSE 推送与 @Async 里**不要开大事务**；写库短事务放最内层。并发场景（连点提交）用 Redis 锁或"当前进行题目序号"乐观校验防重复评分。

## 8. 面试引擎（F05）状态推进

- `InterviewEngine.begin`：校验 status ∈ {matched, ready} 且 `completed_question_count < question_count` → 置 `in_progress`，出第 `completed+1` 题。
- 每题答完落库后 `completed_question_count+1`；达 N → `completed` → `ReportService.generate`（异步）→ SSE `report_done`。
- 追问：每题 0–1 次（SRS P1）；判据来自 grade 输出里的 `suggest_followup`。追问**不计入** completed 计数。

## 9. 配套清单（供 Phase 编码核对）

- OpenAPI：`springdoc` → `http://localhost:8080/swagger-ui.html`；作为交付物之一。
- CORS：`app.cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173`（`WebMvcConfigurer`）。前端 dev server host=127.0.0.1，两种 URL 都放行，避免 Origin 不匹配导致的 403 Invalid CORS request。
- 启动探测：应用启动后打 `/api/ping` 返回 ok（健康检查/答辩暖场用）。
- 依赖了 `spring-boot-starter-validation`；Bean Validation 注解放 DTO。
- 时区：`spring.jackson.time-zone: Asia/Shanghai`，序列化 LocalDateTime 用 ISO。
- **RAG 检索（R10）**：包 `ai/rag/`——`RagProperties`(app.rag.*) / `EmbeddingService` / `HashEmbeddingService`(512 维) / `TextChunkService`(分块+MySQL 唯一源) / `RagInjector`(Top-k 注入 `{knowledge}`) / `RagStartupIndexer`(启动重建) / `QdrantClient`(真向量库镜像适配，provider=qdrant，2026-09-07 落地)。运维接口 `AdminRagController`：`GET /api/admin/rag/stats`、`POST /api/admin/rag/rebuild`、`GET /api/admin/rag/search`、`GET /api/admin/rag/qdrant/health`（契约见 `06`，设计/切启见 `07` §8/§11）。
