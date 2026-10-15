import { test } from 'node:test'
import assert from 'node:assert/strict'

// Function-level tests through the actual production module export. Import-time
// browser globals and numeric producer inputs are synthetic, not live acceptance.
globalThis.document = { body: {} }
globalThis.window = {}
const { NavicReaderPageTurnPreviewMethods } = await import('../../androidMain/assets/reader/navic-reader-page-turn-preview.js')
const plan = NavicReaderPageTurnPreviewMethods.pageTurnRasterPreparationPlan
assert.ok(typeof plan === 'function', 'Use the actual original plan producer')

const layout = Object.freeze({ layoutGeneration: 7, viewGeneration: 3, navigationGeneration: 5,
    sectionIndex: 0, pageIndex: 0, pageCount: 8, width: 560, height: 420 })
function fixture(current = true, available = true) {
    let captures = 0
    const runtime = {
        view: { renderer: available ? { captureResolvedLayout() {
            captures += 1
            return { layout, isCurrent: () => current }
        } } : {} },
        pageTurnCaptureGeometry: () => ({ mode: 'single', viewportWidth: 560, viewportHeight: 420 }),
        effectiveReaderDirection: () => 'ltr',
        currentPagePosition: { pageIndex: 0, pageCount: 8 },
        paginationProfile: { chapters: [{ pageStartIndex: 0, pageCount: 8 }] },
    }
    return { runtime, captures: () => captures }
}

test('original raster plan retains only its still-current Live numeric layout', () => {
    const f = fixture()
    const result = plan.call(f.runtime, 0)
    assert.ok(f.captures() === 1, 'Original Live paginator must actually produce the layout')
    assert.ok(result.resolvedLayout === layout, 'Use the exact original numeric snapshot, not generated counters')
    assert.ok(result.context.currentChapterPageCount === 8, 'Keep original chapter mapping')
})
test('obsolete Live snapshot cannot qualify an otherwise plausible plan', () => {
    const f = fixture(false)
    const result = plan.call(f.runtime, 0)
    assert.ok(result.resolvedLayout === null, 'An obsolete original snapshot must remain unavailable')
})
test('missing Live paginator cannot be replaced by plan fallback geometry', () => {
    const f = fixture(true, false)
    const result = plan.call(f.runtime, 0)
    assert.ok(result.resolvedLayout === null, 'Fallback dimensions do not establish actual Live layout custody')
})
test('the actual initial query with no override uses the current chapter-local layout mapping', () => {
    const current = Object.freeze({ ...layout, sectionIndex: 1 })
    const f = fixture()
    f.runtime.view.renderer.captureResolvedLayout = () => ({ layout: current, isCurrent: () => true })
    f.runtime.currentPagePosition = { pageIndex: 12, pageCount: 20 }
    f.runtime.paginationProfile.chapters = [{ pageStartIndex: 0, pageCount: 12 },
        { pageStartIndex: 12, pageCount: 8 }]
    const result = plan.call(f.runtime, null)
    assert.equal(result.context.centerPageIndex, 12, 'Null is no override, not a request for global page zero')
    assert.equal(result.context.currentChapterIndex, 1)
    assert.equal(result.context.currentChapterPageStartIndex, 12)
    assert.equal(result.context.centerPageIndex - result.context.currentChapterPageStartIndex, current.pageIndex)
    assert.ok(result.resolvedLayout === current)
    assert.ok(!Object.hasOwn(result, 'isCurrent') && !Object.hasOwn(result, 'renderer'),
        'Currentness closure and renderer stay at the Live owner')
})
