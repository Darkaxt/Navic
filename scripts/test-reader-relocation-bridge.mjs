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
const paginationProfile = Object.freeze({
  pageCount: 64,
  chapters: Object.freeze([
    Object.freeze({
      pageStartIndex: 0,
      pageCount: 64,
      spineIndex: 0,
      href: 'test-chapter',
    }),
  ]),
})
let navigationCount = 0
const runtime = {
  foliateSessionId: 'foliate-test',
  paginationProfile,
  currentPagePosition: null,
  pendingExactPageTurnSettlements: new Map(),
  completedExactPageTurnSettlements: new Map(),
  activeExactPageTurnSettlementToken: null,
  nativePageTurnSettledState: null,
  nativePageTurnSettledToken: null,
  view: {
    renderer: {
      goToTextPage: async () => { navigationCount += 1 },
    },
    history: {
      pushState: () => {},
    },
  },
  beginControlledRelocation: () => {},
  scheduleSettledControlledPageTurnRelocation: () => true,
  scheduleControlledRelocationFallback: () => {},
  applyReaderViewportLayout: () => {},
}
Object.assign(runtime, NavicReaderPageTurnMethods)
const settlementCommand = (token, pageIndex, rasterGeneration = pageIndex) => ({
  pageIndex,
  settleToken: token,
  settleSessionId: runtime.foliateSessionId,
  settleRasterGeneration: rasterGeneration,
  settleTextureGeneration: pageIndex + 100,
})
const positionAt = (pageIndex, chapterPageIndex = pageIndex) => ({
  pageIndex,
  spineIndex: 0,
  chapterPageIndex,
})

for (let pageIndex = 0; pageIndex < 33; pageIndex += 1) {
  const token = `settle-${pageIndex}`
  runtime.currentPagePosition = positionAt(pageIndex)
  await runtime.goToVisualPage(settlementCommand(token, pageIndex))
  assert.equal(runtime.peekNativePageTurnSettlement()?.token, token)
  assert.equal(runtime.consumeNativePageTurnSettlement(token), true)
}
assert.equal(navigationCount, 33)
assert.equal(runtime.completedExactPageTurnSettlements.size, 33)

runtime.currentPagePosition = positionAt(0)
await runtime.goToVisualPage(settlementCommand('settle-0', 0))
assert.equal(navigationCount, 33)
await assert.rejects(
  runtime.goToVisualPage(settlementCommand('settle-0', 1, 0)),
  /settlement token cannot be reused/
)
assert.equal(navigationCount, 33)

runtime.currentPagePosition = positionAt(40)
await runtime.goToVisualPage(settlementCommand('settle-cancelled', 40))
assert.equal(runtime.nativePageTurnSettledState?.token, 'settle-cancelled')
assert.equal(runtime.cancelPendingExactPageTurnSettlement('test-cancel'), true)
assert.equal(runtime.nativePageTurnSettledState, null)
assert.equal(runtime.peekNativePageTurnSettlement(), null)

runtime.currentPagePosition = positionAt(41)
await runtime.goToVisualPage(settlementCommand('settle-stale', 41))
assert.equal(runtime.nativePageTurnSettledState?.token, 'settle-stale')
runtime.currentPagePosition = positionAt(41, 42)
assert.equal(runtime.peekNativePageTurnSettlement(), null)
assert.equal(runtime.nativePageTurnSettledState, null)

runtime.currentPagePosition = positionAt(0)
const pendingCommand = settlementCommand('settle-pending', 42)
await runtime.goToVisualPage(pendingCommand)
const pendingNavigationCount = navigationCount
await runtime.goToVisualPage(pendingCommand)
assert.equal(navigationCount, pendingNavigationCount)
await assert.rejects(
  runtime.goToVisualPage({ ...pendingCommand, pageIndex: 43 }),
  /settlement token cannot be reused/
)
assert.equal(runtime.cancelPendingExactPageTurnSettlement('test-pending'), true)

console.log('Reader relocation bridge and settlement PASS')
