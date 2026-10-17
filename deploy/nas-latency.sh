#!/bin/sh
# 逐跳测耗时，定位慢在哪一段

TOKEN=<NAS_GATEWAY_KEY>

echo "========================================"
echo "第1跳：NAS 本机 -> codebuddy-bridge (19001)"
echo "========================================"
docker exec codebuddy-bridge node -e "
const t0=Date.now();
fetch('http://127.0.0.1:19001/v1/chat/completions',{method:'POST',headers:{'content-type':'application/json'},
 body:JSON.stringify({model:'hy3',messages:[{role:'system',content:'你是简洁助手'},{role:'user',content:'只回答两个字：收到'}],max_tokens:200,stream:false})})
.then(r=>r.json()).then(j=>{console.log('  耗时',Date.now()-t0,'ms  content=',JSON.stringify(j.choices?.[0]?.message?.content))})
.catch(e=>console.log('  失败',Date.now()-t0,'ms',e.message))
"

echo ""
echo "========================================"
echo "第2跳：NAS 本机 -> free-llm-bridge (18999)"
echo "========================================"
docker exec free-llm-bridge node -e "
const t0=Date.now();
fetch('http://127.0.0.1:18999/v1/chat/completions',{method:'POST',headers:{'content-type':'application/json'},
 body:JSON.stringify({model:'hy3',messages:[{role:'system',content:'你是简洁助手'},{role:'user',content:'只回答两个字：收到'}],max_tokens:200,stream:false})})
.then(r=>r.json()).then(j=>{console.log('  耗时',Date.now()-t0,'ms  content=',JSON.stringify(j.choices?.[0]?.message?.content))})
.catch(e=>console.log('  失败',Date.now()-t0,'ms',e.message))
"

echo ""
echo "========================================"
echo "第3跳：NAS 本机 -> yishi-gateway (19000)"
echo "========================================"
docker exec yishi-gateway node -e "
const t0=Date.now();
fetch('http://127.0.0.1:19000/v1/chat/completions',{method:'POST',headers:{'content-type':'application/json','authorization':'Bearer $TOKEN'},
 body:JSON.stringify({model:'hy3',messages:[{role:'system',content:'你是简洁助手'},{role:'user',content:'只回答两个字：收到'}],max_tokens:200,stream:false})})
.then(r=>r.json()).then(j=>{console.log('  耗时',Date.now()-t0,'ms  content=',JSON.stringify(j.choices?.[0]?.message?.content))})
.catch(e=>console.log('  失败',Date.now()-t0,'ms',e.message))
"

echo ""
echo "========================================"
echo "第4跳：NAS 自己直连 codebuddy.cn（不走桥）"
echo "========================================"
docker exec codebuddy-bridge node -e "
const t0=Date.now();
fetch('https://www.codebuddy.cn/v3/config',{headers:{'authorization':'Bearer '+(JSON.parse(require('fs').readFileSync('/app/credential.json','utf8')).access_token),'x-domain':'www.codebuddy.cn','x-product':'SaaS','x-product-code':'codebuddy','user-agent':'CodeBuddyIDE/1.106.1'}})
.then(r=>{console.log('  /v3/config 耗时',Date.now()-t0,'ms  status=',r.status)})
.catch(e=>console.log('  失败',Date.now()-t0,'ms',e.message))
"
