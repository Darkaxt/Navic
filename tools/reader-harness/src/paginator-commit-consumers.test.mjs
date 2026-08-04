import assert from 'node:assert/strict'
import test from 'node:test'

const commitModulePath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js',
  import.meta.url,
)
const {
  readerCommitTextPage,
  readerCopyTextPageCommit,
  readerForgetTextPageCommit,
  readerRememberTextPageCommit,
  readerTextPageCommitIsValid,
  readerTextPageCommitMatches,
  readerTextPageCommitOwnerIsValid,
  readerTextPageCommitOwnerWasRemembered,
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

test('receipt authority copies between frozen runtime owners without serialization', () => {
  const result = committedResult()
  const renderer = validatingRenderer(result)
  const source = Object.freeze({ token: 'source-owner' })
  const target = Object.freeze({ token: 'target-owner' })

  assert.equal(readerRememberTextPageCommit(source, renderer, result.receipt), true)
  assert.equal(readerCopyTextPageCommit(source, target), true)
  assert.equal(readerTextPageCommitOwnerIsValid(source), true)
  assert.equal(readerTextPageCommitOwnerIsValid(target), true)
  assert.deepEqual(JSON.parse(JSON.stringify(target)), { token: 'target-owner' })
})

test('remembered ownership remains detectable after paginator invalidation', () => {
  const result = committedResult()
  let valid = true
  const renderer = {
    validateTextPageCommit: receipt => valid && receipt === result.receipt,
  }
  const owner = Object.freeze({ token: 'invalidated-owner' })

  assert.equal(readerRememberTextPageCommit(owner, renderer, result.receipt), true)
  assert.equal(readerTextPageCommitOwnerWasRemembered(owner), true)
  valid = false
  assert.equal(readerTextPageCommitOwnerIsValid(owner), false)
  assert.equal(readerTextPageCommitOwnerWasRemembered(owner), true)
  readerForgetTextPageCommit(owner)
  assert.equal(readerTextPageCommitOwnerWasRemembered(owner), false)
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
const turnsModulePath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js',
  import.meta.url,
)
const { NavicReaderPaginationMethods } = await import(paginationModulePath.href)
const { NavicReaderPageTurnPreviewMethods } = await import(previewModulePath.href)
const { NavicReaderPageTurnMethods } = await import(turnsModulePath.href)

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
  assert.equal(serialized.includes('"receipt":'), false)
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

const liveSettlementRuntime = ({
  counts = [3, 2],
  results = [],
  currentPagePosition = { pageIndex: 0, spineIndex: 0, chapterPageIndex: 0 },
  relocateAfterCommit = true,
  seedReceiptBeforeLayout = false,
  useProductionProfileRepair = false,
} = {}) => {
  let activeReceipt = null
  let commitIndex = 0
  let commitTail = Promise.resolve()
  let concurrentCommits = 0
  let maxConcurrentCommits = 0
  let layoutApplications = 0
  let repairs = 0
  let cancelledDelayedRelocations = 0
  const commits = []
  const controlledRelocationStarts = []
  const locationPosts = []
  const relocationDetails = []
  const scheduledRelocationDetails = []
  const profile = paginationProfile(counts)
  let runtime = null
  const renderer = new EventTarget()
  const invalidateReceipt = reason => {
    const hadReceipt = activeReceipt != null
    activeReceipt = null
    if (hadReceipt) {
      const event = new Event('text-page-commit-invalidated')
      Object.defineProperty(event, 'detail', {
        configurable: true,
        value: Object.freeze({ reason }),
      })
      renderer.dispatchEvent(event)
    }
    return hadReceipt
  }
  const globalPageForPosition = position => {
    const chapter = runtime.paginationProfile.chapters.find(entry =>
      entry.spineIndex === position.index
    )
    return chapter ? chapter.pageStartIndex + position.pageIndex : null
  }
  const publishRelocation = result => {
    if (!relocateAfterCommit || !result?.position) return null
    const globalPageIndex = globalPageForPosition(result.position)
    if (!Number.isInteger(globalPageIndex)) return null
    runtime.currentPagePosition = Object.freeze({
      pageIndex: globalPageIndex,
      spineIndex: result.position.index,
      chapterPageIndex: result.position.pageIndex,
    })
    runtime.relocateSequence += 1
    const detail = Object.freeze({
      relocationSequence: runtime.relocateSequence,
      pageIndex: globalPageIndex,
      spineIndex: result.position.index,
      chapterPageIndex: result.position.pageIndex,
    })
    runtime.lastRelocateDetail = detail
    relocationDetails.push(detail)
    return detail
  }
  renderer.commitTextPage = (index, pageIndex, reason) => {
    const resultIndex = commitIndex
    commitIndex += 1
    const run = async () => {
      concurrentCommits += 1
      maxConcurrentCommits = Math.max(maxConcurrentCommits, concurrentCommits)
      commits.push({ index, pageIndex, reason })
      try {
        const value = results[Math.min(resultIndex, results.length - 1)]
        const result = typeof value === 'function'
          ? await value({ index, pageIndex, reason, runtime })
          : value
        activeReceipt = result?.receipt || null
        publishRelocation(result)
        return result
      } finally {
        concurrentCommits -= 1
      }
    }
    const transaction = commitTail.then(run, run)
    commitTail = transaction.catch(() => {})
    return transaction
  }
  renderer.validateTextPageCommit = receipt => receipt === activeReceipt
  renderer.render = () => { invalidateReceipt('synchronous-render') }

  runtime = {
    foliateSessionId: 'live-session',
    paginationProfile: profile,
    paginationFingerprint: profile.fingerprint,
    observedChapterPageCounts: new Map(),
    currentPagePosition: Object.freeze(currentPagePosition),
    relocateSequence: 7,
    lastRelocateDetail: null,
    pendingExactPageTurnSettlements: new Map(),
    completedExactPageTurnSettlements: new Map(),
    retiredExactPageTurnSettlements: new Map(),
    activeExactPageTurnSettlementToken: null,
    nativePageTurnSettledState: null,
    nativePageTurnSettledToken: null,
    exactPageTurnNavigationToken: null,
    exactPageTurnNavigationInProgress: false,
    liveTextPageCommitInvalidationTarget: null,
    liveTextPageCommitInvalidationListener: null,
    liveTextPageCommitRetryToken: null,
    pageTurnPresentationSequence: 0,
    pageTurnPreviewExposedToken: '',
    pageTurnLivePresentationReceiptValue: null,
    pageTurnLivePresentationTargetValue: null,
    controlledRelocateOwner: null,
    controlledRelocateReason: null,
    controlledRelocateStartSequence: 0,
    view: {
      renderer,
      history: { pushState: () => {} },
    },
    beginControlledRelocation(reason) {
      const owner = Object.freeze({
        sequence: controlledRelocationStarts.length + 1,
        token: this.activeExactPageTurnSettlementToken,
      })
      this.controlledRelocateOwner = owner
      this.controlledRelocateReason = reason
      this.controlledRelocateStartSequence = this.relocateSequence
      controlledRelocationStarts.push({ owner, reason, token: owner.token })
      return owner
    },
    cancelControlledRelocation(owner) {
      if (owner !== this.controlledRelocateOwner) return false
      this.controlledRelocateOwner = null
      this.controlledRelocateReason = null
      return true
    },
    cancelPendingCommittedRelocation() {
      cancelledDelayedRelocations += 1
      scheduledRelocationDetails.length = 0
      this.controlledRelocateOwner = null
      this.controlledRelocateReason = null
    },
    clearPageTurnPreviewPresentationReceipt: () => {},
    consumeControlledRelocationReason(fallback) {
      const reason = this.controlledRelocateReason || fallback
      this.controlledRelocateOwner = null
      this.controlledRelocateReason = null
      return reason
    },
    scheduleSettledControlledPageTurnRelocation() {
      if (!this.lastRelocateDetail) return false
      scheduledRelocationDetails.push(this.lastRelocateDetail)
      this.consumeControlledRelocationReason('page-turn:exact')
      return true
    },
    scheduleCommittedRelocation(detail) {
      if (!detail) return false
      scheduledRelocationDetails.push(detail)
      return true
    },
    scheduleControlledRelocationFallback: () => {},
    applyReaderViewportLayout() {
      layoutApplications += 1
      renderer.render()
    },
    postCurrentLocationSnapshot(reason) {
      locationPosts.push(reason)
      if (useProductionProfileRepair) {
        this.readerEnsurePaginationProfile({}, {
          pageIndex: this.currentPagePosition.chapterPageIndex,
          pageCount: this.paginationProfile.chapters.find(chapter =>
            chapter.spineIndex === this.currentPagePosition.spineIndex
          )?.pageCount,
        })
      }
      return { posted: false }
    },
    repairPaginationProfileFromExactPosition(locator, actualPosition) {
      repairs += 1
      const repairedCounts = this.paginationProfile.chapters.map(chapter =>
        chapter.spineIndex === locator.spineIndex
          ? actualPosition.pageCount
          : chapter.pageCount
      )
      this.paginationProfile = paginationProfile(repairedCounts)
      return this.paginationProfile
    },
  }
  Object.assign(
    runtime,
    useProductionProfileRepair ? NavicReaderPaginationMethods : {},
    NavicReaderPageTurnMethods,
    {
      clearPageTurnPreviewPresentationReceipt: runtime.clearPageTurnPreviewPresentationReceipt,
      applyReaderViewportLayout: runtime.applyReaderViewportLayout,
      postCurrentLocationSnapshot: runtime.postCurrentLocationSnapshot,
      readerPaginationRenderFingerprint: () => profile.fingerprint,
      postPaginationProfileStatus: () => {},
      repairPaginationProfileFromExactPosition: useProductionProfileRepair
        ? function (locator, actualPosition, options) {
            repairs += 1
            return NavicReaderPaginationMethods.repairPaginationProfileFromExactPosition.call(
              this,
              locator,
              actualPosition,
              options,
            )
          }
        : runtime.repairPaginationProfileFromExactPosition,
    },
  )
  runtime.attachLiveTextPageCommitInvalidationListener()
  if (seedReceiptBeforeLayout) {
    activeReceipt = Object.freeze({
      layoutGeneration: 0,
      viewGeneration: 1,
      commitSequence: 0,
      flow: 'paginated',
      index: 0,
      pageIndex: 0,
      pageCount: counts[0],
    })
  }
  return {
    commits,
    controlledRelocationStarts,
    get cancelledDelayedRelocations() { return cancelledDelayedRelocations },
    get layoutApplications() { return layoutApplications },
    get maxConcurrentCommits() { return maxConcurrentCommits },
    get repairs() { return repairs },
    locationPosts,
    relocationDetails,
    scheduledRelocationDetails,
    renderer,
    runtime,
    invalidatePaginatorReceipt: () => invalidateReceipt('test-invalidation'),
    waitForRendererIdle: () => commitTail,
  }
}
const liveSettlementCommand = (token, pageIndex, rasterGeneration = 13) => ({
  pageIndex,
  settleToken: token,
  settleSessionId: 'live-session',
  settleRasterGeneration: rasterGeneration,
  settleTextureGeneration: rasterGeneration + 100,
})

test('live exact layout and initial commit form one serialized token operation', async () => {
  const result = resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 })
  const fixture = liveSettlementRuntime({
    results: [result],
    seedReceiptBeforeLayout: true,
  })

  await fixture.runtime.goToVisualPage(liveSettlementCommand('live-settle-1', 1))
  await flushTasks()
  await fixture.waitForRendererIdle()

  assert.deepEqual(fixture.commits, [
    { index: 0, pageIndex: 1, reason: 'page-turn:exact' },
  ])
  assert.equal(fixture.maxConcurrentCommits, 1)
  assert.equal(fixture.layoutApplications, 1)
  const pending = fixture.runtime.pendingExactPageTurnSettlements.get('live-settle-1')
  assert.equal(pending?.transactionAttempts, 1)
  assert.equal(pending?.profileRepairs, 0)
  assert.equal(readerTextPageCommitOwnerIsValid(pending), true)
  assert.equal(fixture.runtime.peekNativePageTurnSettlement()?.token, 'live-settle-1')
  assert.equal(fixture.runtime.completedExactPageTurnSettlements.has('live-settle-1'), false)
  assert.equal(fixture.runtime.consumeNativePageTurnSettlement('live-settle-1'), true)
  assert.equal(fixture.runtime.consumeNativePageTurnSettlement('live-settle-1'), false)
  assert.equal(fixture.runtime.pendingExactPageTurnSettlements.has('live-settle-1'), false)
  const serialized = JSON.stringify(
    fixture.runtime.completedExactPageTurnSettlements.get('live-settle-1')
  )
  assert.equal(serialized.includes('"receipt":'), false)
  assert.equal(serialized.includes('layoutGeneration'), false)
  assert.equal(serialized.includes('viewGeneration'), false)
  assert.equal(serialized.includes('commitSequence'), false)
})

