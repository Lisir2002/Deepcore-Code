#!/usr/bin/env python3
# ---------------------------------------------------------------------------
# 从源图生成 Android 自适应图标全套资源
#
# 用法：
#   python3 scripts/gen-app-icon.py                       # 用默认源图
#   python3 scripts/gen-app-icon.py path/to/new-icon.jpg  # 换源图
#
# 依赖：Pillow（pip3 install pillow）
#
# 产物（全部写入 app/src/main/res）：
#   drawable/ic_launcher_foreground.webp   前景层（透明背景，WebP 无损）
#   drawable/ic_launcher_background.xml    背景层（纯白矢量）
#   mipmap-anydpi-v26/ic_launcher.xml      自适应图标（API 26+ 生效）
#   mipmap-anydpi-v26/ic_launcher_round.xml
#   mipmap-{mdpi..xxxhdpi}/ic_launcher{,_round}.webp  旧设备兜底
#
# 设计要点（都是踩过坑才定下来的，改之前先读注释）：
#
# 1. 源图是 JPEG，没有 alpha 通道。整块贴进前景层会把白底一起带进去，
#    自适应图标被遮罩一切就变成"白底方块"。必须先按背景色抠图。
#
# 2. 抠图用软阈值（LO~HI 之间给渐变 alpha），不能用硬阈值，
#    否则 logo 边缘会留一圈白边。
#
# 3. 内容占比取 0.56：108dp 画布里系统保证可见的只有中心 66dp 圆，
#    实测 0.64 会有 9.6% 内容出圈（圆形遮罩下被切），0.56 降到 1.8%。
#
# 4. 千万不要对 RGBA 图直接调 Image.quantize()——它会连 alpha 一起量化，
#    实测把 36.6% 的实心像素变成半透明（只剩 0.4% 全不透明）。
#    正确做法：只量化 RGB，再把原 alpha 通道贴回去。
#
# 5. 最终选 WebP 无损：实测是唯一"零像素误差 + 体积可接受"的方案
#    （33 KB，同尺寸 PNG 要 51 KB；有损 WebP/量化 PNG 平均误差都在 4 以上）。
#    minSdk=26，WebP 支持无问题。
# ---------------------------------------------------------------------------
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")
DEFAULT_SRC = os.path.join(ROOT, "art/app-icon-source.jpg")

