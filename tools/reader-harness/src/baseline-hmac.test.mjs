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
  const moduleAvailable = await page.evaluate(() => import('/navic-reader-baseline-hmac.js')
    .then(() => true)
    .catch(() => false))
  assert.equal(moduleAvailable, true, 'the focused baseline HMAC module must be installed')
  return page
}

test('projects exact nested ranges through an immutable pre-injection baseline', async () => {
  const page = await newReaderPage()
  const result = await page.evaluate(async () => {
    const api = await import('/navic-reader-baseline-hmac.js')
    const doc = new DOMParser().parseFromString(
      '<body><p id="content">discard Å\t <span>Café</span>  middle <em>words</em> suffix</p></body>',
      'text/html'
    )
    const paragraph = doc.querySelector('#content')
    const first = paragraph.firstChild
    const last = paragraph.lastChild
    const baseline = api.captureReaderSourceTextBaseline(doc)
    const range = doc.createRange()
    range.setStart(first, 'discard '.length)
    range.setEnd(last, ' suffix'.length)
    paragraph.append(doc.createTextNode(' injected overlay label '))

    const projected = api.projectReaderBaselinePlainText(baseline, range)
    const frozenBeforeMutation = Object.isFrozen(baseline) &&
      Object.isFrozen(baseline.entries) &&
      baseline.entries.every(Object.isFrozen)
    first.data = `changed ${first.data}`
    const changedProjection = api.projectReaderBaselinePlainText(baseline, range)

    return {
      projected,
      frozenBeforeMutation,
      changedProjection,
      normalized: api.normalizeReaderBaselinePlainText('  Å\t\n B  '),
      punctuation: api.normalizeReaderBaselinePlainText('  Mixed, CASE!  '),
    }
  })

  assert.equal(result.projected, 'Å Café middle words suffix')
  assert.equal(result.frozenBeforeMutation, true)
  assert.equal(result.changedProjection, null)
  assert.equal(result.normalized, 'Å B')
  assert.equal(result.punctuation, 'Mixed, CASE!')
  await page.close()
})

test('excludes hidden structural boilerplate and Navic-injected nodes', async () => {
  const page = await newReaderPage()
  const result = await page.evaluate(async () => {
    const api = await import('/navic-reader-baseline-hmac.js')
    const frame = document.createElement('iframe')
    frame.srcdoc = `<!doctype html><html><head><style>.authored-hidden { display: none }</style></head><body>
      <nav>NAV_SENTINEL ${'navigation '.repeat(20)}</nav>
      <header>HEADER_SENTINEL ${'header '.repeat(20)}</header>
      <main><p id="visible">VISIBLE_SENTINEL ${'publication words '.repeat(12)}</p></main>
      <p hidden>HIDDEN_SENTINEL ${'hidden '.repeat(20)}</p>
      <p aria-hidden="true">ARIA_SENTINEL ${'aria '.repeat(20)}</p>
      <p style="visibility:hidden">INLINE_SENTINEL ${'inline '.repeat(20)}</p>
      <p class="authored-hidden">CSS_SENTINEL ${'css '.repeat(20)}</p>
      <aside>ASIDE_SENTINEL ${'aside '.repeat(20)}</aside>
      <footer>FOOTER_SENTINEL ${'footer '.repeat(20)}</footer>
      <script>const SCRIPT_SENTINEL = true</script>
      <style>.STYLE_SENTINEL { color: red }</style>
    </body></html>`
    document.body.append(frame)
    await new Promise(resolve => frame.addEventListener('load', resolve, { once: true }))
    const doc = frame.contentDocument
    const baseline = api.captureReaderSourceTextBaseline(doc)
    const injected = doc.createElement('div')
    injected.textContent = `INJECTED_SENTINEL ${'overlay '.repeat(20)}`
    doc.querySelector('main').append(injected)
    const range = doc.createRange()
    range.selectNodeContents(doc.body)
    const projected = api.projectReaderBaselinePlainText(baseline, range)
    frame.remove()
    return projected
  })

  assert.match(result, /VISIBLE_SENTINEL/)
  for (const sentinel of [
    'NAV_SENTINEL',
    'HEADER_SENTINEL',
    'HIDDEN_SENTINEL',
    'ARIA_SENTINEL',
    'INLINE_SENTINEL',
    'CSS_SENTINEL',
    'ASIDE_SENTINEL',
    'FOOTER_SENTINEL',
    'SCRIPT_SENTINEL',
    'STYLE_SENTINEL',
    'INJECTED_SENTINEL',
  ]) {
    assert.doesNotMatch(result, new RegExp(sentinel))
  }
  await page.close()
})

