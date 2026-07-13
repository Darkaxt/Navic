import assert from 'node:assert/strict'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { startReaderAssetServer } from './serve-reader-assets.mjs'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')

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
