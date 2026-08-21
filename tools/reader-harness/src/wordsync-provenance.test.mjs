import assert from 'node:assert/strict'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { startReaderAssetServer } from './serve-reader-assets.mjs'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')

let server
let browser

test.before(async () => {
  server = await startReaderAssetServer({ repoRoot })
  browser = await chromium.launch()
})

test.after(async () => {
  await browser?.close()
  await server?.close()
})

const newReaderPage = async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
  const moduleAvailable = await page.evaluate(() => import('/navic-reader-wordsync-provenance.js')
    .then(() => true)
    .catch(() => false))
  assert.equal(moduleAvailable, true, 'the focused WordSync provenance module must be installed')
  return page
}

const runScenario = (page, scenario) => page.evaluate(async scenarioName => {
  const api = await import('/navic-reader-wordsync-provenance.js')
  const locationApi = await import('/navic-reader-location.js')
  const raw = '﻿<html xmlns="http://www.w3.org/1999/xhtml"><head><style>hidden words</style><script>ignored words</script></head><body><p>ASCII &amp; Café 😀</p><p>Don’t <span>stop</span> now</p></body></html>'
  const extracted = '﻿ ASCII & Café 😀. Don’t stop now.'
  const href = 'OPS/Text/chapter.xhtml'
  const prefaceHref = 'OPS/Text/preface.xhtml'
  const encoder = new TextEncoder()
  const taggedHash = async bytes => {
    const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
    return `sha256:${Array.from(digest, byte => byte.toString(16).padStart(2, '0')).join('')}`
  }
  const sourceHash = await taggedHash(encoder.encode(raw))
  const extractedTextHash = await taggedHash(encoder.encode(extracted))
  const descriptor = {
    id: 'chapter-raw-1',
    href,
    spineIndex: 2,
    sourceHash,
    extractedTextHash,
    byteLength: encoder.encode(extracted).length,
    tokenCount: 5,
  }
  const createDocument = () => new DOMParser().parseFromString(raw, 'application/xhtml+xml')
  const items = new Map([
    ['preface', { href: prefaceHref }],
    ['chapter', { href }],
  ])
  const loads = []
  const book = {
    resources: {
      spine: [
        { idref: 'preface' },
        { idref: 'missing-manifest-item' },
        { idref: 'chapter' },
      ],
      getItemByID: id => items.get(id) || null,
    },
    sections: [
      { id: prefaceHref },
      { id: href },
    ],
    async loadBlob(requestHref) {
      loads.push(requestHref)
      if (requestHref !== href) throw new Error('unexpected href')
      return new Blob([encoder.encode(raw)], { type: 'application/xhtml+xml' })
    },
  }
  const rawFragment = (overrides = {}) => ({
    resourceHref: 'Audio/chapter.mp3',
    coordinateMode: 'wordsync-v1-extracted-utf8',
    textHref: href,
    rawProvenanceId: descriptor.id,
    rawSpineIndex: descriptor.spineIndex,
    ...overrides,
  })
  const sourceByte = characterIndex => encoder.encode(extracted.slice(0, characterIndex)).length
  const dontStart = sourceByte(extracted.indexOf('Don’t'))
  const dontEnd = sourceByte(extracted.indexOf('Don’t') + 'Don’t'.length)
  const stopEnd = sourceByte(extracted.indexOf('stop') + 'stop'.length)
  const fragment = rawFragment({ rawByteStart: dontStart, rawByteEnd: stopEnd })
  const makeContents = doc => {
    const paints = []
    const content = {
      index: 1,
      doc,
      overlayer: {
        add(key, range) {
          paints.push({ key, text: range.toString(), range })
        },
      },
    }
    return { paints, contents: [content] }
  }
  const makeStore = statuses => new api.ReaderWordSyncProvenanceStore({
    postStatus: status => statuses.push(status),
  })

  if (scenarioName === 'exact') {
    const doc = createDocument()
    const { paints, contents } = makeContents(doc)
    const statuses = []
    const store = makeStore(statuses)
    await store.install(descriptor, book, contents)
    const painted = store.paint(fragment, contents, { overlayKey: 'raw-active' })
    const paintedRange = paints[0]?.range || null
    const progressPainted = store.paint({
      ...fragment,
      rawProgressByteEnd: dontEnd,
    }, contents, { overlayKey: 'raw-progress-byte' })
    const progressText = paints.at(-1)?.text || null
    const fractionalProgressPainted = store.paint({
      ...fragment,
      rawProgressFraction: 0.5,
    }, contents, { overlayKey: 'raw-progress-fraction' })
    const fractionalProgressText = paints.at(-1)?.text || null
    const inverse = paintedRange ? store.rawFieldsForRange(paintedRange) : null
    const stopNode = Array.from(doc.querySelectorAll('span'))[0]?.firstChild
    const sampledRange = doc.createRange()
    sampledRange.selectNodeContents(stopNode)
    const detailRangePreferred = locationApi.readerCommittedVisibleDomRange(
      { range: paintedRange },
      { domRange: sampledRange }
    ) === paintedRange
    const detailRangeAcceptedWithoutSampling = locationApi.readerCommittedVisibleDomRange(
      { range: paintedRange },
      {},
      [doc]
    ) === paintedRange
    const foreignRangeRejected = locationApi.readerCommittedVisibleDomRange(
      { range: createDocument().createRange() },
      { domRange: sampledRange }
    ) === sampledRange
    const foreignRangeRejectedWithoutSampling = locationApi.readerCommittedVisibleDomRange(
      { range: createDocument().createRange() },
      {},
      [doc]
    ) === null
    const arbitraryRange = doc.createRange()
    const dontNode = Array.from(doc.querySelectorAll('p'))[1]?.firstChild
    arbitraryRange.setStart(dontNode, 2)
    arbitraryRange.setEnd(stopNode, 2)
    const nativeCreateRange = doc.createRange.bind(doc)
    let inverseRangeAllocations = 0
    Object.defineProperty(doc, 'createRange', {
      configurable: true,
      value: () => {
        inverseRangeAllocations += 1
        return nativeCreateRange()
      },
    })
    const arbitraryInverse = store.rawFieldsForRange(arbitraryRange)
    delete doc.createRange
    const arbitraryPoint = doc.createRange()
    arbitraryPoint.setStart(stopNode, 2)
    arbitraryPoint.collapse(true)
    const arbitraryPointInverse = store.rawFieldsForPoint(arbitraryPoint)
    const visibleDoc = new DOMParser().parseFromString(
      '<body><p>one</p><p>two</p></body>',
      'text/html'
    )
    const visibleRange = visibleDoc.createRange()
    visibleRange.setStart(visibleDoc.querySelectorAll('p')[0].firstChild, 1)
    visibleRange.setEnd(visibleDoc.querySelectorAll('p')[1].firstChild, 2)
    const exactVisibleOffsets = locationApi.readerVisibleTextRangeForDomRange(visibleRange)
    const exactVisibleFallback = locationApi.NavicReaderLocationMethods.currentVisibleTextRangeForHref.call({
      view: {
        renderer: { getContents: () => [{ index: 0, doc: visibleDoc }] },
        book: { sections: [{ href }] },
      },
    }, href, visibleRange)
    const caret = doc.createRange()
    caret.setStart(stopNode, 0)
    caret.collapse(true)
    const point = store.rawFieldsForPoint(caret)
    return {
      finalStatus: statuses.at(-1),
      loadedHref: loads[0],
      painted,
      paintedText: paints[0]?.text || null,
      progressPainted,
      progressText,
      fractionalProgressPainted,
      fractionalProgressText,
      detailRangePreferred,
      detailRangeAcceptedWithoutSampling,
      foreignRangeRejected,
      foreignRangeRejectedWithoutSampling,
      arbitraryInverse,
      inverseRangeAllocations,
      arbitraryPointInverse,
      exactVisibleOffsets: exactVisibleOffsets ? {
        visibleStart: exactVisibleOffsets.visibleStart,
        visibleEnd: exactVisibleOffsets.visibleEnd,
        sameRange: exactVisibleOffsets.domRange === visibleRange,
      } : null,
      exactVisibleFallback: exactVisibleFallback ? {
        textHref: exactVisibleFallback.textHref,
        visibleStart: exactVisibleFallback.visibleStart,
        visibleEnd: exactVisibleFallback.visibleEnd,
        sameRange: exactVisibleFallback.domRange === visibleRange,
      } : null,
      visibleInCommittedRange: paintedRange
        ? store.rangeIsVisible(fragment, { domRange: paintedRange })
        : false,
      visibleInOtherDocument: store.rangeIsVisible(fragment, {
        domRange: createDocument().createRange(),
      }),
      inverse,
      point,
      expectedStart: dontStart,
      expectedEnd: stopEnd,
      expectedPoint: sourceByte(extracted.indexOf('stop')),
      expectedArbitraryPoint: sourceByte(extracted.indexOf('stop') + 2),
    }
  }

  if (scenarioName === 'descriptor-rejections') {
    const runRejected = async overrides => {
      const statuses = []
      const store = makeStore(statuses)
      await store.install({ ...descriptor, id: `case-${Math.random()}`, ...overrides }, book, makeContents(createDocument()).contents)
      return statuses.at(-1)
    }
    return {
      source: await runRejected({ sourceHash: `sha256:${'0'.repeat(64)}` }),
      extracted: await runRejected({ extractedTextHash: `sha256:${'1'.repeat(64)}` }),
      href: await runRejected({ href: 'OPS/Text/Chapter.xhtml' }),
      spine: await runRejected({ spineIndex: 1 }),
    }
  }

  if (scenarioName === 'fail-closed') {
    const doc = createDocument()
    const oldContent = makeContents(doc)
    const statuses = []
    const store = makeStore(statuses)
    await store.install(descriptor, book, oldContent.contents)
    const emojiStart = sourceByte(extracted.indexOf('😀'))
    const ampersandOffset = sourceByte(extracted.indexOf('&'))
    const middleOfCodePoint = store.resolveRange(rawFragment({
      rawByteStart: emojiStart + 1,
      rawByteEnd: stopEnd,
    }))
    const nonTokenBoundary = store.resolveRange(rawFragment({
      rawByteStart: ampersandOffset,
      rawByteEnd: stopEnd,
    }))
    doc.querySelector('style').textContent = 'body { color: rebeccapurple; }'
    await Promise.resolve()
    await Promise.resolve()
    const afterStyleMutation = store.resolveRange(fragment)
    const styleMutationStatus = statuses.at(-1)
    doc.querySelector('span').firstChild.nodeValue = 'halt'
    await Promise.resolve()
    await Promise.resolve()
    const afterMutation = store.resolveRange(fragment)
    const mutationStatus = statuses.at(-1)

    const staleStatuses = []
    const staleStore = makeStore(staleStatuses)
    const oldDoc = createDocument()
    const staleOldContent = makeContents(oldDoc)
    await staleStore.install(descriptor, book, staleOldContent.contents)
    const replacementDoc = createDocument()
    const replacementContent = makeContents(replacementDoc)
    await staleStore.mapLoadedDocuments(book, replacementContent.contents)
    return {
      middleOfCodePoint: middleOfCodePoint !== null,
      nonTokenBoundary: nonTokenBoundary !== null,
      afterStyleMutation: afterStyleMutation !== null,
      styleMutationStatus,
      afterMutation: afterMutation !== null,
      mutationStatus,
      stalePainted: staleStore.paint(fragment, staleOldContent.contents, { overlayKey: 'stale' }),
      replacementPainted: staleStore.paint(fragment, replacementContent.contents, { overlayKey: 'replacement' }),
    }
  }

  if (scenarioName === 'stale-async-attempt') {
    const doc = createDocument()
    const statuses = []
    const store = makeStore(statuses)
    const subtle = crypto.subtle
    const nativeDigest = subtle.digest.bind(subtle)
    const ownDigest = Object.getOwnPropertyDescriptor(subtle, 'digest')
    let releaseFirstDigest
    const firstDigestGate = new Promise(resolve => { releaseFirstDigest = resolve })
    let notifyFirstDigest
    const firstDigestStarted = new Promise(resolve => { notifyFirstDigest = resolve })
    let deferDigest = true
    Object.defineProperty(subtle, 'digest', {
      configurable: true,
      value: async (...args) => {
        if (deferDigest) {
          deferDigest = false
          notifyFirstDigest()
          await firstDigestGate
        }
        return nativeDigest(...args)
      },
    })
    try {
      const staleInstall = store.install({
        ...descriptor,
        sourceHash: `sha256:${'0'.repeat(64)}`,
      }, book, makeContents(doc).contents)
      await firstDigestStarted
      const currentInstall = await store.install(descriptor, book, makeContents(doc).contents)
      releaseFirstDigest()
      const staleInstallResult = await staleInstall
      return {
        currentInstall,
        staleInstallResult,
        finalStatus: statuses.at(-1),
        currentRangeAvailable: store.resolveRange(fragment) !== null,
      }
    } finally {
      if (ownDigest) Object.defineProperty(subtle, 'digest', ownDigest)
      else delete subtle.digest
    }
  }

  if (scenarioName === 'failed-replacement-attempt') {
    const doc = createDocument()
    const statuses = []
    const store = makeStore(statuses)
    const subtle = crypto.subtle
    const nativeDigest = subtle.digest.bind(subtle)
    const ownDigest = Object.getOwnPropertyDescriptor(subtle, 'digest')
    let releaseFirstDigest
    const firstDigestGate = new Promise(resolve => { releaseFirstDigest = resolve })
    let notifyFirstDigest
    const firstDigestStarted = new Promise(resolve => { notifyFirstDigest = resolve })
    let deferDigest = true
    Object.defineProperty(subtle, 'digest', {
      configurable: true,
      value: async (...args) => {
        if (deferDigest) {
          deferDigest = false
          notifyFirstDigest()
          await firstDigestGate
        }
        return nativeDigest(...args)
      },
    })
    try {
      const staleInstall = store.install(descriptor, book, makeContents(doc).contents)
      await firstDigestStarted
      const replacementInstall = await store.install({
        ...descriptor,
        href: 'OPS/Text/Chapter.xhtml',
      }, book, makeContents(doc).contents)
      releaseFirstDigest()
      const staleInstallResult = await staleInstall
      return {
        replacementInstall,
        staleInstallResult,
        finalStatus: statuses.at(-1),
        staleRangeAvailable: store.resolveRange(fragment) !== null,
      }
    } finally {
      if (ownDigest) Object.defineProperty(subtle, 'digest', ownDigest)
      else delete subtle.digest
    }
  }

  if (scenarioName === 'invalid-replacement-attempt') {
    const doc = createDocument()
    const statuses = []
    const store = makeStore(statuses)
    const subtle = crypto.subtle
    const nativeDigest = subtle.digest.bind(subtle)
    const ownDigest = Object.getOwnPropertyDescriptor(subtle, 'digest')
    let releaseFirstDigest
    const firstDigestGate = new Promise(resolve => { releaseFirstDigest = resolve })
    let notifyFirstDigest
    const firstDigestStarted = new Promise(resolve => { notifyFirstDigest = resolve })
    let deferDigest = true
    Object.defineProperty(subtle, 'digest', {
      configurable: true,
      value: async (...args) => {
        if (deferDigest) {
          deferDigest = false
          notifyFirstDigest()
          await firstDigestGate
        }
        return nativeDigest(...args)
      },
    })
    try {
      const staleInstall = store.install(descriptor, book, makeContents(doc).contents)
      await firstDigestStarted
      const replacementInstall = await store.install({
        ...descriptor,
        sourceHash: 'invalid',
      }, book, makeContents(doc).contents)
      releaseFirstDigest()
      const staleInstallResult = await staleInstall
      return {
        replacementInstall,
        staleInstallResult,
        finalStatus: statuses.at(-1),
        staleRangeAvailable: store.resolveRange(fragment) !== null,
      }
    } finally {
      if (ownDigest) Object.defineProperty(subtle, 'digest', ownDigest)
      else delete subtle.digest
    }
  }

  if (scenarioName === 'unexpected-verification-failure') {
    const doc = createDocument()
    const statuses = []
    const store = makeStore(statuses)
    const OriginalDOMParser = globalThis.DOMParser
    let escaped = false
    let installed = null
    try {
      globalThis.DOMParser = class {
        constructor() { throw new Error('synthetic extraction failure') }
      }
      installed = await store.install(descriptor, book, makeContents(doc).contents)
    } catch (_) {
      escaped = true
    } finally {
      globalThis.DOMParser = OriginalDOMParser
    }
    return { escaped, installed, finalStatus: statuses.at(-1) }
  }

  if (scenarioName === 'routing') {
    const calls = { cue: 0, raw: 0, rejected: 0 }
    const handlers = {
      cue: () => { calls.cue += 1; return 'cue' },
      raw: () => { calls.raw += 1; return 'raw' },
      reject: () => { calls.rejected += 1; return 'rejected' },
    }
    const rawResult = api.routeReaderOverlayCoordinateMode(fragment, handlers)
    const afterRaw = { ...calls }
    const implicitCueResult = api.routeReaderOverlayCoordinateMode({
      resourceHref: 'audio.mp3',
      textHref: href,
      textStart: 1,
      textEnd: 4,
    }, handlers)
    const explicitCueResult = api.routeReaderOverlayCoordinateMode({
      resourceHref: 'audio.mp3',
      coordinateMode: 'cue-v1-dom-utf16',
      textHref: href,
      textStart: 1,
      textEnd: 4,
    }, handlers)
    const unknownResult = api.routeReaderOverlayCoordinateMode({
      resourceHref: 'audio.mp3',
      coordinateMode: 'future-coordinate-mode',
    }, handlers)
    const mixedResult = api.routeReaderOverlayCoordinateMode({
      ...fragment,
      textStart: 1,
      textEnd: 4,
    }, handlers)
    const runtimePainterAvailable = typeof api.paintReaderWordSyncOverlayTextRange === 'function'
    let runtimeRawPaintResult = null
    let runtimeCuePaintResult = null
    let runtimeRawPaintCalls = 0
    if (runtimePainterAvailable) {
      const runtime = {
        rawTextProvenance: {
          paint() { runtimeRawPaintCalls += 1; return true },
        },
        contentEntries: () => [],
        readerMediaOverlayHighlightDraw: () => null,
        readerMediaOverlayHighlightColor: () => null,
        view: null,
      }
      runtimeRawPaintResult = api.paintReaderWordSyncOverlayTextRange(runtime, fragment, 'raw-active')
      runtimeCuePaintResult = api.paintReaderWordSyncOverlayTextRange(runtime, {
        resourceHref: 'audio.mp3',
        coordinateMode: 'cue-v1-dom-utf16',
        textHref: href,
        textStart: 1,
        textEnd: 4,
      }, 'raw-active')
    }
    return {
      rawResult,
      afterRaw,
      implicitCueResult,
      explicitCueResult,
      unknownResult,
      mixedResult,
      runtimePainterAvailable,
      runtimeRawPaintResult,
      runtimeCuePaintResult,
      runtimeRawPaintCalls,
      calls,
    }
  }

  throw new Error('unknown scenario')
}, scenario)

