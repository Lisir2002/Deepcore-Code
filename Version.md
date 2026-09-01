# Version.md — 版本与更新规范

> 本文件定义版本号规则、正式版交付标准、发版流程入口与变更记录规范。
> 操作细节（密钥、Secrets、CI）见 RELEASING.md；每个版本实际改了什么见 CHANGELOG.md。

---

## 一、版本号规则

采用**四段版本号** `MAJOR.MINOR.PATCH.BUILD`（记作 `X.Y.Z.W`），处于 `0.x` 阶段（正式 API 未稳定）：

| 段 | 含义 | 递增时机 | 示例 |
| --- | --- | --- | --- |
| MAJOR | 主版本 | 架构不兼容重构、存储格式破坏性变更 | `0.x` → `1.0.0.0`（首个稳定版） |
| MINOR | 次版本 | 新功能（新里程碑，如 M1 真实模型） | `0.1.x` → `0.2.0.x` |
| PATCH | 修订 | 缺陷修复、配置修正、文档/构建优化 | `0.1.4` → `0.1.5` |
| BUILD | 构建号 | **全局单调递增，每次发版（含 RC） +1，永不重置** | `0.1.4.0` → `0.1.4.1` / `0.1.5.1` |

配套约束：

- `BUILD (W)` 为**全局构建计数器**：不因 `X.Y.Z` 变化而回零。故新补丁首发可能是
  `0.1.5.1` 而非 `0.1.5.0`——这是"全局单调"方案的固有表现（避免同日多次 RC 撞
  `versionCode`），属预期而非 bug。
- `versionCode`（`app/build.gradle.kts`）= `X*1_000_000 + Y*10_000 + Z*100 + W`，
  **严格单调递增**，与 `versionName` 在**同一次 commit** 里一起改；tag 名 = `v` + `versionName`。
- 预发行：`X.Y.Z.W-rcN`（N 为该 `X.Y.Z` 下第 N 个 RC），自动标记为 pre-release
  （`release.yml` 按 tag 是否含 `-rc` 判定 `prerelease`）。
- **禁止手改版本号**：一律走 `scripts/release_helper.py plan --type <t> [--rc]`，
  它负责算 `W`、编码 `versionCode`、写回 `build.gradle.kts`（详见 RELEASING.md）。
- 版本演进决策记入 `CHANGELOG.md` 对应版本的 `### 决策` 小节。

## 二、正式版交付标准（硬性）

一个 `vX.Y.Z.W`（或 `vX.Y.Z.W-rcN`）tag 打出去之前，产物必须满足：

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
7. **tag ↔ versionName 一致**：`release.yml` 会在构建前断言二者相等，不一致直接中止。

> 历史教训：v0.1.1 曾被验证脚本字节序笔误误判为"缺 v2"而改配置，导致 v0.1.2 真正
> 丢失 v3。**验证结论必须来自仓库内标准化脚本，不允许临时手写字节搜索。**

## 三、发版流程与确认门禁

每次发版都经 `scripts/release_helper.py` 计算下一版本，并**在打正式 tag 前暂停向用户确认**：

```bash
# 1. Agent 计算目标版本（写入 build.gradle.kts 的 versionCode/versionName）
python3 scripts/release_helper.py plan --type patch        # 计划正式版，如 0.1.5.1
python3 scripts/release_helper.py plan --type patch --rc   # 计划 RC 预发行，如 0.1.5.1-rc1

# 2. 提交版本号改动（与代码改动同一次 commit）

# 3. 确认门禁（关键）：Agent 用 AskUserQuestion 询问"确认发布正式版 vX.Y.Z.W？"
#      • 用户确认            → 打正式 tag      vX.Y.Z.W      （prerelease=false）
#      • 用户不选 / 选 RC / 超时 → 自动打预发行 tag vX.Y.Z.W-rcN（prerelease=true）

# 4. 推送 tag → release.yml 自动：测试 → 校验 tag↔versionName →
#     构建 Release APK → 创建 GitHub Release（RC 自动标记 prerelease）→ 上传 APK

# 5. 下载产物跑 scripts/check_apk_signing.py 验证三绿
# 6. 更新 CHANGELOG.md / PLAN.md（若里程碑状态变化）
```

完整检查清单见 RELEASING.md《发版前检查清单》。

## 四、变更记录规范（CHANGELOG.md）

- 格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，倒序排列。
- 分组用：`Added / Changed / Fixed / Security / 决策`（决策小节记录"为什么"）。
- 每个版本条目必须包含：版本号、日期、对应 commit/tag、`versionCode`。
- 未发布内容放在 `Unreleased` 区，发版时改名并补日期。
- RC 预发行（`-rcN`）**允许单独记 CHANGELOG 条目**，也可随其正式版合并记录；
  实践：若 RC 间差异重大（如 T8 整体落地、黑边修复、底栏美化等），推荐单独写条目以便追溯；
  小步迭代的 RC 可合并到正式版。

## 五、历史版本决策摘要

| 版本 | 决策 | 原因 |
| --- | --- | --- |
| v0.1.0 | 首个 tag，debug 签名回退策略 | 先立架构，签名体系随后落地 |
| v0.1.1 | 正式签名 + 加固全量落地 | 正式发行版安全基线 |
| v0.1.2 | 误改签名配置（仅显式 v1） | 验证脚本笔误导致误判"缺 v2"，教训见上 |
| v0.1.3 | 恢复 v1+v2+v3 显式三开 | 字节级解析证实 v3 缺失，AGP 默认不可依赖 |
| v0.1.4 | 版本号迁移至 **四段式 x.x.x.x** | 引入全局构建号 W + RC 预发行门禁（详见本文件一、二、三） |
