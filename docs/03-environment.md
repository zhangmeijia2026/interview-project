# 03 · 运行环境与本机初始化

> **本文全部为 2026-09-02 在团队成员本机实测值**，写代码/跑命令以实测为准，不要凭记忆写版本。
> Windows 11 家庭中文版；shell 建议 **Git Bash**（本手册命令均兼容）。

## 1. 工具链实测清单

| 组件 | 实测版本 | 绝对路径 | 备注 |
|------|---------|----------|------|
| JDK | OpenJDK **17.0.20.1**（Temurin） | `D:\生产实习Claude\jdk17` | 无虚拟线程 → 见 `02` §7 |
| Maven | **3.9.16** | `D:\生产实习Claude\maven\apache-maven-3.9.16\bin\mvn.cmd` | 无 mvnw，用绝对路径或加 PATH |
| Node.js | **v22.20.0** | `D:\生产实习Claude\node\node.exe`（npm 同目录） | Vue3/Vite 需要 |
| Redis | **5.0.14.1**（Windows 移植版） | `D:\生产实习Claude\redis\redis-server.exe` | 无 RediSearch；默认端口 6379 |
| MySQL | **8.3**，服务名 **MySQL83**（运行中） | `C:\Program Files\MySQL\MySQL Server 8.3\bin\mysql.exe` | Windows 服务自动启动 |
| 微信开发者工具 | 2.02.x | `D:\生产实习Claude\微信web开发者工具` | 本期不用（无小程序需求），备查 |

> ⚠️ Maven 目录里其实还有一层：`maven\apache-maven-3.9.16\bin\mvn.cmd`。建议把
> `D:\生产实习Claude\jdk17\bin`、`...\apache-maven-3.9.16\bin`、`D:\生产实习Claude\node` 加入系统 PATH。

## 2. Windows 中文路径注意事项（重要，别踩坑）

父路径含中文与空格风险（`D:\生产实习Claude\...`、`C:\Program Files\...`）：

1. **Maven/Spring Boot 在本机开发通常可用**，但个别插件（资源拷贝/打包 tar）对非 ASCII 路径可能告警。若遇 `malformed input` / 打包失败，优先在**纯英文临时目录**做构建验证，或直接放弃打包部署包（答辩跑 `mvn spring-boot:run` 即可）。
2. **MySQL 3306 端口与 `Program Files` 空格**：连接串里的路径问题少，配置类走服务名 `MySQL83` 即可。
3. **Git Bash 访问中文路径**：用正斜杠与引号，如 `ls "/d/生产实习Claude/jdk17"`；避免在命令里手敲中文，可用 Tab 补全。
4. `.env`/密钥文件用环境变量注入，见 §5。

## 3. 初始化步骤（新机器照此做一遍）

```bash
# ① 验证工具
"/d/生产实习Claude/jdk17/bin/java.exe" -version
"/d/生产实习Claude/maven/apache-maven-3.9.16/bin/mvn.cmd" -v
"/d/生产实习Claude/node/node.exe" -v

# ② 确认 MySQL 服务在跑
sc query MySQL83        # STATE 应为 RUNNING
"/c/Program Files/MySQL/MySQL Server 8.3/bin/mysql.exe" -u root -p

# ③ 建库（utf8mb4）。库名约定 interview_system
CREATE DATABASE IF NOT EXISTS interview_system
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- 建表脚本见 docs/04 §7，本期手动执行 schema.sql（Phase 1 落地时放入 backend/db/）

# ④ 启动 Redis（演示前确保在跑）
"/d/生产实习Claude/redis/redis-server.exe"        # 前台；可用 --port 6379
# 检查: redis-cli ping → PONG
```

### 3.5 JD 图片 OCR：准备语言数据（Tess4J 自带引擎，无需系统安装）

> Tess4J 5.x jar 内置 `libtesseract553.dll`/`libleptonica`（win32-x86-64）与 `eng.traineddata`，
> **无需安装 Tesseract 系统程序**。只需在 `backend/ocr-tessdata/`（已 gitignore）备齐 `eng/chi_sim`：

```bash
cd backend
mkdir -p ocr-tessdata
# ① eng/osd 从本地 m2 里的 tess4j jar 抽取（第一次执行；版本号按 pom 实际改）
unzip -j -o "$HOME/.m2/repository/net/sourceforge/tess4j/tess4j/<VERSION>/tess4j-<VERSION>.jar" \
  'tessdata/eng.traineddata' 'tessdata/osd.traineddata' -d ocr-tessdata
# ② 中文 chi_sim（tessdata_fast，~2.4MB；jsdelivr 可达镜像，限 20MB，故用 fast 版）
curl -sL -o ocr-tessdata/chi_sim.traineddata \
  "https://cdn.jsdelivr.net/gh/tesseract-ocr/tessdata_fast@main/chi_sim.traineddata"
ls -la ocr-tessdata/     # 需同时出现 eng.traineddata 与 chi_sim.traineddata
```