test('keeps page-spanning cue progress active until the sentence finishes', async () => {
  const page = await newReaderPage()
  try {
    const result = await page.evaluate(async () => {
      const mediaOverlayApi = await import('/navic-reader-media-overlay.js')
      const runtime = {
        currentVisibleTextRangeForHref: () => ({
          visibleStart: 100,
          visibleEnd: 120,
        }),
      }
      Object.assign(runtime, mediaOverlayApi.NavicReaderMediaOverlayMethods)
      const fragment = {
        coordinateMode: 'cue-v1-dom-utf16',
        textHref: 'synthetic.xhtml',
        textStart: 80,
        textEnd: 140,
        textProgressEnd: 130,
        textProgressFraction: 5 / 6,
      }
      return {
        fragmentVisible: runtime.mediaOverlayFragmentAlreadyVisible(fragment),
        progressVisible: runtime.mediaOverlayFragmentProgressAlreadyVisible(fragment),
      }
    })

    assert.equal(result.fragmentVisible, true)
    assert.equal(result.progressVisible, true)
  } finally {
    await page.close()
  }
})

test('derives page-local anchor geometry from the resolved Foliate iframe and fails closed without ownership', async () => {
  const page = await newReaderPage()
  try {
    const result = await page.evaluate(async () => {
      const api = await import('/navic-reader-wordsync-provenance.js')
      const fragment = { overlayRequestId: 19, wordBoundarySequence: 19 }
      const iframe = document.createElement('iframe')
      Object.assign(iframe.style, {
        position: 'absolute',
        left: '120px',
        top: '30px',
        width: '400px',
        height: '300px',
        border: '0',
      })
      document.body.append(iframe)
      const iframeRect = iframe.getBoundingClientRect()
      const text = iframe.contentDocument.createTextNode('synthetic fixture')
      iframe.contentDocument.body.append(text)
      const range = iframe.contentDocument.createRange()
      range.selectNodeContents(text)
      range.getClientRects = () => [
        { left: 20, top: 10, right: 80, bottom: 34, width: 60, height: 24 },
      ]
      const runtime = {
        wordSyncAnchorGeneration: 4,
        rawTextProvenance: { resolveRange: () => range },
        pageTurnLivePresentationReceipt: () => ({
          scope: 'live',
          token: 'settled-11',
          pageIndex: 12,
          foliateSessionId: 'foliate-7',
          rasterGeneration: 31,
          textureGeneration: 32,
          foregroundMutationGeneration: 8,
          presentationSequence: 9,
        }),
        pageTurnTextPageCommitIdentity: () => ({
          layoutGeneration: 21,
          viewGeneration: 22,
          commitSequence: 23,
          flow: 'paginated',
          index: 3,
          pageIndex: 2,
          pageCount: 7,
        }),
        pageTurnCaptureGeometry: () => ({
          viewportWidth: 1200,
          viewportHeight: 800,
          mode: 'single',
          pages: [{
            role: 'full',
            left: iframeRect.left - 20,
            top: iframeRect.top - 10,
            width: 580,
            height: 760,
          }],
        }),
        pageTurnRasterDescriptor: () => ({
          paginationFingerprint: 'pagination-a',
          layoutFingerprint: 'layout-a',
          decorationFingerprint: 'settings-a',
          visualPageOrdinal: 12,
          spineIndex: 3,
          chapterPageIndex: 2,
          chapterPageCount: 7,
        }),
        currentPagePosition: { pageIndex: 12, spineIndex: 3 },
      }
      const anchor = api.readerWordSyncAnchorReceipt(runtime, fragment)
      const events = []
      runtime.pageTurnLivePresentationReceipt = () => null
      const missingReceipt = api.postReaderWordSyncOverlayActive(
        runtime,
        fragment,
        event => events.push(event)
      )
      return {
        anchor,
        missingReceipt,
        eventCount: events.length,
        anchorGeneration: runtime.wordSyncAnchorGeneration,
      }
    })

    assert.deepEqual(result.anchor.pageLocalRects, [
      { role: 'full', left: 40, top: 20, width: 60, height: 24 },
    ])
    assert.equal(result.missingReceipt, null)
    assert.equal(result.eventCount, 0)
    assert.equal(result.anchorGeneration, 5)
  } finally {
    await page.close()
  }
})

