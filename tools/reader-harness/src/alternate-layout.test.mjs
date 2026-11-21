import assert from 'node:assert/strict'
import test from 'node:test'

// Minimal DOM: helpers.js only touches the document when its functions run.
const storage = new Map()
globalThis.document ??= {
  body: {},
  documentElement: { clientWidth: 0, clientHeight: 0 },
  createElement: () => ({ style: {} }),
  querySelector: () => null,
  addEventListener() {},
}
globalThis.window ??= globalThis
globalThis.localStorage = {
  getItem: key => (storage.has(key) ? storage.get(key) : null),
  setItem: (key, value) => storage.set(key, String(value)),
}
globalThis.getComputedStyle ??= () => ({ getPropertyValue: () => '' })

const reader = new URL('../../../composeApp/src/androidMain/assets/reader/', import.meta.url)
const { NavicReaderAlternateLayoutMethods, ReaderLayoutViewportKeyPrefix } =
  await import(new URL('navic-reader-alternate-layout.js', reader))
const { readerViewportSize } = await import(new URL('navic-reader-helpers.js', reader))

const setLiveViewport = (width, height, devicePixelRatio = 2.5) => {
  globalThis.visualViewport = { width, height }
  globalThis.devicePixelRatio = devicePixelRatio
}

const completeProfile = ({ width, height, chapters, pageCount }) => ({
  fingerprint: `fp-${width}x${height}`,
  estimatedChapterCount: 0,
  pageCount,
  chapters,
  render: { viewportWidth: width, viewportHeight: height, deviceScaleFactor: 2.5 },
})

const landscape = completeProfile({
  width: 1280,
  height: 752,
  pageCount: 60,
  chapters: [
    { spineIndex: 2, href: 'a.xhtml', pageStartIndex: 0, pageCount: 39 },
    { spineIndex: 3, href: 'b.xhtml', pageStartIndex: 39, pageCount: 21 },
  ],
})
const portrait = completeProfile({
  width: 800,
  height: 1232,
  pageCount: 141,
  chapters: [
    { spineIndex: 2, href: 'a.xhtml', pageStartIndex: 0, pageCount: 100 },
    { spineIndex: 3, href: 'b.xhtml', pageStartIndex: 100, pageCount: 41 },
  ],
})

const runtime = (overrides = {}) => {
  const profiles = new Map([[landscape.fingerprint, landscape], [portrait.fingerprint, portrait]])
  const calls = { reads: [], plans: [] }
  const self = {
    ...NavicReaderAlternateLayoutMethods,
    publicationUrl: 'https://example.invalid/book.epub',
    readerSettings: { flowMode: 'paged' },
    paginationProfile: landscape,
    paginationFingerprint: landscape.fingerprint,
    currentPagePosition: { pageIndex: 49, pageCount: 60 },
    readerPaginationRenderFingerprint() {
      const { width, height } = readerViewportSize()
      return `fp-${width}x${height}`
    },
    readCachedPaginationProfile(fingerprint) {
      calls.reads.push(fingerprint)
      return profiles.get(fingerprint) || null
    },
    pageTurnCaptureGeometry() {
      const { width, height } = readerViewportSize()
      return { viewportWidth: width, viewportHeight: height, mode: width >= height ? 'spread' : 'single' }
    },
    pageTurnRasterPreparationPlan(center, layout) {
      calls.plans.push({ center, layout })
      return { context: { centerPageIndex: center }, targets: [] }
    },
    ...overrides,
  }
  return { self, calls, profiles }
}

test('a complete profile remembers its orientation viewport (#57)', () => {
  storage.clear()
  const { self } = runtime()
  self.rememberReaderLayoutViewport(portrait)
  self.rememberReaderLayoutViewport({ render: { viewportWidth: 0, viewportHeight: 10 } })
  assert.deepEqual(JSON.parse(storage.get(`${ReaderLayoutViewportKeyPrefix}portrait`)), {
    width: 800,
    height: 1232,
    devicePixelRatio: 2.5,
  })
  assert.equal(storage.has(`${ReaderLayoutViewportKeyPrefix}landscape`), false)
})

