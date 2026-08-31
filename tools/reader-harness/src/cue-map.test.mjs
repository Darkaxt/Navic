import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { startReaderAssetServer } from './serve-reader-assets.mjs'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const canonicalFixture = JSON.parse(await readFile(
  path.join(repoRoot, 'tools/reader-harness/fixtures/public/canonical-text-v1.json'),
  'utf8'
))

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

test('production cue map exposes visual states and robust one-shot pointer holds', async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })

  const result = await page.evaluate(async () => {
    const { ReaderWhispersyncCueMapRuntime } = await import('/navic-reader-cue-map.js')
    const doc = document.implementation.createHTMLDocument('cue-map')
    const paragraph = doc.createElement('p')
    paragraph.textContent = 'zero one two three four five six seven eight nine'
    doc.body.append(paragraph)
    const text = paragraph.firstChild
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    document.body.append(svg)
    const overlays = new Map()
    const overlayer = {
      add(key, range, draw, options) {
        this.remove(key)
        const left = range.startOffset * 12
        const element = draw([{ left, right: left + 10, top: 20, bottom: 34, width: 10, height: 14 }], options)
        svg.append(element)
        overlays.set(key, element)
      },
      remove(key) {
        overlays.get(key)?.remove()
        overlays.delete(key)
      },
    }
    const events = []
    const rangeForCue = (_content, cue) => {
      const range = doc.createRange()
      range.setStart(text, cue.textStart)
      range.setEnd(text, cue.textEnd)
      return range
    }
    const rasterCalls = []
    const originalGetContext = HTMLCanvasElement.prototype.getContext
    const originalToDataUrl = HTMLCanvasElement.prototype.toDataURL
    HTMLCanvasElement.prototype.getContext = function (...args) {
      rasterCalls.push('getContext')
      return originalGetContext.apply(this, args)
    }
    HTMLCanvasElement.prototype.toDataURL = function (...args) {
      rasterCalls.push('toDataURL')
      return originalToDataUrl.apply(this, args)
    }
    const captureStats = { captured: 0, released: 0 }
    const originalSetPointerCapture = SVGElement.prototype.setPointerCapture
    const originalReleasePointerCapture = SVGElement.prototype.releasePointerCapture
    SVGElement.prototype.setPointerCapture = function () { captureStats.captured += 1 }
    SVGElement.prototype.releasePointerCapture = function () { captureStats.released += 1 }

    const runtime = new ReaderWhispersyncCueMapRuntime({
      contentEntries: () => [{ index: 0, doc, overlayer }],
      resolveRange: rangeForCue,
      resolveAnchorReceipt: (_content, cue) => ({
        boundarySequence: cue.sourceOrdinal,
        captureGeometry: {
          viewportWidth: 1200,
          viewportHeight: 800,
          mode: 'single',
          pages: [{ role: 'full', left: 0, top: 0, width: 1200, height: 800 }],
        },
        pageLocalRects: [{ role: 'full', left: cue.textStart * 12, top: 20, width: 10, height: 14 }],
      }),
      postEvent: event => events.push(event),
      holdDurationMs: 24,
      touchSlopPx: 6,
    })
    const destination = { foliateSessionId: 'session-a', commitSequence: 41 }
    const presentation = generation => ({
      enabled: true,
      revisionDigest: '5f04c2a19e7d',
      presentationGeneration: generation,
      destinationCommitIdentity: destination,
      cues: [
        { sourceOrdinal: 7, textHref: 'Text/chapter.xhtml', textStart: 8, textEnd: 12 },
        { sourceOrdinal: 3, textHref: 'Text/chapter.xhtml', textStart: 2, textEnd: 6 },
      ],
      preparedSourceOrdinal: 7,
      requestedSourceOrdinal: 3,
      audioActiveSourceOrdinal: 7,
      renderedHighlightSourceOrdinal: 3,
      transportAcknowledgementPending: false,
    })
    runtime.replace(presentation(8))

    const markers = () => Array.from(svg.querySelectorAll('[data-navic-cue-source-ordinal]'))
    const markerFor = ordinal => markers().find(marker => marker.dataset.navicCueSourceOrdinal === String(ordinal))
    const pointer = (target, type, x = 20, y = 20) => target.dispatchEvent(new PointerEvent(type, {
      bubbles: true,
      cancelable: true,
      pointerId: 1,
      pointerType: 'touch',
      clientX: x,
      clientY: y,
      button: 0,
    }))
    const holdOutcomes = () => events
      .filter(event => event.type === 'whispersyncCueMapHoldOutcome')
      .map(event => event.outcome)

    const domOrder = markers().map(marker => Number(marker.dataset.navicCueSourceOrdinal))
    const renderedEvent = events.find(event => event.type === 'whispersyncCueMapRendered')
    const renderedOrder = renderedEvent?.sourceOrdinals
    const nativeMarkerOrder = renderedEvent?.markerReceipts?.map(marker => marker.sourceOrdinal)
    const nativeBoundaryOrder = renderedEvent?.markerReceipts
      ?.map(marker => marker.anchorReceipt?.boundarySequence)
    const ordinal3 = markerFor(3)
    const ordinal7 = markerFor(7)
    const stateAttributes = {
      mapped: ordinal3.dataset.navicCueMapped,
      prepared: ordinal7.dataset.navicCuePrepared,
      requested: ordinal3.dataset.navicCueRequested,
      audioActive: ordinal7.dataset.navicCueAudioActive,
      rendered: ordinal3.dataset.navicCueRenderedHighlight,
    }
    const visibleStateLayers = {
      mapped: Boolean(ordinal3.querySelector('[data-navic-cue-visual-state="mapped"]')),
      prepared: Boolean(ordinal7.querySelector('[data-navic-cue-visual-state="prepared"]')),
      requested: Boolean(ordinal3.querySelector('[data-navic-cue-visual-state="requested"]')),
      audioActive: Boolean(ordinal7.querySelector('[data-navic-cue-visual-state="audio-active"]')),
      rendered: Boolean(ordinal3.querySelector('[data-navic-cue-visual-state="rendered-highlight"]')),
    }
    const baselineOffset = Number(ordinal3.dataset.navicCueBaselineOffset)
    const ordinalLabel = ordinal3.querySelector('text')?.textContent

    pointer(markerFor(3), 'pointerdown')
    const determinateRingRunning = markerFor(3)
      .querySelector('[data-navic-cue-hold-ring="determinate"]')
      ?.dataset.navicCueHoldProgressState
    pointer(document, 'pointerup')

    pointer(markerFor(3), 'pointerdown')
    pointer(markerFor(3), 'pointermove', 40, 20)

    pointer(markerFor(3), 'pointerdown')
    pointer(markerFor(3), 'pointercancel')

    pointer(markerFor(3), 'pointerdown')
    pointer(markerFor(3), 'lostpointercapture')

    pointer(markerFor(3), 'pointerdown')
    runtime.cancelHold('chrome-interception')

    pointer(markerFor(3), 'pointerdown')
    runtime.cancelHold('curl-start')

    pointer(markerFor(3), 'pointerdown')
    runtime.replace(presentation(9))

    pointer(markerFor(3), 'pointerdown')
    await new Promise(resolve => setTimeout(resolve, 40))
    const firstCompletedSeekCount = events.filter(event => event.type === 'whispersyncCueMapSeekRequested').length
    const indeterminate = markerFor(3)?.dataset.navicCueHoldState
    const indeterminateRingVisible = markerFor(3)
      ?.querySelector('[data-navic-cue-hold-ring="indeterminate"]')
      ?.dataset.navicCueHoldRingVisible
    const generationCancellationCountBeforeCompletedReplace = holdOutcomes()
      .filter(outcome => outcome === 'cancelled-generation-replacement').length

    runtime.replace(presentation(10))
    const generationCancellationCountAfterCompletedReplace = holdOutcomes()
      .filter(outcome => outcome === 'cancelled-generation-replacement').length
    pointer(markerFor(3), 'pointerdown')
    await new Promise(resolve => setTimeout(resolve, 40))
    pointer(document, 'pointerup')
    const secondCompletedSeekCount = events.filter(event => event.type === 'whispersyncCueMapSeekRequested').length
    pointer(markerFor(3), 'pointerdown')
    await new Promise(resolve => setTimeout(resolve, 40))
    const afterDuplicateAttempt = events.filter(event => event.type === 'whispersyncCueMapSeekRequested').length

    HTMLCanvasElement.prototype.getContext = originalGetContext
    HTMLCanvasElement.prototype.toDataURL = originalToDataUrl
    SVGElement.prototype.setPointerCapture = originalSetPointerCapture
    SVGElement.prototype.releasePointerCapture = originalReleasePointerCapture

    return {
      domOrder,
      renderedOrder,
      nativeMarkerOrder,
      nativeBoundaryOrder,
      stateAttributes,
      visibleStateLayers,
      baselineOffset,
      ordinalLabel,
      determinateRingRunning,
      holdOutcomes: holdOutcomes(),
      firstCompletedSeekCount,
      secondCompletedSeekCount,
      afterDuplicateAttempt,
      indeterminate,
      indeterminateRingVisible,
      generationCancellationCountBeforeCompletedReplace,
      generationCancellationCountAfterCompletedReplace,
      captureStats,
      retainedEventKeys: events.map(event => Object.keys(event)),
      rasterCalls,
    }
  })

  assert.deepEqual(result.domOrder, [3, 7], 'markers must follow DOM reading order, not source/display sorting')
  assert.deepEqual(result.renderedOrder, [3, 7], 'DOM evidence must remain ordinal-only and unsorted')
  assert.deepEqual(
    result.nativeMarkerOrder,
    [3, 7],
    'production rendering must publish Foliate-owned marker geometry for the native page surface'
  )
  assert.deepEqual(result.nativeBoundaryOrder, [3, 7])
  assert.deepEqual(result.stateAttributes, {
    mapped: 'true',
    prepared: 'true',
    requested: 'true',
    audioActive: 'true',
    rendered: 'true',
  })
  assert.deepEqual(result.visibleStateLayers, {
    mapped: true,
    prepared: true,
    requested: true,
    audioActive: true,
    rendered: true,
  })
  assert.ok(result.baselineOffset < 0, 'circled ordinal must be visibly offset above the text baseline')
  assert.equal(result.ordinalLabel, '3')
  assert.equal(result.determinateRingRunning, 'running')
  assert.deepEqual(result.holdOutcomes.slice(0, 7), [
    'cancelled-early-release',
    'cancelled-movement',
    'cancelled-pointer',
    'cancelled-pointer',
    'cancelled-chrome-interception',
    'cancelled-curl-start',
    'cancelled-generation-replacement',
  ])
  assert.equal(result.firstCompletedSeekCount, 1)
  assert.equal(result.secondCompletedSeekCount, 2)
  assert.equal(result.afterDuplicateAttempt, 2)
  assert.equal(result.indeterminate, 'indeterminate')
  assert.equal(result.indeterminateRingVisible, 'true')
  assert.equal(
    result.generationCancellationCountAfterCompletedReplace,
    result.generationCancellationCountBeforeCompletedReplace,
    'replacing a completed hold must release it without reporting cancellation'
  )
  assert.ok(result.captureStats.captured > 0)
  assert.equal(result.captureStats.released, result.captureStats.captured)
  assert.deepEqual(result.rasterCalls, [], 'cue markers and state updates must never invoke a raster API')
  for (const keys of result.retainedEventKeys) {
    assert.equal(keys.includes('text'), false)
    assert.equal(keys.includes('href'), false)
    assert.equal(keys.includes('cfi'), false)
    assert.equal(keys.includes('payload'), false)
    assert.equal(keys.includes('bookId'), false)
  }
})

