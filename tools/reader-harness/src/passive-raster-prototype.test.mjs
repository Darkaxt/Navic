import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { createServer } from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import test from 'node:test'
import { chromium } from 'playwright'

const repoRoot = path.resolve(import.meta.dirname, '../../..')
const readerAssetRoot = path.join(
  repoRoot,
  'composeApp/src/androidMain/assets/reader',
)
const assetRoot = path.join(readerAssetRoot, 'passive-raster-prototype')
const scriptPath = path.join(assetRoot, 'passive-raster-prototype.js')
const sessionPath = path.join(assetRoot, 'passive-raster-foliate-session.js')
const coreSessionPath = path.join(assetRoot, 'synthetic-raster-foliate-session.js')
const productionSessionPath = path.join(assetRoot, 'production-raster-foliate-session.js')
const renderProfilePath = path.join(readerAssetRoot, 'navic-reader-render-profile.js')
const operationRuntimePath = path.join(assetRoot, 'bounded-operation-runtime.js')
const liveScriptPath = path.join(assetRoot, 'live-raster-fixture.js')
const htmlPath = path.join(assetRoot, 'index.html')
const liveHtmlPath = path.join(assetRoot, 'live-fixture.html')

const mimeType = filePath => {
  if (filePath.endsWith('.html')) return 'text/html; charset=utf-8'
  if (filePath.endsWith('.js')) return 'text/javascript; charset=utf-8'
  if (filePath.endsWith('.json')) return 'application/json; charset=utf-8'
  if (filePath.endsWith('.css')) return 'text/css; charset=utf-8'
  return 'application/octet-stream'
}

const startAssetServer = async () => {
  const server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url, 'http://127.0.0.1')
      const prefix = '/assets/reader/'
      if (!url.pathname.startsWith(prefix)) {
        response.writeHead(404).end()
        return
      }
      const relative = decodeURIComponent(url.pathname.slice(prefix.length))
      const filePath = path.resolve(readerAssetRoot, relative)
      if (!filePath.startsWith(path.resolve(readerAssetRoot) + path.sep)) {
        response.writeHead(403).end()
        return
      }
      const metadata = await stat(filePath)
      if (!metadata.isFile()) throw new Error('not-file')
      response.writeHead(200, {
        'content-type': mimeType(filePath),
        'cache-control': 'no-store',
      })
      response.end(await readFile(filePath))
    } catch {
      response.writeHead(404).end()
    }
  })
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const address = server.address()
  return {
    server,
    readerEntrypoint: `http://127.0.0.1:${address.port}/assets/reader/index.html`,
    passiveEntrypoint: `http://127.0.0.1:${address.port}/assets/reader/passive-raster-prototype/index.html`,
    liveEntrypoint: `http://127.0.0.1:${address.port}/assets/reader/passive-raster-prototype/live-fixture.html`,
  }
}

const openHarnessPage = async (browser, entrypoint, apiName, viewport) => {
  const page = await browser.newPage({ viewport })
  await page.goto(entrypoint)
  await page.waitForFunction(name => globalThis[name]?.ready === true, apiName)
  return page
}

const runOperation = async (page, apiName, method, input) => {
  const started = await page.evaluate(
    ({ apiName, method, input }) => globalThis[apiName][method](input),
    { apiName, method, input },
  )
  assert.equal(typeof started?.operationId, 'string')
  await page.waitForFunction(({ apiName, operationId }) => {
    const result = globalThis[apiName].readOperationResult(operationId)
    return result?.state === 'complete' || result?.state === 'failed'
  }, { apiName, operationId: started.operationId })
  const outcome = await page.evaluate(
    ({ apiName, operationId }) => globalThis[apiName].readOperationResult(operationId, true),
    { apiName, operationId: started.operationId },
  )
  assert.equal(outcome.state, 'complete', outcome.failure ?? method)
  return outcome.value
}

const issueManifest = (page, input) => runOperation(
  page,
  'NavicLiveRasterFixture',
  'startLiveManifest',
  input,
)
const captureManifest = (page, issued, passiveCommitSequence) => runOperation(
  page,
  'NavicPassiveRasterPrototype',
  'startCapture',
  {
    manifest: issued.manifest,
    captureTarget: issued.captureTarget,
    passiveCommitSequence,
  },
)

