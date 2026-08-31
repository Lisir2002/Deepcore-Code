# 发布指南

## 一键发版

版本号**禁止手改**，一律由 `scripts/release_helper.py` 计算（详见 `Version.md` 一、三）。
典型流程（Agent 会自动跑，这里给人工对照）：

```bash
# 1. 计算并写入下一版本（patch 修复 / minor 新功能 / major 破坏性）
python3 scripts/release_helper.py plan --type patch        # 正式版，如 0.1.5.1
python3 scripts/release_helper.py plan --type patch --rc   # RC 预发行，如 0.1.5.1-rc1

# 2. 提交版本号改动（与代码同一次 commit）
git add app/build.gradle.kts && git commit -m "chore(release): 对齐版本号 vX.Y.Z.W"

# 3. 确认门禁：Agent 用 AskUserQuestion 询问是否发正式版
#      • 确认           → git tag -a vX.Y.Z.W      -m "..." && 推送
#      • 不选/选 RC/超时 → git tag -a vX.Y.Z.W-rcN -m "..." && 推送（自动 prerelease）

# 4. 推送 tag 触发 release.yml
./scripts/github-tunnel.sh git push origin vX.Y.Z.W        # 或 vX.Y.Z.W-rcN
```

推送 tag 即触发 `.github/workflows/release.yml`：校验 tag↔versionName → 跑测试 →
构建 Release APK → 创建 GitHub Release 并上传 APK（含 `-rc` 自动标记 prerelease）。
Release 说明由 `generate_release_notes` 自动生成（基于两次 tag 之间的提交）。
版本号规则与产物交付标准见 `Version.md`，版本演进历史见 `CHANGELOG.md`。

> 用 `github-tunnel.sh git` 而非裸 `git`：本沙箱到 github.com 有概率性丢包，
> 单发约 1/3 概率挂在 TLS 握手上，重试包装已内置。

## 版本号规范

采用**四段式** `MAJOR.MINOR.PATCH.BUILD`（记作 `X.Y.Z.W`），在 `app/build.gradle.kts` 里维护。
**禁止手改**，一律走 `scripts/release_helper.py plan`：

| 字段 | 规则 | 示例 |
| --- | --- | --- |
| `versionCode` | `X*1_000_000 + Y*10_000 + Z*100 + W`，**严格单调递增** | `0.1.4.0` → `10400`；`0.1.5.1` → `10501` |
| `versionName` | `X.Y.Z.W`；预发行追加 `-rcN`，与 tag 保持一致 | `0.1.4.0` ↔ tag `v0.1.4.0`；`0.1.5.1-rc1` ↔ `v0.1.5.1-rc1` |

- `W` 为**全局单调递增构建号**：每次发版（含 RC）+1，不因 `X.Y.Z` 变化而重置；
  故新补丁首发可能是 `0.1.5.1` 而非 `0.1.5.0`（见 `Version.md` 一）。
- 含 `-rc` 的 tag 会被 `release.yml` 自动标记为 pre-release。
- `release.yml` 会在构建前断言 tag 与 `versionName` 完全一致，不一致直接中止。

发版前务必确认 tag 名与 `versionName` 对齐，否则 GitHub Release 的版本号会与 APK 内部版本不一致。

## 签名与加固配置（已落地）

正式签名、R8 混淆、清单加固、运行时防篡改均已实现，下面是与代码实际匹配的状态说明。
改构建脚本前请先读这里，避免把已经调好的配置改回 debug 回退。

### 密钥（实体在仓库外）

密钥实体由 `keytool` 生成，存放于沙箱之外，仓库内只提交模板：

| 项 | 值 |
| --- | --- |
| 文件 | `/root/deepcode-signing/deepcode-release.jks`（**仓库外**，物理隔离） |
| 格式 | PKCS12（store 与 key 用**同一口令**，否则签名阶段报 `not a private key`，已踩坑） |
| 别名 | `deepcode` |
| 算法/长度 | RSA / 2048，validity 10000 天 |
| 证书 SHA-256（小写） | `062e803e2e8f7914861023cb2b9e93dc1cb6dc521a51331a028880b6e621c6b4` |
| 备份文件 | `/root/deepcode-signing/{fingerprint.txt,.passwords,keystore.base64.txt}`（权限 600，均不入库） |

**密钥是资产也是单点故障**：丢了就要换密钥、且旧用户无法增量升级（Android 要求同包名同签名才能覆盖安装）。请离线备份 `deepcode-release.jks`，切勿进版本库、勿传聊天工具。

### 本地配置（keystore.properties）

仓库已提交 `keystore.properties.example`，本地按需复制并填真实值（真实文件已被 `.gitignore` 屏蔽）：

```bash
cp keystore.properties.example keystore.properties
chmod 600 keystore.properties
```

`app/build.gradle.kts` 读取优先级：**环境变量（CI） > keystore.properties（本地）**，两条路互不干扰。

### CI 配置（GitHub Secrets）