test('production delegates cue-map pointer ownership to the native surface', async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })

  const result = await page.evaluate(async () => {
    const { ReaderWhispersyncCueMapRuntime } = await import('/navic-reader-cue-map.js')
    const productionSource = await fetch('/navic-reader.js').then(response => response.text())
    const doc = document.implementation.createHTMLDocument('native-cue-map-pointer-owner')
    const paragraph = doc.createElement('p')
    paragraph.textContent = 'zero one two three four'
    doc.body.append(paragraph)
    const text = paragraph.firstChild
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    document.body.append(svg)
    const events = []
    const runtime = new ReaderWhispersyncCueMapRuntime({
      contentEntries: () => [{
        index: 0,
        doc,
        overlayer: {
          add(_key, _range, draw, options) {
            svg.append(draw([{ left: 20, right: 40, top: 20, bottom: 34, width: 20, height: 14 }], options))
          },
          remove() {},
        },
      }],
      resolveRange: () => {
        const range = doc.createRange()
        range.setStart(text, 5)
        range.setEnd(text, 8)
        return range
      },
      postEvent: event => events.push(event),
      nativePointerOwnership: true,
      holdDurationMs: 20,
    })
    runtime.replace({
      enabled: true,
      revisionDigest: '5f04c2a19e7d',
      presentationGeneration: 8,
      destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 41 },
      cues: [{ sourceOrdinal: 4, textHref: 'Text/chapter.xhtml', textStart: 5, textEnd: 8 }],
      transportAcknowledgementPending: false,
    })
    const marker = svg.querySelector('[data-navic-cue-source-ordinal="4"]')
    marker.dispatchEvent(new PointerEvent('pointerdown', {
      bubbles: true,
      cancelable: true,
      pointerId: 7,
      pointerType: 'touch',
      clientX: 20,
      clientY: 20,
      button: 0,
    }))
    await new Promise(resolve => setTimeout(resolve, 40))

    return {
      pointerEvents: marker.style.pointerEvents,
      holdOutcomes: events.filter(event => event.type === 'whispersyncCueMapHoldOutcome').length,
      seeks: events.filter(event => event.type === 'whispersyncCueMapSeekRequested').length,
      productionOwnsPointersNatively: /nativePointerOwnership:\s*true/.test(productionSource),
    }
  })

  assert.equal(result.pointerEvents, 'none')
  assert.equal(result.holdOutcomes, 0)
  assert.equal(result.seeks, 0)
  assert.equal(result.productionOwnsPointersNatively, true)
})

