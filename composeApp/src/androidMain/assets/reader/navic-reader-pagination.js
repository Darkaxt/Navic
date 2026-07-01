import {
  CenterTapMovementSlop,
  CenterTapSyntheticClickDedupeMs,
  FixedLayoutSurfaceSwipeThreshold,
  KomikkuNavigationRegionLeft,
  KomikkuNavigationRegionMenu,
  KomikkuNavigationRegionNext,
  KomikkuNavigationRegionPrevious,
  KomikkuNavigationRegionRight,
  ReaderDirectionDefault,
  ReaderDirectionLtr,
  ReaderDirectionRtl,
  ReaderDocumentThemeStyleId,
  ReaderFlowPaged,
  ReaderFlowPagedVertical,
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  ReaderFontSourceNavic,
  ReaderFontSourceSystem,
  ReaderFontSourcePublisher,
  ReaderMediaSyntheticClickSuppressMs,
  ReaderPageBorderOverlayAssets,
  ReaderPageBorderOverlayVariantCount,
  ReaderPageNumberLayerSelector,
  ReaderPaperTextureAssets,
  ReaderPaperTextureVariantCount,
  ReaderReflowableReadableUnitsPerSyntheticPage,
  ReaderReflowableStartProgressPageOffsetThreshold,
  ReaderReflowableProgressEpsilon,
  ReaderShellCoverLayerSelector,
  ReaderShellCoverTransitionMs,
  ReaderSurfacePageBorderOverlayLayerSelector,
  ReaderSurfacePaperTextureLayerSelector,
  ReaderTapZoneDefault,
  ReaderTapZoneDisabled,
  ReaderThemeLight,
  ReaderThemeSepia,
  ScrollEdgeTurnSlop,
  ScrollEdgeTurnSwipeThreshold,
  optionalNumber,
  readerDirectionMode,
  readerEffectiveFontFamily,
  readerFlowMode,
  readerFoliateFlow,
  readerFontSource,
  readerThemeKey,
  readerThemePalette
} from './navic-reader-settings.js'
import {
  readerRoot,
  overlayClass,
  ReaderThemePalettes,
  log,
  logError,
  readerTraceValue,
  readerTrace,
  readerLocationPostKey,
  describeUrl,
  post,
  reportError,
  errorElement,
  closestElement,
  readerMediaSelector,
  readerLinkHasMedia,
  isReaderMediaAnchor,
  isReaderMediaTapTarget,
  readerPointInsideRect,
  readerEventClientPoint,
  readerPointInsideAnchorText,
  readerMediaElementFromCandidate,
  readerImageFromMediaTarget,
  readerMediaTapTargetForEvent,
  readerRectSnapshot,
  readerRootTapPoint,
  markReaderMediaTapHandled,
  readerLastMediaTapRectContainsPoint,
  readerShouldSuppressMediaSyntheticClick,
  markReaderSurfaceTapHandled,
  shouldSuppressReaderSurfaceClick,
  readerAssetUrl,
  ReaderShellCoverProgressThreshold,
  readerTokenText,
  readerSectionTokenText,
  readerSectionLooksLikeCover,
  readerContentDocumentLooksLikeCover,
  suppressReaderEmbeddedCoverPage,
  readerSectionIsReadable,
  readerHrefComparable,
  readerHrefMatches,
  readerHrefMatchesSection,
  stableHash,
  readerPaperTexturePageLocator,
  readerPaperTextureVariantKey,
  readerSurfaceTextureVariantForPage,
  readerPaperTextureVariantForPage,
  readerPageBorderOverlayVariantForPage,
  readerPaperTextureTransform,
  readerPaperTextureCssOffset,
  readerPaperTextureBackgroundPosition,
  readerPaperTextureDragDirection,
  readerSurfacePaperTextureScrollOffset,
  readerSurfacePaperTextureOpacity,
  readerSurfacePageBorderOverlayOpacity,
  readerPageNumberPageCount,
  readerPageNumberPositionWithPageCount,
  readerPageNumberLabel,
  readerPageNumberBlendMode,
  readerFontFaceCss,
  readerParagraphSpacingEm,
  applyReaderParagraphSpacing,
  readerNormalizeChapterOpeningMargins,
  ensureReaderSurfaceTextureLayer,
  ensureReaderSurfaceBorderOverlayLayer,
  ensureReaderPageNumberLayer,
  ensureReaderShellCoverLayer,
  ensureReaderShellCoverImage,
  ensureTapZoneOverlayLayer,
  updateReaderShellCoverLayer,
  updateReaderSurfaceTextureLayer,
  updateReaderSurfaceBorderOverlayLayer,
  updateTapZoneOverlayLayer,
  isParagraphCandidate,
  isReaderParagraphBlock,
  classifyReaderParagraphBlocks,
  setStylesImportant,
  readerViewportSize,
  readerAdaptiveFoliatePageBox,
  readerStartLocatorHasPosition,
  flattenReaderNavigationItems,
  readerNavigationItemMatches,
  readerPaginationFingerprint,
  readerBuildPaginationProfile,
  readerPaginationObservedChapterEntries,
  readerPaginationPositionForLocator,
  readerTypographyCss,
  readerParagraphSpacingCss,
  readerMaxColumnCountValue,
  readerColumnThresholdValue,
  isThemeBackgroundMediaElement,
  readerDocumentThemeCss,
  readerContentCss,
  komikkuTapAction,
  normalizeSearchResult,
  normalizeExcerpt,
  hrefForCfi,
  flattenTocItems,
  tocLabel
} from './navic-reader-helpers.js'

const ReaderPaginationProfileStatusMeasuring = 'measuring'
const ReaderPaginationProfileStatusReady = 'ready'
const ReaderPaginationProfileStatusCached = 'cached'
const ReaderPaginationProfileStatusFailed = 'failed'

function fixedLayoutPagePosition(detail) {
  if (this.view?.isFixedLayout !== true) return null
  const pageCount = Number(this.view?.book?.sections?.length)
  const pageIndex = Number(detail?.index ?? this.fixedLayoutCurrentPageIndex())
  if (!Number.isFinite(pageCount) || pageCount <= 0 || !Number.isFinite(pageIndex)) return null
  return {
    pageIndex: Math.min(pageCount - 1, Math.max(0, Math.floor(pageIndex))),
    pageCount,
    pageCountSource: 'fixed-layout',
  }
}

function reflowablePaginatedRawTextPageCount(pages) {
  if (!Number.isFinite(pages) || pages <= 1) return 1
  return Math.max(1, Math.round(pages) - 2)
}

