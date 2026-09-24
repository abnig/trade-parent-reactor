import assert from 'node:assert/strict'
import { createServer, request } from 'node:http'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { startProductionServer } from './serve-production.mjs'

function listen(server) {
  return new Promise(resolvePromise => server.listen(0, '127.0.0.1', () => resolvePromise(server.address().port)))
}

function close(server) {
  return new Promise(resolvePromise => server.close(resolvePromise))
}

function get(port, path) {
  return new Promise((resolvePromise, reject) => {
    const client = request({ host: '127.0.0.1', port, path }, response => {
      let body = ''
      response.setEncoding('utf8')
      response.on('data', value => { body += value })
      response.on('end', () => resolvePromise({ status: response.statusCode, headers: response.headers, body }))
    })
    client.on('error', reject)
    client.end()
  })
}

test('waits for REST health, serves the SPA, and proxies API responses', async context => {
  const dist = await mkdtemp(join(tmpdir(), 'trade-ui-production-'))
  await writeFile(join(dist, 'index.html'), '<main>trade-ui</main>')
  let healthy = false
  const rest = createServer((request, response) => {
    if (request.url === '/actuator/health') return response.writeHead(healthy ? 200 : 503).end()
    if (request.url === '/api/auth/me') return response.writeHead(200, {
      'Content-Type': 'application/json', 'Set-Cookie': 'JSESSIONID=session; Path=/; HttpOnly'
    }).end('{"username":"alice"}')
    response.writeHead(404).end()
  })
  const restPort = await listen(rest)
  context.after(async () => {
    await close(rest)
    await rm(dist, { recursive: true, force: true })
  })

  setTimeout(() => { healthy = true }, 25)
  const ui = await startProductionServer({ uiPort: 0, restPort, distDir: dist, retryDelayMs: 5 })
  context.after(() => close(ui))
  const uiPort = ui.address().port

  assert.equal((await get(uiPort, '/dashboard')).body, '<main>trade-ui</main>')
  const api = await get(uiPort, '/api/auth/me')
  assert.equal(api.status, 200)
  assert.equal(api.body, '{"username":"alice"}')
  assert.equal(api.headers['set-cookie'][0], 'JSESSIONID=session; Path=/; HttpOnly')
})
