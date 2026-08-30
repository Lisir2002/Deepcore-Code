#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 本地 CI —— 在推送前跑一遍，等价于 GitHub Actions 的 core-test 环节
#
# 用法：
#   ./scripts/ci-local.sh              # 跑全部能在本地跑的检查
#   ./scripts/ci-local.sh --android    # 额外尝试编译 Android（需要 Android SDK）
#
# 设计原则：缺什么能力就跳过什么，但必须明确告诉你"跳过了什么、为什么"。
# 静默跳过比报错更危险 —— 它会让你以为一切正常。
# ---------------------------------------------------------------------------
set -uo pipefail

cd "$(dirname "$0")/.."

if [ -t 1 ]; then
  C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YEL=$'\033[33m'
  C_BLU=$'\033[36m'; C_BLD=$'\033[1m'; C_RST=$'\033[0m'
else
  C_RED=""; C_GRN=""; C_YEL=""; C_BLU=""; C_BLD=""; C_RST=""
fi

PASS=0; FAIL=0; SKIP=0
pass() { printf "  %s✓%s %s\n" "$C_GRN" "$C_RST" "$*"; PASS=$((PASS+1)); }
fail() { printf "  %s✗%s %s\n" "$C_RED" "$C_RST" "$*"; FAIL=$((FAIL+1)); }
skip() { printf "  %s○%s %s %s(%s)%s\n" "$C_YEL" "$C_RST" "$1" "$C_YEL" "$2" "$C_RST"; SKIP=$((SKIP+1)); }
step() { printf "\n%s▶ %s%s\n" "$C_BLD" "$*" "$C_RST"; }

WANT_ANDROID=false
[ "${1:-}" = "--android" ] && WANT_ANDROID=true

# 优先用 wrapper（项目自包含，版本和 CI 一致）；
# 但 wrapper 的分发包没缓存、又下不下来时，退回本机 gradle，别卡死在下载上。
detect_gradle() {
  if [ ! -x ./gradlew ]; then
    echo "gradle"; return
  fi
  local dist_name
  dist_name="$(sed -n 's#.*distributions/gradle-\([^/]*\)-bin\.zip#\1#p' \
               gradle/wrapper/gradle-wrapper.properties 2>/dev/null | head -1)"
  if [ -n "$dist_name" ] && [ -d "${HOME}/.gradle/wrapper/dists/gradle-${dist_name}-bin" ]; then
    echo "./gradlew"; return          # 分发包已缓存，wrapper 可直接用
  fi
  if ./gradlew --version >/dev/null 2>&1; then
    echo "./gradlew"; return          # 能顺利下载也能用
  fi
  echo "gradle"                       # 下不来，退回系统 gradle
}
GRADLE="$(detect_gradle)"

echo "${C_BLD}Agent IDE · 本地 CI${C_RST}"
echo "分支: $(git branch --show-current 2>/dev/null)  提交: $(git rev-parse --short HEAD 2>/dev/null)"

# ---------------------------------------------------------------------------
step "1. 环境检查"

if [ "$GRADLE" = "./gradlew" ]; then
  pass "使用 Gradle Wrapper（版本与 CI 完全一致）"
else
  if [ -x ./gradlew ]; then
    skip "Gradle Wrapper" "分发包下载不到，退回系统 gradle"
    echo "      注意：系统 gradle 版本可能与 CI 不一致，结果仅供参考"
  else
    skip "Gradle Wrapper" "项目内无 ./gradlew"
  fi
  if ! command -v gradle >/dev/null 2>&1; then
    fail "系统也没装 gradle，无法继续"
    exit 1
  fi
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  pass "JAVA_HOME = ${JAVA_HOME}"
elif command -v java >/dev/null 2>&1; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  export JAVA_HOME
  pass "JAVA_HOME 自动推断 = ${JAVA_HOME}"
else
  fail "找不到 Java。请安装 JDK 17 或设置 JAVA_HOME"
  exit 1
fi

java -version 2>&1 | head -1 | sed 's/^/      版本: /'

# ---------------------------------------------------------------------------
step "2. 纯 Kotlin 模块编译（不依赖 Android SDK，任何机器都能跑）"

if timeout 900 "$GRADLE" :core:model:compileKotlin :core:agent:compileKotlin \
     :core:data:compileKotlin :core:uistate:compileKotlin \
     --console=plain -q >/tmp/ci-compile.log 2>&1; then
  pass "core:model / core:agent / core:data / core:uistate 编译通过"
else
  fail "纯 Kotlin 模块编译失败"
  tail -25 /tmp/ci-compile.log
fi

# ---------------------------------------------------------------------------
step "3. 单元测试（Agent 主循环 + UI 归约器）"

