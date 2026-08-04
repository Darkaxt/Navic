import assert from 'node:assert/strict'
import test from 'node:test'

const commitModulePath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js',
  import.meta.url,
)
const {
  readerCommitTextPage,
  readerForgetTextPageCommit,
  readerRememberTextPageCommit,
  readerTextPageCommitIsValid,
  readerTextPageCommitMatches,
  readerTextPageCommitOwnerIsValid,
} = await import(commitModulePath.href)

const committedResult = ({
  index = 2,
  pageIndex = 3,
  pageCount = 7,
  receipt = null,
} = {}) => {
  const position = Object.freeze({ index, pageIndex, pageCount })
  const resolvedReceipt = receipt || Object.freeze({
    layoutGeneration: 11,
    viewGeneration: 13,
    commitSequence: 17,
    flow: 'paginated',
    index,
    pageIndex,
    pageCount,
  })
  return Object.freeze({
    status: 'committed',
    requestedIndex: index,
    requestedPageIndex: pageIndex,
    position,
    receipt: resolvedReceipt,
    reason: 'exact-position',
  })
}

const validatingRenderer = result => ({
  async commitTextPage() {
    return result
  },
  validateTextPageCommit(receipt) {
    return receipt === result.receipt
  },
})

test('missing receipt API returns frozen unsupported without sampling fallbacks', async () => {
  let sampled = false
  const renderer = {
    get page() {
      sampled = true
      return 4
    },
    get pages() {
      sampled = true
      return 9
    },
    exactTextPagePosition() {
      sampled = true
      return { index: 2, pageIndex: 3, pageCount: 7 }
    },
  }

  const result = await readerCommitTextPage(renderer, 2, 3, 'pagination-profile')

  assert.deepEqual(result, {
    status: 'unsupported',
    requestedIndex: 2,
    requestedPageIndex: 3,
    position: null,
    receipt: null,
    reason: 'receipt-api-unavailable',
  })
  assert.equal(Object.isFrozen(result), true)
  assert.equal(sampled, false)
})

test('commit adapter accepts only immutable paginator result shapes', async () => {
  const result = committedResult()
  const renderer = validatingRenderer(result)

  assert.equal(await readerCommitTextPage(renderer, 2, 3, 'pagination-profile'), result)
  await assert.rejects(
    readerCommitTextPage({
      commitTextPage: async () => ({ ...result }),
      validateTextPageCommit: () => true,
    }, 2, 3, 'pagination-profile'),
    TypeError,
  )
})

test('receipt validation delegates only to the owning renderer', () => {
  const result = committedResult()
  const renderer = validatingRenderer(result)

  assert.equal(readerTextPageCommitIsValid(renderer, result), true)
  assert.equal(readerTextPageCommitIsValid({ ...renderer, validateTextPageCommit: () => false }, result), false)
  assert.equal(readerTextPageCommitIsValid({}, result), false)
})

test('commit matching compares exact section page and count coordinates', () => {
  const result = committedResult()

  assert.equal(readerTextPageCommitMatches(result, { index: 2, pageIndex: 3, pageCount: 7 }), true)
  assert.equal(readerTextPageCommitMatches(result, { index: 2, pageIndex: 4, pageCount: 7 }), false)
  assert.equal(readerTextPageCommitMatches(result, { index: 2, pageIndex: 3, pageCount: 8 }), false)
})

test('receipt ownership remains JS-local and validates through a WeakMap', () => {
  const result = committedResult()
  const renderer = validatingRenderer(result)
  const owner = Object.freeze({ token: 'runtime-owner', status: 'ready' })

  assert.equal(readerRememberTextPageCommit(owner, renderer, result.receipt), true)
  assert.equal(readerTextPageCommitOwnerIsValid(owner), true)
  assert.deepEqual(JSON.parse(JSON.stringify(owner)), {
    token: 'runtime-owner',
    status: 'ready',
  })
  assert.equal(JSON.stringify(owner).includes('layoutGeneration'), false)
  assert.equal(JSON.stringify(owner).includes('viewGeneration'), false)
  assert.equal(JSON.stringify(owner).includes('commitSequence'), false)
  assert.equal(readerForgetTextPageCommit(owner), true)
  assert.equal(readerTextPageCommitOwnerIsValid(owner), false)
})

globalThis.window = {
  devicePixelRatio: 1,
  innerWidth: 800,
  innerHeight: 1200,
  localStorage: {
    getItem: () => null,
    setItem: () => {},
  },
}
globalThis.document = {
  baseURI: 'https://neutral.invalid/',
  body: {
    append: () => {},
    contains: () => true,
  },
  documentElement: {
    clientWidth: 800,
    clientHeight: 1200,
    style: { setProperty: () => {} },
  },
  createElement: () => null,
}
globalThis.requestAnimationFrame = callback => callback(0)

const paginationModulePath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-pagination.js',
  import.meta.url,
)
const previewModulePath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js',
  import.meta.url,
)
const { NavicReaderPaginationMethods } = await import(paginationModulePath.href)
const { NavicReaderPageTurnPreviewMethods } = await import(previewModulePath.href)

