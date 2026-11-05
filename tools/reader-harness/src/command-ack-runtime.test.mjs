import assert from 'node:assert/strict'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { startReaderAssetServer } from './serve-reader-assets.mjs'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')

// Real Foliate/EPUB opening: removing any original await-boundary observation must fail.
test('real publication open exposes ordered closed observations without replacing readiness', async () => {
  const server = await startReaderAssetServer({ repoRoot, extraFiles: new Map([
    ['/public-open.epub', path.join(repoRoot, 'tools/reader-harness/fixtures/public-open.epub')],
  ]) })
  const browser = await chromium.launch()
  try {
    const page = await browser.newPage({ viewport: { width: 560, height: 420 } })
    await page.addInitScript(() => {
      window.__openPhases = []
      window.__posted = []
      window.NavicAndroidBridge = { postMessage: value => window.__posted.push(JSON.parse(value)) }
      const original = console.log
      console.log = (...args) => {
        if (args.length === 2 && args[0] === '[NavicReader]' &&
            /^open-(view|cover|navigation|pagination)-(started|completed)$/.test(args[1])) {
          window.__openPhases.push(args[1])
        }
        original(...args)
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.waitForFunction(() => typeof window.NavicReaderBridge?.dispatch === 'function')
    const result = await page.evaluate(async () => {
      const url = new URL('/public-open.epub', location.href).href
      await window.NavicReaderBridge.dispatch({ type: 'openPublication', commandId: 'public-open',
        foliateSessionId: 'public-open-session', url, settings: { maxColumnCount: 1 } })
      return { phases: window.__openPhases,
        ready: window.__posted.filter(record => record.type === 'publicationReady').length,
        acknowledged: window.__posted.filter(record => record.type === 'commandAck').length,
        failed: window.__posted.filter(record => record.type === 'commandFailed' || record.type === 'error').length }
    })
    assert.equal(result.ready, 1, 'PUBLIC_OPEN_READY_REQUIRED')
    assert.equal(result.acknowledged, 1, 'PUBLIC_OPEN_ACK_REQUIRED')
    assert.equal(result.failed, 0, 'PUBLIC_OPEN_NO_FAILURE_REQUIRED')
    assert.deepEqual(result.phases, [
      'open-view-started', 'open-view-completed',
      'open-cover-started', 'open-cover-completed',
      'open-navigation-started', 'open-navigation-completed',
      'open-pagination-started', 'open-pagination-completed',
    ], 'PUBLIC_OPEN_PHASE_ORDER_REQUIRED')
  } finally {
    await browser.close()
    await server.close()
  }
})

test('tracked commands acknowledge completion and deduplicate stable IDs', async () => {
  const server = await startReaderAssetServer({ repoRoot })
  const browser = await chromium.launch()
  try {
    const page = await browser.newPage()
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          window.__navicReaderPostedMessages.push(JSON.parse(value))
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.waitForFunction(() => typeof window.NavicReaderBridge?.dispatch === 'function')

    const result = await page.evaluate(async () => {
      const commandId = 'reader-command-test-1'
      await window.NavicReaderBridge.dispatch({
        commandId,
        type: 'applySettings',
        settings: { theme: 'sepia' },
      })
      await window.NavicReaderBridge.dispatch({
        commandId,
        type: 'applySettings',
        settings: { theme: 'dusk' },
      })
      await window.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: { theme: 'night' },
      })
      return {
        acknowledgements: window.__navicReaderPostedMessages.filter(
          message => message.type === 'commandAck'
        ),
        applySettingsDispatchCount: window.__navicReaderTrace.filter(
          entry => entry.type === 'dispatch' && entry.payload?.type === 'applySettings'
        ).length,
      }
    })

    assert.deepEqual(result.acknowledgements, [
      { type: 'commandAck', commandId: 'reader-command-test-1' },
      { type: 'commandAck', commandId: 'reader-command-test-1' },
    ])
    assert.equal(
      result.applySettingsDispatchCount,
      2,
      'the tracked command should execute once; the untracked harness command should execute separately'
    )
  } finally {
    await browser.close()
    await server.close()
  }
})
