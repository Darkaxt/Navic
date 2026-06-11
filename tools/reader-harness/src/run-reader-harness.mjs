import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import {
  assertBridgePostType,
  assertFirstVisibleLocationStartsAtZero,
  assertForwardPageIndexesDoNotRegress,
  assertNoConsoleErrors,
  assertNoConsecutiveDuplicateLocations,
  assertNoConsecutiveDuplicateVisiblePageLabels,
  assertSurfaceTextureTracksForwardContentMovement,
  assertTraceType,
} from './reader-trace-assertions.mjs'
import { startReaderAssetServer } from './serve-reader-assets.mjs'

const currentFile = fileURLToPath(import.meta.url)
const currentDir = path.dirname(currentFile)
const repoRoot = path.resolve(currentDir, '../../..')
const readerBridge = path.join(repoRoot, 'composeApp/src/androidMain/assets/reader/navic-reader.js')
const bridgeText = fs.readFileSync(readerBridge, 'utf8')

const modeArgIndex = process.argv.indexOf('--mode')
const mode = modeArgIndex >= 0 ? process.argv[modeArgIndex + 1] : 'smoke'

const argValue = name => {
  const index = process.argv.indexOf(name)
  return index >= 0 ? process.argv[index + 1] : null
}

const phoneViewport = {
  viewport: { width: 393, height: 873 },
  deviceScaleFactor: 3,
  isMobile: true,
  hasTouch: true,
}

if (mode === 'serve-smoke') {
  const server = await startReaderAssetServer({ repoRoot })
  try {
    const response = await fetch(`${server.origin}/index.html`)
    if (response.status !== 200) {
      console.error(`Expected /index.html to return 200, got ${response.status}`)
      process.exit(1)
    }
    const html = await response.text()
    if (!html.includes('navic-reader.js')) {
      console.error('Expected served /index.html to reference navic-reader.js')
      process.exit(1)
    }
    console.log(`reader harness serve-smoke passed: ${server.origin}`)
  } finally {
    await server.close()
  }
  process.exit(0)
}

if (mode === 'trace-smoke') {
  const server = await startReaderAssetServer({ repoRoot })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(phoneViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(250)
    const trace = await page.evaluate(() => window.__navicReaderTrace || [])
    assertNoConsoleErrors(errors)
    assertTraceType(trace, 'runtime:ready')
    console.log(`reader harness trace-smoke passed: ${server.origin}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-frontmatter') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-frontmatter mode requires --fixture <path>')
    process.exit(1)
  }
  const fixturePath = path.resolve(fixture)
  if (!fs.existsSync(fixturePath) || !fs.statSync(fixturePath).isFile()) {
    console.error(`Fixture file not found: ${fixturePath}`)
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(phoneViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage: message => {
          let parsed = message
          try {
            parsed = JSON.parse(message)
          } catch {
            parsed = { type: 'unparseable', message }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacing: 0.35,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForTimeout(500)
    for (let index = 0; index < 8; index += 1) {
      await page.evaluate(async () => {
        await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      await page.waitForTimeout(350)
    }

    const result = await page.evaluate(() => ({
      trace: window.__navicReaderTrace || [],
      messages: window.__navicReaderPostedMessages || [],
      rootDataset: { ...document.body.dataset },
    }))
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-frontmatter.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      ...result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertTraceType(result.trace, 'runtime:ready')
    assertTraceType(result.trace, 'relocate:raw')
    assertTraceType(result.trace, 'texture:update')
    assertBridgePostType(result.messages, 'publicationReady')
    assertBridgePostType(result.messages, 'locationChanged')
    assertFirstVisibleLocationStartsAtZero(result.messages)
    assertNoConsecutiveDuplicateLocations(result.messages)
    assertNoConsecutiveDuplicateVisiblePageLabels(result.messages)
    assertForwardPageIndexesDoNotRegress(result.messages)

    console.log(`reader harness epub-frontmatter passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-texture-scroll') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-texture-scroll mode requires --fixture <path-to-epub>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(phoneViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          try {
            window.__navicReaderPostedMessages.push(JSON.parse(value))
          } catch {
            window.__navicReaderPostedMessages.push({ raw: value })
          }
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacing: 0.35,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForTimeout(500)
    const result = await page.evaluate(async () => {
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      const layer = document.querySelector('[data-navic-surface-paper-texture-layer="true"]')
      if (!renderer) throw new Error('Missing foliate renderer')
      if (!layer) throw new Error('Missing surface paper texture layer')
      const beforePosition = Number(renderer.containerPosition)
      const delta = Math.min(120, Math.max(48, Math.round(window.innerWidth * 0.25)))
      renderer.containerPosition = beforePosition + delta
      renderer.dispatchEvent(new Event('scroll'))
      await new Promise(resolve => requestAnimationFrame(resolve))
      return {
        beforePosition,
        afterPosition: Number(renderer.containerPosition),
        delta,
        textureBackgroundPosition: layer.style.backgroundPosition,
        computedTextureBackgroundPosition: getComputedStyle(layer).backgroundPosition,
      }
    })
    assertNoConsoleErrors(errors)
    assertSurfaceTextureTracksForwardContentMovement(result)
    console.log(`reader harness epub-texture-scroll passed: ${JSON.stringify(result)}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode !== 'smoke') {
  console.error(`Unsupported reader harness mode: ${mode}`)
  process.exit(1)
}

if (!bridgeText.includes('__navicReaderTrace')) {
  console.error('Reader harness requires window.__navicReaderTrace instrumentation in navic-reader.js')
  process.exit(1)
}

console.log('reader harness smoke passed')
