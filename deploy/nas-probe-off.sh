#!/bin/sh
# 探测哪种字段能真正关掉 CodeBuddy 的思考（看 reasoning_tokens）
cat > /tmp/probe-off.mjs <<'EOF'
import fs from 'node:fs'
const cred = JSON.parse(fs.readFileSync('/cred.json', 'utf8'))
const H = {
  'content-type': 'application/json', accept: 'text/event-stream',
  authorization: `Bearer ${cred.access_token}`,
  'x-domain': 'www.codebuddy.cn', 'x-product': 'SaaS', 'x-product-code': 'codebuddy',
  'x-conversation-id': 'b'.repeat(32), 'x-conversation-request-id': 'b'.repeat(32),
  'x-conversation-message-id': 'b'.repeat(32),
  'x-ide-name': 'CodeBuddy', 'x-ide-type': 'CodeBuddy', 'x-ide-version': '1.106.1',
  'x-agent-purpose': 'conversation', 'user-agent': 'CodeBuddyIDE/1.106.1',
}
async function one(label, extra) {
  const body = {
    model: 'hy3',
    messages: [{ role: 'system', content: '你是简洁助手' }, { role: 'user', content: '1+1等于几？只回答数字' }],
    stream: true, temperature: 0.1, max_tokens: 400, ...extra,
  }
  const t0 = Date.now()
  try {
    const r = await fetch('https://www.codebuddy.cn/v2/chat/completions', { method: 'POST', headers: H, body: JSON.stringify(body) })
    const raw = await r.text()
    if (r.status !== 200) { console.log(`  [${label}] status=${r.status} ${Date.now()-t0}ms ${raw.slice(0,150)}`); return }
    let content = '', usage = null
    for (const line of raw.split('\n')) {
      const t = line.trim(); if (!t.startsWith('data:')) continue
      const p = t.slice(5).trim(); if (p === '[DONE]') break
      let d; try { d = JSON.parse(p) } catch { continue }
      if (d.usage) usage = d.usage
      content += (d.choices || [])[0]?.delta?.content || ''
    }
    const rt = usage?.completion_tokens_details?.reasoning_tokens
    console.log(`  [${label}] ${Date.now()-t0}ms  reasoning_tok=${rt}  compl=${usage?.completion_tokens}  content=${JSON.stringify(content.slice(0,40))}`)
  } catch (e) { console.log(`  [${label}] FAIL ${e.message}`) }
}
await one('A reasoning.enabled=false', { reasoning: { enabled: false } })
await one('B thinking.type=disabled ', { thinking: { type: 'disabled' } })
await one('C enable_thinking=false  ', { enable_thinking: false })
await one('D extra_body.enable=false', { extra_body: { enable_thinking: false } })
await one('E reasoning.effort=none  ', { reasoning: { effort: 'none' } })
await one('F reasoning.effort=low   ', { reasoning: { effort: 'low' } })
await one('G chat_template_kwargs   ', { chat_template_kwargs: { enable_thinking: false } })
await one('H 对照(无字段)          ', {})
EOF
docker run --rm \
  -v /vol2/1000/Hindsight/codebuddy-credential.json:/cred.json:ro \
  -v /tmp/probe-off.mjs:/probe.mjs:ro \
  node:22-alpine node /probe.mjs
