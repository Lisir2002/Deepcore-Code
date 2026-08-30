#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 查看通道与仓库状态
#
# 用法：./scripts/status.sh
#
# 说明：GitHub 的 CI 结果在本机查不到（网络限制），需要让 AI 助手代查 —— 
#       它有独立的出网通道能读到 GitHub API。直接问"CI 跑完了吗"即可。
# ---------------------------------------------------------------------------
set -uo pipefail

cd "$(dirname "$0")/.."

if [ -t 1 ]; then
  C_GRN=$'\033[32m'; C_YEL=$'\033[33m'; C_BLU=$'\033[36m'; C_BLD=$'\033[1m'; C_RST=$'\033[0m'
else
  C_GRN=""; C_YEL=""; C_BLU=""; C_BLD=""; C_RST=""
fi

echo "${C_BLD}=== 仓库状态 ===${C_RST}"
echo "  分支        : $(git branch --show-current 2>/dev/null)"
echo "  本地提交    : $(git rev-list --count HEAD 2>/dev/null) 个"
echo "  最新提交    : $(git log -1 --format='%h %s' 2>/dev/null)"
echo "  未提交改动  : $(git status --porcelain 2>/dev/null | wc -l | tr -d ' ') 个文件"

echo
echo "${C_BLD}=== 推送通道 ===${C_RST}"
GITHUB_URL="https://github.com/Lisir2002/Deepcore-Code.git"
REDIRECT="$(git config --get-regexp "^url\..*\.insteadof$" 2>/dev/null || true)"

if [ -n "$REDIRECT" ]; then
  echo "$REDIRECT" | while read -r line; do
    KEY="${line%% *}"
    VAL="${line#* }"
    if [ "$VAL" = "$GITHUB_URL" ]; then
      TARGET="${KEY#url.}"
      TARGET="${TARGET%.insteadof}"
      # 令牌脱敏：只显示用户名部分
      MASKED="$(echo "$TARGET" | sed -E 's#(https://[^:]*:)[^@]*(@.*)#\1****\2#')"
      echo "  ${C_GRN}✓ 已配置重定向${C_RST}"
      echo "      $GITHUB_URL"
      echo "        ↓"
      echo "      $MASKED"
    fi
  done
else
  echo "  ${C_YEL}○ 尚未配置中转通道${C_RST}"
  echo "      执行：./scripts/setup-gitee.sh <你的Gitee用户名>"
fi

echo
echo "${C_BLD}=== 网络可达性 ===${C_RST}"
probe() {
  local name="$1" url="$2"
  local code
  code="$(curl -s -o /dev/null --max-time 8 -w '%{http_code}' "$url" 2>/dev/null || echo 000)"
  if [ "$code" = "200" ] || [ "$code" = "301" ] || [ "$code" = "302" ]; then
    printf "  %s✓%s %-22s %s\n" "$C_GRN" "$C_RST" "$name" "$code"
  else
    printf "  %s✗%s %-22s %s (%s)\n" "$C_YEL" "$C_RST" "$name" "$code" "不可达"
  fi
}
probe "Gitee（主通道）"  "https://gitee.com"
probe "GitHub"          "https://api.github.com"

echo
echo "${C_BLD}=== 下一步 ===${C_RST}"
if [ -z "$REDIRECT" ]; then
  echo "  1. 配置通道：./scripts/setup-gitee.sh <Gitee用户名>"
else
  echo "  提交推送：./scripts/push.sh \"提交信息\""
  echo "  本地检查：./scripts/ci-local.sh"
fi
echo "  查 CI 结果：让 AI 助手代查（GitHub 在本机不可达）"
