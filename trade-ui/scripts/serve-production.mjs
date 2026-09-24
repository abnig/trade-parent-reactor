import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { createServer, request as httpRequest } from 'node:http'
import { fileURLToPath } from 'node:url'
import { dirname, extname, resolve, sep } from 'node:path'

const MIME_TYPES = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml'],
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.ico', 'image/x-icon'],
  ['.woff2', 'font/woff2']
])

function port(value, name) {
  const parsed = Number.parseInt(value, 10)
  if (!Number.isInteger(parsed) || parsed < 0 || parsed > 65_535) throw new Error(`${name} must be a TCP port.`)
  return parsed
}

function pause(milliseconds) {
  return new Promise(resolvePromise => setTimeout(resolvePromise, milliseconds))
}

function healthCheck({ restHost, restPort, healthPath }) {
  return new Promise(resolvePromise => {
    const check = httpRequest({ host: restHost, port: restPort, path: healthPath, method: 'GET', timeout: 2_000 }, response => {
      response.resume()
      response.on('end', () => resolvePromise(response.statusCode >= 200 && response.statusCode < 300))
    })
    check.on('error', () => resolvePromise(false))
    check.on('timeout', () => {
      check.destroy()
      resolvePromise(false)
    })
    check.end()
  })
}

export async function waitForRest(options) {
  const retryDelayMs = options.retryDelayMs ?? 1_000
  for (;;) {
    if (await healthCheck(options)) return
    await pause(retryDelayMs)
  }
}

function inside(root, candidate) {
  return candidate === root || candidate.startsWith(root + sep)
}

async function staticFile(root, pathname) {
  let decoded
  try {
    decoded = decodeURIComponent(pathname)
  } catch {
    return null
  }
  const candidate = resolve(root, `.${decoded === '/' ? '/index.html' : decoded}`)
  if (!inside(root, candidate)) return null
  try {
    return (await stat(candidate)).isFile() ? candidate : null
  } catch {
    return null
  }
}

function sendFile(response, file) {
  response.writeHead(200, {
    'Content-Type': MIME_TYPES.get(extname(file).toLowerCase()) ?? 'application/octet-stream',
    'Cache-Control': extname(file) === '.html' ? 'no-cache' : 'public, max-age=31536000, immutable'
  })
  createReadStream(file).on('error', () => response.destroy()).pipe(response)
}

function proxy(request, response, { restHost, restPort }) {
  const headers = { ...request.headers, host: `${restHost}:${restPort}` }
  const upstream = httpRequest({ host: restHost, port: restPort, path: request.url, method: request.method, headers }, upstreamResponse => {
    response.writeHead(upstreamResponse.statusCode ?? 502, upstreamResponse.headers)
    upstreamResponse.pipe(response)
  })
  upstream.on('error', () => {
    if (!response.headersSent) response.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    response.end('{"status":502,"message":"trade-rest is unavailable."}')
  })
  request.pipe(upstream)
}

function listen(server, portNumber) {
  return new Promise((resolvePromise, reject) => {
    server.once('error', reject)
    server.listen(portNumber, '127.0.0.1', () => {
      server.off('error', reject)
      resolvePromise()
    })
  })
}

export async function startProductionServer({
  uiPort = port(process.env.TRADE_UI_PORT ?? '9999', 'TRADE_UI_PORT'),
  restHost = process.env.TRADE_REST_HOST ?? '127.0.0.1',
  restPort = port(process.env.TRADE_REST_PORT ?? '8888', 'TRADE_REST_PORT'),
  healthPath = process.env.TRADE_REST_HEALTH_PATH ?? '/actuator/health',
  distDir = process.env.TRADE_UI_DIST_DIR ?? resolve(dirname(fileURLToPath(import.meta.url)), '..', 'dist'),
  retryDelayMs
} = {}) {
  const root = resolve(distDir)
  if (!(await stat(root)).isDirectory()) throw new Error(`UI bundle directory does not exist: ${root}`)
  await waitForRest({ restHost, restPort, healthPath, retryDelayMs })
  const server = createServer(async (request, response) => {
    const pathname = new URL(request.url, 'http://localhost').pathname
    if (pathname === '/api' || pathname.startsWith('/api/')) return proxy(request, response, { restHost, restPort })
    const asset = await staticFile(root, pathname)
    if (asset) return sendFile(response, asset)
    if (extname(pathname)) {
      response.writeHead(404).end()
      return
    }
    const index = await staticFile(root, '/')
    if (index) return sendFile(response, index)
    response.writeHead(404).end()
  })
  await listen(server, uiPort)
  console.log(`trade-ui listening on http://127.0.0.1:${server.address().port}; proxying /api to ${restHost}:${restPort}`)
  return server
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  startProductionServer().catch(error => {
    console.error(`Unable to start trade-ui: ${error.message}`)
    process.exitCode = 1
  })
}
