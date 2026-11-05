import { test } from 'node:test'
import assert from 'node:assert/strict'

// Real production producers; only browser/DOM and the final paint sink are synthetic.
globalThis.document = { body: { contains: () => true, dataset: {} } }
globalThis.window = { innerWidth: 560, innerHeight: 420 }
const {
    readerSurfaceSpreadMode,
    readerPaperLayoutProfile,
    readerSurfacePageDecorationGeometry,
} = await import('../../androidMain/assets/reader/navic-reader-paper-surface.js')
const { NavicReaderAppearanceMethods } = await import('../../androidMain/assets/reader/navic-reader-appearance.js')
const {
    ReaderFlowPaged, ReaderFlowPagedVertical, ReaderFlowScrolled, ReaderFlowScrolledGaps,
} = await import('../../androidMain/assets/reader/navic-reader-settings-core.js')

// A missing resolved-cap guard must fail before profile/decoration can invent a center binding.
for (const maxColumnCount of [1, '1']) {
    test(`paper geometry respects resolved Foliate column cap ${JSON.stringify(maxColumnCount)}`, () => {
        const spreadMode = readerSurfaceSpreadMode({
            flowMode: ReaderFlowPaged, width: 560, height: 420, maxColumnCount,
        })
        assert.equal(spreadMode, 'single')
        const layoutProfile = readerPaperLayoutProfile({ flowMode: ReaderFlowPaged, spreadMode })
        const geometry = readerSurfacePageDecorationGeometry({ spreadMode, layoutProfile, foliateGap: '0%' })
        assert.equal(layoutProfile.bindingEdge, 'left')
        assert.equal(geometry.backCoverEdge, 'right')
        assert.equal(geometry.pages.full.width, '99%')
        assert.equal(geometry.boundedSpread, false)
    })
}

test('optional cap retains legacy fallback, permitted spreads, and non-spread flows', () => {
    for (const maxColumnCount of [undefined, null, 0, 2, '2']) {
        assert.equal(readerSurfaceSpreadMode({
            flowMode: ReaderFlowPaged, width: 560, height: 420, maxColumnCount,
        }), 'spread')
    }
    for (const flowMode of [ReaderFlowPagedVertical, ReaderFlowScrolled, ReaderFlowScrolledGaps]) {
        assert.equal(readerSurfaceSpreadMode({ flowMode, width: 560, height: 420, maxColumnCount: 2 }), 'single')
    }
    assert.equal(readerSurfaceSpreadMode({
        flowMode: ReaderFlowPaged, width: 420, height: 560, maxColumnCount: 2,
    }), 'single')
})

function appearanceFixture(maxColumnCount) {
    return {
        readerSettings: { maxColumnCount },
        readerFlowModeValue: ReaderFlowPaged,
        publicationUrl: 'https://example.test/book.epub',
        view: { book: { sections: [{ href: 'chapter.xhtml' }] } },
        currentPagePosition: { pageIndex: 2, pageCount: 8 },
        lastRelocateDetail: { href: 'chapter.xhtml' },
        surfacePaperTextureIndex: NavicReaderAppearanceMethods.surfacePaperTextureIndex,
        surfaceTextureLayer: {}, movingPageTextureLayer: {},
        movingPageBorderOverlayLayer: {}, movingPageStainOverlayLayer: {},
        surfaceSpreadGutterOverlayLayer: {},
        currentRendererContainerPosition: NavicReaderAppearanceMethods.currentRendererContainerPosition,
        surfacePaperTextureDiagnosticState: NavicReaderAppearanceMethods.surfacePaperTextureDiagnosticState,
        renderSurfacePaperTextureLayers() {},
        stopSurfacePaperTextureMotionSync() {},
    }
}

// Omitting the adaptive page-box cap at the real appearance call site must fail here.
for (const columns of [1, 0, 2]) {
    test(`live appearance texture and decoration slots honor Foliate column setting ${columns}`, () => {
        const runtime = appearanceFixture(columns)
        NavicReaderAppearanceMethods.applySurfacePaperTextureUpdate.call(
            runtime, { index: 0 }, { pageIndex: 2, pageCount: 8 },
        )
        const spread = columns === 2
        assert.equal(runtime.surfaceSpreadMode, spread ? 'spread' : 'single')
        assert.equal(document.body.dataset.navicSurfaceSpreadMode, spread ? 'spread' : 'single')
        for (const slots of [runtime.surfaceTextureSlots, runtime.surfaceBorderOverlaySlots, runtime.surfaceStainOverlaySlots]) {
            assert.equal(slots.length, 3)
            for (const slot of slots) {
                assert.deepEqual(slot.spreadPages?.map(page => page.page) || [], spread ? ['left', 'right'] : [])
            }
        }
        assert.equal(runtime.surfaceSpreadGutterOverlaySlots.length, spread ? 3 : 0)
    })
}
