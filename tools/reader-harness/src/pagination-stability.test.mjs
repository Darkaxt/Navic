import assert from 'node:assert/strict'
import test from 'node:test'

import {
  readerExactTextPagePosition,
  readerWaitForStableTextPagePosition,
} from '../../../composeApp/src/androidMain/assets/reader/navic-reader-pagination-stability.js'
import {
  readerBuildPaginationProfile,
  readerPaginationProfileWithObservedChapterCount,
} from '../../../composeApp/src/androidMain/assets/reader/navic-reader-pagination-model.js'

const sourceProfile = readerBuildPaginationProfile({
  fingerprint: 'profile-alpha',
  render: { runtimeVersion: 'test' },
  chapters: [
    {
      spineIndex: 7,
      href: 'chapter-a',
      title: 'Chapter A',
      pageCount: 10,
      source: 'observed',
    },
    {
      spineIndex: 8,
      href: 'chapter-b',
      title: 'Chapter B',
      pageCount: 4,
      source: 'observed',
    },
    {
      spineIndex: 9,
      href: 'chapter-c',
      title: 'Chapter C',
      pageCount: 3,
      source: 'observed',
    },
  ],
})

test('stable exact position separates coordinate commitment from profile count consistency', () => {
  const renderer = {
    exactTextPagePosition: () => ({
      index: 8,
      pageIndex: 1,
      pageCount: 5,
    }),
  }

  const actual = readerExactTextPagePosition(renderer)

  assert.deepEqual(actual, {
    index: 8,
    pageIndex: 1,
    pageCount: 5,
  })
  assert.equal(actual.index, 8)
  assert.equal(actual.pageIndex, 1)
  assert.notEqual(actual.pageCount, 4)
})

test('stable position fallback preserves a committed count when the requested local page became invalid', () => {
  const renderer = {
    exactTextPagePosition: () => null,
    getContents: () => [{ index: 8, doc: {} }],
    page: 5,
    pages: 6,
  }

  assert.deepEqual(readerExactTextPagePosition(renderer), {
    index: 8,
    pageIndex: 4,
    pageCount: 4,
  })
})

test('stable pagination waits for fonts and repeated renderer positions', async () => {
  let fontsResolved = false
  let rendered = 0
  let sampled = 0
  const renderer = {
    getContents: () => [{
      index: 8,
      doc: {
        fonts: {
          ready: Promise.resolve().then(() => {
            fontsResolved = true
          }),
        },
      },
    }],
    render: () => {
      assert.equal(fontsResolved, true)
      rendered += 1
    },
    exactTextPagePosition: () => {
      sampled += 1
      return {
        index: 8,
        pageIndex: 1,
        pageCount: sampled === 1 ? 4 : 5,
      }
    },
  }

  const position = await readerWaitForStableTextPagePosition(renderer, {
    nextFrame: async () => {},
    requiredStableSamples: 2,
    maxSamples: 4,
  })

  assert.equal(rendered, 1)
  assert.equal(sampled, 3)
  assert.deepEqual(position, {
    index: 8,
    pageIndex: 1,
    pageCount: 5,
  })
})

test('stale stable-position work is rejected before renderer mutation', async () => {
  let rendered = false
  const renderer = {
    getContents: () => [{
      index: 8,
      doc: { fonts: { ready: Promise.resolve() } },
    }],
    render: () => {
      rendered = true
    },
  }

  const position = await readerWaitForStableTextPagePosition(renderer, {
    isCurrent: () => false,
    nextFrame: async () => {},
  })

  assert.equal(position, null)
  assert.equal(rendered, false)
})

test('observed count repair rebuilds later global starts without mutating the cached profile', () => {
  const repaired = readerPaginationProfileWithObservedChapterCount(
    sourceProfile,
    {
      spineIndex: 8,
      pageCount: 5,
    },
  )

  assert.notEqual(repaired, sourceProfile)
  assert.equal(sourceProfile.pageCount, 17)
  assert.equal(sourceProfile.chapters[1].pageCount, 4)
  assert.equal(sourceProfile.chapters[2].pageStartIndex, 14)
  assert.equal(repaired.pageCount, 18)
  assert.equal(repaired.chapters[1].pageCount, 5)
  assert.equal(repaired.chapters[1].source, 'observed')
  assert.equal(repaired.chapters[2].pageStartIndex, 15)
  assert.equal(repaired.fingerprint, sourceProfile.fingerprint)
  assert.deepEqual(repaired.render, sourceProfile.render)
})

test('observed count repair rejects missing chapters and no-op counts', () => {
  assert.equal(readerPaginationProfileWithObservedChapterCount(
    sourceProfile,
    { spineIndex: 99, pageCount: 5 },
  ), null)
  assert.equal(readerPaginationProfileWithObservedChapterCount(
    sourceProfile,
    { spineIndex: 8, pageCount: 4 },
  ), null)
})