test('same-lifecycle cue-map style update preserves an incomplete hold through one seek', async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })

  const result = await page.evaluate(async () => {
    const { ReaderWhispersyncCueMapRuntime } = await import('/navic-reader-cue-map.js')
    const doc = document.implementation.createHTMLDocument('cue-map-same-lifecycle')
    const paragraph = doc.createElement('p')
    paragraph.textContent = 'zero one two three four five'
    doc.body.append(paragraph)
    const text = paragraph.firstChild
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    document.body.append(svg)
    const overlays = new Map()
    const overlayer = {
      add(key, range, draw, options) {
        this.remove(key)
        const element = draw([{ left: 20, right: 30, top: 20, bottom: 34, width: 10, height: 14 }], options)
        svg.append(element)
        overlays.set(key, element)
      },
      remove(key) {
        overlays.get(key)?.remove()
        overlays.delete(key)
      },
    }
    const events = []
    const runtime = new ReaderWhispersyncCueMapRuntime({
      contentEntries: () => [{ index: 0, doc, overlayer }],
      resolveRange: () => {
        const range = doc.createRange()
        range.setStart(text, 5)
        range.setEnd(text, 8)
        return range
      },
      postEvent: event => events.push(event),
      holdDurationMs: 30,
    })
    const presentation = {
      enabled: true,
      revisionDigest: '5f04c2a19e7d',
      presentationGeneration: 8,
      destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 41 },
      cues: [{ sourceOrdinal: 4, textHref: 'Text/chapter.xhtml', textStart: 5, textEnd: 8 }],
      preparedSourceOrdinal: 4,
      requestedSourceOrdinal: null,
      audioActiveSourceOrdinal: null,
      renderedHighlightSourceOrdinal: null,
      transportAcknowledgementPending: false,
    }
    runtime.replace(presentation)
    const marker = svg.querySelector('[data-navic-cue-source-ordinal="4"]')
    marker.dispatchEvent(new PointerEvent('pointerdown', {
      bubbles: true,
      cancelable: true,
      pointerId: 7,
      pointerType: 'touch',
      clientX: 20,
      clientY: 20,
      button: 0,
    }))
    runtime.replace({
      ...presentation,
      audioActiveSourceOrdinal: 4,
      renderedHighlightSourceOrdinal: 4,
    })
    await new Promise(resolve => setTimeout(resolve, 50))
    document.dispatchEvent(new PointerEvent('pointerup', {
      bubbles: true,
      cancelable: true,
      pointerId: 7,
      pointerType: 'touch',
      clientX: 20,
      clientY: 20,
      button: 0,
    }))

    return {
      seeks: events.filter(event => event.type === 'whispersyncCueMapSeekRequested').length,
      completed: events.filter(event =>
        event.type === 'whispersyncCueMapHoldOutcome' && event.outcome === 'completed').length,
      generationCancellations: events.filter(event =>
        event.type === 'whispersyncCueMapHoldOutcome' &&
        event.outcome === 'cancelled-generation-replacement').length,
      audioActive: svg.querySelector('[data-navic-cue-source-ordinal="4"]')?.dataset.navicCueAudioActive,
      rendered: svg.querySelector('[data-navic-cue-source-ordinal="4"]')?.dataset.navicCueRenderedHighlight,
    }
  })

  assert.equal(result.seeks, 1)
  assert.equal(result.completed, 1)
  assert.equal(result.generationCancellations, 0)
  assert.equal(result.audioActive, 'true')
  assert.equal(result.rendered, 'true')
})