test('derives cue anchor geometry from the accepted Foliate range and request identity', async () => {
  const page = await newReaderPage()
  try {
    const result = await page.evaluate(async () => {
      const api = await import('/navic-reader-wordsync-provenance.js')
      const range = {
        getClientRects: () => [
          { left: 140, top: 50, right: 200, bottom: 74, width: 60, height: 24 },
        ],
      }
      const runtime = {
        wordSyncAnchorGeneration: 0,
        pageTurnLivePresentationReceipt: () => ({
          scope: 'live',
          token: 'settled-11',
          pageIndex: 12,
          foliateSessionId: 'foliate-7',
          rasterGeneration: 31,
          textureGeneration: 32,
          foregroundMutationGeneration: 8,
          presentationSequence: 9,
        }),
        pageTurnTextPageCommitIdentity: () => ({
          layoutGeneration: 21,
          viewGeneration: 22,
          commitSequence: 23,
          flow: 'paginated',
          index: 3,
          pageIndex: 2,
          pageCount: 7,
        }),
        pageTurnCaptureGeometry: () => ({
          viewportWidth: 1200,
          viewportHeight: 800,
          mode: 'single',
          pages: [{ role: 'full', left: 100, top: 20, width: 1000, height: 760 }],
        }),
        pageTurnRasterDescriptor: () => ({
          paginationFingerprint: 'pagination-a',
          layoutFingerprint: 'layout-a',
          decorationFingerprint: 'settings-a',
          visualPageOrdinal: 12,
          spineIndex: 3,
          chapterPageIndex: 2,
          chapterPageCount: 7,
        }),
        currentPagePosition: { pageIndex: 12, spineIndex: 3 },
      }
      return api.readerMediaOverlayAnchorReceipt(
        runtime,
        { overlayRequestId: 27 },
        [range]
      )
    })

    assert.equal(result.boundarySequence, 27)
    assert.equal(result.anchorGeneration, 1)
    assert.deepEqual(result.pageLocalRects, [
      { role: 'full', left: 40, top: 30, width: 60, height: 24 },
    ])
  } finally {
    await page.close()
  }
})

