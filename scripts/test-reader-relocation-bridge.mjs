import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

globalThis.document = { body: {}, baseURI: 'https://reader.test/' }
globalThis.window = {}
const source = await readFile(resolve(
  'composeApp/src/androidMain/assets/reader/navic-reader-bridge-core.js'
), 'utf8')
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const { post } = await import(moduleUrl)

assert.equal(post({ type: 'locationChanged' }), false)
window.NavicAndroidBridge = {
  postMessage: () => { throw new Error('injected bridge failure') },
}
assert.equal(post({ type: 'locationChanged' }), false)
const circular = { type: 'locationChanged' }
circular.self = circular
assert.equal(post(circular), false)
let delivered = null
window.NavicAndroidBridge = {
  postMessage: json => { delivered = JSON.parse(json) },
}
assert.equal(post({ type: 'locationChanged', pageIndex: 4 }), true)
assert.deepEqual(delivered, { type: 'locationChanged', pageIndex: 4 })

const pageTurnModuleUrl = pathToFileURL(resolve(
  'composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js'
)).href
const { NavicReaderPageTurnMethods } = await import(pageTurnModuleUrl)
const paginatorCommitModuleUrl = pathToFileURL(resolve(
  'composeApp/src/androidMain/assets/reader/navic-reader-paginator-commit.js'
)).href
const {
  readerRememberTextPageCommit,
  readerRememberTextPageVisibleContent,
  readerTextPageCommitOwnerIsValid,
} = await import(paginatorCommitModuleUrl)
const locationModuleUrl = pathToFileURL(resolve(
  'composeApp/src/androidMain/assets/reader/navic-reader-location.js'
)).href
const { NavicReaderLocationMethods } = await import(locationModuleUrl)
const paginationProfile = Object.freeze({
  pageCount: 64,
  chapters: Object.freeze([
    Object.freeze({
      pageStartIndex: 0,
      pageCount: 16,
      spineIndex: 0,
      href: 'test-section-0',
    }),
    Object.freeze({
      pageStartIndex: 16,
      pageCount: 48,
      spineIndex: 1,
      href: 'test-section-1',
    }),
  ]),
})
let navigationCount = 0
let commitSequence = 0
let activePaginatorReceipt = null
let commitTail = Promise.resolve()
let concurrentCommits = 0
let maxConcurrentCommits = 0
const committedCoordinates = []
let synchronousLayoutInvalidations = 0
let synchronousSnapshotOptions = null
let runtime = null
const renderer = new EventTarget()
const invalidatePaginatorReceipt = reason => {
  const hadReceipt = activePaginatorReceipt != null
  activePaginatorReceipt = null
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
renderer.commitTextPage = (index, pageIndex, reason) => {
  const run = async () => {
    concurrentCommits += 1
    maxConcurrentCommits = Math.max(maxConcurrentCommits, concurrentCommits)
    navigationCount += 1
    committedCoordinates.push({ index, pageIndex })
    try {
      const chapter = runtime.paginationProfile.chapters.find(entry =>
        entry.spineIndex === index
      )
      const pageCount = chapter?.pageCount || 1
      const position = Object.freeze({ index, pageIndex, pageCount })
      activePaginatorReceipt = Object.freeze({
        layoutGeneration: 1,
        viewGeneration: 1,
        commitSequence: ++commitSequence,
        flow: 'paginated',
        index,
        pageIndex,
        pageCount,
      })
      const result = Object.freeze({
        status: 'committed',
        requestedIndex: index,
        requestedPageIndex: pageIndex,
        position,
        receipt: activePaginatorReceipt,
        reason,
      })
      const chapterPosition = runtime.paginationProfile.chapters.find(entry =>
        entry.spineIndex === position.index
      )
      const pagePosition = Object.freeze({
        pageIndex: chapterPosition.pageStartIndex + position.pageIndex,
        spineIndex: position.index,
        chapterPageIndex: position.pageIndex,
      })
      const relocation = new Event('relocate')
      Object.defineProperty(relocation, 'detail', {
        configurable: true,
        value: Object.freeze({ pagePosition }),
      })
      renderer.dispatchEvent(relocation)
      return result
    } finally {
      concurrentCommits -= 1
    }
  }
  const transaction = commitTail.then(run, run)
  commitTail = transaction.catch(() => {})
  return transaction
}
renderer.validateTextPageCommit = receipt => receipt === activePaginatorReceipt
renderer.validateTextPageVisibleContent = receipt => receipt === activePaginatorReceipt
renderer.render = () => {
  if (invalidatePaginatorReceipt('synchronous-layout')) {
    synchronousLayoutInvalidations += 1
  }
}
renderer.addEventListener('relocate', event => {
  runtime.currentPagePosition = event.detail.pagePosition
  runtime.relocateSequence += 1
  runtime.lastRelocateDetail = Object.freeze({
    ...event.detail,
    relocationSequence: runtime.relocateSequence,
  })
})

runtime = {
  foliateSessionId: 'foliate-test',
  paginationProfile,
  currentPagePosition: null,
  relocateSequence: 9,
  foregroundMutationGeneration: 0,
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
  liveTextPageCommitRetryRequestedToken: null,
  pageTurnPresentationSequence: 0,
  pageTurnPreviewExposedToken: null,
  pageTurnLivePresentationTargetValue: null,
  pageTurnLivePresentationReceiptValue: null,
  controlledRelocateOwner: null,
  controlledRelocateReason: null,
  controlledRelocateStartSequence: 0,
  view: {
    renderer,
    history: {
      pushState: () => {},
    },
  },
  beginControlledRelocation(reason) {
    const owner = Object.freeze({
      token: this.activeExactPageTurnSettlementToken,
      startSequence: this.relocateSequence,
    })
    this.controlledRelocateOwner = owner
    this.controlledRelocateReason = reason
    this.controlledRelocateStartSequence = this.relocateSequence
    return owner
  },
  cancelControlledRelocation(owner) {
    if (owner !== this.controlledRelocateOwner) return false
    this.controlledRelocateOwner = null
    this.controlledRelocateReason = null
    return true
  },
  cancelPendingCommittedRelocation() {
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
  postCurrentLocationSnapshot: (_reason, options) => {
    synchronousSnapshotOptions = options
    return { posted: false }
  },
  scheduleSettledControlledPageTurnRelocation() {
    if (
      this.controlledRelocateReason !== 'page-turn:exact' ||
      !this.lastRelocateDetail ||
      this.lastRelocateDetail.relocationSequence <= this.controlledRelocateStartSequence
    ) return false
    this.consumeControlledRelocationReason('page-turn:exact')
    return true
  },
  scheduleControlledRelocationFallback: () => {},
  applyReaderViewportLayout() {
    renderer.render()
  },
}
Object.assign(runtime, NavicReaderPageTurnMethods)
runtime.attachLiveTextPageCommitInvalidationListener()
let nextMutationGeneration = 40
const mutationGenerationByToken = new Map()
const settlementCommand = (token, pageIndex, rasterGeneration = pageIndex) => {
  if (!mutationGenerationByToken.has(token)) {
    nextMutationGeneration += 1
    mutationGenerationByToken.set(token, nextMutationGeneration)
  }
  return {
    pageIndex,
    settleToken: token,
    settleGestureId: pageIndex + 1,
    settleSessionId: runtime.foliateSessionId,
    settleRasterGeneration: rasterGeneration,
    settleTextureGeneration: pageIndex + 100,
    settleForegroundMutationGeneration: mutationGenerationByToken.get(token),
  }
}
const positionAt = pageIndex => {
  const chapter = paginationProfile.chapters.find(entry =>
    pageIndex >= entry.pageStartIndex && pageIndex < entry.pageStartIndex + entry.pageCount
  )
  return {
    pageIndex,
    spineIndex: chapter.spineIndex,
    chapterPageIndex: pageIndex - chapter.pageStartIndex,
  }
}

for (let pageIndex = 0; pageIndex < 33; pageIndex += 1) {
  const token = `settle-${pageIndex}`
  await runtime.goToVisualPage(settlementCommand(token, pageIndex))
  await commitTail
  assert.deepEqual(runtime.currentPagePosition, positionAt(pageIndex))
  const pending = runtime.pendingExactPageTurnSettlements.get(token)
  assert.equal(pending?.transactionAttempts, 1)
  assert.equal(pending?.profileRepairs, 0)
  assert.equal(readerTextPageCommitOwnerIsValid(pending), true)
  const settlement = runtime.peekNativePageTurnSettlement()
  assert.equal(settlement?.token, token)
  assert.equal(settlement?.gestureId, pageIndex + 1)
  assert.equal(settlement?.foliateSessionId, runtime.foliateSessionId)
  assert.equal(settlement?.rasterGeneration, pageIndex)
  assert.equal(settlement?.textureGeneration, pageIndex + 100)
  assert.equal(
    settlement?.foregroundMutationGeneration,
    mutationGenerationByToken.get(token),
  )
  assert.equal(settlement?.paginationProfile, paginationProfile)
  assert.equal(runtime.pageTurnLivePresentationTargetValue?.relocationEpoch, 10 + pageIndex)
  assert.equal(runtime.pageTurnLivePresentationTargetValue?.foliateSessionId, runtime.foliateSessionId)
  assert.equal(runtime.pageTurnLivePresentationTargetValue?.rasterGeneration, pageIndex)
  assert.equal(runtime.pageTurnLivePresentationTargetValue?.textureGeneration, pageIndex + 100)
  assert.equal(settlement?.spineIndex, pageIndex < 16 ? 0 : 1)
  assert.equal(settlement?.chapterPageIndex, pageIndex < 16 ? pageIndex : pageIndex - 16)
  assert.equal(JSON.stringify(pending).includes('receipt'), false)
  assert.equal(JSON.stringify(settlement).includes('receipt'), false)
  assert.equal(runtime.completedExactPageTurnSettlements.has(token), false)
  assert.equal(runtime.consumeNativePageTurnSettlement(token), true)
  assert.equal(runtime.pendingExactPageTurnSettlements.has(token), false)
}
assert.equal(navigationCount, 33)
assert.equal(maxConcurrentCommits, 1)
assert.equal(synchronousLayoutInvalidations, 32)
assert.deepEqual(committedCoordinates.slice(15, 17), [
  { index: 0, pageIndex: 15 },
  { index: 1, pageIndex: 0 },
])
assert.equal(runtime.completedExactPageTurnSettlements.size, 33)
assert.equal(synchronousSnapshotOptions?.forceDuplicatePost, true)
assert.equal(synchronousSnapshotOptions?.preserveCurrentPagePosition, true)

await runtime.goToVisualPage(settlementCommand('settle-0', 0))
assert.equal(navigationCount, 33)
await assert.rejects(
  runtime.goToVisualPage(settlementCommand('settle-0', 1, 0)),
  /settlement token cannot be reused/
)
assert.equal(navigationCount, 33)

await runtime.goToVisualPage(settlementCommand('settle-cancelled', 40))
assert.equal(runtime.nativePageTurnSettledState?.token, 'settle-cancelled')
assert.equal(runtime.cancelPendingExactPageTurnSettlement('test-cancel'), true)
assert.equal(runtime.nativePageTurnSettledState, null)
assert.equal(runtime.pendingExactPageTurnSettlements.has('settle-cancelled'), false)
assert.equal(runtime.completedExactPageTurnSettlements.has('settle-cancelled'), false)
assert.equal(runtime.retiredExactPageTurnSettlements.has('settle-cancelled'), true)
assert.equal(runtime.peekNativePageTurnSettlement(), null)

await runtime.goToVisualPage(settlementCommand('settle-stale', 41))
assert.equal(runtime.nativePageTurnSettledState?.token, 'settle-stale')
runtime.currentPagePosition = {
  ...positionAt(41),
  chapterPageIndex: 42,
}
assert.equal(runtime.peekNativePageTurnSettlement(), null)
assert.equal(runtime.nativePageTurnSettledState, null)
assert.equal(runtime.pendingExactPageTurnSettlements.has('settle-stale'), true)
assert.equal(runtime.completedExactPageTurnSettlements.has('settle-stale'), false)

const pendingCommand = settlementCommand('settle-pending', 42)
await runtime.goToVisualPage(pendingCommand)
assert.equal(runtime.retiredExactPageTurnSettlements.has('settle-stale'), true)
const pendingNavigationCount = navigationCount
await runtime.goToVisualPage(pendingCommand)
assert.equal(navigationCount, pendingNavigationCount)
await assert.rejects(
  runtime.goToVisualPage({ ...pendingCommand, pageIndex: 43 }),
  /settlement token cannot be reused/
)
assert.equal(runtime.cancelPendingExactPageTurnSettlement('test-pending'), true)
assert.equal(runtime.completedExactPageTurnSettlements.has('settle-pending'), false)
assert.equal(runtime.retiredExactPageTurnSettlements.has('settle-pending'), true)

let staleRecomputationCount = 0
delivered = null
const preservedPosition = positionAt(7)
const preservedPaginatorReceipt = Object.freeze({
  layoutGeneration: 2,
  viewGeneration: 3,
  commitSequence: 5,
  flow: 'paginated',
  index: preservedPosition.spineIndex,
  pageIndex: preservedPosition.chapterPageIndex,
  pageCount: 16,
})
const preservedRenderer = {
  validateTextPageCommit: receipt => receipt === preservedPaginatorReceipt,
  validateTextPageVisibleContent: receipt => receipt === preservedPaginatorReceipt,
}
const preservedPending = Object.freeze({
  token: 'settle-preserved',
  foliateSessionId: runtime.foliateSessionId,
  rasterGeneration: 7,
  textureGeneration: 107,
  foregroundMutationGeneration: 41,
  pageIndex: preservedPosition.pageIndex,
  spineIndex: preservedPosition.spineIndex,
  chapterPageIndex: preservedPosition.chapterPageIndex,
  paginationProfile,
  transactionAttempts: 1,
  profileRepairs: 0,
})
const preservedSettlement = Object.freeze({
  token: 'settle-preserved',
  foliateSessionId: runtime.foliateSessionId,
  rasterGeneration: 7,
  textureGeneration: 107,
  foregroundMutationGeneration: 41,
  pageIndex: preservedPosition.pageIndex,
  spineIndex: preservedPosition.spineIndex,
  chapterPageIndex: preservedPosition.chapterPageIndex,
  paginationProfile,
})
const locationRuntime = {
  foliateSessionId: runtime.foliateSessionId,
  paginationProfile,
  foregroundMutationGeneration: 41,
  currentPagePosition: preservedPosition,
  lastRelocateDetail: {
    href: 'test-section-0',
    fraction: 0.1,
  },
  nativePageTurnSettledState: preservedSettlement,
  nativePageTurnSettledToken: 'settle-preserved',
  completedExactPageTurnSettlements: new Map(),
  retiredExactPageTurnSettlements: new Map(),
  activeExactPageTurnSettlementToken: preservedPending.token,
  pendingExactPageTurnSettlements: new Map([
    [preservedPending.token, preservedPending],
  ]),
  pageTurnPresentationSequence: 0,
  pageTurnPreviewExposedToken: null,
  pageTurnLivePresentationTargetValue: null,
  pageTurnLivePresentationReceiptValue: null,
  lastPostedLocationKey: null,
  nativePageDragPreview: null,
  pendingPageDragPreviewCommand: null,
  duplicatePageFingerprint: {
    compareCommittedPage: async () => null,
  },
}
Object.assign(
  locationRuntime,
  NavicReaderPageTurnMethods,
  NavicReaderLocationMethods,
  {
    removePageDragPreviewLayer: () => {},
    detailTargetsCover: () => false,
    hasNonCoverReadableContent: () => false,
    sectionHrefForDetail: detail => detail.href,
    tryUpdateReaderPageNumberLayer: () => {
      staleRecomputationCount += 1
      return positionAt(0)
    },
    chapterPagePosition: (_detail, pagePosition) => ({
      progress: 0,
      pageIndex: pagePosition.chapterPageIndex,
      pageCount: 64,
    }),
    updateSurfacePaperTexture: () => {},
    postCurrentVisibleTextRange: () => ({ posted: false }),
  }
)
assert.equal(
  readerRememberTextPageCommit(
    preservedPending,
    preservedRenderer,
    preservedPaginatorReceipt
  ),
  true
)
assert.equal(
  readerRememberTextPageVisibleContent(preservedPending),
  true
)
assert.equal(
  readerRememberTextPageCommit(
    preservedSettlement,
    preservedRenderer,
    preservedPaginatorReceipt
  ),
  true
)
assert.equal(
  readerRememberTextPageVisibleContent(preservedSettlement),
  true
)
const preservedDelivery = locationRuntime.postCurrentLocationSnapshot(
  'page-turn:exact-synchronous',
  { forceDuplicatePost: true, preserveCurrentPagePosition: true }
)
assert.equal(preservedDelivery.posted, true)
assert.equal(staleRecomputationCount, 0)
assert.equal(delivered.pageIndex, preservedPosition.pageIndex)
assert.equal(delivered.pageTurnSettleToken, 'settle-preserved')
assert.equal(locationRuntime.nativePageTurnSettledState, null)

console.log('Reader relocation bridge and settlement PASS')
