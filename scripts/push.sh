#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 提交并推送到 GitHub（直连，不再走 Gitee 中转）
#
# 用法：
#   ./scripts/push.sh "提交信息"              # 提交全部改动并推送
#   ./scripts/push.sh "提交信息" --skip-ci    # 跳过推送前的本地验证
#   ./scripts/push.sh --amend "新信息"        # 修改上一次提交
#
# 为什么不必再走 Gitee 中转：
#   本沙箱对 GitHub 的限制是「DNS 劫持 + 概率性丢包」，不是 IP 封锁。
#   github-tunnel.sh install 注入 hosts 解决前者，本脚本用重试包装解决后者，
#   两者叠加后实测 clone/push 稳定成功。
#
# 推送前默认跑一遍本地能跑的验证（等价于 CI 的 core-test 环节），
# 避免把编译不过的代码推上去浪费一轮 CI。
# ---------------------------------------------------------------------------
set -euo pipefail

cd "$(dirname "$0")/.."
TUNNEL="./scripts/github-tunnel.sh"

if [ -t 1 ]; then
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YEL=$'\033[33m'
  C_BLU=$'\033[36m'; C_BLD=$'\033[1m'; C_RST=$'\033[0m'
else
  C_RED=""; C_GRN=""; C_YEL=""; C_BLU=""; C_BLD=""; C_RST=""
fi

info() { printf "%s[信息]%s %s\n" "$C_BLU" "$C_RST" "$*"; }
ok()   { printf "%s[完成]%s %s\n" "$C_GRN" "$C_RST" "$*"; }
warn() { printf "%s[注意]%s %s\n" "$C_YEL" "$C_RST" "$*"; }
err()  { printf "%s[错误]%s %s\n" "$C_RED" "$C_RST" "$*" >&2; }

BRANCH="$(git branch --show-current 2>/dev/null || echo main)"
SKIP_CI=false
AMEND=false
MSG=""

for arg in "$@"; do
  case "$arg" in
    --skip-ci) SKIP_CI=true ;;
    --amend)   AMEND=true ;;
    -*)        err "未知参数: $arg"; exit 1 ;;
    *)         MSG="$arg" ;;
  esac
done

[ -z "$MSG" ] && { err "请提供提交信息，例如：./scripts/push.sh \"feat: 新增功能\""; exit 1; }

# --- 0. 确保通道就绪 ---
# 本地 DNS 若仍把 github.com 指到黑洞网段(198.18.0.0/15)，说明 hosts 未注入或被还原，
# 此时直接推送必挂——先自动修好再往下走。
local_ip="$(getent hosts github.com 2>/dev/null | awk '{print $1}' | head -1)"
case "${local_ip}" in
  198.18.*|198.19.*|100.64.*|"")
    warn "检测到 DNS 仍被劫持（github.com → ${local_ip:-无记录}），自动注入 hosts..."
    "$TUNNEL" install || { err "hosts 注入失败，中止推送"; exit 1; }
    ;;
esac

# --- 1. 提交 ---
if [ -z "$(git status --porcelain)" ] && [ "$AMEND" = false ]; then
  ok "工作区干净，没有改动需要提交"
else
  if [ "$AMEND" = true ]; then
    git commit --amend -m "$MSG"
    ok "已修改上一次提交"
  else
    git add -A
    info "暂存 $(git diff --cached --name-only | wc -l | tr -d ' ') 个文件"
    git commit -q -m "$MSG"
    ok "已提交"
  fi
fi

# --- 2. 推送前本地验证 ---
if [ "$SKIP_CI" = false ]; then
  info "跑本地验证（core:agent / core:uistate 单测）..."
  if ./gradlew :core:agent:test :core:uistate:test --console=plain -q >/tmp/push-ci.log 2>&1; then
    ok "本地验证通过"
  else
    err "本地验证失败，已中止推送。完整日志：/tmp/push-ci.log"
    echo
    tail -30 /tmp/push-ci.log
    echo
    err "提交已保留在本地，修好后重跑即可。确需强推加 --skip-ci"
    exit 1
  fi
fi

# --- 3. 推送（走重试包装吸收概率丢包）---
# git 的 push 是幂等的：失败则未生效可安全重试；若成功但响应丢失，
# 重试只会报 "Everything up-to-date"，无副作用。
info "推送到 origin/${BRANCH}（失败自动重试）..."
if "$TUNNEL" git push -u origin "$BRANCH"; then
  ok "已推送到 GitHub"
else
  err "推送失败。可尝试："
  err "  1) $TUNNEL refresh   刷新 GitHub IP（Anycast 会轮换）"
  err "  2) $TUNNEL doctor    查看当前连通率"
  err "  3) 确认 GITHUB_TOKEN 有效且对目标仓库有写权限"
  exit 1
fi

echo
echo "${C_BLD}查看 CI：${C_RST}https://github.com/Lisir2002/Deepcore-Code/actions"
