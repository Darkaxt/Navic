import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { spawnSync } from 'node:child_process'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { chromium } from 'playwright'
import {
  assertBridgePostType,
  assertFirstVisibleLocationStartsAtZero,
  assertForwardPageIndexesDoNotRegress,
  assertFullEpubTraversal,
  assertNoConsoleErrors,
  assertNoConsecutiveDuplicateLocations,
  assertNoConsecutiveDuplicateVisiblePageLabels,
  assertPdfFastSequentialTurns,
  assertPdfImageSettings,
  assertPdfSmoke,
  assertRendererCssSmoke,
  assertShellCoverDoesNotNavigateWebViewToCover,
  assertSurfaceTextureTracksForwardContentMovement,
  assertTextureTracksRealPageTurnSamples,
  assertTextureTracePayloadsTrackTurnDirection,
  assertTextureUpdatesAreCommittedPageBounded,
  assertTraceType,
} from './reader-trace-assertions.mjs'
import { startReaderAssetServer } from './serve-reader-assets.mjs'

const currentFile = fileURLToPath(import.meta.url)
const currentDir = path.dirname(currentFile)
const repoRoot = path.resolve(currentDir, '../../..')
const readerBridge = path.join(repoRoot, 'composeApp/src/androidMain/assets/reader/navic-reader.js')
const readerHelpers = path.join(repoRoot, 'composeApp/src/androidMain/assets/reader/navic-reader-helpers.js')
const bridgeText = fs.readFileSync(readerBridge, 'utf8')
const helperText = fs.readFileSync(readerHelpers, 'utf8')

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

const performReaderTouchDrag = async (
  page,
  {
    startX,
    startY,
    endX,
    endY,
    steps = 8,
    durationMs = 420,
  }
) => {
  const client = await page.context().newCDPSession(page)
  const point = (x, y) => ({
    x: Math.round(x),
    y: Math.round(y),
    radiusX: 1,
    radiusY: 1,
    force: 1,
    id: 1,
  })
  const stepCount = Math.max(2, Number(steps) || 8)
  const delay = Math.max(8, Math.round((Number(durationMs) || 420) / stepCount))
  try {
    await client.send('Input.dispatchTouchEvent', {
      type: 'touchStart',
      touchPoints: [point(startX, startY)],
    })
    for (let index = 1; index <= stepCount; index += 1) {
      const progress = index / stepCount
      const x = startX + (endX - startX) * progress
      const y = startY + (endY - startY) * progress
      await page.waitForTimeout(delay)
      await client.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [point(x, y)],
      })
    }
    await page.waitForTimeout(delay)
    await client.send('Input.dispatchTouchEvent', {
      type: 'touchEnd',
      touchPoints: [],
    })
  } finally {
    await client.detach().catch(() => {})
  }
}

if (mode === 'phase1-stabilization') {
  const epubFixturePath = path.resolve(argValue('--epub-fixture') || '')
  const pdfFixturePath = path.resolve(argValue('--pdf-fixture') || '')
  if (!epubFixturePath || !fs.existsSync(epubFixturePath)) {
    console.error('phase1-stabilization mode requires --epub-fixture <path-to-epub>')
    process.exit(1)
  }
  if (!pdfFixturePath || !fs.existsSync(pdfFixturePath)) {
    console.error('phase1-stabilization mode requires --pdf-fixture <path-to-pdf>')
    process.exit(1)
  }

  const defaultStepTimeoutMs = 120_000
  const longTraversalTimeoutMs = 360_000
  const steps = [
    { mode: 'trace-smoke' },
    { mode: 'epub-frontmatter', fixture: epubFixturePath },
    { mode: 'epub-page-boundary', fixture: epubFixturePath, timeoutMs: defaultStepTimeoutMs },
    { mode: 'epub-shell-cover', fixture: epubFixturePath },
    { mode: 'epub-external-shell-cover', fixture: epubFixturePath },
    { mode: 'epub-native-tap-zone-open', fixture: epubFixturePath },
    { mode: 'css-smoke', fixture: epubFixturePath },
    { mode: 'texture-offset-logic' },
    { mode: 'epub-texture-scroll', fixture: epubFixturePath },
    { mode: 'epub-texture-page-turns', fixture: epubFixturePath, timeoutMs: defaultStepTimeoutMs },
    { mode: 'epub-texture-frontmatter-transition', fixture: epubFixturePath },
    { mode: 'epub-full-traversal', fixture: epubFixturePath, timeoutMs: longTraversalTimeoutMs },
    { mode: 'pdf-smoke', fixture: pdfFixturePath },
    { mode: 'pdf-fast-sequential-turns', fixture: pdfFixturePath },
    { mode: 'pdf-image-settings', fixture: pdfFixturePath },
  ]

  for (const step of steps) {
    const args = [currentFile, '--mode', step.mode]
    if (step.fixture) args.push('--fixture', step.fixture)
    const timeoutMs = step.timeoutMs ?? defaultStepTimeoutMs
    const startedAt = Date.now()
    console.log(`reader harness phase1-stabilization running: ${step.mode} timeoutMs=${timeoutMs}`)
    const result = spawnSync(process.execPath, args, {
      cwd: repoRoot,
      stdio: 'inherit',
      env: process.env,
      timeout: timeoutMs,
    })
    const elapsed = ((Date.now() - startedAt) / 1000).toFixed(1)
    if (result.error?.code === 'ETIMEDOUT') {
      console.error(`reader harness phase1-stabilization timed out at ${step.mode} elapsed=${elapsed}s timeoutMs=${timeoutMs} error=ETIMEDOUT`)
      process.exit(124)
    }
    if (result.status !== 0) {
      console.error(`reader harness phase1-stabilization failed at ${step.mode} elapsed=${elapsed}s`)
      process.exit(result.status || 1)
    }
    console.log(`reader harness phase1-stabilization completed: ${step.mode} elapsed=${elapsed}s`)
  }

  console.log(`reader harness phase1-stabilization passed: ${steps.length} checks`)
  process.exit(0)
}

