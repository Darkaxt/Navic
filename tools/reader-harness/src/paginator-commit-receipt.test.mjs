import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import path from 'node:path'
import { after, afterEach, before, beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const paginatorPath = path.join(
  repoRoot,
  'composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js',
)
const delayedFontPath = path.join(
  repoRoot,
  'composeApp/src/androidMain/assets/reader/fonts/navic-literata-regular.ttf',
)

const sectionHtml = (label = 'section', extraHead = '') => `<!doctype html>
<html>
<head>
<meta charset="utf-8">
${extraHead}
<style>
  html, body { margin: 0; padding: 0; }
  body { font: 16px/1.5 sans-serif; }
  p { margin: 0 0 0.75em; }
</style>
</head>
<body>
  ${Array.from({ length: 96 }, (_, index) =>
    `<p>${label} paragraph ${index}: synthetic paginator contract prose fills several deterministic text pages.</p>`
  ).join('')}
</body>
</html>`

const delayedFontCss = `<style>
  @font-face { font-family: DelayedCommitFont; src: url('/delayed-font.ttf') format('truetype'); }
  body { font-family: DelayedCommitFont, serif !important; }
</style>`

let browser
let context
let page
let server
let origin
let delayedFontGate = null

const deferred = () => {
  let resolve
  const promise = new Promise(resolvePromise => { resolve = resolvePromise })
  return { promise, resolve }
}

before(async () => {
  server = http.createServer((request, response) => {
    const requestUrl = new URL(request.url || '/', 'http://127.0.0.1')
    if (requestUrl.pathname === '/paginator.js') {
      response.writeHead(200, {
        'content-type': 'text/javascript; charset=utf-8',
        'cache-control': 'no-store',
      })
      fs.createReadStream(paginatorPath).pipe(response)
      return
    }
    if (requestUrl.pathname === '/delayed-font.ttf') {
      const gate = delayedFontGate
      gate?.requested.resolve()
      Promise.resolve(gate?.release.promise).then(() => {
        response.writeHead(200, {
          'content-type': 'font/ttf',
          'cache-control': 'no-store',
        })
        fs.createReadStream(delayedFontPath).pipe(response)
      })
      return
    }
    response.writeHead(200, {
      'content-type': 'text/html; charset=utf-8',
      'cache-control': 'no-store',
    })
    response.end(`<!doctype html><meta charset="utf-8"><style>
      html, body { margin: 0; width: 100%; height: 100%; }
      body { display: flex; align-items: flex-start; }
    </style>`)
  })
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const address = server.address()
  origin = `http://127.0.0.1:${address.port}`
  browser = await chromium.launch({ headless: true })
})

after(async () => {
  await browser?.close()
  await new Promise((resolve, reject) => {
    server?.close(error => error ? reject(error) : resolve())
  })
})

beforeEach(async () => {
  context = await browser.newContext({ viewport: { width: 900, height: 700 } })
  page = await context.newPage()
  await page.goto(origin)
  await page.evaluate(async () => import('/paginator.js'))
  await page.evaluate(() => {
    window.__paginatorFixtures = []
    window.__createPaginatorFixture = ({
      html = [],
      flow = 'paginated',
      deferredLoad = false,
    } = {}) => {
      const urls = new Set()
      let loadStartedResolve
      let releaseLoadResolve
      const loadStarted = new Promise(resolve => { loadStartedResolve = resolve })
      const releaseLoad = new Promise(resolve => { releaseLoadResolve = resolve })
      const section = sectionSource => ({
        linear: 'yes',
        async load() {
          if (deferredLoad) {
            loadStartedResolve()
            await releaseLoad
          }
          const url = URL.createObjectURL(new Blob([sectionSource], { type: 'text/html' }))
          urls.add(url)
          return url
        },
        unload() {},
      })
      const sections = html.map(section)
      const paginator = document.createElement('foliate-paginator')
      paginator.style.cssText = 'display:block;width:560px;height:420px;'
      paginator.setAttribute('max-column-count', '1')
      paginator.setAttribute('margin', '24px')
      paginator.setAttribute('gap', '7%')
      if (flow === 'scrolled') paginator.setAttribute('flow', 'scrolled')
      document.body.append(paginator)
      const book = { dir: 'ltr', sections }
      paginator.open(book)
      const fixture = {
        book,
        paginator,
        sections,
        loadStarted,
        releaseLoad: () => releaseLoadResolve(),
        destroy() {
          try { paginator.destroy() } catch {}
          paginator.remove()
          for (const url of urls) URL.revokeObjectURL(url)
          urls.clear()
        },
      }
      window.__paginatorFixtures.push(fixture)
      window.__paginatorFixture = fixture
      return fixture
    }
    window.__waitForReceiptInvalidation = (paginator, receipt, timeoutMs = 2000) =>
      new Promise((resolve, reject) => {
        if (!paginator.validateTextPageCommit(receipt)) {
          resolve(null)
          return
        }
        const timeout = setTimeout(() => reject(new Error('Timed out waiting for receipt invalidation')), timeoutMs)
        paginator.addEventListener('text-page-commit-invalidated', event => {
          clearTimeout(timeout)
          resolve(event.detail)
        }, { once: true })
      })
  })
})

afterEach(async () => {
  await page?.evaluate(() => {
    for (const fixture of window.__paginatorFixtures || []) fixture.destroy()
    window.__paginatorFixtures = []
    window.__paginatorFixture = null
  }).catch(() => {})
  await context?.close()
  delayedFontGate = null
})

test('commits an exact text page with an immutable identity-scoped receipt', async () => {
  const actual = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    const result = await paginator.commitTextPage(0, 1, 'test-exact')
    const copiedReceipt = Object.freeze({ ...result.receipt })
    return {
      status: result.status,
      position: result.position,
      receipt: result.receipt,
      resultFrozen: Object.isFrozen(result),
      positionFrozen: Object.isFrozen(result.position),
      receiptFrozen: Object.isFrozen(result.receipt),
      validates: paginator.validateTextPageCommit(result.receipt),
      copiedReceiptValidates: paginator.validateTextPageCommit(copiedReceipt),
    }
  }, sectionHtml())

  assert.equal(actual.status, 'committed')
  assert.deepEqual(actual.position, {
    index: 0,
    pageIndex: 1,
    pageCount: actual.position.pageCount,
  })
  assert.equal(actual.receipt.index, 0)
  assert.equal(actual.receipt.pageIndex, 1)
  assert.equal(actual.receipt.pageCount, actual.position.pageCount)
  assert.equal(actual.receipt.flow, 'paginated')
  assert.equal(actual.validates, true)
  assert.equal(actual.copiedReceiptValidates, false)
  assert.equal(actual.resultFrozen, true)
  assert.equal(actual.positionFrozen, true)
  assert.equal(actual.receiptFrozen, true)
})

