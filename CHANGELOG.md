# Changelog

本项目所有显著变更记录于此。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

### Added

- 文档体系：`README.md`（项目说明）、`agent.md`（AI 协同开发规范）、`PLAN.md`（开发设计计划）、
  `Version.md`（版本与更新规范）、本文件（变更日志）。
- `scripts/check_apk_signing.py`：零依赖 APK 签名验证脚本（解析 Signing Block，
  判定 v1/v2/v3 并比对官方证书指纹），替代临时手写字节搜索，杜绝 0.1.2 式验证笔误。

## [0.1.3] — 2026-08-31 · tag [v0.1.3](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.3) · versionCode 4

### Fixed

- 恢复正式签名方案 **v1+v2+v3 显式三开**（`app/build.gradle.kts`），
  修复 v0.1.2 起签名块中 **v3 缺失**的回归。

### 决策

- 经 APK Signing Block 字节级解析证实：v0.1.1（三显式开启）v1/v2/v3 全部命中，
  此前"V2 缺失"是验证脚本把 v2 方案 ID `0x7109871a` 的小端字节序写错
  （`1a 79 08 71` ≠ 正确的 `1a 87 09 71`）造成的误判；
  而 v0.1.2（仅显式 v1）才真正丢失 v3 —— **AGP 8.7.3 + minSdk 26 的默认签名方案不含 v3**。
  结论：三个方案一律显式开启，验证一律走 `scripts/check_apk_signing.py`。

## [0.1.2] — 2026-08-31 · tag [v0.1.2](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.2) · versionCode 3

### Changed

- 版本对齐：`versionName` 0.1.1 → 0.1.2，`versionCode` 2 → 3。

### Security（回归，已于 0.1.3 修复）

- ⚠️ 签名配置被改为仅显式 `enableV1Signing = true`（基于错误验证结论），
  产物实际为 v1+v2，**v3 缺失**。v1 证书指纹与 `SignatureGuard` 校验一致。
- 期间确认 AGP 8.7.3 正确属性名为 `enableV1/V2/V3Signing`；
  `v1SigningEnabled`/`isV1SigningEnabled` 等写法编译报 Unresolved reference。

## [0.1.1] — 2026-08-31 · tag [v0.1.1](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.1) · versionCode 2

### Added

- **正式签名体系**：PKCS12 密钥（仓库外物理隔离）+ GitHub Secrets 注入 + v1+v2+v3 三方案开启
  （字节级复验确认 0.1.1 产物三方案齐备、证书指纹与 SignatureGuard 逐位一致）。
- **代码加固**：R8 混淆 + 资源收缩；CI 断言 mapping 非空（< 5 条直接失败）。
- **清单加固**：`allowBackup=false`、`usesCleartextTraffic=false` + networkSecurityConfig、
  Release 剥离 `android.util.Log`。
- **运行时防篡改**：`SignatureGuard` 启动比对 APK 证书 SHA-256 与官方指纹。

### Fixed

- CI 强制签名判断从 `CI=true` 改为 `CI=true && taskNames 含 Release`——
  GitHub 给每个 job 注入 `CI=true`，旧逻辑导致 core-test 配置阶段抛 `GradleException`。

### Changed

- 版本对齐：`versionCode` 1 → 2。

## [0.1.0] — 2026-08-31 · tag [v0.1.0](https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.0) · versionCode 1

### Added

- M0 架构地基（详见 [ARCHITECTURE.md](ARCHITECTURE.md)）：
  - 多模块工程：`core:{model,agent,data,uistate}`（纯 Kotlin 可测底座）、
    `core:platform`、`designsystem`（唯一 Material3 出口）、`lint`（设计守卫）、`feature:chat`、`app`。
  - 事件驱动主循环 + 全部能力 SPI（`ModelProvider`/`Tool`/`Sandbox`/`Workspace`/`PermissionGate`…）。
  - append-only 事件日志存储，会话重放即恢复。
  - 事件 → 渲染块归约器（含 6 个 JVM 单测）与 Lint 编译期守卫。
- CI 流水线：`ci.yml`（core-test / android-build / design-guard）、`release.yml`（tag 触发发版）。
- 全量重命名至 `com.deepcode.agent`，应用名 DeepCore-Code，自适应图标。

### Fixed

- Android 编译两处根因（包名对齐、lambda `it` 遮蔽）后首个可编译 tag。

[Unreleased]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.1.3...HEAD
[0.1.3]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Lisir2002/Deepcore-Code/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.0
