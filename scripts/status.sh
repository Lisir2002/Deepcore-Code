#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 查看仓库状态与 GitHub 通道健康度
#
# 用法：./scripts/status.sh
#
# 通道原理：本沙箱对 GitHub 的限制是「DNS 劫持 + 概率性丢包」，不是 IP 封锁。
#   · hosts 注入   治 DNS 劫持（github-tunnel.sh install）
#   · 重试包装     治概率丢包（push.sh / github-tunnel.sh 已内置）
# 详见 docs/github-sandbox-tunnel.md
# ---------------------------------------------------------------------------
set -uo pipefail

cd "$(dirname "$0")/.."
TUNNEL="./scripts/github-tunnel.sh"

if [ -t 1 ]; then
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YEL=$'\033[33m'; C_BLU=$'\033[36m'; C_BLD=$'\033[1m'; C_RST=$'\033[0m'
else
  C_RED=""; C_GRN=""; C_YEL=""; C_BLU=""; C_BLD=""; C_RST=""
fi

REPO_URL="${GH_OWNER:-Lisir2002}/${GH_REPO:-Deepcore-Code}"

echo "${C_BLD}=== 仓库状态 ===${C_RST}"
echo "  远端        : $(git remote get-url origin 2>/dev/null || echo '(未配置)')"
echo "  分支        : $(git branch --show-current 2>/dev/null)"
echo "  本地提交    : $(git rev-list --count HEAD 2>/dev/null) 个"
echo "  最新提交    : $(git log -1 --format='%h %s' 2>/dev/null)"
UNCOMMITTED="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
echo "  未提交改动  : ${UNCOMMITTED} 个文件"
# 与远端的落差（走重试包装，避免概率丢包误报）
if UPSTREAM="$(git rev-list --count "@{upstream}..HEAD" 2>/dev/null)"; then
  echo "  领先远端    : ${UPSTREAM} 个提交"
else
  echo "  领先远端    : (未关联上游分支，首次推送后自动建立)"
fi

echo
echo "${C_BLD}=== 通道健康度 ===${C_RST}"

# 1) DNS 是否仍被劫持
poisoned=0
for host in github.com api.github.com; do
  ip="$(getent hosts "$host" 2>/dev/null | awk '{print $1}' | head -1)"
  case "$ip" in
    198.18.*|198.19.*|100.64.*|"")
      printf "  %s✗%s %-24s %s %s(黑洞网段，DNS 被劫持)%s\n" \
        "$C_RED" "$C_RST" "$host" "${ip:-无记录}" "$C_YEL" "$C_RST"
      poisoned=1
      ;;
    *)
      printf "  %s✓%s %-24s %s\n" "$C_GRN" "$C_RST" "$host" "$ip"
      ;;
  esac
done

# 2) 证书校验是否被关掉（旧方案残留过 sslVerify=false，等于裸奔）
if git config --get-regexp '^http\..*\.sslverify$' 2>/dev/null | grep -qi 'false'; then
  printf "  %s✗%s %-24s %s\n" "$C_RED" "$C_RST" "sslVerify" "被关闭，存在中间人风险"
else
  printf "  %s✓%s %-24s %s\n" "$C_GRN" "$C_RST" "sslVerify" "已启用（证书正常校验）"
fi

# 3) Token 有效性
if [ -n "${GITHUB_TOKEN:-}" ]; then
  LOGIN="$($TUNNEL api GET /user 2>/dev/null | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(d.get('login') if isinstance(d,dict) and 'login' in d else '')
" 2>/dev/null)"
  if [ -n "$LOGIN" ]; then
    printf "  %s✓%s %-24s %s\n" "$C_GRN" "$C_RST" "GITHUB_TOKEN" "有效，已登录为 ${LOGIN}"
  else
    printf "  %s✗%s %-24s %s\n" "$C_RED" "$C_RST" "GITHUB_TOKEN" "无效或已吊销，需重新生成"
  fi
else
  printf "  %s○%s %-24s %s\n" "$C_YEL" "$C_RST" "GITHUB_TOKEN" "未设置（推送前需 export）"
fi

echo
echo "${C_BLD}=== 连通率实测（各 6 次，反映概率丢包程度）===${C_RST}"
probe_rate() {
  local name="$1" url="$2" ok=0 i c
  for ((i = 1; i <= 6; i++)); do
    c="$(timeout 10 curl -s -o /dev/null --max-time 8 -w '%{http_code}' "$url" 2>/dev/null)"
    [ "$c" = "200" ] && ok=$((ok+1)) && printf "√" || printf "×"
  done
  if   [ "$ok" -ge 5 ]; then col="$C_GRN"
  elif [ "$ok" -ge 1 ]; then col="$C_YEL"
  else                       col="$C_RED"; fi
  printf "  %s%s/%s%s  %s\n" "$col" "$ok" "6" "$C_RST" "$name"
}
probe_rate "api.github.com" "https://api.github.com/zen"
probe_rate "github.com"     "https://github.com/zen"

echo
echo "${C_BLD}=== 下一步 ===${C_RST}"
if [ "$poisoned" -eq 1 ]; then
  echo "  1. 修复 DNS：$TUNNEL install"
fi
if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "  · 设置令牌：export GITHUB_TOKEN=ghp_xxx（不要粘贴到对话里，会被 GitHub 自动吊销）"
fi
if [ "$UNCOMMITTED" -gt 0 ]; then
  echo "  · 提交推送：./scripts/push.sh \"提交信息\""
fi
echo "  · 刷新 IP  ：$TUNNEL refresh   （GitHub Anycast 会轮换，连不上先试这个）"
echo "  · 本地检查：./scripts/ci-local.sh"
echo "  · 查 CI   ：https://github.com/${REPO_URL}/actions"