test('cue map uses the production media-overlay resolver and real Foliate Overlayer without raster calls', async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })

  const result = await page.evaluate(async () => {
    const { ReaderWhispersyncCueMapRuntime } = await import('/navic-reader-cue-map.js')
    const { NavicReaderMediaOverlayMethods } = await import('/navic-reader-media-overlay.js')
    const { Overlayer } = await import('/vendor/foliate-js/overlayer.js')

    document.body.replaceChildren()
    const paragraph = document.createElement('p')
    paragraph.style.font = '20px sans-serif'
    paragraph.style.margin = '40px'
    paragraph.textContent = 'zero one two three four five six seven eight nine'
    document.body.append(paragraph)
    const overlayer = new Overlayer(document)
    document.body.append(overlayer.element)
    const content = {
      index: 0,
      href: 'Text/chapter.xhtml',
      doc: document,
      overlayer,
    }
    const productionHost = {
      view: {
        book: { sections: [{ href: 'Text/chapter.xhtml' }] },
      },
      mediaOverlayPaintEndForResolvedRange(
        _textStart,
        _textEnd,
        _paintEnd,
        _resolvedNormalizedTextStart,
        resolvedNormalizedTextEnd,
      ) {
        return resolvedNormalizedTextEnd
      },
      resolveMediaOverlayTextRange: NavicReaderMediaOverlayMethods.resolveMediaOverlayTextRange,
    }
    const rasterCalls = []
    const originalGetContext = HTMLCanvasElement.prototype.getContext
    const originalToDataUrl = HTMLCanvasElement.prototype.toDataURL
    HTMLCanvasElement.prototype.getContext = function (...args) {
      rasterCalls.push('getContext')
      return originalGetContext.apply(this, args)
    }
    HTMLCanvasElement.prototype.toDataURL = function (...args) {
      rasterCalls.push('toDataURL')
      return originalToDataUrl.apply(this, args)
    }
    const events = []
    const runtime = new ReaderWhispersyncCueMapRuntime({
      contentEntries: () => [content],
      resolveRange: (entry, cue) => productionHost.resolveMediaOverlayTextRange
        .call(productionHost, entry, cue, cue.textEnd)?.range,
      postEvent: event => events.push(event),
      holdDurationMs: 1000,
    })

    runtime.replace({
      enabled: true,
      revisionDigest: '5f04c2a19e7d',
      presentationGeneration: 8,
      destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 41 },
      cues: [
        {
          sourceOrdinal: 9,
          textHref: './Text/chapter.xhtml#frag',
          textStart: 19,
          textEnd: 23,
          ebookText: 'four',
        },
        {
          sourceOrdinal: 4,
          textHref: 'Text/chapter.xhtml',
          textStart: 5,
          textEnd: 8,
          ebookText: 'one',
        },
      ],
      preparedSourceOrdinal: 4,
      requestedSourceOrdinal: null,
      audioActiveSourceOrdinal: null,
      renderedHighlightSourceOrdinal: null,
      transportAcknowledgementPending: false,
    })

    const markers = Array.from(overlayer.element.querySelectorAll('[data-navic-cue-source-ordinal]'))
    HTMLCanvasElement.prototype.getContext = originalGetContext
    HTMLCanvasElement.prototype.toDataURL = originalToDataUrl
    return {
      markerOrdinals: markers.map(marker => Number(marker.dataset.navicCueSourceOrdinal)),
      markerRects: markers.map(marker => marker.getBoundingClientRect().toJSON()),
      renderedOrdinals: events.find(event => event.type === 'whispersyncCueMapRendered')?.sourceOrdinals,
      rasterCalls,
    }
  })

  assert.deepEqual(result.markerOrdinals, [4, 9])
  assert.deepEqual(result.renderedOrdinals, [4, 9])
  assert.equal(result.markerRects.length, 2)
  assert.ok(result.markerRects.every(rect => Number.isFinite(rect.x) && Number.isFinite(rect.y)))
  assert.deepEqual(result.rasterCalls, [])
})

