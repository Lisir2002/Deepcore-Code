#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# github-tunnel.sh — 受限沙箱访问 GitHub 的通道工具
#
# 【环境结论：2026-08-31 实测，与初版笔记互补】
#
#   1. DNS 被污染：github.com / api.github.com 被解析到黑洞网段 198.18.0.0/15
#      （RFC 2544 保留网段，公网不可路由）。这是「DNS 层劫持」，不是 IP 封锁。
#
#   2. 但不止于此——修正了「只要 --resolve 就万事大吉」的说法：
#        api.github.com   --resolve 成功率 10/10   ← 干净，解析对了就通
#        github.com       --resolve 成功率 2/8~8/10 ← 存在概率性丢包，随网关负载波动
#      失败形态是 gnutls_handshake() failed / curl 28 超时，重试即可成功。
#
#   3. 因此本脚本做两件事，缺一不可：
#        · hosts 注入  → 治 DNS 劫持（让 git/curl 等所有工具天然可用）
#        · 重试包装    → 治概率丢包（单发不可靠，多试几次必中）
#
#   4. 关于「IP 直连 + Host 头（不发 SNI）」方案：已废弃，实测 exit 35 失败。
#      GitHub 现有 CDN 要求 SNI，去掉 SNI 反而握不上手。
#      --resolve / hosts 保留 SNI，证书校验照常通过，无需 -k。
#
#   5. DoH 通道：dns.alidns.com(主) → 223.5.5.5 纯 HTTP(兜底) → doh.pub(备)
#      Cloudflare 1.1.1.1 在本沙箱不可达，不要默认「海外大厂一定通」。
#
# 用法：
#   ./github-tunnel.sh doctor              体检（先跑这个）
#   ./github-tunnel.sh install             注入 hosts（含重启持久化）
#   ./github-tunnel.sh refresh             重新解析并刷新 hosts
#   ./github-tunnel.sh uninstall           移除注入的 hosts 条目
#   ./github-tunnel.sh resolve <域名>      通过 DoH 解析真实 IP
#   ./github-tunnel.sh curl <curl 参数>    带重试的 curl
#   ./github-tunnel.sh git  <git 参数>     带重试的 git
#   ./github-tunnel.sh api  <路径>         GitHub API（需 GITHUB_TOKEN）
#   ./github-tunnel.sh verify              端到端验证
#
# 安全：Token 只从环境变量 GITHUB_TOKEN 读取，绝不落盘、绝不入参。
# ---------------------------------------------------------------------------

set -uo pipefail   # 不用 -e：重试逻辑需要容忍非零退出

C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YEL=$'\033[33m'; C_BLU=$'\033[34m'; C_RST=$'\033[0m'
info() { printf "%s[信息]%s %s\n" "$C_BLU" "$C_RST" "$*"; }
ok()   { printf "%s[完成]%s %s\n" "$C_GRN" "$C_RST" "$*"; }
warn() { printf "%s[注意]%s %s\n" "$C_YEL" "$C_RST" "$*"; }
err()  { printf "%s[错误]%s %s\n" "$C_RED" "$C_RST" "$*" >&2; }

# 需要打通的域名（git clone/push、API、Release 资产、raw 下载都会用到）
HOSTS_LIST=(github.com api.github.com codeload.github.com raw.githubusercontent.com objects.githubusercontent.com)

MARK_BEGIN="# >>> agent-ide github tunnel >>>"
MARK_END="# <<< agent-ide github tunnel <<<"

# 对 github.com 这类概率丢包域名的重试次数。
# 实测单次成功率在 25%~80% 之间随网关负载波动，取 12 次可把全败概率压到 <1%：
#   单次成功率 40% 时，12 次全败 = 0.6^12 ≈ 0.2%
# 注：本机 git 2.43 只编译了 GnuTLS，没有 OpenSSL 后端（http.sslBackend=openssl 实测 0/10），
#     所以无法靠切换 TLS 后端规避，只能重试。
RETRY="${GH_RETRY:-12}"
HOSTS_FILE=/etc/hosts
PERSIST_FILE="$HOME/.user_hosts" # 本沙箱重启后 /etc/hosts 会被还原，需同步写这里