test('live exact settlement rejects receipt coordinates that differ from its locator', async () => {
  const wrongCoordinates = resultFor({
    status: 'committed',
    requestedIndex: 0,
    requestedPageIndex: 1,
    index: 0,
    pageIndex: 0,
    pageCount: 3,
  })
  const fixture = liveSettlementRuntime({ results: [wrongCoordinates] })

  await assert.rejects(
    fixture.runtime.goToVisualPage(liveSettlementCommand('live-coordinate-mismatch', 1)),
    /not committed/,
  )
  assert.equal(fixture.runtime.nativePageTurnSettledState, null)
  assert.deepEqual(fixture.locationPosts, [])
})

test('live exact settlement waits when the current global page is not the committed destination', async () => {
  const result = resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 })
  const fixture = liveSettlementRuntime({
    results: [result],
    currentPagePosition: { pageIndex: 0, spineIndex: 0, chapterPageIndex: 0 },
    relocateAfterCommit: false,
  })

  await fixture.runtime.goToVisualPage(liveSettlementCommand('live-global-mismatch', 1))

  assert.equal(fixture.runtime.nativePageTurnSettledState, null)
  assert.equal(fixture.runtime.activeExactPageTurnSettlement()?.token, 'live-global-mismatch')
  assert.deepEqual(fixture.locationPosts, [])
})

