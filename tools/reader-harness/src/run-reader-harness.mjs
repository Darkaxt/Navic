import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import {
  assertBridgePostType,
  assertFirstVisibleLocationStartsAtZero,
  assertForwardPageIndexesDoNotRegress,
  assertFullEpubTraversal,
  assertNoConsoleErrors,
  assertNoConsecutiveDuplicateLocations,
  assertNoConsecutiveDuplicateVisiblePageLabels,
  assertRendererCssSmoke,
  assertShellCoverDoesNotNavigateWebViewToCover,
  assertSurfaceTextureTracksForwardContentMovement,
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

    const collectSnapshot = async () => page.evaluate(() => {
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
    let snapshot = await collectSnapshot()
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
      await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
      await page.waitForFunction(previous => {
        const messages = (window.__navicReaderPostedMessages || [])
          .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
        const current = messages.at(-1)
        return current && current.pageIndex > previous
      }, previousPageIndex, { timeout: 8000 })
      snapshot = await collectSnapshot()
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
      const clickOptions = {
        bubbles: true,
        cancelable: true,
        button: 0,
        clientX: Math.round(win.innerWidth / 2),
        clientY: Math.round(win.innerHeight / 2),
      }
      image.dispatchEvent(new win.MouseEvent('click', clickOptions))
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      const imageOverlayDatasetAfterFirstClick = image.dataset.navicSepiaOverlay || ''
      const imageMixBlendModeAfterFirstClick = win.getComputedStyle(image).mixBlendMode
      await new Promise(resolve => win.setTimeout(resolve, 700))
      image.dispatchEvent(new win.MouseEvent('click', clickOptions))
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      const imageOverlayDatasetAfterSecondClick = image.dataset.navicSepiaOverlay || ''
      const imageMixBlendModeAfterSecondClick = win.getComputedStyle(image).mixBlendMode
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
        surfaceTextureBackgroundImage: surfaceTextureStyle?.backgroundImage || '',
        surfaceTextureOpacity: surfaceTextureStyle?.opacity || '',
        surfaceBorderBackgroundImage: surfaceBorderStyle?.backgroundImage || '',
        surfaceBorderOpacity: surfaceBorderStyle?.opacity || '',
        surfaceTextureAsset: document.body.dataset.navicSurfacePaperTextureAsset || '',
        surfaceBorderAsset: document.body.dataset.navicSurfaceBorderOverlayAsset || '',
      }
    })
    const trace = await page.evaluate(() => window.__navicReaderTrace || [])
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'css-smoke.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
      trace,
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
