import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import test from 'node:test'

// Entire original runtime, dispatchers, exact navigation, settlement replacement and normal
// LocationChanged producer. Only DOM/layout projection and the renderer receipt boundary are
// controlled. This is neither real Foliate rendering nor Engine executor/Native/GL acceptance.
// No query, seeded settlement record, fabricated Engine receipt, or alternate timer/clock.
class SyntheticElement extends EventTarget {
  dataset = {}
  style = { setProperty() {}, removeProperty() {}, getPropertyValue: () => '' }
  classList = { add() {}, remove() {}, contains: () => false }
  childNodes = []
  clientWidth = 800
  clientHeight = 600
  append(...children) { this.childNodes.push(...children) }
  replaceChildren(...children) { this.childNodes = children }
  contains(child) { return this.childNodes.includes(child) }
  querySelector() { return null }
  querySelectorAll() { return [] }
  setAttribute() {}
  removeAttribute() {}
  getBoundingClientRect() { return { x: 0, y: 0, left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600 } }
  remove() {}
}
globalThis.HTMLElement = SyntheticElement
globalThis.customElements = { define() {} }
globalThis.NodeFilter = { SHOW_ELEMENT: 1, SHOW_TEXT: 4 }
globalThis.document = {
  body: new SyntheticElement(),
  documentElement: new SyntheticElement(),
  baseURI: 'https://reader.test/',
  createElement: () => new SyntheticElement(),
}
globalThis.window = { addEventListener() {}, innerWidth: 800, innerHeight: 600 }
// Constructor/scheduled DOM animation work is not a renderer receipt. Nothing runs these
// callbacks to manufacture success; the genuine synchronous normal producer is exercised.
globalThis.requestAnimationFrame = () => 0
globalThis.cancelAnimationFrame = () => {}

const entrypoint = pathToFileURL(resolve(
  'composeApp/src/androidMain/assets/reader/navic-reader.js'
))
const original = await readFile(entrypoint, 'utf8')
const importable = original.replace(
  /((?:from\s+|import\s*)['"])(\.\/[^'"]+)(['"])/g,
  (_match, prefix, specifier, suffix) => `${prefix}${new URL(specifier, entrypoint).href}${suffix}`,
)
let serial = 0
const Session = 'synthetic-installed-turn-session'
const detailAt = pageIndex => ({
  index: 0,
  href: 'synthetic-turn.xhtml',
  cfi: `synthetic-turn-position-${pageIndex}`,
  fraction: pageIndex / 8,
  chapterProgress: pageIndex / 7,
  pagePosition: {
    pageIndex, pageCount: 8, spineIndex: 0,
    chapterPageIndex: pageIndex, chapterPageCount: 8,
  },
})
const command = (sequence, pageIndex = 4) => ({
  type: 'goToVisualPage',
  commandId: `synthetic-installed-turn-command-${sequence}`,
  pageIndex,
  settleToken: `synthetic-installed-turn-token-${sequence}`,
  settleGestureId: sequence,
  settleSessionId: Session,
  settleRasterGeneration: 11,
  settleTextureGeneration: 13,
  settleForegroundMutationGeneration: sequence,
})

