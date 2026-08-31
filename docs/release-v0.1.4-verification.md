# v0.1.4 发版验证报告

> 验证日期：2026-08-31 · 验证工具：`scripts/check_apk_signing.py`（仓库内置，零依赖）
> 里程碑：M0 收尾（数据层 SQLite 化 M0.6），`0.2.0` 留给 M1 真实模型。

## 一、发版链路

| 环节 | 结果 |
| --- | --- |
| 版本对齐 commit | `2c2caff` chore(release): 对齐版本号 v0.1.4 并更新发版文档 |
| tag | `v0.1.4`（指向 `2c2caff`，versionCode 5 / versionName 0.1.4） |
| CI（main push 6263825） | run `33383391229` ✅ success |
| CI（main push 2c2caff） | run `33384346870` ✅ success |
| Release 流水线 | run `33384372730` ✅ success（12/12 步全绿） |
| 产物 | `app-release.apk` 2,553,242 B，sha256 `b522dd69e49f67da2f26b0c8d4a09d97aed0554c2ee98e862391e1ae97be1715` |

Release 流水线关键步骤均成功：发布前测试（含 `:core:agent`/`:core:uistate`/`:core:data`）、
还原签名密钥、构建 Release APK（R8 混淆 + 正式签名）、校验签名与加固、保存混淆映射表、
创建 GitHub Release 并上传 APK。

## 二、签名验证结论（字节级）

```
v1(JAR): YES ['META-INF/CERT.RSA']
signing block: [0x265c1b, 0x268c1b) size=0x2ff8 head==footer: True
  pair 0x7109871a -> v2 ✅
  pair 0xf05368c0 -> v3 ✅
  pair 0x504b4453 -> padding/other (zipalign)
  pair 0x42726577 -> padding/other (zipalign)
  证书 SHA256: 06:2E:80:3E:2E:8F:79:14:86:10:23:CB:2B:9E:93:DC:1C:B6:DC:52:1A:51:33:1A:02:88:80:B6:E6:21:C6:B4  ✅ 与 SignatureGuard 官方指纹一致
结论: v1=True v2=True v3=True —— 三方案齐备（exit 0）
```

包内 `versionName = 0.1.4`、`versionCode = 5`（来自 commit `2c2caff` 的 `app/build.gradle.kts`，
Release 流水线即基于该 commit 构建）。

## 三、交付标准核对（Version.md 硬性要求）

| 要求 | 状态 |
| --- | --- |
| v1 + v2 + v3 显式三开 | ✅（脚本判定 v1/v2/v3 全 True） |
| 证书指纹与 `SignatureGuard` / `check_apk_signing.py` 三处一致 | ✅ `06:2E:80:…:C6:B4` |
| R8 + 资源收缩开启、mapping 非空 | ✅（Release 流水线保留 mapping 产物） |
| `check_apk_signing.py` 退出码 0 | ✅ exit 0 |
| CI 全绿（ci.yml 四 job + release.yml） | ✅ 三次 run 全 success |
| CHANGELOG 已记 | ✅ `[0.1.4]` 条目已写入 |

## 四、交付物

- Release：https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.4
- 验证脚本：`scripts/check_apk_signing.py`
- 变更记录：`CHANGELOG.md` → `[0.1.4]`；路线：`PLAN.md` → M0.6 ✅、焦点转 M1
- 数据层设计定稿：`DATA_LAYER.md`