test('privately validates normalized visible text without serializing it', async () => {
  const actual = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    const result = await paginator.commitTextPage(0, 0, 'test-visible-content')
    const textNode = paginator.getContents()[0].doc.querySelector('p')?.firstChild
    if (!textNode) throw new Error('Synthetic visible text node is unavailable')
    const original = textNode.data
    const initiallyValid = paginator.validateTextPageVisibleContent(result.receipt)
    textNode.data = original.replaceAll(' ', '\n\t')
    const normalizedWhitespaceValid = paginator.validateTextPageVisibleContent(result.receipt)
    textNode.data = 'different in-memory fixture text'
    return {
      changedTextValid: paginator.validateTextPageVisibleContent(result.receipt),
      initiallyValid,
      normalizedWhitespaceValid,
      resultSerialized: JSON.stringify(result),
      receiptSerialized: JSON.stringify(result.receipt),
    }
  }, sectionHtml('visible-proof'))

  assert.equal(actual.initiallyValid, true)
  assert.equal(actual.normalizedWhitespaceValid, true)
  assert.equal(actual.changedTextValid, false)
  assert.equal(actual.resultSerialized.includes('visible-proof'), false)
  assert.equal(actual.receiptSerialized.includes('visible-proof'), false)
  assert.doesNotMatch(actual.resultSerialized, /text|normal|digest|fingerprint/i)
  assert.doesNotMatch(actual.receiptSerialized, /text|normal|digest|fingerprint/i)
})

test('accepts empty visible text and fails closed when its range cannot be read', async () => {
  const actual = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    const result = await paginator.commitTextPage(0, 0, 'test-empty-visible-content')
    const emptyVisibleContentValid = paginator.validateTextPageVisibleContent(result.receipt)
    const contentRange = paginator.getContents()[0]?.doc?.defaultView?.Range
    if (!contentRange) throw new Error('Synthetic content Range is unavailable')
    const originalToString = contentRange.prototype.toString
    let unavailableRangeValid
    contentRange.prototype.toString = () => { throw new Error('synthetic range read failure') }
    try {
      unavailableRangeValid = paginator.validateTextPageVisibleContent(result.receipt)
    } finally {
      contentRange.prototype.toString = originalToString
    }
    return {
      emptyVisibleContentValid,
      receiptValid: paginator.validateTextPageCommit(result.receipt),
      status: result.status,
      unavailableRangeValid,
    }
  }, `<!doctype html><meta charset="utf-8"><style>
    html, body { margin: 0; padding: 0; }
    #blank { width: 240px; height: 120px; }
  </style><body><div id="blank"></div></body>`)

  assert.equal(actual.status, 'committed')
  assert.equal(actual.receiptValid, true)
  assert.equal(actual.emptyVisibleContentValid, true)
  assert.equal(actual.unavailableRangeValid, false)
})

