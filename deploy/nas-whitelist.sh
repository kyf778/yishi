#!/bin/sh
# 把 CodeBuddy 的可用模型加进 codebuddy 车道的白名单。
#
# 背景：bridge 第 806-810 行有一道闸——
#   只有 id 以 "-free" 结尾、或落在车道的 knownFree/knownFreeModels 里的模型才放行。
#   CodeBuddy 的模型 id（hy3 / deepseek-v4.1-flash …）大多不以 -free 结尾，
#   所以必须显式进白名单，否则报
#   `model "xxx" is not a free-lane model`。
#
# 白名单里放哪些：
#   只放**实际免费或有免费额度**的，别把高倍率模型也放进去（会烧积分）。
#   按实测倍率：hy3=x0.00、deepseek-v4.1-flash=x0.11、glm-5.3-flash=x0.06、
#   minimax-m3=x0.25、space-bunny=x0.08、hy3-x=x0.05、deepseek-v4-flash=x0.17。

set -e
CB=/vol2/1000/Hindsight
STAMP=$(date +%Y%m%d-%H%M%S)

echo "=== 1. 备份 ==="
cp "$CB/docker-compose.yml" "$CB/docker-compose.yml.bak-whitelist-$STAMP"
echo "  saved: docker-compose.yml.bak-whitelist-$STAMP"

echo ""
echo "=== 2. 扩展 codebuddy 车道白名单 ==="
cat > /tmp/extend.mjs <<'EOF'
import fs from 'node:fs'
const file = '/work/docker-compose.yml'
let text = fs.readFileSync(file, 'utf8')

const m = text.match(/^(\s*-\s*)'LANES=(\[.*\])'\s*$/m)
if (!m) { console.error('FATAL: LANES not found'); process.exit(2) }

const lanes = JSON.parse(m[2])
const cb = lanes.find(l => l.name === 'codebuddy')
if (!cb) { console.error('FATAL: codebuddy lane not found'); process.exit(2) }

// 按实测倍率挑：x0.00 优先，其余低倍率的也放进来（用户积分够用）
cb.knownFreeModels = [
  'hy3',                    // x0.00 免费
  'hy3-x',                  // x0.05
  'glm-5.3-flash',          // x0.06
  'space-bunny',            // x0.08
  'deepseek-v4.1-flash',    // x0.11
  'deepseek-v4-flash',      // x0.17
  'minimax-m3',             // x0.25
  'hy4-preview',            // x0.29
]
cb.model = 'hy3'            // 默认仍用最便宜的

text = text.replace(m[0], m[1] + "'LANES=" + JSON.stringify(lanes) + "'")
fs.writeFileSync(file, text)
console.log('  whitelist:', cb.knownFreeModels.join(', '))
EOF

docker run --rm -v "$CB:/work" -v /tmp/extend.mjs:/extend.mjs:ro node:22-alpine node /extend.mjs

echo ""
echo "=== 3. compose 校验 ==="
cd "$CB"
if docker compose config >/dev/null 2>&1; then
  echo "  compose OK"
else
  echo "  compose INVALID, rollback"
  cp "$CB/docker-compose.yml.bak-whitelist-$STAMP" "$CB/docker-compose.yml"
  exit 3
fi

echo ""
echo "=== 4. 重建 free-llm-bridge ==="
docker compose up -d --force-recreate free-llm-bridge 2>&1 | tail -3
sleep 8

echo ""
echo "=== 5. 验证白名单生效 ==="
docker exec free-llm-bridge node -e "
const models=['hy3','glm-5.3-flash','deepseek-v4.1-flash','minimax-m3','hy4-preview'];
(async()=>{ for (const m of models) {
  try {
    const r=await fetch('http://127.0.0.1:18999/v1/chat/completions',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({model:m,messages:[{role:'system',content:'s'},{role:'user',content:'hi'}],max_tokens:20,stream:false})});
    const j=await r.json();
    console.log('  '+m+': '+(r.ok?'OK':'ERR '+JSON.stringify(j.error?.message||j.error).slice(0,90)));
  } catch(e){ console.log('  '+m+': FAIL '+e.message) }
}})()
"
