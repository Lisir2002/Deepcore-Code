#!/usr/bin/env python3
"""APK 签名方案验证脚本（零依赖，纯标准库）。

解析 APK Signing Block 二进制结构，判定 v1/v2/v3 是否启用，并提取签名证书
SHA-256 指纹与 SignatureGuard 的官方指纹比对。

用法:
    python3 scripts/check_apk_signing.py app-release.apk [more.apk ...]

结构规范 (https://source.android.com/docs/security/features/apksigning/v2):
    [u64 size(不含自身8B)][ID-value pairs...][u64 size][16B magic "APK Sig Block 42"]
定位: EOCD -> central directory offset; footer = cd_offset-24; head = cd_offset-size-8。

方案 ID (小端 uint32):
    0x7109871a = v2    0xf05368c0 = v3    0x1b93ad61 = v3.1
    0x504b4453 / 0x42726577 = zipalign padding（非签名方案，正常存在）
"""
import struct
import sys
import zipfile
import hashlib

MAGIC = b"APK Sig Block 42"
SCHEMES = {0x7109871a: "v2", 0xF05368C0: "v3", 0x1B93AD61: "v3.1"}

# 官方发布证书指纹（与 app/.../security/SignatureGuard.kt 保持同步）
OFFICIAL_FP = "06:2E:80:3E:2E:8F:79:14:86:10:23:CB:2B:9E:93:DC:1C:B6:DC:52:1A:51:33:1A:02:88:80:B6:E6:21:C6:B4"


def fp(der: bytes) -> str:
    return ":".join(f"{b:02X}" for b in hashlib.sha256(der).digest())


def u32(d, o):
    return struct.unpack_from("<I", d, o)[0]


def u64(d, o):
    return struct.unpack_from("<Q", d, o)[0]


def extract_certs(block_value: bytes):
    """v2/v3 value 两层 length-prefixed signer 序列 -> certificates DER 列表。"""
    certs, v = [], block_value
    if len(v) < 8:
        return certs
    seq_len = u64(v, 0)
    p, end = 8, 8 + seq_len
    while p + 8 <= min(end, len(v)):
        signer_len = u64(v, p)
        signer = v[p + 8: p + 8 + signer_len]
        p += 8 + signer_len
        if len(signer) < 8:
            continue
        sd_len = u64(signer, 0)
        sd = signer[8: 8 + sd_len]
        if len(sd) < 8:
            continue
        d_len = u64(sd, 0)
        c_off = 8 + d_len
        if c_off + 8 > len(sd):
            continue
        c_len = u64(sd, c_off)
        cseq = sd[c_off + 8: c_off + 8 + c_len]
        q = 0
        while q + 4 <= len(cseq):
            n = u32(cseq, q)
            certs.append(cseq[q + 4: q + 4 + n])
            q += 4 + n
    return certs


def check(path: str) -> bool:
    print(f"\n===== {path} =====")
    data = open(path, "rb").read()
    eocd = data.rfind(b"PK\x05\x06")
    cd_offset = u32(data, eocd + 16)

    with zipfile.ZipFile(path) as z:
        v1files = [n for n in z.namelist()
                   if n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC"))]
    has_v1 = bool(v1files)
    print(f"v1(JAR): {'YES ' + str(v1files) if has_v1 else 'NO'}")

    has_v2 = has_v3 = False
    if data[cd_offset - 16: cd_offset] != MAGIC:
        print("v2/v3: NO signing block")
    else:
        size = u64(data, cd_offset - 24)
        head = cd_offset - size - 8
        head_ok = u64(data, head) == size
        print(f"signing block: [{head:#x}, {cd_offset:#x}) size={size:#x} head==footer: {head_ok}")
        off, end = head + 8, cd_offset - 24
        while off < end:
            plen = u64(data, off)
            pid = u32(data, off + 8)
            val = data[off + 12: off + 8 + plen]
            name = SCHEMES.get(pid, f"padding/other(0x{pid:08x}, len={len(val)})")
            line = f"  pair 0x{pid:08x} -> {name}"
            if pid in SCHEMES:
                has_v2 |= pid == 0x7109871a
                has_v3 |= pid == 0xF05368C0
                line += f" 证书数={len(extract_certs(val))}"
            print(line)
            off += 8 + plen
        # 证书指纹比对（ASN.1 搜索法，跨 v2/v3 提取）
        seen = set()
        m = data.find(MAGIC)
        pos = -1
        while (pos := data.find(b"\x30\x82", pos + 1)) != -1 and pos < m:
            ln = int.from_bytes(data[pos + 2:pos + 4], "big") + 4
            if 700 < ln < 1500 and pos + ln <= len(data):
                seen.add(hashlib.sha256(data[pos:pos + ln]).hexdigest().upper())
        for h in seen:
            mark = "✅ 与 SignatureGuard 官方指纹一致" if h == OFFICIAL_FP.replace(":", "") else "⚠️ 非官方证书！"
            print(f"  证书 SHA256: {':'.join(h[i:i+2] for i in range(0, 64, 2))}  {mark}")

    ok = has_v1 and has_v2 and has_v3
    print(f"结论: v1={has_v1} v2={has_v2} v3={has_v3} -> {'✅ 三方案齐备' if ok else '❌ 不满足发布要求(需 v1+v2+v3)'}")
    return ok


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    all_ok = all(check(p) for p in sys.argv[1:])
    sys.exit(0 if all_ok else 1)