test('invalid undelivered settlement reopens pending state and retries with fresh relocation', async () => {
  const first = resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 })
  const second = resultFor({
    requestedIndex: 0,
    requestedPageIndex: 1,
    pageCount: 3,
    receipt: Object.freeze({
      layoutGeneration: 2,
      viewGeneration: 1,
      commitSequence: 2,
      flow: 'paginated',
      index: 0,
      pageIndex: 1,
      pageCount: 3,
    }),
  })
  const fixture = liveSettlementRuntime({ results: [first, second] })
  await fixture.runtime.goToVisualPage(liveSettlementCommand('live-late-retry', 1))
  const firstDetail = fixture.relocationDetails[0]
  assert.equal(fixture.runtime.nativePageTurnSettledState?.token, 'live-late-retry')
  assert.equal(
    fixture.runtime.pendingExactPageTurnSettlements.get('live-late-retry')?.transactionAttempts,
    1,
  )
  assert.equal(fixture.runtime.completedExactPageTurnSettlements.has('live-late-retry'), false)
  assert.deepEqual(fixture.scheduledRelocationDetails, [firstDetail])

  assert.equal(fixture.invalidatePaginatorReceipt(), true)
  await flushTasks()
  await fixture.waitForRendererIdle()
  await flushTasks()

  assert.equal(fixture.commits.length, 2)
  assert.equal(fixture.cancelledDelayedRelocations, 1)
  assert.deepEqual(
    fixture.controlledRelocationStarts.map(({ reason, token }) => ({ reason, token })),
    [
      { reason: 'page-turn:exact', token: 'live-late-retry' },
      { reason: 'page-turn:exact', token: 'live-late-retry' },
    ],
  )
  assert.deepEqual(fixture.scheduledRelocationDetails, [fixture.relocationDetails[1]])
  assert.equal(
    fixture.runtime.pendingExactPageTurnSettlements.get('live-late-retry')?.transactionAttempts,
    2,
  )
  assert.equal(fixture.runtime.completedExactPageTurnSettlements.has('live-late-retry'), false)
  assert.equal(
    fixture.runtime.peekNativePageTurnSettlement()?.token,
    'live-late-retry',
  )
  assert.equal(fixture.runtime.consumeNativePageTurnSettlement('live-late-retry'), true)
  assert.equal(fixture.runtime.completedExactPageTurnSettlements.has('live-late-retry'), true)
})

