import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import test from 'node:test'

// Import the entire actual entrypoint, including both dispatchers. Only resolve its relative
// imports and append a test-only export of its private fixture runtime; no dispatcher, normal
// producer, or bridge-post implementation is copied/rewritten/mocked. Surface helpers below
// are fixture adapters; dependencies (including LocationMethods/post) are actual files.
// This is a synthetic view/surface fixture, NOT Foliate rendering or native executor acceptance.
globalThis.HTMLElement = class extends EventTarget {}
globalThis.customElements = { define() {} }
globalThis.NodeFilter = { SHOW_ELEMENT: 1, SHOW_TEXT: 4 }
globalThis.document = {
  body: {
    dataset: {},
    style: { setProperty() {}, removeProperty() {} },
    classList: { add() {}, remove() {} },
    querySelector: () => null,
    querySelectorAll: () => [],
  },
  baseURI: 'https://reader.test/',
}
globalThis.window = { addEventListener() {} }
// Constructor-only startup layout is outside this fixture; do not run or fake a timer/clock.
globalThis.requestAnimationFrame = () => 0
globalThis.cancelAnimationFrame = () => {}

const entrypointUrl = pathToFileURL(resolve(
  'composeApp/src/androidMain/assets/reader/navic-reader.js'
))
const actualSource = await readFile(entrypointUrl, 'utf8')
const importableSource = actualSource.replace(
  /((?:from\s+|import\s*)['"])(\.\/[^'"]+)(['"])/g,
  (_match, prefix, specifier, suffix) =>
    `${prefix}${new URL(specifier, entrypointUrl).href}${suffix}`,
)
let fixtureSerial = 0
const SessionId = 'foliate-query-fixture'
const CommandId = 'reader-command-fixture-query'
const successfulMessages = []
const attemptedMessages = []
const deliver = json => { successfulMessages.push(JSON.parse(json)) }

const fixture = async () => {
  successfulMessages.length = 0
  attemptedMessages.length = 0
  window.NavicAndroidBridge = { postMessage: deliver }
  const source = `${importableSource}\nexport { runtime as fixtureRuntime };\n// fixture ${++fixtureSerial}`
  let module
  try {
    module = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
  } catch {
    // Never print a data URL or full source on a harness setup failure.
    throw new Error('Actual reader entrypoint import failed: fixture setup error, not behavior RED')
  }
  const runtime = module.fixtureRuntime
  const bridge = window.NavicReaderBridge
  assert.equal(typeof bridge?.dispatch, 'function', 'Actual outer dispatch must exist')
  assert.equal(typeof runtime.dispatch, 'function', 'Actual runtime dispatch must exist')
  assert.equal(typeof runtime.postLocationChanged, 'function', 'Actual normal producer must exist')
  assert.equal(runtime.destinationCommitSequence, 0, 'Start from actual constructor counter, no commit seed')
  assert.equal(runtime.committedRelocateDetail, null)
  runtime.foliateSessionId = SessionId
  runtime.view = {
    renderer: { getContents: () => [] },
    book: { sections: [] },
    close() {},
    remove() {},
  }
  // Only view/surface dependencies used by the genuine normal producer are fixture adapters.
  // Neither dispatcher, normal message builder, bridge post, nor commit storage is replaced.
  Object.assign(runtime, {
    removePageDragPreviewLayer() {},
    detailTargetsCover: () => false,
    hasNonCoverReadableContent: () => false,
    sectionHrefForDetail: () => undefined,
    tryUpdateReaderPageNumberLayer(detail) {
      this.currentPagePosition = detail.pagePosition
      return detail.pagePosition
    },
    chapterPagePosition: detail => ({
      progress: detail.chapterProgress,
      pageIndex: detail.pagePosition.chapterPageIndex,
      pageCount: detail.pagePosition.chapterPageCount,
    }),
    updateSurfacePaperTexture() {},
    // Empty view has no visible text; leave real visible-range production in place.
    postCurrentLocationSnapshot() {
      assert.fail('Query must not execute the diagnostic/snapshot relocation producer')
    },
  })
  successfulMessages.length = 0 // Discard actual entrypoint Ready, never mistake it for a result.
  return { runtime, bridge }
}