const paginationProfile = (
  counts = [3, 2],
  fingerprint = 'profile-fingerprint',
  authority = 'paginator-commit-receipt',
) => {
  let pageStartIndex = 0
  const chapters = counts.map((pageCount, spineIndex) => {
    const chapter = {
      spineIndex,
      href: `chapter-${spineIndex}`,
      title: `Chapter ${spineIndex + 1}`,
      pageStartIndex,
      pageCount,
      source: 'observed',
    }
    pageStartIndex += pageCount
    return chapter
  })
  return {
    version: 'navic-pagination-v1',
    fingerprint,
    ...(authority ? { authority } : {}),
    render: {
      viewportWidth: 800,
      viewportHeight: 1200,
      runtimeVersion: 'navic-reader-pagination-profile-3',
    },
    pageCount: pageStartIndex,
    observedChapterCount: chapters.length,
    estimatedChapterCount: 0,
    chapters,
  }
}

const resultFor = ({
  status = 'committed',
  requestedIndex,
  requestedPageIndex,
  index = requestedIndex,
  pageIndex = requestedPageIndex,
  pageCount,
  receipt = null,
  reason = status === 'committed' ? 'exact-position' : 'coordinate-mismatch',
}) => {
  if (status !== 'committed' && status !== 'mismatch') {
    return Object.freeze({
      status,
      requestedIndex,
      requestedPageIndex,
      position: null,
      receipt: null,
      reason,
    })
  }
  const position = Object.freeze({ index, pageIndex, pageCount })
  const resolvedReceipt = receipt || Object.freeze({
    layoutGeneration: 1,
    viewGeneration: 1,
    commitSequence: 1,
    flow: 'paginated',
    index,
    pageIndex,
    pageCount,
  })
  return Object.freeze({
    status,
    requestedIndex,
    requestedPageIndex,
    position,
    receipt: resolvedReceipt,
    reason,
  })
}

const profilerFixture = ({ onCommit = null } = {}) => {
  const url = 'https://publication.invalid/book'
  const fingerprint = 'profile-fingerprint'
  const sections = [
    { linear: 'yes', href: 'chapter-0' },
    { linear: 'yes', href: 'chapter-1' },
    { linear: 'no', href: 'nonlinear' },
  ]
  let activeReceipt = null
  const commits = []
  const layoutEvents = []
  const renderer = {
    async commitTextPage(index, pageIndex, reason) {
      commits.push({ index, pageIndex, reason })
      layoutEvents.push(`commit:${index}`)
      const result = resultFor({
        requestedIndex: index,
        requestedPageIndex: pageIndex,
        pageCount: index === 0 ? 3 : 2,
        receipt: Object.freeze({
          layoutGeneration: index + 1,
          viewGeneration: 1,
          commitSequence: index + 1,
          flow: 'paginated',
          index,
          pageIndex,
          pageCount: index === 0 ? 3 : 2,
        }),
      })
      activeReceipt = result.receipt
      await onCommit?.(index)
      return result
    },
    validateTextPageCommit(receipt) {
      return receipt === activeReceipt
    },
  }
  const profileView = {
    dataset: {},
    renderer,
    book: { sections },
    setAttribute: () => {},
    addEventListener: () => {},
    async open() {},
    close() {},
    remove() {},
  }
  const statuses = []
  const runtime = {
    publicationUrl: url,
    paginationProfileTaskToken: 4,
    view: { isFixedLayout: false },
    readerSettings: { flowMode: 'paged' },
    sectionTargetsCover: () => false,
    applyDocumentTheme: () => {},
    applyReaderViewportLayoutToProfilerView: (_view, _settings) => {
      layoutEvents.push('layout')
    },
    postPaginationProfileStatus: (status, payload) => statuses.push({ status, payload }),
    readerPaginationRenderFingerprint: () => fingerprint,
    readerPaginationRenderMetadata: () => ({
      publicationKey: url,
      viewportWidth: 800,
      viewportHeight: 1200,
      runtimeVersion: 'navic-reader-pagination-profile-3',
    }),
  }
  Object.assign(runtime, NavicReaderPaginationMethods)
  runtime.readerPaginationRenderFingerprint = () => fingerprint
  runtime.readerPaginationRenderMetadata = () => ({
    publicationKey: url,
    viewportWidth: 800,
    viewportHeight: 1200,
    runtimeVersion: 'navic-reader-pagination-profile-3',
  })
  runtime.postPaginationProfileStatus = (status, payload) => statuses.push({ status, payload })
  runtime.applyReaderViewportLayoutToProfilerView = (_view, _settings) => {
    layoutEvents.push('layout')
  }
  document.createElement = () => profileView
  return { commits, fingerprint, layoutEvents, profileView, runtime, statuses, url }
}