test('live invalidation retries under one token and exhaustion emits no acknowledgement', async () => {
  const invalidated = ({ index, pageIndex }) => resultFor({
    status: 'invalidated',
    requestedIndex: index,
    requestedPageIndex: pageIndex,
    pageCount: 1,
    reason: 'layout-invalidated',
  })
  const committed = ({ index, pageIndex }) => resultFor({
    requestedIndex: index,
    requestedPageIndex: pageIndex,
    pageCount: 3,
  })
  const recovered = liveSettlementRuntime({ results: [invalidated, committed] })

  await recovered.runtime.goToVisualPage(liveSettlementCommand('live-retry', 1))
  assert.equal(recovered.commits.length, 2)
  assert.equal(recovered.runtime.peekNativePageTurnSettlement()?.token, 'live-retry')

  const exhausted = liveSettlementRuntime({ results: [invalidated, invalidated, invalidated] })
  const preservedPosition = exhausted.runtime.currentPagePosition
  const exhaustedCommand = liveSettlementCommand('live-exhausted', 1)
  await assert.rejects(
    exhausted.runtime.goToVisualPage(exhaustedCommand),
    /did not stabilize/,
  )
  assert.equal(exhausted.commits.length, 3)
  assert.equal(exhausted.runtime.currentPagePosition, preservedPosition)
  assert.equal(exhausted.runtime.nativePageTurnSettledState, null)
  assert.deepEqual(exhausted.locationPosts, [])
  await exhausted.runtime.goToVisualPage(exhaustedCommand)
  assert.equal(exhausted.commits.length, 3)
  assert.equal(
    exhausted.runtime.completedExactPageTurnSettlements.has('live-exhausted'),
    false,
  )
  assert.equal(
    exhausted.runtime.retiredExactPageTurnSettlements.has('live-exhausted'),
    true,
  )
})

