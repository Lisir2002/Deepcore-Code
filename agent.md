# agent.md — AI 协同开发规范

> 本文件是**人类与 AI 协作者共同遵守的工作契约**。任何接入本仓库的 AI 编程助手
> （Claude Code、WorkBuddy、Cursor 等）在动手写代码/改文档之前，必须先读完本文件。
> 与 ARCHITECTURE.md 冲突时以架构文档为准；与安全红线冲突时以本文为准。

---

## 0. 沟通与语言

- 所有交流、commit message、文档、代码注释使用**简体中文**（代码标识符除外）。
- 回复保证**准确性与上下文统一性**：不确定的事情明说"未验证"，禁止编造执行结果。
- 每次执行任务**实时更新项目任务状态**；每次实现之后**同步更新对应文档**。

## 1. 架构铁律（违反 = 重做）

1. **事件是唯一契约**：Agent 与 UI 之间只通过 `AgentEvent` 通信。事件是"已发生的事实"，
   不允许携带颜色、文案模板、排版提示等 UI 指令。
2. **纯 Kotlin 分界线不可下移**：`:core:model/agent/data/uistate` 保持零 Android 依赖。
   主循环、归约器等易错逻辑必须能在 JVM 上直接单测（不开模拟器）。
3. **designsystem 是唯一 UI 出口**：`:feature:*` 与 `:app` 禁止直接 import
   `androidx.compose.material3.*`、禁止自建 Scaffold/Button/TopAppBar、禁止硬编码
   `16.dp`/`14.sp` 设计令牌。`:lint` 模块会在**编译期**拦截。
4. **能力即接口**：模型、工具、沙箱、工作区、权限门全部 SPI 化。新能力 = 新实现类 +
   `:app` 里一行绑定，禁止在业务代码里 `when (provider.type)` 之类的前提判断。
5. **渲染按产物类型分发**：`ToolOutput` 是结构化类型（Text/Diff/FileList/SearchHits/KeyValues），
   禁止按工具名 `when (tool.name)` 分发渲染。

## 2. 修改代码前

- 先读 `ARCHITECTURE.md` 的模块划分与扩展点表，确认改动落在正确的模块。
- **改哪层测哪层**：动 `:core:agent` / `:core:uistate` 必须补/跑对应 JVM 单测；
  动 UI 必须过 `:lint`；动构建脚本必须本地跑 `./scripts/ci-local.sh`。
- 不确定行为时先看现有用例怎么写，遵循既有风格（注释讲"为什么"而不是"是什么"）。

## 3. 构建与发布红线

- **密钥永不入库**：`deepcode-release.jks`、`keystore.properties`（真实值）、口令、
  GitHub 令牌一律不进 git。仓库内只允许 `keystore.properties.example` 模板。
- **CI 上缺签名必须失败**：任何"先跳过签名打出包再说"的改动一律拒绝。
  判断逻辑见 `app/build.gradle.kts` buildTypes.release（`onCi && buildingRelease`）。
- **签名方案 v1+v2+v3 显式三开**，不依赖 AGP 默认值（教训见 CHANGELOG 0.1.2 条目）。
- 发版流程与检查清单见 `RELEASING.md` 与 `Version.md`；产物验证：
  `python3 scripts/check_apk_signing.py <apk>`，非三绿不得分发。
- **proguard-rules.pro 与 SignatureGuard 指纹不是随手可改的配置**：
  换密钥必须同步 `SignatureGuard.OFFICIAL_SIGNATURE_SHA256`；删 keep 规则前先读注释。

## 4. Git 与 CI 约定

- commit message 用约定式前缀 + 中文摘要：
  `feat: / fix: / refactor: / chore: / build(release): / docs:`。
- `main` 分支受 CI 保护：push 触发 `ci.yml`（core-test / android-build / design-guard）；
  push `v*` tag 触发 `release.yml`（release-build + GitHub Release）。
- CI 全绿才算完成。红了先看是不是自己改出来的，再判断是否级联误红（0.1.2 期间
  出现过"core-test 因 app 构建脚本属性名错误而级联变红"的案例，别被表象骗了）。
- 版本演进：`versionCode` 严格递增、`versionName` 与 tag 对齐，同一次变更里改。

## 5. 文档同步义务（每次实现后）

| 你动了什么 | 必须同步什么 |
| --- | --- |
| 新增/修改模块、架构决策 | `ARCHITECTURE.md`（+ `README.md` 模块表） |
| 版本号、签名、加固、发版流程 | `Version.md`、`RELEASING.md`、`CHANGELOG.md` |
| 里程碑进度 | `PLAN.md` |
| 构建脚本行为 | 脚本内注释 + `RELEASING.md` 对应小节 |
| 新增脚本/工具 | `scripts/` 内文件头注释 + 本文件或 README 索引 |

## 6. 安全红线

- 对话中出现的令牌/密钥属于**已泄露**，用完立即提醒作废轮换，绝不写入任何文件。
- 上传/分发动作（GitHub Release、外部渠道）只能使用仓库 Secrets 注入的凭据。
- 沙箱网络受限时优先排查通道方案（见 `docs/github-sandbox-tunnel.md`），不要私自
  引入第三方镜像源。

## 7. 已知踩坑速查（别再踩一遍）

| 坑 | 正解 |
| --- | --- |
| AGP 8.7 中 `v1SigningEnabled`/`isV1SigningEnabled` 不存在 | 用 `enableV1/V2/V3Signing`（`Boolean?`，null 走默认） |
| 只凭 `CI=true` 强制签名导致 core-test 配置阶段抛错 | GitHub 给每个 job 都注入 `CI=true`，须叠加 `taskNames.contains("Release")` |
| AGP 8.7.3 + minSdk 26 默认**不含 v3** | 显式 `enableV3Signing = true` |
| PKCS12 口令 store/key 不一致报 `not a private key` | 两个口令必须相同 |
| 验证 APK 签名时 v2 方案 ID 字节序写错导致误判"缺 V2" | `0x7109871a` 小端是 `1a 87 09 71`；用 `scripts/check_apk_signing.py`，别手写字节搜索 |