test('the alternate layout is computed from the remembered viewport without leaking it (#57)', () => {
  storage.clear()
  setLiveViewport(1280, 752)
  const { self } = runtime()
  assert.equal(self.readerAlternateLayout(), null, 'no remembered portrait viewport yet')
  self.rememberReaderLayoutViewport(portrait)
  const layout = self.readerAlternateLayout()
  assert.equal(layout.orientation, 'portrait')
  assert.equal(layout.fingerprint, portrait.fingerprint)
  assert.equal(layout.profile, portrait)
  assert.deepEqual(layout.viewport, { width: 800, height: 1232 })
  assert.deepEqual(layout.captureGeometry, { viewportWidth: 800, viewportHeight: 1232, mode: 'single' })
  assert.deepEqual(readerViewportSize(), { width: 1280, height: 752 }, 'live viewport restored')
})

test('a different density, an incomplete profile or a non-paged flow has no alternate layout (#57)', () => {
  storage.clear()
  setLiveViewport(1280, 752, 3)
  const { self, profiles, calls } = runtime()
  self.rememberReaderLayoutViewport(portrait)
  assert.equal(self.readerAlternateLayout(), null, 'density differs')

  setLiveViewport(1280, 752)
  profiles.set(portrait.fingerprint, { ...portrait, estimatedChapterCount: 2 })
  assert.equal(self.readerAlternateLayout(), null, 'incomplete profile')
  profiles.set(portrait.fingerprint, portrait)
  assert.equal(self.readerAlternateLayout()?.profile, portrait, 'a miss is not remembered')
  const reads = calls.reads.length
  self.readerAlternateLayout()
  assert.equal(calls.reads.length, reads, 'a found profile is reused')

  assert.equal(runtime({ readerSettings: { flowMode: 'scrolled' } }).self.readerAlternateLayout(), null)
  assert.equal(runtime({ view: { isFixedLayout: true } }).self.readerAlternateLayout(), null)
})

test('the alternate plan centres on the same chapter anchor and plans the other layout (#57)', () => {
  storage.clear()
  setLiveViewport(1280, 752)
  const { self, calls, profiles } = runtime()
  assert.deepEqual(self.pageTurnAlternateLayoutPlan(), { failureReason: 'no-remembered-viewport' })
  self.rememberReaderLayoutViewport(portrait)
  profiles.delete(portrait.fingerprint)
  assert.deepEqual(self.pageTurnAlternateLayoutPlan(), { failureReason: 'no-alternate-profile' })
  profiles.set(portrait.fingerprint, portrait)
  assert.equal(calls.plans.length, 0)
  // Live page 49 is page 10 of 21 in spine 3 (anchor 0.5); portrait spine 3 spans 100..140.
  const plan = self.pageTurnAlternateLayoutPlan()
  assert.equal(plan.orientation, 'portrait')
  assert.equal(calls.plans.length, 1)
  assert.equal(calls.plans[0].center, 120)
  assert.equal(calls.plans[0].layout.profile, portrait)

  self.currentPagePosition = { pageIndex: 59, pageCount: 60 }
  assert.equal(self.readerAlternateLayoutCenterPageIndex(self.readerAlternateLayout()), 140)
  self.currentPagePosition = { pageIndex: 0, pageCount: 60 }
  assert.equal(self.readerAlternateLayoutCenterPageIndex(self.readerAlternateLayout()), 0)
})

test('alternate manifest inputs and descriptors pass the layout through (#57)', () => {
  storage.clear()
  setLiveViewport(1280, 752)
  const seen = []
  const { self } = runtime({
    pageTurnPassiveRasterManifestInputs: (...args) => (seen.push(['manifest', ...args]), { ok: true }),
    pageTurnPassiveRasterDescriptor: (...args) => (seen.push(['descriptor', ...args]), { ok: true }),
  })
  assert.deepEqual(self.pageTurnAlternatePassiveRasterManifestInputs(3, 4, 5), {
    failureReason: 'alternate-layout-unavailable',
  })
  assert.equal(self.pageTurnAlternatePassiveRasterDescriptor(3), null)
  self.rememberReaderLayoutViewport(portrait)
  self.pageTurnAlternatePassiveRasterManifestInputs(3, 4, 5)
  self.pageTurnAlternatePassiveRasterDescriptor(7)
  assert.deepEqual(seen.map(([kind, ...args]) => [kind, ...args.slice(0, -1)]), [
    ['manifest', 3, 4, 5],
    ['descriptor', 7],
  ])
  assert.ok(seen.every(entry => entry.at(-1)?.profile === portrait))
})