test('active live cancellation fails without retrying or acknowledging', async () => {
  const cancelled = ({ index, pageIndex }) => resultFor({
    status: 'cancelled',
    requestedIndex: index,
    requestedPageIndex: pageIndex,
    pageCount: 1,
    reason: 'navigation-superseded',
  })
  const fixture = liveSettlementRuntime({ results: [cancelled] })

  await assert.rejects(
    fixture.runtime.goToVisualPage(liveSettlementCommand('live-cancelled', 1)),
    /cancelled/,
  )
  assert.equal(fixture.commits.length, 1)
  assert.equal(fixture.runtime.nativePageTurnSettledState, null)
  assert.equal(fixture.runtime.completedExactPageTurnSettlements.has('live-cancelled'), false)
  assert.equal(fixture.runtime.retiredExactPageTurnSettlements.has('live-cancelled'), true)
  assert.deepEqual(fixture.locationPosts, [])
})

test('profile replacement after await remaps the original global page before binding', async () => {
  let replacementProfile = null
  const fixture = liveSettlementRuntime({
    counts: [3, 2],
    relocateAfterCommit: false,
    results: [
      ({ index, pageIndex, runtime }) => {
        replacementProfile = paginationProfile([2, 2], 'replacement-fingerprint')
        runtime.paginationProfile = replacementProfile
        return resultFor({
          requestedIndex: index,
          requestedPageIndex: pageIndex,
          pageCount: 3,
        })
      },
      ({ index, pageIndex }) => resultFor({
        requestedIndex: index,
        requestedPageIndex: pageIndex,
        pageCount: 2,
      }),
    ],
  })

  await fixture.runtime.goToVisualPage(liveSettlementCommand('live-profile-remap', 2))

  assert.deepEqual(fixture.commits.map(({ index, pageIndex }) => ({ index, pageIndex })), [
    { index: 0, pageIndex: 2 },
    { index: 1, pageIndex: 0 },
  ])
  const pending = fixture.runtime.activeExactPageTurnSettlement()
  assert.equal(pending?.paginationProfile, replacementProfile)
  assert.equal(pending?.pageIndex, 2)
  assert.equal(pending?.spineIndex, 1)
  assert.equal(pending?.chapterPageIndex, 0)
  assert.equal(pending?.transactionAttempts, 2)
  assert.equal(readerTextPageCommitOwnerIsValid(pending), true)
})