if (mode === 'texture-offset-logic') {
  globalThis.document = globalThis.document || {
    body: {},
    documentElement: { clientWidth: 560, clientHeight: 873 },
  }
  globalThis.window = globalThis.window || {
    innerWidth: 560,
    innerHeight: 873,
    visualViewport: { width: 560, height: 873 },
    location: { origin: 'http://127.0.0.1', href: 'http://127.0.0.1/index.html' },
  }
  const helpers = await import(`${pathToFileURL(readerHelpers).href}?texture-offset-logic=${Date.now()}`)
  if (typeof helpers.readerSurfacePaperTextureScrollOffset !== 'function') {
    throw new Error('readerSurfacePaperTextureScrollOffset helper is not exported')
  }
  if (typeof helpers.readerPaperTextureDragDirection !== 'function') {
    throw new Error('readerPaperTextureDragDirection helper is not exported')
  }

  const assertOffset = (name, actual, expected) => {
    if (actual?.x !== expected.x || actual?.y !== expected.y) {
      throw new Error(`${name} expected ${JSON.stringify(expected)} but got ${JSON.stringify(actual)}`)
    }
  }

  assertOffset(
    'forward movement offsets texture left',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 280,
      baseOffset: 0,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: 'next',
    }),
    { x: -280, y: 0 }
  )
  assertOffset(
    'forward movement keeps texture direction across inverted renderer coordinates',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: -280,
      baseOffset: 0,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: 'next',
    }),
    { x: -280, y: 0 }
  )
  assertOffset(
    'forward area boundary follows renderer wrap instead of inverting texture',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: -750,
      baseOffset: 423,
      viewportWidth: 698,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: 'next',
    }),
    { x: 698, y: 0 }
  )
  assertOffset(
    'previous area boundary follows renderer wrap instead of inverting texture',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 1395,
      baseOffset: 697,
      viewportWidth: 698,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: 'previous',
    }),
    { x: -698, y: 0 }
  )
  assertOffset(
    'directionless area-wrap jump does not invert texture',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 120,
      baseOffset: 2604,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: null,
    }),
    { x: 0, y: 0 }
  )
  assertOffset(
    'directionless reverse area-wrap jump does not invert texture',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 2604,
      baseOffset: 120,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: null,
    }),
    { x: 0, y: 0 }
  )
  const assertDirection = (name, actual, expected) => {
    if (actual !== expected) {
      throw new Error(`${name} expected ${expected} but got ${actual}`)
    }
  }
  assertDirection(
    'horizontal left drag seeds next texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: -84,
      deltaY: 8,
      flowMode: 'paged',
    }),
    'next'
  )
  assertDirection(
    'horizontal right drag seeds previous texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: 84,
      deltaY: 8,
      flowMode: 'paged',
    }),
    'previous'
  )
  assertDirection(
    'vertical paged upward drag seeds next texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: 4,
      deltaY: -84,
      flowMode: 'paged-vertical',
    }),
    'next'
  )
  assertDirection(
    'small drag does not seed texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: 8,
      deltaY: 3,
      flowMode: 'paged',
    }),
    null
  )

  console.log('reader harness texture-offset-logic passed')
  process.exit(0)
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

