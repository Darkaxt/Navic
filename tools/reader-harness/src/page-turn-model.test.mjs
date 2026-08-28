import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const modelPath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-page-turn-model.js',
  import.meta.url,
)
const modelSource = await readFile(modelPath, 'utf8')
const previewPath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js',
  import.meta.url,
)
const previewSource = await readFile(previewPath, 'utf8')
const rasterPlanSource = previewSource
  .split('function pageTurnRasterPreparationPlan(', 2)[1]
  ?.split('\nfunction pageTurnPreviewContext()', 1)[0]
const pageTurnRasterPreparationPlan = Function(
  'ReaderPageRasterTargetAuthorityCurrentLive',
  'ReaderPageRasterTargetAuthorityOffscreenPassive',
  `return (function pageTurnRasterPreparationPlan(${rasterPlanSource})`,
)('CurrentLive', 'OffscreenPassive')
const {
  ReaderDirectionLtr,
  ReaderDirectionRtl,
  ReaderLogicalDirectionNext,
  ReaderLogicalDirectionPrevious,
  ReaderPageTurnLandscapeSpreadSlide,
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

const rasterPlanRuntime = {
  currentPagePosition: { pageIndex: 4, pageCount: 12 },
  paginationProfile: { chapters: profile.chapters },
  readerDirectionModeValue: ReaderDirectionLtr,
  effectiveReaderDirection: () => ReaderDirectionLtr,
  pageTurnCaptureGeometry: () => ({ mode: 'single' }),
}

test('raster preparation assigns one center live authority and passive authority elsewhere', () => {
  const plan = pageTurnRasterPreparationPlan.call(rasterPlanRuntime)
  const currentLive = plan.targets.filter(target => target.authority === 'CurrentLive')

  assert.equal(currentLive.length, 1)
  assert.equal(currentLive[0].pageIndex, plan.context.centerPageIndex)
  assert.ok(
    plan.targets
      .filter(target => target !== currentLive[0])
      .every(target => target.authority === 'OffscreenPassive'),
  )
})

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

test('landscape next slides one complete spread and advances two pages', () => {
  assert.deepEqual(readerPageTurnPlan({
    currentPageIndex: 16,
    pageCount: 40,
    layoutMode: 'spread',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  }), {
    kind: ReaderPageTurnLandscapeSpreadSlide,
    logicalDirection: ReaderLogicalDirectionNext,
    sourcePageIndex: 16,
    targetPageIndex: 18,
    sourcePageSide: ReaderPhysicalPageLeft,
    targetPageSide: ReaderPhysicalPageLeft,
  })
})

test('landscape previous slides one complete spread and retreats two pages', () => {
  assert.deepEqual(readerPageTurnPlan({
    currentPageIndex: 16,
    pageCount: 40,
    layoutMode: 'spread',
    logicalDirection: ReaderLogicalDirectionPrevious,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  }), {
    kind: ReaderPageTurnLandscapeSpreadSlide,
    logicalDirection: ReaderLogicalDirectionPrevious,
    sourcePageIndex: 16,
    targetPageIndex: 14,
    sourcePageSide: ReaderPhysicalPageLeft,
    targetPageSide: ReaderPhysicalPageLeft,
  })
})

test('RTL landscape preserves logical ordinals and mirrors spread anchor side', () => {
  const plan = readerPageTurnPlan({
    currentPageIndex: 16,
    pageCount: 40,
    layoutMode: 'spread',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageRight,
    readerDirection: ReaderDirectionRtl,
  })
  assert.equal(plan.sourcePageIndex, 16)
  assert.equal(plan.targetPageIndex, 18)
  assert.equal(plan.sourcePageSide, ReaderPhysicalPageRight)
  assert.equal(plan.targetPageSide, ReaderPhysicalPageRight)
})

test('portrait LTR next always slides one adjacent visual page', () => {
  const fromLeft = readerPageTurnPlan({
    currentPageIndex: 6,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  })
  assert.deepEqual(fromLeft, {
    kind: ReaderPageTurnPortraitSlide,
    logicalDirection: ReaderLogicalDirectionNext,
    sourcePageIndex: 6,
    targetPageIndex: 7,
    sourcePageSide: ReaderPhysicalPageLeft,
    targetPageSide: ReaderPhysicalPageRight,
  })

  const fromRight = readerPageTurnPlan({
    currentPageIndex: 7,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageRight,
    readerDirection: ReaderDirectionLtr,
  })
  assert.deepEqual(fromRight, {
    kind: ReaderPageTurnPortraitSlide,
    logicalDirection: ReaderLogicalDirectionNext,
    sourcePageIndex: 7,
    targetPageIndex: 8,
    sourcePageSide: ReaderPhysicalPageRight,
    targetPageSide: ReaderPhysicalPageLeft,
  })
})

test('portrait previous always slides one adjacent visual page', () => {
  const fromRight = readerPageTurnPlan({
    currentPageIndex: 7,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionPrevious,
    currentPageSide: ReaderPhysicalPageRight,
    readerDirection: ReaderDirectionLtr,
  })
  assert.equal(fromRight.kind, ReaderPageTurnPortraitSlide)
  assert.equal(fromRight.targetPageIndex, 6)

  const fromLeft = readerPageTurnPlan({
    currentPageIndex: 6,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionPrevious,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  })
  assert.equal(fromLeft.kind, ReaderPageTurnPortraitSlide)
  assert.equal(fromLeft.targetPageIndex, 5)
  assert.equal(fromLeft.sourcePageSide, ReaderPhysicalPageLeft)
  assert.equal(fromLeft.targetPageSide, ReaderPhysicalPageRight)
})

test('portrait RTL still slides exactly one logical page', () => {
  const slide = readerPageTurnPlan({
    currentPageIndex: 7,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionRtl,
  })
  assert.equal(slide.kind, ReaderPageTurnPortraitSlide)
  assert.equal(slide.targetPageIndex, 8)
  assert.equal(slide.targetPageSide, ReaderPhysicalPageRight)
})

test('planner accepts terminal landscape spread when its target start exists', () => {
  assert.deepEqual(readerPageTurnPlan({
    currentPageIndex: 8,
    pageCount: 12,
    layoutMode: 'spread',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  }), {
    kind: ReaderPageTurnLandscapeSpreadSlide,
    logicalDirection: ReaderLogicalDirectionNext,
    sourcePageIndex: 8,
    targetPageIndex: 10,
    sourcePageSide: ReaderPhysicalPageLeft,
    targetPageSide: ReaderPhysicalPageLeft,
  })
})

test('planner rejects targets outside publication boundaries', () => {
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

test('single-page boundaries reject navigation without fabricating pages', () => {
  assert.equal(readerPageTurnPlan({
    currentPageIndex: 0,
    pageCount: 1,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  }), null)
  assert.deepEqual(readerPageTurnPlan({
    currentPageIndex: 4,
    pageCount: 12,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageCenter,
    readerDirection: ReaderDirectionLtr,
  }), {
    kind: ReaderPageTurnPortraitSlide,
    logicalDirection: ReaderLogicalDirectionNext,
    sourcePageIndex: 4,
    targetPageIndex: 5,
    sourcePageSide: ReaderPhysicalPageCenter,
    targetPageSide: ReaderPhysicalPageCenter,
  })
})

test('RTL portrait previous preserves one-page ordinal', () => {
  const plan = readerPageTurnPlan({
    currentPageIndex: 8,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionPrevious,
    currentPageSide: ReaderPhysicalPageRight,
    readerDirection: ReaderDirectionRtl,
  })
  assert.equal(plan.kind, ReaderPageTurnPortraitSlide)
  assert.equal(plan.targetPageIndex, 7)
  assert.equal(plan.sourcePageSide, ReaderPhysicalPageRight)
  assert.equal(plan.targetPageSide, ReaderPhysicalPageLeft)
})

test('slide plans expose no curl or reverse-face roles', () => {
  const plan = readerPageTurnPlan({
    currentPageIndex: 6,
    pageCount: 20,
    layoutMode: 'single',
    logicalDirection: ReaderLogicalDirectionNext,
    currentPageSide: ReaderPhysicalPageLeft,
    readerDirection: ReaderDirectionLtr,
  })

  assert.equal('turningFrontPageIndex' in plan, false)
  assert.equal('turningReversePageIndex' in plan, false)
  assert.equal('underneathPageIndex' in plan, false)
  assert.equal('turningFrontPageSide' in plan, false)
  assert.equal('turningReversePageSide' in plan, false)
  assert.equal('underneathPageSide' in plan, false)
})
