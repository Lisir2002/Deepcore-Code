# DeepCore-Code

**跑在 Android 手机上的 AI Agent IDE** —— 让你在手机上拥有一个能读写文件、执行命令、流式输出的编程智能体。

> Kotlin 2.0 · Jetpack Compose · 事件驱动架构 · 纯 Kotlin 可测核心

---

## 这是什么

DeepCore-Code 是一个 AI 编程智能体的移动端宿主（Agent IDE for Android）。它不是聊天机器人套壳，而是围绕一套**事件驱动的 Agent 运行时**构建：

- **主循环**：思考 → 调用工具 → 消化结果 → 继续思考，全流程事件化
- **工具系统**：能力即接口（`Tool` / `Sandbox` / `Workspace` / `ModelProvider` 全部 SPI 化）
- **流式 UI**：事件 → 渲染块 → 界面的唯一映射链，工具输出按产物类型（文本/Diff/文件列表/搜索命中）分发渲染
- **会话可恢复**：存 append-only 事件日志而非消息快照，重放即恢复

架构设计与扩展点清单见 **[ARCHITECTURE.md](ARCHITECTURE.md)**。

## 模块结构

```
:app                 装配层：DI 绑定、MainActivity
:feature:chat        会话页（只准用 designsystem 组件）
:designsystem        UI 统一层：唯一可用 Material3 的模块
:core:platform       Android 能力实现（工作区、白名单沙箱、内置工具）
──────────── 纯 Kotlin 分界线（以下模块零 Android 依赖，JVM 直接单测）────────────
:core:uistate        事件 → 渲染块归约器
:core:agent          主循环 + 全部 SPI + 权限门 + 上下文策略
:core:data           append-only 事件日志存储
:core:model          领域模型 + 事件定义
:lint                设计系统守卫（绕过 designsystem 直接编译失败）
```

**加功能 = 加实现，不是改地基。**

## 技术栈

| 项 | 版本 |
| --- | --- |
| Kotlin / Compose | 2.0.21 / BOM (Material3) |
| AGP / Gradle | 8.7.3 / 8.9 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| DI / 序列化 | Koin / kotlinx.serialization |

## 快速开始

```bash
git clone https://github.com/Lisir2002/Deepcore-Code.git
cd Deepcore-Code

# 纯 Kotlin 部分（无需 Android SDK，秒级反馈）
./gradlew :core:agent:test :core:uistate:test

# 完整构建（需 JDK 17+ 与 Android SDK 35）
./gradlew :app:assembleDebug

# 本地全量检查（等价 CI）
./scripts/ci-local.sh
```

## 下载正式版

前往 [GitHub Releases](https://github.com/Lisir2002/Deepcore-Code/releases) 下载 `app-release.apk`。

正式版交付标准（详情见 [Version.md](Version.md) 与 [RELEASING.md](RELEASING.md)）：

- **签名**：v1 + v2 + v3 三方案全开，官方证书指纹
  `06:2E:80:3E:…:C6:B4`，启动时运行时校验（`SignatureGuard`）
- **加固**：R8 混淆 + 资源收缩 + 清单加固（禁备份/禁明文流量/Release 剥离日志）
- **验证**：`python3 scripts/check_apk_signing.py app-release.apk`，三方案 + 指纹全过才可分发

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 架构设计、三大地基决策、扩展点清单 |
| [PLAN.md](PLAN.md) | 里程碑路线图与当前进展 |
| [agent.md](agent.md) | AI 协同开发规范（人与 AI 协作者共同遵守） |
| [Version.md](Version.md) | 版本号规范、签名方案要求、发版检查清单 |
| [CHANGELOG.md](CHANGELOG.md) | 每个版本的实际变更记录 |
| [RELEASING.md](RELEASING.md) | 发布操作手册（密钥、Secrets、CI 流程） |
| [docs/github-sandbox-tunnel.md](docs/github-sandbox-tunnel.md) | 沙箱网络通道方案 |

## 许可

暂未定License，保留所有权利。