test('visible-content proof rejects wrong stale replaced invalidated scrolled and destroyed receipts', async () => {
  const actual = await page.evaluate(async ({ first, second }) => {
    const wrongFixture = window.__createPaginatorFixture({ html: [first] })
    const wrongCommit = await wrongFixture.paginator.commitTextPage(0, 0, 'test-visible-wrong')
    const copiedReceipt = Object.freeze({ ...wrongCommit.receipt })
    const wrongIdentity = wrongFixture.paginator.validateTextPageVisibleContent(copiedReceipt)
    wrongFixture.destroy()

    const staleFixture = window.__createPaginatorFixture({ html: [first] })
    const staleCommit = await staleFixture.paginator.commitTextPage(0, 0, 'test-visible-stale-first')
    const currentCommit = await staleFixture.paginator.commitTextPage(0, 1, 'test-visible-stale-second')
    const stale = staleFixture.paginator.validateTextPageVisibleContent(staleCommit.receipt)
    const current = staleFixture.paginator.validateTextPageVisibleContent(currentCommit.receipt)
    staleFixture.destroy()

    const replacedFixture = window.__createPaginatorFixture({ html: [first, second] })
    const replacedCommit = await replacedFixture.paginator.commitTextPage(0, 0, 'test-visible-replaced')
    await replacedFixture.paginator.commitTextPage(1, 0, 'test-visible-replacement')
    const replaced = replacedFixture.paginator.validateTextPageVisibleContent(replacedCommit.receipt)
    replacedFixture.destroy()

    const invalidatedFixture = window.__createPaginatorFixture({ html: [first] })
    const invalidatedCommit = await invalidatedFixture.paginator.commitTextPage(0, 0, 'test-visible-invalidated')
    invalidatedFixture.paginator.setAttribute('gap', '12%')
    const invalidated = invalidatedFixture.paginator.validateTextPageVisibleContent(invalidatedCommit.receipt)
    invalidatedFixture.destroy()

    const scrolledFixture = window.__createPaginatorFixture({ html: [first] })
    const scrolledCommit = await scrolledFixture.paginator.commitTextPage(0, 0, 'test-visible-scrolled')
    scrolledFixture.paginator.setAttribute('flow', 'scrolled')
    const scrolled = scrolledFixture.paginator.validateTextPageVisibleContent(scrolledCommit.receipt)
    scrolledFixture.destroy()

    const destroyedFixture = window.__createPaginatorFixture({ html: [first] })
    const destroyedCommit = await destroyedFixture.paginator.commitTextPage(0, 0, 'test-visible-destroyed')
    destroyedFixture.paginator.destroy()
    const destroyed = destroyedFixture.paginator.validateTextPageVisibleContent(destroyedCommit.receipt)

    return { current, destroyed, invalidated, replaced, scrolled, stale, wrongIdentity }
  }, { first: sectionHtml('visible-first'), second: sectionHtml('visible-second') })

  assert.equal(actual.current, true)
  for (const [scenario, valid] of Object.entries(actual)) {
    if (scenario === 'current') continue
    assert.equal(valid, false, `${scenario} receipt retained visible-content proof`)
  }
})

test('reports out-of-range pages as mismatch and scrolled flow as unsupported', async () => {
  const actual = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    await paginator.commitTextPage(0, 0, 'test-mismatch-prime')
    const relocationPositions = []
    const recordRelocationPosition = () => {
      relocationPositions.push(paginator.exactTextPagePosition())
    }
    paginator.addEventListener('relocate', recordRelocationPosition)
    const mismatch = await paginator.commitTextPage(0, 10_000, 'test-mismatch')
    paginator.removeEventListener('relocate', recordRelocationPosition)
    const mismatchValidates = paginator.validateTextPageCommit(mismatch.receipt)
    paginator.setAttribute('flow', 'scrolled')
    const unsupported = await paginator.commitTextPage(0, 0, 'test-unsupported')
    return { mismatch, mismatchValidates, relocationPositions, unsupported }
  }, sectionHtml())

  assert.equal(actual.mismatch.status, 'mismatch')
  assert.notEqual(actual.mismatch.position, null)
  assert.notEqual(actual.mismatch.receipt, null)
  assert.equal(actual.mismatchValidates, true)
  assert.ok(
    actual.relocationPositions.every(position => position !== null),
    'Out-of-range commitment must not emit an intermediate relocation outside valid text pages.',
  )
  assert.notEqual(actual.mismatch.position.pageIndex, 10_000)
  assert.equal(actual.unsupported.status, 'unsupported')
  assert.equal(actual.unsupported.reason, 'unsupported-flow')
  assert.equal(actual.unsupported.position, null)
  assert.equal(actual.unsupported.receipt, null)
})