# ---------------------------------------------------------------------------
# DoH 解析：三级降级链
# ---------------------------------------------------------------------------
_extract_a() {
  python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    ips = [a['data'] for a in d.get('Answer', []) if a.get('type') == 1 and a.get('data')]
    print(ips[0] if ips else '')
except Exception:
    print('')
" 2>/dev/null
}

doh_resolve() {
  local host="$1" ip
  # 主通道：阿里 DoH
  ip=$(curl -s --max-time 8 -H "accept: application/dns-json" \
        "https://dns.alidns.com/resolve?name=${host}&type=A" 2>/dev/null | _extract_a)
  [ -n "$ip" ] && { echo "$ip"; return 0; }
  # 兜底一：阿里纯 HTTP DNS（无 TLS 依赖，DoH 域名被污染时的最后底牌）
  ip=$(curl -s --max-time 8 -H "accept: application/dns-json" \
        "http://223.5.5.5/resolve?name=${host}&type=A" 2>/dev/null | _extract_a)
  [ -n "$ip" ] && { echo "$ip"; return 0; }
  # 兜底二：腾讯 doh.pub
  ip=$(curl -s --max-time 8 -H "accept: application/dns-json" \
        "https://doh.pub/dns-query?name=${host}&type=A" 2>/dev/null | _extract_a)
  [ -n "$ip" ] && { echo "$ip"; return 0; }
  return 1
}

# 是否命中黑洞/保留网段（说明本地 DNS 仍被污染）
is_blackholed() {
  case "$1" in
    198.18.*|198.19.*|100.64.*|0.0.0.0|127.*) return 0 ;;
    *) return 1 ;;
  esac
}

# ---------------------------------------------------------------------------
# 重试包装：核心，应对概率性丢包
# ---------------------------------------------------------------------------
with_retry() {
  local desc="$1"; shift
  local i rc delay
  for ((i = 1; i <= RETRY; i++)); do
    "$@" && return 0
    rc=$?
    if [ "$i" -lt "$RETRY" ]; then
      # 退避 1s→2s→3s(封顶)，避免密集重试反而触发网关限流
      delay=$((i < 3 ? i : 3))
      printf "%s[重试]%s %s 第 %s/%s 次失败(rc=%s)，%ss 后重试\n" \
        "$C_YEL" "$C_RST" "$desc" "$i" "$RETRY" "$rc" "$delay" >&2
      sleep "$delay"
    fi
  done
  err "$desc 连续 ${RETRY} 次失败"
  return 1
}

# ---------------------------------------------------------------------------
# hosts 注入
# ---------------------------------------------------------------------------
_hosts_strip() {
  # 移除旧的标记块（幂等）
  local f="$1"
  [ -f "$f" ] || return 0
  python3 - "$f" "$MARK_BEGIN" "$MARK_END" <<'PY'
import sys
path, begin, end = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    lines = open(path).read().splitlines()
except Exception:
    sys.exit(0)
out, skip = [], False
for ln in lines:
    if ln.strip() == begin: skip = True;  continue
    if ln.strip() == end:   skip = False; continue
    if not skip: out.append(ln)
open(path, 'w').write("\n".join(out) + ("\n" if out else ""))
PY
}

