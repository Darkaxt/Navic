// Adapted from Anx Reader: tmp/references/anx-reader/lib/page/book_player/epub_player.dart:627-879
// (callback catalog, including translateText at 864)
// tmp/references/anx-reader/assets/foliate-js/src/view.js:115-194 (relocation)
// :216-327 (link/image taxonomy)
// :335-397 (annotations)

import './vendor/foliate-js/view.js'
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
  readerSideMarginValue,
  readerTopMarginValue,
  readerBottomMarginValue,
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
import { NavicReaderPageTurnMethods } from './navic-reader-page-turns.js'
import { NavicReaderContentInteractionMethods } from './navic-reader-content-interactions.js'
import { NavicReaderPaginationMethods } from './navic-reader-pagination.js'
import { NavicReaderAppearanceMethods } from './navic-reader-appearance.js'

const ReaderRelocationCommitDelayMs = 180
const ViewportScrollStepRatio = 0.75
const ReaderPdfFitWidth = 'width'
const ReaderPdfFitPage = 'page'
const ReaderPdfFitHeight = 'height'
const ReaderPdfFitOriginal = 'original'
const ReaderPdfPageGapMaxPercent = 48
const ReaderPaginationProfileStatusMeasuring = 'measuring'
const ReaderPaginationProfileStatusReady = 'ready'
const ReaderPaginationProfileStatusCached = 'cached'
const ReaderPaginationProfileStatusFailed = 'failed'

const readerRelocationReasonIsExplicit = reason => {
  const normalized = String(reason || '').trim()
  return normalized !== '' && normalized !== 'relocate-committed'
}

