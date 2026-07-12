export const ReaderDirectionLtr = 'ltr'
export const ReaderDirectionRtl = 'rtl'

export const ReaderLogicalDirectionNext = 'next'
export const ReaderLogicalDirectionPrevious = 'previous'

export const ReaderPhysicalPageLeft = 'left'
export const ReaderPhysicalPageRight = 'right'
export const ReaderPhysicalPageCenter = 'center'

export const ReaderPageTurnLandscapeLeaf = 'landscape-leaf'
export const ReaderPageTurnPortraitLeaf = 'portrait-leaf'
export const ReaderPageTurnPortraitSlide = 'portrait-slide'

const normalizedPageIndex = value => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? Math.floor(numeric) : null
}

const normalizedPageCount = value => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? Math.max(0, Math.floor(numeric)) : 0
}

const validPhysicalSide = value =>
  value === ReaderPhysicalPageLeft ||
  value === ReaderPhysicalPageRight ||
  value === ReaderPhysicalPageCenter

const oppositePhysicalSide = side => {
  if (side === ReaderPhysicalPageLeft) return ReaderPhysicalPageRight
  if (side === ReaderPhysicalPageRight) return ReaderPhysicalPageLeft
  return ReaderPhysicalPageCenter
}

const leadingPageSide = readerDirection =>
  readerDirection === ReaderDirectionRtl ? ReaderPhysicalPageRight : ReaderPhysicalPageLeft

const trailingPageSide = readerDirection =>
  readerDirection === ReaderDirectionRtl ? ReaderPhysicalPageLeft : ReaderPhysicalPageRight

export function readerPageLocatorForVisualIndex(profile, requestedIndex) {
  const pageIndex = normalizedPageIndex(requestedIndex)
  const pageCount = normalizedPageCount(profile?.pageCount)
  if (pageIndex == null || pageIndex < 0 || pageIndex >= pageCount) return null

  const chapter = (profile?.chapters || []).find(candidate => {
    const start = normalizedPageIndex(candidate?.pageStartIndex)
    const count = normalizedPageCount(candidate?.pageCount)
    return start != null && count > 0 && pageIndex >= start && pageIndex < start + count
  })
  if (!chapter) return null

  const pageStartIndex = Math.max(0, normalizedPageIndex(chapter.pageStartIndex) ?? 0)
  const chapterPageCount = normalizedPageCount(chapter.pageCount)
  const chapterPageIndex = pageIndex - pageStartIndex
  const anchor = chapterPageCount > 1 ? chapterPageIndex / (chapterPageCount - 1) : 0
  return Object.freeze({
    pageIndex,
    pageCount,
    spineIndex: Math.max(0, normalizedPageIndex(chapter.spineIndex) ?? 0),
    href: String(chapter.href || ''),
    chapterPageIndex,
    chapterPageCount,
    anchor,
  })
}

export function readerPhysicalPageSide({
  pageIndex,
  explicitSide = '',
  readerDirection = ReaderDirectionLtr,
  coverSide = '',
} = {}) {
  if (validPhysicalSide(explicitSide)) return explicitSide
  const normalizedIndex = normalizedPageIndex(pageIndex)
  if (normalizedIndex == null || normalizedIndex < 0) return ReaderPhysicalPageCenter

  const resolvedCoverSide = validPhysicalSide(coverSide) && coverSide !== ReaderPhysicalPageCenter
    ? coverSide
    : readerDirection === ReaderDirectionRtl
      ? ReaderPhysicalPageLeft
      : ReaderPhysicalPageRight
  const firstReadableSide = oppositePhysicalSide(resolvedCoverSide)
  return normalizedIndex % 2 === 0 ? firstReadableSide : oppositePhysicalSide(firstReadableSide)
}

const pageIndexExists = (pageIndex, pageCount) =>
  Number.isInteger(pageIndex) && pageIndex >= 0 && pageIndex < pageCount

const frozenPlan = ({
  kind,
  logicalDirection,
  sourcePageIndex,
  turningFrontPageIndex,
  turningReversePageIndex = null,
  underneathPageIndex = null,
  targetPageIndex,
  sourcePageSide,
  targetPageSide,
}) => Object.freeze({
  kind,
  logicalDirection,
  sourcePageIndex,
  turningFrontPageIndex,
  turningReversePageIndex,
  underneathPageIndex,
  targetPageIndex,
  sourcePageSide,
  targetPageSide,
})

export function readerPageTurnPlan({
  currentPageIndex,
  pageCount,
  layoutMode,
  logicalDirection,
  currentPageSide,
  readerDirection = ReaderDirectionLtr,
} = {}) {
  const sourcePageIndex = normalizedPageIndex(currentPageIndex)
  const normalizedCount = normalizedPageCount(pageCount)
  const next = logicalDirection === ReaderLogicalDirectionNext
  const previous = logicalDirection === ReaderLogicalDirectionPrevious
  if (sourcePageIndex == null || !pageIndexExists(sourcePageIndex, normalizedCount) || (!next && !previous)) {
    return null
  }

  const leading = leadingPageSide(readerDirection)
  const trailing = trailingPageSide(readerDirection)
  if (layoutMode === 'spread') {
    const plan = next
      ? {
          kind: ReaderPageTurnLandscapeLeaf,
          logicalDirection,
          sourcePageIndex,
          turningFrontPageIndex: sourcePageIndex + 1,
          turningReversePageIndex: sourcePageIndex + 2,
          underneathPageIndex: sourcePageIndex + 3,
          targetPageIndex: sourcePageIndex + 2,
          sourcePageSide: trailing,
          targetPageSide: leading,
        }
      : {
          kind: ReaderPageTurnLandscapeLeaf,
          logicalDirection,
          sourcePageIndex,
          turningFrontPageIndex: sourcePageIndex,
          turningReversePageIndex: sourcePageIndex - 1,
          underneathPageIndex: sourcePageIndex - 2,
          targetPageIndex: sourcePageIndex - 2,
          sourcePageSide: leading,
          targetPageSide: leading,
        }
    if (
      !pageIndexExists(plan.turningFrontPageIndex, normalizedCount) ||
      !pageIndexExists(plan.turningReversePageIndex, normalizedCount) ||
      !pageIndexExists(plan.underneathPageIndex, normalizedCount) ||
      !pageIndexExists(plan.targetPageIndex, normalizedCount)
    ) return null
    return frozenPlan(plan)
  }

  if (!validPhysicalSide(currentPageSide) || currentPageSide === ReaderPhysicalPageCenter) return null
  const step = next ? 1 : -1
  const targetPageIndex = sourcePageIndex + step
  if (!pageIndexExists(targetPageIndex, normalizedCount)) return null
  const leaf = next ? currentPageSide === trailing : currentPageSide === leading
  if (!leaf) {
    return frozenPlan({
      kind: ReaderPageTurnPortraitSlide,
      logicalDirection,
      sourcePageIndex,
      turningFrontPageIndex: sourcePageIndex,
      targetPageIndex,
      sourcePageSide: currentPageSide,
      targetPageSide: oppositePhysicalSide(currentPageSide),
    })
  }

  const underneathPageIndex = targetPageIndex + step
  if (!pageIndexExists(underneathPageIndex, normalizedCount)) return null
  return frozenPlan({
    kind: ReaderPageTurnPortraitLeaf,
    logicalDirection,
    sourcePageIndex,
    turningFrontPageIndex: sourcePageIndex,
    turningReversePageIndex: targetPageIndex,
    underneathPageIndex,
    targetPageIndex,
    sourcePageSide: currentPageSide,
    targetPageSide: oppositePhysicalSide(currentPageSide),
  })
}