test('posts only ordinal booleans for eligible exact duplicate pages', async () => {
  const page = await newReaderPage()
  const result = await page.evaluate(async () => {
    const api = await import('/navic-reader-baseline-hmac.js')
    const events = []
    const diagnostics = new api.ReaderDuplicatePageFingerprintDiagnostics({
      postEvent: event => events.push(event),
    })
    diagnostics.beginSession()
    const makePage = (text, section = {}) => {
      const doc = new DOMParser().parseFromString(`<body><main><p>${text}</p></main></body>`, 'text/html')
      const captured = diagnostics.captureDocument(doc, { section })
      const range = doc.createRange()
      range.selectNodeContents(doc.body)
      return { captured, doc, range }
    }
    const repeated = `Chapter heading. ${'The same publication sentence remains on this committed page. '.repeat(4)}`
    const first = makePage(repeated)
    const duplicate = makePage(`  Chapter heading.\n${'The same publication sentence remains on this committed page. '.repeat(4)}`)
    await diagnostics.compareCommittedPage({
      range: first.range,
      pageOrdinal: 7,
      locator: 'epubcfi(first-private-locator)',
    })
    await diagnostics.compareCommittedPage({
      range: duplicate.range,
      pageOrdinal: 11,
      locator: 'epubcfi(second-private-locator)',
    })

    const short = makePage('short page')
    const imageOnly = makePage('')
    imageOnly.doc.body.append(imageOnly.doc.createElement('img'))
    const boilerplate = makePage(`Navigation ${'boilerplate '.repeat(30)}`, { epubType: 'titlepage' })
    const cover = makePage(repeated, { epubType: 'cover' })
    const nonLinear = makePage(repeated, { linear: 'no' })
    for (const [ordinal, candidate] of [
      [12, short],
      [13, imageOnly],
      [14, boilerplate],
      [15, cover],
      [16, nonLinear],
    ]) {
      await diagnostics.compareCommittedPage({
        range: candidate.range,
        pageOrdinal: ordinal,
        locator: `private-${ordinal}`,
      })
    }

    return {
      events,
      captures: {
        boilerplate: boilerplate.captured,
        cover: cover.captured,
        nonLinear: nonLinear.captured,
      },
    }
  })

  assert.deepEqual(result.events, [{
    type: 'duplicatePageSuspected',
    currentPageOrdinal: 11,
    previousPageOrdinal: 7,
    plainTextSame: true,
    locatorSame: false,
  }])
  assert.deepEqual(result.captures, {
    boilerplate: false,
    cover: false,
    nonLinear: false,
  })
  assert.deepEqual(Object.keys(result.events[0]).sort(), [
    'currentPageOrdinal',
    'locatorSame',
    'plainTextSame',
    'previousPageOrdinal',
    'type',
  ])
  await page.close()
})