test('scrolled margin changes refresh View padding inputs', async () => {
  const actual = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html], flow: 'scrolled' })
    const loaded = await paginator.goTo({ index: 0, anchor: 0 })
    const viewElement = paginator.getContents()[0].doc.defaultView.frameElement.parentElement
    const initialPadding = viewElement.style.padding
    paginator.setAttribute('margin', '40px')
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    return {
      initialPadding,
      loaded,
      updatedPadding: viewElement.style.padding,
    }
  }, sectionHtml('scrolled-padding'))

  assert.equal(actual.loaded, true)
  assert.equal(actual.initialPadding, '24px 0px')
  assert.equal(actual.updatedPadding, '40px 0px')
})

test('returns unsupported when flow changes to scrolled during font readiness', async () => {
  delayedFontGate = { requested: deferred(), release: deferred() }
  await page.evaluate(({ html }) => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    window.__flowChangeCommitPromise = paginator.commitTextPage(0, 1, 'test-flow-change')
  }, { html: sectionHtml('flow-change', delayedFontCss) })

  await delayedFontGate.requested.promise
  await page.evaluate(() => {
    window.__paginatorFixture.paginator.setAttribute('flow', 'scrolled')
  })
  delayedFontGate.release.resolve()
  const result = await page.evaluate(() => window.__flowChangeCommitPromise)

  assert.equal(result.status, 'unsupported')
  assert.equal(result.reason, 'unsupported-flow')
  assert.equal(result.position, null)
  assert.equal(result.receipt, null)
})

for (const mutation of ['attribute', 'style', 'resize', 'expansion']) {
  test(`invalidates when ${mutation} changes layout while awaiting fonts`, async () => {
    delayedFontGate = { requested: deferred(), release: deferred() }
    await page.evaluate(({ html }) => {
      const { paginator } = window.__createPaginatorFixture({ html: [html] })
      window.__fontLayoutCommitPromise = paginator.commitTextPage(0, 1, 'test-font-layout-change')
    }, { html: sectionHtml(`font-${mutation}`, delayedFontCss) })

    await delayedFontGate.requested.promise
    await page.evaluate(async mutationKind => {
      const { paginator } = window.__paginatorFixture
      switch (mutationKind) {
        case 'attribute':
          paginator.setAttribute('gap', '11%')
          break
        case 'style':
          paginator.setStyles('body { letter-spacing: 0.15em !important; }')
          break
        case 'resize':
          paginator.style.width = '610px'
          break
        case 'expansion':
          paginator.getContents()[0].doc.body.style.setProperty('width', '2000px', 'important')
          break
      }
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    }, mutation)
    delayedFontGate.release.resolve()
    const result = await page.evaluate(() => window.__fontLayoutCommitPromise)

    assert.equal(result.status, 'invalidated')
    assert.equal(result.reason, 'layout-invalidated')
    assert.equal(result.position, null)
    assert.equal(result.receipt, null)
  })
}

test('rejects invalid section and page coordinates with TypeError', async () => {
  const failures = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    if (typeof paginator.commitTextPage !== 'function') {
      throw new Error('commitTextPage is unavailable')
    }
    const invalid = [-1, 0.5, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY, Number.NaN, '0']
    const attempts = [
      ...invalid.map(value => [value, 0]),
      ...invalid.map(value => [0, value]),
    ]
    return Promise.all(attempts.map(async ([index, pageIndex]) => {
      try {
        await paginator.commitTextPage(index, pageIndex, 'test-invalid-argument')
        return null
      } catch (error) {
        return error?.name
      }
    }))
  }, sectionHtml())

  assert.deepEqual(failures, Array.from({ length: 12 }, () => 'TypeError'))
})

