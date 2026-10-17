#!/bin/sh
# 分层测时：同一 3 块图请求，逐层测，找出 40 秒黑洞在哪
cd /tmp
cat > /tmp/layer-test.mjs <<'EOF'
import fs from 'node:fs'
const tiles = ['/tmp/r_t0.jpg','/tmp/r_t1.jpg','/tmp/r_t2.jpg']
const PROMPT = '你是记忆备忘录解析器。找出图中最早的未来截止日期。只返回 JSON：title, summary, deadline, timezone, eventType, confidence。'
const content = [{ type:'text', text:'请识别这些截图中的事项。' }]
for (const p of tiles) {
  content.push({ type:'image_url', image_url:{ url:'data:image/jpeg;base64,' + fs.readFileSync(p).toString('base64') } })
}
const BODY = JSON.stringify({
  model:'hy3',
  messages:[{role:'system',content:PROMPT},{role:'user',content}],
  max_tokens:1200, temperature:0.1, stream:false, reasoning:{enabled:false},
})
const TOKEN = '<NAS_GATEWAY_KEY>'

async function one(url, label, auth) {
  const t0 = Date.now()
  const headers = { 'content-type':'application/json; charset=utf-8' }
  if (auth) headers.authorization = `Bearer ${TOKEN}`
  try {
    const r = await fetch(url, { method:'POST', headers, body: BODY })
    const text = await r.text()
    const el = Date.now() - t0
    let info = ''
    try { const j = JSON.parse(text); info = `content=${JSON.stringify((j.choices?.[0]?.message?.content||'').slice(0,60))}` } catch { info = text.slice(0,120) }
    console.log(`  [${label}] status=${r.status} ${el}ms  ${info}`)
  } catch (e) {
    console.log(`  [${label}] FAIL ${Date.now()-t0}ms ${e.message}`)
  }
}

console.log('=== 层1: codebuddy-bridge 直连 (19001) ===')
await one('http://codebuddy-bridge:19001/v1/chat/completions', 'bridge')
console.log('=== 层2: free-llm-bridge (18999) ===')
await one('http://free-llm-bridge:18999/v1/chat/completions', 'free-llm')
console.log('=== 层3: yishi-gateway (19000, 带token) ===')
await one('http://yishi-gateway:19000/v1/chat/completions', 'gateway', true)
EOF

# 把图拷进容器可见位置
cp /tmp/r_t0.jpg /tmp/r_t1.jpg /tmp/r_t2.jpg /tmp/ 2>/dev/null
docker run --rm --network hindsight_default \
  -v /tmp/r_t0.jpg:/tmp/r_t0.jpg:ro -v /tmp/r_t1.jpg:/tmp/r_t1.jpg:ro -v /tmp/r_t2.jpg:/tmp/r_t2.jpg:ro \
  -v /tmp/layer-test.mjs:/layer-test.mjs:ro \
  node:22-alpine node /layer-test.mjs
