# v0.1.3 发版验证报告

> 验证日期：2026-08-31 · 验证工具：`scripts/check_apk_signing.py`（仓库内置，零依赖）

## 一、发版链路

| 环节 | 结果 |
| --- | --- |
| 修复 commit | `09032e8` fix(release): 显式开启 v3 签名方案 |
| 文档 commit | `312682f` docs: 搭建项目文档体系 |
| tag | `v0.1.3`（指向 312682f） |
| CI（main push） | run `33373096288` ✅ success（签名修复）/ `33374086785`（文档 commit） |
| Release 流水线 | run `33374103142` ✅ success |
| 产物 | `app-release.apk` 2,470,615 B，sha256 `6703bdac…ff4173c` |

## 二、签名验证结论（字节级）

```
v1(JAR): YES [META-INF/CERT.RSA]
signing block: [0x251990, 0x254990) head==footer: True
  pair 0x7109871a -> v2 ✅
  pair 0xf05368c0 -> v3 ✅
  证书 SHA256: 06:2E:80:…:C6:B4 ✅ 与 SignatureGuard 官方指纹一致
结论: v1=True v2=True v3=True —— 三方案齐备（exit 0）
```

包内 `versionName = 0.1.3`（AXML UTF-16 串确认），`versionCode = 4`。

## 三、三版本对比

| 方案 | v0.1.1 | v0.1.2 | v0.1.3 |
| --- | --- | --- | --- |
| v1 (JAR) | ✅ | ✅ | ✅ |
| v2 (整包校验) | ✅ | ✅ | ✅ |
| v3 (密钥轮换) | ✅ | ❌ | ✅ |
| 证书指纹 | ✅ | ✅ | ✅ |

## 四、复盘（为什么会有 0.1.2 的回归）

1. v0.1.1 发版后，验证脚本把 v2 方案 ID `0x7109871a` 的小端字节**写错**为
   `1a 79 08 71`（正确是 `1a 87 09 71`），导致误判"v0.1.1 缺 V2"。
2. 基于误判修改签名配置：先改用不存在的旧属性名（CI 编译失败），再退到
   "仅显式 `enableV1Signing=true`"（commit `99794a5`，v0.1.2）。
3. AGP 8.7.3 + minSdk 26 的默认签名方案**不含 v3** → v0.1.2 真正丢失 v3。
4. 本轮以修正后的字节级解析复验三版本，确认以上因果，恢复三显式开启并入库
   标准化验证脚本，杜绝临时手写字节搜索。

## 五、交付物

- Release：https://github.com/Lisir2002/Deepcore-Code/releases/tag/v0.1.3
- 验证脚本：`scripts/check_apk_signing.py`
- 加固状态：R8 + 资源收缩、禁备份、禁明文流量、Release 剥离日志、
  `SignatureGuard` 运行时指纹校验（详见 RELEASING.md）