test('cue-map replacement terminal paths publish bounded rendered outcomes', async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })

  const result = await page.evaluate(async () => {
    const { ReaderWhispersyncCueMapRuntime } = await import('/navic-reader-cue-map.js')
    const doc = document.implementation.createHTMLDocument('cue-map-terminal-outcomes')
    const paragraph = doc.createElement('p')
    paragraph.textContent = 'zero one two three four'
    doc.body.append(paragraph)
    const text = paragraph.firstChild
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    document.body.append(svg)
    const events = []
    const runtime = new ReaderWhispersyncCueMapRuntime({
      contentEntries: () => [{
        index: 0,
        doc,
        overlayer: {
          add(_key, _range, draw, options) {
            svg.append(draw([{ left: 20, right: 30, top: 20, bottom: 34, width: 10, height: 14 }], options))
          },
          remove() {},
        },
      }],
      resolveRange: () => {
        const range = doc.createRange()
        range.setStart(text, 5)
        range.setEnd(text, 8)
        return range
      },
      resolveAnchorReceipt: () => ({
        boundarySequence: 4,
        captureGeometry: {
          viewportWidth: 1200,
          viewportHeight: 800,
          mode: 'single',
          pages: [{ role: 'full', left: 0, top: 0, width: 1200, height: 800 }],
        },
        pageLocalRects: [{ role: 'full', left: 20, top: 20, width: 10, height: 14 }],
      }),
      postEvent: event => events.push(event),
      holdDurationMs: 1000,
    })
    const presentation = {
      enabled: true,
      revisionDigest: '5f04c2a19e7d',
      presentationGeneration: 8,
      destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 41 },
      cues: [{ sourceOrdinal: 4, textHref: 'Text/chapter.xhtml', textStart: 5, textEnd: 8 }],
      transportAcknowledgementPending: false,
    }
    const renderedAfter = replace => {
      const eventCount = events.length
      const accepted = runtime.replace(replace)
      return {
        accepted,
        events: events.slice(eventCount).filter(event => event.type === 'whispersyncCueMapRendered'),
      }
    }

    runtime.replace(presentation)
    const identical = renderedAfter(presentation)
    const marker = svg.querySelector('[data-navic-cue-source-ordinal="4"]')
    marker.dispatchEvent(new PointerEvent('pointerdown', {
      bubbles: true,
      cancelable: true,
      pointerId: 7,
      pointerType: 'touch',
      clientX: 20,
      clientY: 20,
      button: 0,
    }))
    const deferred = renderedAfter({ ...presentation, audioActiveSourceOrdinal: 4 })
    runtime.cancelHold('chrome-interception')
    const cleared = renderedAfter({
      ...presentation,
      enabled: false,
      presentationGeneration: 9,
      destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 42 },
    })
    const rejected = renderedAfter({
      ...presentation,
      presentationGeneration: 10,
      destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 43 },
      cues: null,
    })

    return { identical, deferred, cleared, rejected }
  })

  for (const [path, expected] of Object.entries({
    identical: { accepted: true, ordinals: [4], markerReceipts: 1, generation: 8, commitSequence: 41 },
    deferred: { accepted: true, ordinals: [4], markerReceipts: 1, generation: 8, commitSequence: 41 },
    cleared: { accepted: false, ordinals: [], markerReceipts: 0, generation: 9, commitSequence: 42 },
    rejected: { accepted: false, ordinals: [], markerReceipts: 0, generation: 10, commitSequence: 43 },
  })) {
    const outcome = result[path]
    assert.equal(outcome.accepted, expected.accepted, `${path} must preserve replacement semantics`)
    assert.equal(outcome.events.length, 1, `${path} must publish one terminal cue-map outcome`)
    const [event] = outcome.events
    assert.deepEqual(event.sourceOrdinals, expected.ordinals)
    assert.equal(event.markerReceipts.length, expected.markerReceipts)
    assert.equal(event.revisionDigest, '5f04c2a19e7d')
    assert.equal(event.presentationGeneration, expected.generation)
    assert.equal(event.destinationFoliateSessionId, 'session-a')
    assert.equal(event.destinationCommitSequence, expected.commitSequence)
    assert.ok(event.sourceOrdinals.length <= 32)
  }
})