test('trusted live profile repair remains current through its location snapshot', async () => {
  const fixture = liveSettlementRuntime({
    counts: [3, 2],
    currentPagePosition: { pageIndex: 2, spineIndex: 1, chapterPageIndex: 0 },
    useProductionProfileRepair: true,
    results: [
      resultFor({
        status: 'mismatch',
        requestedIndex: 0,
        requestedPageIndex: 2,
        index: 0,
        pageIndex: 1,
        pageCount: 2,
      }),
      ({ index, pageIndex }) => {
        assert.deepEqual({ index, pageIndex }, { index: 1, pageIndex: 0 })
        return resultFor({
          requestedIndex: index,
          requestedPageIndex: pageIndex,
          pageCount: 2,
        })
      },
    ],
  })

  await fixture.runtime.goToVisualPage(liveSettlementCommand('live-production-repair', 2))

  assert.equal(fixture.repairs, 1)
  assert.deepEqual(fixture.commits.map(({ index, pageIndex }) => ({ index, pageIndex })), [
    { index: 0, pageIndex: 2 },
    { index: 1, pageIndex: 0 },
  ])
  const pending = fixture.runtime.activeExactPageTurnSettlement()
  assert.deepEqual(fixture.runtime.paginationProfile.chapters.map(chapter => chapter.pageCount), [2, 2])
  assert.equal(pending?.paginationProfile, fixture.runtime.paginationProfile)
  assert.equal(pending?.profileRepairs, 1)
  assert.equal(readerTextPageCommitOwnerIsValid(pending), true)
})

test('trusted live count changes repair remap and retry while untrusted mismatch never repairs', async () => {
  const larger = liveSettlementRuntime({
    counts: [2, 2],
    results: [
      resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 }),
      resultFor({ requestedIndex: 0, requestedPageIndex: 1, pageCount: 3 }),
    ],
  })
  await larger.runtime.goToVisualPage(liveSettlementCommand('live-larger', 1))
  assert.equal(larger.repairs, 1)
  assert.deepEqual(larger.commits.map(({ index, pageIndex }) => ({ index, pageIndex })), [
    { index: 0, pageIndex: 1 },
    { index: 0, pageIndex: 1 },
  ])

  const shorter = liveSettlementRuntime({
    counts: [3, 2],
    currentPagePosition: { pageIndex: 2, spineIndex: 1, chapterPageIndex: 0 },
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
  await shorter.runtime.goToVisualPage(liveSettlementCommand('live-shorter', 2))
  assert.equal(shorter.repairs, 1)
  assert.deepEqual(shorter.commits.map(({ index, pageIndex }) => ({ index, pageIndex })), [
    { index: 0, pageIndex: 2 },
    { index: 1, pageIndex: 0 },
  ])
  assert.equal(shorter.runtime.peekNativePageTurnSettlement()?.spineIndex, 1)

  const untrusted = liveSettlementRuntime({
    results: [resultFor({
      status: 'mismatch',
      requestedIndex: 0,
      requestedPageIndex: 1,
      index: 1,
      pageIndex: 0,
      pageCount: 2,
    })],
  })
  await assert.rejects(
    untrusted.runtime.goToVisualPage(liveSettlementCommand('live-untrusted', 1)),
    /not committed/,
  )
  assert.equal(untrusted.repairs, 0)
  assert.equal(untrusted.runtime.nativePageTurnSettledState, null)
})

test('superseded live token publishes no stale settlement or location', async () => {
  let releaseFirst = () => {}
  const fixture = liveSettlementRuntime({
    currentPagePosition: { pageIndex: 2, spineIndex: 0, chapterPageIndex: 2 },
    results: [
      async ({ index, pageIndex }) => {
        await new Promise(resolve => { releaseFirst = resolve })
        return resultFor({ requestedIndex: index, requestedPageIndex: pageIndex, pageCount: 3 })
      },
      ({ index, pageIndex }) => resultFor({
        requestedIndex: index,
        requestedPageIndex: pageIndex,
        pageCount: 3,
      }),
    ],
  })

  const stale = fixture.runtime.goToVisualPage(liveSettlementCommand('live-stale', 1))
  await Promise.resolve()
  const current = fixture.runtime.goToVisualPage(liveSettlementCommand('live-current', 2))
  releaseFirst()
  await Promise.allSettled([stale, current])

  assert.equal(fixture.runtime.peekNativePageTurnSettlement()?.token, 'live-current')
  assert.equal(fixture.runtime.completedExactPageTurnSettlements.has('live-stale'), false)
  assert.equal(fixture.runtime.retiredExactPageTurnSettlements.has('live-stale'), true)
  assert.equal(fixture.locationPosts.length, 1)
})

