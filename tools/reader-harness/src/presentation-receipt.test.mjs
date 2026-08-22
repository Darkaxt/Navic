import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const presentationPath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-page-turn-presentation.js',
  import.meta.url,
)
let presentationSource
try {
  presentationSource = await readFile(presentationPath, 'utf8')
} catch {
  assert.fail('The page-turn presentation receipt module must exist')
}
const {
  ReaderPageTurnPresentationScopeLive,
  ReaderPageTurnPresentationScopePreview,
  issueReaderPageTurnPresentationReceipt,
  parseReaderPageTurnPresentationReceipt,
  readerPageTurnPresentationReceiptMatches,
} = await import(`data:text/javascript;base64,${Buffer.from(presentationSource).toString('base64')}`)

globalThis.document = { body: {}, baseURI: 'https://neutral.invalid/' }
globalThis.window = {}
const turnsPath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js',
  import.meta.url,
)
const previewPath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js',
  import.meta.url,
)
const { NavicReaderPageTurnMethods } = await import(turnsPath.href)
const { NavicReaderPageTurnPreviewMethods } = await import(previewPath.href)
const {
  readerRememberTextPageCommit,
  readerRememberTextPageVisibleContent,
} = await import(new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js',
  import.meta.url,
).href)

const previewTarget = Object.freeze({
  scope: ReaderPageTurnPresentationScopePreview,
  token: 'preview-token-alpha',
  pageIndex: 7,
  previewGeneration: 11,
  foregroundMutationGeneration: 41,
})

const liveTarget = Object.freeze({
  scope: ReaderPageTurnPresentationScopeLive,
  token: 'live-token-alpha',
  pageIndex: 8,
  foliateSessionId: 'session-alpha',
  rasterGeneration: 13,
  textureGeneration: 17,
  foregroundMutationGeneration: 41,
})

test('issuance freezes receipts and increases the presentation sequence', () => {
  const preview = issueReaderPageTurnPresentationReceipt(previewTarget)
  const live = issueReaderPageTurnPresentationReceipt(liveTarget, preview.presentationSequence)

  assert.equal(preview.presentationSequence, 1)
  assert.equal(live.presentationSequence, 2)
  assert.equal(Object.isFrozen(preview), true)
  assert.equal(Object.isFrozen(live), true)
  assert.deepEqual(Object.keys(preview).sort(), [
    'foregroundMutationGeneration',
    'pageIndex',
    'presentationSequence',
    'previewGeneration',
    'scope',
    'token',
  ])
  assert.deepEqual(Object.keys(live).sort(), [
    'foliateSessionId',
    'foregroundMutationGeneration',
    'pageIndex',
    'presentationSequence',
    'rasterGeneration',
    'scope',
    'textureGeneration',
    'token',
  ])
  assert.throws(() => {
    preview.pageIndex = 9
  }, TypeError)
})

test('preview matching requires the exact scope token page and generation', () => {
  const receipt = issueReaderPageTurnPresentationReceipt(previewTarget, 20)

  assert.equal(readerPageTurnPresentationReceiptMatches(receipt, previewTarget), true)
  for (const mismatch of [
    { ...previewTarget, scope: ReaderPageTurnPresentationScopeLive },
    { ...previewTarget, token: 'preview-token-beta' },
    { ...previewTarget, pageIndex: 6 },
    { ...previewTarget, previewGeneration: 12 },
    { ...previewTarget, foregroundMutationGeneration: 42 },
  ]) {
    assert.equal(readerPageTurnPresentationReceiptMatches(receipt, mismatch), false)
  }
})

test('live matching requires the exact scope token page session and generations', () => {
  const receipt = issueReaderPageTurnPresentationReceipt(liveTarget, 30)

  assert.equal(readerPageTurnPresentationReceiptMatches(receipt, liveTarget), true)
  for (const mismatch of [
    { ...liveTarget, scope: ReaderPageTurnPresentationScopePreview },
    { ...liveTarget, token: 'live-token-beta' },
    { ...liveTarget, pageIndex: 9 },
    { ...liveTarget, foliateSessionId: 'session-beta' },
    { ...liveTarget, rasterGeneration: 14 },
    { ...liveTarget, textureGeneration: 18 },
    { ...liveTarget, foregroundMutationGeneration: 42 },
  ]) {
    assert.equal(readerPageTurnPresentationReceiptMatches(receipt, mismatch), false)
  }
})