test('profiler retries invalidated and immediately stale commits for the same section', async () => {
  for (const firstOutcome of ['invalidated', 'stale']) {
    const fixture = profilerFixture()
    const renderer = fixture.profileView.renderer
    let attempt = 0
    let activeReceipt = null
    renderer.commitTextPage = async (index, pageIndex) => {
      fixture.commits.push({ index, pageIndex, reason: 'pagination-profile' })
      attempt += 1
      const result = firstOutcome === 'invalidated' && attempt === 1
        ? resultFor({
            status: 'invalidated',
            requestedIndex: index,
            requestedPageIndex: pageIndex,
            pageCount: 1,
            reason: 'layout-invalidated',
          })
        : resultFor({
            requestedIndex: index,
            requestedPageIndex: pageIndex,
            pageCount: index === 0 ? 3 : 2,
          })
      activeReceipt = result.receipt
      return result
    }
    renderer.validateTextPageCommit = receipt =>
      !(firstOutcome === 'stale' && attempt === 1) && receipt === activeReceipt

    const profile = await fixture.runtime.buildCompletePaginationProfileInProfilerView({
      url: fixture.url,
      fingerprint: fixture.fingerprint,
      settings: fixture.runtime.readerSettings,
      token: fixture.runtime.paginationProfileTaskToken,
    })

    assert.deepEqual(profile.chapters.map(chapter => chapter.pageCount), [3, 2])
    assert.equal(fixture.commits.filter(commit => commit.index === 0).length, 2)
    assert.equal(fixture.commits.filter(commit => commit.index === 1).length, 1)
  }
})

test('profiler caps same-section invalidation retries at three transactions', async () => {
  const fixture = profilerFixture()
  const renderer = fixture.profileView.renderer
  renderer.commitTextPage = async (index, pageIndex) => {
    fixture.commits.push({ index, pageIndex, reason: 'pagination-profile' })
    return resultFor({
      status: 'invalidated',
      requestedIndex: index,
      requestedPageIndex: pageIndex,
      pageCount: 1,
      reason: 'layout-invalidated',
    })
  }

  await assert.rejects(
    fixture.runtime.buildCompletePaginationProfileInProfilerView({
      url: fixture.url,
      fingerprint: fixture.fingerprint,
      settings: fixture.runtime.readerSettings,
      token: fixture.runtime.paginationProfileTaskToken,
    }),
    /could not commit section 0/,
  )
  assert.equal(fixture.commits.length, 3)
})

test('profiler commits page zero once per readable section and records validated counts', async () => {
  const fixture = profilerFixture()

  const profile = await fixture.runtime.buildCompletePaginationProfileInProfilerView({
    url: fixture.url,
    fingerprint: fixture.fingerprint,
    settings: fixture.runtime.readerSettings,
    token: fixture.runtime.paginationProfileTaskToken,
  })

  assert.deepEqual(fixture.commits, [
    { index: 0, pageIndex: 0, reason: 'pagination-profile' },
    { index: 1, pageIndex: 0, reason: 'pagination-profile' },
  ])
  assert.deepEqual(profile.chapters.map(chapter => chapter.pageCount), [3, 2])
  assert.equal(profile.authority, 'paginator-commit-receipt')
  for (const index of [0, 1]) {
    const commitAt = fixture.layoutEvents.indexOf(`commit:${index}`)
    assert.equal(fixture.layoutEvents[commitAt - 1], 'layout')
  }
})

test('profiler records nothing from mismatch missing invalid or stale receipts', async () => {
  for (const outcome of ['mismatch', 'missing', 'invalid', 'stale']) {
    const fixture = profilerFixture()
    const renderer = fixture.profileView.renderer
    renderer.commitTextPage = async (index, pageIndex) => {
      const result = outcome === 'missing'
        ? Object.freeze({
            status: 'mismatch',
            requestedIndex: index,
            requestedPageIndex: pageIndex,
            position: Object.freeze({ index, pageIndex, pageCount: 3 }),
            receipt: null,
            reason: 'coordinate-mismatch',
          })
        : resultFor({
            status: outcome === 'mismatch' ? 'mismatch' : 'committed',
            requestedIndex: index,
            requestedPageIndex: pageIndex,
            pageCount: 3,
          })
      if (outcome === 'stale') fixture.runtime.paginationProfileTaskToken += 1
      renderer.validateTextPageCommit = () => outcome !== 'invalid'
      return result
    }

    const result = await fixture.runtime.buildCompletePaginationProfileInProfilerView({
      url: fixture.url,
      fingerprint: fixture.fingerprint,
      settings: fixture.runtime.readerSettings,
      token: 4,
    }).catch(() => null)

    assert.equal(result, null, `${outcome} must not produce a profile`)
  }
})