应用配置默认 `app.ocr.datapath=./ocr-tessdata`（相对 backend 运行目录）。缺语言包/目录时，
JD 图片上传会 `status=failed` 并提示改走"粘贴文字"，不影响其他功能。

> 目录已支持**自动定位**（2026-09-05）：配置值不可用时按 cwd → `cwd/backend` → 逐级父目录(+backend)
> 找 `ocr-tessdata`，因此**从仓库根/IDE 启动后端也能识别图片 JD**，不必非在 backend/ 下运行。
> ⚠️ 实测教训：找到后代码会**转成相对 cwd 路径**再交给 Tess4J——本机父路径含中文（`D:\生产实习Claude\…`），
> 传**含中文的绝对路径**会让 tess4j 原生库报 `couldn't load any languages`（见 §2「中文路径注意事项」）；
> 相对路径不含中文前缀、原生层按 cwd 解析则正常。若设 `OCR_DATAPATH` 请尽量用不含中文的路径。

## 4. 后端工程创建参数（start.spring.io，生成后放 `backend/`）

| 参数 | 取值 |
|------|------|
| Group / Artifact | `com.group5` / `interview` |
| Language / Build | Java / Maven |
| Java | **17** |
| Boot | 当前 GA（如 3.5.x；本项目基线 Boot **3.x**，勿选 4 系除非全组同意升级） |
| Dependencies | Spring Web, Spring Data JPA, **MySQL Driver**, **Spring AI OpenAI**, Spring Data Redis, Validation, **springdoc-openapi**, Lombok, Spring Security(可选——本系统手写 JWT 过滤器即可，若引 Security 需配置 permitAll 清单) |

**版本策略（避免编造）**：Spring AI 与 Boot 的兼容版本号以 **start.spring.io 生成页面上当前可选 GA**为准；
生成后 `pom.xml` 里核对 `spring-ai` 的 BOM 版本，并把 `docs/05` 的示例属性名对照所选版本文档校准一遍。

## 5. 环境变量与本地配置模板

建议把密钥放**用户级环境变量**（`setx` 或系统设置），不写进仓库：

```bash
setx DEEPSEEK_API_KEY "sk-你的key"     # DeepSeek 开放平台 https://platform.deepseek.com
setx MYSQL_ROOT_PASSWORD "你的root密码"
```

后端本地配置：`backend/src/main/resources/application-local.yml`（**加入 .gitignore**）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/interview_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: ${MYSQL_ROOT_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate      # 表由 Phase1 脚本建，绝不用 update 生产
    open-in-view: false
  data:
    redis:
      host: localhost
      port: 6379

spring:
  ai:
    openai:                            # OpenAI 兼容协议指向 DeepSeek（docs/07 §10）
      api-key: ${DEEPSEEK_API_KEY}     # 密钥只放本 local 文件 / 环境变量

ai:
  mock-enabled: true                   # true=离线引擎（默认演示/CI）；false=真实 DeepSeek（需密钥+网络可达）
  cost:
    daily-limit-yuan: 2.0              # 每日模型预算，超限自动回落离线引擎

app:
  jwt:
    secret: ${JWT_SECRET:devOnlyChangeMeToA64CharRandomString}
    access-ttl-minutes: 120
    refresh-ttl-days: 7
  upload-dir: ./uploads
  verify-code: 123456                  # 开发期固定校验码（上线接 SMTP 见 01 §5）
  cors:
    allowed-origins: http://localhost:5173

logging:
  level:
    com.group5.interview: debug
server:
  port: 8080
```

## 6. 前端工程创建参数（Vue3）

```bash
# 在 frontend/ 下初始化
"/d/生产实习Claude/node/npm.cmd" create vite@latest . -- --template vue-ts
"/d/生产实习Claude/node/npm.cmd" install
"/d/生产实习Claude/node/npm.cmd" i element-plus pinia vue-router axios echarts
"/d/生产实习Claude/node/npm.cmd" run dev        # http://localhost:5173
```

> 前端细节见 `08`。`vite.config.ts` 需配代理 `/api → http://localhost:8080`（开发期免跨域）。

## 7. 提交前自检清单

- [ ] `mvn clean test` 通过（backend/）
- [ ] 前端 `npm run build` 无类型错误
- [ ] 无 `.env`/密钥/上传文件/target/node_modules 入库
- [ ] 涉及表结构改动 → 先改 `04`；涉及接口 → 先改 `06`
- [ ] MySQL 连接串含 `allowPublicKeyRetrieval=true`（caching_sha2_password）