const diagnosticNumber = value => {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

const normalizedReaderPdfFitMode = value =>
  [ReaderPdfFitWidth, ReaderPdfFitPage, ReaderPdfFitHeight, ReaderPdfFitOriginal].includes(value)
    ? value
    : ReaderPdfFitWidth

const readerPdfZoomAttribute = value => {
  switch (normalizedReaderPdfFitMode(value)) {
    case ReaderPdfFitPage:
      return 'fit-page'
    case ReaderPdfFitHeight:
      return 'fit-height'
    case ReaderPdfFitOriginal:
      return '1'
    case ReaderPdfFitWidth:
    default:
      return 'fit-width'
  }
}

const normalizedReaderPdfPageGapPercent = value => {
  const gap = Number.parseInt(value, 10)
  if (!Number.isFinite(gap)) return 0
  return Math.min(ReaderPdfPageGapMaxPercent, Math.max(0, gap))
}

class NavicReaderRuntime {
  view = null
  mediaOverlayEnabled = false
  readerSettings = {}
  readerTapZoneMode = ReaderTapZoneDefault
  readerFlowModeValue = ReaderFlowPaged
  readerDirectionModeValue = ReaderDirectionDefault
  smallerTapZone = false
  nativeTapZones = false
  originalBookDir = null
  publicationUrl = ''
  surfaceTextureLayer = null
  surfaceBorderOverlayLayer = null
  surfaceTextureVariant = null
  surfaceBorderOverlayVariant = null
  surfacePaperTextureBaseOffset = 0
  surfaceTextureScrollOffset = { x: 0, y: 0 }
  surfacePaperTextureScrollRenderer = null
  surfacePaperTextureScrollListener = null
  surfacePaperTextureTurnDirection = null
  tapZoneOverlayLayer = null
  pageNumberLayer = null
  shellCoverLayer = null
  pageDragPreviewLayer = null
  pageDragPreviewFrame = null
  pageDragPreviewTargetKey = ''
  pageDragPreviewReadyKey = ''
  pageDragPreviewLoadToken = 0
  shellCoverBlobUrl = null
  shellCoverVisible = false
  externalShellCover = false
  shellCoverHideTimer = null
  pageNumberRefreshScheduled = false
  currentPagePosition = null
  paginationProfile = null
  paginationFingerprint = null
  paginationProfileTaskToken = 0
  paginationProfileMeasurementInProgress = false
  observedChapterPageCounts = new Map()
  committedRelocateDetail = null
  lastPostedLocationKey = null
  pendingRelocateDetail = null
  pendingRelocateReason = 'relocate-committed'
  controlledRelocateReason = null
  controlledRelocateStartSequence = 0
  relocateSequence = 0
  relocatePostScheduled = false
  relocatePostTimer = null
  reflowableBookPageModel = null
  reflowablePageIndexOffset = null
  reflowableLastLocationSignature = null
  reflowableLastLocationPageIndex = null
  reflowableLastLocationSectionIndex = null
  reflowableLastLocationProgressBucket = null
  reflowableLastLocationProgress = null
  lastRelocateDetail = null
  pageTurnPromise = null
  pageTurnQueue = []
  pageTurnInProgress = false
  pageTurnDirection = null
  recentPageTurnDirection = null
  nativePageDragPreview = null
  fixedLayoutNavigationPageIndex = null
  fixedLayoutNavigationDirection = null
  suppressedCoverSectionIndexes = new Set()
  embeddedCoverSuppressedSectionIndexes = new Set()
  embeddedCoverRerenderScheduled = false
  recentContentActionTouch = null
  viewportResizeListener = () => this.applyReaderViewportLayout('resize')

  constructor() {
    window.visualViewport?.addEventListener('resize', this.viewportResizeListener)
    window.addEventListener('resize', this.viewportResizeListener)
    requestAnimationFrame(() => this.applyReaderViewportLayout('startup'))
  }

  dispatch(command) {
    log('dispatch', command?.type || 'invalid')
    readerTrace('dispatch', { type: command?.type || 'invalid' })
    if (!command || typeof command !== 'object') return
    switch (command.type) {
      case 'openPublication':
        return this.openPublication(command)
      case 'goToCfi':
        return this.goTo(command.cfi)
      case 'goToHref':
        return this.goTo(command.href)
      case 'goToProgress':
        return this.goToProgress(command.progress)
      case 'diagnosticLocationSnapshot':
        return this.postCurrentLocationSnapshot(command.reason || 'diagnostic-snapshot', {
          forceDuplicatePost: true,
        })
      case 'diagnosticScrolledEdgePullUp':
        return this.diagnosticScrolledEdgePullUp()
      case 'goToChapterProgress':
        return this.goToChapterProgress(command.href, command.progress)
      case 'nextPage':
        readerTrace('dispatch:nextPage', {
          hasPromise: Boolean(this.pageTurnPromise),
          queueLength: this.pageTurnQueue.length,
        })
        return this.nextPage()
      case 'previousPage':
        readerTrace('dispatch:previousPage', {
          hasPromise: Boolean(this.pageTurnPromise),
          queueLength: this.pageTurnQueue.length,
        })
        return this.previousPage()
      case 'historyBack':
        return this.view?.history?.back?.()
      case 'historyForward':
        return this.view?.history?.forward?.()
      case 'scrollViewport':
        return this.scrollViewport(command.direction)
      case 'previewPageDrag':
        return this.previewPageDrag(command)
      case 'contentLongPressAt':
        return this.handleNativeTapZoneContentLongPressAt(command.x, command.y, command.viewWidth, command.viewHeight, 'native-long-press-command')
      case 'applyHighlight':
        return this.applyHighlight(command)
      case 'applyHighlights':
        return this.applyHighlights(command.highlights || [])
      case 'applyOverlayFragment':
        return this.applyOverlayFragment(command.fragment || command)
      case 'clearOverlay':
        return this.clearOverlay()
      case 'applySettings':
        return this.applySettings(command.settings || {})
      case 'search':
        return this.search(command.query)
      case 'clearSearch':
        return this.clearSearch()
      default:
        post({ type: 'error', code: 'unknown_command', message: `Unknown reader command: ${command.type}` })
    }
  }

  async openPublication({ url, mediaOverlayEnabled = false, externalShellCover = false, startLocator = null, settings = null }) {
    if (!url) {
      logError('openPublication:missing-url')
      post({ type: 'error', code: 'missing_url', message: 'Reader publication URL is required.' })
      return
    }
    this.mediaOverlayEnabled = Boolean(mediaOverlayEnabled)
    log('openPublication:start', describeUrl(url), `overlay=${this.mediaOverlayEnabled}`)
    try {
      this.close()
      this.externalShellCover = Boolean(externalShellCover)
      this.publicationUrl = url
      this.lastRelocateDetail = null
      if (settings) this.readerSettings = settings
      this.applyReaderViewportLayout('before-open')
      this.view = document.createElement('foliate-view')
      this.view.addEventListener('relocate', event => this.onRelocate(event.detail || {}))
      this.view.addEventListener('load', event => this.onLoad(event.detail || {}))
      this.view.addEventListener('external-link', event => this.onExternalLink(event))
      this.view.addEventListener('link', event => this.onInternalLink(event))
      this.view.addEventListener('draw-annotation', event => this.onAnnotationDrawn(event.detail || {}))
      this.view.addEventListener('show-annotation', event => this.onAnnotationClick(event.detail || {}))
      this.view.addEventListener('create-overlay', event => this.onOverlayCreated(event.detail || {}))
      this.view.history?.addEventListener?.('index-change', () => this.postNavigationState('history-index-change'))
      readerRoot.replaceChildren(this.view)
      if (settings) this.applySettings(settings)
      this.applyReaderViewportLayout('view-created')
      await this.view.open(url)
      this.postNavigationState('view-opened')
      this.attachSurfacePaperTextureScrollSync()
      this.applyReaderViewportLayout('view-opened')
      log('openPublication:view-opened', describeUrl(url))
      if (settings) this.applySettings(settings)
      const shellCoverUrl = this.externalShellCover ? null : await this.loadShellCover()
      const hasShellCoverSurface = this.externalShellCover || Boolean(shellCoverUrl)
      const shouldStartAtShellCover = hasShellCoverSurface && this.startLocatorTargetsShellCover(startLocator)
      this.postToc()
      const locator = startLocator?.cfi || startLocator?.href
      const progress = Number(startLocator?.progress)
      if (shouldStartAtShellCover) {
        await this.goToFirstReadableContent()
      } else if (locator) {
        await this.view.goTo(locator)
      } else if (Number.isFinite(progress)) {
        await this.goToProgress(progress)
      } else if (hasShellCoverSurface) {
        await this.goToFirstReadableContent()
      } else {
        await this.view.init?.({ showTextStart: true })
      }
      await this.ensureCompletePaginationProfile(url, this.readerSettings)
      this.attachSurfaceTapGesture(this.view)
      this.attachReaderTapZoneGesture(this.view)
      if (shellCoverUrl) this.showShellCover()
      log('openPublication:ready', describeUrl(url))
      this.applyReaderViewportLayout('ready')
      this.logContentLayout('ready')
      post({ type: 'ready' })
      post({ type: 'publicationReady' })
      if (readerStartLocatorHasPosition(startLocator) && !shouldStartAtShellCover) {
        requestAnimationFrame(() => this.postCurrentLocationSnapshot('initial-resume'))
      }
    } catch (error) {
      reportError(error, 'open_failed')
    }
  }

  onInternalLink(event) {
    const href = event?.detail?.href || ''
    if (this.nativeTapZones === true) {
      event.preventDefault()
      post({
        type: 'internalLink',
        href,
        prevented: true,
        source: 'native-short-tap',
      })
      log('link:prevented-native-short-tap', describeUrl(href))
      readerTrace('link:prevented-native-short-tap', { href })
      return
    }
    post({
      type: 'internalLink',
      href,
      prevented: false,
      source: 'foliate-link',
    })
  }

  onExternalLink(event) {
    const href = event?.detail?.href || ''
    const anchorHref = event?.detail?.a?.getAttribute?.('href') || ''
    event?.preventDefault?.()
    post({
      type: 'externalLink',
      href,
      anchorHref,
    })
    log('external-link:prevented', describeUrl(href))
    readerTrace('external-link:prevented', { href, anchorHref })
  }

  annotationRangeCfi(index, range) {
    const numericIndex = Number(index)
    if (!Number.isFinite(numericIndex) || !range) return undefined
    try {
      return this.view?.getCFI?.(Math.floor(numericIndex), range)
    } catch (error) {
      log('annotation:cfi-failed', error?.message || String(error))
      return undefined
    }
  }

  onAnnotationClick(detail = {}) {
    const index = Number(detail.index)
    post({
      type: 'annotationClick',
      value: detail.value || detail.annotation?.value || '',
      index: Number.isFinite(index) ? Math.floor(index) : undefined,
      rangeCfi: this.annotationRangeCfi(index, detail.range),
    })
  }

  onAnnotationDrawn(detail = {}) {
    const index = Number(detail.index)
    post({
      type: 'annotationDrawn',
      value: detail.value || detail.annotation?.value || '',
      index: Number.isFinite(index) ? Math.floor(index) : undefined,
      rangeCfi: this.annotationRangeCfi(index, detail.range),
    })
  }

  onOverlayCreated(detail = {}) {
    const index = Number(detail.index)
    post({
      type: 'overlayCreated',
      index: Number.isFinite(index) ? Math.floor(index) : undefined,
    })
  }

  postNavigationState(source = 'unknown') {
    const history = this.view?.history
    if (!history) return
    post({
      type: 'pushState',
      canGoBack: history.canGoBack === true,
      canGoForward: history.canGoForward === true,
    })
    readerTrace('navigation-state', {
      source,
      canGoBack: history.canGoBack === true,
      canGoForward: history.canGoForward === true,
    })
  }

  close() {
    this.clearOverlay()
    this.clearShellCover()
    this.detachSurfacePaperTextureScrollSync()
    this.view?.close?.()
    this.view?.remove?.()
    this.view = null
    this.readerSettings = {}
    this.nativeTapZones = false
    this.originalBookDir = null
    this.publicationUrl = ''
    this.externalShellCover = false
    this.pageTurnPromise = null
    this.pageTurnQueue = []
    this.pageTurnInProgress = false
    this.pageTurnDirection = null
    this.recentPageTurnDirection = null
    this.nativePageDragPreview = null
    this.removePageDragPreviewLayer()
    this.fixedLayoutNavigationPageIndex = null
    this.fixedLayoutNavigationDirection = null
    this.suppressedCoverSectionIndexes = new Set()
    this.embeddedCoverSuppressedSectionIndexes = new Set()
    this.embeddedCoverRerenderScheduled = false
    this.recentContentActionTouch = null
    this.readerDirectionModeValue = ReaderDirectionDefault
    this.surfaceTextureLayer?.remove?.()
    this.surfaceTextureLayer = null
    this.surfaceBorderOverlayLayer?.remove?.()
    this.surfaceBorderOverlayLayer = null
    this.tapZoneOverlayLayer?.remove?.()
    this.tapZoneOverlayLayer = null
    this.pageNumberLayer?.remove?.()
    this.pageNumberLayer = null
    this.currentPagePosition = null
    this.paginationProfile = null
    this.paginationFingerprint = null
    this.paginationProfileTaskToken += 1
    this.paginationProfileMeasurementInProgress = false
    this.observedChapterPageCounts = new Map()
    this.committedRelocateDetail = null
    this.lastPostedLocationKey = null
    this.pendingRelocateDetail = null
    this.pendingRelocateReason = 'relocate-committed'
    this.relocatePostScheduled = false
    if (this.relocatePostTimer != null) {
      clearTimeout(this.relocatePostTimer)
      this.relocatePostTimer = null
    }
    this.reflowableBookPageModel = null
    this.reflowablePageIndexOffset = null
    this.reflowableLastLocationSignature = null
    this.reflowableLastLocationPageIndex = null
    this.reflowableLastLocationSectionIndex = null
    this.reflowableLastLocationProgressBucket = null
    this.reflowableLastLocationProgress = null
    this.surfaceTextureVariant = null
    this.surfaceBorderOverlayVariant = null
    this.surfacePaperTextureBaseOffset = 0
    this.surfaceTextureScrollOffset = { x: 0, y: 0 }
    this.surfacePaperTextureTurnDirection = null
    this.lastRelocateDetail = null
  }

  clearShellCover({ revoke = true } = {}) {
    if (this.shellCoverHideTimer) {
      clearTimeout(this.shellCoverHideTimer)
      this.shellCoverHideTimer = null
    }
    this.shellCoverVisible = false
    this.shellCoverLayer?.remove?.()
    this.shellCoverLayer = null
    delete readerRoot.dataset.navicShellCoverVisible
    if (revoke && this.shellCoverBlobUrl) {
      URL.revokeObjectURL(this.shellCoverBlobUrl)
      this.shellCoverBlobUrl = null
    }
  }

  async loadShellCover() {
    if (this.shellCoverBlobUrl) {
      URL.revokeObjectURL(this.shellCoverBlobUrl)
      this.shellCoverBlobUrl = null
    }
    try {
      const book = this.view?.book
      const blob = await book.getCover?.()
      if (!blob) {
        log('shell-cover:missing')
        return null
      }
      this.shellCoverBlobUrl = URL.createObjectURL(blob)
      log('shell-cover:loaded', blob.type || 'blob', blob.size || 0)
      return this.shellCoverBlobUrl
    } catch (error) {
      logError('shell-cover:load-failed', error?.message || error)
      return null
    }
  }

  firstReadableContentTarget() {
    const sections = Array.from(this.view?.book?.sections || [])
    if (!sections.length) return null
    const firstNonCover = sections.findIndex((section, index) =>
      readerSectionIsReadable(section) && !this.sectionTargetsCover(section, index)
    )
    if (firstNonCover >= 0) return firstNonCover
    const firstReadable = sections.findIndex(readerSectionIsReadable)
    return firstReadable >= 0 ? firstReadable : 0
  }

  coverSectionEntries() {
    return Array.from(this.view?.book?.sections || [])
      .map((section, index) => ({ section, index }))
      .filter(({ section, index }) => this.sectionTargetsCover(section, index))
  }

  hasNonCoverReadableContent() {
    return Array.from(this.view?.book?.sections || []).some((section, index) =>
      readerSectionIsReadable(section) && !this.sectionTargetsCover(section, index)
    )
  }

  sectionTargetsCover(section, index) {
    return readerSectionLooksLikeCover(section, index) || this.suppressedCoverSectionIndexes.has(index)
  }

  startLocatorTargetsShellCover(startLocator) {
    if (!readerStartLocatorHasPosition(startLocator)) return false
    const coverSections = this.coverSectionEntries()
    if (!coverSections.length || !this.hasNonCoverReadableContent()) return false
    const href = startLocator?.href
    if (href && coverSections.some(({ section }) => readerHrefMatchesSection(href, section))) {
      log('shell-cover:start-locator-cover', `href=${href}`)
      return true
    }
    const cfi = String(startLocator?.cfi || '')
    if (/cover/i.test(cfi)) {
      log('shell-cover:start-locator-cover', 'cfi-token')
      return true
    }
    const progress = Number(startLocator?.progress)
    const firstCoverIndex = Math.min(...coverSections.map(({ index }) => index))
    const firstReadableIndex = Number(this.firstReadableContentTarget())
    if (
      Number.isFinite(progress) &&
      progress >= 0 &&
      progress <= ReaderShellCoverProgressThreshold &&
      firstCoverIndex === 0 &&
      Number.isFinite(firstReadableIndex) &&
      firstReadableIndex > firstCoverIndex
    ) {
      log('shell-cover:start-locator-cover', `progress=${progress}`)
      return true
    }
    return false
  }

  detailTargetsCover(detail) {
    const coverSections = this.coverSectionEntries()
    if (!coverSections.length) return false
    const index = Number(detail?.section?.current ?? detail?.index)
    if (Number.isFinite(index)) {
      const section = this.view?.book?.sections?.[Math.floor(index)]
      return this.sectionTargetsCover(section, Math.floor(index))
    }
    const href = detail?.href || detail?.tocItem?.href || detail?.section?.href
    return Boolean(href && coverSections.some(({ section }) => readerHrefMatchesSection(href, section)))
  }

  sectionHrefForDetail(detail) {
    const index = Number(detail?.section?.current ?? detail?.index)
    if (!Number.isFinite(index)) return ''
    const section = this.view?.book?.sections?.[Math.floor(index)]
    return section?.href || section?.id || section?.url || section?.name || ''
  }

  async goToFirstReadableContent() {
    const target = this.firstReadableContentTarget()
    if (target == null) {
      await this.view?.init?.({ showTextStart: true })
      return
    }
    log('shell-cover:first-readable', target)
    await this.view?.goTo?.(target)
  }

  showShellCover({ animate = true } = {}) {
    if (!this.shellCoverBlobUrl) return false
    if (this.shellCoverHideTimer) {
      clearTimeout(this.shellCoverHideTimer)
      this.shellCoverHideTimer = null
    }
    this.shellCoverVisible = true
    readerRoot.dataset.navicShellCoverVisible = 'true'
    this.pageNumberLayer?.remove?.()
    this.pageNumberLayer = null
    this.shellCoverLayer = this.shellCoverLayer && readerRoot.contains(this.shellCoverLayer)
      ? this.shellCoverLayer
      : ensureReaderShellCoverLayer()
    updateReaderShellCoverLayer(
      this.shellCoverLayer,
      this.shellCoverBlobUrl,
      this.readerSettings,
      this.view?.book?.metadata?.title || ''
    )
    this.attachSurfaceTapGesture(this.shellCoverLayer)
    this.shellCoverLayer.dataset.navicShellCoverState = animate ? 'entering' : 'visible'
    if (animate) {
      setStylesImportant(this.shellCoverLayer, {
        opacity: '0',
        transform: 'translateX(4%) scale(0.985)',
      })
      requestAnimationFrame(() => {
        if (!this.shellCoverVisible || !this.shellCoverLayer) return
        this.shellCoverLayer.dataset.navicShellCoverState = 'visible'
        setStylesImportant(this.shellCoverLayer, {
          opacity: '1',
          transform: 'translateX(0) scale(1)',
          'pointer-events': 'auto',
        })
      })
    }
    log('shell-cover:show', animate ? 'animated' : 'static')
    return true
  }

  hideShellCover({ animate = true } = {}) {
    if (!this.shellCoverVisible && !this.shellCoverLayer) return false
    this.shellCoverVisible = false
    delete readerRoot.dataset.navicShellCoverVisible
    const layer = this.shellCoverLayer
    const finish = () => {
      if (this.shellCoverVisible || this.shellCoverLayer !== layer) return
      layer?.remove?.()
      this.shellCoverLayer = null
      this.updateReaderPageNumberLayer()
    }
    if (layer && animate) {
      layer.dataset.navicShellCoverState = 'exiting'
      setStylesImportant(layer, {
        opacity: '0',
        transform: 'translateX(-8%) scale(1.018)',
        'pointer-events': 'none',
      })
      this.shellCoverHideTimer = setTimeout(() => {
        this.shellCoverHideTimer = null
        finish()
      }, ReaderShellCoverTransitionMs + 40)
    } else {
      finish()
    }
    log('shell-cover:hide', animate ? 'animated' : 'static')
    return true
  }

  canReturnToShellCover() {
    if (!this.shellCoverBlobUrl || this.shellCoverVisible) return false
    const pageIndex = Number(this.currentPagePosition?.pageIndex)
    if (Number.isFinite(pageIndex)) return pageIndex <= 0
    const sectionIndex = Number(this.lastRelocateDetail?.section?.current ?? this.lastRelocateDetail?.index)
    const firstContent = Number(this.firstReadableContentTarget())
    if (
      Number.isFinite(sectionIndex) &&
      Number.isFinite(firstContent) &&
      Math.floor(sectionIndex) <= firstContent
    ) {
      return true
    }
    return false
  }

  applyReaderViewportLayout(label = 'unknown') {
    const { width, height } = readerViewportSize()
    const widthPx = `${width}px`
    const heightPx = `${height}px`
    setStylesImportant(document.documentElement, {
      width: '100%',
      height: heightPx,
      'min-height': heightPx,
      margin: '0px',
      overflow: 'hidden',
    })
    setStylesImportant(document.body, {
      position: 'fixed',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      margin: '0px',
      overflow: 'hidden',
    })
    setStylesImportant(this.view, {
      position: 'fixed',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      overflow: 'hidden',
    })
    const renderer = this.view?.renderer
    const fixedLayout = this.view?.isFixedLayout === true || renderer?.localName === 'foliate-fxl'
    const pageBox = readerAdaptiveFoliatePageBox({ width, height }, this.readerSettings)
    setStylesImportant(renderer, {
      position: 'absolute',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      overflow: fixedLayout ? 'auto' : 'hidden',
    })
    if (renderer && !fixedLayout) {
      renderer.setAttribute('max-inline-size', pageBox.maxInlineSize)
      renderer.setAttribute('max-block-size', pageBox.maxBlockSize)
      renderer.setAttribute('max-column-count', pageBox.maxColumnCount)
      renderer.setAttribute('column-threshold', pageBox.columnThreshold)
      renderer.setAttribute('top-margin', `${readerTopMarginValue(this.readerSettings)}px`)
      renderer.setAttribute('bottom-margin', `${readerBottomMarginValue(this.readerSettings)}px`)
      renderer.setAttribute('gap', `${readerSideMarginValue(this.readerSettings)}%`)
      renderer.dataset.navicAdaptivePageBox = JSON.stringify(pageBox)
    }
    this.applyPdfImageSettings(this.readerSettings)
    if (renderer) requestAnimationFrame(() => renderer?.render?.())
    if (this.shellCoverVisible && this.shellCoverLayer && this.shellCoverBlobUrl) {
      updateReaderShellCoverLayer(
        this.shellCoverLayer,
        this.shellCoverBlobUrl,
        this.readerSettings,
        this.view?.book?.metadata?.title || ''
      )
    }
    this.renderSurfacePaperTextureLayers()
    this.renderTapZoneOverlayLayer()
    this.preloadPageDragPreviewTargets?.(`viewport-layout:${label}`)
    log('viewport-layout', `label=${label}`, `${width}x${height}`)
  }

  applyReaderViewportLayoutToProfilerView(profileView, settings = this.readerSettings) {
    if (!profileView) return
    const { width, height } = readerViewportSize()
    const widthPx = `${width}px`
    const heightPx = `${height}px`
    setStylesImportant(profileView, {
      position: 'fixed',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      overflow: 'hidden',
      visibility: 'hidden',
      opacity: '0',
      'pointer-events': 'none',
      'z-index': '-1',
    })
    const renderer = profileView?.renderer
    const pageBox = readerAdaptiveFoliatePageBox({ width, height }, settings)
    setStylesImportant(renderer, {
      position: 'absolute',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      overflow: 'hidden',
    })
    if (renderer) {
      renderer.setAttribute('max-inline-size', pageBox.maxInlineSize)
      renderer.setAttribute('max-block-size', pageBox.maxBlockSize)
      renderer.setAttribute('max-column-count', pageBox.maxColumnCount)
      renderer.setAttribute('column-threshold', pageBox.columnThreshold)
      renderer.setAttribute('top-margin', `${readerTopMarginValue(settings)}px`)
      renderer.setAttribute('bottom-margin', `${readerBottomMarginValue(settings)}px`)
      renderer.setAttribute('gap', `${readerSideMarginValue(settings)}%`)
      renderer.dataset.navicAdaptivePageBox = JSON.stringify(pageBox)
    }
    renderer?.setAttribute?.('flow', readerFoliateFlow(readerFlowMode(settings)))
    renderer?.render?.()
  }

  applyPdfImageSettings(settings = this.readerSettings) {
    const renderer = this.view?.renderer
    if (!renderer || this.view?.isFixedLayout !== true) return
    const fitMode = normalizedReaderPdfFitMode(settings?.pdfFitMode)
    const cropBorders = settings?.pdfCropBorders === true
    const gapPercent = normalizedReaderPdfPageGapPercent(settings?.pdfPageGapPercent)
    const viewport = readerViewportSize()
    const gapPx = Math.round(Math.max(1, viewport.height || 0) * gapPercent / 100)
    renderer.setAttribute('zoom', readerPdfZoomAttribute(fitMode))
    renderer.setAttribute('page-gap', String(gapPx))
    if (cropBorders) renderer.setAttribute('crop-borders', 'true')
    else renderer.removeAttribute('crop-borders')
    renderer.setAttribute('data-navic-pdf-fit-mode', fitMode)
    renderer.setAttribute('data-navic-pdf-crop-borders', cropBorders ? 'true' : 'false')
    renderer.setAttribute('data-navic-pdf-page-gap-percent', String(gapPercent))
    renderer.setAttribute('data-navic-pdf-page-gap-px', String(gapPx))
    renderer.style.setProperty('--reader-pdf-page-gap', `${gapPx}px`)
    renderer.style.setProperty('--reader-pdf-crop-scale', cropBorders ? '1.045' : '1')
    readerTrace('pdf-settings:apply', {
      fitMode,
      zoom: renderer.getAttribute('zoom'),
      cropBorders,
      gapPercent,
      gapPx,
    })
  }

  async resolveReaderNavigationTarget(locator) {
    if (!this.view || locator == null) return null
    const target = typeof locator === 'string' ? locator.trim() : locator
    if (typeof target === 'string' && !target) return null
    let resolved = null
    try {
      resolved = await Promise.resolve(
        this.view.resolveNavigation?.(target) ||
        this.view.book?.resolveHref?.(target)
      )
    } catch (error) {
      log('go-to:resolve-failed', error?.message || String(error))
    }
    const index = Number(resolved?.index)
    const rendererTarget = Number.isFinite(index)
      ? { index: Math.floor(index) }
      : null
    if (rendererTarget && resolved?.anchor !== undefined) rendererTarget.anchor = resolved.anchor
    if (rendererTarget && resolved?.select !== undefined) rendererTarget.select = resolved.select
    return {
      target,
      resolved,
      rendererTarget,
    }
  }

  async goTo(locator) {
    if (!this.view || !locator) return
    try {
      const navigationTarget = await this.resolveReaderNavigationTarget(locator)
      if (!navigationTarget) return
      if (navigationTarget.rendererTarget && this.view.renderer?.goTo) {
        log('go-to:resolved', navigationTarget.target, `index=${navigationTarget.rendererTarget.index}`)
        this.beginControlledRelocation('go-to')
        await this.view.renderer.goTo(navigationTarget.rendererTarget)
        this.view.history?.pushState?.(navigationTarget.target)
      } else {
        log('go-to:fallback', navigationTarget.target)
        this.beginControlledRelocation('go-to')
        await this.view.goTo(navigationTarget.target)
      }
      this.scheduleControlledRelocationFallback('go-to')
      this.applyReaderViewportLayout('go-to')
      requestAnimationFrame(() => this.logContentLayout('go-to'))
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }



















































  async applyHighlight({ id, cfi, color = null, note = null }) {
    if (!this.view || !id || !cfi) return
    try {
      await this.view.addAnnotation?.({ id, value: cfi, color, note }, false)
    } catch (error) {
      reportError(error, 'highlight_failed')
    }
  }

  async applyHighlights(highlights) {
    for (const highlight of highlights || []) {
      await this.applyHighlight(highlight)
    }
  }

  async applyOverlayFragment(fragment) {
    if (!this.view || !fragment) return
    const targetHref = fragment.textHref && fragment.fragmentId
      ? `${fragment.textHref}#${fragment.fragmentId}`
      : fragment.textHref
    if (targetHref) {
      await this.goTo(targetHref)
    }
    this.clearOverlay()
    if (fragment.fragmentId) {
      for (const doc of this.contentDocuments()) {
        const element = doc.getElementById(fragment.fragmentId)
        if (element) element.classList.add(overlayClass)
      }
    }
    post({ type: 'overlayFragmentActive', ...fragment })
  }

  clearOverlay() {
    let removedAny = false
    for (const doc of this.contentDocuments()) {
      for (const element of doc.querySelectorAll(`.${overlayClass}`)) {
        element.classList.remove(overlayClass)
        removedAny = true
      }
    }
    if (removedAny) post({ type: 'footnoteClose' })
  }



























































  async search(query) {
    if (!this.view || !query) return
    try {
      const results = []
      for await (const result of this.view.search?.({ query }) || []) {
        results.push(...normalizeSearchResult(result, results.length, this.view))
      }
      post({ type: 'searchResults', query, results })
    } catch (error) {
      reportError(error, 'search_failed')
    }
  }

  clearSearch() {
    try {
      this.view?.clearSearch?.()
      post({ type: 'searchResults', query: '', results: [] })
    } catch (error) {
      reportError(error, 'clear_search_failed')
    }
  }

  postToc() {
    const items = flattenTocItems(this.view?.book?.toc || [])
    post({ type: 'toc', items })
  }

  currentFixedLayoutLocationDetail() {
    if (this.view?.isFixedLayout !== true) return null
    const index = this.fixedLayoutCurrentPageIndex()
    const pageCount = Number(this.view?.book?.sections?.length)
    if (!Number.isFinite(index) || !Number.isFinite(pageCount) || pageCount <= 0) return null
    const section = this.view?.book?.sections?.[index]
    return {
      index,
      href: section?.href || section?.id,
      fraction: pageCount <= 1 ? 0 : index / (pageCount - 1),
    }
  }

  postCurrentLocationSnapshot(reason = 'snapshot', options = {}) {
    const detail = this.lastRelocateDetail || this.currentFixedLayoutLocationDetail()
    if (!detail) {
      log('location-snapshot:missing', reason)
      return { posted: false, skipped: 'missing-location', reason }
    }
    log('location-snapshot', reason)
    return this.postLocationChanged(detail, reason, options)
  }

  postLocationChanged(detail, reason = 'relocate', options = {}) {
    this.removePageDragPreviewLayer()
    if (this.detailTargetsCover(detail) && this.hasNonCoverReadableContent()) {
      this.updateReaderPageNumberLayer(null)
      log('location-changed:cover-skipped', reason)
      readerTrace('location:cover-skipped', { reason, detail })
      return { posted: false, skipped: 'cover', reason }
    }
    const sectionHref = this.sectionHrefForDetail(detail)
    const rawTocItem = detail.tocItem || {}
    const tocItem = sectionHref && rawTocItem.href && !readerHrefMatches(sectionHref, rawTocItem.href)
      ? {}
      : rawTocItem
    const pagePosition = this.tryUpdateReaderPageNumberLayer(detail, this.currentPagePosition, reason)
    const chapterPosition = this.chapterPagePosition(detail, pagePosition)
    const pageModelDiagnostics = {
      pageCountSource: pagePosition?.pageCountSource || null,
      paginationFingerprint: this.paginationFingerprint || null,
      paginationProfilePageCount: diagnosticNumber(this.paginationProfile?.pageCount),
      paginationProfileObservedChapterCount: diagnosticNumber(this.paginationProfile?.observedChapterCount),
      paginationProfileEstimatedChapterCount: diagnosticNumber(this.paginationProfile?.estimatedChapterCount),
      rawLocationCurrent: diagnosticNumber(detail.location?.current),
      rawLocationTotal: diagnosticNumber(detail.location?.total),
    }
    const message = {
      type: 'locationChanged',
      href: detail.href || sectionHref || tocItem.href,
      cfi: detail.cfi,
      progress: optionalNumber(detail.fraction ?? detail.progress ?? detail.totalProgress),
      pageIndex: pagePosition?.pageIndex,
      pageCount: pagePosition?.pageCount,
      chapterProgress: chapterPosition?.progress,
      chapterPageIndex: chapterPosition?.pageIndex,
      chapterPageCount: chapterPosition?.pageCount,
      tocTitle: tocItem.label || tocItem.title,
      rangeCfi: detail.cfi || null,
      reason: reason || null,
      fraction: optionalNumber(detail.fraction),
      size: optionalNumber(detail.size),
      tocItemLabel: tocItem.label || tocItem.title || null,
      pageItemLabel: detail.pageItem?.label || detail.pageItem?.text || null,
      ...pageModelDiagnostics,
    }
    const locationKey = readerLocationPostKey(message)
    if (locationKey === this.lastPostedLocationKey && !options.forceDuplicatePost) {
      log('location-changed:duplicate-skipped', reason)
      readerTrace('location:duplicate-skipped', { reason, message })
      return { posted: false, skipped: 'duplicate', reason }
    }
    this.updateSurfacePaperTexture(detail, pagePosition)
    this.committedRelocateDetail = detail
    this.lastPostedLocationKey = locationKey
    readerTrace('location:page-model', {
      reason,
      href: message.href,
      pageIndex: message.pageIndex,
      pageCount: message.pageCount,
      chapterPageIndex: message.chapterPageIndex,
      chapterPageCount: message.chapterPageCount,
      ...pageModelDiagnostics,
    })
    log('location-page-model',
      `reason=${reason}`,
      `source=${pageModelDiagnostics.pageCountSource || 'none'}`,
      `page=${message.pageIndex ?? 'n/a'}/${message.pageCount ?? 'n/a'}`,
      `chapter=${message.chapterPageIndex ?? 'n/a'}/${message.chapterPageCount ?? 'n/a'}`,
      `profile=${pageModelDiagnostics.paginationProfilePageCount ?? 'n/a'}`,
      `observed=${pageModelDiagnostics.paginationProfileObservedChapterCount ?? 'n/a'}`,
      `raw=${pageModelDiagnostics.rawLocationCurrent ?? 'n/a'}/${pageModelDiagnostics.rawLocationTotal ?? 'n/a'}`,
      `fingerprint=${pageModelDiagnostics.paginationFingerprint || 'none'}`,
      `href=${message.href || 'none'}`
    )
    readerTrace('location:post', { reason, message })
    post(message)
    log('location-changed:posted', reason)
    if (detail.cfi) post({ type: 'cfiChanged', cfi: detail.cfi })
    if (tocItem.href || tocItem.label || tocItem.title) {
      post({ type: 'tocItemChanged', href: tocItem.href, title: tocItem.label || tocItem.title })
    }
    return { posted: true, reason, href: message.href || null, pageIndex: message.pageIndex ?? null, message }
  }

  beginControlledRelocation(reason) {
    this.controlledRelocateReason = reason || null
    this.controlledRelocateStartSequence = this.relocateSequence
    log('controlled-relocate:begin', this.controlledRelocateReason || 'none', `seq=${this.controlledRelocateStartSequence}`)
  }

  consumeControlledRelocationReason(fallback = 'relocate-committed') {
    const reason = this.controlledRelocateReason || fallback
    log(
      'controlled-relocate:consume',
      reason,
      `fallback=${fallback}`,
      `stored=${this.controlledRelocateReason || 'none'}`,
      `seq=${this.relocateSequence}`,
      `start=${this.controlledRelocateStartSequence}`
    )
    this.controlledRelocateReason = null
    return reason
  }

  scheduleControlledRelocationFallback(reason) {
    const startSequence = this.controlledRelocateStartSequence
    log('controlled-relocate:fallback-scheduled', reason, `start=${startSequence}`)
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        if (this.controlledRelocateReason !== reason) {
          log('controlled-relocate:fallback-skipped', reason, `stored=${this.controlledRelocateReason || 'none'}`)
          return
        }
        if (this.relocateSequence > startSequence) {
          log('controlled-relocate:fallback-skipped', reason, `seq=${this.relocateSequence}`, `start=${startSequence}`)
          return
        }
        log('controlled-relocate:fallback-commit', reason, `seq=${this.relocateSequence}`, `start=${startSequence}`)
        this.scheduleCommittedRelocation(this.lastRelocateDetail, this.consumeControlledRelocationReason(reason))
      })
    })
  }

  onRelocate(detail) {
    readerTrace('relocate:raw', detail)
    this.lastRelocateDetail = detail
    this.relocateSequence += 1
    if (this.pageTurnInProgress || this.pageTurnPromise) return
    this.scheduleCommittedRelocation(detail, this.consumeControlledRelocationReason('relocate-committed'))
  }

  cancelPendingCommittedRelocation() {
    this.pendingRelocateDetail = null
    this.pendingRelocateReason = 'relocate-committed'
    this.controlledRelocateReason = null
    this.controlledRelocateStartSequence = this.relocateSequence
    this.relocatePostScheduled = false
    if (this.relocatePostTimer != null) {
      clearTimeout(this.relocatePostTimer)
      this.relocatePostTimer = null
    }
  }

  scheduleCommittedRelocation(detail, reason = 'relocate-committed') {
    if (!detail) return
    const previousReason = this.pendingRelocateReason
    const preserveExplicitReason = this.relocatePostScheduled &&
      readerRelocationReasonIsExplicit(previousReason) &&
      !readerRelocationReasonIsExplicit(reason)
    this.pendingRelocateDetail = detail
    this.pendingRelocateReason = preserveExplicitReason ? previousReason : reason
    if (this.relocatePostScheduled) return
    this.relocatePostScheduled = true
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        this.relocatePostTimer = setTimeout(() => {
          this.relocatePostTimer = null
          this.relocatePostScheduled = false
          const pendingDetail = this.pendingRelocateDetail
          const pendingReason = this.pendingRelocateReason
          this.pendingRelocateDetail = null
          this.pendingRelocateReason = 'relocate-committed'
          if (!pendingDetail) return
          this.applyThemeToLoadedContent(this.readerSettings)
          this.postLocationChanged(pendingDetail, pendingReason)
        }, ReaderRelocationCommitDelayMs)
      })
    })
  }

  suppressLoadedCoverDocument(doc, index) {
    const normalizedIndex = Number(index)
    if (!Number.isFinite(normalizedIndex)) return false
    const sectionIndex = Math.floor(normalizedIndex)
    const section = this.view?.book?.sections?.[sectionIndex]
    if (!readerContentDocumentLooksLikeCover(doc, section, sectionIndex)) return false
    this.suppressedCoverSectionIndexes.add(sectionIndex)
    doc.documentElement.dataset.navicSuppressedCover = 'true'
    doc.body?.setAttribute?.('data-navic-suppressed-cover', 'true')
    setStylesImportant(doc.documentElement, {
      background: 'transparent',
      color: 'transparent',
    })
    if (doc.body) {
      setStylesImportant(doc.body, {
        display: 'none',
        visibility: 'hidden',
        background: 'transparent',
        color: 'transparent',
      })
    }
    readerTrace('cover:document-suppressed', {
      index: sectionIndex,
      href: section?.href || section?.id || '',
    })
    log('cover-document:suppressed', `index=${sectionIndex}`, section?.href || section?.id || '')
    if (this.hasNonCoverReadableContent()) {
      requestAnimationFrame(() => {
        if (!this.view || this.shellCoverVisible) return
        const current = Number(this.lastRelocateDetail?.section?.current ?? this.lastRelocateDetail?.index)
        if (!Number.isFinite(current) || Math.floor(current) === sectionIndex) {
          this.goToFirstReadableContent().catch(error => reportError(error, 'navigation_failed'))
        }
      })
    }
    return true
  }

  suppressLoadedEmbeddedCoverPage(doc, index) {
    const normalizedIndex = Number(index)
    if (!Number.isFinite(normalizedIndex)) return false
    const sectionIndex = Math.floor(normalizedIndex)
    const suppressed = suppressReaderEmbeddedCoverPage(doc, sectionIndex)
    if (!suppressed) return false
    const firstSuppression = !this.embeddedCoverSuppressedSectionIndexes.has(sectionIndex)
    this.embeddedCoverSuppressedSectionIndexes.add(sectionIndex)
    const section = this.view?.book?.sections?.[sectionIndex]
    readerTrace('cover:embedded-page-suppressed', {
      index: sectionIndex,
      href: section?.href || section?.id || '',
      rerender: firstSuppression,
    })
    log('cover-embedded-page:suppressed', `index=${sectionIndex}`, section?.href || section?.id || '')
    if (firstSuppression && !this.embeddedCoverRerenderScheduled) {
      this.embeddedCoverRerenderScheduled = true
      requestAnimationFrame(() => {
        this.embeddedCoverRerenderScheduled = false
        if (!this.view) return
        this.view.renderer?.render?.()
        this.applyReaderViewportLayout('embedded-cover-suppressed')
        this.scheduleReaderPageNumberRefresh('embedded-cover-suppressed')
        this.scheduleCommittedRelocation(this.lastRelocateDetail, 'embedded-cover-suppressed')
      })
    }
    return true
  }

  attachContentDocumentBehaviors(doc, index) {
    if (!doc) return
    if (this.suppressLoadedCoverDocument(doc, index)) return
    this.suppressLoadedEmbeddedCoverPage(doc, index)
    this.applyDocumentDirection(doc, this.readerDirectionModeValue)
    this.applyDocumentTheme(doc, this.readerSettings, index)
    this.classifyReaderLinks(doc)
    this.attachSepiaImageOverlayToggle(doc)
    this.attachLinkNavigation(doc, index)
    this.attachScrolledEdgeTurnGestures(doc)
    this.attachSurfacePaperTextureDragDirection(doc)
    this.attachReaderTapZoneGesture(doc)
    if (doc.defaultView?.__navicSelectionBridgeAttached) return
    doc.defaultView.__navicSelectionBridgeAttached = true
    doc.addEventListener('selectionchange', () => {
      const selection = doc.getSelection()
      const text = selection?.toString?.().trim()
      if (!text) {
        post({ type: 'selectionCleared' })
        return
      }
      const range = selection.rangeCount ? selection.getRangeAt(0) : null
      const pos = this.selectionPosition(range)
      const contextText = this.selectionContextText(range, text)
      const footnote = this.selectionLooksLikeFootnote(range)
      const cfi = range && Number.isFinite(index)
        ? this.view?.getCFI?.(index, range)
        : undefined
      post({
        type: 'selectionChanged',
        text,
        cfi,
        href: this.view?.book?.sections?.[index]?.href,
        footnote,
        contextText,
        pos,
      })
    })
  }

  selectionPosition(range) {
    const rect = range?.getBoundingClientRect?.()
    if (!rect) return undefined
    return {
      left: rect.left,
      top: rect.top,
      right: rect.right,
      bottom: rect.bottom,
    }
  }

  selectionContextText(range, selectedText) {
    const element = this.selectionElement(range)
    const context = element?.innerText || element?.textContent || selectedText
    const normalized = String(context || '').replace(/\s+/g, ' ').trim()
    if (!normalized) return undefined
    return normalized.length > 500 ? normalized.slice(0, 500) : normalized
  }

  selectionElement(range) {
    const node = range?.commonAncestorContainer
    if (!node) return null
    if (node.nodeType === 1) return node
    const parent = node.parentElement || node.parentNode
    return parent?.nodeType === 1 ? parent : null
  }

  selectionLooksLikeFootnote(range) {
    const element = this.selectionElement(range)
    return !!element?.closest?.(
      'a[href^="#fn"], a[href*="footnote"], ' +
      'a[role="doc-noteref"], [role="doc-footnote"], ' +
      '[type~="noteref"], [type~="footnote"], ' +
      '[epub\\:type~="noteref"], [epub\\:type~="footnote"]'
    )
  }

  onLoad(detail = {}) {
    this.applyReaderViewportLayout('load')
    this.applyReaderDirection(this.readerDirectionModeValue, false)
    if (detail.doc) log('load:event-doc', `index=${detail.index ?? 'unknown'}`)
    const docIndex = Number(detail.index)
    const sectionIndex = Number.isFinite(docIndex) ? Math.floor(docIndex) : undefined
    const section = sectionIndex !== undefined ? this.view?.book?.sections?.[sectionIndex] : undefined
    post({
      type: 'loadDoc',
      index: sectionIndex,
      href: section?.href || undefined,
      title: section?.title || section?.label || undefined,
      sectionId: section?.id || undefined,
    })
    for (const content of this.contentEntries(detail)) {
      this.attachContentDocumentBehaviors(content.doc, content.index)
    }
    requestAnimationFrame(() => {
      this.applyRendererTheme(this.readerSettings)
      this.updateReaderPageNumberLayer()
      requestAnimationFrame(() => this.logContentLayout('load'))
    })
    if (this.mediaOverlayEnabled) post({ type: 'overlayFragmentInactive' })
  }

  logContentLayout(label = 'unknown') {
    const describeRect = rect => rect
      ? `${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
      : 'missing'
    const contents = this.view?.renderer?.getContents?.() || []
    const surfaceRect = this.view?.getBoundingClientRect?.()
    const rendererRect = this.view?.renderer?.getBoundingClientRect?.()
    log(
      'surface-layout',
      `label=${label}`,
      `view=${describeRect(surfaceRect)}`,
      `renderer=${describeRect(rendererRect)}`,
      `inner=${Math.round(window.innerWidth)}x${Math.round(window.innerHeight)}`,
      `visual=${Math.round(window.visualViewport?.width || 0)}x${Math.round(window.visualViewport?.height || 0)}`
    )
    if (!contents.length) {
      log('content-layout', `label=${label}`, 'contents=0')
      return
    }
    for (const content of contents) {
      const doc = content.doc
      if (!doc) {
        log('content-layout', `label=${label}`, `index=${content.index}`, 'doc=missing')
        continue
      }
      const frameElement = doc.defaultView?.frameElement
      const frameRect = frameElement?.getBoundingClientRect?.()
      const bodyRect = doc.body?.getBoundingClientRect?.()
      const htmlRect = doc.documentElement?.getBoundingClientRect?.()
      const bodyStyle = doc.defaultView.getComputedStyle(doc.body)
      const htmlStyle = doc.defaultView.getComputedStyle(doc.documentElement)
      const paragraphSpacing = bodyStyle.getPropertyValue('--reader-paragraph-spacing') ||
        htmlStyle.getPropertyValue('--reader-paragraph-spacing') ||
        'unset'
      const surfaceTextureStyle = this.surfaceTextureLayer
        ? window.getComputedStyle(this.surfaceTextureLayer)
        : null
      const surfaceBorderOverlayStyle = this.surfaceBorderOverlayLayer
        ? window.getComputedStyle(this.surfaceBorderOverlayLayer)
        : null
      const paragraphBlocks = Array.from(doc.querySelectorAll?.('p,[data-navic-paragraph-block="true"]') || [])
      const firstParagraphStyle = paragraphBlocks[0]
        ? doc.defaultView.getComputedStyle(paragraphBlocks[0])
        : null
      const textLength = doc.body?.textContent?.replace(/\s+/g, ' ').trim().length ?? 0
      log(
        'content-layout',
        `label=${label}`,
        `index=${content.index}`,
        `frameElement=${frameElement?.localName || 'missing'}`,
        `iframe=${describeRect(frameRect)}`,
        `html=${describeRect(htmlRect)}`,
        `body=${describeRect(bodyRect)}`,
        `textLength=${textLength}`,
        `color=${bodyStyle.color}`,
        `background=${bodyStyle.backgroundColor || htmlStyle.backgroundColor}`,
        `paragraphSpacing=${paragraphSpacing}`,
        `paragraphBlockCount=${paragraphBlocks.length}`,
        `firstParagraphMarginEnd=${firstParagraphStyle?.marginBlockEnd || firstParagraphStyle?.marginBottom || 'unset'}`,
        `surfaceTextureOpacity=${surfaceTextureStyle?.opacity || 'unset'}`,
        `surfaceTextureImage=${surfaceTextureStyle?.backgroundImage === 'none' ? 'none' : surfaceTextureStyle ? 'set' : 'unset'}`,
        `surfaceBorderOverlayOpacity=${surfaceBorderOverlayStyle?.opacity || 'unset'}`,
        `surfaceBorderOverlayImage=${surfaceBorderOverlayStyle?.backgroundImage === 'none' ? 'none' : surfaceBorderOverlayStyle ? 'set' : 'unset'}`,
        `surfaceTextureLayer=${this.surfaceTextureLayer ? 'present' : 'missing'}`,
        `surfaceTextureAsset=${readerRoot.dataset.navicSurfacePaperTextureAsset || 'unset'}`,
        `surfaceBorderOverlayAsset=${readerRoot.dataset.navicSurfaceBorderOverlayAsset || 'unset'}`
      )
    }
  }

  contentEntries(detail = {}) {
    const entries = []
    const seen = new Set()
    const contents = this.view?.renderer?.getContents?.() || []
    const add = (doc, index) => {
      if (!doc || seen.has(doc)) return
      seen.add(doc)
      entries.push({ doc, index })
    }
    if (detail.doc) {
      const matchingContent = contents.find(content => content.doc === detail.doc)
      add(detail.doc, Number.isFinite(detail.index) ? detail.index : matchingContent?.index)
    }
    for (const content of contents) {
      add(content.doc, content.index)
    }
    return entries
  }

  contentDocuments() {
    return this.contentEntries().map(content => content.doc)
  }
}

Object.assign(NavicReaderRuntime.prototype,
  NavicReaderPageTurnMethods,
  NavicReaderContentInteractionMethods,
  NavicReaderPaginationMethods,
  NavicReaderAppearanceMethods
)

const runtime = new NavicReaderRuntime()

window.NavicReaderBridge = {
  dispatch: command => runtime.dispatch(command),
  readerContentActionAtPoint: (x, y, viewWidth, viewHeight) =>
    runtime.readerContentActionAtRootPoint(x, y, viewWidth, viewHeight)?.handled === true,
  postOverlayFragmentActive: fragment => post({ type: 'overlayFragmentActive', ...fragment }),
  postOverlayFragmentInactive: fragmentId => post({ type: 'overlayFragmentInactive', fragmentId }),
}

readerTrace('runtime:ready', { engine: 'foliate-js' })
log('module-loaded')
post({ type: 'ready' })