test('stale profile work cannot assign cache ready status or location snapshots', async () => {
  const writes = []
  const statuses = []
  const locations = []
  let releaseBuild
  const profile = paginationProfile()
  const runtime = {
    publicationUrl: 'https://publication.invalid/original',
    readerSettings: { flowMode: 'paged' },
    view: { isFixedLayout: false },
    paginationProfile: null,
    paginationFingerprint: null,
    paginationProfileTaskToken: 0,
    paginationProfileMeasurementInProgress: false,
    observedChapterPageCounts: new Map(),
    readerPaginationRenderFingerprint: () => 'fingerprint-original',
    readCachedPaginationProfile: () => null,
    buildCompletePaginationProfileInProfilerView: () => new Promise(resolve => {
      releaseBuild = resolve
    }),
    writeCachedPaginationProfile: value => writes.push(value),
    hydrateObservedChapterPageCountsFromProfile: () => {},
    postPaginationProfileStatus: status => statuses.push(status),
    postCurrentLocationSnapshot: reason => locations.push(reason),
  }
  Object.assign(runtime, NavicReaderPaginationMethods, {
    readerPaginationRenderFingerprint: runtime.readerPaginationRenderFingerprint,
    readCachedPaginationProfile: runtime.readCachedPaginationProfile,
    buildCompletePaginationProfileInProfilerView: runtime.buildCompletePaginationProfileInProfilerView,
    writeCachedPaginationProfile: runtime.writeCachedPaginationProfile,
    hydrateObservedChapterPageCountsFromProfile: runtime.hydrateObservedChapterPageCountsFromProfile,
    postPaginationProfileStatus: runtime.postPaginationProfileStatus,
    postCurrentLocationSnapshot: runtime.postCurrentLocationSnapshot,
  })

  const pending = runtime.ensureCompletePaginationProfile(runtime.publicationUrl, runtime.readerSettings)
  await Promise.resolve()
  runtime.publicationUrl = 'https://publication.invalid/replacement'
  runtime.readerPaginationRenderFingerprint = () => 'fingerprint-replacement'
  releaseBuild(profile)
  await pending

  assert.equal(runtime.paginationProfile, null)
  assert.deepEqual(writes, [])
  assert.deepEqual(statuses, [])
  assert.deepEqual(locations, [])
})

test('profile-3 cache rejects a complete single-section profile without receipt authority', () => {
  const fixture = profilerFixture()
  const rawProfile = paginationProfile([3], fixture.fingerprint, null)
  const originalGetItem = window.localStorage.getItem
  window.localStorage.getItem = () => JSON.stringify(rawProfile)
  try {
    assert.equal(fixture.runtime.readCachedPaginationProfile(fixture.fingerprint), null)
  } finally {
    window.localStorage.getItem = originalGetItem
  }
})

test('raw relocation profiles cannot replace or cache an authoritative receipt profile', () => {
  const fingerprint = 'profile-fingerprint'
  const authoritative = paginationProfile([3], fingerprint)
  const raw = paginationProfile([4], fingerprint, null)
  const writes = []
  const runtime = {
    paginationFingerprint: fingerprint,
    paginationProfile: authoritative,
    paginationProfileMeasurementInProgress: false,
    observedChapterPageCounts: new Map(),
    view: { isFixedLayout: false },
  }
  Object.assign(runtime, NavicReaderPaginationMethods, {
    readerPaginationRenderFingerprint: () => fingerprint,
    activeExactPageTurnSettlement: () => null,
    readerBuildPaginationProfileFromSectionPosition: () => raw,
    writeCachedPaginationProfile: profile => writes.push(profile),
  })

  const resolved = runtime.readerEnsurePaginationProfile({ index: 0 }, {
    pageIndex: 0,
    pageCount: 4,
  })

  assert.equal(resolved, authoritative)
  assert.equal(runtime.paginationProfile, authoritative)
  assert.deepEqual(writes, [])
})

test('raw relocation profiles remain provisional session state and never enter cache', () => {
  const fingerprint = 'profile-fingerprint'
  const raw = paginationProfile([4], fingerprint, null)
  const writes = []
  const runtime = {
    paginationFingerprint: fingerprint,
    paginationProfile: null,
    paginationProfileMeasurementInProgress: false,
    observedChapterPageCounts: new Map(),
    view: { isFixedLayout: false },
  }
  Object.assign(runtime, NavicReaderPaginationMethods, {
    readerPaginationRenderFingerprint: () => fingerprint,
    activeExactPageTurnSettlement: () => null,
    readerBuildPaginationProfileFromSectionPosition: () => raw,
    writeCachedPaginationProfile: profile => writes.push(profile),
  })

  const resolved = runtime.readerEnsurePaginationProfile({ index: 0 }, {
    pageIndex: 0,
    pageCount: 4,
  })

  assert.equal(resolved, raw)
  assert.equal(runtime.paginationProfile, raw)
  assert.deepEqual(writes, [])
})

