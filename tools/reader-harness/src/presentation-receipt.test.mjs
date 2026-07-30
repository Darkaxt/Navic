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

const previewTarget = Object.freeze({
  scope: ReaderPageTurnPresentationScopePreview,
  token: 'preview-token-alpha',
  pageIndex: 7,
  previewGeneration: 11,
})

const liveTarget = Object.freeze({
  scope: ReaderPageTurnPresentationScopeLive,
  token: 'live-token-alpha',
  pageIndex: 8,
  foliateSessionId: 'session-alpha',
  rasterGeneration: 13,
  textureGeneration: 17,
})

test('issuance freezes receipts and increases the presentation sequence', () => {
  const preview = issueReaderPageTurnPresentationReceipt(previewTarget)
  const live = issueReaderPageTurnPresentationReceipt(liveTarget, preview.presentationSequence)

  assert.equal(preview.presentationSequence, 1)
  assert.equal(live.presentationSequence, 2)
  assert.equal(Object.isFrozen(preview), true)
  assert.equal(Object.isFrozen(live), true)
  assert.deepEqual(Object.keys(preview).sort(), [
    'pageIndex',
    'presentationSequence',
    'previewGeneration',
    'scope',
    'token',
  ])
  assert.deepEqual(Object.keys(live).sort(), [
    'foliateSessionId',
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

const liveRuntime = ({
  previewExposed = false,
  prePreviewPagePosition = null,
  mutatePageNumberPosition = false,
} = {}) => {
  const paginationProfile = Object.freeze({ marker: 'profile-alpha' })
  const pending = Object.freeze({
    token: liveTarget.token,
    foliateSessionId: liveTarget.foliateSessionId,
    rasterGeneration: liveTarget.rasterGeneration,
    textureGeneration: liveTarget.textureGeneration,
    pageIndex: liveTarget.pageIndex,
    spineIndex: 3,
    chapterPageIndex: 5,
    paginationProfile,
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
    currentPagePosition: Object.freeze({
      pageIndex: liveTarget.pageIndex,
      spineIndex: pending.spineIndex,
      chapterPageIndex: pending.chapterPageIndex,
    }),
    pendingExactPageTurnSettlements: new Map([[pending.token, pending]]),
    completedExactPageTurnSettlements: new Map(),
    activeExactPageTurnSettlementToken: pending.token,
    nativePageTurnSettledState: null,
    nativePageTurnSettledToken: null,
    pageTurnPresentationSequence: 0,
    pageTurnLivePresentationReceiptValue: null,
    pageTurnLivePresentationTargetValue: null,
    pageTurnPreviewPresentationReceiptValue: null,
    pageTurnPreviewExposedToken: previewExposed ? 'preview-token-alpha' : '',
    pageTurnPreviewLiveVisibility: '',
    pageTurnPreviewLiveOpacity: '',
    pageTurnPreviewLivePagePosition: prePreviewPagePosition,
    pageTurnPreviewDecorationPageIndex: null,
    pageTurnPreviewView: null,
    view: { style },
    updateReaderPageNumberLayer: () => {},
    renderSurfacePaperTextureLayers: () => {},
    applyReaderViewportLayoutToProfilerView: () => {},
  }
  Object.assign(runtime, NavicReaderPageTurnMethods, NavicReaderPageTurnPreviewMethods)
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
  assert.equal(runtime.consumeNativePageTurnSettlement(liveTarget.token), true)
  assert.equal(runtime.nativePageTurnSettledState, null)

  const receipt = runtime.pageTurnLivePresentationReceipt()
  assert.notEqual(receipt, null)
  assert.equal(readerPageTurnPresentationReceiptMatches(receipt, liveTarget), true)
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
  assert.equal(runtime.restorePageTurnLiveComposition('preview-token-alpha'), true)

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