function reflowablePaginatedVisualTextPageCount(pages) {
  return this.reflowablePaginatedRawTextPageCount(pages)
}

function reflowablePaginatedTextPageCount(pages) {
  return this.reflowablePaginatedVisualTextPageCount(pages)
}

function reflowableChapterProgressAnchor(progress, renderer = this.view?.renderer) {
  const numericProgress = Number(progress)
  const clampedProgress = Number.isFinite(numericProgress)
    ? Math.min(1, Math.max(0, numericProgress))
    : 0
  return clampedProgress
}

function reflowableLastVisualRendererPage(renderer = this.view?.renderer) {
  const pages = Number(renderer?.pages)
  if (!Number.isFinite(pages) || pages <= 1) return 1
  const visualTextPageCount = this.reflowablePaginatedVisualTextPageCount(pages)
  return Math.max(1, visualTextPageCount)
}

function scrolledRendererViewportSize(renderer = this.view?.renderer) {
  const rendererSize = Number(renderer?.size)
  if (Number.isFinite(rendererSize) && rendererSize > 0) return rendererSize
  const start = Number(renderer?.start)
  const end = Number(renderer?.end)
  const visibleSize = Number.isFinite(start) && Number.isFinite(end) ? Math.abs(end - start) : 0
  if (Number.isFinite(visibleSize) && visibleSize > 0) return visibleSize
  const viewport = readerViewportSize()
  const sideProp = String(renderer?.sideProp || '')
  const fallback = sideProp === 'width' ? Number(viewport.width) : Number(viewport.height)
  return Number.isFinite(fallback) && fallback > 0 ? fallback : null
}

function reflowableScrolledSectionPagePosition() {
  if (this.view?.isFixedLayout === true) return null
  const renderer = this.view?.renderer
  if (!renderer || !renderer.scrolled) return null
  const start = Number(renderer.start)
  const end = Number(renderer.end)
  const viewSize = Number(renderer.viewSize)
  const viewportSize = this.scrolledRendererViewportSize(renderer)
  if (
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    !Number.isFinite(viewSize) ||
    !Number.isFinite(viewportSize) ||
    viewSize <= 0 ||
    viewportSize <= 0
  ) {
    return null
  }
  const pageCount = Math.max(1, Math.ceil(viewSize / viewportSize))
  const atSectionEnd = viewSize - end <= ScrollEdgeTurnSlop
  const rawPageIndex = atSectionEnd
    ? pageCount - 1
    : Math.floor(Math.max(0, start) / viewportSize)
  return {
    pageIndex: Math.min(pageCount - 1, Math.max(0, rawPageIndex)),
    pageCount,
    pageCountSource: 'scrolled-section',
  }
}

function reflowableSectionPagePosition() {
  if (this.view?.isFixedLayout === true) return null
  const renderer = this.view?.renderer
  if (!renderer) return null
  if (renderer.scrolled) return this.reflowableScrolledSectionPagePosition()
  let page
  let pages
  try {
    page = Number(renderer.page)
    pages = Number(renderer.pages)
  } catch (error) {
    log('reflowable-section-pages:pending', error?.message || error)
    return null
  }
  if (!Number.isFinite(page) || !Number.isFinite(pages) || pages <= 1) return null
  const pageCount = this.reflowablePaginatedTextPageCount(pages)
  return {
    pageIndex: Math.min(pageCount - 1, Math.max(0, Math.floor(page - 1))),
    pageCount,
    pageCountSource: 'section',
  }
}

function reflowableLocationPagePosition(detail) {
  if (this.view?.isFixedLayout === true) return null
  const location = detail?.location
  const pageCount = Number(location?.total)
  const progress = Number(detail?.fraction ?? detail?.progress ?? detail?.totalProgress)
  const clampedProgress = Number.isFinite(progress) ? Math.min(1, Math.max(0, progress)) : null
  const progressPageIndex = Number.isFinite(clampedProgress) ? Math.floor(clampedProgress * pageCount) : null
  const locationPageIndex = Number(location?.current)
  let pageIndex = Number.isFinite(locationPageIndex) ? locationPageIndex : progressPageIndex
  if (!Number.isFinite(pageIndex) || !Number.isFinite(pageCount) || pageCount <= 0) return null
  pageIndex = Math.min(pageCount - 1, Math.max(0, Math.floor(pageIndex)))

  const sectionIndex = Number(detail?.section?.current ?? detail?.index)
  const progressBucket = Number.isFinite(progressPageIndex)
    ? Math.min(pageCount - 1, Math.max(0, progressPageIndex))
    : pageIndex
  const signature = [
    detail?.href || detail?.tocItem?.href || '',
    detail?.cfi || '',
    Number.isFinite(sectionIndex) ? Math.floor(sectionIndex) : '',
    Number.isFinite(progressBucket) ? progressBucket : '',
  ].join('|')
  const previousPageIndex = this.reflowableLastLocationPageIndex == null
    ? null
    : Number(this.reflowableLastLocationPageIndex)
  const previousSectionIndex = this.reflowableLastLocationSectionIndex == null
    ? null
    : Number(this.reflowableLastLocationSectionIndex)
  const previousProgressBucket = this.reflowableLastLocationProgressBucket == null
    ? null
    : Number(this.reflowableLastLocationProgressBucket)
  const previousProgress = this.reflowableLastLocationProgress == null
    ? null
    : Number(this.reflowableLastLocationProgress)
  const signatureChanged = signature !== this.reflowableLastLocationSignature
  const sameSection =
    Number.isFinite(sectionIndex) &&
    Number.isFinite(previousSectionIndex) &&
    Math.floor(sectionIndex) === previousSectionIndex
  const advancedProgressWithinSection =
    sameSection &&
    Number.isFinite(clampedProgress) &&
    Number.isFinite(previousProgress) &&
    clampedProgress > previousProgress + ReaderReflowableProgressEpsilon
  const progressedToNewBucketWithinSection =
    sameSection &&
    Number.isFinite(progressBucket) &&
    Number.isFinite(previousProgressBucket) &&
    progressBucket > previousProgressBucket
  const progressDidNotMoveBackward =
    !Number.isFinite(clampedProgress) ||
    !Number.isFinite(previousProgress) ||
    clampedProgress >= previousProgress - ReaderReflowableProgressEpsilon
  const advancedToLaterSection =
    Number.isFinite(sectionIndex) &&
    Number.isFinite(previousSectionIndex) &&
    Math.floor(sectionIndex) > previousSectionIndex
  if (
    sameSection &&
    Number.isFinite(previousPageIndex) &&
    pageIndex < previousPageIndex &&
    progressDidNotMoveBackward
  ) {
    pageIndex = previousPageIndex
  }
  if (
    signatureChanged &&
    Number.isFinite(previousPageIndex) &&
    pageIndex <= previousPageIndex &&
    (advancedProgressWithinSection || progressedToNewBucketWithinSection || advancedToLaterSection)
  ) {
    pageIndex = previousPageIndex + 1
  }
  pageIndex = Math.min(pageCount - 1, Math.max(0, Math.floor(pageIndex)))
  this.reflowableLastLocationSignature = signature
  this.reflowableLastLocationPageIndex = pageIndex
  this.reflowableLastLocationSectionIndex = Number.isFinite(sectionIndex) ? Math.floor(sectionIndex) : null
  this.reflowableLastLocationProgressBucket = Number.isFinite(progressBucket) ? Math.floor(progressBucket) : null
  this.reflowableLastLocationProgress = Number.isFinite(clampedProgress) ? clampedProgress : null
  return this.normalizedReflowablePagePosition({
    pageIndex,
    pageCount: Math.floor(pageCount),
    pageCountSource: 'location',
  }, detail)
}