if (mode === 'font-css-smoke') {
  const server = await startReaderAssetServer({ repoRoot })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(phoneViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    const result = await page.evaluate(async helperUrl => {
      const helpers = await import(helperUrl)
      const safeSettings = {
        fontSource: 'custom',
        customFontFamily: 'Storyteller Serif',
        customFontUrl: 'https://appassets.androidplatform.net/reader-cache/fonts/storyteller-serif.ttf',
      }
      const unsafeSettings = {
        fontSource: 'custom',
        customFontFamily: 'Bad Font"; color:red;/*',
        customFontUrl: 'https://evil.example/fonts/bad.ttf',
      }
      return {
        customSource: helpers.readerFontSource(safeSettings),
        customFamily: helpers.readerEffectiveFontFamily(safeSettings),
        safeCss: helpers.readerFontFaceCss(safeSettings),
        unsafeCss: helpers.readerFontFaceCss(unsafeSettings),
        navicCss: helpers.readerFontFaceCss({ fontSource: 'navic' }),
      }
    }, `${server.origin}/navic-reader-helpers.js`)
    assertNoConsoleErrors(errors)
    if (result.customSource !== 'custom') {
      throw new Error(`Expected custom font source; observed ${result.customSource}`)
    }
    if (!String(result.customFamily || '').includes('Storyteller Serif')) {
      throw new Error(`Expected custom effective font family; observed ${result.customFamily || 'unset'}`)
    }
    if (!String(result.safeCss || '').includes('@font-face') || !String(result.safeCss || '').includes('storyteller-serif.ttf')) {
      throw new Error(`Expected safe custom font CSS; observed ${result.safeCss || 'unset'}`)
    }
    if (String(result.unsafeCss || '').includes('@font-face') || String(result.unsafeCss || '').includes('evil.example')) {
      throw new Error(`Expected unsafe custom font URL to be rejected; observed ${result.unsafeCss || 'unset'}`)
    }
    if (!String(result.navicCss || '').includes('Navic Literata')) {
      throw new Error('Expected bundled Navic font CSS to remain available')
    }
    console.log('reader harness font-css-smoke passed')
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

if (mode === 'epub-page-boundary') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-page-boundary mode requires --fixture <path>')
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
    await page.waitForTimeout(650)
    await page.evaluate(async () => {
      await window.NavicReaderBridge.dispatch({ type: 'goToProgress', progress: 0.74 })
    })
    await page.waitForTimeout(700)
    for (let index = 0; index < 130; index += 1) {
      await page.evaluate(async () => {
        await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      await page.waitForTimeout(220)
    }

    const result = await page.evaluate(() => ({
      trace: window.__navicReaderTrace || [],
      messages: window.__navicReaderPostedMessages || [],
    }))
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-page-boundary.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      ...result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertBridgePostType(result.messages, 'locationChanged')
    assertNoConsecutiveDuplicateVisiblePageLabels(result.messages)
    assertForwardPageIndexesDoNotRegress(result.messages)
    assertTextureUpdatesAreCommittedPageBounded(result.trace)

    console.log(`reader harness epub-page-boundary passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-native-tap-zone-open') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-native-tap-zone-open mode requires --fixture <path>')
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
          nativeTapZones: true,
          showTapZones: true,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 20000 })
    const result = await page.evaluate(() => ({
      trace: window.__navicReaderTrace || [],
      messages: window.__navicReaderPostedMessages || [],
      tapOverlayExists: Boolean(document.querySelector('[data-navic-tap-zone-overlay-layer="true"]')),
    }))
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-native-tap-zone-open.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      ...result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertBridgePostType(result.messages, 'publicationReady')
    assertBridgePostType(result.messages, 'locationChanged')
    if (!result.tapOverlayExists) {
      throw new Error('Expected visible tap-zone overlay layer in native tap-zone diagnostic mode')
    }
    console.log(`reader harness epub-native-tap-zone-open passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-full-traversal') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-full-traversal mode requires --fixture <path>')
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
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 15000 })
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })

    const collectLocationSnapshot = async () => page.evaluate(() => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const location = messages.at(-1)
      const view = document.querySelector('foliate-view')
      const contents = view?.renderer?.getContents?.() || []
      const coverImageHits = []
      for (const content of contents) {
        const doc = content?.doc
        if (!doc?.body) continue
        for (const image of Array.from(doc.images || [])) {
          const src = image.getAttribute('src') || image.currentSrc || image.src || ''
          if (/cover|frontcover|coverpage|cubierta|portada/i.test(src)) {
            coverImageHits.push({ pageIndex: location?.pageIndex, href: location?.href, src })
          }
        }
      }
      return {
        location,
        shellCoverVisible: document.body.dataset.navicShellCoverVisible === 'true',
        documents: [],
        coverImageHits,
        coverLikePages: [],
      }
    })

    const collectCoverScanSnapshot = async () => page.evaluate(() => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const location = messages.at(-1)
      const view = document.querySelector('foliate-view')
      const contents = view?.renderer?.getContents?.() || []
      const documents = []
      const coverImageHits = []
      const coverLikePages = []
      for (const content of contents) {
        const doc = content?.doc
        if (!doc?.body) continue
        const frameRect = doc.defaultView?.frameElement?.getBoundingClientRect?.()
        const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0
        const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0
        const text = doc.body.textContent?.replace(/\s+/g, ' ').trim() || ''
        const screenRectFor = rect => ({
          left: (frameRect?.left || 0) + rect.left,
          top: (frameRect?.top || 0) + rect.top,
          right: (frameRect?.left || 0) + rect.right,
          bottom: (frameRect?.top || 0) + rect.bottom,
          width: rect.width,
          height: rect.height,
        })
        const intersectionArea = rect => {
          const left = Math.max(0, rect.left)
          const right = Math.min(viewportWidth, rect.right)
          const top = Math.max(0, rect.top)
          const bottom = Math.min(viewportHeight, rect.bottom)
          return Math.max(0, right - left) * Math.max(0, bottom - top)
        }
        const images = Array.from(doc.images || []).map(image => {
          const width = Number(image.getAttribute('width') || image.naturalWidth)
          const height = Number(image.getAttribute('height') || image.naturalHeight)
          const src = image.getAttribute('src') || image.currentSrc || image.src || ''
          const rect = image.getBoundingClientRect()
          const screenRect = screenRectFor(rect)
          return {
            src,
            width,
            height,
            aspect: width > 0 ? height / width : 0,
            screenArea: intersectionArea(screenRect),
            screenRect,
          }
        })
        const visibleText = Array.from(doc.body.querySelectorAll('body *'))
          .filter(element => {
            const rect = screenRectFor(element.getBoundingClientRect())
            const area = intersectionArea(rect)
            const elementArea = Math.max(1, rect.width * rect.height)
            return area > 48 && area / elementArea > 0.15
          })
          .map(element => element.textContent?.replace(/\s+/g, ' ').trim() || '')
          .filter(Boolean)
          .join(' ')
          .replace(/\s+/g, ' ')
          .trim()
        for (const image of images) {
          if (/cover|frontcover|coverpage|cubierta|portada/i.test(image.src)) {
            coverImageHits.push({ pageIndex: location?.pageIndex, href: location?.href, src: image.src })
          }
        }
        const dominantVisibleImage = images
          .filter(image => image.screenArea > 0)
          .sort((left, right) => right.screenArea - left.screenArea)[0]
        const viewportArea = Math.max(1, viewportWidth * viewportHeight)
        const firstVisiblePage = Number(location?.pageIndex) === 0
        const coverLike =
          firstVisiblePage &&
          visibleText.length <= 40 &&
          dominantVisibleImage &&
          dominantVisibleImage.screenArea / viewportArea >= 0.45 &&
          (
            /cover|frontcover|coverpage|cubierta|portada/i.test(dominantVisibleImage.src) ||
            (Number.isFinite(dominantVisibleImage.aspect) && dominantVisibleImage.aspect >= 1.15 && dominantVisibleImage.aspect <= 1.85)
          )
        if (coverLike) {
          coverLikePages.push({
            pageIndex: location?.pageIndex,
            href: location?.href,
            visibleText,
            dominantVisibleImage,
            images,
          })
        }
        documents.push({
          index: content.index,
          textLength: text.length,
          textSample: text.slice(0, 220),
          images,
        })
      }
      return {
        location,
        shellCoverVisible: document.body.dataset.navicShellCoverVisible === 'true',
        documents,
        coverImageHits,
        coverLikePages,
      }
    })

    const pages = []
    const coverImageHits = []
    const coverLikePages = []
    let snapshot = await collectCoverScanSnapshot()
    if (snapshot.coverLikePages.length > 0) {
      throw new Error(`Expected first WebView-visible page not to be the EPUB cover; observed ${JSON.stringify(snapshot.coverLikePages[0])}`)
    }
    const maxTurns = Math.max(1, Number(snapshot.location?.pageCount || 0) + 12)
    for (let turn = 0; turn < maxTurns; turn += 1) {
      if (!snapshot.location) throw new Error('Missing location during full EPUB traversal')
      pages.push({
        location: snapshot.location,
        shellCoverVisible: snapshot.shellCoverVisible,
        documents: snapshot.documents,
      })
      coverImageHits.push(...snapshot.coverImageHits)
      coverLikePages.push(...snapshot.coverLikePages)
      if (snapshot.location.pageIndex % 50 === 0) {
        console.log(`epub-full-traversal progress: ${snapshot.location.pageIndex + 1}/${snapshot.location.pageCount}`)
      }
      if (snapshot.location.pageIndex >= snapshot.location.pageCount - 1) break
      const previousPageIndex = snapshot.location.pageIndex
      await page.evaluate(async () => {
        const renderer = document.querySelector('foliate-view')?.renderer
        renderer?.removeAttribute?.('animated')
        await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      await page.waitForFunction(previous => {
        const messages = (window.__navicReaderPostedMessages || [])
          .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
        const current = messages.at(-1)
        return current && current.pageIndex > previous
      }, previousPageIndex, { timeout: 8000 })
      snapshot = await collectLocationSnapshot()
    }
    const trace = await page.evaluate(() => window.__navicReaderTrace || [])
    const result = {
      pages,
      coverImageHits,
      coverLikePages,
      traceSummary: {
        rawRelocations: trace.filter(event => event?.type === 'relocate:raw').length,
        committedLocations: trace.filter(event => event?.type === 'location:post').length,
        suppressedCoverDocuments: trace.filter(event => event?.type === 'cover:document-suppressed').length,
      },
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-full-traversal.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertFullEpubTraversal(result)
    console.log(`reader harness epub-full-traversal passed: ${outputPath}`)
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

if (mode === 'epub-texture-page-turns') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-texture-page-turns mode requires --fixture <path-to-epub>')
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
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
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
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 15000 })
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })

    const collectState = async label => page.evaluate(sampleLabel => {
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      const layer = document.querySelector('[data-navic-surface-paper-texture-layer="true"]')
      const borderLayer = document.querySelector('[data-navic-surface-page-border-overlay-layer="true"]')
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const location = messages.at(-1) || null
      return {
        label: sampleLabel,
        timestamp: performance.now(),
        viewportWidth: Number(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0),
        viewportHeight: Number(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0),
        location,
        href: location?.href || '',
        pageIndex: location?.pageIndex,
        pageCount: location?.pageCount,
        position: Number(renderer?.containerPosition),
        rendererPage: Number(renderer?.page),
        rendererPages: Number(renderer?.pages),
        textureKey: document.body.dataset.navicSurfacePaperTextureKey || '',
        textureBackgroundPosition: layer?.style.backgroundPosition || '',
        computedTextureBackgroundPosition: layer ? getComputedStyle(layer).backgroundPosition : '',
        borderBackgroundPosition: borderLayer?.style.backgroundPosition || '',
        computedBorderBackgroundPosition: borderLayer ? getComputedStyle(borderLayer).backgroundPosition : '',
      }
    }, label)

    const dragProbe = await page.evaluate(async delta => {
      const sample = label => {
        const view = document.querySelector('foliate-view')
        const renderer = view?.renderer
        const layer = document.querySelector('[data-navic-surface-paper-texture-layer="true"]')
        const messages = (window.__navicReaderPostedMessages || [])
          .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
        const location = messages.at(-1) || null
        return {
          label,
          timestamp: performance.now(),
          viewportWidth: Number(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0),
          viewportHeight: Number(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0),
          location,
          href: location?.href || '',
          pageIndex: location?.pageIndex,
          pageCount: location?.pageCount,
          position: Number(renderer?.containerPosition),
          textureKey: document.body.dataset.navicSurfacePaperTextureKey || '',
          textureBackgroundPosition: layer?.style.backgroundPosition || '',
          computedTextureBackgroundPosition: layer ? getComputedStyle(layer).backgroundPosition : '',
        }
      }
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      if (!renderer) throw new Error('Missing foliate renderer for texture drag probe')
      const samples = [sample('before')]
      renderer.scrollBy(delta, 0)
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      samples.push(sample('after-scrollBy'))
      renderer.scrollBy(-delta, 0)
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      return { name: 'renderer-scrollBy-forward', direction: 'forward', samples, delta }
    }, Math.min(160, Math.max(80, Math.round(phoneViewport.viewport.width * 0.32))))

    const bridgeProbe = async name => {
      const samples = [await collectState('before')]
      await page.evaluate(() => {
        const renderer = document.querySelector('foliate-view')?.renderer
        renderer?.setAttribute?.('animated', '')
        window.__navicTexturePageTurnPromise = window.NavicReaderBridge.dispatch({ type: 'nextPage' })
        return true
      })
      for (const delay of [40, 80, 120, 180, 260]) {
        await page.waitForTimeout(delay)
        samples.push(await collectState(`t+${delay}`))
      }
      await page.evaluate(async () => {
        await window.__navicTexturePageTurnPromise
      })
      await page.waitForTimeout(260)
      samples.push(await collectState('settled'))
      return { name, direction: 'forward', samples }
    }

    const bridgeNextProbe = await bridgeProbe('bridge-next-animated')

    const boundarySamples = []
    let bridgeBoundaryProbe = null
    let previous = await collectState('boundary-search-start')
    for (let index = 0; index < 180; index += 1) {
      await page.evaluate(async () => {
        document.querySelector('foliate-view')?.renderer?.removeAttribute?.('animated')
        await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      await page.waitForFunction(beforePageIndex => {
        const messages = (window.__navicReaderPostedMessages || [])
          .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
        const current = messages.at(-1)
        return current && current.pageIndex > beforePageIndex
      }, previous.pageIndex, { timeout: 8000 })
      const current = await collectState(`boundary-search-${index}`)
      if (current.href && previous.href && current.href !== previous.href) {
        boundarySamples.push(previous, current)
        await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'previousPage' }))
        await page.waitForFunction(afterPageIndex => {
          const messages = (window.__navicReaderPostedMessages || [])
            .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
          const currentLocation = messages.at(-1)
          return currentLocation && currentLocation.pageIndex < afterPageIndex
        }, current.pageIndex, { timeout: 8000 })
        boundarySamples.push(await collectState('boundary-returned-before'))
        bridgeBoundaryProbe = await bridgeProbe('bridge-next-boundary-animated')
        break
      }
      previous = current
    }

    const result = {
      probes: [dragProbe, bridgeNextProbe, bridgeBoundaryProbe].filter(Boolean),
      boundarySamples,
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-texture-page-turns.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertTextureTracksRealPageTurnSamples(result)
    console.log(`reader harness epub-texture-page-turns passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-texture-frontmatter-transition') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-texture-frontmatter-transition mode requires --fixture <path-to-epub>')
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
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
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
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 15000 })
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })
    const collectState = async label => page.evaluate(sampleLabel => {
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      const layer = document.querySelector('[data-navic-surface-paper-texture-layer="true"]')
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const location = messages.at(-1) || null
      const viewportWidth = Number(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0)
      const viewportHeight = Number(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0)
      const visibleDocuments = []
      const contents = view?.renderer?.getContents?.() || []
      for (const content of contents) {
        const doc = content?.doc
        if (!doc?.body) continue
        const frameRect = doc.defaultView?.frameElement?.getBoundingClientRect?.()
        const screenRectFor = rect => ({
          left: (frameRect?.left || 0) + rect.left,
          top: (frameRect?.top || 0) + rect.top,
          right: (frameRect?.left || 0) + rect.right,
          bottom: (frameRect?.top || 0) + rect.bottom,
          width: rect.width,
          height: rect.height,
        })
        const intersectionArea = rect => {
          const left = Math.max(0, rect.left)
          const right = Math.min(viewportWidth, rect.right)
          const top = Math.max(0, rect.top)
          const bottom = Math.min(viewportHeight, rect.bottom)
          return Math.max(0, right - left) * Math.max(0, bottom - top)
        }
        const visibleText = Array.from(doc.body.querySelectorAll('body *'))
          .filter(element => {
            const rect = screenRectFor(element.getBoundingClientRect())
            const area = intersectionArea(rect)
            const elementArea = Math.max(1, rect.width * rect.height)
            return area > 48 && area / elementArea > 0.15
          })
          .map(element => element.textContent?.replace(/\s+/g, ' ').trim() || '')
          .filter(Boolean)
          .join(' ')
          .replace(/\s+/g, ' ')
          .trim()
        if (visibleText) {
          visibleDocuments.push({
            index: content.index,
            href: content.section?.href || '',
            visibleText: visibleText.slice(0, 600),
          })
        }
      }
      const visibleText = visibleDocuments
        .map(document => document.visibleText)
        .join(' ')
        .replace(/\s+/g, ' ')
        .trim()
      return {
        label: sampleLabel,
        timestamp: performance.now(),
        viewportWidth,
        viewportHeight,
        location,
        href: location?.href || '',
        pageIndex: location?.pageIndex,
        pageCount: location?.pageCount,
        position: Number(renderer?.containerPosition),
        rendererPage: Number(renderer?.page),
        rendererPages: Number(renderer?.pages),
        textureKey: document.body.dataset.navicSurfacePaperTextureKey || '',
        textureBackgroundPosition: layer?.style.backgroundPosition || '',
        computedTextureBackgroundPosition: layer ? getComputedStyle(layer).backgroundPosition : '',
        visibleText: visibleText.slice(0, 1000),
        visibleDocuments,
      }
    }, label)

    const firstState = await collectState('author-search-start')
    const authorSearch = await page.evaluate(async () => {
      const query = "Author's Note"
      const before = (window.__navicReaderPostedMessages || []).length
      await window.NavicReaderBridge.dispatch({ type: 'search', query })
      const messages = window.__navicReaderPostedMessages || []
      return messages.slice(before).find(message => message?.type === 'searchResults' && message.query === query) || null
    })
    const authorSearchResult =
      authorSearch?.results?.find(result =>
        (result?.cfi || result?.href) &&
        /Author's Note/i.test(result?.excerpt || '') &&
        !/AUTHOR’S NOTE/.test(result?.excerpt || '')
      ) ||
      authorSearch?.results?.find(result => result?.cfi || result?.href)
    if (!authorSearchResult) {
      throw new Error(`Expected search to locate the visible AUTHOR'S NOTE heading; observed ${JSON.stringify(authorSearch)}`)
    }
    await page.evaluate(async result => {
      document.querySelector('foliate-view')?.renderer?.removeAttribute?.('animated')
      if (result.cfi) {
        await window.NavicReaderBridge.dispatch({ type: 'goToCfi', cfi: result.cfi })
      } else {
        await window.NavicReaderBridge.dispatch({ type: 'goToHref', href: result.href })
      }
    }, authorSearchResult)
    await page.waitForTimeout(500)
    const authorNoteState = await collectState('author-note-search-hit')
    if (!Number.isFinite(Number(authorNoteState?.pageIndex))) {
      throw new Error(`Expected AUTHOR'S NOTE search locator to produce a page location; observed ${JSON.stringify(authorNoteState)}`)
    }
    await page.evaluate(async () => {
      document.querySelector('foliate-view')?.renderer?.removeAttribute?.('animated')
      await window.NavicReaderBridge.dispatch({ type: 'previousPage' })
    })
    await page.waitForTimeout(420)
    const beforeAuthorNoteState = await collectState('author-note-boundary-before')
    if (Number(beforeAuthorNoteState?.pageIndex) >= Number(authorNoteState?.pageIndex)) {
      throw new Error(
        `Expected previousPage to return before AUTHOR'S NOTE before probing; ` +
        `before=${beforeAuthorNoteState?.pageIndex}/${beforeAuthorNoteState?.pageCount} ` +
        `author=${authorNoteState?.pageIndex}/${authorNoteState?.pageCount}`
      )
    }

    const bridgeProbe = async name => {
      const samples = [await collectState('before')]
      await page.evaluate(() => {
        const renderer = document.querySelector('foliate-view')?.renderer
        renderer?.setAttribute?.('animated', '')
        window.__navicTextureFrontmatterTurnPromise = window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      for (const delay of [40, 80, 120, 180, 260]) {
        await page.waitForTimeout(delay)
        samples.push(await collectState(`t+${delay}`))
      }
      await page.evaluate(async () => {
        await window.__navicTextureFrontmatterTurnPromise
      })
      await page.waitForTimeout(260)
      samples.push(await collectState('settled'))
      return { name, direction: 'forward', samples }
    }

    const dragProbe = async (name, direction = 'forward') => {
      const traceStart = await page.evaluate(() => (window.__navicReaderTrace || []).length)
      const samples = [await collectState('before')]
      await page.evaluate(() => {
        const renderer = document.querySelector('foliate-view')?.renderer
        renderer?.setAttribute?.('animated', '')
      })
      const viewport = page.viewportSize() || phoneViewport.viewport
      const reverse = direction === 'backward'
      const dragPromise = performReaderTouchDrag(page, {
        startX: viewport.width * (reverse ? 0.18 : 0.82),
        startY: viewport.height * 0.52,
        endX: viewport.width * (reverse ? 0.82 : 0.18),
        endY: viewport.height * 0.52,
        durationMs: 520,
        steps: 10,
      })
      for (const delay of [40, 80, 120, 180, 260, 360]) {
        await page.waitForTimeout(delay)
        samples.push(await collectState(`t+${delay}`))
      }
      await dragPromise
      await page.waitForTimeout(520)
      samples.push(await collectState('settled'))
      const trace = await page.evaluate(start => (window.__navicReaderTrace || []).slice(start), traceStart)
      return { name, direction, samples, trace }
    }

    const dragAuthorEntryProbe = await dragProbe('drag-author-note-boundary')
    if (Number(dragAuthorEntryProbe.samples.at(-1)?.pageIndex) < Number(authorNoteState?.pageIndex)) {
      throw new Error(
        `Expected drag-author-note-boundary probe to settle on the visible AUTHOR'S NOTE page; ` +
        `settled=${dragAuthorEntryProbe.samples.at(-1)?.pageIndex}/${dragAuthorEntryProbe.samples.at(-1)?.pageCount} ` +
        `author=${authorNoteState?.pageIndex}/${authorNoteState?.pageCount}`
      )
    }
    const dragPostAuthorBoundaryProbe = await dragProbe('drag-post-author-note-boundary')
    const dragAuthorEntryPage = Number(dragAuthorEntryProbe.samples.at(-1)?.pageIndex)
    const dragPostAuthorPage = Number(dragPostAuthorBoundaryProbe.samples.at(-1)?.pageIndex)
    if (!Number.isFinite(dragPostAuthorPage) || dragPostAuthorPage <= dragAuthorEntryPage) {
      throw new Error(
        `Expected drag-post-author-note-boundary probe to advance after the AUTHOR'S NOTE page; ` +
        `before=${dragAuthorEntryPage}/${dragAuthorEntryProbe.samples.at(-1)?.pageCount} ` +
        `settled=${dragPostAuthorBoundaryProbe.samples.at(-1)?.pageIndex}/${dragPostAuthorBoundaryProbe.samples.at(-1)?.pageCount}`
      )
    }
    const dragReverseAuthorBoundaryProbe = await dragProbe('drag-reverse-author-note-boundary', 'backward')
    const dragReverseAuthorPage = Number(dragReverseAuthorBoundaryProbe.samples.at(-1)?.pageIndex)
    if (!Number.isFinite(dragReverseAuthorPage) || dragReverseAuthorPage >= dragPostAuthorPage) {
      throw new Error(
        `Expected drag-reverse-author-note-boundary probe to move back across the AUTHOR'S NOTE boundary; ` +
        `before=${dragPostAuthorPage}/${dragPostAuthorBoundaryProbe.samples.at(-1)?.pageCount} ` +
        `settled=${dragReverseAuthorBoundaryProbe.samples.at(-1)?.pageIndex}/${dragReverseAuthorBoundaryProbe.samples.at(-1)?.pageCount}`
      )
    }
    const postAuthorProbe = await bridgeProbe('frontmatter-post-author-note')
    const result = {
      probes: [dragAuthorEntryProbe, dragPostAuthorBoundaryProbe, dragReverseAuthorBoundaryProbe, postAuthorProbe],
      authorBoundarySearch: {
        first: firstState,
        searchResult: authorSearchResult,
        before: beforeAuthorNoteState,
        author: authorNoteState,
      },
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const dragProbeMissingDirection = [dragAuthorEntryProbe, dragPostAuthorBoundaryProbe, dragReverseAuthorBoundaryProbe]
      .find(probe => !probe.trace?.some(event => event?.type === 'texture:drag-direction'))
    if (dragProbeMissingDirection) {
      throw new Error(`Expected ${dragProbeMissingDirection.name} to emit texture:drag-direction before checking texture movement`)
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-texture-frontmatter-transition.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertTextureTracksRealPageTurnSamples(result)
    assertTextureTracePayloadsTrackTurnDirection(result.trace)
    console.log(`reader harness epub-texture-frontmatter-transition passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'pdf-smoke') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('pdf-smoke mode requires --fixture <path-to-pdf>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.pdf'
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
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
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
          theme: 'dark',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 20000 })
    if (await page.evaluate(() => document.body.dataset.navicShellCoverVisible === 'true')) {
      await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
      await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })
    }
    await page.waitForTimeout(1000)

    const currentLocation = async () => page.evaluate(() => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      return messages.at(-1) || null
    })

    const measurePageBounds = async () => {
      const buffer = await page.screenshot({ type: 'png' })
      const dataUrl = `data:image/png;base64,${buffer.toString('base64')}`
      return page.evaluate(async imageUrl => {
        const image = new Image()
        image.src = imageUrl
        await image.decode()
        const canvas = document.createElement('canvas')
        canvas.width = image.naturalWidth
        canvas.height = image.naturalHeight
        const context = canvas.getContext('2d', { willReadFrequently: true })
        context.drawImage(image, 0, 0)
        const { width, height } = canvas
        const data = context.getImageData(0, 0, width, height).data
        let left = width
        let right = -1
        let top = height
        let bottom = -1
        let hits = 0
        for (let y = 0; y < height; y += 1) {
          for (let x = 0; x < width; x += 1) {
            const offset = (y * width + x) * 4
            const red = data[offset]
            const green = data[offset + 1]
            const blue = data[offset + 2]
            const alpha = data[offset + 3]
            const brightPagePixel = alpha > 240 && red > 210 && green > 210 && blue > 210
            if (!brightPagePixel) continue
            hits += 1
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
          }
        }
        if (right < left || bottom < top) {
          return { width, height, coverage: 0, centerError: Number.NaN }
        }
        const boxWidth = right - left + 1
        const boxHeight = bottom - top + 1
        const leftMargin = left
        const rightMargin = width - right - 1
        return {
          width,
          height,
          left,
          right,
          top,
          bottom,
          boxWidth,
          boxHeight,
          leftMargin,
          rightMargin,
          centerError: (left + right) / 2 - width / 2,
          coverage: hits / (width * height),
        }
      }, dataUrl)
    }

    const initialLocation = await currentLocation()
    const initialPageBounds = await measurePageBounds()
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForFunction(previousPageIndex => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const current = messages.at(-1)
      return current && current.pageIndex > previousPageIndex
    }, initialLocation.pageIndex, { timeout: 10000 })
    const afterNextLocation = await currentLocation()
    await page.evaluate(() => {
      window.__navicPdfDoubleNext = Promise.all([
        window.NavicReaderBridge.dispatch({ type: 'nextPage' }),
        window.NavicReaderBridge.dispatch({ type: 'nextPage' }),
      ])
      return true
    })
    await page.evaluate(async () => window.__navicPdfDoubleNext)
    await page.waitForFunction(previousPageIndex => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const current = messages.at(-1)
      return current && current.pageIndex > previousPageIndex
    }, afterNextLocation.pageIndex, { timeout: 10000 })
    const afterDoubleNextLocation = await currentLocation()
    const result = {
      initialLocation,
      initialPageBounds,
      afterNextLocation,
      afterDoubleNextLocation,
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'pdf-smoke.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertPdfSmoke(result)
    console.log(`reader harness pdf-smoke passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'pdf-image-settings') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('pdf-image-settings mode requires --fixture <path-to-pdf>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.pdf'
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
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
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
          theme: 'dark',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          pdfFitMode: 'height',
          pdfCropBorders: true,
          pdfPageGapPercent: 12,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 20000 })
    await page.waitForTimeout(1000)

    const pageBounds = await page.screenshot({ type: 'png' }).then(buffer => {
      const dataUrl = `data:image/png;base64,${buffer.toString('base64')}`
      return page.evaluate(async imageUrl => {
        const image = new Image()
        image.src = imageUrl
        await image.decode()
        const canvas = document.createElement('canvas')
        canvas.width = image.naturalWidth
        canvas.height = image.naturalHeight
        const context = canvas.getContext('2d', { willReadFrequently: true })
        context.drawImage(image, 0, 0)
        const { width, height } = canvas
        const data = context.getImageData(0, 0, width, height).data
        let left = width
        let right = -1
        let top = height
        let bottom = -1
        let hits = 0
        for (let y = 0; y < height; y += 1) {
          for (let x = 0; x < width; x += 1) {
            const offset = (y * width + x) * 4
            const red = data[offset]
            const green = data[offset + 1]
            const blue = data[offset + 2]
            const alpha = data[offset + 3]
            const brightPagePixel = alpha > 240 && red > 210 && green > 210 && blue > 210
            if (!brightPagePixel) continue
            hits += 1
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
          }
        }
        return {
          width,
          height,
          left,
          right,
          top,
          bottom,
          coverage: hits / (width * height),
        }
      }, dataUrl)
    })

    const rendererState = await page.evaluate(() => {
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      const pageGapPx = Number.parseFloat(renderer?.getAttribute('data-navic-pdf-page-gap-px') || '')
      return {
        zoom: renderer?.getAttribute('zoom'),
        cropBorders: renderer?.getAttribute('data-navic-pdf-crop-borders'),
        pageGapPx,
        rootPageGap: getComputedStyle(document.documentElement).getPropertyValue('--reader-pdf-page-gap'),
      }
    })

    const result = {
      pageBounds,
      rendererState,
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'pdf-image-settings.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertPdfImageSettings(result)
    console.log(`reader harness pdf-image-settings passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'pdf-fast-sequential-turns') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('pdf-fast-sequential-turns mode requires --fixture <path-to-pdf>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.pdf'
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
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
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
          theme: 'dark',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 20000 })
    if (await page.evaluate(() => document.body.dataset.navicShellCoverVisible === 'true')) {
      await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
      await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })
    }
    await page.waitForTimeout(500)

    const currentLocation = async () => page.evaluate(() => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      return messages.at(-1) || null
    })

    const initialLocation = await currentLocation()
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForTimeout(1000)
    const finalLocation = await currentLocation()
    const result = {
      initialLocation,
      finalLocation,
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'pdf-fast-sequential-turns.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertPdfFastSequentialTurns(result)
    console.log(`reader harness pdf-fast-sequential-turns passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-shell-cover') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-shell-cover mode requires --fixture <path-to-epub>')
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
      const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
      const shellVisible = () => document.body.dataset.navicShellCoverVisible === 'true'
      const location = () => {
        const messages = window.__navicReaderPostedMessages || []
        return [...messages].reverse().find(message => message?.type === 'locationChanged') || null
      }
      const initialShellVisible = shellVisible()
      const initialLocation = location()
      await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      await wait(900)
      const afterNextShellVisible = shellVisible()
      const afterNextLocation = location()
      await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      await wait(900)
      const afterSecondNextShellVisible = shellVisible()
      const afterSecondNextLocation = location()
      await window.NavicReaderBridge.dispatch({ type: 'previousPage' })
      await wait(900)
      const afterSecondPreviousShellVisible = shellVisible()
      const afterSecondPreviousLocation = location()
      await window.NavicReaderBridge.dispatch({ type: 'previousPage' })
      await wait(900)
      const afterPreviousShellVisible = shellVisible()
      const afterPreviousLocation = location()
      return {
        initialShellVisible,
        initialLocation,
        afterNextShellVisible,
        afterNextLocation,
        afterSecondNextShellVisible,
        afterSecondNextLocation,
        afterSecondPreviousShellVisible,
        afterSecondPreviousLocation,
        afterPreviousShellVisible,
        afterPreviousLocation,
      }
    })
    assertNoConsoleErrors(errors)
    assertShellCoverDoesNotNavigateWebViewToCover(result)
    console.log(`reader harness epub-shell-cover passed: ${JSON.stringify(result)}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-external-shell-cover') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-external-shell-cover mode requires --fixture <path-to-epub>')
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
        externalShellCover: true,
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
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 15000 })
    const result = await page.evaluate(async () => {
      const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
      const shellVisible = () => document.body.dataset.navicShellCoverVisible === 'true'
      const shellLayerExists = () => Boolean(document.querySelector('[data-navic-shell-cover-layer="true"]'))
      const location = () => {
        const messages = window.__navicReaderPostedMessages || []
        return [...messages].reverse().find(message => message?.type === 'locationChanged') || null
      }
      const initial = {
        shellVisible: shellVisible(),
        shellLayerExists: shellLayerExists(),
        location: location(),
      }
      await window.NavicReaderBridge.dispatch({ type: 'previousPage' })
      await wait(900)
      const afterPrevious = {
        shellVisible: shellVisible(),
        shellLayerExists: shellLayerExists(),
        location: location(),
      }
      return { initial, afterPrevious }
    })
    assertNoConsoleErrors(errors)
    if (result.initial.shellVisible || result.initial.shellLayerExists) {
      throw new Error(`Expected external shell-cover mode not to show a WebView shell cover initially; observed ${JSON.stringify(result.initial)}`)
    }
    if (result.initial.location?.pageIndex !== 0) {
      throw new Error(`Expected external shell-cover mode to start WebView on first readable pageIndex 0; observed ${JSON.stringify(result.initial.location)}`)
    }
    if (result.afterPrevious.shellVisible || result.afterPrevious.shellLayerExists) {
      throw new Error(`Expected previousPage in external shell-cover mode not to show the WebView fallback cover; observed ${JSON.stringify(result.afterPrevious)}`)
    }
    console.log(`reader harness epub-external-shell-cover passed: ${JSON.stringify(result)}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'css-smoke') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('css-smoke mode requires --fixture <path-to-epub>')
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
          tapZone: 'disabled',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const contents = document.querySelector('foliate-view')?.renderer?.getContents?.() || []
      return contents.some(content => content?.doc?.body)
    }, null, { timeout: 10000 })
    await page.waitForTimeout(500)
    const result = await page.evaluate(async () => {
      const view = document.querySelector('foliate-view')
      const contents = view?.renderer?.getContents?.() || []
      const content = contents.find(entry => entry?.doc?.body)
      if (!content?.doc) throw new Error('Missing loaded EPUB content document')
      const doc = content.doc
      const win = doc.defaultView
      const probe = doc.createElement('section')
      probe.setAttribute('data-navic-css-smoke', 'true')
      probe.innerHTML = `
        <p data-navic-css-smoke-paragraph="true">Navic paragraph spacing probe one.</p>
        <p data-navic-css-smoke-paragraph="true">Navic paragraph spacing probe two.</p>
        <a data-navic-link-kind="text" data-navic-css-smoke-link="true" href="#navic-css-smoke-target">Probe link</a>
        <a data-navic-link-kind="media" data-navic-css-smoke-media-link="true" href="#navic-css-smoke-target">
          <img data-navic-css-smoke-media-image="true" alt="" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16'%3E%3Crect width='16' height='16' fill='white'/%3E%3C/svg%3E">
        </a>
        <span id="navic-css-smoke-target">target</span>
      `
      doc.body.prepend(probe)
      await new Promise(resolve => win.requestAnimationFrame(resolve))

      const htmlStyle = win.getComputedStyle(doc.documentElement)
      const bodyStyle = win.getComputedStyle(doc.body)
      const paragraph = doc.querySelector('[data-navic-css-smoke-paragraph="true"]')
      const paragraphStyle = win.getComputedStyle(paragraph)
      const textLink = doc.querySelector('[data-navic-css-smoke-link="true"]')
      const textLinkStyle = win.getComputedStyle(textLink)
      const textLinkAfterStyle = win.getComputedStyle(textLink, '::after')
      const mediaLink = doc.querySelector('[data-navic-css-smoke-media-link="true"]')
      const mediaLinkAfterStyle = win.getComputedStyle(mediaLink, '::after')
      const image = doc.querySelector('[data-navic-css-smoke-media-image="true"]')
      const imageMixBlendModeBefore = win.getComputedStyle(image).mixBlendMode
      const postedMessages = () => Array.isArray(win.parent.__navicReaderPostedMessages)
        ? win.parent.__navicReaderPostedMessages
        : []
      const contentTapHandledSources = messages => messages
        .filter(message => message?.type === 'readerContentTapHandled')
        .map(message => message?.source || '')
      const clickOptionsFor = element => {
        const rect = element.getBoundingClientRect()
        return {
          bubbles: true,
          cancelable: true,
          button: 0,
          clientX: Math.round(rect.left + rect.width / 2),
          clientY: Math.round(rect.top + rect.height / 2),
        }
      }
      const touchPointFor = element => {
        const rect = element.getBoundingClientRect()
        const clientX = Math.round(rect.left + rect.width / 2)
        const clientY = Math.round(rect.top + rect.height / 2)
        return {
          identifier: 1,
          target: element,
          clientX,
          clientY,
          pageX: clientX + win.scrollX,
          pageY: clientY + win.scrollY,
          screenX: clientX,
          screenY: clientY,
        }
      }
      const rootPointFor = element => {
        const rect = element.getBoundingClientRect()
        const frameRect = win.frameElement?.getBoundingClientRect?.()
        return {
          x: Math.round((frameRect?.left || 0) + rect.left + rect.width / 2),
          y: Math.round((frameRect?.top || 0) + rect.top + rect.height / 2),
        }
      }
      const dispatchTouchEvent = (element, type, touches, changedTouches) => {
        const event = new win.Event(type, { bubbles: true, cancelable: true })
        Object.defineProperty(event, 'touches', { value: touches, configurable: true })
        Object.defineProperty(event, 'targetTouches', { value: touches, configurable: true })
        Object.defineProperty(event, 'changedTouches', { value: changedTouches, configurable: true })
        element.dispatchEvent(event)
      }
      const dispatchSyntheticTouchTap = element => {
        const touch = touchPointFor(element)
        dispatchTouchEvent(element, 'touchstart', [touch], [touch])
        dispatchTouchEvent(element, 'touchend', [], [touch])
      }
      const traceLengthBeforeImageClicks = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.length
        : 0
      const postedLengthBeforeImageClicks = postedMessages().length
      image.dispatchEvent(new win.MouseEvent('click', clickOptionsFor(image)))
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      const imageOverlayDatasetAfterFirstClick = image.dataset.navicSepiaOverlay || ''
      const imageMixBlendModeAfterFirstClick = win.getComputedStyle(image).mixBlendMode
      await new Promise(resolve => win.setTimeout(resolve, 700))
      image.dispatchEvent(new win.MouseEvent('click', clickOptionsFor(image)))
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      const imageOverlayDatasetAfterSecondClick = image.dataset.navicSepiaOverlay || ''
      const imageMixBlendModeAfterSecondClick = win.getComputedStyle(image).mixBlendMode
      const traceAfterImageClicks = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.slice(traceLengthBeforeImageClicks)
        : []
      const postedAfterImageClicks = postedMessages().slice(postedLengthBeforeImageClicks)
      const traceLengthBeforeTextLinkClick = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.length
        : 0
      const postedLengthBeforeTextLinkClick = postedMessages().length
      win.__navicLastMediaTapHandledAt = 0
      win.__navicSuppressNextMediaClickUntil = 0
      textLink.dispatchEvent(new win.MouseEvent('click', clickOptionsFor(textLink)))
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const traceAfterTextLinkClick = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.slice(traceLengthBeforeTextLinkClick)
        : []
      const postedAfterTextLinkClick = postedMessages().slice(postedLengthBeforeTextLinkClick)
      const imageContentTapHandledSources = contentTapHandledSources(postedAfterImageClicks)
      const textLinkContentTapHandledSources = contentTapHandledSources(postedAfterTextLinkClick)
      const postedLengthBeforeImageTouch = postedMessages().length
      dispatchSyntheticTouchTap(image)
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const postedAfterImageTouch = postedMessages().slice(postedLengthBeforeImageTouch)
      const postedLengthBeforeTextLinkTouch = postedMessages().length
      dispatchSyntheticTouchTap(textLink)
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const postedAfterTextLinkTouch = postedMessages().slice(postedLengthBeforeTextLinkTouch)
      const imageTouchContentTapHandledSources = contentTapHandledSources(postedAfterImageTouch)
      const textLinkTouchContentTapHandledSources = contentTapHandledSources(postedAfterTextLinkTouch)
      const imageRootPoint = rootPointFor(image)
      const textLinkRootPoint = rootPointFor(textLink)
      const paragraphRootPoint = rootPointFor(paragraph)
      const imageNativeCenterContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        imageRootPoint.x,
        imageRootPoint.y
      )
      const nativeCoordinateScale = Number(win.parent.devicePixelRatio || win.devicePixelRatio || 1)
      const nativeViewWidth = Math.round((win.parent.visualViewport?.width || win.parent.innerWidth) * nativeCoordinateScale)
      const nativeViewHeight = Math.round((win.parent.visualViewport?.height || win.parent.innerHeight) * nativeCoordinateScale)
      const imageNativeScaledContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        Math.round(imageRootPoint.x * nativeCoordinateScale),
        Math.round(imageRootPoint.y * nativeCoordinateScale),
        nativeViewWidth,
        nativeViewHeight
      )
      const textLinkNativeCenterContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        textLinkRootPoint.x,
        textLinkRootPoint.y
      )
      const textLinkNativeScaledContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        Math.round(textLinkRootPoint.x * nativeCoordinateScale),
        Math.round(textLinkRootPoint.y * nativeCoordinateScale),
        nativeViewWidth,
        nativeViewHeight
      )
      const paragraphNativeCenterContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        paragraphRootPoint.x,
        paragraphRootPoint.y
      )
      const paragraphNativeScaledContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        Math.round(paragraphRootPoint.x * nativeCoordinateScale),
        Math.round(paragraphRootPoint.y * nativeCoordinateScale),
        nativeViewWidth,
        nativeViewHeight
      )
      const recentTouchContentHitAfterRemoval = async (html, selector) => {
        const wrapper = doc.createElement('span')
        wrapper.setAttribute('data-navic-css-smoke-transient-touch', 'true')
        wrapper.innerHTML = html
        doc.body.prepend(wrapper)
        await new Promise(resolve => win.requestAnimationFrame(resolve))
        const target = wrapper.querySelector(selector)
        if (!target) throw new Error(`Missing transient target for ${selector}`)
        const rootPoint = rootPointFor(target)
        dispatchSyntheticTouchTap(target)
        wrapper.remove()
        await new Promise(resolve => win.setTimeout(resolve, 25))
        return win.parent.NavicReaderBridge.readerContentActionAtPoint(
          Math.round(rootPoint.x * nativeCoordinateScale),
          Math.round(rootPoint.y * nativeCoordinateScale),
          nativeViewWidth,
          nativeViewHeight
        )
      }
      const imageRecentTouchContentHitAfterRemoval = await recentTouchContentHitAfterRemoval(
        `<img alt="" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='20'%3E%3Crect width='20' height='20' fill='white'/%3E%3C/svg%3E">`,
        'img'
      )
      const textLinkRecentTouchContentHitAfterRemoval = await recentTouchContentHitAfterRemoval(
        `<a href="#navic-css-smoke-target">Transient link</a>`,
        'a'
      )
      const surfaceTextureLayer = document.querySelector('[data-navic-surface-paper-texture-layer="true"]')
      const surfaceBorderLayer = document.querySelector('[data-navic-surface-page-border-overlay-layer="true"]')
      const surfaceTextureStyle = surfaceTextureLayer ? getComputedStyle(surfaceTextureLayer) : null
      const surfaceBorderStyle = surfaceBorderLayer ? getComputedStyle(surfaceBorderLayer) : null
      return {
        contentDocumentCount: contents.length,
        theme: doc.documentElement.dataset.navicReaderTheme || '',
        htmlBackground: htmlStyle.backgroundColor,
        bodyBackground: bodyStyle.backgroundColor,
        paragraphSpacingVariable: bodyStyle.getPropertyValue('--reader-paragraph-spacing') ||
          htmlStyle.getPropertyValue('--reader-paragraph-spacing'),
        paragraphMarginBottom: paragraphStyle.marginBlockEnd || paragraphStyle.marginBottom,
        textLinkColor: textLinkStyle.color,
        textLinkDecoration: textLinkStyle.textDecorationLine,
        textLinkAfterContent: textLinkAfterStyle.content,
        textLinkAfterVerticalAlign: textLinkAfterStyle.verticalAlign,
        mediaLinkAfterContent: mediaLinkAfterStyle.content,
        imageMixBlendModeBefore,
        imageOverlayDatasetAfterFirstClick,
        imageMixBlendModeAfterFirstClick,
        imageOverlayDatasetAfterSecondClick,
        imageMixBlendModeAfterSecondClick,
        imageOverlayTraceCount: traceAfterImageClicks.filter(event => event?.type === 'image:sepia-overlay').length,
        imageNavigationTraceCount: traceAfterImageClicks.filter(event => event?.type === 'link:navigate').length,
        imageContentTapHandledCount: imageContentTapHandledSources.length,
        imageContentTapHandledSources,
        imageTouchContentTapHandledCount: imageTouchContentTapHandledSources.length,
        imageTouchContentTapHandledSources,
        textLinkNavigationTraceCount: traceAfterTextLinkClick.filter(event => event?.type === 'link:navigate').length,
        textLinkHitMissTraceCount: traceAfterTextLinkClick.filter(event => event?.type === 'link:text-hit-miss').length,
        textLinkContentTapHandledCount: textLinkContentTapHandledSources.length,
        textLinkContentTapHandledSources,
        textLinkTouchContentTapHandledCount: textLinkTouchContentTapHandledSources.length,
        textLinkTouchContentTapHandledSources,
        imageNativeCenterContentHit,
        imageNativeScaledContentHit,
        textLinkNativeCenterContentHit,
        textLinkNativeScaledContentHit,
        paragraphNativeCenterContentHit,
        paragraphNativeScaledContentHit,
        imageRecentTouchContentHitAfterRemoval,
        textLinkRecentTouchContentHitAfterRemoval,
        surfaceTextureBackgroundImage: surfaceTextureStyle?.backgroundImage || '',
        surfaceTextureOpacity: surfaceTextureStyle?.opacity || '',
        surfaceBorderBackgroundImage: surfaceBorderStyle?.backgroundImage || '',
        surfaceBorderOpacity: surfaceBorderStyle?.opacity || '',
        surfaceTextureAsset: document.body.dataset.navicSurfacePaperTextureAsset || '',
        surfaceBorderAsset: document.body.dataset.navicSurfaceBorderOverlayAsset || '',
      }
    })
    const trace = await page.evaluate(() => window.__navicReaderTrace || [])
    const postedMessages = await page.evaluate(() => window.__navicReaderPostedMessages || [])
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'css-smoke.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
      trace,
      postedMessages,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertTraceType(trace, 'runtime:ready')
    assertTraceType(trace, 'texture:update')
    assertRendererCssSmoke(result)
    console.log(`reader harness css-smoke passed: ${outputPath}`)
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

if (!bridgeText.includes('readerTrace') || !helperText.includes('__navicReaderTrace')) {
  console.error('Reader harness requires readerTrace instrumentation in the reader runtime and helpers')
  process.exit(1)
}

console.log('reader harness smoke passed')