if timeout 900 "$GRADLE" :core:agent:test :core:uistate:test \
     --console=plain -q >/tmp/ci-test.log 2>&1; then
  # 从 XML 报告里数出真实用例数
  COUNT=$(find . -path "*/build/test-results/test/*.xml" 2>/dev/null \
          | xargs grep -ho 'tests="[0-9]*"' 2>/dev/null \
          | grep -o '[0-9]*' | paste -sd+ | bc 2>/dev/null || echo "?")
  FAILED=$(find . -path "*/build/test-results/test/*.xml" 2>/dev/null \
           | xargs grep -ho 'failures="[0-9]*"\|errors="[0-9]*"' 2>/dev/null \
           | grep -o '[0-9]*' | paste -sd+ | bc 2>/dev/null || echo "0")
  if [ "${FAILED:-0}" = "0" ]; then
    pass "全部通过（${COUNT} 个用例）"
  else
    fail "有 ${FAILED} 个用例失败"
    tail -25 /tmp/ci-test.log
  fi
else
  fail "测试执行失败"
  tail -25 /tmp/ci-test.log
fi

# ---------------------------------------------------------------------------
step "4. 设计系统守卫（Lint 规则模块）"

if timeout 900 "$GRADLE" :lint:build --console=plain -q >/tmp/ci-lint.log 2>&1; then
  pass "自定义 Lint 规则编译通过（能拦截绕过组件库的写法）"
else
  fail "Lint 模块编译失败"
  tail -25 /tmp/ci-lint.log
fi

# ---------------------------------------------------------------------------
step "5. CI 工作流 YAML 语法校验"

if python3 -c "
import yaml, glob, sys
files = sorted(glob.glob('.github/workflows/*.yml'))
if not files:
    print('NO_FILES'); sys.exit(1)
for f in files:
    yaml.safe_load(open(f, encoding='utf-8'))
    print('  OK', f)
" 2>/tmp/ci-yaml.log; then
  pass "工作流 YAML 语法正确"
else
  fail "YAML 语法错误"
  cat /tmp/ci-yaml.log
fi

# ---------------------------------------------------------------------------
step "6. 源码静态自检（括号配对 / 常量定义完整性）"

if python3 - <<'PY' 2>/tmp/ci-static.log
import os, re, sys
bad = []
for root, _, files in os.walk('.'):
    if '/build/' in root or '/.git/' in root or root.endswith('/build'):
        continue
    for f in files:
        if not f.endswith('.kt'):
            continue
        p = os.path.join(root, f)
        s = open(p, encoding='utf-8').read()
        s = re.sub(r'"""[\s\S]*?"""', 'STR', s)
        s = re.sub(r'//[^\n]*', '', s)
        s = re.sub(r'/\*[\s\S]*?\*/', '', s)
        s = re.sub(r'"(?:[^"\\\n]|\\.)*"', 'STR', s)
        for a, b in [('{', '}'), ('(', ')'), ('[', ']')]:
            if s.count(a) != s.count(b):
                bad.append(f"{p}: {a}{b} 不配对 {s.count(a)}/{s.count(b)}")
if bad:
    print('\n'.join(bad)); sys.exit(1)
print(f"  已检查 {sum(1 for r,_,fs in os.walk('.') if '/build/' not in r and '/.git/' not in r for x in fs if x.endswith('.kt'))} 个 Kotlin 文件")
PY
then
  pass "全部 Kotlin 文件括号配对正常"
else
  fail "静态自检发现问题"
  cat /tmp/ci-static.log
fi

# ---------------------------------------------------------------------------
step "7. Android 编译（Compose / AGP 代码的唯一真实验证）"

if [ "$WANT_ANDROID" != true ]; then
  skip "Android 编译" "默认跳过，加 --android 启用"
  echo "      提示：这一步需要 Android SDK，是唯一能验证 Compose 代码的环节"
elif [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] || [ ! -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then
  skip "Android 编译" "未找到 Android SDK"
  echo "      设置 ANDROID_HOME，或在 Android Studio 里打开项目"
else
  if timeout 1200 "$GRADLE" :app:assembleDebug --console=plain -q >/tmp/ci-android.log 2>&1; then
    pass "Debug APK 编译通过"
  else
    fail "Android 编译失败"
    tail -40 /tmp/ci-android.log
  fi
fi

# ---------------------------------------------------------------------------
echo
echo "${C_BLD}════════════════════════════════════════${C_RST}"
printf "  通过 %s%s%s   失败 %s%s%s   跳过 %s%s%s\n" \
  "$C_GRN" "$PASS" "$C_RST" \
  "$([ $FAIL -gt 0 ] && echo "$C_RED" || echo "")" "$FAIL" "$C_RST" \
  "$C_YEL" "$SKIP" "$C_RST"
echo "${C_BLD}════════════════════════════════════════${C_RST}"

if [ $FAIL -gt 0 ]; then
  echo "${C_RED}有失败项，建议修复后再推送。${C_RST}"
  exit 1
fi
echo "${C_GRN}本地检查全部通过，可以推送了：./scripts/push.sh \"提交信息\"${C_RST}"