function readerPageListPosition(detail) {
  if (this.view?.isFixedLayout === true) return null
  const pageItem = detail?.pageItem
  if (!pageItem) return null
  const pageListItems = this.readerPageListItems()
  if (!pageListItems.length) return null
  const pageIndex = pageListItems.findIndex(item => readerNavigationItemMatches(item, pageItem))
  if (pageIndex < 0) return null
  return {
    pageIndex,
    pageCount: pageListItems.length,
    pageCountSource: 'page-list',
  }
}

function readerPageListItems() {
  if (this.view?.isFixedLayout === true) return []
  return flattenReaderNavigationItems(this.view?.book?.pageList || [])
}

function readerPageListPageCount() {
  const pageListItems = this.readerPageListItems()
  return pageListItems.length > 0 ? pageListItems.length : null
}

function reflowableSectionSizes() {
  return Array.from(this.view?.book?.sections || []).map(section => {
    const size = section?.linear === 'no' ? 0 : Number(section?.size)
    return Number.isFinite(size) && size > 0 ? size : 0
  })
}

function readerPaginationSectionHref(section, index) {
  return section?.href || section?.id || section?.url || section?.name || `section-${index}`
}

function readerPaginationSectionTitle(section, index) {
  return section?.label || section?.title || section?.name || `Section ${index + 1}`
}

function readerPaginationContentKey() {
  const sections = Array.from(this.view?.book?.sections || [])
  const sectionTokens = sections.map((section, index) => [
    index,
    this.readerPaginationSectionHref(section, index),
    section?.linear || '',
    Number(section?.size) || 0,
  ].join(':'))
  return stableHash(sectionTokens.join('|'))
}

function readerPaginationRenderMetadata() {
  const viewport = readerViewportSize()
  const settings = this.readerSettings || {}
  const width = Number(viewport.width)
  const height = Number(viewport.height)
  const adaptivePageBox = readerAdaptiveFoliatePageBox({ width, height }, settings)
  return {
    publicationKey: this.publicationUrl,
    contentKey: this.readerPaginationContentKey(),
    viewportWidth: width,
    viewportHeight: height,
    deviceScaleFactor: window.devicePixelRatio || 1,
    orientation: width >= height ? 'landscape' : 'portrait',
    spreadMode: width >= height ? 'dual' : 'single',
    flowMode: this.readerFlowModeValue || readerFlowMode(settings),
    fontSource: readerFontSource(settings),
    fontFamily: readerEffectiveFontFamily(settings),
    customFontFamily: settings.customFontFamily || '',
    customFontUrl: settings.customFontUrl || '',
    fontSizePercent: settings.fontSizePercent ?? 140,
    lineHeight: settings.lineHeight ?? 1,
    paragraphSpacingPercent: settings.paragraphSpacingPercent ?? settings.paragraphSpacing ?? 0,
    marginPercent: settings.marginPercent ?? 0,
    fontWeight: settings.fontWeight ?? 400,
    letterSpacing: settings.letterSpacing ?? 0,
    wordSpacing: settings.wordSpacing ?? 0,
    sideMargin: settings.sideMargin ?? 6,
    topMargin: settings.topMargin ?? 90,
    bottomMargin: settings.bottomMargin ?? 50,
    indent: settings.indent ?? 0,
    headingFontSize: settings.headingFontSize ?? 1,
    maxColumnCount: readerMaxColumnCountValue(settings),
    columnThreshold: readerColumnThresholdValue(settings),
    adaptivePageBox,
    publisherCss: readerFontSource(settings) === ReaderFontSourcePublisher ? 'publisher' : 'navic',
    direction: this.readerDirectionModeValue || readerDirectionMode(settings),
    runtimeVersion: 'navic-reader-pagination-profile-1',
  }
}

function readerPaginationRenderFingerprint() {
  return readerPaginationFingerprint(this.readerPaginationRenderMetadata())
}

function readerPaginationCacheKey(fingerprint) {
  return `navic-reader-pagination-profile:${fingerprint}`
}

function readCachedPaginationProfile(fingerprint) {
  if (!fingerprint) return null
  try {
    const raw = window.localStorage?.getItem?.(this.readerPaginationCacheKey(fingerprint))
    if (!raw) return null
    const profile = JSON.parse(raw)
    if (profile?.fingerprint !== fingerprint) return null
    if (!profile?.render) return null
    if (!Number.isFinite(Number(profile.render.viewportWidth))) return null
    if (!Number.isFinite(Number(profile.render.viewportHeight))) return null
    if (!Array.isArray(profile?.chapters) || profile.chapters.length <= 0) return null
    return profile
  } catch (error) {
    log('pagination-profile:cache-read-failed', error?.message || error)
    return null
  }
}

function writeCachedPaginationProfile(profile) {
  if (!profile?.fingerprint) return
  try {
    window.localStorage?.setItem?.(this.readerPaginationCacheKey(profile.fingerprint), JSON.stringify(profile))
  } catch (error) {
    log('pagination-profile:cache-write-failed', error?.message || error)
  }
}

function isCompletePaginationProfile(profile) {
  return Boolean(profile?.chapters?.length) && Number(profile?.estimatedChapterCount) === 0
}

function observedChapterKey(index, section) {
  return `${Math.max(0, Math.floor(Number(index) || 0))}:${this.readerPaginationSectionHref(section, index)}`
}

