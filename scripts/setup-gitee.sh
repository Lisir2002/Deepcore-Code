#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 配置 Gitee 中转通道
#
# 作用：让 git 对 github.com 的所有操作（push/pull/fetch）自动重定向到 Gitee，
#       命令行习惯完全不用改 —— 你照常 `git push origin main`。
#
# 原理：git config url.<gitee>.insteadOf <github>
#       Git 会在发起网络请求前做地址替换，对上层命令完全透明。
#
# 用法：
#   ./scripts/setup-gitee.sh <你的Gitee用户名>
#   ./scripts/setup-gitee.sh <你的Gitee用户名> --https   # 用令牌而非 SSH
# ---------------------------------------------------------------------------
set -euo pipefail

GITHUB_REPO="Lisir2002/Deepcore-Code"
GITHUB_URL="https://github.com/${GITHUB_REPO}.git"
REPO_NAME="Deepcore-Code"

# 颜色输出
if [ -t 1 ]; then
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YEL=$'\033[33m'
  C_BLU=$'\033[36m'; C_BLD=$'\033[1m'; C_RST=$'\033[0m'
else
  C_RED=""; C_GRN=""; C_YEL=""; C_BLU=""; C_BLD=""; C_RST=""
fi

info()  { printf "%s[信息]%s %s\n" "$C_BLU" "$C_RST" "$*"; }
ok()    { printf "%s[完成]%s %s\n" "$C_GRN" "$C_RST" "$*"; }
warn()  { printf "%s[注意]%s %s\n" "$C_YEL" "$C_RST" "$*"; }
err()   { printf "%s[错误]%s %s\n" "$C_RED" "$C_RST" "$*" >&2; }
title() { printf "\n%s=== %s ===%s\n" "$C_BLD" "$*" "$C_RST"; }

# ---------------------------------------------------------------------------
title "配置 Gitee 中转通道"