# 自适应图标画布：108dp，xxxhdpi(4x) 下 = 432px
FG = 432
# 内容占画布比例（见设计要点 3）
CONTENT_RATIO = 0.56
# 抠图软阈值：与背景色的通道最大差值 <= LO 视为全透，>= HI 视为全实
LO, HI = 14, 52
# 背景层颜色（与源图底色一致，保证观感还原）
BG_HEX = "#FBFBF9"

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def hex2rgb(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def detect_bg(img):
    """取四角 40x40 均值作为背景色。四角通道最大差值 >12 说明不是纯色底，直接拒绝。"""
    w, h = img.size
    px = img.load()

    def avg(x0, y0, n=40):
        acc = [0, 0, 0]
        for y in range(y0, y0 + n):
            for x in range(x0, x0 + n):
                r, g, b = px[x, y]
                acc[0] += r
                acc[1] += g
                acc[2] += b
        return tuple(v // (n * n) for v in acc)

    corners = [avg(0, 0), avg(w - 40, 0), avg(0, h - 40), avg(w - 40, h - 40)]
    spread = max(max(v) - min(v) for v in zip(*corners))
    if spread > 12:
        raise SystemExit(
            f"源图不是纯色背景（四角通道最大差值 {spread}），自动抠图不可靠。\n"
            f"请换一张纯色底的图，或手动处理后再运行本脚本。"
        )
    return tuple(sum(c[i] for c in corners) // 4 for i in range(3))


def key_out(img, bg, lo=LO, hi=HI):
    """软阈值抠图：距离背景越近越透明，保留抗锯齿边缘。"""
    w, h = img.size
    px = img.load()
    out = Image.new("RGBA", (w, h))
    dst = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            d = max(abs(r - bg[0]), abs(g - bg[1]), abs(b - bg[2]))
            if d <= lo:
                a = 0
            elif d >= hi:
                a = 255
            else:
                a = int((d - lo) / (hi - lo) * 255)
            dst[x, y] = (r, g, b, a)
    return out


def safe_zone_report(canvas, fg=FG):
    """统计有多少可见内容落在中心 66dp 圆之外。"""
    rad = int(fg * 66 / 108) / 2
    px = canvas.load()
    outside = painted = 0
    for y in range(0, fg, 2):
        for x in range(0, fg, 2):
            if px[x, y][3] > 128:
                painted += 1
                if math.hypot(x - fg / 2, y - fg / 2) > rad:
                    outside += 1
    return outside / max(painted, 1) * 100


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SRC
    if not os.path.exists(src):
        raise SystemExit(f"源图不存在: {src}")

    img = Image.open(src).convert("RGB")
    print(f"源图: {src}  {img.size[0]}x{img.size[1]}")

    bg = detect_bg(img)
    print(f"背景色: #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}（四角采样）")

    # --- 抠图 → 裁边 → 缩放 → 居中 ---
    rgba = key_out(img, bg)
    bbox = rgba.getbbox()
    print(f"内容边界: {bbox[2]-bbox[0]}x{bbox[3]-bbox[1]}")

    content = rgba.crop(bbox)
    target = int(FG * CONTENT_RATIO)
    scale = min(target / content.width, target / content.height)
    nw, nh = int(content.width * scale), int(content.height * scale)
    content = content.resize((nw, nh), Image.LANCZOS)
    print(f"缩放后: {nw}x{nh}（占画布 {nw/FG*100:.0f}%）")

    canvas = Image.new("RGBA", (FG, FG), (0, 0, 0, 0))
    canvas.paste(content, ((FG - nw) // 2, (FG - nh) // 2), content)

    outside = safe_zone_report(canvas)
    flag = "✅" if outside < 2 else ("⚠️" if outside < 6 else "❌")
    print(f"66dp 安全圆外内容: {outside:.1f}% {flag}")

    # --- 前景层：WebP 无损（零像素误差，体积最优）---
    os.makedirs(f"{RES}/drawable", exist_ok=True)
    fg_path = f"{RES}/drawable/ic_launcher_foreground.webp"
    canvas.save(fg_path, "WEBP", lossless=True, method=6)
    print(f"✅ {os.path.relpath(fg_path, ROOT)}  {os.path.getsize(fg_path)/1024:.1f} KB")

    # --- 背景层：纯色矢量 ---
    bg_path = f"{RES}/drawable/ic_launcher_background.xml"
    with open(bg_path, "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n')
        f.write('    android:width="108dp"\n')
        f.write('    android:height="108dp"\n')
        f.write('    android:viewportWidth="108"\n')
        f.write('    android:viewportHeight="108">\n')
        f.write(f'    <path android:fillColor="{BG_HEX}"\n')
        f.write('          android:pathData="M0,0h108v108h-108z"/>\n')
        f.write('</vector>\n')
    print(f"✅ {os.path.relpath(bg_path, ROOT)}")

    # --- 自适应图标（API 26+）---
    anydpi = f"{RES}/mipmap-anydpi-v26"
    os.makedirs(anydpi, exist_ok=True)
    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
        '</adaptive-icon>\n'
    )
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, name), "w", encoding="utf-8") as f:
            f.write(adaptive)
    print(f"✅ {os.path.relpath(anydpi, ROOT)}/ic_launcher{{,_round}}.xml")

    # --- 旧设备兜底：各密度位图，白底合成 ---
    for folder, size in DENSITIES.items():
        out_dir = f"{RES}/{folder}"
        os.makedirs(out_dir, exist_ok=True)
        r = canvas.resize((size, size), Image.LANCZOS)

        flat = Image.new("RGB", (size, size), bg)
        flat.paste(r, (0, 0), r)
        flat.save(f"{out_dir}/ic_launcher.webp", "WEBP", quality=92)

        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
        circ = Image.new("RGB", (size, size), bg)
        circ.paste(r, (0, 0), r)
        circ.putalpha(mask)
        circ.save(f"{out_dir}/ic_launcher_round.webp", "WEBP", quality=92)
    print(f"✅ mipmap-{{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}} 各 2 个文件")

    print("\n完成。注意：清单里必须引用图标，否则装的还是系统默认机器人图：")
    print('    android:icon="@mipmap/ic_launcher"')
    print('    android:roundIcon="@mipmap/ic_launcher_round"')


if __name__ == "__main__":
    main()