test('derives page-local anchor geometry only from a current Foliate presentation receipt', async () => {
  const page = await newReaderPage()
  try {
    const result = await page.evaluate(async () => {
      const api = await import('/navic-reader-wordsync-provenance.js')
      const fragment = { overlayRequestId: 19, wordBoundarySequence: 19 }
      const runtime = {
        wordSyncAnchorGeneration: 4,
        rawTextProvenance: {
          resolveRange: () => ({
            getClientRects: () => [
              { left: 570, top: 40, right: 630, bottom: 64, width: 60, height: 24 },
              { left: 650, top: 40, right: 712, bottom: 64, width: 62, height: 24 },
            ],
          }),
        },
        pageTurnLivePresentationReceipt: () => ({
          scope: 'live',
          token: 'settled-11',
          pageIndex: 12,
          foliateSessionId: 'foliate-7',
          rasterGeneration: 31,
          textureGeneration: 32,
          foregroundMutationGeneration: 8,
          presentationSequence: 9,
        }),
        pageTurnTextPageCommitIdentity: () => ({
          layoutGeneration: 21,
          viewGeneration: 22,
          commitSequence: 23,
          flow: 'paginated',
          index: 3,
          pageIndex: 2,
          pageCount: 7,
        }),
        pageTurnCaptureGeometry: () => ({
          viewportWidth: 1200,
          viewportHeight: 800,
          mode: 'spread',
          pages: [
            { role: 'left', left: 10, top: 0, width: 580, height: 800 },
            { role: 'right', left: 610, top: 0, width: 580, height: 800 },
          ],
        }),
        pageTurnRasterDescriptor: () => ({
          paginationFingerprint: 'pagination-a',
          layoutFingerprint: 'layout-a',
          decorationFingerprint: 'settings-a',
          visualPageOrdinal: 12,
          spineIndex: 3,
          chapterPageIndex: 2,
          chapterPageCount: 7,
        }),
        currentPagePosition: { pageIndex: 12, spineIndex: 3 },
      }
      const anchor = api.readerWordSyncAnchorReceipt(runtime, fragment)
      const events = []
      api.postReaderWordSyncOverlayActive(runtime, fragment, event => events.push(event))
      runtime.currentPagePosition = { pageIndex: 13, spineIndex: 3 }
      const stalePage = api.readerWordSyncAnchorReceipt(runtime, fragment)
      runtime.currentPagePosition = { pageIndex: 12, spineIndex: 3 }
      runtime.pageTurnLivePresentationReceipt = () => null
      const exposedPreview = api.readerWordSyncAnchorReceipt(runtime, fragment)
      return {
        anchor,
        postedEvent: events[0],
        stalePage,
        exposedPreview,
        anchorGeneration: runtime.wordSyncAnchorGeneration,
      }
    })

    assert.deepEqual(result.anchor, {
      foliateSessionId: 'foliate-7',
      destinationCommitToken: 'settled-11',
      visualPageOrdinal: 12,
      spineIndex: 3,
      rasterGeneration: 31,
      textureGeneration: 32,
      presentationMutationGeneration: 8,
      presentationSequence: 9,
      anchorGeneration: 5,
      boundarySequence: 19,
      layoutGeneration: 21,
      viewGeneration: 22,
      commitSequence: 23,
      committedSpineIndex: 3,
      committedChapterPageIndex: 2,
      committedChapterPageCount: 7,
      paginationFingerprint: 'pagination-a',
      layoutFingerprint: 'layout-a',
      readerSettingsRasterKey: 'settings-a',
      captureGeometry: {
        viewportWidth: 1200,
        viewportHeight: 800,
        mode: 'spread',
        pages: [
          { role: 'left', left: 10, top: 0, width: 580, height: 800 },
          { role: 'right', left: 610, top: 0, width: 580, height: 800 },
        ],
      },
      pageLocalRects: [
        { role: 'left', left: 560, top: 40, width: 20, height: 24 },
        { role: 'right', left: 0, top: 40, width: 20, height: 24 },
        { role: 'right', left: 40, top: 40, width: 62, height: 24 },
      ],
    })
    assert.equal(result.postedEvent.type, 'overlayFragmentActive')
    assert.equal(result.postedEvent.overlayRequestId, 19)
    assert.equal(result.postedEvent.anchorReceipt.anchorGeneration, 6)
    assert.equal(result.postedEvent.anchorReceipt.boundarySequence, 19)
    assert.equal(result.stalePage, null)
    assert.equal(result.exposedPreview, null)
    assert.equal(result.anchorGeneration, 6)
  } finally {
    await page.close()
  }
})

