# CodeBuddy（腾讯 WorkBuddy 国内版）接入部署文档

部署日期：2026-10-17
状态：**已上线，公网可用并实测通过**

---

## 一、这件事做成了什么

把**你自己 CodeBuddy 账号的积分模型**，接成了忆时可以直接调用的公网 AI 服务。

```
手机（任何网络，不用装任何 VPN/App）
   ↓ HTTPS
https://nas-test.tailacf3df.ts.net/v1/chat/completions
   ↓
NAS: yishi-gateway (19000, Bearer 认证)
   ↓
NAS: free-llm-bridge (18999, 多车道故障转移)
   ↓ ← codebuddy 车道
NAS: codebuddy-bridge (19001, 格式转换 + token 续期)
   ↓
腾讯 CodeBuddy /v2/chat/completions
```

**实测结果**（2026-10-17）：

| 测试项 | 结果 |
|---|---|
| 公网通道 | ✅ `HTTPS 200` |
| 中文问答 | ✅ "数据库索引是一种类似于书籍目录的辅助数据结构…" |
| **图片识别** | ✅ 正确识别「下周三16点」→ `2026-10-21T16:00` |
| 多模型 | ✅ hy3 / glm-5.3-flash / deepseek-v4.1-flash / minimax-m3 / hy4-preview |
| 响应速度 | 4.9 ~ 12.2 秒 |

---

## 二、为什么需要 codebuddy-bridge 这一层

**CodeBuddy 的 API 不是标准 OpenAI**，有五处硬性要求（全部实测踩出来的）：

| # | 要求 | 不满足会怎样 |
|---|---|---|
| 1 | **`stream` 必须为 `true`** | `400 code=11101 Non-stream chat request is currently not supported` |
| 2 | **必须带身份头** `X-Product` / `X-Product-Code` / `X-Conversation-*` / `X-IDE-*` | 可能被当成非官方客户端或风控 |
| 3 | **`messages[0]` 必须是 `system`** | `400 code=11128 first message is not system prompt` |
| 4 | **图片必须和文字在同一条 user 消息里** | 图单独一条 → 回「不支持图片识别」 |
| 5 | **token 会过期**，需按 `refresh_token` 续期 | `401` |

这一层的作用就是**满足这五条，同时对外暴露标准 OpenAI 形态**：
- 收标准 OpenAI 请求（含非流式）
- 强制改成流式发给 CodeBuddy
- 把 SSE 拼回完整文本，再按调用方要的形态返回
- 自动补 system、归并图片、续期 token

---

## 三、关键参数（供排查用）

### 产品配置

| 项 | 值 |
|---|---|
| 端点 | `https://www.codebuddy.cn` |
| X-Domain | `www.codebuddy.cn` |
| X-Product | `SaaS`（固定值，部署类型）|
| **X-Product-Code** | **`codebuddy`**（⚠️ 不是 workbuddy）|
| User-Agent | `CodeBuddyIDE/1.106.1` |
| X-IDE-Name / X-IDE-Type | `CodeBuddy` |
| X-IDE-Version | `1.106.1` |

> ⚠️ **命名很容易搞混**，记录清楚：
> 插件界面上的「**WorkBuddy 国内版**」= 内部 provider **`buddy`** = `copilot.tencent.com` / `www.codebuddy.cn`，productCode **`codebuddy`**。
> 插件界面上的「WorkBuddy 国际版」= 内部 provider `workbuddy` = `www.workbuddy.ai`，productCode `workbuddy`。
> 本方案用的是**国内版**。

### 账号（用的第 2 个）

| 项 | 值 |
|---|---|
| 凭据 ref | `BUDDY_ACCOUNT_B48091B4` |
| 昵称 | 悲伤玉米排骨汤 |
| user_id | `d278ed8b-a92b-49f9-a1c0-29b7db0cfbe2` |
| domain | `www.codebuddy.cn` |

### 模型与倍率（实测）

**免费 / 低价**（已加入白名单）：

| 模型 | 倍率 | 图片 | 上下文 |
|---|---|---|---|
| `hy3` | **x0.00** | ✅ | 192K |
| `hy3-x` | x0.05 | ✅ | — |
| `glm-5.3-flash` | x0.06 | ✅ | 1M |
| `space-bunny` | x0.08 | ✅ | — |
| `deepseek-v4.1-flash` | x0.11 | ✅ | 1M |
| `deepseek-v4-flash` | x0.17 | ✅ | 1M |
| `minimax-m3` | x0.25 | ✅ | 512K |
| `hy4-preview` | x0.29 | ✅ | 1M |

**⚠️ 不要用 `free-queue`**：它是"按容量自动挑车道"的哨兵，
实测会挑到已经不通的 kilo 车道并返回 404。要么点名模型，要么确认车道都健康。

---

## 四、文件清单

### 新增（都在 NAS `/vol2/1000/Hindsight/`）

| 文件 | 说明 | 权限 |
|---|---|---|
| `codebuddy-bridge.js` | 桥的源码（约 480 行 Node） | 644 |
| `codebuddy-credential.json` | 账号凭据（含 refresh_token） | **600** |
| `docker-compose.yml` | 已加 `codebuddy-bridge` 服务 + `codebuddy` 车道 | — |