function hydrateObservedChapterPageCountsFromProfile(profile) {
  for (const entry of readerPaginationObservedChapterEntries(profile)) {
    this.observedChapterPageCounts.set(entry.key, entry.pageCount)
  }
}

function paginationProfileObservedSignature(profile) {
  return readerPaginationObservedChapterEntries(profile)
    .map(entry => `${entry.key}:${entry.pageCount}`)
    .join('|')
}

function paginationProfileHasObservedCountIncrease(freshProfile, currentProfile) {
  const currentCountsByKey = new Map()
  for (const entry of readerPaginationObservedChapterEntries(currentProfile)) {
    currentCountsByKey.set(entry.key, Math.max(0, Math.floor(Number(entry.pageCount) || 0)))
  }
  for (const entry of readerPaginationObservedChapterEntries(freshProfile)) {
    const freshCount = Math.max(0, Math.floor(Number(entry.pageCount) || 0))
    const currentCount = currentCountsByKey.get(entry.key) || 0
    if (freshCount > currentCount) return true
  }
  return false
}

function postPaginationProfileStatus(status, payload = {}) {
  const message = {
    type: 'paginationProfileStatus',
    status,
    fingerprint: this.paginationFingerprint || payload.fingerprint || null,
    ...payload,
  }
  readerTrace('pagination-profile:status', message)
  post(message)
}

function paginationProfileSectionPageCount(renderer) {
  let pages
  try {
    pages = Number(renderer?.pages)
  } catch {
    pages = null
  }
  if (!Number.isFinite(pages) || pages <= 1) return 1
  return this.reflowablePaginatedTextPageCount(pages)
}

async function buildCompletePaginationProfileInProfilerView({ url, fingerprint, settings, token }) {
  if (!url || !fingerprint || this.view?.isFixedLayout === true) return null
  const profileView = document.createElement('foliate-view')
  profileView.dataset.navicPaginationProfiler = 'true'
  profileView.setAttribute('aria-hidden', 'true')
  profileView.addEventListener('load', event => {
    const detail = event.detail || {}
    this.applyDocumentTheme(detail.doc, settings, detail.index)
  })
  readerRoot.append(profileView)
  try {
    this.applyReaderViewportLayoutToProfilerView(profileView, settings)
    await profileView.open(url)
    this.applyReaderViewportLayoutToProfilerView(profileView, settings)
    const sections = Array.from(profileView?.book?.sections || [])
    const readableEntries = sections
      .map((section, index) => ({ section, index }))
      .filter(({ section, index }) =>
        readerSectionIsReadable(section) && !this.sectionTargetsCover(section, index)
      )
    if (!readableEntries.length) return null
    this.postPaginationProfileStatus(ReaderPaginationProfileStatusMeasuring, {
      fingerprint,
      completedSections: 0,
      totalSections: readableEntries.length,
    })
    const measuredPageCounts = new Map()
    for (const { section, index } of readableEntries) {
      if (token !== this.paginationProfileTaskToken) return null
      await profileView.goTo(index)
      this.applyReaderViewportLayoutToProfilerView(profileView, settings)
      const pageCount = this.paginationProfileSectionPageCount(profileView.renderer)
      measuredPageCounts.set(index, pageCount)
      this.postPaginationProfileStatus(ReaderPaginationProfileStatusMeasuring, {
        fingerprint,
        completedSections: measuredPageCounts.size,
        totalSections: readableEntries.length,
        href: this.readerPaginationSectionHref(section, index),
        sectionPageCount: pageCount,
      })
    }
    const chapters = sections.map((section, index) => {
      const pageCount = measuredPageCounts.get(index) || 0
      return {
        spineIndex: index,
        href: this.readerPaginationSectionHref(section, index),
        title: this.readerPaginationSectionTitle(section, index),
        pageCount,
        source: pageCount > 0 ? 'observed' : 'estimated',
      }
    })
    return readerBuildPaginationProfile({ fingerprint, chapters, render: this.readerPaginationRenderMetadata() })
  } finally {
    profileView.close?.()
    profileView.remove?.()
  }
}

async function ensureCompletePaginationProfile(url = this.publicationUrl, settings = this.readerSettings) {
  if (this.view?.isFixedLayout === true) return null
  const fingerprint = this.readerPaginationRenderFingerprint()
  this.paginationFingerprint = fingerprint
  const cachedProfile = this.readCachedPaginationProfile(fingerprint)
  if (
    cachedProfile?.chapters?.length &&
    cachedProfile.estimatedChapterCount === 0
  ) {
    this.paginationProfile = cachedProfile
    this.hydrateObservedChapterPageCountsFromProfile(cachedProfile)
    this.postPaginationProfileStatus(ReaderPaginationProfileStatusCached, {
      fingerprint,
      pageCount: cachedProfile.pageCount,
      completedSections: cachedProfile.observedChapterCount || 0,
      totalSections: cachedProfile.observedChapterCount || 0,
    })
    readerTrace('pagination-profile:cache-hit', {
      fingerprint,
      pageCount: cachedProfile.pageCount,
      chapterCount: cachedProfile.chapters?.length || 0,
      observedChapterCount: cachedProfile.observedChapterCount || 0,
    })
    this.postCurrentLocationSnapshot('pagination-profile-cached')
    return cachedProfile
  }
  const token = ++this.paginationProfileTaskToken
  this.paginationProfileMeasurementInProgress = true
  try {
    const profile = await this.buildCompletePaginationProfileInProfilerView({ url, fingerprint, settings, token })
    if (!profile?.chapters?.length || token !== this.paginationProfileTaskToken) return this.paginationProfile
    this.paginationProfile = profile
    this.hydrateObservedChapterPageCountsFromProfile(profile)
    this.writeCachedPaginationProfile(profile)
    readerTrace('pagination-profile:updated', {
      fingerprint,
      pageCount: profile.pageCount,
      chapterCount: profile.chapters.length,
      observedChapterCount: profile.observedChapterCount || 0,
      estimatedChapterCount: profile.estimatedChapterCount || 0,
      complete: profile.estimatedChapterCount === 0,
    })
    this.postPaginationProfileStatus(ReaderPaginationProfileStatusReady, {
      fingerprint,
      pageCount: profile.pageCount,
      completedSections: profile.observedChapterCount || 0,
      totalSections: profile.observedChapterCount || 0,
    })
    this.postCurrentLocationSnapshot('pagination-profile-ready')
    return profile
  } catch (error) {
    readerTrace('pagination-profile:failed', {
      fingerprint,
      message: error?.message || String(error),
    })
    this.postPaginationProfileStatus(ReaderPaginationProfileStatusFailed, {
      fingerprint,
      message: error?.message || String(error),
    })
    return this.paginationProfile
  } finally {
    if (token === this.paginationProfileTaskToken) {
      this.paginationProfileMeasurementInProgress = false
    }
  }
}