test('records the accepted exact request before posting its active boundary', async () => {
  const page = await newReaderPage()
  try {
    const result = await page.evaluate(async () => {
      const wordSyncApi = await import('/navic-reader-wordsync-provenance.js')
      const events = []
      const runtime = { exactWordSyncActiveRequestId: 61 }
      wordSyncApi.postReaderWordSyncOverlayActive(
        runtime,
        { overlayRequestId: 62, wordBoundarySequence: 9 },
        event => events.push(event),
        { boundarySequence: 9 },
      )
      return {
        activeRequestId: runtime.exactWordSyncActiveRequestId,
        postedRequestId: events[0]?.overlayRequestId,
      }
    })

    assert.equal(result.activeRequestId, 62)
    assert.equal(result.postedRequestId, 62)
  } finally {
    await page.close()
  }
})

test('coalesces exact boundaries until the current Foliate destination settles', async () => {
  const page = await newReaderPage()
  try {
    const results = await page.evaluate(async () => {
      const wordSyncApi = await import('/navic-reader-wordsync-provenance.js')
      const mediaOverlayApi = await import('/navic-reader-media-overlay.js')
      const locationApi = await import('/navic-reader-location.js')

      const createFrame = (label, left) => {
        const iframe = document.createElement('iframe')
        Object.assign(iframe.style, {
          position: 'absolute',
          left: `${left}px`,
          top: '20px',
          width: '240px',
          height: '180px',
          border: '0',
        })
        iframe.dataset.label = label
        document.body.append(iframe)
        const text = iframe.contentDocument.createTextNode(`synthetic ${label}`)
        iframe.contentDocument.body.append(text)
        const range = iframe.contentDocument.createRange()
        range.selectNodeContents(text)
        range.getClientRects = () => [
          { left: 12, top: 16, right: 72, bottom: 40, width: 60, height: 24 },
        ]
        return { iframe, range }
      }
      const frames = {
        source: createFrame('source', 40),
        intermediate: createFrame('intermediate', 320),
        destination: createFrame('destination', 600),
      }
      const run = reason => {
        const bridgeEvents = []
        const paints = []
        let frameLabel = 'source'
        let settled = false
        window.NavicAndroidBridge = {
          postMessage: json => bridgeEvents.push(JSON.parse(json)),
        }
        const runtime = {
          foliateSessionId: `session-${reason}`,
          wordSyncAnchorGeneration: 0,
          mediaOverlayActiveFragment: { overlayRequestId: 41 },
          committedVisibleTextRange: { frameLabel: 'source' },
          currentPagePosition: { pageIndex: 4, spineIndex: 2 },
          surfacePaperTextureFallbackDirection: null,
          relocateSequence: 0,
          rawTextProvenance: {
            rangeIsVisible: (_fragment, visibleRange) =>
              visibleRange?.frameLabel === frameLabel,
            resolveRange: () => frames[frameLabel].range,
          },
          pageTurnLivePresentationReceipt: () => settled ? ({
            scope: 'live',
            token: 'destination-commit',
            pageIndex: 4,
            foliateSessionId: `session-${reason}`,
            rasterGeneration: 31,
            textureGeneration: 32,
            foregroundMutationGeneration: 8,
            presentationSequence: 9,
          }) : null,
          pageTurnTextPageCommitIdentity: () => settled ? ({
            layoutGeneration: 21,
            viewGeneration: 22,
            commitSequence: 23,
            flow: 'paginated',
            index: 2,
            pageIndex: 1,
            pageCount: 3,
          }) : null,
          pageTurnCaptureGeometry: () => {
            const rect = frames.destination.iframe.getBoundingClientRect()
            return {
              viewportWidth: 900,
              viewportHeight: 400,
              mode: 'single',
              pages: [{
                role: 'full',
                left: rect.left,
                top: rect.top,
                width: rect.width,
                height: rect.height,
              }],
            }
          },
          pageTurnRasterDescriptor: () => ({
            paginationFingerprint: 'pagination-destination',
            layoutFingerprint: 'layout-destination',
            decorationFingerprint: 'settings-destination',
            visualPageOrdinal: 4,
            spineIndex: 2,
            chapterPageIndex: 1,
            chapterPageCount: 3,
          }),
          paintActiveMediaOverlayFragment(fragment) {
            if (!this.rawTextProvenance.rangeIsVisible(
              fragment,
              this.committedVisibleTextRange
            )) return false
            paints.push({ frameLabel, boundarySequence: fragment.wordBoundarySequence })
            this.mediaOverlayActiveFragment = fragment
            return true
          },
          clearOverlay() {
            this.mediaOverlayActiveFragment = null
          },
          postOverlayFragmentInactive(fragment, inactiveReason) {
            bridgeEvents.push({
              type: 'overlayFragmentInactive',
              overlayRequestId: fragment.overlayRequestId,
              reason: inactiveReason,
            })
          },
          rejectOverlayFragment(fragment, inactiveReason) {
            this.clearOverlay()
            this.postOverlayFragmentInactive(fragment, inactiveReason)
          },
        }
        Object.assign(
          runtime,
          mediaOverlayApi.NavicReaderMediaOverlayMethods,
          locationApi.NavicReaderLocationMethods
        )
        const fragment = boundarySequence => ({
          coordinateMode: 'wordsync-v1-extracted-utf8',
          overlayRequestId: 41,
          wordBoundarySequence: boundarySequence,
          textHref: 'synthetic.xhtml',
          rawProvenanceId: 'synthetic-raw',
          rawSpineIndex: 2,
          rawByteStart: boundarySequence,
          rawByteEnd: boundarySequence + 1,
        })

        runtime.beginControlledRelocation(reason)
        const relocationEpoch = runtime.activeExactWordSyncRelocation?.epoch
        wordSyncApi.applyReaderWordSyncOverlayFragment(runtime, fragment(7))
        runtime.consumeControlledRelocationReason(reason)
        frameLabel = 'intermediate'
        wordSyncApi.applyReaderWordSyncOverlayFragment(runtime, fragment(8))
        wordSyncApi.applyReaderWordSyncOverlayFragment(runtime, fragment(10))
        const beforeSettlement = {
          paints: [...paints],
          events: bridgeEvents.map(event => event.type),
          deferredBoundary: runtime.deferredExactWordSyncOverlay?.fragment
            ?.wordBoundarySequence,
        }

        settled = true
        frameLabel = 'destination'
        runtime.committedVisibleTextRange = { frameLabel: 'destination' }
        runtime.completeExactWordSyncOverlayRelocation(relocationEpoch)
        const activeEvents = bridgeEvents.filter(event =>
          event.type === 'overlayFragmentActive')
        return {
          beforeSettlement,
          paints,
          activeEventCount: activeEvents.length,
          boundarySequence: activeEvents[0]?.wordBoundarySequence,
          receiptBoundarySequence: activeEvents[0]?.anchorReceipt?.boundarySequence,
          destinationCommitToken: activeEvents[0]?.anchorReceipt?.destinationCommitToken,
          commitSequence: activeEvents[0]?.anchorReceipt?.commitSequence,
        }
      }

      return [run('page-turn:next'), run('media-overlay-follow')]
    })

    for (const result of results) {
      assert.deepEqual(result.beforeSettlement, {
        paints: [],
        events: [],
        deferredBoundary: 10,
      })
      assert.deepEqual(result.paints, [
        { frameLabel: 'destination', boundarySequence: 10 },
      ])
      assert.equal(result.activeEventCount, 1)
      assert.equal(result.boundarySequence, 10)
      assert.equal(result.receiptBoundarySequence, 10)
      assert.equal(result.destinationCommitToken, 'destination-commit')
      assert.equal(result.commitSequence, 23)
    }
  } finally {
    await page.close()
  }
})

