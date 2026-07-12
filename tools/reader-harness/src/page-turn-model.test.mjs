import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const modelPath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js',
  import.meta.url,
)
const modelSource = await readFile(modelPath, 'utf8')
const {
  ReaderDirectionLtr,
  ReaderDirectionRtl,
  ReaderLogicalDirectionNext,
  ReaderLogicalDirectionPrevious,
  ReaderPageTurnLandscapeLeaf,
  ReaderPageTurnPortraitLeaf,
  ReaderPageTurnPortraitSlide,
  ReaderPhysicalPageCenter,
  ReaderPhysicalPageLeft,
  ReaderPhysicalPageRight,
  readerPageLocatorForVisualIndex,
  readerPageTurnPlan,
  readerPhysicalPageSide,
} = await import(`data:text/javascript;base64,${Buffer.from(modelSource).toString('base64')}`)

const profile = {
  pageCount: 12,
  chapters: [
    {
      spineIndex: 3,
      href: 'chapter-1.xhtml',
      pageStartIndex: 0,
      pageCount: 3,
    },
    {
      spineIndex: 7,
      href: 'chapter-2.xhtml',
      pageStartIndex: 3,
      pageCount: 3,
    },
    {
      spineIndex: 8,
      href: 'chapter-3.xhtml',
      pageStartIndex: 6,
      pageCount: 6,
    },
  ],
}

test('visual page index resolves to exact chapter locator', () => {
  assert.deepEqual(readerPageLocatorForVisualIndex(profile, 4), {
    pageIndex: 4,
    pageCount: 12,
    spineIndex: 7,
    href: 'chapter-2.xhtml',
    chapterPageIndex: 1,
    chapterPageCount: 3,
    anchor: 0.5,
  })
})

test('visual page lookup crosses chapter boundaries without approximation', () => {
  assert.equal(readerPageLocatorForVisualIndex(profile, 2).href, 'chapter-1.xhtml')
  assert.deepEqual(readerPageLocatorForVisualIndex(profile, 3), {
    pageIndex: 3,
    pageCount: 12,
    spineIndex: 7,
    href: 'chapter-2.xhtml',
    chapterPageIndex: 0,
    chapterPageCount: 3,
    anchor: 0,
  })
  assert.equal(readerPageLocatorForVisualIndex(profile, -1), null)
  assert.equal(readerPageLocatorForVisualIndex(profile, 12), null)
})

test('explicit EPUB page side overrides parity', () => {
  assert.equal(readerPhysicalPageSide({
    pageIndex: 2,
    explicitSide: ReaderPhysicalPageCenter,
    readerDirection: ReaderDirectionLtr,
  }), ReaderPhysicalPageCenter)
  assert.equal(readerPhysicalPageSide({
    pageIndex: 2,
    explicitSide: ReaderPhysicalPageRight,
    readerDirection: ReaderDirectionLtr,
  }), ReaderPhysicalPageRight)
})

test('fallback page parity anchors the first readable page opposite the cover', () => {
  assert.equal(readerPhysicalPageSide({
    pageIndex: 0,
    readerDirection: ReaderDirectionLtr,
    coverSide: ReaderPhysicalPageRight,
  }), ReaderPhysicalPageLeft)
  assert.equal(readerPhysicalPageSide({
    pageIndex: 1,
    readerDirection: ReaderDirectionLtr,
    coverSide: ReaderPhysicalPageRight,
  }), ReaderPhysicalPageRight)
  assert.equal(readerPhysicalPageSide({
    pageIndex: 0,
    readerDirection: ReaderDirectionRtl,
    coverSide: ReaderPhysicalPageLeft,
  }), ReaderPhysicalPageRight)
})

test('landscape next turns one physical leaf and advances two pages', () => {
  assert.deepEqual(readerPageTurnPlan({
    currentPageIndex: 16,
    pageCount: 40,
    layoutMode: 'spread',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  }), {
    kind: ReaderPageTurnLandscapeLeaf,
    logicalDirection: ReaderLogicalDirectionNext,
    sourcePageIndex: 16,
    turningFrontPageIndex: 17,
    turningReversePageIndex: 18,
    underneathPageIndex: 19,
    targetPageIndex: 18,
    sourcePageSide: ReaderPhysicalPageRight,
    targetPageSide: ReaderPhysicalPageLeft,
    turningFrontPageSide: ReaderPhysicalPageRight,
    turningReversePageSide: ReaderPhysicalPageLeft,
    underneathPageSide: ReaderPhysicalPageRight,
  })
})

test('landscape previous mirrors one physical leaf and advances two pages', () => {
  assert.deepEqual(readerPageTurnPlan({
    currentPageIndex: 16,
    pageCount: 40,
    layoutMode: 'spread',
    logicalDirection: ReaderLogicalDirectionPrevious,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  }), {
    kind: ReaderPageTurnLandscapeLeaf,
    logicalDirection: ReaderLogicalDirectionPrevious,
    sourcePageIndex: 16,
    turningFrontPageIndex: 16,
    turningReversePageIndex: 15,
    underneathPageIndex: 14,
    targetPageIndex: 14,
    sourcePageSide: ReaderPhysicalPageLeft,
    targetPageSide: ReaderPhysicalPageLeft,
    turningFrontPageSide: ReaderPhysicalPageLeft,
    turningReversePageSide: ReaderPhysicalPageRight,
    underneathPageSide: ReaderPhysicalPageLeft,
  })
})

