import { build } from 'vite'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

const output = await mkdtemp(join(tmpdir(), 'trade-ui-tests-'))
try {
  await build({
    configFile: false,
    logLevel: 'error',
    esbuild: { jsx: 'automatic' },
    ssr: { noExternal: true },
    build: {
      ssr: resolve('src/App.test.jsx'), outDir: output, emptyOutDir: false,
      rollupOptions: {
        external: ['node:test', 'node:assert/strict'],
        output: { format: 'cjs', entryFileNames: 'view.test.cjs' }
      }
    }
  })
  const result = spawnSync(process.execPath, ['--test', 'src/api/api.test.js', join(output, 'view.test.cjs')], { stdio: 'inherit' })
  process.exitCode = result.status ?? 1
} finally {
  await rm(output, { recursive: true, force: true })
}
