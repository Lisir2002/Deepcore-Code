# 发布指南

## 一键发版

```bash
git tag -a v0.1.1 -m "版本说明"
./scripts/github-tunnel.sh git push origin v0.1.1
```

推送 tag 即触发 `.github/workflows/release.yml`：跑测试 → 构建 Release APK → 创建 GitHub Release 并上传 APK。
Release 说明由 `generate_release_notes` 自动生成（基于两次 tag 之间的提交）。

> 用 `github-tunnel.sh git` 而非裸 `git`：本沙箱到 github.com 有概率性丢包，
> 单发约 1/3 概率挂在 TLS 握手上，重试包装已内置。

## 版本号规范

在 `app/build.gradle.kts` 里维护，**两者必须同时更新**：

| 字段 | 规则 | 示例 |
| --- | --- | --- |
| `versionCode` | 整数，**严格单调递增**，只增不减 | `1` → `2` → `3` |
| `versionName` | 语义化版本，与 tag 保持一致 | `0.1.0` ↔ tag `v0.1.0` |

`-rc` / `-beta` / `-alpha` 后缀的版本会被 release 流程自动标记为 pre-release。

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

- `release` 签名方案：**v1 + v2 + v3 全开**。v2/v3 校验整包二进制，是防篡改主力；v1 仅做老渠道兼容，不能单独使用。
- 配置就绪时 → 用正式密钥签名。
- **CI 上缺配置 → 直接 `GradleException` 中止**（CI 环境变量存在即视为发版环境，绝不允许打出 debug 签名的"假正式包"）。
- 本地缺配置 → 友好告警后回退 debug 签名，方便随手 `assembleRelease`，但不对外分发。

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

- [ ] `versionCode` 已递增
- [ ] `versionName` 与即将打的 tag 一致
- [ ] 本地 `./scripts/ci-local.sh` 全绿
- [ ] 上一轮 GitHub Actions 的 CI（`core-test` / `android-build` / `release-build` / `design-guard`）全绿
- [ ] Release 构建产物经 `apksigner verify` 确认 v1/v2/v3 均通过
- [ ] `mapping.txt` 显示确有类被混淆（CI 已自动断言，小于 5 条直接失败）
- [ ] 正式版已配置签名（非 debug 回退）；`SignatureGuard.OFFICIAL_SIGNATURE_SHA256` 与当前密钥指纹一致
- [ ] 密钥已离线备份，`/root/deepcode-signing/` 未进入版本库
- [ ] 关键变更已写入 tag 说明

## 相关文档

- `docs/github-sandbox-tunnel.md` —— 沙箱网络通道原理
- `ARCHITECTURE.md` —— 架构设计与扩展点清单