test('visible cue geometry remains complete beyond retained ordinal evidence', async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })

  const result = await page.evaluate(async () => {
    const { ReaderWhispersyncCueMapRuntime } = await import('/navic-reader-cue-map.js')
    const doc = document.implementation.createHTMLDocument('complete-visible-cue-geometry')
    const paragraph = doc.createElement('p')
    paragraph.textContent = 'x '.repeat(45)
    doc.body.append(paragraph)
    const text = paragraph.firstChild
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    document.body.append(svg)
    const events = []
    const runtime = new ReaderWhispersyncCueMapRuntime({
      contentEntries: () => [{
        index: 0,
        doc,
        overlayer: {
          add(_key, _range, draw, options) {
            svg.append(draw([{ left: 20, right: 30, top: 20, bottom: 34, width: 10, height: 14 }], options))
          },
          remove() {},
        },
      }],
      resolveRange: (_content, cue) => {
        const range = doc.createRange()
        range.setStart(text, cue.textStart)
        range.setEnd(text, cue.textEnd)
        return range
      },
      resolveAnchorReceipt: (_content, cue) => ({
        boundarySequence: cue.sourceOrdinal,
        captureGeometry: {
          viewportWidth: 1200,
          viewportHeight: 800,
          mode: 'single',
          pages: [{ role: 'full', left: 0, top: 0, width: 1200, height: 800 }],
        },
        pageLocalRects: [{ role: 'full', left: cue.textStart * 10, top: 20, width: 10, height: 14 }],
      }),
      postEvent: event => events.push(event),
      nativePointerOwnership: true,
    })
    runtime.replace({
      enabled: true,
      revisionDigest: '5f04c2a19e7d',
      presentationGeneration: 8,
      destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 41 },
      cues: Array.from({ length: 45 }, (_, index) => ({
        sourceOrdinal: index + 5,
        textHref: 'Text/chapter.xhtml',
        textStart: index * 2,
        textEnd: index * 2 + 1,
      })),
      transportAcknowledgementPending: false,
    })
    const rendered = events.find(event => event.type === 'whispersyncCueMapRendered')
    return {
      visibleMarkerOrdinals: Array.from(svg.querySelectorAll('[data-navic-cue-source-ordinal]'))
        .map(marker => Number(marker.dataset.navicCueSourceOrdinal)),
      sourceOrdinalEvidence: rendered?.sourceOrdinals,
      nativeMarkerReceiptOrdinals: rendered?.markerReceipts?.map(marker => marker.sourceOrdinal),
      nativeAnchorBoundarySequences: rendered?.markerReceipts
        ?.map(marker => marker.anchorReceipt?.boundarySequence),
    }
  })

  assert.deepEqual(result.visibleMarkerOrdinals, Array.from({ length: 45 }, (_, index) => index + 5))
  assert.deepEqual(result.sourceOrdinalEvidence, Array.from({ length: 32 }, (_, index) => index + 5))
  assert.deepEqual(result.nativeMarkerReceiptOrdinals, Array.from({ length: 45 }, (_, index) => index + 5))
  assert.deepEqual(result.nativeAnchorBoundarySequences, Array.from({ length: 45 }, (_, index) => index + 5))
})

test('legacy cue mapping searches the whole spine and never admits an ambiguous foreign offset', async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
  try {
    const result = await page.evaluate(async fixture => {
      const api = await import('/navic-reader-helpers.js')
      const text = fixture.spine.extractedText
      const uniqueLocator = fixture.locators.uniqueOutsideFormerWindow
      const repeatedLocator = fixture.locators.repeated
      const driftStart = text.indexOf(fixture.legacyCue.driftAnchorLocator)
      const normalizedMap = api.readerMediaOverlayNormalizedTextMap([{ text }])
      const unique = api.readerMediaOverlayResolvedTextRange(
        normalizedMap,
        driftStart,
        driftStart + uniqueLocator.length,
        uniqueLocator
      )
      const ambiguous = api.readerMediaOverlayResolvedTextRange(
        normalizedMap,
        text.indexOf(repeatedLocator),
        text.indexOf(repeatedLocator) + repeatedLocator.length,
        repeatedLocator
      )
      return {
        unique,
        uniqueText: unique ? text.slice(unique.textStart, unique.textEnd) : null,
        ambiguous,
      }
    }, canonicalFixture)

    assert.notEqual(result.unique?.locator, 'offset', 'a foreign Bindery offset must never be admitted')
    assert.equal(result.unique?.matched, true)
    assert.equal(result.uniqueText, canonicalFixture.locators.uniqueOutsideFormerWindow)
    assert.equal(result.ambiguous, null, 'an unconstrained repeated full locator must fail closed')
  } finally {
    await page.close()
  }
})