test('book replacement discards same-index content and unloads the owning section', async () => {
  const actual = await page.evaluate(async ({ firstHtml, secondHtml }) => {
    const urls = new Set()
    const trackedSection = html => {
      let loadCount = 0
      let unloadCount = 0
      return {
        linear: 'yes',
        get loadCount() { return loadCount },
        get unloadCount() { return unloadCount },
        async load() {
          loadCount += 1
          const url = URL.createObjectURL(new Blob([html], { type: 'text/html' }))
          urls.add(url)
          return url
        },
        unload() { unloadCount += 1 },
      }
    }
    const firstSection = trackedSection(firstHtml)
    const secondSection = trackedSection(secondHtml)
    const paginator = document.createElement('foliate-paginator')
    paginator.style.cssText = 'display:block;width:560px;height:420px;'
    paginator.setAttribute('max-column-count', '1')
    paginator.setAttribute('margin', '24px')
    document.body.append(paginator)
    const firstBook = { dir: 'ltr', sections: [firstSection] }
    const secondBook = { dir: 'ltr', sections: [secondSection] }
    paginator.open(firstBook)
    const firstCommit = await paginator.commitTextPage(0, 0, 'test-first-book')
    paginator.open(secondBook)
    const contentsAfterOpen = paginator.getContents().length
    const secondCommit = await paginator.commitTextPage(0, 0, 'test-second-book')
    const publication = paginator.getContents()[0]?.doc?.body?.dataset?.publication || null
    const result = {
      contentsAfterOpen,
      firstLoadCount: firstSection.loadCount,
      firstStatus: firstCommit.status,
      firstUnloadCount: firstSection.unloadCount,
      oldReceiptValid: paginator.validateTextPageCommit(firstCommit.receipt),
      publication,
      secondLoadCount: secondSection.loadCount,
      secondReceiptValid: paginator.validateTextPageCommit(secondCommit.receipt),
      secondStatus: secondCommit.status,
      viewGenerationAdvanced: firstCommit.receipt != null && secondCommit.receipt != null
        ? secondCommit.receipt.viewGeneration > firstCommit.receipt.viewGeneration
        : null,
    }
    window.__paginatorFixtures.push({
      destroy() {
        try { paginator.destroy() } catch {}
        paginator.remove()
        for (const url of urls) URL.revokeObjectURL(url)
      },
    })
    return result
  }, {
    firstHtml: sectionHtml('first-book').replace('<body>', '<body data-publication="first">'),
    secondHtml: sectionHtml('second-book').replace('<body>', '<body data-publication="second">'),
  })

  assert.equal(actual.contentsAfterOpen, 0)
  assert.equal(actual.firstStatus, 'committed')
  assert.equal(actual.secondStatus, 'committed')
  assert.equal(actual.firstLoadCount, 1)
  assert.equal(actual.firstUnloadCount, 1)
  assert.equal(actual.oldReceiptValid, false)
  assert.equal(actual.publication, 'second')
  assert.equal(actual.secondLoadCount, 1)
  assert.equal(actual.secondReceiptValid, true)
  assert.equal(actual.viewGenerationAdvanced, true)
})

test('returns no receipt when section ownership is replaced, navigation is superseded, or paginator is destroyed', async () => {
  const actual = await page.evaluate(async html => {
    const replacementFixture = window.__createPaginatorFixture({ html: [html], deferredLoad: true })
    const replacementPending = replacementFixture.paginator.commitTextPage(0, 0, 'test-section-replacement')
    await replacementFixture.loadStarted
    replacementFixture.paginator.open({
      dir: 'ltr',
      sections: [{
        linear: 'yes',
        async load() {
          return URL.createObjectURL(new Blob([html], { type: 'text/html' }))
        },
        unload() {},
      }],
    })
    replacementFixture.releaseLoad()
    const replaced = await replacementPending

    const supersededFixture = window.__createPaginatorFixture({ html: [html], deferredLoad: true })
    const supersededPending = supersededFixture.paginator.commitTextPage(0, 0, 'test-superseded')
    await supersededFixture.loadStarted
    supersededFixture.paginator.open(supersededFixture.book)
    supersededFixture.releaseLoad()
    const superseded = await supersededPending

    const destroyedFixture = window.__createPaginatorFixture({ html: [html], deferredLoad: true })
    const destroyedPending = destroyedFixture.paginator.commitTextPage(0, 0, 'test-destroyed')
    await destroyedFixture.loadStarted
    destroyedFixture.paginator.destroy()
    destroyedFixture.releaseLoad()
    const destroyed = await destroyedPending

    return { replaced, superseded, destroyed }
  }, sectionHtml())

  for (const result of Object.values(actual)) {
    assert.ok(result.status === 'cancelled' || result.status === 'invalidated')
    assert.equal(result.receipt, null)
  }
  assert.equal(actual.replaced.reason, 'section-replaced')
  assert.equal(actual.superseded.reason, 'navigation-superseded')
  assert.equal(actual.destroyed.reason, 'paginator-destroyed')
})