test('parsing accepts only complete neutral receipt shapes', () => {
  const receipt = issueReaderPageTurnPresentationReceipt(previewTarget, 40)

  assert.deepEqual(
    parseReaderPageTurnPresentationReceipt(JSON.stringify(receipt)),
    receipt,
  )
  assert.equal(Object.isFrozen(parseReaderPageTurnPresentationReceipt(receipt)), true)
  assert.equal(parseReaderPageTurnPresentationReceipt({ ...receipt, unexpected: 'value' }), null)
  assert.equal(parseReaderPageTurnPresentationReceipt({ ...receipt, pageIndex: 7.5 }), null)
  assert.equal(parseReaderPageTurnPresentationReceipt({ ...receipt, token: '' }), null)
  assert.equal(parseReaderPageTurnPresentationReceipt('{"scope":"preview"}'), null)
})

test('mutation generation is required and is a positive safe wire integer', () => {
  for (const target of [previewTarget, liveTarget]) {
    const valid = {
      ...target,
      presentationSequence: 41,
    }
    const { foregroundMutationGeneration: _, ...legacy } = valid
    assert.equal(parseReaderPageTurnPresentationReceipt(legacy), null)

    for (const malformed of [0, -1, 1.5, '41', Number.MAX_SAFE_INTEGER + 1]) {
      assert.equal(
        parseReaderPageTurnPresentationReceipt({
          ...valid,
          foregroundMutationGeneration: malformed,
        }),
        null,
      )
    }
  }
})

const liveRuntime = ({
  previewExposed = false,
  prePreviewPagePosition = null,
  mutatePageNumberPosition = false,
} = {}) => {
  const paginationProfile = Object.freeze({ marker: 'profile-alpha' })
  const paginatorReceipt = Object.freeze({
    layoutGeneration: 5,
    viewGeneration: 7,
    commitSequence: 11,
    flow: 'paginated',
    index: 3,
    pageIndex: 5,
    pageCount: 9,
  })
  let paginatorReceiptIsValid = true
  const renderer = {
    validateTextPageCommit: receipt =>
      paginatorReceiptIsValid && receipt === paginatorReceipt,
    validateTextPageVisibleContent: receipt =>
      paginatorReceiptIsValid && receipt === paginatorReceipt,
  }
  const pending = Object.freeze({
    token: liveTarget.token,
    foliateSessionId: liveTarget.foliateSessionId,
    rasterGeneration: liveTarget.rasterGeneration,
    textureGeneration: liveTarget.textureGeneration,
    foregroundMutationGeneration: liveTarget.foregroundMutationGeneration,
    pageIndex: liveTarget.pageIndex,
    spineIndex: 3,
    chapterPageIndex: 5,
    paginationProfile,
    transactionAttempts: 1,
    profileRepairs: 0,
  })
  const style = {
    getPropertyValue: () => '',
    setProperty: () => {},
    removeProperty: () => {},
  }
  const runtime = {
    foliateSessionId: liveTarget.foliateSessionId,
    paginationProfile,
    relocateSequence: 1,
    foregroundMutationGeneration: liveTarget.foregroundMutationGeneration,
    currentPagePosition: Object.freeze({
      pageIndex: liveTarget.pageIndex,
      spineIndex: pending.spineIndex,
      chapterPageIndex: pending.chapterPageIndex,
    }),
    pendingExactPageTurnSettlements: new Map([[pending.token, pending]]),
    completedExactPageTurnSettlements: new Map(),
    retiredExactPageTurnSettlements: new Map(),
    activeExactPageTurnSettlementToken: pending.token,
    nativePageTurnSettledState: null,
    nativePageTurnSettledToken: null,
    pageTurnPresentationSequence: 0,
    pageTurnLivePresentationReceiptValue: null,
    pageTurnLivePresentationTargetValue: null,
    pageTurnPreviewPresentationReceiptValue: null,
    pageTurnPreviewExposedToken: previewExposed ? 'preview-token-alpha' : '',
    pageTurnPreviewExposedMutationGeneration: previewExposed
      ? liveTarget.foregroundMutationGeneration
      : null,
    pageTurnPreviewLiveVisibility: '',
    pageTurnPreviewLiveOpacity: '',
    pageTurnPreviewLivePagePosition: prePreviewPagePosition,
    pageTurnPreviewDecorationPageIndex: null,
    pageTurnPreviewView: null,
    view: { style, renderer },
    setLivePaginatorReceiptValid: value => {
      paginatorReceiptIsValid = value === true
    },
    updateReaderPageNumberLayer: () => {},
    renderSurfacePaperTextureLayers: () => {},
    applyReaderViewportLayoutToProfilerView: () => {},
  }
  Object.assign(runtime, NavicReaderPageTurnMethods, NavicReaderPageTurnPreviewMethods)
  assert.equal(readerRememberTextPageCommit(pending, renderer, paginatorReceipt), true)
  assert.equal(readerRememberTextPageVisibleContent(pending), true)
  if (mutatePageNumberPosition) {
    runtime.updateReaderPageNumberLayer = pagePosition => {
      runtime.currentPagePosition = pagePosition
    }
  }
  return runtime
}