test('canonical byte cues use verified provenance, reject slice mismatch, and never recapture raster', async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
  try {
    const result = await page.evaluate(async fixture => {
      const { ReaderWhispersyncCueMapRuntime } = await import('/navic-reader-cue-map.js')
      const { ReaderWordSyncProvenanceStore, extractBinderyV1Text } =
        await import('/navic-reader-wordsync-provenance.js')
      const { NavicReaderMediaOverlayMethods } = await import('/navic-reader-media-overlay.js')
      const encoder = new TextEncoder()
      const taggedHash = async bytes => {
        const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
        return `sha256:${Array.from(digest, byte => byte.toString(16).padStart(2, '0')).join('')}`
      }
      const sourceBytes = encoder.encode(fixture.spine.sourceXhtml)
      const extractedBytes = encoder.encode(fixture.spine.extractedText)
      const tokenCount = Array.from(
        fixture.spine.extractedText.matchAll(/[A-Za-z0-9]+(?:['’][A-Za-z0-9]+)?/gu)
      ).length
      const descriptor = {
        id: `bindery-v1-spine-${fixture.spine.spineIndex}`,
        href: fixture.spine.href,
        spineIndex: fixture.spine.spineIndex,
        sourceHash: await taggedHash(sourceBytes),
        extractedTextHash: await taggedHash(extractedBytes),
        byteLength: extractedBytes.length,
        tokenCount,
      }
      const doc = new DOMParser().parseFromString(fixture.spine.sourceXhtml, 'application/xhtml+xml')
      const item = { href: fixture.spine.href }
      const book = {
        resources: {
          spine: [
            { idref: 'missing-0' },
            { idref: 'missing-1' },
            { idref: 'canonical' },
          ],
          getItemByID: id => id === 'canonical' ? item : null,
        },
        sections: [{ id: fixture.spine.href }],
        loadBlob: async href => {
          if (href !== fixture.spine.href) throw new Error('unexpected synthetic href')
          return new Blob([sourceBytes], { type: 'application/xhtml+xml' })
        },
      }
      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
      document.body.append(svg)
      const overlays = new Map()
      const content = {
        index: 0,
        doc,
        overlayer: {
          add(key, range, draw, options) {
            this.remove(key)
            const marker = draw([{ left: 20, right: 30, top: 20, bottom: 34, width: 10, height: 14 }], options)
            svg.append(marker)
            overlays.set(key, marker)
          },
          remove(key) {
            overlays.get(key)?.remove()
            overlays.delete(key)
          },
        },
      }
      const statuses = []
      const store = new ReaderWordSyncProvenanceStore({ postStatus: status => statuses.push(status) })
      const installed = await store.install(descriptor, book, [content])
      const mediaOverlayHost = {
        view: { book },
        rawTextProvenance: store,
        mediaOverlayPaintEndForResolvedRange: (
          _textStart,
          _textEnd,
          _paintEnd,
          _resolvedStart,
          resolvedEnd,
        ) => resolvedEnd,
      }
      Object.assign(mediaOverlayHost, NavicReaderMediaOverlayMethods)
      const locator = fixture.locators.uniqueOutsideFormerWindow
      const characterStart = fixture.spine.extractedText.indexOf(locator)
      const rawByteStart = encoder.encode(fixture.spine.extractedText.slice(0, characterStart)).length
      const rawByteEnd = rawByteStart + encoder.encode(locator).length
      const cue = {
        sourceOrdinal: fixture.legacyCue.sourceOrdinal,
        textHref: fixture.spine.href,
        coordinateMode: 'wordsync-v1-extracted-utf8',
        rawProvenanceId: descriptor.id,
        rawSpineIndex: descriptor.spineIndex,
        rawByteStart,
        rawByteEnd,
        ebookText: locator,
      }
      const rasterCalls = []
      const originalGetContext = HTMLCanvasElement.prototype.getContext
      const originalToDataURL = HTMLCanvasElement.prototype.toDataURL
      HTMLCanvasElement.prototype.getContext = function (...args) {
        rasterCalls.push('getContext')
        return originalGetContext.apply(this, args)
      }
      HTMLCanvasElement.prototype.toDataURL = function (...args) {
        rasterCalls.push('toDataURL')
        return originalToDataURL.apply(this, args)
      }
      const runCues = candidates => {
        const events = []
        const runtime = new ReaderWhispersyncCueMapRuntime({
          contentEntries: () => [content],
          resolveRange: (entry, current) => mediaOverlayHost
            .resolveMediaOverlayTextRange(entry, current, current.rawByteEnd)?.range,
          resolveAnchorReceipt: (_entry, current) => ({ boundarySequence: current.sourceOrdinal }),
          postEvent: event => events.push(event),
          nativePointerOwnership: true,
        })
        const accepted = runtime.replace({
          enabled: true,
          revisionDigest: '5f04c2a19e7d',
          presentationGeneration: 8,
          destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 41 },
          cues: candidates,
          transportAcknowledgementPending: false,
        })
        return {
          accepted,
          rendered: events.find(event => event.type === 'whispersyncCueMapRendered'),
        }
      }
      const runCue = candidate => ({
        ...runCues([candidate]),
        text: mediaOverlayHost
          .resolveMediaOverlayTextRange(content, candidate, candidate.rawByteEnd)?.range?.toString() || null,
      })
      const exact = runCue(cue)
      const sliceMismatch = runCue({ ...cue, ebookText: 'Different synthetic locator' })
      const partialMismatch = runCues([
        cue,
        { ...cue, sourceOrdinal: cue.sourceOrdinal + 1, ebookText: 'Different synthetic locator' },
      ])

      const digestStatuses = []
      const digestStore = new ReaderWordSyncProvenanceStore({
        postStatus: status => digestStatuses.push(status),
      })
      const digestInstalled = await digestStore.install({
        ...descriptor,
        id: 'bindery-v1-digest-mismatch',
        extractedTextHash: `sha256:${'0'.repeat(64)}`,
      }, book, [content])
      const digestHost = {
        ...mediaOverlayHost,
        rawTextProvenance: digestStore,
      }
      Object.assign(digestHost, NavicReaderMediaOverlayMethods)
      const digestEvents = []
      const digestRuntime = new ReaderWhispersyncCueMapRuntime({
        contentEntries: () => [content],
        resolveRange: (entry, current) => digestHost
          .resolveMediaOverlayTextRange(entry, current, current.rawByteEnd)?.range,
        resolveAnchorReceipt: (_entry, current) => ({ boundarySequence: current.sourceOrdinal }),
        postEvent: event => digestEvents.push(event),
        nativePointerOwnership: true,
      })
      digestRuntime.replace({
        enabled: true,
        revisionDigest: 'aaaaaaaaaaaa',
        presentationGeneration: 9,
        destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 42 },
        cues: [{ ...cue, rawProvenanceId: 'bindery-v1-digest-mismatch' }],
        transportAcknowledgementPending: false,
      })
      HTMLCanvasElement.prototype.getContext = originalGetContext
      HTMLCanvasElement.prototype.toDataURL = originalToDataURL
      return {
        fixtureExtractedTextMatches: extractBinderyV1Text(fixture.spine.sourceXhtml) === fixture.spine.extractedText,
        installed,
        finalStatus: statuses.at(-1),
        exact,
        sliceMismatch,
        partialMismatch,
        digestInstalled,
        digestFinalStatus: digestStatuses.at(-1),
        digestRendered: digestEvents.find(event => event.type === 'whispersyncCueMapRendered'),
        rasterCalls,
      }
    }, canonicalFixture)

    assert.equal(result.fixtureExtractedTextMatches, true)
    assert.equal(result.installed, true)
    assert.equal(result.finalStatus?.status, 'ready')
    assert.equal(result.exact.accepted, true)
    assert.equal(
      result.exact.text,
      canonicalFixture.locators.uniqueOutsideFormerWindow,
      'the production resolver must route verified cue bytes through the provenance store'
    )
    assert.deepEqual(result.exact.rendered?.sourceOrdinals, [40])
    assert.equal(result.exact.rendered?.markerReceipts?.length, 1)
    assert.deepEqual(result.sliceMismatch.rendered?.sourceOrdinals, [])
    assert.deepEqual(result.sliceMismatch.rendered?.markerReceipts, [])
    assert.equal(result.partialMismatch.accepted, false)
    assert.deepEqual(result.partialMismatch.rendered?.sourceOrdinals, [])
    assert.deepEqual(result.partialMismatch.rendered?.markerReceipts, [])
    assert.equal(result.digestInstalled, false)
    assert.equal(result.digestFinalStatus?.reason, 'extracted-hash-mismatch')
    assert.deepEqual(result.digestRendered?.sourceOrdinals, [])
    assert.deepEqual(result.digestRendered?.markerReceipts, [])
    assert.deepEqual(result.rasterCalls, [])
  } finally {
    await page.close()
  }
})