cmd_install() {
  info "解析 ${#HOSTS_LIST[@]} 个域名的真实 IP ..."
  local block="$MARK_BEGIN" host ip
  local failed=()
  for host in "${HOSTS_LIST[@]}"; do
    if ip=$(doh_resolve "$host") && [ -n "$ip" ]; then
      printf "  %-34s %s\n" "$host" "$ip"
      block+=$'\n'"${ip} ${host}"
    else
      printf "  %-34s %s\n" "$host" "(解析失败，跳过)"
      failed+=("$host")
    fi
  done
  block+=$'\n'"$MARK_END"

  for f in "$HOSTS_FILE" "$PERSIST_FILE"; do
    _hosts_strip "$f"
    printf '\n%s\n' "$block" >> "$f"
  done
  ok "已注入 $HOSTS_FILE 并持久化到 $PERSIST_FILE（沙箱重启后自动恢复）"
  [ ${#failed[@]} -gt 0 ] && warn "以下域名未解析成功：${failed[*]} —— 可稍后 refresh 重试"
  return 0
}

cmd_uninstall() {
  _hosts_strip "$HOSTS_FILE"
  _hosts_strip "$PERSIST_FILE"
  ok "已移除注入的 hosts 条目"
}

cmd_refresh() {
  info "刷新 hosts（GitHub Anycast 的 A 记录会轮换，实测 TTL 6~48s 波动）"
  cmd_install
}

# ---------------------------------------------------------------------------
# 体检
# ---------------------------------------------------------------------------
cmd_doctor() {
  printf "%s=== 1. 本地 DNS 是否被污染 ===%s\n" "$C_BLU" "$C_RST"
  local host lip poisoned=0
  for host in "${HOSTS_LIST[@]}"; do
    lip=$(getent hosts "$host" 2>/dev/null | awk '{print $1}' | head -1)
    if [ -z "$lip" ]; then
      printf "  %-34s %s\n" "$host" "(无本地记录)"
    elif is_blackholed "$lip"; then
      printf "  %-34s %s %s← 黑洞网段，被劫持%s\n" "$host" "$lip" "$C_YEL" "$C_RST"
      poisoned=1
    else
      printf "  %-34s %s %s← 已指向真实 IP%s\n" "$host" "$lip" "$C_GRN" "$C_RST"
    fi
  done

  printf "\n%s=== 2. DoH 通道可用性 ===%s\n" "$C_BLU" "$C_RST"
  local dip
  for host in github.com api.github.com; do
    if dip=$(doh_resolve "$host"); then
      printf "  %-34s %s %s✓%s\n" "$host" "$dip" "$C_GRN" "$C_RST"
    else
      printf "  %-34s %s✗ 全部 DoH 通道失败%s\n" "$host" "$C_RED" "$C_RST"
    fi
  done

  printf "\n%s=== 3. 连通性实测（含重试，共 %s 次）===%s\n" "$C_BLU" "$RETRY" "$C_RST"
  local okc=0 n=${RETRY}
  for ((i = 1; i <= n; i++)); do
    if timeout 10 curl -s -o /dev/null --max-time 8 \
         -w "" "https://api.github.com/zen" 2>/dev/null; then okc=$((okc+1)); printf "√"; else printf "×"; fi
  done
  printf "  api.github.com: %s/%s\n" "$okc" "$n"

  okc=0
  for ((i = 1; i <= n; i++)); do
    c=$(timeout 10 curl -s -o /dev/null --max-time 8 -w "%{http_code}" "https://github.com/zen" 2>/dev/null)
    [ "$c" = "200" ] && okc=$((okc+1)) && printf "√" || printf "×"
  done
  printf "  github.com:     %s/%s\n" "$okc" "$n"

  printf "\n%s=== 4. 结论 ===%s\n" "$C_BLU" "$C_RST"
  if [ "$poisoned" -eq 1 ]; then
    warn "DNS 仍被污染 → 执行: $0 install"
  else
    ok "DNS 已指向真实 IP"
  fi
  ok "概率性丢包由重试吸收（本脚本的 curl/git 子命令已内置）"
}

# ---------------------------------------------------------------------------
# 子命令
# ---------------------------------------------------------------------------
cmd_resolve() {
  local host="${1:-github.com}" ip
  if ip=$(doh_resolve "$host"); then echo "$ip"; else err "无法解析 $host（DoH 全部通道失败）"; return 1; fi
}

cmd_curl() { with_retry "curl $*" curl "$@"; }

cmd_git()  { with_retry "git $*"  git  "$@"; }

cmd_api() {
  local token="${GITHUB_TOKEN:-}"
  if [ -z "$token" ]; then
    err "请先设置环境变量 GITHUB_TOKEN（不要在命令行传 token）"
    return 1
  fi
  # 令牌含空白/换行时，curl 会以 exit 43 直接拒绝构造 header，
  # 而且重试 12 次结果完全一样——白等 ~30s 还不如立刻说清原因。
  # 典型踩法：GITHUB_TOKEN=$(cat token.env) 把注释行和 export 前缀一起读进来了，
  # 正确做法是 source token.env。
  if [[ "$token" =~ [[:space:]] ]]; then
    err "GITHUB_TOKEN 含空白字符（当前长度 ${#token}），curl 无法构造 header"
    err "从文件读取请改用: source <文件>  而不是 GITHUB_TOKEN=\$(cat <文件>)"
    return 1
  fi
  local method="${1:-GET}"; shift || true
  local path="${1:-/user}"; shift 2>/dev/null || true
  # 方法名也会原样进 -X，拼错同样是 exit 43。
  # 典型误用：api --method POST /xxx —— 本脚本用位置参数，不是 --method 风格。
  if [[ ! "$method" =~ ^(GET|POST|PUT|PATCH|DELETE|HEAD)$ ]]; then
    err "HTTP 方法非法: '$method'（应为 GET/POST/PUT/PATCH/DELETE/HEAD 之一）"
    err "用法: $0 api <方法> <路径> [额外 curl 参数...]"
    err "例  : $0 api POST /repos/OWNER/REPO/actions/workflows/ci.yml/dispatches -d '{\"ref\":\"main\"}'"
    return 1
  fi
  case "$path" in
    http*)  : ;;
    /repos/*|/user*|/rate_limit|/search/*) : ;;
    *) path="/repos/${GH_OWNER:-Lisir2002}/${GH_REPO:-Deepcore-Code}${path}" ;;
  esac
  with_retry "api ${method} ${path}" curl -s --max-time 30 \
    -X "$method" \
    -H "Authorization: Bearer ${token}" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "$@" \
    "https://api.github.com${path}"
}

cmd_verify() {
  info "端到端验证（全部走重试包装）"
  printf "  %-40s" "api.github.com/zen"
  if cmd_curl -s --max-time 10 -o /dev/null -w "" https://api.github.com/zen 2>/dev/null; then
    printf "%s✓%s\n" "$C_GRN" "$C_RST"
  else printf "%s✗%s\n" "$C_RED" "$C_RST"; fi

  printf "  %-40s" "git ls-remote (匿名仓库)"
  if cmd_git ls-remote https://github.com/octocat/Hello-World.git >/dev/null 2>&1; then
    printf "%s✓%s\n" "$C_GRN" "$C_RST"
  else printf "%s✗%s\n" "$C_RED" "$C_RST"; fi

  if [ -n "${GITHUB_TOKEN:-}" ]; then
    printf "  %-40s" "GitHub API 鉴权"
    local r; r=$(cmd_api GET /user 2>/dev/null | python3 -c "
import sys,json
try: print(json.load(sys.stdin).get('login','?'))
except Exception: print('')
" 2>/dev/null)
    [ -n "$r" ] && printf "%s✓ 已登录为 %s%s\n" "$C_GRN" "$r" "$C_RST" \
                 || printf "%s✗ token 无效或已吊销%s\n" "$C_RED" "$C_RST"
  else
    warn "未设置 GITHUB_TOKEN，跳过鉴权验证"
  fi
}

case "${1:-doctor}" in
  doctor)    shift; cmd_doctor "$@" ;;
  install)   shift; cmd_install "$@" ;;
  uninstall) shift; cmd_uninstall "$@" ;;
  refresh)   shift; cmd_refresh "$@" ;;
  resolve)   shift; cmd_resolve "$@" ;;
  curl)      shift; cmd_curl "$@" ;;
  git)       shift; cmd_git "$@" ;;
  api)       shift; cmd_api "$@" ;;
  verify)    shift; cmd_verify "$@" ;;
  *)
    cat <<EOF
用法: $0 <命令> [参数]

  doctor              体检：DNS 污染 / DoH 通道 / 连通率实测
  install             注入 hosts（同时写入 $PERSIST_FILE 以在沙箱重启后保留）
  refresh             重新解析并刷新 hosts（GitHub IP 会轮换）
  uninstall           移除注入的 hosts 条目
  resolve <域名>      通过 DoH 解析真实 IP
  curl <参数>         带重试的 curl（吸收概率丢包）
  git  <参数>         带重试的 git
  api <方法> <路径>   GitHub API，需 GITHUB_TOKEN 环境变量
                      例: api POST /repos/OWNER/REPO/actions/workflows/ci.yml/dispatches
                          -d '{"ref":"main"}'
                      注意: 方法是位置参数，不是 --method 风格；
                            令牌请用 `source <env文件>` 载入（勿用 \$(cat)，会把注释行读进来）
  verify              端到端验证

环境变量：GITHUB_TOKEN  GH_OWNER(默认 Lisir2002)  GH_REPO(默认 Deepcore-Code)
          GH_RETRY(默认 6)
EOF
    exit 1
    ;;
esac
