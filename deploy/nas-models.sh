#!/bin/sh
# 从 codebuddy-bridge 取完整模型表（含倍率）
docker exec codebuddy-bridge node -e '
fetch("http://127.0.0.1:19001/v1/models")
 .then(r=>r.json())
 .then(j=>{
   const want = ["hy3","hy3-x","glm-5.3-flash","space-bunny","deepseek-v4.1-flash","deepseek-v4-flash","minimax-m3","hy4-preview"];
   console.log("  模型".padEnd(24)+"倍率".padEnd(10)+"图片");
   for (const m of j.data) {
     if (!want.includes(m.id)) continue;
     console.log("  "+m.id.padEnd(24)+String(m.credits??"?").padEnd(10)+String(m.supports_images));
   }
 })
 .catch(e=>console.log("FAIL",e.message))
'