test('captures before reader mutation and compares only the committed Foliate range', async () => {
  const page = await newReaderPage()
  const result = await page.evaluate(async () => {
    const locationApi = await import('/navic-reader-location.js')
    const [entrypointSource, locationSource] = await Promise.all([
      fetch('/navic-reader.js').then(response => response.text()),
      fetch('/navic-reader-location.js').then(response => response.text()),
    ])
    const sourceDoc = new DOMParser().parseFromString('<body><p>source</p></body>', 'text/html')
    const captures = []
    const comparisons = []
    const runtime = {
      view: {
        isFixedLayout: false,
        book: { sections: [{}, { linear: 'yes' }] },
      },
      contentEntries: () => [{ doc: sourceDoc, index: 1 }],
      sectionTargetsCover: () => false,
      duplicatePageFingerprint: {
        captureDocument: (doc, options) => {
          captures.push({ sameDocument: doc === sourceDoc, options })
          return true
        },
        compareCommittedPage: input => {
          comparisons.push(input)
          return Promise.resolve(true)
        },
      },
    }
    const exactRange = sourceDoc.createRange()
    exactRange.selectNodeContents(sourceDoc.body)
    locationApi.NavicReaderLocationMethods.captureDuplicatePageBaselines.call(runtime, { doc: sourceDoc })
    locationApi.NavicReaderLocationMethods.compareCommittedDuplicatePage.call(runtime, {
      range: exactRange,
      cfi: 'private-locator',
    }, { pageIndex: 10 })
    await Promise.resolve()
    return {
      importInstalled: entrypointSource.includes(
        "import { ReaderDuplicatePageFingerprintDiagnostics } from './navic-reader-baseline-hmac.js'"
      ),
      captureIsFirstOnLoadStatement: /onLoad\(detail = \{\}\) \{\s*this\.captureDuplicatePageBaselines\(detail\)/u.test(entrypointSource),
      comparisonUsesDetailRange: locationSource.includes('range: detail.range'),
      captures,
      comparison: comparisons[0] ? {
        sameRange: comparisons[0].range === exactRange,
        pageOrdinal: comparisons[0].pageOrdinal,
        locator: comparisons[0].locator,
      } : null,
    }
  })

  assert.equal(result.importInstalled, true)
  assert.equal(result.captureIsFirstOnLoadStatement, true)
  assert.equal(result.comparisonUsesDetailRange, true)
  assert.equal(result.captures.length, 1)
  assert.equal(result.captures[0].sameDocument, true)
  assert.equal(result.captures[0].options.fixedLayout, false)
  assert.deepEqual(result.comparison, {
    sameRange: true,
    pageOrdinal: 11,
    locator: 'private-locator',
  })
  await page.close()
})

test('uses a non-extractable session key and rejects stale async commits', async () => {
  const page = await newReaderPage()
  const result = await page.evaluate(async () => {
    const api = await import('/navic-reader-baseline-hmac.js')
    const pendingSigns = []
    let extractable = null
    const cryptoProvider = {
      subtle: {
        generateKey(_algorithm, requestedExtractable) {
          extractable = requestedExtractable
          return Promise.resolve({ sessionKey: true })
        },
        sign() {
          return new Promise(resolve => pendingSigns.push(resolve))
        },
      },
    }
    const events = []
    const diagnostics = new api.ReaderDuplicatePageFingerprintDiagnostics({
      cryptoProvider,
      postEvent: event => events.push(event),
    })
    diagnostics.beginSession()
    const createCandidate = () => {
      const doc = new DOMParser().parseFromString(
        `<body><p>${'Synthetic publication text long enough for a fingerprint. '.repeat(4)}</p></body>`,
        'text/html'
      )
      diagnostics.captureDocument(doc)
      const range = doc.createRange()
      range.selectNodeContents(doc.body)
      return range
    }
    const stale = diagnostics.compareCommittedPage({
      range: createCandidate(),
      pageOrdinal: 7,
      locator: 'private-seven',
    })
    while (pendingSigns.length < 2) await Promise.resolve()
    const current = diagnostics.compareCommittedPage({
      range: createCandidate(),
      pageOrdinal: 11,
      locator: 'private-eleven',
    })
    while (pendingSigns.length < 4) await Promise.resolve()
    for (const resolve of pendingSigns.splice(0)) resolve(new Uint8Array(32).buffer)
    await Promise.all([stale, current])
    return { extractable, events }
  })

  assert.equal(result.extractable, false)
  assert.deepEqual(result.events, [])
  await page.close()
})
