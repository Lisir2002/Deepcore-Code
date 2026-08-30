#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 拉取 GitHub Actions 日志并提取错误
#
# 用法：
#   ./scripts/ci-logs.sh                 # 拉取最新一次运行的日志
#   ./scripts/ci-logs.sh <run_id>        # 拉取指定运行
#   ./scripts/ci-logs.sh <run_id> --full # 显示完整编译日志（不只错误行）
#
# 为什么需要这个脚本：
#   GitHub 把日志托管在 Azure Blob（results-receiver.actions.githubusercontent.com），
#   该域名在本沙箱同样被 DNS 污染，必须再走一次 DoH + --resolve 才能下载。
#   另外日志文件名含中文，解压后是乱码文件名，手工翻找很痛苦。
#
# 依赖：GITHUB_TOKEN（读权限即可）
# ---------------------------------------------------------------------------
set -uo pipefail

cd "$(dirname "$0")/.."
TUNNEL="./scripts/github-tunnel.sh"

C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YEL=$'\033[33m'; C_BLU=$'\033[34m'; C_RST=$'\033[0m'

RUN_ID="${1:-}"
FULL=false
for a in "$@"; do [ "$a" = "--full" ] && FULL=true; done

# 未指定 run id 则取最新一次
if [ -z "$RUN_ID" ]; then
  RUN_ID="$($TUNNEL api GET "/repos/${GH_OWNER:-Lisir2002}/${GH_REPO:-Deepcore-Code}/actions/runs?per_page=1" 2>/dev/null \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['workflow_runs'][0]['id'])" 2>/dev/null)"
fi
[ -z "$RUN_ID" ] && { echo "${C_RED}无法获取 run id${C_RST}"; exit 1; }
echo "${C_BLU}run id:${C_RST} $RUN_ID"

OUT=/tmp/ci-logs-$$
mkdir -p "$OUT"

# --- 1. 拿 302 的 Location ---
LOC=$(timeout 25 curl -s -o /dev/null -w "%{redirect_url}" --max-time 20 \
  -H "Authorization: Bearer ${GITHUB_TOKEN:?请设置 GITHUB_TOKEN}" \
  "https://api.github.com/repos/${GH_OWNER:-Lisir2002}/${GH_REPO:-Deepcore-Code}/actions/runs/${RUN_ID}/logs" 2>/dev/null)

if [ -z "$LOC" ]; then
  echo "${C_RED}未拿到日志重定向地址——通常是令牌权限不足或已被吊销${C_RST}"
  exit 1
fi

# --- 2. 对 Blob 域名再做一次 DoH + --resolve（它同样被 DNS 污染）---
BHOST=$(echo "$LOC" | sed -E 's#https?://([^/]+)/.*#\1#')
BIP=$("$TUNNEL" resolve "$BHOST" 2>/dev/null)
[ -z "$BIP" ] && { echo "${C_RED}无法解析 ${BHOST}${C_RST}"; exit 1; }

timeout 90 curl -sL --resolve "${BHOST}:443:${BIP}" --max-time 80 "$LOC" -o "$OUT/logs.zip" 2>/dev/null
unzip -q -o "$OUT/logs.zip" -d "$OUT" 2>/dev/null

echo "${C_BLU}日志目录:${C_RST} $OUT"
echo

# --- 3. 提取错误 ---
if [ "$FULL" = true ]; then
  find "$OUT" -path "*/6_*.txt" -exec sh -c 'echo "════ $1 ════"; cat "$1"' _ {} \; 2>/dev/null
  exit 0
fi

echo "${C_YEL}════ 编译错误 ════${C_RST}"
FOUND=0
while IFS= read -r f; do
  ERRS=$(grep -aE "^(e|w): |error:|FAILURE:|Execution failed for task|What went wrong|Caused by|Unresolved reference" "$f" 2>/dev/null)
  if [ -n "$ERRS" ]; then
    FOUND=1
    echo "--- $(basename "$f") ---"
    echo "$ERRS" | sed 's/^[0-9T:.Z-]*Z //' | sort -u | head -25
    echo
  fi
done < <(find "$OUT" -name "*.txt" 2>/dev/null)

[ "$FOUND" -eq 0 ] && echo "${C_GRN}未发现编译错误${C_RST}"