globalThis.document = {
  body: {},
  baseURI: 'https://neutral.invalid/',
}
globalThis.window = {}
globalThis.requestAnimationFrame = callback => {
  callback()
  return 1
}
const previewPath = new URL(
  '../../../composeApp/src/androidMain/assets/reader/navic-reader-page-turn-preview.js',
  import.meta.url,
)
const { NavicReaderPageTurnPreviewMethods } =
  await import(previewPath.href)

const exactPreviewRuntime = ({
  positionForRequest,
  shiftCommittedPageOnLayout = false,
}) => {
  const requests = []
  let currentPosition = null
  let navigationCommitted = false
  let layoutApplications = 0
  const runtime = {
    paginationProfile: sourceProfile,
    readerSettings: {},
    applyReaderViewportLayoutToProfilerView: () => {
      layoutApplications += 1
      if (navigationCommitted && shiftCommittedPageOnLayout) {
        currentPosition = {
          ...currentPosition,
          pageIndex: currentPosition.pageIndex + 1,
        }
      }
    },
    repairPaginationProfileFromExactPosition(locator, actual) {
      const repaired =
        readerPaginationProfileWithObservedChapterCount(
          this.paginationProfile,
          {
            spineIndex: actual.index,
            pageCount: actual.pageCount,
          },
        )
      if (repaired) this.paginationProfile = repaired
      return repaired
    },
  }
  const renderer = {
    async goToTextPage(spineIndex, chapterPageIndex) {
      requests.push({ spineIndex, chapterPageIndex })
      currentPosition = positionForRequest({
        spineIndex,
        chapterPageIndex,
      })
      navigationCommitted = true
      return true
    },
    exactTextPagePosition() {
      const { index, pageIndex, pageCount } =
        currentPosition || {}
      return pageIndex < pageCount
        ? { index, pageIndex, pageCount }
        : null
    },
    getContents: () => [{
      index: currentPosition?.index,
      doc: { fonts: { ready: Promise.resolve() } },
    }],
    get page() {
      return Number(currentPosition?.pageIndex) + 1
    },
    get pages() {
      return Number(currentPosition?.pageCount) + 2
    },
    render: () => {},
  }
  Object.assign(runtime, NavicReaderPageTurnPreviewMethods)
  return {
    runtime,
    view: { renderer },
    requests,
    layoutApplications: () => layoutApplications,
  }
}

test('passive raster accepts committed coordinates after repairing a larger chapter count', async () => {
  const { runtime, view, requests } = exactPreviewRuntime({
    positionForRequest: ({ spineIndex, chapterPageIndex }) => ({
      index: spineIndex,
      pageIndex: chapterPageIndex,
      pageCount: spineIndex === 8 ? 5 : 3,
    }),
  })

  const locator = await runtime.resolvePageTurnPreviewLocator(
    view,
    11,
    'page-turn-raster-batch',
    'Passive raster',
  )

  assert.deepEqual(requests, [
    { spineIndex: 8, chapterPageIndex: 1 },
    { spineIndex: 8, chapterPageIndex: 1 },
  ])
  assert.equal(locator.spineIndex, 8)
  assert.equal(locator.chapterPageIndex, 1)
  assert.equal(locator.chapterPageCount, 5)
  assert.equal(runtime.paginationProfile.pageCount, 18)
})

test('passive raster keeps the exact page anchor after navigation commits', async () => {
  const {
    runtime,
    view,
    requests,
    layoutApplications,
  } = exactPreviewRuntime({
    shiftCommittedPageOnLayout: true,
    positionForRequest: ({ spineIndex, chapterPageIndex }) => ({
      index: spineIndex,
      pageIndex: chapterPageIndex,
      pageCount: spineIndex === 8 ? 4 : 3,
    }),
  })

  const locator = await runtime.resolvePageTurnPreviewLocator(
    view,
    13,
    'page-turn-raster-batch',
    'Passive raster',
  )

  assert.deepEqual(requests, [
    { spineIndex: 8, chapterPageIndex: 3 },
  ])
  assert.equal(layoutApplications(), 1)
  assert.equal(locator.spineIndex, 8)
  assert.equal(locator.chapterPageIndex, 3)
})

test('passive raster remaps the global page before capture when a chapter became shorter', async () => {
  const { runtime, view, requests } = exactPreviewRuntime({
    positionForRequest: ({ spineIndex, chapterPageIndex }) => ({
      index: spineIndex,
      pageIndex: chapterPageIndex,
      pageCount: spineIndex === 8 ? 2 : 3,
    }),
  })

  const locator = await runtime.resolvePageTurnPreviewLocator(
    view,
    13,
    'page-turn-raster-batch',
    'Passive raster',
  )

  assert.deepEqual(requests, [
    { spineIndex: 8, chapterPageIndex: 3 },
    { spineIndex: 9, chapterPageIndex: 1 },
  ])
  assert.equal(locator.spineIndex, 9)
  assert.equal(locator.chapterPageIndex, 1)
  assert.equal(locator.chapterPageCount, 3)
  assert.equal(runtime.paginationProfile.pageCount, 15)
})
