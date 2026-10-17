#!/bin/sh
# 端到端验证：公网入口 -> yishi-gateway -> free-llm-bridge -> codebuddy-bridge -> CodeBuddy

set -e
TOKEN=<NAS_GATEWAY_KEY>

echo "=== 1. free-llm-bridge 里的模型清单（看 codebuddy 是否在列）==="
docker exec free-llm-bridge node -e "
fetch('http://127.0.0.1:18999/v1/models')
  .then(r=>r.json())
  .then(j=>{ const ids=j.data.map(m=>m.id); console.log('  模型数:', ids.length); console.log('  含 hy3:', ids.includes('hy3')); console.log('  前 20:', ids.slice(0,20).join(', ')) })
  .catch(e=>console.log('  FAIL', e.message))
"

echo ""
echo "=== 2. 直接问 free-llm-bridge（点名 hy3）==="
docker exec free-llm-bridge node -e "
fetch('http://127.0.0.1:18999/v1/chat/completions', {
  method:'POST', headers:{'content-type':'application/json'},
  body: JSON.stringify({model:'hy3', messages:[{role:'system',content:'你是简洁助手'},{role:'user',content:'只回答两个字：收到'}], max_tokens:300, stream:false})
}).then(r=>r.json()).then(j=>{
  console.log('  content:', JSON.stringify(j.choices?.[0]?.message?.content));
  console.log('  usage:', JSON.stringify(j.usage));
}).catch(e=>console.log('  FAIL', e.message))
"

echo ""
echo "=== 3. 走公网入口（yishi-gateway:19000）==="
docker exec yishi-gateway node -e "
fetch('http://127.0.0.1:19000/v1/chat/completions', {
  method:'POST',
  headers:{'content-type':'application/json','authorization':'Bearer $TOKEN'},
  body: JSON.stringify({model:'hy3', messages:[{role:'system',content:'你是简洁助手'},{role:'user',content:'只回答两个字：收到'}], max_tokens:300, stream:false})
}).then(r=>r.json()).then(j=>{
  console.log('  content:', JSON.stringify(j.choices?.[0]?.message?.content));
  console.log('  usage:', JSON.stringify(j.usage));
}).catch(e=>console.log('  FAIL', e.message))
"

echo ""
echo "=== 4. free-queue 哨兵（自动挑有容量的车道）==="
docker exec yishi-gateway node -e "
fetch('http://127.0.0.1:19000/v1/chat/completions', {
  method:'POST',
  headers:{'content-type':'application/json','authorization':'Bearer $TOKEN'},
  body: JSON.stringify({model:'free-queue', messages:[{role:'system',content:'你是简洁助手'},{role:'user',content:'只回答两个字：收到'}], max_tokens:300, stream:false})
}).then(r=>r.json()).then(j=>{
  console.log('  content:', JSON.stringify(j.choices?.[0]?.message?.content));
  console.log('  model:', j.model);
}).catch(e=>console.log('  FAIL', e.message))
"