function shouldUseFreshPaginationProfile(freshProfile) {
  if (!freshProfile?.chapters?.length) return false
  if (!this.paginationProfile?.chapters?.length) return true
  if (freshProfile.fingerprint !== this.paginationProfile.fingerprint) return true
  if (this.paginationProfileHasObservedCountIncrease(freshProfile, this.paginationProfile)) return true
  const currentEstimatedCount = Math.max(0, Number(this.paginationProfile.estimatedChapterCount) || 0)
  if (currentEstimatedCount === 0) return false
  const freshObservedCount = Math.max(0, Number(freshProfile.observedChapterCount) || 0)
  const currentObservedCount = Math.max(0, Number(this.paginationProfile.observedChapterCount) || 0)
  if (freshObservedCount > currentObservedCount) return true
  if (freshObservedCount < currentObservedCount) return false
  return this.paginationProfileObservedSignature(freshProfile) !==
    this.paginationProfileObservedSignature(this.paginationProfile)
}

function readerBuildPaginationProfileFromSectionPosition(detail, sectionPosition) {
  if (this.view?.isFixedLayout === true || !sectionPosition) return null
  const sectionIndex = Number(detail?.section?.current ?? detail?.index)
  if (!Number.isFinite(sectionIndex)) return null
  const normalizedSectionIndex = Math.max(0, Math.floor(sectionIndex))
  const sections = Array.from(this.view?.book?.sections || [])
  if (!sections.length) return null
  const sectionSizes = this.reflowableSectionSizes()
  const currentSection = sections[normalizedSectionIndex]
  if (!readerSectionIsReadable(currentSection) || this.sectionTargetsCover(currentSection, normalizedSectionIndex)) {
    return null
  }
  const currentSectionSize = Number(sectionSizes[normalizedSectionIndex])
  const currentSectionPageCount = Number(sectionPosition.pageCount)
  if (!Number.isFinite(currentSectionPageCount) || currentSectionPageCount <= 0) return null
  this.observedChapterPageCounts.set(
    this.observedChapterKey(normalizedSectionIndex, currentSection),
    Math.max(1, Math.floor(currentSectionPageCount))
  )
  const readableUnitsPerPage =
    Number.isFinite(currentSectionSize) && currentSectionSize > 0
      ? currentSectionSize / currentSectionPageCount
      : ReaderReflowableReadableUnitsPerSyntheticPage
  if (!Number.isFinite(readableUnitsPerPage) || readableUnitsPerPage <= 0) return null
  const chapters = sections.map((section, index) => {
    if (!readerSectionIsReadable(section) || this.sectionTargetsCover(section, index)) {
      return {
        spineIndex: index,
        href: this.readerPaginationSectionHref(section, index),
        title: this.readerPaginationSectionTitle(section, index),
        pageCount: 0,
        source: 'estimated',
      }
    }
    const observedPageCount = this.observedChapterPageCounts.get(this.observedChapterKey(index, section))
    const sectionSize = Number(sectionSizes[index])
    const estimatedPageCount = Number.isFinite(sectionSize) && sectionSize > 0
      ? Math.max(1, Math.ceil(sectionSize / readableUnitsPerPage))
      : 0
    return {
      spineIndex: index,
      href: this.readerPaginationSectionHref(section, index),
      title: this.readerPaginationSectionTitle(section, index),
      pageCount: observedPageCount || estimatedPageCount,
      source: observedPageCount ? 'observed' : 'estimated',
    }
  })
  const fingerprint = this.paginationFingerprint || this.readerPaginationRenderFingerprint()
  return readerBuildPaginationProfile({ fingerprint, chapters, render: this.readerPaginationRenderMetadata() })
}

function readerEnsurePaginationProfile(detail, sectionPosition) {
  if (this.view?.isFixedLayout === true) return null
  const fingerprint = this.readerPaginationRenderFingerprint()
  if (this.paginationFingerprint !== fingerprint) {
    this.paginationFingerprint = fingerprint
    const cachedProfile = this.readCachedPaginationProfile(fingerprint)
    this.paginationProfile = this.paginationProfileMeasurementInProgress && !this.isCompletePaginationProfile(cachedProfile)
      ? null
      : cachedProfile
    this.observedChapterPageCounts = new Map()
    if (this.paginationProfile) {
      this.hydrateObservedChapterPageCountsFromProfile(this.paginationProfile)
      readerTrace('pagination-profile:cache-hit', {
        fingerprint,
        pageCount: this.paginationProfile.pageCount,
        chapterCount: this.paginationProfile.chapters?.length || 0,
        observedChapterCount: this.paginationProfile.observedChapterCount || 0,
      })
    }
  }
  const freshProfile = this.readerBuildPaginationProfileFromSectionPosition(detail, sectionPosition)
  if (freshProfile?.chapters?.length) {
    if (
      this.paginationProfileMeasurementInProgress &&
      !this.isCompletePaginationProfile(freshProfile) &&
      !this.isCompletePaginationProfile(this.paginationProfile)
    ) {
      readerTrace('pagination-profile:provisional-retained', {
        fingerprint,
        pageCount: freshProfile.pageCount,
        chapterCount: freshProfile.chapters.length,
        observedChapterCount: freshProfile.observedChapterCount || 0,
        estimatedChapterCount: freshProfile.estimatedChapterCount || 0,
      })
      return this.paginationProfile
    }
    if (this.shouldUseFreshPaginationProfile(freshProfile)) {
      this.paginationProfile = freshProfile
      this.writeCachedPaginationProfile(freshProfile)
      readerTrace('pagination-profile:updated', {
        fingerprint,
        pageCount: freshProfile.pageCount,
        chapterCount: freshProfile.chapters.length,
        observedChapterCount: freshProfile.observedChapterCount || 0,
        estimatedChapterCount: freshProfile.estimatedChapterCount || 0,
      })
    } else {
      readerTrace('pagination-profile:retained', {
        fingerprint,
        pageCount: this.paginationProfile?.pageCount || 0,
        chapterCount: this.paginationProfile?.chapters?.length || 0,
        observedChapterCount: this.paginationProfile?.observedChapterCount || 0,
        freshPageCount: freshProfile.pageCount,
      })
    }
  }
  return this.paginationProfile
}