test('does not restore a deferred exact boundary cleared before relocation settlement', async () => {
  const page = await newReaderPage()
  try {
    const result = await page.evaluate(async () => {
      const mediaOverlayApi = await import('/navic-reader-media-overlay.js')
      const retried = []
      const runtime = {
        foliateSessionId: 'session-1',
        exactWordSyncRelocationEpoch: 0,
        activeExactWordSyncRelocation: null,
        deferredExactWordSyncOverlay: null,
        mediaOverlayActiveFragment: null,
        clearOverlay({ preserveActiveFragment = false } = {}) {
          if (!preserveActiveFragment) this.mediaOverlayActiveFragment = null
        },
      }
      Object.assign(runtime, mediaOverlayApi.NavicReaderMediaOverlayMethods)
      runtime.beginExactWordSyncOverlayRelocation()
      runtime.exactWordSyncActiveRequestId = 71
      const relocationEpoch = runtime.activeExactWordSyncRelocation?.epoch
      runtime.deferExactWordSyncOverlayFragment({
        coordinateMode: 'wordsync-v1-extracted-utf8',
        overlayRequestId: 71,
        wordBoundarySequence: 12,
        textHref: 'synthetic.xhtml',
        rawProvenanceId: 'synthetic-raw',
        rawSpineIndex: 0,
        rawByteStart: 12,
        rawByteEnd: 13,
      })
      runtime.clearExactWordSyncOverlayPresentation(71, 12)
      const deferredAfterClear = runtime.deferredExactWordSyncOverlay
      runtime.completeExactWordSyncOverlayRelocation(relocationEpoch)
      if (runtime.mediaOverlayActiveFragment?.wordBoundarySequence != null) {
        retried.push(runtime.mediaOverlayActiveFragment.wordBoundarySequence)
      }
      return {
        deferredAfterClear,
        activeRelocationEpoch: runtime.activeExactWordSyncRelocation?.epoch,
        retried,
        activeRequestId: runtime.exactWordSyncActiveRequestId,
      }
    })

    assert.equal(result.deferredAfterClear, null)
    assert.equal(result.activeRelocationEpoch, undefined)
    assert.deepEqual(result.retried, [])
    assert.equal(result.activeRequestId, 71)
  } finally {
    await page.close()
  }
})

test('drops a deferred exact boundary when a newer relocation supersedes its epoch', async () => {
  const page = await newReaderPage()
  try {
    const result = await page.evaluate(async () => {
      const wordSyncApi = await import('/navic-reader-wordsync-provenance.js')
      const mediaOverlayApi = await import('/navic-reader-media-overlay.js')
      const locationApi = await import('/navic-reader-location.js')
      const paints = []
      const runtime = {
        foliateSessionId: 'session-1',
        wordSyncAnchorGeneration: 0,
        mediaOverlayActiveFragment: { overlayRequestId: 51 },
        committedVisibleTextRange: {},
        currentPagePosition: { pageIndex: 1, spineIndex: 0 },
        surfacePaperTextureFallbackDirection: null,
        relocateSequence: 0,
        rawTextProvenance: {
          rangeIsVisible: () => true,
          resolveRange: () => null,
        },
        paintActiveMediaOverlayFragment: fragment => {
          paints.push(fragment.wordBoundarySequence)
          return true
        },
        clearOverlay() {},
        postOverlayFragmentInactive() {},
        rejectOverlayFragment() {},
        pageTurnLivePresentationReceipt: () => null,
        pageTurnTextPageCommitIdentity: () => null,
      }
      Object.assign(
        runtime,
        mediaOverlayApi.NavicReaderMediaOverlayMethods,
        locationApi.NavicReaderLocationMethods
      )
      const fragment = wordBoundarySequence => ({
        coordinateMode: 'wordsync-v1-extracted-utf8',
        overlayRequestId: 51,
        wordBoundarySequence,
        textHref: 'synthetic.xhtml',
        rawProvenanceId: 'synthetic-raw',
        rawSpineIndex: 0,
        rawByteStart: wordBoundarySequence,
        rawByteEnd: wordBoundarySequence + 1,
      })

      runtime.beginControlledRelocation('page-turn:next')
      const staleEpoch = runtime.activeExactWordSyncRelocation?.epoch
      wordSyncApi.applyReaderWordSyncOverlayFragment(runtime, fragment(3))
      runtime.beginControlledRelocation('go-to')
      const currentEpoch = runtime.activeExactWordSyncRelocation?.epoch
      wordSyncApi.applyReaderWordSyncOverlayFragment(runtime, fragment(4))
      runtime.completeExactWordSyncOverlayRelocation(staleEpoch)
      const afterStaleSettlement = [...paints]
      runtime.completeExactWordSyncOverlayRelocation(currentEpoch)
      return {
        staleEpoch,
        currentEpoch,
        afterStaleSettlement,
        paints,
        deferred: runtime.deferredExactWordSyncOverlay,
      }
    })

    assert.notEqual(result.staleEpoch, result.currentEpoch)
    assert.deepEqual(result.afterStaleSettlement, [])
    assert.deepEqual(result.paints, [])
    assert.equal(result.deferred, null)
  } finally {
    await page.close()
  }
})

