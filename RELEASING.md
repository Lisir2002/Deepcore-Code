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

## 签名配置（正式发布前必须处理）

**当前状态**：`app/build.gradle.kts` 的 release 构建回退到 debug 签名：

```kotlin
buildTypes {
    release {
        // 未配置正式 keystore 时回退到 debug 签名，
        // 这样 CI 上的 assembleRelease 不会因为缺 keystore 直接失败。
        signingConfig = signingConfigs.getByName("debug")
    }
}
```

这意味着 v0.1.0 的 Release APK **可以验证分发链路，但不适合对外分发**——
debug 签名的 APK 无法上架应用商店，且签名密钥是公开的。

### 配置正式签名

1. 生成 keystore（只做一次，密钥文件切勿提交）：

```bash
keytool -genkeypair -v -keystore agentide.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias agentide
```

2. 把 keystore 编码成 base64，填入仓库 Secrets：

```bash
base64 -w 0 agentide.jks   # 输出存入 SIGNING_KEYSTORE_BASE64
```

需要配置 4 个 Secrets（`Settings → Secrets and variables → Actions`）：

| Secret | 内容 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | keystore 文件的 base64 |
| `SIGNING_KEY_ALIAS` | 密钥别名 |
| `SIGNING_KEY_PASSWORD` | 密钥密码 |
| `SIGNING_STORE_PASSWORD` | keystore 密码 |

3. 修改 `app/build.gradle.kts`，让 release 读取环境变量：

```kotlin
signingConfigs {
    create("release") {
        storeFile = System.getenv("SIGNING_KEYSTORE")?.let { file(it) }
        storePassword = System.getenv("SIGNING_STORE_PASSWORD")
        keyAlias = System.getenv("SIGNING_KEY_ALIAS")
        keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
    }
}

buildTypes {
    release {
        isMinifyEnabled = true          // 正式版建议开启，配合 proguard 规则
        isShrinkResources = true
        signingConfig = signingConfigs.getByName("release")
    }
}
```

4. 在 release.yml 的构建步骤前解码 keystore：

```yaml
- name: 准备签名
  run: echo "${{ secrets.SIGNING_KEYSTORE_BASE64 }}" | base64 -d > /tmp/agentide.jks
  env:
    SIGNING_KEYSTORE: /tmp/agentide.jks
```

> 开启 `isMinifyEnabled` 后，Compose 与 Koin 的混淆规则需要同步配置，
> 否则 Release 包可能运行时崩溃。建议先在 Debug 之外的渠道灰度验证。

## 发版前检查清单

- [ ] `versionCode` 已递增
- [ ] `versionName` 与即将打的 tag 一致
- [ ] 本地 `./scripts/ci-local.sh` 全绿
- [ ] 上一轮 GitHub Actions 的 CI 三个 job 全绿
- [ ] 正式版已配置签名（非 debug 回退）
- [ ] 关键变更已写入 tag 说明

## 相关文档

- `docs/github-sandbox-tunnel.md` —— 沙箱网络通道原理
- `ARCHITECTURE.md` —— 架构设计与扩展点清单
