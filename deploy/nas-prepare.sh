#!/bin/sh
# 安全的接入方式：给 yishi-gateway 换成"先试 codebuddy、失败回退 free-llm-bridge"的上游。
#
# 为什么不改 free-llm-bridge 的 LANES：
#   hindsight 容器正在用 free-llm-bridge:18999，重建它会导致 hindsight 短暂中断。
#   yishi-gateway 只对外提供公网入口，重建它影响面小得多。
#
# 做法：升级 yishi-gateway.js，让它支持 --upstream 传多个地址（逗号分隔），
#       按顺序尝试，前一个失败就换下一个。

set -e
CB=/vol2/1000/Hindsight
STAMP=$(date +%Y%m%d-%H%M%S)

echo "=== 1. 备份 yishi-gateway.js 和容器配置 ==="
cp "$CB/yishi-gateway.js" "$CB/yishi-gateway.js.bak-before-cb-$STAMP"
echo "  saved: yishi-gateway.js.bak-before-cb-$STAMP"

echo "=== 2. 记录当前容器完整配置（用于重建）==="
docker inspect yishi-gateway > "$CB/yishi-gateway-inspect-before-cb-$STAMP.json"
echo "  saved: yishi-gateway-inspect-before-cb-$STAMP.json"

echo ""
echo "=== 3. 当前 upstream 配置 ==="
docker inspect yishi-gateway --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E 'UPSTREAM|TOKEN' | sed 's/TOKEN=.*/TOKEN=<redacted>/'

echo ""
echo "=== 4. 检查网关是否已支持多上游 ==="
if grep -q "UPSTREAM_LIST\|fallbackUpstreams\|,.*split" "$CB/yishi-gateway.js"; then
  echo "  已支持多上游"
else
  echo "  仅支持单上游，需要升级"
fi