test('retains a newer exact request and consumes stale requests during relocation', async () => {
  const page = await newReaderPage()
  try {
    const result = await page.evaluate(async () => {
      const mediaOverlayApi = await import('/navic-reader-media-overlay.js')
      const runtime = {
        foliateSessionId: 'session-1',
        exactWordSyncRelocationEpoch: 0,
        activeExactWordSyncRelocation: null,
        deferredExactWordSyncOverlay: null,
        mediaOverlayActiveFragment: { overlayRequestId: 61 },
      }
      Object.assign(runtime, mediaOverlayApi.NavicReaderMediaOverlayMethods)
      runtime.beginExactWordSyncOverlayRelocation()
      const fragment = (overlayRequestId, wordBoundarySequence) => ({
        coordinateMode: 'wordsync-v1-extracted-utf8',
        overlayRequestId,
        wordBoundarySequence,
        textHref: 'synthetic.xhtml',
        rawProvenanceId: 'synthetic-raw',
        rawSpineIndex: 0,
        rawByteStart: wordBoundarySequence,
        rawByteEnd: wordBoundarySequence + 1,
      })
      const newerRequestDeferred = runtime.deferExactWordSyncOverlayFragment(
        fragment(62, 0)
      )
      const staleRequestConsumed = runtime.deferExactWordSyncOverlayFragment(
        fragment(61, 8)
      )
      return {
        newerRequestDeferred,
        staleRequestConsumed,
        pendingRequestId: runtime.deferredExactWordSyncOverlay?.fragment?.overlayRequestId,
        pendingBoundary: runtime.deferredExactWordSyncOverlay?.boundarySequence,
      }
    })

    assert.equal(result.newerRequestDeferred, true)
    assert.equal(result.staleRequestConsumed, true)
    assert.equal(result.pendingRequestId, 62)
    assert.equal(result.pendingBoundary, 0)
  } finally {
    await page.close()
  }
})

test('integrates exact raw mapping into reader lifecycle, visibility, painting, and inverse events', async () => {
  const page = await newReaderPage()
  try {
    const sources = await page.evaluate(async () => Object.fromEntries(await Promise.all(
      [
        'navic-reader.js',
        'navic-reader-location.js',
        'navic-reader-media-overlay.js',
        'navic-reader-content-interactions.js',
        'navic-reader-wordsync-provenance.js',
      ].map(async name => [name, await (await fetch(`/${name}`)).text()])
    )))
    assert.match(sources['navic-reader.js'], /ReaderWordSyncProvenanceStore/)
    assert.match(sources['navic-reader.js'], /case 'installRawTextProvenance'/)
    assert.match(sources['navic-reader.js'], /rawTextProvenance\.mapLoadedDocuments/)
    assert.match(sources['navic-reader.js'], /rawTextProvenance\.clear/)
    assert.match(sources['navic-reader.js'], /applyReaderWordSyncOverlayFragment/)
    assert.match(sources['navic-reader.js'], /paintReaderWordSyncOverlayTextRange/)
    assert.match(
      sources['navic-reader-wordsync-provenance.js'],
      /export const applyReaderWordSyncOverlayFragment/
    )
    assert.match(
      sources['navic-reader-wordsync-provenance.js'],
      /export const paintReaderWordSyncOverlayTextRange/
    )
    assert.match(sources['navic-reader.js'], /readerMediaOverlayResolvedTextRange/)
    assert.match(sources['navic-reader-location.js'], /rawFieldsForRange/)
    assert.doesNotMatch(sources['navic-reader-location.js'], /if \(!domRange\) return null/)
    assert.match(sources['navic-reader-location.js'], /candidates\.push\(\{\s*textHref,\s*\.\.\.range,?\s*\}\)/)
    assert.match(sources['navic-reader-media-overlay.js'], /rangeIsVisible/)
    assert.match(sources['navic-reader-content-interactions.js'], /rawFieldsForPoint/)
  } finally {
    await page.close()
  }
})

test('extracts Bindery v1 text with bounded scanners and exact replacement semantics', async () => {
  const page = await newReaderPage()
  try {
    const source = await page.evaluate(async () =>
      (await fetch('/navic-reader-wordsync-provenance.js')).text())
    assert.doesNotMatch(source, /const ScriptStylePattern/)
    assert.doesNotMatch(source, /const SelectedClosingTagPattern/)
    assert.doesNotMatch(source, /const GenericTagPattern/)
    const result = await page.evaluate(async () => {
      const api = await import('/navic-reader-wordsync-provenance.js')
      const cases = [
        ["A<script>x</script>B<style>y</style>C", 'A B C'],
        ["A<script>unterminated<style>x</style>B", 'A unterminated B'],
        ["A<scripture>x</script>B", 'A B'],
        ['A</P>B</h6>C</br>D', 'A. B. C. D'],
        ['A<>B<<tag>C<unclosed', 'A<>B C<unclosed'],
        ["A<div data='>'>B</div>C", "A '>B. C"],
      ]
      const outputs = cases.map(([input]) => api.extractBinderyV1Text(input))
      const repeated = 'prefix' + '<script'.repeat(100_000)
      return {
        outputs,
        expected: cases.map(([, expected]) => expected),
        repeatedUnchanged: api.extractBinderyV1Text(repeated) === repeated,
      }
    })
    assert.deepEqual(result.outputs, result.expected)
    assert.equal(result.repeatedUnchanged, true)
  } finally {
    await page.close()
  }
})

test('loads the first physical duplicate ZIP entry like Bindery v1', async () => {
  const page = await newReaderPage()
  try {
    const firstSelected = await page.evaluate(async () => {
      const api = await import('/vendor/foliate-js/view.js')
      const first = { filename: 'OPS/Text/chapter.xhtml', marker: 'first' }
      const second = { filename: 'OPS/Text/chapter.xhtml', marker: 'second' }
      return api.foliateFirstZipEntryByExactName([first, second]).get(first.filename) === first
    })
    assert.equal(firstSelected, true)
  } finally {
    await page.close()
  }
})

