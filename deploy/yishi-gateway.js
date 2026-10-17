#!/usr/bin/env node
/**
 * yishi-gateway —— 给 free-llm-bridge 加一层 Bearer token 认证的薄网关。
 *
 * 为什么需要它：
 *   - free-llm-bridge 本身无任何下游认证（源码里 handle() 根本不校验 Authorization），
 *     且会把免费额度花在任何人身上；直接暴露公网等于把额度送人 + 可能 SSRF。
 *   - 本网关只放行带正确 token 的请求，其余一律 401；只转发 chat/completions 与 models。
 *
 * 用法：
 *   YISHI_GATEWAY_TOKEN=<强随机token> node yishi-gateway.js --port 19000 --upstream http://127.0.0.1:18999
 *
 * 把它跑在 NAS 上与桥同一 Docker 网络内（upstream 用服务名 free-llm-bridge:18999），
 * 再由 tailscale serve/funnel 或 cloudflared 暴露为 HTTPS。
 */
const http = require('node:http')
const crypto = require('node:crypto')

const args = process.argv.slice(2)
function flag(name, def) {
  const i = args.indexOf(name)
  return i >= 0 && args[i + 1] ? args[i + 1] : def
}
const PORT = Number(flag('--port', process.env.PORT || '19000'))
const UPSTREAM = flag('--upstream', process.env.UPSTREAM || 'http://127.0.0.1:18999').replace(/\/+$/, '')
const TOKEN = process.env.YISHI_GATEWAY_TOKEN || ''
const MAX_BODY = Number(process.env.MAX_BODY || 8 * 1024 * 1024)

if (!TOKEN) {
  console.error('[yishi-gateway] YISHI_GATEWAY_TOKEN is required; refusing to run with no auth')
  process.exit(2)
}

const ALLOWED = new Set(['/v1/chat/completions', '/chat/completions', '/v1/models', '/models', '/health'])

function timingSafeEqual(a, b) {
  const ba = Buffer.from(a)
  const bb = Buffer.from(b)
  if (ba.length !== bb.length) return false
  return crypto.timingSafeEqual(ba, bb)
}

function bearer(req) {
  const h = String(req.headers['authorization'] || '')
  return h.toLowerCase().startsWith('bearer ') ? h.slice(7).trim() : ''
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = []
    let size = 0
    req.on('data', c => {
      size += c.length
      if (size > MAX_BODY) { reject(Object.assign(new Error('body too large'), { status: 413 })); req.destroy(); return }
      chunks.push(c)
    })
    req.on('end', () => resolve(Buffer.concat(chunks)))
    req.on('error', reject)
  })
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost')
  const path = url.pathname.replace(/\/+$/, '') || '/'

  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'access-control-allow-origin': '*',
      'access-control-allow-headers': 'authorization, content-type',
      'access-control-allow-methods': 'GET, POST, OPTIONS',
    })
    res.end()
    return
  }

  if (!ALLOWED.has(path)) {
    res.writeHead(404, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ error: { message: 'not found', type: 'invalid_request_error' } }))
    return
  }

  // /health 允许匿名探活（只报存活，不泄露模型/额度），其余必须带 token。
  if (path !== '/health' && !timingSafeEqual(bearer(req), TOKEN)) {
    res.writeHead(401, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ error: { message: 'missing or invalid API key', type: 'invalid_request_error' } }))
    return
  }

  const upstreamPath = url.pathname + url.search
  const body = req.method === 'POST' ? await readBody(req).catch(err => {
    res.writeHead(err.status || 400, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ error: { message: err.message, type: 'invalid_request_error' } }))
    return null
  }) : null
  if (req.method === 'POST' && body === null) return

  const headers = {
    'content-type': req.headers['content-type'] || 'application/json',
  }
  // 会话亲和：透传下游 session/request id，缺失则按 IP 生成稳定值。
  headers['x-session-id'] = req.headers['x-session-id'] || `yishi-${req.socket.remoteAddress || 'local'}`
  if (req.headers['x-request-id']) headers['x-request-id'] = req.headers['x-request-id']

  const upstream = http.request({
    host: new URL(UPSTREAM).hostname,
    port: new URL(UPSTREAM).port,
    path: upstreamPath,
    method: req.method,
    headers,
  }, ures => {
    res.writeHead(ures.statusCode || 502, ures.headers)
    ures.pipe(res)
  })
  upstream.on('error', err => {
    res.writeHead(502, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ error: { message: `gateway upstream error: ${err.message}`, type: 'server_error' } }))
  })
  if (body) upstream.write(body)
  upstream.end()
})

server.listen(PORT, '0.0.0.0', () => {
  console.error(`[yishi-gateway] listening :${PORT} -> ${UPSTREAM} (token auth on)`)
})
