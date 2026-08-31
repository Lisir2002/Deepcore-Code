# PLAN.md — 项目开发设计计划

> 里程碑路线与当前进展。本文回答"现在在哪、下一步去哪"；
> 架构怎么落地见 ARCHITECTURE.md，版本节奏见 Version.md。

---

## 里程碑总览

| 里程碑 | 目标 | 状态 |
| --- | --- | --- |
| **M0 架构地基** | 骨架 + 主循环 + UI 统一机制跑通完整链路 | ✅ 完成（v0.1.0） |
| **M0.5 发布基线** | 正式签名 + 加固 + CI 发版流水线 | ✅ 完成（v0.1.3） |
| **M0.6 数据层 SQLite 化** | SQLDelight 接管持久化（events/sessions 表 + TableModule 扩展协议） | 🔄 设计定稿（见 DATA_LAYER.md），待实现 |
| **M1 能真跑** | 接真实模型（OkHttp + SSE 的 `ModelProvider`），真实对话 | 🔜 下一步 |
| **M2 能用** | 工作区文件树、Diff 审阅、git 工具、会话列表页 | ⏳ 规划中 |
| **M3 能扛** | 上下文压缩实战化、前台服务保活、数据层深化 | ⏳ 规划中 |
| **M4 扩展** | MCP 客户端、子 Agent 并行、Plan Mode、远端沙箱 | ⏳ 规划中 |

---

## 已交付能力清单

### M0 架构地基（v0.1.0）

- [x] Gradle 多模块工程（L0/L1 纯 Kotlin 底座 + L2 能力层 + UI 统一层 + Lint 守卫）
- [x] 事件驱动主循环：`AgentEvent` 契约 + 流式工具输出
- [x] 会话存 append-only 事件日志，重放即恢复
- [x] 事件 → 渲染块归约器（纯 Kotlin，6 个用例）
- [x] Lint 守卫：绕过 designsystem 直接编译失败
- [x] 单测抓出真实 bug（工具执行过快时流式输出被吞）

### M0.5 发布基线（v0.1.1 → v0.1.3）

- [x] 正式签名体系：PKCS12 密钥（仓库外隔离）+ CI Secrets 注入 + v1+v2+v3 三方案
- [x] R8 代码混淆 + 资源收缩（mapping 断言：< 5 条混淆直接失败）
- [x] 清单加固：禁备份、禁明文流量、Release 剥离日志
- [x] 运行时防篡改：`SignatureGuard` 启动比对证书指纹
- [x] CI 流水线：`ci.yml`（core-test / android-build / design-guard）+ `release.yml`（tag 触发）
- [x] 产物验证脚本 `scripts/check_apk_signing.py`（字节级解析 Signing Block）
- [x] 版本验证复盘：定位 v0.1.2 缺 v3 根因（AGP 默认不含 v3），v0.1.3 修复

### 文档体系（随 v0.1.3）

- [x] README / agent.md / PLAN.md / Version.md / CHANGELOG.md / RELEASING.md 全套

---

## 当前焦点

**M0.6 数据层 SQLite 化**：方案已定稿（SQLDelight 2.x + payload JSON + sessions 表 +
TableModule 注册制扩展协议 + 统一事务边界），四项决策 2026-08-31 经评审确认，
见 `DATA_LAYER.md`。下一步按其《实施清单》落地代码，随后进入 M1。

## M1 设计要点（数据层之后）

1. **`OkHttpProvider : ModelProvider`**：SSE 流式解析，映射到 `ThinkingDelta` /
   `MessageDelta` 事件；超时/重试策略进 `ContextPolicy` 之外的独立配置。
2. **模型配置面**：API Key 存 `EncryptedSharedPreferences`；`PermissionGate`
   增加网络访问确认（复用现有风险等级模型）。
3. **验收标准**：真实模型流式对话跑通一轮完整工具调用；`core:agent` 全部用例保持绿；
   事件序列可重放恢复。
4. **发布口径**：M1 功能进 `0.2.0`（次版本号，符合 Version.md 语义）。

## 风险与依赖

| 风险 | 缓解 |
| --- | --- |
| Android SDK 依赖网络下载（沙箱不稳定） | CI 已跑通全量构建，本地通道见 docs/github-sandbox-tunnel.md |
| 密钥单点故障 | `/root/deepcode-signing/` 离线备份；丢失只能换密钥 + 全量重装（已在 RELEASING.md 声明） |
| proguard 规则回归 | mapping 断言 + Release 手测清单 |
| AI 协作者误改已调好的配置 | agent.md 铁律 + 构建脚本内注释 + 本计划约束 |
