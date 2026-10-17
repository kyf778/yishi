#!/bin/sh
# 诊断为什么 free-llm-bridge 连不上 codebuddy-bridge

echo "=== 1. 三个容器的网络详情 ==="
for c in free-llm-bridge yishi-gateway codebuddy-bridge; do
  echo "--- $c ---"
  docker inspect "$c" --format 'NetworkID: {{range $k,$v := .NetworkSettings.Networks}}{{$k}} => {{$v.NetworkID}} IP={{$v.IPAddress}}{{end}}'
done

echo ""
echo "=== 2. codebuddy-bridge 的别名/DNS ==="
docker inspect codebuddy-bridge --format 'DNSNames: {{range $k,$v := .NetworkSettings.Networks}}{{json $v.DNSNames}}{{end}}'

echo ""
echo "=== 3. 从 free-llm-bridge 内部看 DNS 解析 ==="
docker exec free-llm-bridge getent hosts codebuddy-bridge 2>&1 || echo "(getent 不可用)"
docker exec free-llm-bridge cat /etc/resolv.conf 2>&1 | head -5

echo ""
echo "=== 4. 从 free-llm-bridge 直接 curl（用 IP）==="
CBIP=$(docker inspect codebuddy-bridge --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
echo "codebuddy-bridge IP = $CBIP"
docker exec free-llm-bridge node -e "fetch('http://$CBIP:19001/health').then(r=>r.json()).then(j=>console.log('IP_OK',JSON.stringify(j))).catch(e=>console.log('IP_FAIL',e.message))"

echo ""
echo "=== 5. 用容器名再试一次（间隔几秒，排除启动竞争）==="
sleep 2
docker exec free-llm-bridge node -e "fetch('http://codebuddy-bridge:19001/health').then(r=>r.json()).then(j=>console.log('NAME_OK',JSON.stringify(j))).catch(e=>console.log('NAME_FAIL',e.message))"