const assertReceiptMatchesManifest = (receipt, manifest) => {
  assert.equal(receipt.echoedManifestSequence, manifest.manifestSequence)
  assert.equal(receipt.echoedCaptureEpoch, manifest.captureEpoch)
  assert.equal(receipt.echoedLiveFoliateSessionId, manifest.liveFoliateSessionId)
  assert.equal(
    receipt.echoedPublicationSessionGeneration,
    manifest.publicationSessionGeneration,
  )
  assert.equal(receipt.echoedDestinationCommitToken, manifest.destinationCommitToken)
  assert.equal(receipt.observedCaptureTarget, manifest.opaqueCaptureTarget)
  assert.equal(receipt.observedVisualPageOrdinal, manifest.visualPageOrdinal)
  assert.equal(receipt.observedRasterProfileKey, manifest.rasterProfileKey)
  assert.equal(receipt.observedPaginationFingerprint, manifest.paginationFingerprint)
  assert.equal(receipt.observedLayoutFingerprint, manifest.layoutFingerprint)
  assert.equal(receipt.observedDecorationFingerprint, manifest.decorationFingerprint)
  assert.deepEqual(
    receipt.observedViewportAndCaptureGeometry,
    manifest.viewportAndCaptureGeometry,
  )
  assert.equal(receipt.echoedRasterGeneration, manifest.rasterGeneration)
}