test('validated passive repair promotes the repaired profile to receipt authority', () => {
  const raw = paginationProfile([2, 2], 'profile-fingerprint', null)
  const writes = []
  const runtime = {
    paginationProfile: raw,
    observedChapterPageCounts: new Map(),
    hydrateObservedChapterPageCountsFromProfile: () => {},
    writeCachedPaginationProfile: profile => writes.push(profile),
    postPaginationProfileStatus: () => {},
    postCurrentLocationSnapshot: () => {},
  }
  Object.assign(runtime, NavicReaderPaginationMethods, {
    hydrateObservedChapterPageCountsFromProfile: runtime.hydrateObservedChapterPageCountsFromProfile,
    writeCachedPaginationProfile: runtime.writeCachedPaginationProfile,
    postPaginationProfileStatus: runtime.postPaginationProfileStatus,
    postCurrentLocationSnapshot: runtime.postCurrentLocationSnapshot,
  })

  const repaired = runtime.repairPaginationProfileFromExactPosition(
    raw.chapters[0],
    { index: 0, pageIndex: 0, pageCount: 3 },
  )

  assert.equal(repaired.authority, 'paginator-commit-receipt')
  assert.equal(writes[0], repaired)
})

test('profile schema bump invalidates profile-2 fingerprints without persisting receipt generations', () => {
  const fixture = profilerFixture()
  const metadata = fixture.runtime.readerPaginationRenderMetadata()
  const profile3Fingerprint = fixture.runtime.readerPaginationRenderFingerprint()
  const serialized = JSON.stringify(paginationProfile())

  assert.equal(metadata.runtimeVersion, 'navic-reader-pagination-profile-3')
  assert.notEqual(profile3Fingerprint, 'navic-reader-pagination-profile-2')
  assert.equal(serialized.includes('layoutGeneration'), false)
  assert.equal(serialized.includes('viewGeneration'), false)
  assert.equal(serialized.includes('commitSequence'), false)
})

test('post-layout profile replacement clears stale ownership and requires committed live content', () => {
  let replacements = 0
  const runtime = {
    publicationUrl: 'https://publication.invalid/book',
    readerSettings: { flowMode: 'paged' },
    currentPagePosition: { pageIndex: 1, pageCount: 5 },
    view: {
      isFixedLayout: false,
      renderer: {
        getContents: () => [{ doc: {} }],
      },
    },
    paginationProfileTaskToken: 8,
    paginationProfileMeasurementInProgress: true,
    paginationProfile: paginationProfile(),
    paginationFingerprint: 'stale-fingerprint',
    observedChapterPageCounts: new Map([['stale', 3]]),
    ensureCompletePaginationProfile: async () => {
      replacements += 1
      return null
    },
  }
  Object.assign(runtime, NavicReaderPaginationMethods, {
    ensureCompletePaginationProfile: runtime.ensureCompletePaginationProfile,
  })

  runtime.clearPaginationProfileOwnership('settings-change')
  assert.equal(runtime.paginationProfileTaskToken, 9)
  assert.equal(runtime.paginationProfileMeasurementInProgress, false)
  assert.equal(runtime.paginationProfile, null)
  assert.equal(runtime.paginationFingerprint, null)
  assert.equal(runtime.observedChapterPageCounts.size, 0)
  assert.equal(runtime.startCompletePaginationProfileReplacementAfterLayout('settings-change'), true)
  assert.equal(replacements, 1)

  runtime.currentPagePosition = null
  assert.equal(runtime.startCompletePaginationProfileReplacementAfterLayout('initial-open'), false)
  assert.equal(replacements, 1)
})

test('scrolled profile work bypasses without creating a profiler task', async () => {
  const runtime = {
    publicationUrl: 'https://publication.invalid/book',
    readerSettings: { flowMode: 'scrolled' },
    readerFlowModeValue: 'scrolled',
    view: { isFixedLayout: false },
    paginationProfileTaskToken: 7,
    buildCompletePaginationProfileInProfilerView: () => assert.fail('scrolled profiling must bypass'),
  }
  Object.assign(runtime, NavicReaderPaginationMethods, {
    buildCompletePaginationProfileInProfilerView: runtime.buildCompletePaginationProfileInProfilerView,
  })

  assert.equal(await runtime.ensureCompletePaginationProfile(), null)
  assert.equal(runtime.paginationProfileTaskToken, 7)
})

const passiveRuntime = ({ counts = [3, 2], results = [] } = {}) => {
  const profile = paginationProfile(counts)
  let activeReceipt = null
  let commitIndex = 0
  const commits = []
  const renderer = {
    async commitTextPage(index, pageIndex, reason) {
      commits.push({ index, pageIndex, reason })
      const value = results[Math.min(commitIndex, results.length - 1)]
      commitIndex += 1
      const result = typeof value === 'function' ? await value({ index, pageIndex }) : value
      activeReceipt = result?.receipt || null
      return result
    },
    validateTextPageCommit(receipt) {
      return receipt === activeReceipt
    },
  }
  const view = { renderer }
  const runtime = {
    paginationProfile: profile,
    paginationFingerprint: profile.fingerprint,
    readerSettings: { flowMode: 'paged' },
    pageTurnPreviewGeneration: 1,
    pageTurnPreviewStateValue: null,
    pageTurnPreviewBatchStateValue: null,
    pageTurnPreviewPresentationReceiptValue: null,
    applyReaderViewportLayoutToProfilerView: () => {},
    clearPageTurnPreviewPresentationReceipt: () => {},
    repairPaginationProfileFromExactPosition(locator, actual) {
      const repairedCounts = this.paginationProfile.chapters.map(chapter =>
        chapter.spineIndex === locator.spineIndex ? actual.pageCount : chapter.pageCount
      )
      this.paginationProfile = paginationProfile(repairedCounts)
      return this.paginationProfile
    },
  }
  Object.assign(runtime, NavicReaderPageTurnPreviewMethods, {
    applyReaderViewportLayoutToProfilerView: runtime.applyReaderViewportLayoutToProfilerView,
    repairPaginationProfileFromExactPosition: runtime.repairPaginationProfileFromExactPosition,
  })
  return { commits, renderer, runtime, view }
}

