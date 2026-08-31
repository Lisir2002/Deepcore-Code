# Version.md — 版本与更新规范

> 本文件定义版本号规则、正式版交付标准、发版流程入口与变更记录规范。
> 操作细节（密钥、Secrets、CI）见 RELEASING.md；每个版本实际改了什么见 CHANGELOG.md。

---

## 一、版本号规则

采用**语义化版本** `MAJOR.MINOR.PATCH`，处于 `0.x` 阶段（正式 API 未稳定）：

| 段 | 递增时机 | 示例 |
| --- | --- | --- |
| MAJOR | 架构不兼容重构、存储格式破坏性变更 | `0.x` → `1.0.0`（首个稳定版） |
| MINOR | 新功能（新里程碑交付，如 M1 真实模型） | `0.1.x` → `0.2.0` |
| PATCH | 缺陷修复、配置修正、文档/构建优化 | `0.1.2` → `0.1.3` |

配套约束：

- `versionCode`（`app/build.gradle.kts`）**严格单调递增，只增不减**，与 `versionName`
  在**同一次 commit** 里一起改，tag 名必须与 `versionName` 完全一致（`v` + 值）。
- `-rc` / `-beta` / `-alpha` 后缀自动标记为 pre-release。
- 版本演进决策记入 `CHANGELOG.md` 对应版本的 `### 决策` 小节。

## 二、正式版交付标准（硬性）

一个 `vX.Y.Z` tag 打出去之前，产物必须满足：

1. **签名方案 v1 + v2 + v3 全开**（`app/build.gradle.kts` 显式 `enableV1/V2/V3Signing = true`，
   不依赖 AGP 默认值——AGP 8.7.3 + minSdk 26 的默认**不含 v3**，见 CHANGELOG 0.1.2）。
2. **证书指纹一致**：签名证书 SHA-256 与 `SignatureGuard.OFFICIAL_SIGNATURE_SHA256`
   及 `scripts/check_apk_signing.py` 的 `OFFICIAL_FP` 三处一致。
3. **加固到位**：R8 + 资源收缩开启且 mapping 非空；禁备份/禁明文流量/Release 剥离日志。
4. **验证通过**：
   ```bash
   python3 scripts/check_apk_signing.py app-release.apk
   # 输出 v1=True v2=True v3=True + 指纹 ✅ 退出码 0，才算可分发
   ```
5. **CI 全绿**：`ci.yml` 四 job + `release.yml` release-build。
6. **CHANGELOG 已记**：新版本条目先于或随 tag 同步写入。

> 历史教训：v0.1.1 曾被验证脚本字节序笔误误判为"缺 v2"而改配置，导致 v0.1.2 真正
> 丢失 v3。**验证结论必须来自仓库内标准化脚本，不允许临时手写字节搜索。**

## 三、发版流程（速览）

```bash
# 1. 确认 versionCode/versionName 已对齐，本地 ./scripts/ci-local.sh 全绿
# 2. 打 tag 并推送
git tag -a v0.1.3 -m "版本说明"
git push origin v0.1.3
# 3. release.yml 自动：测试 → 构建 Release APK → 创建 GitHub Release
# 4. 下载产物跑 scripts/check_apk_signing.py 验证三绿
# 5. 更新 CHANGELOG.md / PLAN.md（若里程碑状态变化）
```

完整检查清单见 RELEASING.md《发版前检查清单》。

## 四、变更记录规范（CHANGELOG.md）

- 格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，倒序排列。
- 分组用：`Added / Changed / Fixed / Security / 决策`（决策小节记录"为什么"）。
- 每个版本条目必须包含：版本号、日期、对应 commit/tag、`versionCode`。
- 未发布内容放在 `Unreleased` 区，发版时改名并补日期。

## 五、历史版本决策摘要

| 版本 | 决策 | 原因 |
| --- | --- | --- |
| v0.1.0 | 首个 tag，debug 签名回退策略 | 先立架构，签名体系随后落地 |
| v0.1.1 | 正式签名 + 加固全量落地 | 正式发行版安全基线 |
| v0.1.2 | 误改签名配置（仅显式 v1） | 验证脚本笔误导致误判"缺 v2"，教训见上 |
| v0.1.3 | 恢复 v1+v2+v3 显式三开 | 字节级解析证实 v3 缺失，AGP 默认不可依赖 |