function readerPaginationProfilePosition(detail, sectionPosition = this.reflowableSectionPagePosition()) {
  if (!sectionPosition || this.view?.isFixedLayout === true) return null
  const sectionIndex = Number(detail?.section?.current ?? detail?.index)
  const sectionHref = this.sectionHrefForDetail(detail) || detail?.href || detail?.tocItem?.href || ''
  const profile = this.readerEnsurePaginationProfile(detail, sectionPosition)
  const position = readerPaginationPositionForLocator(profile, {
    href: sectionHref,
    spineIndex: sectionIndex,
    chapterPageIndex: sectionPosition.pageIndex,
    chapterPageCount: sectionPosition.pageCount,
  })
  if (position?.pageCountSource === 'pagination-profile') {
    readerTrace('pagination-profile:position', {
      href: sectionHref,
      spineIndex: sectionIndex,
      pageIndex: position.pageIndex,
      pageCount: position.pageCount,
      chapterPageIndex: position.chapterPageIndex,
      chapterPageCount: position.chapterPageCount,
      pageCountSource: 'pagination-profile',
    })
    return this.normalizedReflowablePagePosition(position, detail)
  }
  return null
}

function reflowableStableBookPageModel(sectionIndex, sectionPosition, sectionSizes) {
  const totalReadableSize = sectionSizes.reduce((sum, size) => sum + size, 0)
  if (!Number.isFinite(totalReadableSize) || totalReadableSize <= 0) return null
  const currentSectionSize = Number(sectionSizes[sectionIndex])
  const currentSectionPageCount = Number(sectionPosition?.pageCount)
  const hasVisualSectionMeasure =
    Number.isFinite(currentSectionSize) &&
    currentSectionSize > 0 &&
    Number.isFinite(currentSectionPageCount) &&
    currentSectionPageCount > 0
  const source = hasVisualSectionMeasure ? 'visual-layout' : 'synthetic-location'
  const readableUnitsPerPage = hasVisualSectionMeasure
    ? currentSectionSize / currentSectionPageCount
    : ReaderReflowableReadableUnitsPerSyntheticPage
  if (!Number.isFinite(readableUnitsPerPage) || readableUnitsPerPage <= 0) return null
  const pageCount = Math.max(1, Math.ceil(totalReadableSize / readableUnitsPerPage))
  const shouldSetModel =
    !this.reflowableBookPageModel ||
    this.reflowableBookPageModel.totalReadableSize !== totalReadableSize ||
    this.reflowableBookPageModel.source !== source ||
    (source !== 'visual-layout' && this.reflowableBookPageModel.pageCount !== pageCount)
  if (shouldSetModel) {
    this.reflowableBookPageModel = {
      source,
      sectionIndex,
      totalReadableSize,
      readableUnitsPerPage,
      pageCount,
    }
    log(
      'reflowable-page-model:set',
      this.reflowableBookPageModel.source,
      `section=${sectionIndex}`,
      `pages=${pageCount}`,
      `unitsPerPage=${Math.round(readableUnitsPerPage)}`
    )
  }
  return this.reflowableBookPageModel
}

function normalizedReflowablePagePosition(pagePosition, detail) {
  if (!pagePosition) return null
  const progress = Number(detail?.fraction ?? detail?.progress ?? detail?.totalProgress)
  const canApplyStartOffset = pagePosition.pageCountSource !== 'location'
  if (
    canApplyStartOffset &&
    this.reflowablePageIndexOffset == null &&
    Number.isFinite(progress) &&
    progress >= 0 &&
    progress <= ReaderReflowableStartProgressPageOffsetThreshold &&
    Number.isFinite(pagePosition.pageIndex) &&
    pagePosition.pageIndex > 0
  ) {
    this.reflowablePageIndexOffset = pagePosition.pageIndex
    log('reflowable-page-offset:set', `offset=${this.reflowablePageIndexOffset}`, `progress=${progress}`)
  }
  const offset = Number(this.reflowablePageIndexOffset)
  if (!Number.isFinite(offset) || offset <= 0) return pagePosition
  return {
    ...pagePosition,
    pageIndex: Math.min(
      pagePosition.pageCount - 1,
      Math.max(0, pagePosition.pageIndex - offset)
    ),
  }
}

function reflowableWholeBookPagePosition(detail) {
  detail = detail || this.lastRelocateDetail || {}
  const sectionPosition = this.reflowableSectionPagePosition()
  if (!sectionPosition) return null
  const sectionIndex = Number(detail?.section?.current ?? detail?.index)
  const sectionSizes = this.reflowableSectionSizes()
  if (!Number.isFinite(sectionIndex)) return null
  const normalizedSectionIndex = Math.max(0, Math.floor(sectionIndex))
  const model = this.reflowableStableBookPageModel(normalizedSectionIndex, sectionPosition, sectionSizes)
  if (!model || !Number.isFinite(model.readableUnitsPerPage) || model.readableUnitsPerPage <= 0) return null
  const readableUnitsBeforeCurrentSection = sectionSizes
    .slice(0, normalizedSectionIndex)
    .reduce((sum, size) => sum + size, 0)
  const estimatedGlobalPageCount = Math.max(
    model.pageCount,
    Math.ceil(model.totalReadableSize / model.readableUnitsPerPage)
  )
  const estimatedGlobalPageIndex = Math.floor(readableUnitsBeforeCurrentSection / model.readableUnitsPerPage) +
    sectionPosition.pageIndex
  return this.normalizedReflowablePagePosition({
    pageIndex: Math.min(
      estimatedGlobalPageCount - 1,
      Math.max(0, estimatedGlobalPageIndex)
    ),
    pageCount: estimatedGlobalPageCount,
    pageCountSource: model.source,
  }, detail)
}

function reflowablePagePosition(detail) {
  const sectionPosition = this.reflowableSectionPagePosition()
  return this.readerPaginationProfilePosition(detail, sectionPosition) ||
    this.reflowableWholeBookPagePosition(detail) ||
    this.reflowableLocationPagePosition(detail) ||
    this.readerPageListPosition(detail) ||
    sectionPosition
}

function readerPagePosition(detail) {
  return this.fixedLayoutPagePosition(detail) || this.reflowablePagePosition(detail)
}