test('passive resolver returns locator and opaque validated commitment', async () => {
  const result = resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 })
  const { runtime, view } = passiveRuntime({ results: [result] })

  const resolved = await runtime.resolvePageTurnPreviewLocator(
    view,
    1,
    'page-turn-preview',
    'Passive preview',
  )

  assert.deepEqual(resolved.locator, {
    pageIndex: 1,
    pageCount: 5,
    spineIndex: 0,
    href: 'chapter-0',
    chapterPageIndex: 1,
    chapterPageCount: 3,
    anchor: 0.5,
  })
  assert.equal(resolved.actualPosition, result.position)
  assert.equal(resolved.receipt, result.receipt)
  assert.equal(resolved.transactionAttempts, 1)
  assert.equal(resolved.profileRepairs, 0)
})

test('trusted larger and shorter chapter counts repair remap and retry', async () => {
  const larger = passiveRuntime({
    counts: [2, 2],
    results: [
      resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 }),
      resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 }),
    ],
  })
  const largerResolved = await larger.runtime.resolvePageTurnPreviewLocator(
    larger.view, 1, 'page-turn-raster-batch', 'Passive raster',
  )
  assert.equal(largerResolved.locator.chapterPageCount, 3)
  assert.equal(largerResolved.profileRepairs, 1)
  assert.equal(larger.commits.length, 2)

  const shorter = passiveRuntime({
    counts: [3, 2],
    results: [
      resultFor({
        status: 'mismatch',
        requestedIndex: 0,
        requestedPageIndex: 2,
        index: 0,
        pageIndex: 1,
        pageCount: 2,
      }),
      ({ index, pageIndex }) => resultFor({
        requestedIndex: index,
        requestedPageIndex: pageIndex,
        pageCount: 2,
      }),
    ],
  })
  const shorterResolved = await shorter.runtime.resolvePageTurnPreviewLocator(
    shorter.view, 2, 'page-turn-raster-batch', 'Passive raster',
  )
  assert.equal(shorterResolved.locator.spineIndex, 1)
  assert.equal(shorterResolved.locator.chapterPageIndex, 0)
  assert.equal(shorterResolved.profileRepairs, 1)
  assert.equal(shorter.commits.length, 2)
})

test('untrusted passive mismatch never repairs a pagination profile', async () => {
  for (const scenario of ['different-section', 'invalid-receipt']) {
    const result = resultFor({
      status: 'mismatch',
      requestedIndex: 0,
      requestedPageIndex: 2,
      index: scenario === 'different-section' ? 1 : 0,
      pageIndex: 0,
      pageCount: 2,
    })
    const fixture = passiveRuntime({ results: [result] })
    let repairs = 0
    fixture.runtime.repairPaginationProfileFromExactPosition = () => {
      repairs += 1
      return fixture.runtime.paginationProfile
    }
    if (scenario === 'invalid-receipt') {
      fixture.renderer.validateTextPageCommit = () => false
    }

    await assert.rejects(
      fixture.runtime.resolvePageTurnPreviewLocator(
        fixture.view, 2, 'page-turn-raster-batch', 'Passive raster',
      ),
    )
    assert.equal(repairs, 0)
  }
})

test('passive invalidation retries one item at most three times without remapping it', async () => {
  const invalidated = ({ index, pageIndex }) => resultFor({
    status: 'invalidated',
    requestedIndex: index,
    requestedPageIndex: pageIndex,
    pageCount: 1,
    reason: 'layout-invalidated',
  })
  const fixture = passiveRuntime({ results: [invalidated, invalidated, invalidated] })

  await assert.rejects(
    fixture.runtime.resolvePageTurnPreviewLocator(
      fixture.view, 1, 'page-turn-raster-batch', 'Passive raster',
    ),
  )
  assert.equal(fixture.commits.length, 3)
  assert.deepEqual(new Set(fixture.commits.map(commit => `${commit.index}:${commit.pageIndex}`)), new Set(['0:1']))
})