test('live receipt survives settlement acknowledgement consumption before getter query', () => {
  const runtime = liveRuntime()

  assert.equal(runtime.maybeCompleteNativePageTurnSettlement(), true)
  assert.equal(
    runtime.pendingExactPageTurnSettlements.get(liveTarget.token)?.transactionAttempts,
    1,
  )
  assert.equal(runtime.activeExactPageTurnSettlementToken, liveTarget.token)
  assert.equal(runtime.completedExactPageTurnSettlements.has(liveTarget.token), false)
  assert.equal(runtime.consumeNativePageTurnSettlement(liveTarget.token), true)
  assert.equal(runtime.nativePageTurnSettledState, null)
  assert.equal(runtime.pendingExactPageTurnSettlements.has(liveTarget.token), false)
  assert.equal(runtime.completedExactPageTurnSettlements.has(liveTarget.token), true)

  const receipt = runtime.pageTurnLivePresentationReceipt()
  assert.notEqual(receipt, null)
  assert.equal(readerPageTurnPresentationReceiptMatches(receipt, liveTarget), true)
})

test('live anchor authority copies the receipt and canonical commit together', () => {
  const runtime = liveRuntime()

  assert.equal(runtime.maybeCompleteNativePageTurnSettlement(), true)
  const authority = runtime.pageTurnLivePresentationAnchorAuthority()

  assert.notEqual(authority, null)
  assert.equal(
    readerPageTurnPresentationReceiptMatches(authority.presentation, liveTarget),
    true,
  )
  assert.deepEqual(authority.canonicalCommit, {
    layoutGeneration: 5,
    viewGeneration: 7,
    commitSequence: 11,
    flow: 'paginated',
    index: 3,
    pageIndex: 5,
    pageCount: 9,
  })
  assert.equal(Object.isFrozen(authority), true)
  assert.equal(Object.isFrozen(authority.canonicalCommit), true)

  runtime.setLivePaginatorReceiptValid(false)
  assert.equal(runtime.pageTurnLivePresentationAnchorAuthority(), null)
})

test('live presentation rejects paginator authority invalidated after settlement', () => {
  const runtime = liveRuntime()

  assert.equal(runtime.maybeCompleteNativePageTurnSettlement(), true)
  assert.notEqual(runtime.pageTurnLivePresentationReceipt(), null)

  runtime.setLivePaginatorReceiptValid(false)

  assert.equal(runtime.pageTurnLivePresentationReceipt(), null)
  assert.equal(runtime.restorePageTurnLivePresentationReceipt(), null)
  assert.equal(runtime.pageTurnLivePresentationTargetValue, null)
})

test('live presentation authority is cleared when its mutation generation is superseded', () => {
  const runtime = liveRuntime()

  assert.equal(runtime.maybeCompleteNativePageTurnSettlement(), true)
  assert.notEqual(runtime.pageTurnLivePresentationReceipt(), null)

  runtime.foregroundMutationGeneration = liveTarget.foregroundMutationGeneration + 1

  assert.equal(runtime.pageTurnLivePresentationReceipt(), null)
  assert.equal(runtime.restorePageTurnLivePresentationReceipt(), null)
  assert.equal(runtime.pageTurnLivePresentationTargetValue, null)
})

test('stale restoration cannot mutate composition after preview state is gone', () => {
  const runtime = liveRuntime()
  runtime.foregroundMutationGeneration = liveTarget.foregroundMutationGeneration + 1

  assert.equal(
    runtime.restorePageTurnLiveComposition(
      '',
      liveTarget.foregroundMutationGeneration,
    ),
    false,
  )
})