function chapterPagePosition(detail, fallback = null) {
  const pagePosition = this.view?.isFixedLayout === true
    ? this.fixedLayoutPagePosition(detail)
    : this.reflowableSectionPagePosition()
  const resolved = this.view?.isFixedLayout === true
    ? pagePosition || fallback
    : pagePosition
  if (!resolved) return null
  const pageIndex = Number(resolved.pageIndex)
  const pageCount = Number(resolved.pageCount)
  if (!Number.isFinite(pageIndex) || !Number.isFinite(pageCount) || pageCount <= 0) return null
  return {
    pageIndex: Math.min(pageCount - 1, Math.max(0, Math.floor(pageIndex))),
    pageCount: Math.max(1, Math.floor(pageCount)),
    progress: pageCount > 1
      ? Math.min(1, Math.max(0, pageIndex / (pageCount - 1)))
      : 0,
  }
}

function detailSectionKey(detail) {
  const index = Number(detail?.section?.current ?? detail?.index)
  if (Number.isFinite(index)) return `index:${Math.floor(index)}`
  const href = detail?.href || detail?.tocItem?.href || detail?.section?.href || ''
  const comparableHref = readerHrefComparable(href)
  return comparableHref ? `href:${comparableHref}` : ''
}

function committedPageTurnPosition(pagePosition, detail, reason) {
  if (!pagePosition || !String(reason || '').startsWith('page-turn:')) return pagePosition
  if (pagePosition.pageCountSource === 'fixed-layout') return pagePosition
  const currentSectionKey = this.detailSectionKey(this.committedRelocateDetail)
  const candidateSectionKey = this.detailSectionKey(detail)
  const sameSection = Boolean(currentSectionKey && currentSectionKey === candidateSectionKey)
  const explicitTargetPageIndex = Number(this.pageTurnTargetPageIndex)
  const explicitTargetPageCount = readerPageNumberPageCount(pagePosition, this.currentPagePosition?.pageCount)
  if (
    sameSection &&
    Number.isFinite(explicitTargetPageIndex) &&
    Number.isFinite(explicitTargetPageCount) &&
    explicitTargetPageCount > 0
  ) {
    const clampedTargetPageIndex = Math.min(
      explicitTargetPageCount - 1,
      Math.max(0, Math.floor(explicitTargetPageIndex))
    )
    return {
      ...pagePosition,
      pageIndex: clampedTargetPageIndex,
      pageCount: explicitTargetPageCount,
      pageCountSource: pagePosition.pageCountSource || 'page-turn',
    }
  }
  const chapterPageCount = Number(pagePosition.chapterPageCount)
  const chapterPageIndex = Number(pagePosition.chapterPageIndex)
  if (
    pagePosition.pageCountSource === 'pagination-profile' &&
    Number.isFinite(chapterPageCount) &&
    chapterPageCount <= 1 &&
    (!Number.isFinite(chapterPageIndex) || chapterPageIndex <= 0)
  ) {
    return pagePosition
  }
  const currentPageIndex = Number(this.currentPagePosition?.pageIndex)
  const candidatePageIndex = Number(pagePosition.pageIndex)
  const pageCount = readerPageNumberPageCount(pagePosition, this.currentPagePosition?.pageCount)
  if (!Number.isFinite(currentPageIndex) || !Number.isFinite(candidatePageIndex) || !Number.isFinite(pageCount) || pageCount <= 0) {
    return pagePosition
  }
  if (pagePosition.pageCountSource === 'pagination-profile' && !sameSection) {
    return pagePosition
  }
  if (
    pagePosition.pageCountSource === 'pagination-profile' &&
    this.currentPagePosition?.pageCountSource === 'pagination-profile' &&
    candidatePageIndex === currentPageIndex
  ) {
    return pagePosition
  }
  const direction = String(reason).includes(':previous') ? 'previous' : 'next'
  const targetPageIndex = direction === 'previous'
    ? currentPageIndex - 1
    : currentPageIndex + 1
  if (direction === 'next' && candidatePageIndex === targetPageIndex) return pagePosition
  if (direction === 'previous' && candidatePageIndex === targetPageIndex) return pagePosition
  return {
    ...pagePosition,
    pageIndex: Math.min(pageCount - 1, Math.max(0, targetPageIndex)),
    pageCount,
    pageCountSource: pagePosition.pageCountSource || 'page-turn',
  }
}

function passiveCommittedRelocationPosition(pagePosition, detail, reason) {
  if (!pagePosition) return pagePosition
  if (String(reason || '') !== 'relocate-committed') return pagePosition
  if (pagePosition.pageCountSource === 'fixed-layout') return pagePosition
  const currentPageIndex = Number(this.currentPagePosition?.pageIndex)
  const candidatePageIndex = Number(pagePosition.pageIndex)
  const pageCount = readerPageNumberPageCount(pagePosition, this.currentPagePosition?.pageCount)
  if (!Number.isFinite(currentPageIndex) || !Number.isFinite(candidatePageIndex) || !Number.isFinite(pageCount) || pageCount <= 0) {
    return pagePosition
  }
  const currentSectionKey = this.detailSectionKey(this.committedRelocateDetail)
  const candidateSectionKey = this.detailSectionKey(detail)
  const sameSection = Boolean(currentSectionKey && currentSectionKey === candidateSectionKey)
  const recentPageTurnDirection = this.recentPageTurnDirection
  const hasRecentPageTurn = recentPageTurnDirection === 'next' || recentPageTurnDirection === 'previous'
  const canClampAcrossSections = !sameSection && hasRecentPageTurn
  if (!sameSection && !canClampAcrossSections) return pagePosition
  const consumeRecentPageTurn = () => {
    if (hasRecentPageTurn) this.recentPageTurnDirection = null
  }
  if (Math.abs(candidatePageIndex - currentPageIndex) <= 1) {
    consumeRecentPageTurn()
    return pagePosition
  }
  const direction = hasRecentPageTurn
    ? (recentPageTurnDirection === 'previous' ? -1 : 1)
    : (candidatePageIndex > currentPageIndex ? 1 : -1)
  const clampedPageIndex = Math.min(pageCount - 1, Math.max(0, currentPageIndex + direction))
  log(
    'page-number:passive-relocate-clamped',
    `from=${currentPageIndex + 1}`,
    `candidate=${candidatePageIndex + 1}`,
    `to=${clampedPageIndex + 1}`,
    `section=${candidateSectionKey}`
  )
  consumeRecentPageTurn()
  return {
    ...pagePosition,
    pageIndex: Math.min(pageCount - 1, Math.max(0, currentPageIndex + direction)),
    pageCount,
    pageCountSource: pagePosition.pageCountSource || 'relocate-committed',
  }
}