test('changed attributes, styles, and explicit render invalidate before layout mutation while no-op assignments do not', async () => {
  const actual = await page.evaluate(async html => {
    const run = async mutation => {
      const fixture = window.__createPaginatorFixture({ html: [html] })
      const { paginator } = fixture
      const committed = await paginator.commitTextPage(0, 1, 'test-layout-mutation')
      const receipt = committed.receipt
      let eventCount = 0
      paginator.addEventListener('text-page-commit-invalidated', () => { eventCount += 1 })
      paginator.setAttribute('gap', paginator.getAttribute('gap'))
      paginator.setStyles(undefined)
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      const validAfterNoOps = paginator.validateTextPageCommit(receipt)
      const invalidation = window.__waitForReceiptInvalidation(paginator, receipt)
      mutation(paginator)
      const detail = await invalidation
      const validAfterMutation = paginator.validateTextPageCommit(receipt)
      fixture.destroy()
      return { detail, eventCount, validAfterNoOps, validAfterMutation }
    }
    return {
      attribute: await run(paginator => paginator.setAttribute('gap', '9%')),
      styles: await run(paginator => paginator.setStyles('body { font-size: 18px !important; }')),
      render: await run(paginator => paginator.render()),
    }
  }, sectionHtml())

  assert.equal(actual.attribute.validAfterNoOps, true)
  assert.equal(actual.attribute.eventCount, 1)
  for (const outcome of Object.values(actual)) {
    assert.equal(outcome.validAfterMutation, false)
    assert.ok(outcome.detail.layoutGeneration > outcome.detail.previousLayoutGeneration)
  }
  assert.equal(actual.attribute.detail.reason, 'attribute-change')
  assert.equal(actual.styles.detail.reason, 'style-change')
  assert.equal(actual.render.detail.reason, 'explicit-render')
})

test('reentrant invalidation preserves every layout generation advance', async () => {
  const actual = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    const first = await paginator.commitTextPage(0, 1, 'test-reentrant-first')
    const invalidations = []
    paginator.addEventListener('text-page-commit-invalidated', event => {
      invalidations.push(event.detail)
      if (invalidations.length === 1) {
        paginator.setStyles('body { color: rgb(0, 0, 200) !important; }')
      }
    })
    paginator.setStyles('body { color: rgb(200, 0, 0) !important; }')
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    const second = await paginator.commitTextPage(0, 1, 'test-reentrant-second')
    return {
      firstLayoutGeneration: first.receipt?.layoutGeneration ?? null,
      firstStatus: first.status,
      invalidations,
      secondLayoutGeneration: second.receipt?.layoutGeneration ?? null,
      secondStatus: second.status,
    }
  }, sectionHtml())

  assert.equal(actual.firstStatus, 'committed')
  assert.equal(actual.secondStatus, 'committed')
  assert.equal(actual.invalidations.length, 1)
  assert.equal(
    actual.invalidations[0].layoutGeneration,
    actual.firstLayoutGeneration + 1,
  )
  assert.equal(
    actual.invalidations[0].previousLayoutGeneration,
    actual.firstLayoutGeneration,
  )
  assert.equal(actual.secondLayoutGeneration, actual.firstLayoutGeneration + 2)
})

test('container and visual viewport resize invalidate the active layout generation', async () => {
  const actual = await page.evaluate(async html => {
    const run = async mutation => {
      const fixture = window.__createPaginatorFixture({ html: [html] })
      const { paginator } = fixture
      const committed = await paginator.commitTextPage(0, 1, 'test-resize')
      const invalidation = window.__waitForReceiptInvalidation(paginator, committed.receipt)
      mutation(paginator)
      const detail = await invalidation
      fixture.destroy()
      return detail
    }
    return {
      container: await run(paginator => { paginator.style.width = '610px' }),
      visualViewport: await run(() => visualViewport.dispatchEvent(new Event('resize'))),
    }
  }, sectionHtml())

  assert.equal(actual.container.reason, 'container-resize')
  assert.equal(actual.visualViewport.reason, 'visual-viewport-resize')
  assert.ok(actual.container.layoutGeneration > actual.container.previousLayoutGeneration)
  assert.ok(actual.visualViewport.layoutGeneration > actual.visualViewport.previousLayoutGeneration)
})