test('restoring live composition preserves a newer settlement completed behind preview', () => {
  const oldPagePosition = Object.freeze({
    pageIndex: liveTarget.pageIndex - 1,
    spineIndex: 2,
    chapterPageIndex: 4,
  })
  const runtime = liveRuntime({
    previewExposed: true,
    prePreviewPagePosition: oldPagePosition,
    mutatePageNumberPosition: true,
  })

  assert.equal(runtime.maybeCompleteNativePageTurnSettlement(), true)
  assert.equal(runtime.pageTurnLivePresentationReceiptValue, null)
  assert.equal(runtime.consumeNativePageTurnSettlement(liveTarget.token), true)
  assert.equal(
    runtime.restorePageTurnLiveComposition(
      'preview-token-alpha',
      liveTarget.foregroundMutationGeneration,
    ),
    true,
  )

  assert.equal(runtime.currentPagePosition.pageIndex, liveTarget.pageIndex)
  const receipt = runtime.pageTurnLivePresentationReceipt()
  assert.notEqual(receipt, null)
  assert.equal(readerPageTurnPresentationReceiptMatches(receipt, liveTarget), true)
})

test('live receipt cannot resurrect after an unobserved A to B to A relocation', () => {
  const runtime = liveRuntime()

  assert.equal(runtime.maybeCompleteNativePageTurnSettlement(), true)
  assert.equal(runtime.consumeNativePageTurnSettlement(liveTarget.token), true)
  runtime.currentPagePosition = Object.freeze({
    pageIndex: liveTarget.pageIndex + 1,
    spineIndex: 4,
    chapterPageIndex: 0,
  })
  runtime.relocateSequence += 1
  runtime.currentPagePosition = Object.freeze({
    pageIndex: liveTarget.pageIndex,
    spineIndex: 3,
    chapterPageIndex: 5,
  })
  runtime.relocateSequence += 1

  assert.equal(runtime.pageTurnLivePresentationReceipt(), null)
})