function readerPageNumberFontFamily(settings = this.readerSettings) {
  const configured = readerEffectiveFontFamily(settings)
  const selected = String(settings?.fontFamily || '').trim()
  if (configured) return configured
  if (selected && selected !== 'inherit') return selected
  const visibleContentFont = readerPageNumberVisibleContentFontFamily.call(this)
  if (visibleContentFont) return visibleContentFont
  for (const doc of this.contentDocuments()) {
    const fontFamily = doc?.defaultView?.getComputedStyle?.(doc.body)?.fontFamily
    if (fontFamily && fontFamily !== 'initial') return fontFamily
  }
  return 'Georgia, serif'
}

function readerPageNumberVisibleContentFontFamily() {
  const selectors = [
    'p',
    'blockquote',
    'li',
    '[data-navic-paragraph-block="true"]',
    'article',
    'section',
    'div',
    'span',
  ]
  for (const doc of this.contentDocuments()) {
    for (const selector of selectors) {
      const elements = Array.from(doc?.body?.querySelectorAll?.(selector) || []).slice(0, 240)
      for (const element of elements) {
        const text = String(element?.textContent || '').replace(/\s+/g, ' ').trim()
        if (text.length < 2) continue
        const rect = element.getBoundingClientRect?.()
        if (!rect || rect.width <= 0 || rect.height <= 0) continue
        const style = doc.defaultView?.getComputedStyle?.(element)
        const fontFamily = style?.fontFamily
        if (fontFamily && fontFamily !== 'initial' && fontFamily !== 'inherit') return fontFamily
      }
    }
  }
  return ''
}

function updateReaderPageNumberLayer(pagePosition = this.currentPagePosition) {
  const pageNumberPosition = readerPageNumberPositionWithPageCount(pagePosition, this.currentPagePosition?.pageCount)
  this.currentPagePosition = pageNumberPosition || null
  if (pageNumberPosition?.pageCountSource === 'fixed-layout') {
    this.syncFixedLayoutNavigationPageIndex(pageNumberPosition)
  }
  if (this.shellCoverVisible) {
    this.pageNumberLayer?.remove?.()
    this.pageNumberLayer = null
    return
  }
  const label = readerPageNumberLabel(pageNumberPosition)
  if (!label) {
    this.pageNumberLayer?.remove?.()
    this.pageNumberLayer = null
    return
  }
  this.pageNumberLayer = this.pageNumberLayer && readerRoot.contains(this.pageNumberLayer)
    ? this.pageNumberLayer
    : ensureReaderPageNumberLayer()
  const fontFamily = this.readerPageNumberFontFamily()
  document.documentElement.style.setProperty('--reader-page-number-font-family', fontFamily)
  this.pageNumberLayer.textContent = label
  this.pageNumberLayer.dataset.navicPageNumberTotal = String(pageNumberPosition.pageCount || '')
  setStylesImportant(this.pageNumberLayer, {
    position: 'fixed',
    left: '50%',
    bottom: 'calc(env(safe-area-inset-bottom, 0px) + 18px)',
    transform: 'translateX(-50%)',
    'z-index': '2147483644',
    'pointer-events': 'none',
    'user-select': 'none',
    color: 'color-mix(in srgb, var(--reader-foreground) 58%, transparent)',
    opacity: '0.82',
    'mix-blend-mode': readerPageNumberBlendMode(this.readerSettings),
    'font-family': fontFamily,
    'font-size': '0.82rem',
    'font-style': 'normal',
    'font-weight': '400',
    'font-variant-numeric': 'normal',
    'line-height': '1',
    'letter-spacing': '0',
    background: 'transparent',
    border: '0',
    padding: '0',
    margin: '0',
  })
}

function tryUpdateReaderPageNumberLayer(detail = this.lastRelocateDetail, fallback = this.currentPagePosition, reason = '') {
  try {
    if (String(reason || '') !== 'relocate-committed' && !String(reason || '').startsWith('page-turn:')) {
      this.recentPageTurnDirection = null
      this.pageTurnTargetPageIndex = null
    }
    const candidatePagePosition = (detail ? this.readerPagePosition(detail) : null) || fallback
    const committedPagePosition = this.committedPageTurnPosition(candidatePagePosition, detail, reason)
    const pagePosition = this.passiveCommittedRelocationPosition(committedPagePosition, detail, reason)
    this.updateReaderPageNumberLayer(pagePosition)
    return pagePosition || null
  } catch (error) {
    logError('page-number:update-failed', error?.message || error)
    return fallback || null
  }
}

function scheduleReaderPageNumberRefresh(reason = 'deferred') {
  if (this.pageNumberRefreshScheduled) return
  this.pageNumberRefreshScheduled = true
  requestAnimationFrame(() => {
    this.pageNumberRefreshScheduled = false
    log('page-number:refresh', reason)
    this.tryUpdateReaderPageNumberLayer(this.lastRelocateDetail, this.currentPagePosition)
  })
}

export const NavicReaderPaginationMethods = {
  fixedLayoutPagePosition,
  reflowablePaginatedRawTextPageCount,
  reflowablePaginatedVisualTextPageCount,
  reflowablePaginatedTextPageCount,
  reflowableChapterProgressAnchor,
  reflowableLastVisualRendererPage,
  scrolledRendererViewportSize,
  reflowableScrolledSectionPagePosition,
  reflowableSectionPagePosition,
  reflowableLocationPagePosition,
  readerPageListPosition,
  readerPageListItems,
  readerPageListPageCount,
  reflowableSectionSizes,
  readerPaginationSectionHref,
  readerPaginationSectionTitle,
  readerPaginationContentKey,
  readerPaginationRenderMetadata,
  readerPaginationRenderFingerprint,
  readerPaginationCacheKey,
  readCachedPaginationProfile,
  writeCachedPaginationProfile,
  isCompletePaginationProfile,
  observedChapterKey,
  hydrateObservedChapterPageCountsFromProfile,
  paginationProfileObservedSignature,
  paginationProfileHasObservedCountIncrease,
  postPaginationProfileStatus,
  paginationProfileSectionPageCount,
  buildCompletePaginationProfileInProfilerView,
  ensureCompletePaginationProfile,
  shouldUseFreshPaginationProfile,
  readerBuildPaginationProfileFromSectionPosition,
  readerEnsurePaginationProfile,
  readerPaginationProfilePosition,
  reflowableStableBookPageModel,
  normalizedReflowablePagePosition,
  reflowableWholeBookPagePosition,
  reflowablePagePosition,
  readerPagePosition,
  chapterPagePosition,
  detailSectionKey,
  committedPageTurnPosition,
  passiveCommittedRelocationPosition,
  readerPageNumberFontFamily,
  updateReaderPageNumberLayer,
  tryUpdateReaderPageNumberLayer,
  scheduleReaderPageNumberRefresh
}
