#!/bin/sh
# 给 free-llm-bridge 追加 codebuddy 车道
# NAS 主机没装 node，所以用 node 容器来跑 JSON 处理。

set -e
CB=/vol2/1000/Hindsight
STAMP=$(date +%Y%m%d-%H%M%S)

echo "=== 1. 备份 ==="
cp "$CB/docker-compose.yml" "$CB/docker-compose.yml.bak-lane-$STAMP"
echo "  saved: docker-compose.yml.bak-lane-$STAMP"

echo ""
echo "=== 2. 确认 codebuddy-bridge 可达 ==="
docker exec free-llm-bridge node -e "fetch('http://codebuddy-bridge:19001/health').then(r=>r.json()).then(j=>console.log('  REACHABLE',j.service)).catch(e=>console.log('  UNREACHABLE',e.message))"

echo ""
echo "=== 3. 用 node 容器追加车道 ==="
cat > /tmp/add-lane.mjs <<'EOF'
import fs from 'node:fs'
const file = '/work/docker-compose.yml'
let text = fs.readFileSync(file, 'utf8')

const m = text.match(/^(\s*-\s*)'LANES=(\[.*\])'\s*$/m)
if (!m) { console.error('FATAL: LANES line not found'); process.exit(2) }

const lanes = JSON.parse(m[2])
console.log('  existing lanes:', lanes.map(l => l.name).join(', '))

if (lanes.some(l => l.name === 'codebuddy')) {
  console.log('  codebuddy lane already present')
  process.exit(0)
}

lanes.push({
  name: 'codebuddy',
  baseUrl: 'http://codebuddy-bridge:19001/v1',
  model: 'hy3',
  pathStyle: 'openai',
  knownFree: true,
  knownFreeModels: ['hy3'],
  fingerprintTools: false,
  sessionScoped: false,
  strictSchema: false,
  normalizeSchema: false,
  headers: { 'user-agent': 'codebuddy-bridge' },
})

text = text.replace(m[0], m[1] + "'LANES=" + JSON.stringify(lanes) + "'")
fs.writeFileSync(file, text)
console.log('  lanes now:', lanes.map(l => l.name).join(', '))
EOF

docker run --rm -v "$CB:/work" -v /tmp/add-lane.mjs:/add-lane.mjs:ro node:22-alpine node /add-lane.mjs

echo ""
echo "=== 4. compose 语法校验 ==="
cd "$CB"
if docker compose config >/dev/null 2>&1; then
  echo "  compose OK"
else
  echo "  compose INVALID, rolling back"
  cp "$CB/docker-compose.yml.bak-lane-$STAMP" "$CB/docker-compose.yml"
  exit 3
fi

echo ""
echo "=== 5. 重建 free-llm-bridge ==="
docker compose up -d --force-recreate free-llm-bridge 2>&1 | tail -3

sleep 8
echo ""
echo "=== 6. 验证 ==="
docker ps --filter name=free-llm-bridge --format '  {{.Names}}  {{.Status}}'
echo "  --- logs ---"
docker logs free-llm-bridge 2>&1 | tail -15