test('restore keeps committed passive raster authority through descriptor persistence and batch advance', () => {
  const styleState = () => {
    const values = new Map()
    return {
      getPropertyValue: name => values.get(name) || '',
      setProperty: (name, value) => values.set(name, String(value)),
      removeProperty: name => values.delete(name),
    }
  }
  const liveStyle = styleState()
  const previewStyle = styleState()
  const paginatorReceipt = Object.freeze({
    layoutGeneration: 1,
    viewGeneration: 1,
    commitSequence: 1,
    flow: 'paginated',
    index: 0,
    pageIndex: 1,
    pageCount: 3,
  })
  let receiptIsValid = true
  let hiddenLayoutApplications = 0
  let rendererRenders = 0
  const renderer = {
    validateTextPageCommit: receipt => receiptIsValid && receipt === paginatorReceipt,
    render: () => {
      rendererRenders += 1
      receiptIsValid = false
    },
  }
  const identity = Object.freeze({
    token: 'batch:item',
    generation: 4,
    foregroundMutationGeneration: 41,
    status: 'ready',
    pageIndex: 1,
    visualPageOrdinal: 1,
    spineIndex: 0,
    href: 'chapter-0',
    chapterPageIndex: 1,
    chapterPageCount: 3,
    transactionAttempts: 1,
    profileRepairs: 0,
  })
  const batch = Object.freeze({
    ...identity,
    token: 'batch',
    itemToken: identity.token,
    cursor: 0,
    total: 1,
    pageIndexes: [1],
  })
  const runtime = {
    publicationUrl: 'https://publication.invalid/book',
    paginationFingerprint: 'profile-fingerprint',
    paginationProfile: {
      render: {},
      pageCount: 3,
      chapters: [{
        spineIndex: 0,
        href: 'chapter-0',
        pageStartIndex: 0,
        pageCount: 3,
      }],
    },
    readerSettings: {},
    currentPagePosition: { pageIndex: 0, pageCount: 3 },
    pageTurnPreviewGeneration: 4,
    foregroundMutationGeneration: 41,
    pageTurnPreviewStateValue: identity,
    pageTurnPreviewBatchStateValue: batch,
    pageTurnPreviewView: { style: previewStyle, renderer },
    pageTurnPreviewExposedToken: '',
    pageTurnPreviewPresentationReceiptValue: null,
    pageTurnPresentationSequence: 0,
    pageTurnPreviewLiveVisibility: '',
    pageTurnPreviewLiveOpacity: '',
    pageTurnPreviewLivePagePosition: null,
    pageTurnPreviewDecorationPageIndex: null,
    pageTurnLivePresentationReceiptValue: null,
    pageTurnLivePresentationTargetValue: null,
    view: { style: liveStyle },
    updateReaderPageNumberLayer: () => {},
    renderSurfacePaperTextureLayers: () => {},
    pageDragPreviewDimensions: () => ({ width: 800, height: 1200 }),
    applyReaderViewportLayoutToProfilerView: () => {
      hiddenLayoutApplications += 1
      receiptIsValid = false
    },
  }
  Object.assign(runtime, NavicReaderPageTurnMethods, NavicReaderPageTurnPreviewMethods, {
    updateReaderPageNumberLayer: runtime.updateReaderPageNumberLayer,
    renderSurfacePaperTextureLayers: runtime.renderSurfacePaperTextureLayers,
    pageDragPreviewDimensions: runtime.pageDragPreviewDimensions,
    applyReaderViewportLayoutToProfilerView: runtime.applyReaderViewportLayoutToProfilerView,
    pageTurnCaptureGeometry: () => ({
      mode: 'single',
      pages: [],
      viewportWidth: 800,
      viewportHeight: 1200,
    }),
    preparePageTurnPreviewBatchItem: async () => {},
  })
  const originalGetComputedStyle = window.getComputedStyle
  window.getComputedStyle = element => ({
    display: element.style.getPropertyValue('display') || 'block',
    visibility: element.style.getPropertyValue('visibility') || 'visible',
    opacity: element.style.getPropertyValue('opacity') || '1',
  })
  try {
    assert.equal(readerRememberTextPageCommit(identity, renderer, paginatorReceipt), true)
    assert.equal(readerRememberTextPageCommit(batch, renderer, paginatorReceipt), true)
    assert.equal(runtime.exposePageTurnPreviewFinal(identity.token, 41), true)
    assert.equal(runtime.confirmPageTurnPreviewPresentation(identity.token, 41), true)
    assert.notEqual(runtime.pageTurnPreviewPresentationReceipt(), null)

    assert.equal(runtime.restorePageTurnLiveComposition(identity.token, 41), true)
    assert.equal(hiddenLayoutApplications, 0)
    assert.equal(rendererRenders, 0)
    assert.equal(receiptIsValid, true)
    assert.equal(previewStyle.getPropertyValue('visibility'), 'hidden')
    assert.equal(previewStyle.getPropertyValue('z-index'), '-1')
    assert.notEqual(runtime.pageTurnRasterDescriptor(1), null)
    assert.equal(runtime.advancePageTurnPreviewBatch('batch', 1, 41).status, 'complete')
  } finally {
    window.getComputedStyle = originalGetComputedStyle
  }
})

test('preview presentation receipt rejects stale paginator authority', () => {
  const state = Object.freeze({
    token: previewTarget.token,
    generation: previewTarget.previewGeneration,
    foregroundMutationGeneration: previewTarget.foregroundMutationGeneration,
    status: 'ready',
    pageIndex: previewTarget.pageIndex,
    transactionAttempts: 1,
  })
  const paginatorReceipt = Object.freeze({
    layoutGeneration: 1,
    viewGeneration: 1,
    commitSequence: 1,
    flow: 'paginated',
    index: 2,
    pageIndex: 3,
    pageCount: 7,
  })
  let paginatorReceiptIsValid = true
  const renderer = {
    validateTextPageCommit: receipt =>
      paginatorReceiptIsValid && receipt === paginatorReceipt,
  }
  const runtime = {
    pageTurnPreviewStateValue: state,
    pageTurnPreviewBatchStateValue: null,
    pageTurnPreviewGeneration: previewTarget.previewGeneration,
    foregroundMutationGeneration: previewTarget.foregroundMutationGeneration,
    pageTurnPreviewView: { renderer },
    pageTurnPreviewExposedToken: previewTarget.token,
    pageTurnPreviewPresentationReceiptValue: null,
    pageTurnPresentationSequence: 0,
  }
  Object.assign(runtime, NavicReaderPageTurnPreviewMethods)
  assert.equal(readerRememberTextPageCommit(state, renderer, paginatorReceipt), true)
  runtime.issuePageTurnPreviewPresentationReceipt(previewTarget)

  paginatorReceiptIsValid = false

  assert.equal(runtime.pageTurnPreviewPresentationReceipt(), null)
})