const normalDetail = (pageIndex = 2) => ({
  href: 'synthetic.xhtml',
  cfi: 'synthetic-cfi',
  tocItem: { href: 'synthetic.xhtml', label: 'Synthetic chapter' },
  pageItem: { label: 'Synthetic page' },
  fraction: 0.25,
  size: 0.125,
  chapterProgress: 0.5,
  pagePosition: {
    pageIndex,
    pageCount: 8,
    spineIndex: 0,
    chapterPageIndex: pageIndex,
    chapterPageCount: 4,
  },
})
const query = (bridge, overrides = {}) => bridge.dispatch({
  type: 'resolveCurrentDestination',
  commandId: CommandId,
  foliateSessionId: SessionId,
  ...overrides,
})
const resultCount = messages => messages.filter(
  message => message.type === 'currentDestinationResolved'
).length
const deliverNormalLocation = runtime => {
  const delivered = runtime.postLocationChanged(normalDetail(), 'relocate-committed')
  assert.equal(delivered.posted, true, 'Genuine normal producer must deliver before query')
  assert.equal(runtime.destinationCommitSequence, 1)
  const message = successfulMessages.find(entry => entry.type === 'locationChanged')
  assert.ok(message, 'Real bridge must observe the normal location delivery')
  assert.ok(message.destinationFoliateSessionId === SessionId,
    'Normal delivery must preserve the exact source session')
  assert.equal(message.destinationCommitSequence, 1)
  assert.ok(Number.isSafeInteger(message.destinationCommitSequence))
  return message
}
const assertNoResult = () => {
  assert.equal(resultCount(successfulMessages), 0, 'Unavailable source cannot produce semantic result')
  assert.equal(resultCount(attemptedMessages), 0, 'Unavailable source cannot even attempt semantic result')
}
const rejectQuery = async (bridge, overrides = {}) => {
  // Assert the actual recognized rejection path, not unknown-command ACK-only absence.
  // Boolean/fixed-message assertions never render a command, locator, source or exception.
  let rejected = false
  try { await query(bridge, overrides) } catch (error) {
    rejected = error instanceof Error && error.message === 'Current destination unavailable'
  }
  assert.ok(rejected, 'Query must reject with the bounded current-destination failure')
  assertNoResult()
  assert.ok(!successfulMessages.some(message => message.type === 'commandAck'),
    'Rejected query must not be acknowledged as successful dispatch')
  assert.ok(!successfulMessages.some(message => message.type === 'error'),
    'Recognized query must not fall through to unknown-command error')
  const id = overrides.commandId === undefined ? CommandId : overrides.commandId
  if (typeof id === 'string' && id.trim()) {
    assert.ok(successfulMessages.some(message => message.type === 'commandFailed'),
      'Correlatable rejected query must deliver transport failure')
  }
}

test('actual dispatch replies with correlated destination from successful normal producer, without new commit', async () => {
  const { runtime, bridge } = await fixture()
  const location = deliverNormalLocation(runtime)
  successfulMessages.length = 0
  await query(bridge)
  const results = successfulMessages.filter(message => message.type === 'currentDestinationResolved')
  assert.equal(results.length, 1, 'Expected distinct correlated result from actual bridge dispatch')
  const result = results[0]
  assert.ok(result.commandId === CommandId, 'Result must preserve exact dispatch identity')
  assert.ok(result.foliateSessionId === SessionId, 'Result must preserve exact live session')
  assert.ok(result.destinationFoliateSessionId === location.destinationFoliateSessionId,
    'Result must preserve the actual destination session')
  assert.equal(result.destinationCommitSequence, location.destinationCommitSequence)
  for (const field of [
    'href', 'cfi', 'progress', 'pageIndex', 'pageCount', 'chapterProgress', 'chapterPageIndex',
    'chapterPageCount', 'rangeCfi', 'reason', 'fraction', 'size', 'tocItemLabel', 'pageItemLabel',
  ]) assert.ok(result[field] === location[field], 'Query must preserve the exact flat locator field')
  assert.equal(successfulMessages.filter(message => message.type === 'locationChanged').length, 0)
  assert.equal(runtime.destinationCommitSequence, 1, 'Query must not manufacture another commit')
  assert.equal(successfulMessages.some(message => message.type === 'commandAck'), true,
    'Transport acknowledgement remains separate from semantic result')
})

test('normal duplicate suppression does not advance the destination commit', async () => {
  const { runtime } = await fixture()
  deliverNormalLocation(runtime)
  const duplicate = runtime.postLocationChanged(normalDetail(), 'relocate-committed')
  assert.equal(duplicate.posted, false)
  assert.equal(duplicate.skipped, 'duplicate')
  assert.equal(runtime.destinationCommitSequence, 1)
})

test('failed first normal delivery cannot become a committed query result', async () => {
  const { runtime, bridge } = await fixture()
  window.NavicAndroidBridge.postMessage = json => {
    attemptedMessages.push(JSON.parse(json))
    throw new Error('synthetic delivery failure')
  }
  const failed = runtime.postLocationChanged(normalDetail(), 'relocate-committed')
  assert.equal(failed.posted, false)
  assert.equal(failed.skipped, 'bridge-delivery-failed')
  assert.equal(runtime.destinationCommitSequence, 0)
  // Real committedRelocateDetail is assigned before delivery: it is NOT successful proof.
  assert.ok(runtime.committedRelocateDetail)
  attemptedMessages.length = 0
  window.NavicAndroidBridge.postMessage = deliver
  await rejectQuery(bridge)
  assert.equal(runtime.destinationCommitSequence, 0)
})