async function fixture() {
  const delivered = []
  const attempted = []
  let failLocationDelivery = false
  window.NavicAndroidBridge = { postMessage(json) {
    const message = JSON.parse(json)
    attempted.push(message)
    if (failLocationDelivery && message.type === 'locationChanged') {
      throw new Error('Synthetic location delivery failure')
    }
    delivered.push(message)
  } }
  let module
  try {
    module = await import(`data:text/javascript;base64,${Buffer.from(
      `${importable}\nexport { runtime as fixtureRuntime };\n// installed turn fixture ${++serial}`
    ).toString('base64')}`)
  } catch {
    throw new Error('E407.JS.Prerequisite.OriginalEntrypointImport')
  }
  const runtime = module.fixtureRuntime
  const bridge = window.NavicReaderBridge
  assert.equal(typeof bridge?.dispatch, 'function', 'E407.JS.Prerequisite.OriginalDispatch')
  assert.equal(typeof runtime.goToVisualPage, 'function', 'E407.JS.Prerequisite.ExactNavigation')
  assert.equal(typeof runtime.postLocationChanged, 'function', 'E407.JS.Prerequisite.NormalProducer')
  assert.equal(runtime.destinationCommitSequence, 0, 'E407.JS.Prerequisite.NoCommitSeed')

  const transactions = []
  let activeReceipt = null
  let receiptSequence = 0
  const renderer = new EventTarget()
  renderer.getContents = () => []
  renderer.page = 2
  renderer.pages = 10 // The original paginated model excludes its two boundary pages.
  renderer.validateTextPageCommit = receipt => receipt === activeReceipt
  renderer.validateTextPageVisibleContent = receipt => receipt === activeReceipt
  renderer.commitTextPage = (index, pageIndex, reason) => new Promise((resolveCommit, rejectCommit) => {
    const sourceView = runtime.view
    // This is the controlled physical renderer boundary, not a replacement for navigation.
    // Genuine goToVisualPage must call it before this transaction exists.
    const transaction = {
      complete({ projectBeforeReturn = false, status = 'committed' } = {}) {
        if (status !== 'committed') {
          resolveCommit(Object.freeze({
            status, requestedIndex: index, requestedPageIndex: pageIndex,
            position: null, receipt: null, reason,
          }))
          return
        }
        const position = Object.freeze({ index, pageIndex, pageCount: 8 })
        activeReceipt = Object.freeze({
          layoutGeneration: 1, viewGeneration: 1, commitSequence: ++receiptSequence,
          flow: 'paginated', ...position,
        })
        renderer.page = pageIndex + 1
        const detail = detailAt(pageIndex)
        const relocate = new Event('relocate')
        Object.defineProperty(relocate, 'detail', { value: detail })
        sourceView.dispatchEvent(relocate) // Original installed public view listener.
        if (projectBeforeReturn) runtime.tryUpdateReaderPageNumberLayer(detail, null, 'page-turn:exact')
        resolveCommit(Object.freeze({
          status, requestedIndex: index, requestedPageIndex: pageIndex,
          position, receipt: activeReceipt, reason,
        }))
      },
      fail() { rejectCommit(new Error('Synthetic exact navigation failure')) },
    }
    transactions.push(transaction)
  })
  const measurementRenderer = new EventTarget()
  let measurementReceipt = null
  measurementRenderer.getContents = () => []
  measurementRenderer.validateTextPageCommit = receipt => receipt === measurementReceipt
  measurementRenderer.commitTextPage = async (index, pageIndex, reason) => {
    const position = Object.freeze({ index, pageIndex, pageCount: 8 })
    measurementReceipt = Object.freeze({
      layoutGeneration: 1, viewGeneration: 1, commitSequence: 1, flow: 'paginated', ...position,
    })
    return Object.freeze({ status: 'committed', requestedIndex: index,
      requestedPageIndex: pageIndex, position, receipt: measurementReceipt, reason })
  }
  let createdViews = 0
  document.createElement = tag => {
    if (tag !== 'foliate-view') return new SyntheticElement()
    const view = new SyntheticElement()
    view.renderer = createdViews++ === 0 ? renderer : measurementRenderer
    view.book = { sections: [{ href: 'synthetic-turn.xhtml', size: 64, linear: 'yes' }], toc: [] }
    view.history = Object.assign(new EventTarget(), { pushState() {} })
    view.open = async () => {} // Controlled source transport; no fetch/network or real Foliate.
    view.close = () => {}
    view.init = async () => {
      const event = new Event('relocate')
      Object.defineProperty(event, 'detail', { value: detailAt(1) })
      view.dispatchEvent(event)
    }
    return view
  }
  Object.assign(runtime, {
    // DOM presentation dependencies only. Keep original open/close, profile construction,
    // geometry/page-position projection, dispatch, navigation, cancellation and settlement.
    removePageDragPreviewLayer() {},
    updateSurfacePaperTexture() {},
    attachSurfacePaperTextureScrollSync() {},
    applyReaderViewportLayout() {},
    applyReaderViewportLayoutToProfilerView() {},
    attachSurfaceTapGesture() {},
    attachReaderTapZoneGesture() {},
    logContentLayout() {},
  })
  let openFailed = false
  try {
    await bridge.dispatch({ type: 'openPublication', commandId: 'synthetic-installed-turn-open',
      url: 'https://synthetic.invalid/installed-turn.epub', foliateSessionId: Session,
      suppressWebShellCover: true })
  } catch { openFailed = true }
  assert.equal(openFailed, false, 'E407.JS.Prerequisite.OriginalOpenDispatch')
  assert.equal(createdViews, 2, 'E407.JS.Prerequisite.OriginalViewAndProfilerConstruction')
  assert.ok(delivered.some(message => message.type === 'publicationReady'),
    'E407.JS.Prerequisite.OriginalPublicationReady')
  assert.ok(delivered.some(message => message.type === 'locationChanged' && message.pageIndex === 1),
    'E407.JS.Prerequisite.OriginalSourceDelivery')
  assert.ok(delivered.some(message => message.type === 'paginationProfileStatus' && message.status === 'ready'),
    'E407.JS.Prerequisite.OriginalMeasuredProfile')
  // Only the genuine original open and controlled receipt-backed profiler may install state.
  // No direct session/view/profile/readiness/committed-owner assignments on the runtime.
  delivered.length = 0
  attempted.length = 0
  return {
    runtime, bridge, delivered, attempted, transactions,
    failDelivery(value) { failLocationDelivery = value },
    clearMessages() { delivered.length = 0; attempted.length = 0 },
    deliverTarget(pageIndex = 4) {
      return runtime.postLocationChanged(detailAt(pageIndex), 'page-turn:exact')
    },
  }
}

const settledLocations = f => f.delivered.filter(message =>
  message.type === 'locationChanged' && message.pageTurnSettleToken != null)
const exactLocations = (f, request) => settledLocations(f).filter(message =>
  message.pageTurnSettleToken === request.settleToken)
const ackCount = (f, request) => f.delivered.filter(message =>
  message.type === 'commandAck' && message.commandId === request.commandId).length
const failureCount = (f, request) => f.delivered.filter(message =>
  message.type === 'commandFailed' && message.commandId === request.commandId).length
const assertCorrelated = (message, request, label) => {
  assert.ok(message?.commandId === request.commandId, `${label}.ExactCommandIdentity`)
  assert.ok(message.foliateSessionId === request.settleSessionId, `${label}.ExactSession`)
  assert.ok(message.pageTurnSettleToken === request.settleToken, `${label}.ExactToken`)
  assert.ok(message.pageTurnSettleRasterGeneration === request.settleRasterGeneration,
    `${label}.ExactSourceRaster`)
  assert.ok(message.pageTurnSettleTextureGeneration === request.settleTextureGeneration,
    `${label}.ExactSourceTexture`)
  assert.ok(message.pageIndex === request.pageIndex, `${label}.ObservedOrdinal`)
}
const execute = async (f, request, options) => {
  let failure = false
  const result = Promise.resolve(f.bridge.dispatch(request)).catch(() => { failure = true })
  assert.equal(f.transactions.length > 0, true, 'E407.JS.Navigation.ActualRendererEffect')
  f.transactions.at(-1).complete(options)
  await result
  assert.equal(failure, false, 'E407.JS.Navigation.NoUnexpectedFailure')
}

for (const projectBeforeReturn of [false, true]) {
  test(`E407.JS.Correlation.${projectBeforeReturn ? 'ResultBeforeAck' : 'AckBeforeResult'}`, async () => {
    const f = await fixture()
    const request = command(1)
    await execute(f, request, { projectBeforeReturn })
    assert.equal(ackCount(f, request), 1, 'E407.JS.Order.OriginalAck')
    if (!projectBeforeReturn) {
      assert.equal(exactLocations(f, request).length, 0, 'E407.JS.Order.AckIsNotSettlement')
      assert.equal(f.deliverTarget().posted, true, 'E407.JS.Order.DelayedNormalDelivery')
    }
    const locations = exactLocations(f, request)
    assert.equal(locations.length, 1, 'E407.JS.Order.OneActualSettlement')
    const locationIndex = f.delivered.indexOf(locations[0])
    const ackIndex = f.delivered.findIndex(message => message.type === 'commandAck')
    assert.equal(locationIndex < ackIndex, projectBeforeReturn, 'E407.JS.Order.ExercisedOrdering')
    assertCorrelated(locations[0], request, 'E407.JS.Correlation')
    assert.equal(f.delivered.some(message => message.type === 'currentDestinationResolved'), false,
      'E407.JS.Correlation.NoQuerySubstitution')
    assert.equal(f.transactions.length, 1, 'E407.JS.Correlation.OneExactNavigation')
  })
}

test('E407.JS.Correlation.ReplacementAndConsumedRecordDuplicate', async () => {
  const f = await fixture()
  const first = command(1, 4)
  await execute(f, first)
  assert.equal(f.deliverTarget(4).posted, true, 'E407.JS.Replacement.FirstDelivery')
  const firstLocation = exactLocations(f, first)[0]
  assert.equal(exactLocations(f, first).length, 1, 'E407.JS.Replacement.FirstSettlement')
  f.clearMessages()
  // Original outer duplicate handling must not navigate/replay even after real consumption
  // moved the immutable original settlement to the completed-record path.
  await f.bridge.dispatch(first)
  assert.equal(f.transactions.length, 1, 'E407.JS.Replacement.CompletedDuplicateNoNavigation')
  assert.equal(settledLocations(f).length, 0, 'E407.JS.Replacement.CompletedDuplicateNoReplay')
  const second = command(2, 6)
  await execute(f, second)
  assert.equal(f.deliverTarget(6).posted, true, 'E407.JS.Replacement.SecondDelivery')
  const secondLocation = exactLocations(f, second)[0]
  assert.equal(exactLocations(f, second).length, 1, 'E407.JS.Replacement.SecondSettlement')
  // Successful navigation replaces its pending immutable record both before and after the
  // renderer await; the original command correlation must survive every such replacement.
  assertCorrelated(firstLocation, first, 'E407.JS.Replacement.FirstCorrelation')
  assertCorrelated(secondLocation, second, 'E407.JS.Replacement.SecondCorrelation')
  assert.ok(secondLocation.destinationCommitSequence > firstLocation.destinationCommitSequence,
    'E407.JS.Replacement.NewGenuineDestinationCommit')
})

test('E407.JS.Correlation.FailedNormalDeliveryRetainsExactRetry', async () => {
  const f = await fixture()
  const request = command(1)
  await execute(f, request)
  f.failDelivery(true)
  const failed = f.deliverTarget()
  assert.equal(failed.posted, false, 'E407.JS.Delivery.FailureNotSuccess')
  assert.equal(exactLocations(f, request).length, 0, 'E407.JS.Delivery.NoDeliveredSettlement')
  const attempts = f.attempted.filter(message => message.type === 'locationChanged')
  assert.equal(attempts.length, 1, 'E407.JS.Delivery.RealProducerAttempt')
  f.failDelivery(false)
  assert.equal(f.deliverTarget().posted, true, 'E407.JS.Delivery.GenuineRetryDelivery')
  const location = exactLocations(f, request)[0]
  assert.equal(exactLocations(f, request).length, 1, 'E407.JS.Delivery.OneSettlementAfterRetry')
  assert.ok(location.destinationCommitSequence === attempts[0].destinationCommitSequence,
    'E407.JS.Delivery.FailedAttemptCannotAdvanceCommit')
  assert.equal(f.transactions.length, 1, 'E407.JS.Delivery.NoFollowUpNavigationOrQuery')
  assertCorrelated(attempts[0], request, 'E407.JS.Delivery.AttemptCorrelation')
  assertCorrelated(location, request, 'E407.JS.Delivery.RetryCorrelation')
})

test('E407.JS.Correlation.ExactCancellationDoesNotStealSuccessor', async () => {
  const f = await fixture()
  const old = command(1, 4)
  // Either retired-command transport disposition remains distinct from semantic acceptance.
  const oldNavigation = Promise.resolve(f.bridge.dispatch(old)).catch(() => {})
  assert.equal(f.transactions.length, 1, 'E407.JS.Cancel.OriginalEffectReached')
  assert.equal(f.runtime.cancelPendingExactPageTurnSettlement('synthetic-exact-cancel'), true,
    'E407.JS.Cancel.OriginalCancellation')
  const fresh = command(2, 6)
  const freshNavigation = Promise.resolve(f.bridge.dispatch(fresh)).catch(() => {
    assert.fail('E407.JS.Cancel.UnexpectedSuccessorFailure')
  })
  assert.equal(f.transactions.length, 2, 'E407.JS.Cancel.SuccessorEffectReached')
  // Old physical completion arrives while the original successor transaction is waiting.
  f.transactions[0].complete()
  await oldNavigation
  assert.equal(exactLocations(f, old).length, 0, 'E407.JS.Cancel.NoCancelledSettlement')
  f.transactions[1].complete()
  await freshNavigation
  assert.equal(f.deliverTarget(6).posted, true, 'E407.JS.Cancel.SuccessorNormalDelivery')
  assert.equal(exactLocations(f, fresh).length, 1, 'E407.JS.Cancel.OneSuccessorSettlement')
  assert.equal(exactLocations(f, old).length, 0, 'E407.JS.Cancel.OldTokenRemainsInert')
  assertCorrelated(exactLocations(f, fresh)[0], fresh, 'E407.JS.Cancel.SuccessorCorrelation')
})

// Prospective behavioral coverage, UNEXECUTED until MAIN's original-runtime gate.
// These controls use original dispatch/navigation and do not seed settlement records.
test('E407.JS.Correlation.OriginalImmutableRecordsCarryCommandThroughConsumption', async () => {
  const f = await fixture()
  const request = command(1)
  const navigation = Promise.resolve(f.bridge.dispatch(request))
  const pendingBeforeReturn = f.runtime.activeExactPageTurnSettlement()
  assert.ok(Object.isFrozen(pendingBeforeReturn), 'E407.JS.Record.OriginalPendingImmutable')
  assert.ok(pendingBeforeReturn?.commandId === request.commandId,
    'E407.JS.Record.PendingCommandCorrelation')
  f.transactions[0].complete()
  await navigation
  const pendingAfterReturn = f.runtime.activeExactPageTurnSettlement()
  assert.ok(pendingAfterReturn !== pendingBeforeReturn, 'E407.JS.Record.OriginalPendingReplacement')
  assert.ok(pendingAfterReturn?.commandId === request.commandId,
    'E407.JS.Record.ReplacementCommandCorrelation')
  assert.equal(f.deliverTarget().posted, true, 'E407.JS.Record.OriginalDelivery')
  const completed = f.runtime.completedExactPageTurnSettlements.get(request.settleToken)
  assert.ok(Object.isFrozen(completed), 'E407.JS.Record.OriginalCompletedImmutable')
  assert.ok(completed?.commandId === request.commandId,
    'E407.JS.Record.CompletedCommandCorrelation')
})

test('E407.JS.Correlation.TokenCannotTransferToDifferentCommand', async () => {
  const f = await fixture()
  const request = command(1)
  await execute(f, request)
  assert.equal(f.deliverTarget().posted, true, 'E407.JS.Token.OriginalSettlementDelivery')
  const foreign = { ...request, commandId: 'synthetic-foreign-command' }
  let rejected = false
  try { await f.bridge.dispatch(foreign) } catch { rejected = true }
  assert.equal(rejected, true, 'E407.JS.Token.CommandReuseRejected')
  assert.equal(f.transactions.length, 1, 'E407.JS.Token.NoSecondNavigation')
  assert.equal(ackCount(f, foreign), 0, 'E407.JS.Token.NoForeignSuccessfulAck')
  assert.equal(failureCount(f, foreign), 1, 'E407.JS.Token.ExactForeignFailure')
  assert.equal(exactLocations(f, request).length, 1, 'E407.JS.Token.NoSettlementReplay')
})

test('E407.JS.Cancel.ExactRetirementCannotCancelOriginalSuccessor', async () => {
  const f = await fixture()
  const old = command(1, 4)
  const oldNavigation = Promise.resolve(f.bridge.dispatch(old)).catch(() => {})
  assert.equal(f.runtime.cancelPendingExactPageTurnSettlement('synthetic-retirement',
    old.settleToken, old.commandId), true, 'E407.JS.ExactCancel.OriginalCancellation')
  const fresh = command(2, 6)
  const freshNavigation = Promise.resolve(f.bridge.dispatch(fresh)).catch(() => {
    assert.fail('E407.JS.ExactCancel.UnexpectedSuccessorFailure')
  })
  assert.equal(f.transactions.length, 2, 'E407.JS.ExactCancel.SuccessorOriginalEffect')
  assert.equal(f.runtime.cancelPendingExactPageTurnSettlement('synthetic-late-retirement',
    old.settleToken, old.commandId), false, 'E407.JS.ExactCancel.OldRetirementRejected')
  f.transactions[0].complete()
  await oldNavigation
  f.transactions[1].complete()
  await freshNavigation
  assert.equal(f.deliverTarget(6).posted, true, 'E407.JS.ExactCancel.SuccessorDelivery')
  assert.equal(exactLocations(f, fresh).length, 1, 'E407.JS.ExactCancel.OneSuccessorSettlement')
  assert.equal(exactLocations(f, old).length, 0, 'E407.JS.ExactCancel.NoOldSettlement')
  assertCorrelated(exactLocations(f, fresh)[0], fresh, 'E407.JS.ExactCancel.SuccessorCorrelation')
})

test('E407.JS.Correlation.FailureRetainsCommandOnlyOnOriginalRetiredRecord', async () => {
  const f = await fixture()
  const request = command(1)
  let rejected = false
  const navigation = Promise.resolve(f.bridge.dispatch(request)).catch(() => { rejected = true })
  f.transactions[0].fail()
  await navigation
  assert.equal(rejected, true, 'E407.JS.Record.ActualFailure')
  assert.equal(f.runtime.activeExactPageTurnSettlement(), null,
    'E407.JS.Record.FailedPendingRemoved')
  const retired = f.runtime.retiredExactPageTurnSettlements.get(request.settleToken)
  assert.ok(Object.isFrozen(retired), 'E407.JS.Record.OriginalRetiredImmutable')
  assert.ok(retired?.commandId === request.commandId,
    'E407.JS.Record.RetiredCommandCorrelation')
  assert.equal(settledLocations(f).length, 0, 'E407.JS.Record.FailureNotSettlement')
})

test('E407.JS.Control.ActualRendererFailureCannotProduceSettlementOrAck', async () => {
  const f = await fixture()
  const request = command(1)
  let rejected = false
  const navigation = Promise.resolve(f.bridge.dispatch(request)).catch(() => { rejected = true })
  assert.equal(f.transactions.length, 1, 'E407.JS.Failure.ActualRendererEffect')
  f.transactions[0].fail()
  await navigation
  assert.equal(rejected, true, 'E407.JS.Failure.OriginalNavigationRejected')
  assert.equal(ackCount(f, request), 0, 'E407.JS.Failure.NoSuccessfulAck')
  assert.equal(failureCount(f, request), 1, 'E407.JS.Failure.ExactCommandFailure')
  f.deliverTarget()
  assert.equal(settledLocations(f).length, 0, 'E407.JS.Failure.NoFabricatedSettlement')
})

test('E407.JS.Control.CancelledRendererReceiptCannotBecomeLocationSettlement', async () => {
  const f = await fixture()
  const request = command(1)
  let rejected = false
  const navigation = Promise.resolve(f.bridge.dispatch(request)).catch(() => { rejected = true })
  assert.equal(f.transactions.length, 1, 'E407.JS.CancelledReceipt.ActualEffect')
  f.transactions[0].complete({ status: 'cancelled' })
  await navigation
  assert.equal(rejected, true, 'E407.JS.CancelledReceipt.Rejected')
  assert.equal(ackCount(f, request), 0, 'E407.JS.CancelledReceipt.NoSuccessfulAck')
  f.deliverTarget()
  assert.equal(settledLocations(f).length, 0, 'E407.JS.CancelledReceipt.NoSettlement')
})

test('E407.JS.Control.WrongSessionRejectsBeforeActualNavigation', async () => {
  const f = await fixture()
  const request = { ...command(1), settleSessionId: 'synthetic-foreign-turn-session' }
  let rejected = false
  try { await f.bridge.dispatch(request) } catch { rejected = true }
  assert.equal(rejected, true, 'E407.JS.Admission.WrongSessionRejected')
  assert.equal(f.transactions.length, 0, 'E407.JS.Admission.NoRendererEffect')
  assert.equal(ackCount(f, request), 0, 'E407.JS.Admission.NoSuccessfulAck')
  assert.equal(failureCount(f, request), 1, 'E407.JS.Admission.CorrelatedFailure')
})
