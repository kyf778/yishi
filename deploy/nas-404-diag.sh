#!/bin/sh
# 逐跳定位 404 来源
echo "=== A. codebuddy-bridge 直连（hy3 短请求）==="
docker exec codebuddy-bridge node -e '
const t0=Date.now();
fetch("http://127.0.0.1:19001/v1/chat/completions",{method:"POST",headers:{"content-type":"application/json"},
 body:JSON.stringify({model:"hy3",messages:[{role:"system",content:"s"},{role:"user",content:"只回答两个字：收到"}],max_tokens:200,stream:false})})
.then(async r=>{const t=await r.text();console.log("  status="+r.status+"  "+(Date.now()-t0)+"ms  body="+t.slice(0,300))})
.catch(e=>console.log("  FAIL "+(Date.now()-t0)+"ms "+e.message))
'

echo ""
echo "=== B. bridge 的 /health（看凭据状态）==="
docker exec codebuddy-bridge node -e '
fetch("http://127.0.0.1:19001/health").then(r=>r.json()).then(j=>console.log("  "+JSON.stringify(j))).catch(e=>console.log("  FAIL "+e.message))
'

echo ""
echo "=== C. free-llm-bridge 点名 hy3 ==="
docker exec free-llm-bridge node -e '
const t0=Date.now();
fetch("http://127.0.0.1:18999/v1/chat/completions",{method:"POST",headers:{"content-type":"application/json"},
 body:JSON.stringify({model:"hy3",messages:[{role:"system",content:"s"},{role:"user",content:"只回答两个字：收到"}],max_tokens:200,stream:false})})
.then(async r=>{const t=await r.text();console.log("  status="+r.status+"  "+(Date.now()-t0)+"ms  body="+t.slice(0,300))})
.catch(e=>console.log("  FAIL "+(Date.now()-t0)+"ms "+e.message))
'

echo ""
echo "=== D. free-llm-bridge 最近 30 行日志 ==="
docker logs free-llm-bridge --tail 30 2>&1

echo ""
echo "=== E. 凭据文件修改时间（是否被续期写过）==="
ls -l /vol2/1000/Hindsight/codebuddy-credential.json
date