test('changed expansion metrics invalidate but settled duplicate observer work is a no-op', async () => {
  const actual = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    const committed = await paginator.commitTextPage(0, 1, 'test-expansion')
    await paginator.getContents()[0].doc.fonts.ready
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    const validAfterSettledObservers = paginator.validateTextPageCommit(committed.receipt)
    const invalidation = window.__waitForReceiptInvalidation(paginator, committed.receipt)
    const contentDocument = paginator.getContents()[0].doc
    contentDocument.body.style.setProperty('width', '2000px', 'important')
    const detail = await invalidation
    return {
      detail,
      validAfterSettledObservers,
      validAfterExpansion: paginator.validateTextPageCommit(committed.receipt),
    }
  }, sectionHtml())

  assert.equal(actual.validAfterSettledObservers, true)
  assert.equal(actual.validAfterExpansion, false)
  assert.equal(actual.detail.reason, 'view-expansion')
  assert.ok(actual.detail.layoutGeneration > actual.detail.previousLayoutGeneration)
})

test('geometry-neutral body injections do not trigger paginator expansion measurement', async () => {
  const actual = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    const committed = await paginator.commitTextPage(0, 1, 'test-neutral-injection')
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    const contentDocument = paginator.getContents()[0].doc
    const originalGetBoundingClientRect = Range.prototype.getBoundingClientRect
    let rangeLayoutReads = 0
    Range.prototype.getBoundingClientRect = function (...args) {
      rangeLayoutReads += 1
      return originalGetBoundingClientRect.apply(this, args)
    }
    try {
      const injectedMarker = contentDocument.createElement('span')
      injectedMarker.className = 'navic-synthetic-overlay-marker'
      injectedMarker.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden;'
      contentDocument.body.append(injectedMarker)
      await Promise.resolve()
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    } finally {
      Range.prototype.getBoundingClientRect = originalGetBoundingClientRect
    }
    return {
      rangeLayoutReads,
      receiptValid: paginator.validateTextPageCommit(committed.receipt),
    }
  }, sectionHtml())

  assert.equal(actual.rangeLayoutReads, 0)
  assert.equal(actual.receiptValid, true)
})

test('View expansion aborts stale DOM writes when invalidation destroys its owner', async () => {
  const actual = await page.evaluate(async html => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    await paginator.commitTextPage(0, 1, 'test-expand-destroy')
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    const contentDocument = paginator.getContents()[0].doc
    const iframe = contentDocument.defaultView.frameElement
    const viewElement = iframe.parentElement
    let staleStyleWrites = 0
    const styleObserver = new MutationObserver(records => {
      staleStyleWrites += records.filter(record => record.attributeName === 'style').length
    })
    styleObserver.observe(iframe, { attributes: true, attributeFilter: ['style'] })
    styleObserver.observe(viewElement, { attributes: true, attributeFilter: ['style'] })
    const invalidation = new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timed out waiting for expansion invalidation')), 2000)
      paginator.addEventListener('text-page-commit-invalidated', event => {
        if (event.detail.reason !== 'view-expansion') return
        clearTimeout(timeout)
        paginator.destroy()
        resolve(event.detail)
      }, { once: true })
    })
    contentDocument.body.style.setProperty('width', '2000px', 'important')
    const detail = await invalidation
    await Promise.resolve()
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    styleObserver.disconnect()
    return { detail, staleStyleWrites }
  }, sectionHtml())

  assert.equal(actual.detail.reason, 'view-expansion')
  assert.equal(actual.staleStyleWrites, 0)
})

test('exact and ordinary page movement invalidate position authority without changing layout generation', async () => {
  const actual = await page.evaluate(async html => {
    const exactFixture = window.__createPaginatorFixture({ html: [html] })
    const exactFirst = await exactFixture.paginator.commitTextPage(0, 1, 'test-exact-first')
    const exactSecond = await exactFixture.paginator.commitTextPage(0, 2, 'test-exact-second')
    const exact = {
      oldValid: exactFixture.paginator.validateTextPageCommit(exactFirst.receipt),
      newValid: exactFixture.paginator.validateTextPageCommit(exactSecond.receipt),
      oldLayoutGeneration: exactFirst.receipt.layoutGeneration,
      newLayoutGeneration: exactSecond.receipt.layoutGeneration,
    }
    exactFixture.destroy()

    const ordinaryFixture = window.__createPaginatorFixture({ html: [html] })
    const ordinaryFirst = await ordinaryFixture.paginator.commitTextPage(0, 1, 'test-ordinary-first')
    const invalidation = window.__waitForReceiptInvalidation(
      ordinaryFixture.paginator,
      ordinaryFirst.receipt,
    )
    const moved = await ordinaryFixture.paginator.next()
    const detail = await invalidation
    const ordinary = {
      moved,
      detail,
      valid: ordinaryFixture.paginator.validateTextPageCommit(ordinaryFirst.receipt),
    }
    ordinaryFixture.destroy()
    return { exact, ordinary }
  }, sectionHtml())

  assert.equal(actual.exact.oldValid, false)
  assert.equal(actual.exact.newValid, true)
  assert.equal(actual.exact.newLayoutGeneration, actual.exact.oldLayoutGeneration)
  assert.equal(actual.ordinary.moved, true)
  assert.equal(actual.ordinary.valid, false)
  assert.equal(actual.ordinary.detail.reason, 'position-moved')
  assert.equal(
    actual.ordinary.detail.layoutGeneration,
    actual.ordinary.detail.previousLayoutGeneration,
  )
})