test('RTL landscape mirrors physical page roles without changing logical ordinals', () => {
  const plan = readerPageTurnPlan({
    currentPageIndex: 16,
    pageCount: 40,
    layoutMode: 'spread',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageRight,
    readerDirection: ReaderDirectionRtl,
  })
  assert.equal(plan.turningFrontPageIndex, 17)
  assert.equal(plan.turningReversePageIndex, 18)
  assert.equal(plan.underneathPageIndex, 19)
  assert.equal(plan.sourcePageSide, ReaderPhysicalPageLeft)
  assert.equal(plan.targetPageSide, ReaderPhysicalPageRight)
  assert.equal(plan.turningFrontPageSide, ReaderPhysicalPageLeft)
  assert.equal(plan.turningReversePageSide, ReaderPhysicalPageRight)
  assert.equal(plan.underneathPageSide, ReaderPhysicalPageLeft)
})

test('portrait LTR next alternates slide then leaf', () => {
  const slide = readerPageTurnPlan({
    currentPageIndex: 6,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  })
  assert.deepEqual(slide, {
    kind: ReaderPageTurnPortraitSlide,
    logicalDirection: ReaderLogicalDirectionNext,
    sourcePageIndex: 6,
    turningFrontPageIndex: 6,
    turningReversePageIndex: null,
    underneathPageIndex: null,
    targetPageIndex: 7,
    sourcePageSide: ReaderPhysicalPageLeft,
    targetPageSide: ReaderPhysicalPageRight,
    turningFrontPageSide: ReaderPhysicalPageLeft,
    turningReversePageSide: null,
    underneathPageSide: null,
  })

  const leaf = readerPageTurnPlan({
    currentPageIndex: 7,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageRight,
    readerDirection: ReaderDirectionLtr,
  })
  assert.deepEqual(leaf, {
    kind: ReaderPageTurnPortraitLeaf,
    logicalDirection: ReaderLogicalDirectionNext,
    sourcePageIndex: 7,
    turningFrontPageIndex: 7,
    turningReversePageIndex: 8,
    underneathPageIndex: 9,
    targetPageIndex: 8,
    sourcePageSide: ReaderPhysicalPageRight,
    targetPageSide: ReaderPhysicalPageLeft,
    turningFrontPageSide: ReaderPhysicalPageRight,
    turningReversePageSide: ReaderPhysicalPageLeft,
    underneathPageSide: ReaderPhysicalPageRight,
  })
})

test('portrait previous mirrors slide and leaf behavior', () => {
  const slide = readerPageTurnPlan({
    currentPageIndex: 7,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionPrevious,
    currentPageSide: ReaderPhysicalPageRight,
    readerDirection: ReaderDirectionLtr,
  })
  assert.equal(slide.kind, ReaderPageTurnPortraitSlide)
  assert.equal(slide.targetPageIndex, 6)

  const leaf = readerPageTurnPlan({
    currentPageIndex: 6,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionPrevious,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  })
  assert.equal(leaf.kind, ReaderPageTurnPortraitLeaf)
  assert.equal(leaf.turningReversePageIndex, 5)
  assert.equal(leaf.underneathPageIndex, 4)
  assert.equal(leaf.targetPageIndex, 5)
  assert.equal(leaf.turningFrontPageSide, ReaderPhysicalPageLeft)
  assert.equal(leaf.turningReversePageSide, ReaderPhysicalPageRight)
  assert.equal(leaf.underneathPageSide, ReaderPhysicalPageLeft)
})

test('portrait RTL mirrors which physical side performs the leaf', () => {
  const leaf = readerPageTurnPlan({
    currentPageIndex: 7,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionRtl,
  })
  assert.equal(leaf.kind, ReaderPageTurnPortraitLeaf)
  assert.equal(leaf.targetPageIndex, 8)
  assert.equal(leaf.targetPageSide, ReaderPhysicalPageRight)
  assert.equal(leaf.turningFrontPageSide, ReaderPhysicalPageLeft)
  assert.equal(leaf.turningReversePageSide, ReaderPhysicalPageRight)
  assert.equal(leaf.underneathPageSide, ReaderPhysicalPageLeft)
})

test('planner rejects incomplete physical bundles at publication boundaries', () => {
  assert.equal(readerPageTurnPlan({
    currentPageIndex: 10,
    pageCount: 12,
    layoutMode: 'spread',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  }), null)
  assert.equal(readerPageTurnPlan({
    currentPageIndex: 0,
    pageCount: 12,
    layoutMode: 'spread',
    logicalDirection: ReaderLogicalDirectionPrevious,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  }), null)
})

test('single-page boundaries and center pages fall back without fabricating leaves', () => {
  assert.equal(readerPageTurnPlan({
    currentPageIndex: 0,
    pageCount: 1,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  }), null)
  assert.equal(readerPageTurnPlan({
    currentPageIndex: 4,
    pageCount: 12,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageCenter,
    readerDirection: ReaderDirectionLtr,
  }), null)
})

test('RTL portrait previous mirrors one-page ordinal and physical leaf roles', () => {
  const plan = readerPageTurnPlan({
    currentPageIndex: 8,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionPrevious,
    currentPageSide: ReaderPhysicalPageRight,
    readerDirection: ReaderDirectionRtl,
  })
  assert.equal(plan.kind, ReaderPageTurnPortraitLeaf)
  assert.equal(plan.targetPageIndex, 7)
  assert.equal(plan.turningFrontPageSide, ReaderPhysicalPageRight)
  assert.equal(plan.turningReversePageSide, ReaderPhysicalPageLeft)
  assert.equal(plan.underneathPageSide, ReaderPhysicalPageRight)
})