if [ $# -lt 1 ]; then
  err "缺少 Gitee 用户名"
  echo "用法: $0 <你的Gitee用户名> [--https]"
  exit 1
fi

GITEE_USER="$1"
AUTH_MODE="${2:-ssh}"

if [ "$AUTH_MODE" = "--https" ]; then
  printf "请输入 Gitee 私人令牌（输入不回显）: "
  read -rs GITEE_TOKEN; echo
  [ -z "${GITEE_TOKEN:-}" ] && { err "令牌不能为空"; exit 1; }
  GITEE_URL="https://${GITEE_USER}:${GITEE_TOKEN}@gitee.com/${GITEE_USER}/${REPO_NAME}.git"
else
  GITEE_URL="git@gitee.com:${GITEE_USER}/${REPO_NAME}.git"
fi

info "Gitee 仓库 : ${GITEE_USER}/${REPO_NAME}"
info "认证方式   : $([ "$AUTH_MODE" = '--https' ] && echo 'HTTPS 令牌' || echo 'SSH 密钥')"

# ---------------------------------------------------------------------------
title "第 1 步 / 共 3 步：准备认证凭据"

if [ "$AUTH_MODE" = "--https" ]; then
  ok "使用令牌认证，跳过密钥生成"
else
  KEY="${HOME}/.ssh/id_ed25519_gitee"
  if [ ! -f "$KEY" ]; then
    info "生成 SSH 密钥对..."
    mkdir -p "${HOME}/.ssh" && chmod 700 "${HOME}/.ssh"
    ssh-keygen -t ed25519 -C "agent-ide@deepcore" -f "$KEY" -N "" -q
    ok "密钥已生成"
  else
    ok "复用已有密钥 ${KEY}"
  fi

  echo
  echo "${C_BLD}请把下面这段公钥添加到 Gitee → 设置 → SSH 公钥：${C_RST}"
  echo "${C_YEL}────────────────────────────────────────────────────────${C_RST}"
  cat "${KEY}.pub"
  echo "${C_YEL}────────────────────────────────────────────────────────${C_RST}"
  echo
  printf "添加完成后按 ${C_BLD}回车${C_RST} 继续，或按 Ctrl-C 退出... "
  read -r _
fi

# ---------------------------------------------------------------------------
title "第 2 步 / 共 3 步：配置地址重定向"

if [ "$AUTH_MODE" != "--https" ]; then
  # 让 Gitee 走专用密钥，避免和本机其他 SSH 配置打架
  if ! grep -q "Host gitee.com" "${HOME}/.ssh/config" 2>/dev/null; then
    mkdir -p "${HOME}/.ssh"
    cat >> "${HOME}/.ssh/config" <<EOF

Host gitee.com
    HostName gitee.com
    User git
    IdentityFile ${HOME}/.ssh/id_ed25519_gitee
    IdentitiesOnly yes
    StrictHostKeyChecking accept-new
EOF
    chmod 600 "${HOME}/.ssh/config"
    ok "SSH 配置已写入 ~/.ssh/config"
  else
    ok "SSH 配置已存在"
  fi
fi

# 核心：把 github.com 地址透明替换成 gitee.com
git config --unset-all "url.${GITEE_URL}.insteadOf" 2>/dev/null || true
git config --add "url.${GITEE_URL}.insteadOf" "${GITHUB_URL}"
git config --add "url.${GITEE_URL}.insteadOf" "git@github.com:${GITHUB_REPO}.git"
git config --add "url.${GITEE_URL}.insteadOf" "https://github.com/${GITHUB_REPO}"

ok "已配置重定向"
info "  ${GITHUB_URL}"
info "        ↓"
info "  ${GITEE_URL/\/\/*@/\/**@}"

# ---------------------------------------------------------------------------
title "第 3 步 / 共 3 步：连通性测试"

info "正在测试 Gitee 连接（最长 20 秒）..."
if [ "$AUTH_MODE" = "--https" ]; then
  if GIT_TERMINAL_PROMPT=0 git ls-remote --exit-code "$GITEE_URL" >/dev/null 2>&1; then
    ok "连接成功：令牌有效，仓库可访问"
  else
    err "连接失败。请检查："
    echo "    1. Gitee 上是否已创建仓库 ${GITEE_USER}/${REPO_NAME}"
    echo "    2. 令牌是否具备 projects 权限"
    echo "    3. 用户名是否正确"
    exit 1
  fi
else
  if GIT_SSH_COMMAND="ssh -o ConnectTimeout=15 -o BatchMode=yes" \
     git ls-remote --exit-code "$GITEE_URL" >/dev/null 2>&1; then
    ok "连接成功：SSH 认证通过"
  else
    err "SSH 连接失败。请确认公钥已添加到 Gitee。"
    echo "    手动验证：ssh -T git@gitee.com"
    exit 1
  fi
fi

# ---------------------------------------------------------------------------
title "配置完成"

cat <<EOF

${C_BLD}现在你可以照常用 git，命令一个字都不用改：${C_RST}

    git add -A
    git commit -m "你的提交信息"
    git push origin main      ${C_GRN}# 实际推送到 Gitee，Gitee 再镜像到 GitHub${C_RST}

${C_BLD}还剩最后一步（只需做一次）：${C_RST}
在 Gitee 仓库页面 → 管理 → 仓库镜像管理 → 添加镜像
    - 镜像方向：${C_YEL}Push${C_RST}（Gitee → GitHub）
    - 镜像仓库：${GITHUB_REPO}
    - 个人令牌：你的 GitHub PAT（须含 repo 权限）

配好之后，GitHub 每次收到镜像都会自动触发 Actions 跑 CI。
镜像有最短 5 分钟间隔；急着看结果可以去镜像管理页点「更新」手动触发。

${C_YEL}安全提示：你之前在对话里贴过 GitHub 令牌，建议立即去
GitHub → Settings → Developer settings → Personal access tokens
把这个令牌 revoke 掉，重新生成一个。${C_RST}
EOF
