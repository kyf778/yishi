#!/bin/sh
# NAS 上的部署与验证脚本
set -e

echo "=== 1. 清理旧测试容器 ==="
docker rm -f cb-net-test 2>/dev/null || true

echo ""
echo "=== 2. 启动 codebuddy-bridge 到 hindsight_default 网络 ==="
docker run -d \
  --name codebuddy-bridge \
  --network hindsight_default \
  --restart unless-stopped \
  -e HOST=0.0.0.0 \
  -e DEFAULT_MODEL=hy3 \
  -e UPSTREAM_TIMEOUT_MS=300000 \
  -v /vol2/1000/Hindsight/codebuddy-bridge.js:/app/codebuddy-bridge.js:ro \
  -v /vol2/1000/Hindsight/codebuddy-credential.json:/app/credential.json \
  node:22-alpine \
  node /app/codebuddy-bridge.js --port 19001 --credential /app/credential.json

sleep 5
echo ""
echo "=== 3. 容器状态 ==="
docker ps --filter name=codebuddy-bridge --format '{{.Names}}  {{.Status}}'
echo "--- logs ---"
docker logs codebuddy-bridge 2>&1 | tail -8

echo ""
echo "=== 4. 从 free-llm-bridge 容器内部访问 bridge（验证网络连通）==="
docker exec free-llm-bridge node -e "fetch('http://codebuddy-bridge:19001/health').then(r=>r.json()).then(j=>console.log('CONNECT_OK', JSON.stringify(j))).catch(e=>console.log('CONNECT_FAIL', e.message))"

echo ""
echo "=== 5. 从 yishi-gateway 容器内部访问 bridge ==="
docker exec yishi-gateway node -e "fetch('http://codebuddy-bridge:19001/health').then(r=>r.json()).then(j=>console.log('CONNECT_OK', JSON.stringify(j))).catch(e=>console.log('CONNECT_FAIL', e.message))"