test('live and passive Foliate sessions preserve synthetic parity across the Stage 2 matrix', async () => {
  const { server, passiveEntrypoint, liveEntrypoint } = await startAssetServer()
  const browser = await chromium.launch({ headless: true })
  let livePage
  let passivePage
  try {
    livePage = await openHarnessPage(
      browser,
      liveEntrypoint,
      'NavicLiveRasterFixture',
      { width: 800, height: 1200 },
    )
    passivePage = await openHarnessPage(
      browser,
      passiveEntrypoint,
      'NavicPassiveRasterPrototype',
      { width: 800, height: 1200 },
    )
    const matrix = [
      {
        name: 'portrait',
        viewport: { width: 800, height: 1200 },
        profileKey: 'portrait-day',
        targetKey: 'section-0-page-0',
      },
      {
        name: 'landscape-spread',
        viewport: { width: 1200, height: 800 },
        profileKey: 'landscape-day',
        targetKey: 'section-0-page-1',
      },
      {
        name: 'theme-typography-replacement',
        viewport: { width: 1200, height: 800 },
        profileKey: 'landscape-night-large',
        targetKey: 'section-1-page-0',
      },
      {
        name: 'chapter-boundary',
        viewport: { width: 1200, height: 800 },
        profileKey: 'landscape-night-large',
        targetKey: 'section-2-page-0',
      },
    ]

    let sequence = 0
    for (const scenario of matrix) {
      await livePage.setViewportSize(scenario.viewport)
      await passivePage.setViewportSize(scenario.viewport)
      const issued = await issueManifest(livePage, {
        profileKey: scenario.profileKey,
        targetKey: scenario.targetKey,
        rasterGeneration: sequence + 1,
      })
      const receipt = await captureManifest(passivePage, issued, ++sequence)
      if (receipt.observedPaginationFingerprint !== issued.manifest.paginationFingerprint) {
        const inspect = page => page.evaluate(() => {
          const view = document.querySelector('foliate-view')
          const renderer = view?.renderer
          const exact = renderer?.exactTextPagePosition?.() ?? null
          const content = renderer?.getContents?.().find(entry => entry.index === exact?.index)
          const bodyStyle = content?.doc?.defaultView?.getComputedStyle(content.doc.body)
          const rect = view?.getBoundingClientRect()
          return {
            exact,
            maxColumnCount: renderer?.getAttribute('max-column-count'),
            flow: renderer?.getAttribute('flow'),
            viewportWidth: Math.round((rect?.width ?? 0) * (devicePixelRatio || 1)),
            viewportHeight: Math.round((rect?.height ?? 0) * (devicePixelRatio || 1)),
            fontSize: bodyStyle?.fontSize,
            lineHeight: bodyStyle?.lineHeight,
          }
        })
        console.error(JSON.stringify({
          scenario: scenario.name,
          live: await inspect(livePage),
          passive: await inspect(passivePage),
        }))
      }
      assertReceiptMatchesManifest(receipt, issued.manifest)
      assert.equal(receipt.passiveCommitSequence, sequence, scenario.name)
    }

    const originalSession = await issueManifest(livePage, {
      profileKey: 'landscape-day',
      targetKey: 'section-1-page-1',
      rasterGeneration: 20,
    })
    await livePage.close()
    livePage = await openHarnessPage(
      browser,
      liveEntrypoint,
      'NavicLiveRasterFixture',
      { width: 1200, height: 800 },
    )
    const replacementSession = await issueManifest(livePage, {
      profileKey: 'landscape-day',
      targetKey: 'section-1-page-1',
      rasterGeneration: 21,
    })
    assert.notEqual(
      replacementSession.manifest.liveFoliateSessionId,
      originalSession.manifest.liveFoliateSessionId,
    )
    const replacementReceipt = await captureManifest(passivePage, replacementSession, ++sequence)
    assertReceiptMatchesManifest(replacementReceipt, replacementSession.manifest)
  } finally {
    await livePage?.close().catch(() => {})
    await passivePage?.close().catch(() => {})
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
})

test('passive receipt reports runtime observations instead of forged manifest expectations', async () => {
  const { server, passiveEntrypoint, liveEntrypoint } = await startAssetServer()
  const browser = await chromium.launch({ headless: true })
  let livePage
  let passivePage
  try {
    livePage = await openHarnessPage(
      browser,
      liveEntrypoint,
      'NavicLiveRasterFixture',
      { width: 800, height: 1200 },
    )
    passivePage = await openHarnessPage(
      browser,
      passiveEntrypoint,
      'NavicPassiveRasterPrototype',
      { width: 800, height: 1200 },
    )
    const first = await issueManifest(livePage, {
      profileKey: 'portrait-day',
      targetKey: 'section-0-page-0',
      rasterGeneration: 1,
    })
    const second = await issueManifest(livePage, {
      profileKey: 'portrait-day',
      targetKey: 'section-1-page-0',
      rasterGeneration: 2,
    })
    const forgedFingerprint = {
      ...first,
      manifest: {
        ...first.manifest,
        paginationFingerprint: 'forged-pagination-fingerprint',
        layoutFingerprint: 'forged-layout-fingerprint',
        decorationFingerprint: 'forged-decoration-fingerprint',
      },
    }
    const observedFingerprint = await captureManifest(passivePage, forgedFingerprint, 1)
    assert.notEqual(
      observedFingerprint.observedPaginationFingerprint,
      forgedFingerprint.manifest.paginationFingerprint,
    )
    assert.notEqual(
      observedFingerprint.observedLayoutFingerprint,
      forgedFingerprint.manifest.layoutFingerprint,
    )
    assert.notEqual(
      observedFingerprint.observedDecorationFingerprint,
      forgedFingerprint.manifest.decorationFingerprint,
    )

    const mismatchedTarget = await runOperation(
      passivePage,
      'NavicPassiveRasterPrototype',
      'startCapture',
      {
        manifest: first.manifest,
        captureTarget: second.captureTarget,
        passiveCommitSequence: 2,
      },
    )
    assert.notEqual(mismatchedTarget.observedCaptureTarget, first.manifest.opaqueCaptureTarget)
    assert.notEqual(mismatchedTarget.observedVisualPageOrdinal, first.manifest.visualPageOrdinal)

    await passivePage.setViewportSize({ width: 1200, height: 800 })
    const staleOrientation = await captureManifest(passivePage, first, 3)
    assert.notDeepEqual(
      staleOrientation.observedViewportAndCaptureGeometry,
      first.manifest.viewportAndCaptureGeometry,
    )
  } finally {
    await livePage?.close().catch(() => {})
    await passivePage?.close().catch(() => {})
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
})

test('passive synthetic assets host Foliate without a semantic or Android bridge channel', async () => {
  const [
    html,
    script,
    session,
    coreSession,
    productionSession,
    renderProfile,
    liveHtml,
    liveScript,
  ] = await Promise.all([
    readFile(htmlPath, 'utf8'),
    readFile(scriptPath, 'utf8'),
    readFile(sessionPath, 'utf8'),
    readFile(coreSessionPath, 'utf8'),
    readFile(productionSessionPath, 'utf8'),
    readFile(renderProfilePath, 'utf8'),
    readFile(liveHtmlPath, 'utf8'),
    readFile(liveScriptPath, 'utf8'),
  ])
  const source = [
    html,
    script,
    session,
    coreSession,
    productionSession,
    renderProfile,
  ].join('\n')

  assert.match(html, /Synthetic raster page/)
  assert.match(html, /type="module"/)
  assert.match(coreSession, /\.\.\/vendor\/foliate-js\/view\.js/)
  assert.match(coreSession, /document\.createElement\('foliate-view'\)/)
  assert.match(coreSession, /createSyntheticPublication/)
  assert.match(coreSession, /\.commitTextPage\(/)
  assert.match(coreSession, /\.validateTextPageCommit\(/)
  assert.doesNotMatch(coreSession, /this\.view\.goTo\(resolvedTarget\.href\)/)
  assert.match(coreSession, /exactTextPagePosition\(\)/)
  assert.match(coreSession, /--synthetic-raster-sentinel/)
  assert.match(coreSession, /linear-gradient/)
  assert.match(script, /startCapture/)
  assert.match(script, /readOperationResult/)
  assert.match(script, /Object\.freeze/)
  assert.doesNotMatch(source, /startLiveManifest/)
  assert.doesNotMatch(source, /issueLiveManifest/)
  assert.doesNotMatch(source, /NavicLiveRasterFixture/)
  assert.match(liveHtml, /live-raster-fixture\.js/)
  assert.match(liveScript, /startLiveManifest/)
  assert.match(liveScript, /NavicLiveRasterFixture/)
  for (const forbidden of [
    'postMessage',
    'NavicAndroidBridge',
    'NavicReaderBridge',
    'locationChanged',
    'visibleTextRange',
    'overlayFragment',
    'selectionChanged',
    'WordSync',
    'localStorage',
    'sessionStorage',
    'indexedDB',
  ]) {
    assert.equal(source.includes(forbidden), false, forbidden)
  }
})

test('cancelled exclusive operations remain occupied until abort drain completes', async () => {
  const { createBoundedOperationRuntime } = await import(pathToFileURL(operationRuntimePath).href)
  const operations = createBoundedOperationRuntime()
  let abortObserved = false
  const first = operations.startExclusive(async signal => {
    await new Promise((resolve, reject) => {
      const abort = () => {
        abortObserved = true
        reject(signal.reason)
      }
      if (signal.aborted) abort()
      else signal.addEventListener('abort', abort, { once: true })
    })
  })

  assert.equal(typeof first?.operationId, 'string')
  assert.equal(operations.startExclusive(async () => 'overlap'), null)
  assert.equal(operations.cancel(first.operationId), true)
  assert.equal(operations.read(first.operationId)?.state, 'cancelling')

  for (let turn = 0; turn < 20 && operations.read(first.operationId)?.state !== 'cancelled'; turn += 1) {
    await new Promise(resolve => setImmediate(resolve))
  }

  assert.equal(abortObserved, true)
  assert.equal(operations.read(first.operationId)?.state, 'cancelled')
  assert.equal(typeof operations.startExclusive(async () => 'replacement')?.operationId, 'string')
})

test('production Foliate publication replacement recreates the view and failed open retries', async () => {
  const { server, passiveEntrypoint } = await startAssetServer()
  const browser = await chromium.launch({ headless: true })
  let page
  try {
    page = await openHarnessPage(
      browser,
      passiveEntrypoint,
      'NavicPassiveRasterPrototype',
      { width: 1200, height: 800 },
    )
    const result = await page.evaluate(async () => {
      const { ProductionRasterFoliateSessionCore } = await import(
        './production-raster-foliate-session.js'
      )
      const prototype = customElements.get('foliate-view').prototype
      const originalOpen = prototype.open
      const originalClose = prototype.close
      const opens = []
      const closes = []
      const attempts = new Map()
      prototype.open = function (url) {
        opens.push({ view: this, url })
        const attempt = (attempts.get(url) || 0) + 1
        attempts.set(url, attempt)
        return url === 'publication-retry' && attempt === 1
          ? Promise.reject(new Error('synthetic-open-failure'))
          : Promise.resolve()
      }
      prototype.close = function () {
        closes.push(this)
      }
      try {
        const host = document.createElement('section')
        document.body.append(host)
        const replacementCore = new ProductionRasterFoliateSessionCore(host)
        await replacementCore.open('publication-a')
        const firstView = replacementCore.view
        await replacementCore.open('publication-b')
        const replacementView = replacementCore.view

        const retryCore = new ProductionRasterFoliateSessionCore(host)
        let firstFailed = false
        try {
          await retryCore.open('publication-retry')
        } catch {
          firstFailed = true
        }
        let retrySucceeded = true
        try {
          await retryCore.open('publication-retry')
        } catch {
          retrySucceeded = false
        }
        return {
          replaced: firstView !== replacementView,
          firstViewClosed: closes.includes(firstView),
          firstFailed,
          retrySucceeded,
          retryAttempts: attempts.get('publication-retry'),
        }
      } finally {
        prototype.open = originalOpen
        prototype.close = originalClose
      }
    })

    assert.equal(result.replaced, true)
    assert.equal(result.firstViewClosed, true)
    assert.equal(result.firstFailed, true)
    assert.equal(result.retrySucceeded, true)
    assert.equal(result.retryAttempts, 2)
  } finally {
    await page?.close().catch(() => {})
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
})

test('raster asset verification admits only the latest request for the current decorations', async () => {
  const { server, passiveEntrypoint } = await startAssetServer()
  const browser = await chromium.launch({ headless: true })
  let page
  try {
    page = await openHarnessPage(
      browser,
      passiveEntrypoint,
      'NavicPassiveRasterPrototype',
      { width: 1200, height: 800 },
    )
    const result = await page.evaluate(async () => {
      const { waitForReaderRasterAssets } = await import(
        '../navic-reader-render-profile.js'
      )
      const originalImage = window.Image
      const pending = []
      class DeferredImage {
        set src(value) {
          this.url = value
          pending.push(this)
        }
      }
      window.Image = DeferredImage
      const layer = document.createElement('div')
      layer.dataset.navicMovingPagePaperTextureLayer = 'true'
      document.body.append(layer)
      try {
        layer.style.backgroundImage = 'url("./old-raster.png")'
        const oldRequest = waitForReaderRasterAssets(document)
        layer.style.backgroundImage = 'url("./current-raster.png")'
        const currentRequest = waitForReaderRasterAssets(document)
        pending.find(image => image.url.endsWith('/current-raster.png')).onload()
        const currentAccepted = await currentRequest
        pending.find(image => image.url.endsWith('/old-raster.png')).onload()
        const oldAccepted = await oldRequest

        const changedRequest = waitForReaderRasterAssets(document)
        layer.style.backgroundImage = 'url("./changed-without-request.png")'
        pending
          .filter(image => image.url.endsWith('/current-raster.png'))
          .at(-1)
          .onload()
        const changedAccepted = await changedRequest
        return { currentAccepted, oldAccepted, changedAccepted }
      } finally {
        layer.remove()
        window.Image = originalImage
      }
    })

    assert.deepEqual(result, {
      currentAccepted: true,
      oldAccepted: false,
      changedAccepted: false,
    })
  } finally {
    await page?.close().catch(() => {})
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
})

test('production passive capture renders fingerprinted decorations and observes aborts', async () => {
  const { server, passiveEntrypoint } = await startAssetServer()
  const browser = await chromium.launch({ headless: true })
  let page
  try {
    page = await openHarnessPage(
      browser,
      passiveEntrypoint,
      'NavicPassiveRasterPrototype',
      { width: 1200, height: 800 },
    )
    const result = await page.evaluate(async () => {
      const { ProductionRasterFoliateSessionCore } = await import(
        './production-raster-foliate-session.js'
      )
      const { readerRealizedRasterObservation } = await import(
        '../navic-reader-render-profile.js'
      )
      const { stableHash } = await import('../navic-reader-identity.js')
      const prototype = customElements.get('foliate-view').prototype
      const originalOpen = prototype.open
      const originalClose = prototype.close
      prototype.open = function () {
        const frame = document.createElement('iframe')
        frame.hidden = true
        document.body.append(frame)
        const contentDocument = frame.contentDocument
        const heading = contentDocument.createElement('h1')
        heading.textContent = 'Passive content'
        const paragraph = contentDocument.createElement('p')
        paragraph.textContent = 'Realized proof.'
        contentDocument.body.replaceChildren(heading, paragraph)
        Object.defineProperty(contentDocument, 'fonts', { value: { status: 'loaded' } })
        const renderer = document.createElement('div')
        const receipt = Object.freeze({ token: 'passive-receipt' })
        renderer.setStyles = () => {}
        renderer.render = () => {}
        renderer.commitTextPage = async (index, pageIndex) => Object.freeze({
          status: 'committed',
          receipt,
          position: Object.freeze({ index, pageIndex, pageCount: 3 }),
        })
        renderer.validateTextPageCommit = value => value === receipt
        renderer.exactTextPagePosition = () => Object.freeze({
          index: 0,
          pageIndex: 0,
          pageCount: 3,
        })
        renderer.getContents = () => [{ index: 0, doc: contentDocument }]
        this.renderer = renderer
        this.book = { sections: [{ href: 'chapter.xhtml' }] }
        return Promise.resolve()
      }
      prototype.close = function () {
        this.renderer = null
      }
      const profileFor = async (core, target) => {
        target.rasterProfileKey = 'bootstrap-profile'
        const controller = new AbortController()
        const bootstrap = core.commitOpaqueTarget(
          JSON.stringify(target),
          target.rasterProfileKey,
          controller.signal,
        ).catch(() => null)
        requestAnimationFrame(() => controller.abort())
        await bootstrap
        const realized = readerRealizedRasterObservation(core.view, target, document)
        if (!realized) throw new Error('realized-decoration-profile-unavailable')
        target.rasterProfileKey = realized.rasterProfileKey
        target.paginationFingerprint = realized.paginationFingerprint
        target.layoutFingerprint = realized.layoutFingerprint
        target.decorationFingerprint = realized.decorationFingerprint
        return {
          decorationFingerprint: realized.decorationFingerprint,
          opaqueRasterProfileKey: realized.rasterProfileKey,
          rasterProfileKey: String(realized.rasterProfileKey),
        }
      }
      const targetFor = readerSettings => ({
        publicationUrl: 'publication-decoration-parity',
        spineIndex: 0,
        href: 'chapter.xhtml',
        chapterPageIndex: 0,
        chapterPageCount: 3,
        visualPageOrdinal: 0,
        render: {},
        readerSettings,
        layoutMode: 'spread',
        layoutPages: [],
        viewportWidth: 1200,
        viewportHeight: 800,
      })
      try {
        const host = document.getElementById('passive-raster-stage')
        const core = new ProductionRasterFoliateSessionCore(host)
        const defaults = targetFor({ theme: 'day' })
        const defaultProfile = await profileFor(core, defaults)
        defaults.rasterProfileKey = defaultProfile.opaqueRasterProfileKey
        const defaultObservation = await core.commitOpaqueTarget(
          JSON.stringify(defaults),
          defaultProfile.rasterProfileKey,
        )
        const planned = targetFor({ theme: 'day' })
        const plannedPaginationFingerprint = 'live-pagination-plan'
        const plannedLayoutFingerprint = stableHash(JSON.stringify({
          render: planned.render,
          mode: planned.layoutMode,
          pages: planned.layoutPages,
          viewportWidth: planned.viewportWidth,
          viewportHeight: planned.viewportHeight,
        }))
        const plannedDecorationFingerprint = stableHash(JSON.stringify({
          theme: planned.readerSettings.theme,
          paperTextureEnabled: true,
          pageEdgesEnabled: true,
          paperStainsEnabled: true,
          coverBackdropEnabled: true,
        }))
        Object.assign(planned, {
          profileAuthority: 'passive-realized-v1',
          paginationFingerprint: plannedPaginationFingerprint,
          layoutFingerprint: plannedLayoutFingerprint,
          decorationFingerprint: plannedDecorationFingerprint,
          rasterProfileKey: stableHash(JSON.stringify({
            publicationUrl: planned.publicationUrl,
            paginationFingerprint: plannedPaginationFingerprint,
            layoutFingerprint: plannedLayoutFingerprint,
            decorationFingerprint: plannedDecorationFingerprint,
            viewportWidth: planned.viewportWidth,
            viewportHeight: planned.viewportHeight,
          })),
        })
        const plannedObservation = await core.commitOpaqueTarget(
          JSON.stringify(planned),
          String(planned.rasterProfileKey),
        )
        const plannedRealized = readerRealizedRasterObservation(core.view, planned, document)
        if (!plannedRealized) throw new Error('planned-realized-profile-unavailable')
        const defaultLayers = {
          paper: document.querySelector('[data-navic-moving-page-paper-texture-layer="true"]') != null,
          edges: document.querySelector('[data-navic-moving-page-border-overlay-layer="true"]') != null,
          stains: document.querySelector('[data-navic-moving-page-stain-overlay-layer="true"]') != null,
          gutter: document.querySelector('[data-navic-surface-spread-gutter-overlay-layer="true"]') != null,
        }

        const disabled = targetFor({
          theme: 'day',
          paperTextureEnabled: false,
          pageEdgesEnabled: false,
          paperStainsEnabled: false,
        })
        const disabledProfile = await profileFor(core, disabled)
        disabled.rasterProfileKey = disabledProfile.opaqueRasterProfileKey
        await core.commitOpaqueTarget(JSON.stringify(disabled), disabledProfile.rasterProfileKey)
        const disabledLayerCount = document.querySelectorAll([
          '[data-navic-moving-page-paper-texture-layer="true"]',
          '[data-navic-moving-page-border-overlay-layer="true"]',
          '[data-navic-moving-page-stain-overlay-layer="true"]',
          '[data-navic-surface-spread-gutter-overlay-layer="true"]',
        ].join(',')).length

        const abortController = new AbortController()
        const abortTask = core.commitOpaqueTarget(
          JSON.stringify(defaults),
          defaultProfile.rasterProfileKey,
          abortController.signal,
        ).then(
          () => 'completed',
          failure => failure?.name || 'failed',
        )
        requestAnimationFrame(() => abortController.abort())
        const abortOutcome = await abortTask
        return {
          defaultLayers,
          defaultDecorationFingerprint: defaultObservation.decorationFingerprint,
          expectedDecorationFingerprint: defaultProfile.decorationFingerprint,
          plannedObservation,
          plannedExpected: {
            rasterProfileKey: plannedRealized.rasterProfileKey,
            paginationFingerprint: plannedRealized.paginationFingerprint,
            layoutFingerprint: plannedRealized.layoutFingerprint,
            decorationFingerprint: plannedRealized.decorationFingerprint,
          },
          plannedManifestProfile: {
            rasterProfileKey: planned.rasterProfileKey,
            paginationFingerprint: planned.paginationFingerprint,
            layoutFingerprint: planned.layoutFingerprint,
            decorationFingerprint: planned.decorationFingerprint,
          },
          disabledLayerCount,
          abortOutcome,
        }
      } finally {
        prototype.open = originalOpen
        prototype.close = originalClose
      }
    })

    assert.deepEqual(result.defaultLayers, {
      paper: true,
      edges: true,
      stains: true,
      gutter: true,
    })
    assert.equal(
      result.defaultDecorationFingerprint,
      result.expectedDecorationFingerprint,
    )
    assert.deepEqual({
      rasterProfileKey: result.plannedObservation.rasterProfileKey,
      paginationFingerprint: result.plannedObservation.paginationFingerprint,
      layoutFingerprint: result.plannedObservation.layoutFingerprint,
      decorationFingerprint: result.plannedObservation.decorationFingerprint,
    }, result.plannedExpected)
    assert.notDeepEqual({
      rasterProfileKey: result.plannedObservation.rasterProfileKey,
      paginationFingerprint: result.plannedObservation.paginationFingerprint,
      layoutFingerprint: result.plannedObservation.layoutFingerprint,
      decorationFingerprint: result.plannedObservation.decorationFingerprint,
    }, result.plannedManifestProfile)
    assert.equal(result.disabledLayerCount, 0)
    assert.equal(result.abortOutcome, 'AbortError')
  } finally {
    await page?.close().catch(() => {})
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
})

test('production passive real Foliate matches live realized content decorations assets and pixels', async () => {
  const { server, readerEntrypoint, passiveEntrypoint } = await startAssetServer()
  const browser = await chromium.launch({ headless: true })
  const viewport = { width: 1200, height: 800 }
  let livePage
  let passivePage
  const screenshotHash = value => createHash('sha256').update(value).digest('hex')
  const runScenario = (page, modulePath, input) => page.evaluate(
    async ({ modulePath, input }) => {
      const { runReaderRenderParityScenario } = await import(modulePath)
      return runReaderRenderParityScenario(input)
    },
    { modulePath, input },
  )
  try {
    livePage = await browser.newPage({ viewport })
    await livePage.goto(readerEntrypoint, { waitUntil: 'domcontentloaded' })
    passivePage = await openHarnessPage(
      browser,
      passiveEntrypoint,
      'NavicPassiveRasterPrototype',
      viewport,
    )
    const scenarios = [
      {
        name: 'default-decorations',
        settings: {
          direction: 'ltr',
          theme: 'sepia',
          fontSource: 'system',
          fontFamily: 'Georgia, serif',
          fontSizePercent: 118,
          lineHeight: 1.62,
          paragraphSpacingPercent: 34,
          paperTextureEnabled: true,
          pageEdgesEnabled: true,
          paperStainsEnabled: true,
        },
      },
      {
        name: 'disabled-decorations',
        settings: {
          direction: 'ltr',
          theme: 'sepia',
          fontSource: 'system',
          fontFamily: 'Georgia, serif',
          fontSizePercent: 118,
          lineHeight: 1.62,
          paragraphSpacingPercent: 34,
          paperTextureEnabled: false,
          pageEdgesEnabled: false,
          paperStainsEnabled: false,
        },
      },
    ]
    const results = []
    for (const scenario of scenarios) {
      const live = await runScenario(
        livePage,
        './passive-raster-prototype/render-parity-fixture.js',
        { mode: 'live', settings: scenario.settings },
      )
      const livePixels = screenshotHash(await livePage.screenshot())
      const passive = await runScenario(
        passivePage,
        './render-parity-fixture.js',
        { mode: 'passive', target: live.target },
      )
      const passivePixels = screenshotHash(await passivePage.screenshot())

      assert.deepEqual(passive.documentState, live.documentState, scenario.name)
      assert.equal(passive.observation.paginationFingerprint, live.observation.paginationFingerprint)
      assert.equal(passive.observation.layoutFingerprint, live.observation.layoutFingerprint)
      assert.equal(passive.observation.decorationFingerprint, live.observation.decorationFingerprint)
      assert.deepEqual(passive.observation.loadedAssetUrls, live.observation.loadedAssetUrls)
      assert.equal(passivePixels, livePixels, `${scenario.name} pixels`)
      assert.deepEqual(
        {
          publicationDirection: live.documentState.publicationDirection,
          rootDirection: live.documentState.rootDirection,
          bodyDirection: live.documentState.bodyDirection,
          inlineNormalized: live.documentState.inlineNormalized,
          inlineFontSizePriority: live.documentState.inlineFontSizePriority,
          inlineFontFamilyPriority: live.documentState.inlineFontFamilyPriority,
          lineFragmentsNormalized: live.documentState.lineFragmentsNormalized,
          chapterOpeningCapped: live.documentState.chapterOpeningCapped,
        },
        {
          publicationDirection: 'ltr',
          rootDirection: 'ltr',
          bodyDirection: 'ltr',
          inlineNormalized: true,
          inlineFontSizePriority: 'important',
          inlineFontFamilyPriority: 'important',
          lineFragmentsNormalized: true,
          chapterOpeningCapped: true,
        },
      )
      assert.match(
        live.documentState.mergedFragmentText,
        /The public parity chapter joins fragmented prose without losing words\./,
      )
      assert.ok(live.documentState.looseParagraphCount >= 2)
      assert.ok(live.documentState.paragraphBlockCount >= 1)
      assert.ok(live.documentState.contentGeometry.proseWidth > 0)
      if (scenario.name === 'default-decorations') {
        assert.ok(live.observation.loadedAssetUrls.length > 0)
      } else {
        assert.equal(live.observation.loadedAssetUrls.length, 0)
      }
      results.push({
        decorationFingerprint: live.observation.decorationFingerprint,
        pixels: livePixels,
      })
    }
    assert.notEqual(results[0].decorationFingerprint, results[1].decorationFingerprint)
    assert.notEqual(results[0].pixels, results[1].pixels)
  } finally {
    await livePage?.close().catch(() => {})
    await passivePage?.close().catch(() => {})
    await browser.close()
    await new Promise(resolve => server.close(resolve))
  }
})