test('unsupported passive transaction bypasses text raster preparation', async () => {
  const unsupported = ({ index, pageIndex }) => resultFor({
    status: 'unsupported',
    requestedIndex: index,
    requestedPageIndex: pageIndex,
    pageCount: 1,
    reason: 'unsupported-flow',
  })
  const fixture = passiveRuntime({ results: [unsupported] })

  const resolved = await fixture.runtime.resolvePageTurnPreviewLocator(
    fixture.view, 1, 'page-turn-raster-batch', 'Passive raster',
  )

  assert.equal(resolved.status, 'unsupported')
  assert.equal(resolved.locator, null)
  assert.equal(resolved.receipt, null)
})

const flushTasks = async () => {
  await Promise.resolve()
  await new Promise(resolve => setImmediate(resolve))
}

test('passive ready state retains commitment only through JS-local ownership', async () => {
  const result = resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 })
  const fixture = passiveRuntime({ results: [result] })
  fixture.runtime.pageTurnPreviewGeneration = 2
  fixture.runtime.ensurePageTurnPreviewRenderer = async () => fixture.view

  await fixture.runtime.preparePageTurnPreview(2, 'preview-token', 1)

  const state = fixture.runtime.pageTurnPreviewStateValue
  assert.equal(state.status, 'ready')
  assert.equal(state.pageIndex, 1)
  assert.equal(readerTextPageCommitOwnerIsValid(state), true)
  const serialized = JSON.stringify(state)
  assert.equal(serialized.includes('receipt'), false)
  assert.equal(serialized.includes('layoutGeneration'), false)
  assert.equal(serialized.includes('viewGeneration'), false)
  assert.equal(serialized.includes('commitSequence'), false)
})

test('passive receipt diagnostics exclude reusable locators and receipts', async () => {
  const result = resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 })
  const fixture = passiveRuntime({ results: [result] })
  fixture.runtime.pageTurnPreviewGeneration = 2
  fixture.runtime.ensurePageTurnPreviewRenderer = async () => fixture.view
  window.__navicReaderTrace = []

  await fixture.runtime.preparePageTurnPreview(2, 'preview-token', 1)

  const trace = JSON.stringify(window.__navicReaderTrace)
  delete window.__navicReaderTrace
  assert.equal(trace.includes('chapter-0'), false)
  assert.equal(trace.includes('receipt'), false)
  assert.equal(trace.includes('layoutGeneration'), false)
  assert.equal(trace.includes('viewGeneration'), false)
  assert.equal(trace.includes('commitSequence'), false)
})

test('late invalidation restarts the same batch cursor without advancing it', async () => {
  const result = resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 })
  const fixture = passiveRuntime({ results: [result, result] })
  fixture.runtime.ensurePageTurnPreviewRenderer = async () => fixture.view
  let receiptIsCurrent = true
  const validate = fixture.renderer.validateTextPageCommit.bind(fixture.renderer)
  fixture.renderer.validateTextPageCommit = receipt => receiptIsCurrent && validate(receipt)

  fixture.runtime.beginPageTurnPreviewBatch('batch-token', [1])
  await flushTasks()
  const ready = fixture.runtime.pageTurnPreviewBatchStateValue
  assert.equal(ready.status, 'ready')
  assert.equal(ready.cursor, 0)
  assert.equal(ready.transactionAttempts, 1)

  receiptIsCurrent = false
  const restarting = fixture.runtime.advancePageTurnPreviewBatch('batch-token', 1)
  assert.equal(restarting.status, 'preparing')
  assert.equal(restarting.cursor, 0)
  assert.equal(restarting.pageIndex, 1)
  receiptIsCurrent = true
  await flushTasks()

  assert.equal(fixture.runtime.pageTurnPreviewBatchStateValue.status, 'ready')
  assert.equal(fixture.runtime.pageTurnPreviewBatchStateValue.cursor, 0)
  assert.equal(fixture.runtime.pageTurnPreviewBatchStateValue.transactionAttempts, 2)
  assert.equal(fixture.commits.length, 2)
})

test('stale preview tokens cannot publish a ready state', async () => {
  let releaseCommit
  const result = resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 })
  const fixture = passiveRuntime({
    results: [async ({ index, pageIndex }) => {
      await new Promise(resolve => {
        releaseCommit = resolve
      })
      return resultFor({ requestedIndex: index, requestedPageIndex: pageIndex, pageCount: 3 })
    }],
  })
  fixture.runtime.ensurePageTurnPreviewRenderer = async () => fixture.view
  fixture.runtime.pageTurnPreviewGeneration = 4

  const pending = fixture.runtime.preparePageTurnPreview(4, 'stale-token', 1)
  await Promise.resolve()
  fixture.runtime.pageTurnPreviewGeneration = 5
  releaseCommit()
  await pending

  assert.notEqual(fixture.runtime.pageTurnPreviewStateValue?.status, 'ready')
})