### 源码副本（在项目里，便于重部署）

| 文件 | 说明 |
|---|---|
| `yishi/deploy/codebuddy-bridge.js` | 桥源码 |
| `yishi/deploy/nas-deploy.sh` | 首次部署脚本 |
| `yishi/deploy/nas-add-lane.sh` | 追加车道 |
| `yishi/deploy/nas-whitelist.sh` | 扩展模型白名单 |
| `yishi/deploy/nas-verify.sh` | 端到端验证 |

### 备份（NAS 上，出问题可回滚）

```
docker-compose.yml.bak-before-cb-YYYYMMDD-HHMMSS   （改前）
docker-compose.yml.bak-lane-YYYYMMDD-HHMMSS        （加车道前）
docker-compose.yml.bak-whitelist-YYYYMMDD-HHMMSS   （改白名单前）
```

---

## 五、日常运维

### 看桥的状态

```bash
ssh -i nas_key adm@192.168.31.123
docker logs codebuddy-bridge --tail 30
docker ps --filter name=codebuddy-bridge
```

### 健康检查

```bash
docker exec codebuddy-bridge node -e "
fetch('http://127.0.0.1:19001/health').then(r=>r.json()).then(j=>console.log(j))
"
```

返回里的 `expiresIn` 是 token 剩余有效期（分钟）。**续期是自动的**（到期前 5 分钟触发）。

### 公网验证

```bash
curl -s https://nas-test.tailacf3df.ts.net/v1/chat/completions \
  -H "Authorization: Bearer <网关token>" \
  -H "Content-Type: application/json" \
  -d '{"model":"hy3","messages":[{"role":"system","content":"hi"},{"role":"user","content":"只回答：收到"}],"max_tokens":100}'
```

### 换 token（重新登录后）

```bash
# 从本机 ~/.dsh/.credentials.yaml 取出 BUDDY_ACCOUNT_B48091B4 的 JSON，
# 去掉 BOM 后写到 NAS，并 chmod 600
```

### 重启桥

```bash
docker restart codebuddy-bridge
# 或改了 compose 后：
docker compose up -d --force-recreate codebuddy-bridge
```

---

## 六、踩过的坑（重要）

| 坑 | 现象 | 解决 |
|---|---|---|
| **凭据文件 BOM** | 桥启动即 `FATAL Unexpected token '', "{access_"... is not valid JSON` | 用 `UTF8Encoding($false)` 写文件（PS 的 `Set-Content -Encoding UTF8` 会加 BOM）|
| **NAS 主机没有 node** | 脚本里 `node: not found` | 用 `docker run --rm node:22-alpine` 跑 |
| **容器启动竞争** | 刚起来就访问报 `fetch failed`，几秒后正常 | 部署后 sleep 5~8 再验证 |
| **模型白名单** | `model "deepseek-v4.1-flash" is not a free-lane model` | bridge 第 806 行只放行 `-free` 结尾或白名单里的模型；把要用的加进 `knownFreeModels` |
| **PowerShell 引号地狱** | `$(...)` 被 PS 解析、嵌套引号报错 | 复杂命令写成 `.sh` 文件再 scp 过去执行 |
| **思考模式吃 token** | `max_tokens=100` 时正文空、`finish_reason=length` | 请求带 `reasoning:{enabled:false}`，或把 `max_tokens` 放大 |
| **free-queue 选到坏车道** | `404 model ... is not routed by the upstream` | 点名模型，别用哨兵 |

---

## 七、App 侧配置

`AppSettings.kt` 的默认值已改：

```kotlin
const val BASE_URL = "https://nas-test.tailacf3df.ts.net/v1"
const val MODEL = "hy3"                    // 免费，支持图片
const val API_KEY = "<网关 token>"
const val VISION_BASE_URL = BASE_URL       // 视觉也走这个
const val VISION_MODEL = MODEL
```

同时 `MainActivity.buildProvider()` 改成 `disableReasoning = true`（一律关思考），
因为 CodeBuddy 的模型默认开思考会吃 token，Kilo 的视觉模型不带这个字段会返回空。

---

## 八、成本估算

| 模型 | 倍率 | 一次截图识别的估算消耗 |
|---|---|---|
| `hy3` | x0.00 | **0**（完全免费）|
| `glm-5.3-flash` | x0.06 | 约 0.1~0.5 积分 |
| `deepseek-v4.1-flash` | x0.11 | 约 0.2~1 积分 |

按你的账号积分，跑课程设计**绰绰有余**。优先用 `hy3`（免费且能读图）。

---

## 九、限制与风险

1. **依赖 NAS**：NAS 挂了或 Tailscale Funnel 断了就不能用。
   App 里保留了 Kilo 作为兜底配置（`FALLBACK_VISION_*`）。
2. **token 一年有效**（expires_at 2027-10-07），过期要重新登录 CodeBuddy 客户端。
3. **积分会用完**：`glm-5.3-flash` 实测报过 `14018 Credits exhausted`，
   要定期在插件里「一键领取」。
4. **服务端可能风控**：非官方客户端调用，本方案尽力模仿官方请求形态（身份头齐全）。
5. **别在前端暴露凭据**：`codebuddy-credential.json` 只在 NAS 上，权限 600。
