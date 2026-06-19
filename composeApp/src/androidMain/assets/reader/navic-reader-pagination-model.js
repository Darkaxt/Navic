import {
  readerHrefMatches,
  stableHash,
} from './navic-reader-identity.js'

export const ReaderPaginationProfileVersion = 'navic-pagination-v1'

const readerPaginationNumber = (value, fallback = null) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

const readerPaginationInteger = (value, fallback = null) => {
  const number = readerPaginationNumber(value)
  return Number.isFinite(number) ? Math.round(number) : fallback
}

export const readerPaginationRenderState = input => ({
  version: ReaderPaginationProfileVersion,
  publicationKey: String(input?.publicationKey || ''),
  contentKey: String(input?.contentKey || ''),
  viewportWidth: readerPaginationInteger(input?.viewportWidth, 0),
  viewportHeight: readerPaginationInteger(input?.viewportHeight, 0),
  deviceScaleFactor: readerPaginationNumber(input?.deviceScaleFactor, 1),
  orientation: String(input?.orientation || ''),
  spreadMode: String(input?.spreadMode || ''),
  flowMode: String(input?.flowMode || ''),
  fontSource: String(input?.fontSource || ''),
  fontFamily: String(input?.fontFamily || ''),
  customFontFamily: String(input?.customFontFamily || ''),
  customFontUrl: String(input?.customFontUrl || ''),
  fontSizePercent: readerPaginationInteger(input?.fontSizePercent, 100),
  lineHeight: readerPaginationNumber(input?.lineHeight, 1),
  paragraphSpacingPercent: readerPaginationInteger(input?.paragraphSpacingPercent, 0),
  marginPercent: readerPaginationInteger(input?.marginPercent, 0),
  fontWeight: readerPaginationNumber(input?.fontWeight, 400),
  letterSpacing: readerPaginationNumber(input?.letterSpacing, 0),
  wordSpacing: readerPaginationNumber(input?.wordSpacing, 0),
  sideMargin: readerPaginationNumber(input?.sideMargin, 6),
  topMargin: readerPaginationNumber(input?.topMargin, 90),
  bottomMargin: readerPaginationNumber(input?.bottomMargin, 50),
  indent: readerPaginationNumber(input?.indent, 0),
  headingFontSize: readerPaginationNumber(input?.headingFontSize, 1),
  publisherCss: String(input?.publisherCss || ''),
  direction: String(input?.direction || ''),
  runtimeVersion: String(input?.runtimeVersion || ''),
})

export const readerPaginationFingerprint = input =>
  `${ReaderPaginationProfileVersion}:${stableHash(JSON.stringify(readerPaginationRenderState(input)))}`

export const readerBuildPaginationProfile = ({ fingerprint = '', chapters = [], render = null } = {}) => {
  const normalizedChapters = []
  let pageStartIndex = 0
  for (const chapter of chapters || []) {
    const pageCount = Math.max(0, Math.floor(Number(chapter?.pageCount) || 0))
    if (pageCount <= 0) continue
    const spineIndex = Math.max(0, Math.floor(Number(chapter?.spineIndex) || normalizedChapters.length))
    const source = chapter?.source === 'estimated' ? 'estimated' : 'observed'
    const normalized = {
      spineIndex,
      href: String(chapter?.href || ''),
      title: String(chapter?.title || ''),
      pageStartIndex,
      pageCount,
      source,
    }
    normalizedChapters.push(normalized)
    pageStartIndex += pageCount
  }
  const observedChapterCount = normalizedChapters.filter(chapter => chapter.source === 'observed').length
  const estimatedChapterCount = normalizedChapters.length - observedChapterCount
  return {
    version: ReaderPaginationProfileVersion,
    fingerprint: String(fingerprint || ''),
    render: render ? readerPaginationRenderState(render) : null,
    pageCount: Math.max(1, pageStartIndex),
    observedChapterCount,
    estimatedChapterCount,
    chapters: normalizedChapters,
  }
}

export const readerPaginationObservedChapterEntries = profile =>
  (profile?.chapters || [])
    .filter(chapter => chapter?.source === 'observed')
    .map((chapter, index) => {
      const spineIndex = Math.max(0, Math.floor(Number(chapter?.spineIndex) || index))
      const href = String(chapter?.href || '')
      const pageCount = Math.max(0, Math.floor(Number(chapter?.pageCount) || 0))
      return {
        key: `${spineIndex}:${href}`,
        spineIndex,
        href,
        pageCount,
      }
    })
    .filter(entry => entry.href && entry.pageCount > 0)

export const readerPaginationChapterForHref = (profile, href) => {
  const requestedHref = String(href || '')
  if (!requestedHref) return null
  return (profile?.chapters || []).find(chapter => readerHrefMatches(requestedHref, chapter.href)) || null
}

export const readerPaginationChapterForSpineIndex = (profile, spineIndex) => {
  const requestedSpineIndex = Number(spineIndex)
  if (!Number.isFinite(requestedSpineIndex)) return null
  const normalizedSpineIndex = Math.max(0, Math.floor(requestedSpineIndex))
  return (profile?.chapters || []).find(chapter => {
    const chapterSpineIndex = Number(chapter?.spineIndex)
    return Number.isFinite(chapterSpineIndex) && Math.floor(chapterSpineIndex) === normalizedSpineIndex
  }) || null
}

export const readerPaginationPositionForLocator = (profile, locator = {}) => {
  const chapter =
    readerPaginationChapterForHref(profile, locator?.href) ||
    readerPaginationChapterForSpineIndex(profile, locator?.spineIndex)
  if (!chapter) return null
  const chapterPageCount = Math.max(1, Math.floor(Number(chapter.pageCount) || 1))
  const rawChapterIndex = Number(locator?.chapterPageIndex)
  const chapterPageIndex = Number.isFinite(rawChapterIndex)
    ? Math.min(chapterPageCount - 1, Math.max(0, Math.floor(rawChapterIndex)))
    : 0
  const pageCount = Math.max(1, Math.floor(Number(profile?.pageCount) || chapterPageCount))
  const pageIndex = Math.min(
    pageCount - 1,
    Math.max(0, Math.floor(Number(chapter.pageStartIndex) || 0) + chapterPageIndex)
  )
  return {
    pageIndex,
    pageCount,
    pageCountSource: 'pagination-profile',
    chapterPageIndex,
    chapterPageCount,
    progress: pageCount > 1 ? pageIndex / (pageCount - 1) : 0,
  }
}