test('standalone and batch preparation atomically supersede each other', () => {
  const result = resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 })
  const fixture = passiveRuntime({ results: [result] })
  fixture.runtime.preparePageTurnPreview = async () => {}
  fixture.runtime.preparePageTurnPreviewBatchItem = async () => {}
  fixture.renderer.validateTextPageCommit = receipt => receipt === result.receipt
  let restorations = 0
  fixture.runtime.restorePageTurnLiveComposition = () => {
    restorations += 1
    return true
  }

  const oldStandalone = Object.freeze({
    token: 'old-standalone',
    generation: 1,
    status: 'ready',
    pageIndex: 1,
  })
  const oldBatch = Object.freeze({
    token: 'old-batch',
    itemToken: 'old-item',
    generation: 1,
    status: 'ready',
    cursor: 0,
    total: 1,
    pageIndexes: [1],
    pageIndex: 1,
  })
  assert.equal(readerRememberTextPageCommit(oldStandalone, fixture.renderer, result.receipt), true)
  assert.equal(readerRememberTextPageCommit(oldBatch, fixture.renderer, result.receipt), true)
  fixture.runtime.pageTurnPreviewStateValue = oldStandalone
  fixture.runtime.pageTurnPreviewBatchStateValue = oldBatch

  const standalone = fixture.runtime.beginPageTurnPreviewPreparation('new-standalone', 1)
  assert.equal(standalone.status, 'preparing')
  assert.equal(fixture.runtime.pageTurnPreviewBatchStateValue, null)
  assert.equal(readerTextPageCommitOwnerIsValid(oldStandalone), false)
  assert.equal(readerTextPageCommitOwnerIsValid(oldBatch), false)
  assert.equal(restorations, 1)

  const replacementStandalone = Object.freeze({
    token: 'replacement-standalone',
    generation: fixture.runtime.pageTurnPreviewGeneration,
    status: 'ready',
    pageIndex: 1,
  })
  assert.equal(readerRememberTextPageCommit(
    replacementStandalone,
    fixture.renderer,
    result.receipt,
  ), true)
  fixture.runtime.pageTurnPreviewStateValue = replacementStandalone

  const batch = fixture.runtime.beginPageTurnPreviewBatch('new-batch', [1])
  assert.equal(batch.status, 'preparing')
  assert.equal(fixture.runtime.pageTurnPreviewStateValue, null)
  assert.equal(readerTextPageCommitOwnerIsValid(replacementStandalone), false)
  assert.equal(restorations, 2)
})

test('restart refuses stale preview generations', () => {
  const fixture = passiveRuntime()
  const stale = Object.freeze({
    token: 'stale-preview',
    generation: 1,
    status: 'ready',
    pageIndex: 1,
    transactionAttempts: 1,
    profileRepairs: 0,
  })
  fixture.runtime.pageTurnPreviewGeneration = 2
  fixture.runtime.pageTurnPreviewStateValue = stale

  assert.equal(fixture.runtime.restartInvalidatedPageTurnPreviewCommitment(stale), false)
  assert.equal(fixture.runtime.pageTurnPreviewGeneration, 2)
  assert.equal(fixture.runtime.pageTurnPreviewStateValue, stale)
})

test('profile repair cap carries across passive commitment restarts', async () => {
  const mismatch = resultFor({
    status: 'mismatch',
    requestedIndex: 0,
    requestedPageIndex: 1,
    index: 0,
    pageIndex: 1,
    pageCount: 4,
  })
  const fixture = passiveRuntime({ counts: [3, 2], results: [mismatch] })
  let repairs = 0
  fixture.runtime.repairPaginationProfileFromExactPosition = () => {
    repairs += 1
    return paginationProfile([4, 2])
  }

  await assert.rejects(
    fixture.runtime.resolvePageTurnPreviewLocator(
      fixture.view,
      1,
      'page-turn-preview',
      'Passive preview',
      () => true,
      0,
      2,
    ),
  )
  assert.equal(repairs, 0)

  let restartArguments = null
  fixture.runtime.preparePageTurnPreview = async (...args) => {
    restartArguments = args
  }
  const ready = Object.freeze({
    token: 'preview-token',
    generation: 3,
    status: 'ready',
    pageIndex: 1,
    transactionAttempts: 1,
    profileRepairs: 2,
  })
  fixture.runtime.pageTurnPreviewGeneration = 3
  fixture.runtime.pageTurnPreviewStateValue = ready
  assert.equal(fixture.runtime.restartInvalidatedPageTurnPreviewCommitment(ready), true)
  assert.equal(restartArguments[4], 2)
})

test('unsupported preview preparation bypasses without a failed state', async () => {
  const unsupported = ({ index, pageIndex }) => resultFor({
    status: 'unsupported',
    requestedIndex: index,
    requestedPageIndex: pageIndex,
    pageCount: 1,
    reason: 'unsupported-flow',
  })
  const fixture = passiveRuntime({ results: [unsupported] })
  fixture.runtime.ensurePageTurnPreviewRenderer = async () => fixture.view
  fixture.runtime.pageTurnPreviewGeneration = 3

  await fixture.runtime.preparePageTurnPreview(3, 'unsupported-token', 1)

  assert.equal(fixture.runtime.pageTurnPreviewStateValue.status, 'unsupported')
})