test('failed later normal delivery cannot pair undelivered location with previous identity', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  window.NavicAndroidBridge.postMessage = () => { throw new Error('synthetic later failure') }
  const failed = runtime.postLocationChanged(normalDetail(3), 'relocate-committed')
  assert.equal(failed.posted, false)
  assert.equal(runtime.destinationCommitSequence, 1)
  assert.equal(runtime.committedRelocateDetail.pagePosition.pageIndex, 3)
  successfulMessages.length = 0
  window.NavicAndroidBridge.postMessage = deliver
  await rejectQuery(bridge)
  assert.equal(runtime.destinationCommitSequence, 1)
})

test('missing live view cannot use retained location or commit identity', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  runtime.view = null
  successfulMessages.length = 0
  await rejectQuery(bridge)
})

test('wrong explicit session cannot resolve the current live source', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  successfulMessages.length = 0
  await rejectQuery(bridge, { foliateSessionId: 'foliate-wrong-fixture' })
})

test('fresh actual view without successful location rejects query', async () => {
  const { bridge } = await fixture()
  await rejectQuery(bridge)
})

test('replacement view with unchanged session cannot inherit previous proof', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  runtime.view = { renderer: { getContents: () => [] }, book: { sections: [] } }
  successfulMessages.length = 0
  await rejectQuery(bridge)
})

test('replacement session on same view cannot inherit previous proof', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  runtime.foliateSessionId = 'foliate-replaced-fixture'
  successfulMessages.length = 0
  await rejectQuery(bridge, { foliateSessionId: runtime.foliateSessionId })
})

for (const [label, overrides] of [
  ['missing session', { foliateSessionId: null }],
  ['blank session', { foliateSessionId: '' }],
  ['noncanonical session', { foliateSessionId: ` ${SessionId}` }],
  ['nonstring session', { foliateSessionId: 7 }],
  ['missing command identity', { commandId: null }],
  ['blank command identity', { commandId: '' }],
  ['noncanonical command identity', { commandId: ` ${CommandId}` }],
  ['nonstring command identity', { commandId: 7 }],
]) test(`actual query rejects ${label}`, async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  successfulMessages.length = 0
  await rejectQuery(bridge, overrides)
})

test('duplicate successful command only ACKs without repeating semantic result or commit', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  successfulMessages.length = 0
  await query(bridge)
  assert.equal(resultCount(successfulMessages), 1, 'First query must return actual semantic result')
  successfulMessages.length = 0
  await query(bridge)
  assert.equal(resultCount(successfulMessages), 0, 'Duplicate dispatch must not replay the result')
  assert.equal(successfulMessages.filter(message => message.type === 'commandAck').length, 1)
  assert.equal(runtime.destinationCommitSequence, 1)
})

test('noncanonical identity cannot bypass validation through duplicate ACK path', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  successfulMessages.length = 0
  await query(bridge)
  successfulMessages.length = 0
  await rejectQuery(bridge, { commandId: ` ${CommandId}` })
})

test('failed query result delivery rejects and does not claim successful dispatch', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  successfulMessages.length = 0
  window.NavicAndroidBridge.postMessage = json => {
    const message = JSON.parse(json)
    if (message.type === 'currentDestinationResolved') {
      attemptedMessages.push(message)
      throw new Error('synthetic query delivery failure')
    }
    deliver(json)
  }
  let rejected = false
  try { await query(bridge) } catch (error) {
    rejected = error instanceof Error && error.message === 'Current destination unavailable'
  }
  assert.ok(rejected, 'Failed result delivery must reject through bounded failure')
  assert.equal(resultCount(attemptedMessages), 1, 'Actual query must attempt its distinct result')
  assert.equal(resultCount(successfulMessages), 0)
  assert.equal(successfulMessages.filter(message => message.type === 'commandAck').length, 0)
  assert.equal(successfulMessages.filter(message => message.type === 'commandFailed').length, 1)
  assert.equal(runtime.destinationCommitSequence, 1)
})

test('normal duplicate retains successful proof without manufacturing another commit', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  runtime.postLocationChanged(normalDetail(), 'relocate-committed')
  successfulMessages.length = 0
  await query(bridge)
  assert.equal(resultCount(successfulMessages), 1)
  assert.equal(runtime.destinationCommitSequence, 1)
})

test('closed actual runtime cannot resolve a previous committed destination', async () => {
  const { runtime, bridge } = await fixture()
  deliverNormalLocation(runtime)
  runtime.close()
  assert.equal(runtime.view, null)
  assert.equal(runtime.foliateSessionId, '')
  successfulMessages.length = 0
  await rejectQuery(bridge)
})