test('view replacement and destruction invalidate receipts before discarding their view', async () => {
  const actual = await page.evaluate(async ({ first, second }) => {
    const replacementFixture = window.__createPaginatorFixture({ html: [first, second] })
    const oldCommit = await replacementFixture.paginator.commitTextPage(0, 1, 'test-view-old')
    const newCommit = await replacementFixture.paginator.commitTextPage(1, 0, 'test-view-new')
    const replacement = {
      oldValid: replacementFixture.paginator.validateTextPageCommit(oldCommit.receipt),
      newValid: replacementFixture.paginator.validateTextPageCommit(newCommit.receipt),
      viewAdvanced: newCommit.receipt.viewGeneration > oldCommit.receipt.viewGeneration,
    }
    replacementFixture.destroy()

    const destroyFixture = window.__createPaginatorFixture({ html: [first] })
    const destroyCommit = await destroyFixture.paginator.commitTextPage(0, 0, 'test-view-destroy')
    const invalidation = window.__waitForReceiptInvalidation(
      destroyFixture.paginator,
      destroyCommit.receipt,
    )
    destroyFixture.paginator.destroy()
    const destroyDetail = await invalidation
    return { replacement, destroyDetail }
  }, { first: sectionHtml('first'), second: sectionHtml('second') })

  assert.equal(actual.replacement.oldValid, false)
  assert.equal(actual.replacement.newValid, true)
  assert.equal(actual.replacement.viewAdvanced, true)
  assert.equal(actual.destroyDetail.reason, 'paginator-destroyed')
})

test('waits for current fonts and emits only bounded privacy-safe invalidation detail', async () => {
  delayedFontGate = { requested: deferred(), release: deferred() }
  const sentinel = 'private-sentinel.example/path#locator'
  await page.evaluate(({ html, sentinelValue }) => {
    const { paginator } = window.__createPaginatorFixture({ html: [html] })
    window.__fontCommitSettled = false
    window.__fontCommitPromise = paginator
      .commitTextPage(0, 1, sentinelValue)
      .then(result => {
        window.__fontCommitSettled = true
        return result
      })
  }, { html: sectionHtml('font', delayedFontCss), sentinelValue: sentinel })

  await delayedFontGate.requested.promise
  assert.equal(await page.evaluate(() => window.__fontCommitSettled), false)
  delayedFontGate.release.resolve()

  const actual = await page.evaluate(async sentinelValue => {
    const result = await window.__fontCommitPromise
    const { paginator } = window.__paginatorFixture
    const invalidation = window.__waitForReceiptInvalidation(paginator, result.receipt)
    paginator.setAttribute('margin', '30px')
    const detail = await invalidation
    return {
      status: result.status,
      validBeforeInvalidation: result.receipt !== null,
      detail,
      detailKeys: Object.keys(detail).sort(),
      serialized: JSON.stringify(detail),
      containsSentinel: JSON.stringify(detail).includes(sentinelValue),
    }
  }, sentinel)

  assert.equal(actual.status, 'committed')
  assert.equal(actual.validBeforeInvalidation, true)
  assert.deepEqual(actual.detailKeys, [
    'commitSequence',
    'layoutGeneration',
    'previousLayoutGeneration',
    'reason',
    'viewGeneration',
  ])
  assert.ok([
    'attribute-change',
    'style-change',
    'explicit-render',
    'container-resize',
    'visual-viewport-resize',
    'view-expansion',
    'view-replaced',
    'view-discarded',
    'section-committed',
    'section-replaced',
    'position-moved',
    'paginator-destroyed',
  ].includes(actual.detail.reason))
  assert.equal(actual.containsSentinel, false)
  assert.doesNotMatch(actual.serialized, /url|href|cfi|locator|text|publication|book/i)
})