test('cue-map rejects a same-spine generation with decreasing source ordinals', async () => {
  const page = await browser.newPage()
  await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
  try {
    const result = await page.evaluate(async fixture => {
      const { ReaderWhispersyncCueMapRuntime } = await import('/navic-reader-cue-map.js')
      const text = document.createTextNode(fixture.spine.extractedText)
      document.body.replaceChildren(text)
      const events = []
      const runtime = new ReaderWhispersyncCueMapRuntime({
        contentEntries: () => [{
          index: 0,
          doc: document,
          overlayer: { add() {}, remove() {} },
        }],
        resolveRange: (_content, cue) => {
          const range = document.createRange()
          const start = fixture.spine.extractedText.indexOf(cue.ebookText)
          range.setStart(text, start)
          range.setEnd(text, start + cue.ebookText.length)
          return range
        },
        resolveAnchorReceipt: (_content, cue) => ({ boundarySequence: cue.sourceOrdinal }),
        postEvent: event => events.push(event),
        nativePointerOwnership: true,
      })
      const accepted = runtime.replace({
        enabled: true,
        revisionDigest: '5f04c2a19e7d',
        presentationGeneration: 8,
        destinationCommitIdentity: { foliateSessionId: 'session-a', commitSequence: 41 },
        cues: fixture.decreasingSourceOrdinals.map(cue => ({
          sourceOrdinal: cue.sourceOrdinal,
          textHref: fixture.spine.href,
          rawSpineIndex: fixture.spine.spineIndex,
          ebookText: cue.locator,
        })),
        transportAcknowledgementPending: false,
      })
      return {
        accepted,
        rendered: events.find(event => event.type === 'whispersyncCueMapRendered'),
      }
    }, canonicalFixture)

    assert.equal(result.accepted, false, 'decreasing same-spine source ordinals must reject the generation')
    assert.deepEqual(result.rendered?.sourceOrdinals, [])
    assert.deepEqual(result.rendered?.markerReceipts, [])
  } finally {
    await page.close()
  }
})