`release.yml` 从 `SIGNING_KEYSTORE_BASE64` 解码出 keystore（解码到 `$RUNNER_TEMP`，不进工作区），再注入 `SIGNING_STORE_FILE / SIGNING_STORE_PASSWORD / SIGNING_KEY_ALIAS / SIGNING_KEY_PASSWORD` 四个环境变量。需要配置 4 个 Secrets（`Settings → Secrets and variables → Actions`）：

| Secret | 内容 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `keystore.base64.txt` 的内容（`base64 -w0 deepcode-release.jks`） |
| `SIGNING_KEY_ALIAS` | `deepcode` |
| `SIGNING_KEY_PASSWORD` | 密钥口令 |
| `SIGNING_STORE_PASSWORD` | keystore 口令（PKCS12 下与上面相同） |

### 构建行为（app/build.gradle.kts）

- `release` 签名方案：**v1 + v2 + v3 三个开关全部显式 `= true`，不依赖 AGP 默认值**。
  字节级验证结论：AGP 8.7.3 + minSdk 26 的默认方案**不含 v3**（v0.1.2 教训），
  且显式三开时 v1/v2/v3 全部正确命中（v0.1.1 已复验）。v2/v3 校验整包二进制，是防篡改主力；
  v1 仅做老渠道兼容，不能单独使用。
- 配置就绪时 → 用正式密钥签名。
- **CI 上缺配置 → 直接 `GradleException` 中止**（CI 环境变量存在即视为发版环境，绝不允许打出 debug 签名的"假正式包"）。
- 本地缺配置 → 友好告警后回退 debug 签名，方便随手 `assembleRelease`，但不对外分发。
- CI 强制签名的判断是 `CI=true && 本次 task 名含 Release`：GitHub 给每个 job 都注入
  `CI=true`，只看它会把 core-test 一起拦死（v0.1.1 前踩过）。

### 产物验证（发版必做）

```bash
python3 scripts/check_apk_signing.py app-release.apk
```

脚本零依赖（纯 Python 标准库），字节级解析 APK Signing Block：判定 v1/v2/v3、
提取签名证书指纹与 `SignatureGuard` 官方指纹比对。**三方案全 True 且指纹 ✅、退出码 0
才可分发。** 背景：v0.1.2 曾因临时手写字节搜索时把 v2 方案 ID 字节序写错而误判
"缺 V2"，连锁改配置后真正丢了 V3——验证一律走本脚本，不再手写字节搜索。

### 代码加固清单

| 层面 | 做法 | 文件 |
| --- | --- | --- |
| 代码混淆 | `isMinifyEnabled` + `isShrinkResources`，规则见 proguard | `app/build.gradle.kts`、`app/proguard-rules.pro` |
| 防重签名 | 启动时比对 APK 证书 SHA-256 与官方指纹；Play / debug 证书按来源跳过 | `app/.../security/SignatureGuard.kt`、`DeepCoreCodeApp.kt` |
| 备份加固 | `android:allowBackup="false"`，禁止 adb 备份窃取数据 | `AndroidManifest.xml` |
| 明文流量 | `usesCleartextTraffic=false` + `networkSecurityConfig` | `AndroidManifest.xml`、`res/xml/network_security_config.xml` |
| 日志清理 | Release 包移除 `android.util.Log` 调用（保留崩溃栈行号） | `proguard-rules.pro` 末尾 |

> proguard 规则都是踩坑换来的（Compose / Koin / kotlinx-serialization 必须保留），
> 删之前先读注释。Release-only 崩溃用 `app/build/outputs/mapping/release/mapping.txt` 还原栈。
> 重新生成密钥或启用 Play App Signing 后，**必须同步更新 `SignatureGuard.OFFICIAL_SIGNATURE_SHA256`**。

## 发版前检查清单

- [ ] `versionCode` 已由 `scripts/release_helper.py` 计算且单调递增（`X*1e6+Y*1e4+Z*100+W`）
- [ ] `versionName` 与即将打的 tag 一致（含 `-rcN` 预发行）；`release.yml` 会自动校验
- [ ] 本地 `./scripts/ci-local.sh` 全绿
- [ ] 上一轮 GitHub Actions 的 CI（`core-test` / `android-build` / `release-build` / `design-guard`）全绿
- [ ] Release 构建产物经 `python3 scripts/check_apk_signing.py` 验证 v1/v2/v3 三绿 + 指纹一致
- [ ] `mapping.txt` 显示确有类被混淆（CI 已自动断言，小于 5 条直接失败）
- [ ] 正式版已配置签名（非 debug 回退）；`SignatureGuard.OFFICIAL_SIGNATURE_SHA256` 与当前密钥指纹一致
- [ ] 密钥已离线备份，`/root/deepcode-signing/` 未进入版本库
- [ ] 关键变更已写入 tag 说明

## 相关文档

- `Version.md` —— 版本号规则、交付标准、变更记录规范
- `CHANGELOG.md` —— 各版本实际变更与决策记录
- `PLAN.md` —— 里程碑路线图
- `agent.md` —— AI 协同开发规范（含踩坑速查表）
- `ARCHITECTURE.md` —— 架构设计与扩展点清单
- `docs/github-sandbox-tunnel.md` —— 沙箱网络通道原理