test('maps verified extracted UTF-8 token bytes to exact split DOM ranges and inverse coordinates', async () => {
  const page = await newReaderPage()
  try {
    const result = await runScenario(page, 'exact')
    assert.deepEqual(result.finalStatus, {
      type: 'rawTextProvenanceStatus',
      provenanceId: 'chapter-raw-1',
      status: 'ready',
    })
    assert.equal(result.loadedHref, 'OPS/Text/chapter.xhtml')
    assert.equal(result.painted, true)
    assert.equal(result.paintedText, 'Don’t stop')
    assert.equal(result.progressPainted, true)
    assert.equal(result.progressText, 'Don’t')
    assert.equal(result.fractionalProgressPainted, true)
    assert.equal(result.fractionalProgressText, 'Don’t')
    assert.equal(result.detailRangePreferred, true)
    assert.equal(result.detailRangeAcceptedWithoutSampling, true)
    assert.equal(result.foreignRangeRejected, true)
    assert.equal(result.foreignRangeRejectedWithoutSampling, true)
    assert.deepEqual(result.arbitraryInverse, {
      rawProvenanceId: 'chapter-raw-1',
      rawSpineIndex: 2,
      rawByteStart: result.expectedStart,
      rawByteEnd: result.expectedEnd,
    })
    assert.ok(result.inverseRangeAllocations <= 3)
    assert.deepEqual(result.arbitraryPointInverse, {
      rawProvenanceId: 'chapter-raw-1',
      rawByteOffset: result.expectedArbitraryPoint,
    })
    assert.deepEqual(result.exactVisibleOffsets, {
      visibleStart: 1,
      visibleEnd: 5,
      sameRange: true,
    })
    assert.deepEqual(result.exactVisibleFallback, {
      textHref: 'OPS/Text/chapter.xhtml',
      visibleStart: 1,
      visibleEnd: 5,
      sameRange: true,
    })
    assert.equal(result.visibleInCommittedRange, true)
    assert.equal(result.visibleInOtherDocument, false)
    assert.deepEqual(result.inverse, {
      rawProvenanceId: 'chapter-raw-1',
      rawSpineIndex: 2,
      rawByteStart: result.expectedStart,
      rawByteEnd: result.expectedEnd,
    })
    assert.deepEqual(result.point, {
      rawProvenanceId: 'chapter-raw-1',
      rawByteOffset: result.expectedPoint,
    })
  } finally {
    await page.close()
  }
})

test('rejects exact source, extracted hash, href, and original spine mismatches with safe reasons', async () => {
  const page = await newReaderPage()
  try {
    const result = await runScenario(page, 'descriptor-rejections')
    assert.equal(result.source.status, 'rejected')
    assert.equal(result.source.reason, 'source-hash-mismatch')
    assert.equal(result.extracted.status, 'rejected')
    assert.equal(result.extracted.reason, 'extracted-hash-mismatch')
    assert.equal(result.href.status, 'rejected')
    assert.equal(result.href.reason, 'section-mismatch')
    assert.equal(result.spine.status, 'rejected')
    assert.equal(result.spine.reason, 'section-mismatch')
  } finally {
    await page.close()
  }
})

test('rejects non-token bytes, DOM mutation, and stale document generations', async () => {
  const page = await newReaderPage()
  try {
    const result = await runScenario(page, 'fail-closed')
    assert.equal(result.middleOfCodePoint, false)
    assert.equal(result.nonTokenBoundary, false)
    assert.equal(result.afterStyleMutation, true)
    assert.equal(result.styleMutationStatus.status, 'ready')
    assert.equal(result.afterMutation, false)
    assert.equal(result.mutationStatus.status, 'rejected')
    assert.equal(result.mutationStatus.reason, 'document-changed')
    assert.equal(result.stalePainted, false)
    assert.equal(result.replacementPainted, true)
  } finally {
    await page.close()
  }
})

test('keeps a newer ready mapping when an older hash attempt rejects', async () => {
  const page = await newReaderPage()
  try {
    const result = await runScenario(page, 'stale-async-attempt')
    assert.equal(result.currentInstall, true)
    assert.equal(result.staleInstallResult, false)
    assert.equal(result.currentRangeAvailable, true)
    assert.deepEqual(result.finalStatus, {
      type: 'rawTextProvenanceStatus',
      provenanceId: 'chapter-raw-1',
      status: 'ready',
    })
  } finally {
    await page.close()
  }
})

test('does not resurrect an old mapping after a replacement descriptor rejects', async () => {
  const page = await newReaderPage()
  try {
    const result = await runScenario(page, 'failed-replacement-attempt')
    assert.equal(result.replacementInstall, false)
    assert.equal(result.staleInstallResult, false)
    assert.equal(result.staleRangeAvailable, false)
    assert.deepEqual(result.finalStatus, {
      type: 'rawTextProvenanceStatus',
      provenanceId: 'chapter-raw-1',
      status: 'rejected',
      reason: 'section-mismatch',
    })
  } finally {
    await page.close()
  }
})

test('does not resurrect an old mapping after a malformed replacement rejects', async () => {
  const page = await newReaderPage()
  try {
    const result = await runScenario(page, 'invalid-replacement-attempt')
    assert.equal(result.replacementInstall, false)
    assert.equal(result.staleInstallResult, false)
    assert.equal(result.staleRangeAvailable, false)
    assert.deepEqual(result.finalStatus, {
      type: 'rawTextProvenanceStatus',
      provenanceId: 'chapter-raw-1',
      status: 'rejected',
      reason: 'invalid-descriptor',
    })
  } finally {
    await page.close()
  }
})

test('rejects unexpected verification exceptions without leaking them from install', async () => {
  const page = await newReaderPage()
  try {
    const result = await runScenario(page, 'unexpected-verification-failure')
    assert.equal(result.escaped, false)
    assert.equal(result.installed, false)
    assert.deepEqual(result.finalStatus, {
      type: 'rawTextProvenanceStatus',
      provenanceId: 'chapter-raw-1',
      status: 'rejected',
      reason: 'source-unavailable',
    })
  } finally {
    await page.close()
  }
})

test('routes raw overlays away from cue fuzzy resolution while retaining implicit and explicit cue-v1', async () => {
  const page = await newReaderPage()
  try {
    const result = await runScenario(page, 'routing')
    assert.equal(result.rawResult, 'raw')
    assert.deepEqual(result.afterRaw, { cue: 0, raw: 1, rejected: 0 })
    assert.equal(result.implicitCueResult, 'cue')
    assert.equal(result.explicitCueResult, 'cue')
    assert.equal(result.unknownResult, 'rejected')
    assert.equal(result.mixedResult, 'rejected')
    assert.equal(result.runtimePainterAvailable, true)
    assert.equal(result.runtimeRawPaintResult, true)
    assert.equal(result.runtimeCuePaintResult, null)
    assert.equal(result.runtimeRawPaintCalls, 1)
    assert.deepEqual(result.calls, { cue: 2, raw: 1, rejected: 2 })
  } finally {
    await page.close()
  }
})
