#!/bin/sh
# 探测 CodeBuddy 上游是否接受 reasoning 字段 + 关掉思考是否真的变快
cat > /tmp/probe-reasoning.mjs <<'EOF'
import fs from 'node:fs'
const cred = JSON.parse(fs.readFileSync('/cred.json', 'utf8'))
const H = {
  'content-type': 'application/json',
  accept: 'text/event-stream',
  authorization: `Bearer ${cred.access_token}`,
  'x-domain': 'www.codebuddy.cn',
  'x-product': 'SaaS',
  'x-product-code': 'codebuddy',
  'x-conversation-id': 'a'.repeat(32),
  'x-conversation-request-id': 'a'.repeat(32),
  'x-conversation-message-id': 'a'.repeat(32),
  'x-ide-name': 'CodeBuddy',
  'x-ide-type': 'CodeBuddy',
  'x-ide-version': '1.106.1',
  'x-agent-purpose': 'conversation',
  'user-agent': 'CodeBuddyIDE/1.106.1',
}
async function one(label, extra) {
  const body = {
    model: 'hy3',
    messages: [
      { role: 'system', content: '你是简洁助手' },
      { role: 'user', content: '只回答两个字：收到' },
    ],
    stream: true,
    temperature: 0.1,
    max_tokens: 300,
    ...extra,
  }
  const t0 = Date.now()
  try {
    const r = await fetch('https://www.codebuddy.cn/v2/chat/completions', {
      method: 'POST', headers: H, body: JSON.stringify(body),
    })
    const raw = await r.text()
    const el = Date.now() - t0
    if (r.status !== 200) { console.log(`  [${label}] status=${r.status} ${el}ms  ${raw.slice(0,200)}`); return }
    let content = '', reasoning = '', usage = null
    for (const line of raw.split('\n')) {
      const t = line.trim()
      if (!t.startsWith('data:')) continue
      const p = t.slice(5).trim()
      if (p === '[DONE]') break
      let d; try { d = JSON.parse(p) } catch { continue }
      if (d.usage) usage = d.usage
      const delta = (d.choices || [])[0]?.delta || {}
      if (typeof delta.content === 'string') content += delta.content
      if (typeof delta.reasoning_content === 'string') reasoning += delta.reasoning_content
    }
    console.log(`  [${label}] status=200 ${el}ms  content=${JSON.stringify(content)} reasoning_len=${reasoning.length} usage=${JSON.stringify(usage)}`)
  } catch (e) {
    console.log(`  [${label}] FAIL ${Date.now()-t0}ms ${e.message}`)
  }
}
console.log('=== 1. 带 reasoning:{enabled:false} ===')
await one('off ', { reasoning: { enabled: false } })
console.log('=== 2. 不带 reasoning 字段 ===')
await one('none', {})
console.log('=== 3. 再测一次带 off（看稳定性）===')
await one('off2', { reasoning: { enabled: false } })
EOF
docker run --rm \
  -v /vol2/1000/Hindsight/codebuddy-credential.json:/cred.json:ro \
  -v /tmp/probe-reasoning.mjs:/probe.mjs:ro \
  node:22-alpine node /probe.mjs
