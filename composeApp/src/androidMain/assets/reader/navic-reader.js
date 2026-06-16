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
  applyReaderParagraphSpacing,
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
      this.view.addEventListener('external-link', event => event.preventDefault())
      readerRoot.replaceChildren(this.view)
      if (settings) this.applySettings(settings)
      this.applyReaderViewportLayout('view-created')
      await this.view.open(url)
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

  async goTo(locator) {
    if (!this.view || !locator) return
    try {
      await this.view.goTo(locator)
      this.scheduleCommittedRelocation(this.lastRelocateDetail, 'go-to')
      this.logContentLayout('go-to')
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }

  progressTargetForSections(fraction) {
    const sectionCount = Number(this.view?.book?.sections?.length)
    if (!Number.isFinite(sectionCount) || sectionCount <= 0) return null
    const index = Math.floor(Math.min(1, Math.max(0, fraction)) * sectionCount)
    return Math.min(sectionCount - 1, Math.max(0, index))
  }

  fixedLayoutCurrentPageIndex() {
    if (this.view?.isFixedLayout !== true) return null
    try {
      const index = Number(this.view?.renderer?.index)
      return Number.isFinite(index) ? Math.floor(index) : null
    } catch (error) {
      log('fixed-layout-index:unavailable', error?.message || String(error))
      return null
    }
  }

  fixedLayoutNavigationBasePageIndex() {
    if (this.view?.isFixedLayout !== true) return null
    const navigationIndex = Number(this.fixedLayoutNavigationPageIndex)
    if (Number.isFinite(navigationIndex)) return Math.floor(navigationIndex)
    const committedPageIndex = Number(this.currentPagePosition?.pageIndex)
    if (Number.isFinite(committedPageIndex)) return Math.floor(committedPageIndex)
    return this.fixedLayoutCurrentPageIndex()
  }

  syncFixedLayoutNavigationPageIndex(pagePosition) {
    if (this.view?.isFixedLayout !== true) return
    const pageIndex = Number(pagePosition?.pageIndex)
    if (!Number.isFinite(pageIndex)) return
    const pendingIndex = Number(this.fixedLayoutNavigationPageIndex)
    if (Number.isFinite(pendingIndex)) {
      if (this.fixedLayoutNavigationDirection === 'next' && pageIndex < pendingIndex) return
      if (this.fixedLayoutNavigationDirection === 'previous' && pageIndex > pendingIndex) return
    }
    this.fixedLayoutNavigationPageIndex = Math.floor(pageIndex)
    this.fixedLayoutNavigationDirection = null
  }

  fixedLayoutAdjacentPageTarget(direction) {
    if (this.view?.isFixedLayout !== true) return null
    const current = this.fixedLayoutNavigationBasePageIndex()
    const sectionCount = Number(this.view?.book?.sections?.length)
    if (!Number.isFinite(current) || !Number.isFinite(sectionCount) || sectionCount <= 0) return null
    const forward = direction === 'next'
    const rtl = this.effectiveReaderDirection() === ReaderDirectionRtl
    const delta = forward === rtl ? -1 : 1
    const target = current + delta
    return target >= 0 && target < sectionCount ? target : null
  }

  async goToProgress(progress) {
    if (!this.view) return
    const numericProgress = Number(progress)
    const fraction = Number.isFinite(numericProgress)
      ? Math.min(1, Math.max(0, numericProgress))
      : 0
    try {
      log('progress-seek:start', fraction)
      const canUseFraction = typeof this.view?.goToFraction === 'function' &&
        this.view?.book?.splitTOCHref &&
        this.view?.book?.getTOCFragment
      const progressTarget = this.progressTargetForSections(fraction)
      if (canUseFraction) {
        await this.view.goToFraction(fraction)
      } else if (progressTarget != null) {
        log('progress-seek:fallback-section', progressTarget)
        await this.view.goTo(progressTarget)
      } else {
        await this.view.goTo({ fraction })
      }
      this.scheduleCommittedRelocation(this.lastRelocateDetail, 'progress-seek')
      this.applyReaderViewportLayout('progress-seek')
      requestAnimationFrame(() => {
        this.logContentLayout('progress-seek')
        log('progress-seek:done', fraction)
      })
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }

  async goToChapterProgress(href, progress) {
    if (!this.view) return
    const targetHref = String(href || '').trim()
    if (!targetHref) return
    const numericProgress = Number(progress)
    const fraction = Number.isFinite(numericProgress)
      ? Math.min(1, Math.max(0, numericProgress))
      : 0
    try {
      log('chapter-progress-seek:start', targetHref, fraction)
      const resolved = await Promise.resolve(
        this.view.resolveNavigation?.(targetHref) ||
        this.view.book?.resolveHref?.(targetHref)
      )
      const index = Number(resolved?.index)
      if (Number.isFinite(index) && this.view.renderer?.goTo) {
        await this.view.renderer.goTo({ index, anchor: fraction })
        this.view.history?.pushState?.({ href: targetHref, chapterFraction: fraction })
      } else {
        await this.view.goTo(targetHref)
      }
      this.scheduleCommittedRelocation(this.lastRelocateDetail, 'chapter-progress-seek')
      this.applyReaderViewportLayout('chapter-progress-seek')
      requestAnimationFrame(() => {
        this.logContentLayout('chapter-progress-seek')
        log('chapter-progress-seek:done', targetHref, fraction)
      })
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }

  nextPage() {
    return this.turnPage('next')
  }

  previousPage() {
    return this.turnPage('previous')
  }

  previewPageDrag(command) {
    if (!this.view || this.shellCoverVisible) return
    const renderer = this.view?.renderer
    if (!renderer || renderer.scrolled || typeof renderer.scrollBy !== 'function') return
    const phase = command?.phase === 'release'
      ? 'release'
      : command?.phase === 'cancel'
        ? 'cancel'
        : 'update'
    if (phase === 'cancel') {
      const previousDeltaX = Number(this.nativePageDragPreview?.deltaX) || 0
      if (previousDeltaX !== 0) renderer.scrollBy(previousDeltaX, 0)
      readerTrace('page-drag-preview:cancel', { deltaX: previousDeltaX })
      this.nativePageDragPreview = null
      this.surfacePaperTextureTurnDirection = null
      this.renderSurfacePaperTextureLayers()
      return
    }
    const deltaX = Number(command?.deltaX)
    if (!Number.isFinite(deltaX)) return
    const textureDirection = readerPaperTextureDragDirection({
      deltaX,
      deltaY: 0,
      flowMode: this.readerFlowModeValue,
      threshold: 1,
    })
    if (textureDirection) {
      this.surfacePaperTextureTurnDirection = textureDirection
      readerTrace('texture:drag-direction', {
        direction: textureDirection,
        source: 'native-preview',
      })
    }
    const lastDeltaX = Number(this.nativePageDragPreview?.deltaX) || 0
    const incrementalDeltaX = deltaX - lastDeltaX
    if (incrementalDeltaX !== 0) renderer.scrollBy(-incrementalDeltaX, 0)
    this.nativePageDragPreview = phase === 'release'
      ? null
      : { deltaX }
    readerTrace('page-drag-preview', {
      phase,
      deltaX,
      incrementalDeltaX,
      start: renderer.start,
      end: renderer.end,
      viewSize: renderer.viewSize,
    })
  }

  async scrollViewport(direction) {
    if (!this.view) return
    const scrollDirection = direction === 'up' ? 'up' : 'down'
    const renderer = this.view?.renderer
    if (!renderer?.scrolled || typeof renderer.scrollBy !== 'function') {
      return scrollDirection === 'down' ? this.nextPage() : this.previousPage()
    }
    const viewportSize = Number(renderer.size) || Number(readerViewportSize().height) || 0
    const scrollDistance = Math.max(1, Math.round(viewportSize * ViewportScrollStepRatio))
    const delta = scrollDirection === 'down' ? scrollDistance : -scrollDistance
    const scrollsAlongHeight = renderer.sideProp !== 'width'
    log('viewport-scroll:start', scrollDirection, `distance=${scrollDistance}`)
    readerTrace('viewport-scroll:start', {
      direction: scrollDirection,
      distance: scrollDistance,
      start: renderer.start,
      end: renderer.end,
      viewSize: renderer.viewSize,
    })
    if (scrollsAlongHeight) {
      renderer.scrollBy(delta, 0)
    } else {
      renderer.scrollBy(0, delta)
    }
    this.applyReaderViewportLayout(`viewport-scroll:${scrollDirection}`)
    requestAnimationFrame(() => {
      this.logContentLayout(`viewport-scroll:${scrollDirection}`)
      readerTrace('viewport-scroll:done', {
        direction: scrollDirection,
        start: renderer.start,
        end: renderer.end,
        viewSize: renderer.viewSize,
      })
      log('viewport-scroll:done', scrollDirection)
    })
  }

  turnPage(direction) {
    if (this.shellCoverVisible && direction === 'next') {
      log('page-turn:shell-cover-hide', direction)
      this.hideShellCover()
      return
    }
    if (this.shellCoverVisible && direction === 'previous') {
      log('page-turn:shell-cover-boundary', direction)
      return
    }
    if (direction === 'previous' && this.canReturnToShellCover()) {
      log('page-turn:shell-cover-return', direction)
      this.showShellCover()
      return
    }
    if (this.pageTurnPromise) {
      log('page-turn:queued', direction)
      readerTrace('page-turn:queued', {
        direction,
        navigationIndex: this.fixedLayoutNavigationPageIndex,
        rendererIndex: this.fixedLayoutCurrentPageIndex(),
      })
      return new Promise((resolve, reject) => {
        this.pageTurnQueue.push({ direction, resolve, reject })
      })
    }
    return this.startPageTurn(direction)
  }

  startPageTurn(direction) {
    readerTrace('page-turn:start-request', {
      direction,
      hasPromise: Boolean(this.pageTurnPromise),
      queueLength: this.pageTurnQueue.length,
    })
    this.cancelPendingCommittedRelocation()
    this.pageTurnInProgress = true
    this.pageTurnDirection = direction
    this.surfacePaperTextureTurnDirection = direction
    const turnPromise = Promise.resolve().then(() => this.performPageTurn(direction))
    let completionPromise = null
    completionPromise = turnPromise.finally(() => {
      if (this.pageTurnPromise === completionPromise) this.pageTurnPromise = null
      this.pageTurnInProgress = false
      if (this.pageTurnDirection === direction) this.pageTurnDirection = null
      readerTrace('page-turn:settled', {
        direction,
        navigationIndex: this.fixedLayoutNavigationPageIndex,
        rendererIndex: this.fixedLayoutCurrentPageIndex(),
      })
      this.startNextQueuedPageTurn()
    })
    this.pageTurnPromise = completionPromise
    readerTrace('page-turn:promise-set', {
      direction,
      queueLength: this.pageTurnQueue.length,
    })
    return completionPromise
  }

  startNextQueuedPageTurn() {
    if (this.pageTurnPromise || this.pageTurnQueue.length === 0) return
    const next = this.pageTurnQueue.shift()
    this.startPageTurn(next.direction).then(next.resolve, next.reject)
  }

  async performPageTurn(direction) {
    if (!this.view) return
    try {
      log('page-turn:start', direction)
      const directFixedLayoutPageTarget = this.fixedLayoutAdjacentPageTarget(direction)
      if (directFixedLayoutPageTarget != null) {
        log('page-turn:fixed-direct', direction, directFixedLayoutPageTarget)
        readerTrace('page-turn:fixed-direct', {
          direction,
          target: directFixedLayoutPageTarget,
          navigationIndex: this.fixedLayoutNavigationPageIndex,
          rendererIndex: this.fixedLayoutCurrentPageIndex(),
        })
        this.fixedLayoutNavigationPageIndex = directFixedLayoutPageTarget
        this.fixedLayoutNavigationDirection = direction
        await this.view.goTo(directFixedLayoutPageTarget)
      } else if (direction === 'next') {
        await this.view?.next?.()
      } else {
        await this.view?.prev?.()
      }
      this.recentPageTurnDirection = direction
      this.applyReaderViewportLayout(`page-turn:${direction}`)
      requestAnimationFrame(() => {
        this.logContentLayout(`page-turn:${direction}`)
        this.scheduleCommittedRelocation(this.lastRelocateDetail, `page-turn:${direction}`)
        log('page-turn:done', direction)
      })
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }

  attachScrolledEdgeTurnGestures(doc) {
    if (!doc?.defaultView || doc.defaultView.__navicScrolledEdgeTurnGesturesAttached) return
    doc.defaultView.__navicScrolledEdgeTurnGesturesAttached = true
    let touchState = null
    doc.addEventListener('touchstart', event => {
      const touch = event.changedTouches?.[0]
      if (!touch || event.touches?.length > 1) {
        touchState = null
        return
      }
      touchState = {
        x: touch.screenX ?? touch.clientX ?? 0,
        y: touch.screenY ?? touch.clientY ?? 0,
      }
    }, { passive: true })
    doc.addEventListener('touchmove', event => {
      if (!touchState || event.touches?.length > 1) {
        touchState = null
        return
      }
      const touch = event.changedTouches?.[0]
      if (!touch) return
      touchState.lastX = touch.screenX ?? touch.clientX ?? touchState.x
      touchState.lastY = touch.screenY ?? touch.clientY ?? touchState.y
    }, { passive: true })
    doc.addEventListener('touchend', event => {
      const state = touchState
      touchState = null
      if (!state) return
      const touch = event.changedTouches?.[0]
      if (!touch) return
      const endX = touch.screenX ?? touch.clientX ?? state.lastX ?? state.x
      const endY = touch.screenY ?? touch.clientY ?? state.lastY ?? state.y
      const deltaX = endX - state.x
      const deltaY = endY - state.y
      const selection = doc.getSelection?.()
      if (selection && selection.rangeCount > 0 && !selection.isCollapsed) return
      if (Math.abs(deltaY) < ScrollEdgeTurnSwipeThreshold || Math.abs(deltaY) <= Math.abs(deltaX)) return
      this.turnScrolledEdgePage(deltaY)
    }, { passive: true })
    doc.addEventListener('touchcancel', () => {
      touchState = null
    }, { passive: true })
  }

  attachSurfaceTapGesture(element) {
    if (!element || element.__navicSurfaceTapGestureAttached) return
    element.__navicSurfaceTapGestureAttached = true
    let touchState = null
    element.addEventListener('touchstart', event => {
      const touch = event.changedTouches?.[0]
      if (!touch || event.touches?.length > 1) {
        touchState = null
        return
      }
      touchState = {
        target: event.target,
        x: touch.screenX ?? touch.clientX ?? 0,
        y: touch.screenY ?? touch.clientY ?? 0,
        clientX: touch.clientX,
        clientY: touch.clientY,
      }
    }, { passive: true })
    element.addEventListener('touchmove', event => {
      if (!touchState || event.touches?.length > 1) {
        touchState = null
        return
      }
      const touch = event.changedTouches?.[0]
      if (!touch) return
      touchState.lastX = touch.screenX ?? touch.clientX ?? touchState.x
      touchState.lastY = touch.screenY ?? touch.clientY ?? touchState.y
    }, { passive: true })
    element.addEventListener('touchend', async event => {
      const state = touchState
      touchState = null
      if (!state || event.touches?.length > 0) return
      const touch = event.changedTouches?.[0]
      if (!touch) return
      const endX = touch.screenX ?? touch.clientX ?? state.lastX ?? state.x
      const endY = touch.screenY ?? touch.clientY ?? state.lastY ?? state.y
      const deltaX = endX - state.x
      const deltaY = endY - state.y
      if (
        this.view?.isFixedLayout === true &&
        Math.abs(deltaX) >= FixedLayoutSurfaceSwipeThreshold &&
        Math.abs(deltaX) > Math.abs(deltaY)
      ) {
        markReaderSurfaceTapHandled(element, event)
        const handled = await this.turnFixedLayoutSwipePage(deltaX)
        if (handled) {
          event.preventDefault()
          event.stopPropagation()
          markReaderSurfaceTapHandled(element, event)
        }
        return
      }
    }, { passive: false })
    element.addEventListener('touchcancel', () => {
      touchState = null
    }, { passive: true })
    element.addEventListener('click', event => {
      if (event.defaultPrevented || event.button !== 0) return
      if (shouldSuppressReaderSurfaceClick(element, event)) {
        event.preventDefault()
        event.stopPropagation()
        return
      }
    }, { passive: false })
  }

  readerTapZoneActionForPoint(clientX, clientY) {
    if (this.readerTapZoneMode === ReaderTapZoneDisabled) return null
    const { width, height } = readerViewportSize()
    if (!width || !height) return null
    return komikkuTapAction(
      this.readerTapZoneMode,
      Math.max(0, Math.min(1, (clientX || 0) / width)),
      Math.max(0, Math.min(1, (clientY || 0) / height)),
      this.smallerTapZone,
      this.readerFlowModeValue
    )
  }

  readerTapZoneCommand(action) {
    const rtl = this.effectiveReaderDirection() === ReaderDirectionRtl
    switch (action) {
      case KomikkuNavigationRegionPrevious:
        return 'previous'
      case KomikkuNavigationRegionNext:
        return 'next'
      case KomikkuNavigationRegionLeft:
        return rtl ? 'next' : 'previous'
      case KomikkuNavigationRegionRight:
        return rtl ? 'previous' : 'next'
      default:
        return null
    }
  }

  readerTapZoneGestureHost(target) {
    return target?.defaultView || target || null
  }

  markReaderTapZoneTouchHandled(target, event) {
    const host = this.readerTapZoneGestureHost(target)
    if (!host) return
    host.__navicSuppressNextTapZoneClickUntil = (event?.timeStamp || performance.now()) + CenterTapSyntheticClickDedupeMs
  }

  shouldSuppressReaderTapZoneClick(target, event) {
    const host = this.readerTapZoneGestureHost(target)
    const until = Number(host?.__navicSuppressNextTapZoneClickUntil || 0)
    return until > 0 && (event?.timeStamp || performance.now()) <= until
  }

  shouldIgnoreReaderTapZoneTarget(event, sourceTarget) {
    const target = event?.target
    const doc = target?.ownerDocument || sourceTarget?.ownerDocument || sourceTarget
    const selection = doc?.getSelection?.()
    if (selection && selection.rangeCount > 0 && !selection.isCollapsed) return true
    const anchor = closestElement(target, 'a[href]')
    if (anchor && readerPointInsideAnchorText(anchor, event)) return true
    if (readerMediaTapTargetForEvent(doc, event, anchor)) return true
    return false
  }

  rememberReaderContentActionTouch(doc, event, detail = {}) {
    const rootPoint = readerRootTapPoint(event, doc) || readerEventClientPoint(event)
    const x = Number(rootPoint?.x ?? rootPoint?.clientX)
    const y = Number(rootPoint?.y ?? rootPoint?.clientY)
    if (!Number.isFinite(x) || !Number.isFinite(y)) return
    this.recentContentActionTouch = {
      x,
      y,
      kind: detail.kind || 'content',
      href: detail.href || '',
      source: detail.source || '',
      expiresAt: performance.now() + ReaderMediaSyntheticClickSuppressMs,
    }
    readerTrace('content-hit-test:remember', {
      kind: this.recentContentActionTouch.kind,
      href: this.recentContentActionTouch.href,
      source: this.recentContentActionTouch.source,
      x: Math.round(x),
      y: Math.round(y),
    })
  }

  suppressReaderNativeTapZoneContentActivation(doc, event, source = 'content-click') {
    if (this.nativeTapZones !== true || !doc || event?.defaultPrevented || event?.button > 0) return false
    const anchor = closestElement(event.target, 'a[href]')
    const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
    if (!anchor && !mediaTapTarget) return false
    event.preventDefault?.()
    event.stopPropagation?.()
    event.stopImmediatePropagation?.()
    readerTrace('native-tap-zones:content-click-suppressed', {
      source,
      kind: mediaTapTarget ? 'media' : 'link',
      href: anchor?.getAttribute?.('href') || '',
    })
    return true
  }

  async handleNativeTapZoneContentLongPress(doc, event, index = null, source = 'content-long-press') {
    if (this.nativeTapZones !== true || !doc || event?.defaultPrevented) return false
    const anchor = closestElement(event.target, 'a[href]')
    const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
    if (!anchor && !mediaTapTarget) return false
    readerTrace('native-tap-zones:content-long-press', {
      source,
      kind: mediaTapTarget ? 'media' : 'link',
      href: anchor?.getAttribute?.('href') || '',
    })
    if (mediaTapTarget) {
      return this.toggleSepiaImageOverlayFromEvent(doc, event, mediaTapTarget)
    }
    if (index == null) return false
    return this.activateReaderLinkFromEvent(doc, event, index, source)
  }

  async handleNativeTapZoneContentLongPressAt(rootX, rootY, viewWidth = null, viewHeight = null, source = 'content-long-press-command') {
    if (this.nativeTapZones !== true) return false
    const rootPoint = this.normalizeReaderContentRootPoint(rootX, rootY, viewWidth, viewHeight)
    for (const entry of this.contentEntries()) {
      const hit = this.readerContentActionInDocumentAtPoint(entry.doc, rootPoint.x, rootPoint.y, entry.index)
      if (!hit?.handled) continue
      const frame = entry.doc.defaultView?.frameElement
      const frameRect = frame?.getBoundingClientRect?.()
      const event = {
        target: hit.target,
        clientX: hit.x,
        clientY: hit.y,
        button: 0,
        defaultPrevented: false,
        preventDefault() {
          this.defaultPrevented = true
        },
        stopPropagation() {},
        stopImmediatePropagation() {},
      }
      readerTrace('native-tap-zones:content-long-press-at', {
        source,
        kind: hit.kind,
        href: hit.href || '',
        index: hit.index,
        x: Math.round(Number(rootPoint.x) || 0),
        y: Math.round(Number(rootPoint.y) || 0),
        frameX: Math.round(Number(frameRect?.left || 0)),
        frameY: Math.round(Number(frameRect?.top || 0)),
      })
      if (hit.kind === 'media') {
        return this.toggleSepiaImageOverlayFromEvent(entry.doc, event, hit.mediaTapTarget)
      }
      if (hit.kind === 'link') {
        return this.activateReaderLinkFromEvent(entry.doc, event, hit.index, source)
      }
      return true
    }
    readerTrace('native-tap-zones:content-long-press-at-miss', {
      source,
      x: Math.round(Number(rootPoint.x) || 0),
      y: Math.round(Number(rootPoint.y) || 0),
    })
    return false
  }

  readerContentActionClaimPayload(doc, event, detail = {}) {
    const rootPoint = readerRootTapPoint(event, doc) || readerEventClientPoint(event)
    const x = Number(rootPoint?.x ?? rootPoint?.clientX)
    const y = Number(rootPoint?.y ?? rootPoint?.clientY)
    const anchor = detail.anchor || closestElement(event?.target, 'a[href]')
    const image = detail.image || readerImageFromMediaTarget(detail.mediaTapTarget)
    const payload = {
      type: 'readerContentTapHandled',
      action: detail.action || detail.kind || 'content',
      source: detail.source || '',
    }
    const href = detail.href || anchor?.getAttribute?.('href') || ''
    const src = detail.src || image?.currentSrc || image?.getAttribute?.('src') || ''
    const text = detail.text ||
      anchor?.textContent?.trim?.() ||
      image?.getAttribute?.('alt') ||
      image?.getAttribute?.('title') ||
      ''
    if (href) payload.href = href
    if (src) payload.src = src
    if (text) payload.text = text
    if (Number.isFinite(x)) payload.x = x
    if (Number.isFinite(y)) payload.y = y
    return payload
  }

  recentReaderContentActionAtRootPoint(rootPoint) {
    const recent = this.recentContentActionTouch
    if (!recent) return null
    if (performance.now() > Number(recent.expiresAt || 0)) {
      this.recentContentActionTouch = null
      return null
    }
    const x = Number(rootPoint?.x)
    const y = Number(rootPoint?.y)
    if (!Number.isFinite(x) || !Number.isFinite(y)) return null
    const slop = CenterTapMovementSlop * 3
    if (Math.abs(x - recent.x) > slop || Math.abs(y - recent.y) > slop) return null
    readerTrace('content-hit-test:recent', {
      kind: recent.kind,
      href: recent.href,
      source: recent.source,
      x: Math.round(x),
      y: Math.round(y),
    })
    return {
      handled: true,
      kind: recent.kind,
      href: recent.href,
      source: recent.source,
      recent: true,
    }
  }

  claimReaderInteractiveContentTouch(doc, event) {
    if (!doc || event?.defaultPrevented || event?.touches?.length > 1) return false
    if (this.nativeTapZones === true) return false
    const anchor = closestElement(event.target, 'a[href]')
    const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
    if (mediaTapTarget) {
      this.rememberReaderContentActionTouch(doc, event, {
        kind: 'media',
        href: anchor?.getAttribute?.('href') || '',
        source: 'media-touch',
      })
      post(this.readerContentActionClaimPayload(doc, event, {
        kind: 'media',
        href: anchor?.getAttribute?.('href') || '',
        source: 'media-touch',
        anchor,
        mediaTapTarget,
      }))
      readerTrace('content-touch:media', {
        tagName: mediaTapTarget.tagName || 'media',
        href: anchor?.getAttribute?.('href') || '',
      })
      return true
    }
    if (anchor) {
      this.rememberReaderContentActionTouch(doc, event, {
        kind: 'link',
        href: anchor.getAttribute('href') || '',
        source: 'link-touch',
      })
      post(this.readerContentActionClaimPayload(doc, event, {
        kind: 'link',
        href: anchor.getAttribute('href') || '',
        source: 'link-touch',
        anchor,
      }))
      readerTrace('content-touch:link', {
        href: anchor.getAttribute('href') || '',
        textHit: readerPointInsideAnchorText(anchor, event),
      })
      return true
    }
    return false
  }

  readerContentActionInDocumentAtPoint(doc, rootX, rootY, index = null) {
    if (!doc?.elementFromPoint) return null
    const frame = doc.defaultView?.frameElement
    const frameRect = frame?.getBoundingClientRect?.()
    const x = Number(rootX) - (frameRect?.left || 0)
    const y = Number(rootY) - (frameRect?.top || 0)
    if (!Number.isFinite(x) || !Number.isFinite(y)) return null
    if (frameRect && (x < 0 || y < 0 || x > frameRect.width || y > frameRect.height)) return null
    const target = doc.elementFromPoint(x, y)
    if (!target) return null
    const event = {
      target,
      clientX: x,
      clientY: y,
      button: 0,
      defaultPrevented: false,
    }
    const anchor = closestElement(target, 'a[href]')
    const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
    if (mediaTapTarget) {
      return {
        handled: true,
        kind: 'media',
        href: anchor?.getAttribute?.('href') || '',
        target,
        x,
        y,
        mediaTapTarget,
        index,
      }
    }
    if (anchor) {
      return {
        handled: true,
        kind: 'link',
        href: anchor.getAttribute('href') || '',
        textHit: readerPointInsideAnchorText(anchor, event),
        target,
        x,
        y,
        index,
      }
    }
    const formTarget = closestElement(target, 'button,input,textarea,select,summary,[role="button"],[contenteditable="true"]')
    if (formTarget) {
      return {
        handled: true,
        kind: 'control',
        index,
      }
    }
    return null
  }

  normalizeReaderContentRootPoint(rootX, rootY, viewWidth = null, viewHeight = null) {
    const rawX = Number(rootX)
    const rawY = Number(rootY)
    if (!Number.isFinite(rawX) || !Number.isFinite(rawY)) return { x: rawX, y: rawY }
    const viewportWidth = Number(window.visualViewport?.width || window.innerWidth || document.documentElement?.clientWidth || 0)
    const viewportHeight = Number(window.visualViewport?.height || window.innerHeight || document.documentElement?.clientHeight || 0)
    const nativeWidth = Number(viewWidth)
    const nativeHeight = Number(viewHeight)
    const scaleX = Number.isFinite(nativeWidth) && nativeWidth > 0 && viewportWidth > 0
      ? nativeWidth / viewportWidth
      : 1
    const scaleY = Number.isFinite(nativeHeight) && nativeHeight > 0 && viewportHeight > 0
      ? nativeHeight / viewportHeight
      : scaleX
    const deviceScale = Number(window.devicePixelRatio || 1)
    let x = rawX
    let y = rawY
    let source = 'css'
    if (scaleX > 1.01 || scaleY > 1.01) {
      x = rawX / scaleX
      y = rawY / scaleY
      source = 'native-view'
    } else if (deviceScale > 1.01 && (rawX > viewportWidth || rawY > viewportHeight)) {
      x = rawX / deviceScale
      y = rawY / deviceScale
      source = 'device-pixel-ratio'
    }
    if (source !== 'css') {
      readerTrace('content-hit-test:normalize', {
        source,
        rawX: Math.round(rawX),
        rawY: Math.round(rawY),
        x: Math.round(x),
        y: Math.round(y),
        viewWidth: Number.isFinite(nativeWidth) ? Math.round(nativeWidth) : null,
        viewHeight: Number.isFinite(nativeHeight) ? Math.round(nativeHeight) : null,
        viewportWidth: Math.round(viewportWidth),
        viewportHeight: Math.round(viewportHeight),
        deviceScale,
      })
    }
    return { x, y }
  }

  readerContentActionAtRootPoint(rootX, rootY, viewWidth = null, viewHeight = null) {
    const rootPoint = this.normalizeReaderContentRootPoint(rootX, rootY, viewWidth, viewHeight)
    const recentHit = this.recentReaderContentActionAtRootPoint(rootPoint)
    if (recentHit?.handled) return recentHit
    for (const entry of this.contentEntries()) {
      const hit = this.readerContentActionInDocumentAtPoint(entry.doc, rootPoint.x, rootPoint.y, entry.index)
      if (!hit?.handled) continue
      readerTrace('content-hit-test', {
        kind: hit.kind,
        href: hit.href || '',
        index: hit.index,
        x: Math.round(Number(rootPoint.x) || 0),
        y: Math.round(Number(rootPoint.y) || 0),
      })
      return hit
    }
    return { handled: false }
  }

  handleReaderTapZoneTap(event, sourceTarget) {
    if (!event || event.defaultPrevented || event.button > 0) return false
    const doc = event?.target?.ownerDocument || sourceTarget?.ownerDocument || sourceTarget
    if (this.shouldSuppressReaderTapZoneClick(sourceTarget, event)) {
      event.preventDefault?.()
      event.stopPropagation?.()
      event.stopImmediatePropagation?.()
      return true
    }
    if (this.shouldIgnoreReaderTapZoneTarget(event, sourceTarget)) return false
    const rootPoint = readerRootTapPoint(event, doc) || readerEventClientPoint(event)
    const action = this.readerTapZoneActionForPoint(rootPoint?.x ?? rootPoint?.clientX, rootPoint?.y ?? rootPoint?.clientY)
    if (!action) return false
    if (action === KomikkuNavigationRegionMenu) {
      event.preventDefault?.()
      event.stopPropagation?.()
      event.stopImmediatePropagation?.()
      post({ type: 'readerCenterTap' })
      return true
    }
    const command = this.readerTapZoneCommand(action)
    if (!command) return false
    event.preventDefault?.()
    event.stopPropagation?.()
    event.stopImmediatePropagation?.()
    if (command === 'next') void this.nextPage()
    else void this.previousPage()
    return true
  }

  attachReaderTapZoneGesture(target) {
    const host = this.readerTapZoneGestureHost(target)
    if (!target || !host || host.__navicReaderTapZoneGestureAttached) return
    host.__navicReaderTapZoneGestureAttached = true
    if (this.nativeTapZones === true) {
      this.renderTapZoneOverlayLayer()
      return
    }
    let touchState = null
    target.addEventListener('touchstart', event => {
      const touch = event.changedTouches?.[0]
      if (!touch || event.touches?.length > 1) {
        touchState = null
        return
      }
      touchState = {
        target: event.target,
        x: touch.screenX ?? touch.clientX ?? 0,
        y: touch.screenY ?? touch.clientY ?? 0,
        clientX: touch.clientX,
        clientY: touch.clientY,
      }
    }, { passive: true })
    target.addEventListener('touchend', event => {
      const state = touchState
      touchState = null
      if (!state || event.touches?.length > 0) return
      const touch = event.changedTouches?.[0]
      if (!touch) return
      const endX = touch.screenX ?? touch.clientX ?? state.x
      const endY = touch.screenY ?? touch.clientY ?? state.y
      if (Math.abs(endX - state.x) > CenterTapMovementSlop) return
      if (Math.abs(endY - state.y) > CenterTapMovementSlop) return
      const tapEvent = {
        defaultPrevented: event.defaultPrevented,
        button: 0,
        target: state.target || event.target,
        clientX: touch.clientX ?? state.clientX,
        clientY: touch.clientY ?? state.clientY,
        preventDefault: () => event.preventDefault(),
        stopPropagation: () => event.stopPropagation(),
        stopImmediatePropagation: () => event.stopImmediatePropagation(),
        timeStamp: event.timeStamp,
      }
      if (this.handleReaderTapZoneTap(tapEvent, target)) {
        this.markReaderTapZoneTouchHandled(target, event)
      }
    }, { passive: false })
    target.addEventListener('touchcancel', () => {
      touchState = null
    }, { passive: true })
    target.addEventListener('click', event => {
      this.handleReaderTapZoneTap(event, target)
    }, { passive: false })
  }

  attachLinkNavigation(doc, index) {
    if (!doc?.defaultView || doc.defaultView.__navicLinkNavigationAttached) return
    doc.defaultView.__navicLinkNavigationAttached = true
    doc.addEventListener('touchstart', event => {
      this.claimReaderInteractiveContentTouch(doc, event)
    }, { capture: true, passive: true })
    doc.addEventListener('touchend', event => {
      this.claimReaderInteractiveContentTouch(doc, event)
    }, { capture: true, passive: true })
    doc.addEventListener('pointerdown', event => {
      this.claimReaderInteractiveContentTouch(doc, event)
    }, { capture: true, passive: true })
    doc.addEventListener('mousedown', event => {
      this.claimReaderInteractiveContentTouch(doc, event)
    }, { capture: true, passive: true })
    doc.addEventListener('contextmenu', async event => {
      await this.handleNativeTapZoneContentLongPress(doc, event, index, 'link-long-press')
    }, { capture: true })
    doc.addEventListener('click', async event => {
      if (event.defaultPrevented || event.button > 0) return
      if (this.suppressReaderNativeTapZoneContentActivation(doc, event, 'link-click')) return
      await this.activateReaderLinkFromEvent(doc, event, index, 'link')
    }, { capture: true })
  }

  async activateReaderLinkFromEvent(doc, event, index, source = 'link') {
      const anchor = closestElement(event.target, 'a[href]')
      if (!anchor) return false
      if (readerShouldSuppressMediaSyntheticClick(doc, event, anchor)) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
        log('link:media-synthetic-click-suppressed', describeUrl(anchor.getAttribute('href') || ''))
        readerTrace('link:media-synthetic-click-suppressed', {
          href: anchor.getAttribute('href') || '',
        })
        return true
      }
      const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
      if (mediaTapTarget) {
        const mediaSource = source === 'link-long-press' ? 'media-long-press' : 'media-anchor'
        this.rememberReaderContentActionTouch(doc, event, {
          kind: 'media',
          href: anchor?.getAttribute?.('href') || '',
          source: mediaSource,
        })
        post(this.readerContentActionClaimPayload(doc, event, {
          kind: 'media',
          href: anchor?.getAttribute?.('href') || '',
          source: mediaSource,
          anchor,
          mediaTapTarget,
        }))
        const toggled = this.toggleSepiaImageOverlayFromEvent(doc, event)
        if (!toggled) {
          event.preventDefault()
          event.stopPropagation()
          event.stopImmediatePropagation()
        }
        log('link:media-tap', mediaTapTarget.tagName || 'media', describeUrl(anchor.getAttribute('href') || ''))
        readerTrace('link:media-tap', {
          href: anchor.getAttribute('href') || '',
          tagName: mediaTapTarget.tagName || 'media',
          toggled,
        })
        return true
      }
      if (!readerPointInsideAnchorText(anchor, event)) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
        log('link:text-hit-miss', describeUrl(anchor.getAttribute('href') || ''))
        readerTrace('link:text-hit-miss', {
          href: anchor.getAttribute('href') || '',
        })
        return true
      }
      const rawHref = anchor.getAttribute('href')
      if (!rawHref) return false
      const section = this.view?.book?.sections?.[index]
      const href = section?.resolveHref?.(rawHref) ?? rawHref
      this.rememberReaderContentActionTouch(doc, event, {
        kind: 'link',
        href,
        source,
      })
      post(this.readerContentActionClaimPayload(doc, event, {
        kind: 'link',
        href,
        source,
        anchor,
      }))
      event.preventDefault()
      event.stopPropagation()
      try {
        if (this.view?.book?.isExternal?.(href)) {
          log('link:external', describeUrl(href))
          readerTrace('link:external', { href })
          globalThis.open?.(href, '_blank')
          return
        }
        log('link:navigate', href)
        readerTrace('link:navigate', { href })
        await this.goTo(href)
        return true
      } catch (error) {
        reportError(error, 'link_navigation_failed')
        return true
      }
  }

  classifyReaderLinks(doc) {
    if (!doc?.querySelectorAll) return
    for (const anchor of doc.querySelectorAll('a[href]')) {
      anchor.dataset.navicLinkKind = readerLinkHasMedia(anchor) ? 'media' : 'text'
    }
  }

  toggleSepiaImageOverlayFromEvent(doc, event, mediaTapTarget = null) {
    if (event.defaultPrevented || event.button > 0) return false
    if (readerThemeKey(this.readerSettings?.theme) !== ReaderThemeSepia) return false
    if (!mediaTapTarget) {
      const anchor = closestElement(event.target, 'a[href]')
      mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
    }
    const image = readerImageFromMediaTarget(mediaTapTarget)
    if (!image) return false
    this.rememberReaderContentActionTouch(doc, event, {
      kind: 'media',
      source: 'image',
    })
    post(this.readerContentActionClaimPayload(doc, event, {
      kind: 'image',
      source: 'image',
      image,
      mediaTapTarget,
    }))
    event.preventDefault?.()
    event.stopPropagation?.()
    event.stopImmediatePropagation?.()
    const disabled = image.dataset.navicSepiaOverlay === 'off'
    if (disabled) {
      delete image.dataset.navicSepiaOverlay
    } else {
      image.dataset.navicSepiaOverlay = 'off'
    }
    markReaderMediaTapHandled(doc, event, image || mediaTapTarget)
    log('image:sepia-overlay', disabled ? 'on' : 'off')
    readerTrace('image:sepia-overlay', {
      state: disabled ? 'on' : 'off',
      tagName: image.tagName || 'img',
    })
    return true
  }

  attachSepiaImageOverlayToggle(doc) {
    if (!doc?.defaultView || doc.defaultView.__navicSepiaImageOverlayToggleAttached) return
    doc.defaultView.__navicSepiaImageOverlayToggleAttached = true
    let touchState = null
    doc.addEventListener('touchstart', event => {
      const touch = event.changedTouches?.[0]
      if (!touch || event.touches?.length > 1) {
        touchState = null
        return
      }
      const anchor = closestElement(event.target, 'a[href]')
      this.claimReaderInteractiveContentTouch(doc, event)
      touchState = {
        target: event.target,
        x: touch.screenX ?? touch.clientX ?? 0,
        y: touch.screenY ?? touch.clientY ?? 0,
        clientX: touch.clientX,
        clientY: touch.clientY,
        mediaTapTarget: readerMediaTapTargetForEvent(doc, event, anchor),
      }
    }, { capture: true, passive: true })
    doc.addEventListener('touchend', event => {
      const state = touchState
      touchState = null
      if (!state || event.touches?.length > 0) return
      const touch = event.changedTouches?.[0]
      if (!touch) return
      const endX = touch.screenX ?? touch.clientX ?? state.x
      const endY = touch.screenY ?? touch.clientY ?? state.y
      if (Math.abs(endX - state.x) > CenterTapMovementSlop) return
      if (Math.abs(endY - state.y) > CenterTapMovementSlop) return
      const tapEvent = {
        defaultPrevented: event.defaultPrevented,
        button: 0,
        target: state.target || event.target,
        clientX: touch.clientX ?? state.clientX,
        clientY: touch.clientY ?? state.clientY,
        preventDefault: () => event.preventDefault(),
        stopPropagation: () => event.stopPropagation(),
        stopImmediatePropagation: () => event.stopImmediatePropagation(),
        timeStamp: event.timeStamp,
      }
      if (state.mediaTapTarget && this.suppressReaderNativeTapZoneContentActivation(doc, tapEvent, 'image-touchend')) return
      this.toggleSepiaImageOverlayFromEvent(doc, tapEvent, state.mediaTapTarget)
    }, { capture: true, passive: false })
    doc.addEventListener('touchcancel', () => {
      touchState = null
    }, { capture: true, passive: true })
    doc.addEventListener('contextmenu', event => {
      void this.handleNativeTapZoneContentLongPress(doc, event, null, 'image-long-press')
    }, { capture: true, passive: false })
    doc.addEventListener('click', event => {
      const anchor = closestElement(event.target, 'a[href]')
      const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
      if (mediaTapTarget && this.suppressReaderNativeTapZoneContentActivation(doc, event, 'image-click')) return
      const lastMediaTap = Number(doc.defaultView?.__navicLastMediaTapHandledAt || 0)
      const timestamp = event.timeStamp || performance.now()
      if (lastMediaTap && Math.abs(timestamp - lastMediaTap) < CenterTapSyntheticClickDedupeMs) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
        return
      }
      this.toggleSepiaImageOverlayFromEvent(doc, event, mediaTapTarget)
    }, { capture: true, passive: false })
  }

  effectiveReaderDirection() {
    if (this.readerDirectionModeValue === ReaderDirectionLtr || this.readerDirectionModeValue === ReaderDirectionRtl) {
      return this.readerDirectionModeValue
    }
    return this.view?.book?.dir === ReaderDirectionRtl ? ReaderDirectionRtl : ReaderDirectionLtr
  }

  turnScrolledEdgePage(deltaY) {
    const renderer = this.view?.renderer
    if (!renderer || !renderer.scrolled) return false
    const atStart = renderer.start <= ScrollEdgeTurnSlop
    const atEnd = renderer.viewSize - renderer.end <= ScrollEdgeTurnSlop
    if (deltaY > ScrollEdgeTurnSwipeThreshold && atStart) {
      log('page-turn:edge-swipe', 'previous', `start=${renderer.start}`)
      void this.previousPage()
      return true
    }
    if (deltaY < -ScrollEdgeTurnSwipeThreshold && atEnd) {
      log('page-turn:edge-swipe', 'next', `remaining=${renderer.viewSize - renderer.end}`)
      void this.nextPage()
      return true
    }
    return false
  }

  async turnFixedLayoutSwipePage(deltaX) {
    if (this.view?.isFixedLayout !== true) return false
    if (Math.abs(deltaX) < FixedLayoutSurfaceSwipeThreshold) return false
    const swipedLeft = deltaX < 0
    const rtl = this.effectiveReaderDirection() === ReaderDirectionRtl
    log('page-turn:fixed-swipe', swipedLeft ? 'left' : 'right')
    if (swipedLeft === rtl) {
      await this.previousPage()
    } else {
      await this.nextPage()
    }
    return true
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
    for (const doc of this.contentDocuments()) {
      for (const element of doc.querySelectorAll(`.${overlayClass}`)) {
        element.classList.remove(overlayClass)
      }
    }
  }

  fixedLayoutPagePosition(detail) {
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

  reflowableSectionPagePosition() {
    if (this.view?.isFixedLayout === true) return null
    const renderer = this.view?.renderer
    if (!renderer || renderer.scrolled) return null
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
    const pageCount = Math.max(1, Math.round(pages) - 1)
    return {
      pageIndex: Math.min(pageCount - 1, Math.max(0, Math.floor(page - 1))),
      pageCount,
      pageCountSource: 'section',
    }
  }

  reflowableLocationPagePosition(detail) {
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

  readerPageListPosition(detail) {
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

  readerPageListItems() {
    if (this.view?.isFixedLayout === true) return []
    return flattenReaderNavigationItems(this.view?.book?.pageList || [])
  }

  readerPageListPageCount() {
    const pageListItems = this.readerPageListItems()
    return pageListItems.length > 0 ? pageListItems.length : null
  }

  reflowableSectionSizes() {
    return Array.from(this.view?.book?.sections || []).map(section => {
      const size = section?.linear === 'no' ? 0 : Number(section?.size)
      return Number.isFinite(size) && size > 0 ? size : 0
    })
  }

  readerPaginationSectionHref(section, index) {
    return section?.href || section?.id || section?.url || section?.name || `section-${index}`
  }

  readerPaginationSectionTitle(section, index) {
    return section?.label || section?.title || section?.name || `Section ${index + 1}`
  }

  readerPaginationContentKey() {
    const sections = Array.from(this.view?.book?.sections || [])
    const sectionTokens = sections.map((section, index) => [
      index,
      this.readerPaginationSectionHref(section, index),
      section?.linear || '',
      Number(section?.size) || 0,
    ].join(':'))
    return stableHash(sectionTokens.join('|'))
  }

  readerPaginationRenderMetadata() {
    const viewport = readerViewportSize()
    const settings = this.readerSettings || {}
    const width = Number(viewport.width)
    const height = Number(viewport.height)
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
      fontSizePercent: settings.fontSizePercent ?? 100,
      lineHeight: settings.lineHeight ?? 1,
      paragraphSpacingPercent: settings.paragraphSpacingPercent ?? settings.paragraphSpacing ?? 0,
      marginPercent: settings.marginPercent ?? 0,
      publisherCss: readerFontSource(settings) === ReaderFontSourcePublisher ? 'publisher' : 'navic',
      direction: this.readerDirectionModeValue || readerDirectionMode(settings),
      runtimeVersion: 'navic-reader-pagination-profile-1',
    }
  }

  readerPaginationRenderFingerprint() {
    return readerPaginationFingerprint(this.readerPaginationRenderMetadata())
  }

  readerPaginationCacheKey(fingerprint) {
    return `navic-reader-pagination-profile:${fingerprint}`
  }

  readCachedPaginationProfile(fingerprint) {
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

  writeCachedPaginationProfile(profile) {
    if (!profile?.fingerprint) return
    try {
      window.localStorage?.setItem?.(this.readerPaginationCacheKey(profile.fingerprint), JSON.stringify(profile))
    } catch (error) {
      log('pagination-profile:cache-write-failed', error?.message || error)
    }
  }

  isCompletePaginationProfile(profile) {
    return Boolean(profile?.chapters?.length) && Number(profile?.estimatedChapterCount) === 0
  }

  observedChapterKey(index, section) {
    return `${Math.max(0, Math.floor(Number(index) || 0))}:${this.readerPaginationSectionHref(section, index)}`
  }

  hydrateObservedChapterPageCountsFromProfile(profile) {
    for (const entry of readerPaginationObservedChapterEntries(profile)) {
      this.observedChapterPageCounts.set(entry.key, entry.pageCount)
    }
  }

  paginationProfileObservedSignature(profile) {
    return readerPaginationObservedChapterEntries(profile)
      .map(entry => `${entry.key}:${entry.pageCount}`)
      .join('|')
  }

  postPaginationProfileStatus(status, payload = {}) {
    const message = {
      type: 'paginationProfileStatus',
      status,
      fingerprint: this.paginationFingerprint || payload.fingerprint || null,
      ...payload,
    }
    readerTrace('pagination-profile:status', message)
    post(message)
  }

  paginationProfileSectionPageCount(renderer) {
    let pages
    try {
      pages = Number(renderer?.pages)
    } catch {
      pages = null
    }
    if (!Number.isFinite(pages) || pages <= 1) return 1
    return Math.max(1, Math.round(pages) - 1)
  }

  async buildCompletePaginationProfileInProfilerView({ url, fingerprint, settings, token }) {
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

  async ensureCompletePaginationProfile(url = this.publicationUrl, settings = this.readerSettings) {
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

  shouldUseFreshPaginationProfile(freshProfile) {
    if (!freshProfile?.chapters?.length) return false
    if (!this.paginationProfile?.chapters?.length) return true
    if (freshProfile.fingerprint !== this.paginationProfile.fingerprint) return true
    const currentEstimatedCount = Math.max(0, Number(this.paginationProfile.estimatedChapterCount) || 0)
    if (currentEstimatedCount === 0) return false
    const freshObservedCount = Math.max(0, Number(freshProfile.observedChapterCount) || 0)
    const currentObservedCount = Math.max(0, Number(this.paginationProfile.observedChapterCount) || 0)
    if (freshObservedCount > currentObservedCount) return true
    if (freshObservedCount < currentObservedCount) return false
    return this.paginationProfileObservedSignature(freshProfile) !==
      this.paginationProfileObservedSignature(this.paginationProfile)
  }

  readerBuildPaginationProfileFromSectionPosition(detail, sectionPosition) {
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

  readerEnsurePaginationProfile(detail, sectionPosition) {
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

  readerPaginationProfilePosition(detail, sectionPosition = this.reflowableSectionPagePosition()) {
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

  reflowableStableBookPageModel(sectionIndex, sectionPosition, sectionSizes) {
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

  normalizedReflowablePagePosition(pagePosition, detail) {
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

  reflowableWholeBookPagePosition(detail) {
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

  reflowablePagePosition(detail) {
    const sectionPosition = this.reflowableSectionPagePosition()
    return this.readerPaginationProfilePosition(detail, sectionPosition) ||
      this.reflowableWholeBookPagePosition(detail) ||
      this.reflowableLocationPagePosition(detail) ||
      this.readerPageListPosition(detail) ||
      sectionPosition
  }

  readerPagePosition(detail) {
    return this.fixedLayoutPagePosition(detail) || this.reflowablePagePosition(detail)
  }

  chapterPagePosition(detail, fallback = null) {
    const pagePosition = this.view?.isFixedLayout === true
      ? this.fixedLayoutPagePosition(detail)
      : this.reflowableSectionPagePosition()
    const resolved = pagePosition || fallback
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

  detailSectionKey(detail) {
    const index = Number(detail?.section?.current ?? detail?.index)
    if (Number.isFinite(index)) return `index:${Math.floor(index)}`
    const href = detail?.href || detail?.tocItem?.href || detail?.section?.href || ''
    const comparableHref = readerHrefComparable(href)
    return comparableHref ? `href:${comparableHref}` : ''
  }

  committedPageTurnPosition(pagePosition, reason) {
    if (!pagePosition || !String(reason || '').startsWith('page-turn:')) return pagePosition
    if (pagePosition.pageCountSource === 'fixed-layout') return pagePosition
    const currentPageIndex = Number(this.currentPagePosition?.pageIndex)
    const candidatePageIndex = Number(pagePosition.pageIndex)
    const pageCount = readerPageNumberPageCount(pagePosition, this.currentPagePosition?.pageCount)
    if (!Number.isFinite(currentPageIndex) || !Number.isFinite(candidatePageIndex) || !Number.isFinite(pageCount) || pageCount <= 0) {
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

  passiveCommittedRelocationPosition(pagePosition, detail, reason) {
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

  readerPageNumberFontFamily(settings = this.readerSettings) {
    const configured = readerEffectiveFontFamily(settings)
    if (configured) return configured
    for (const doc of this.contentDocuments()) {
      const fontFamily = doc?.defaultView?.getComputedStyle?.(doc.body)?.fontFamily
      if (fontFamily && fontFamily !== 'initial') return fontFamily
    }
    return 'Georgia, serif'
  }

  updateReaderPageNumberLayer(pagePosition = this.currentPagePosition) {
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
      'font-family': 'var(--reader-page-number-font-family, Georgia, serif)',
      'font-size': '0.82rem',
      'font-style': 'normal',
      'font-weight': '400',
      'font-variant-numeric': 'oldstyle-nums tabular-nums',
      'line-height': '1',
      'letter-spacing': '0',
      background: 'transparent',
      border: '0',
      padding: '0',
      margin: '0',
    })
  }

  tryUpdateReaderPageNumberLayer(detail = this.lastRelocateDetail, fallback = this.currentPagePosition, reason = '') {
    try {
      if (String(reason || '') !== 'relocate-committed' && !String(reason || '').startsWith('page-turn:')) {
        this.recentPageTurnDirection = null
      }
      const candidatePagePosition = (detail ? this.readerPagePosition(detail) : null) || fallback
      const committedPagePosition = this.committedPageTurnPosition(candidatePagePosition, reason)
      const pagePosition = this.passiveCommittedRelocationPosition(committedPagePosition, detail, reason)
      this.updateReaderPageNumberLayer(pagePosition)
      return pagePosition || null
    } catch (error) {
      logError('page-number:update-failed', error?.message || error)
      return fallback || null
    }
  }

  scheduleReaderPageNumberRefresh(reason = 'deferred') {
    if (this.pageNumberRefreshScheduled) return
    this.pageNumberRefreshScheduled = true
    requestAnimationFrame(() => {
      this.pageNumberRefreshScheduled = false
      log('page-number:refresh', reason)
      this.tryUpdateReaderPageNumberLayer(this.lastRelocateDetail, this.currentPagePosition)
    })
  }

  renderTapZoneOverlayLayer() {
    const settings = this.readerSettings || {}
    if (settings.showTapZones !== true || this.readerTapZoneMode === ReaderTapZoneDisabled) {
      this.tapZoneOverlayLayer?.remove?.()
      this.tapZoneOverlayLayer = null
      return
    }
    this.tapZoneOverlayLayer = ensureTapZoneOverlayLayer()
    updateTapZoneOverlayLayer(
      this.tapZoneOverlayLayer,
      settings,
      this.readerTapZoneMode,
      this.smallerTapZone,
      this.readerFlowModeValue
    )
  }

  applySettings(settings) {
    settings = { ...this.readerSettings, ...settings }
    this.readerSettings = settings
    this.reflowableBookPageModel = null
    this.paginationProfile = null
    this.paginationFingerprint = null
    this.observedChapterPageCounts = new Map()
    const rootStyle = document.documentElement.style
    if (typeof settings.tapZone === 'string') this.readerTapZoneMode = settings.tapZone || ReaderTapZoneDefault
    this.smallerTapZone = settings.smallerTapZone === true
    this.nativeTapZones = settings.nativeTapZones === true
    if (settings.fontSizePercent) rootStyle.setProperty('--reader-font-size', `${settings.fontSizePercent}%`)
    if (settings.lineHeight) rootStyle.setProperty('--reader-line-height', String(settings.lineHeight))
    rootStyle.setProperty('--reader-page-number-font-family', this.readerPageNumberFontFamily(settings))
    rootStyle.setProperty('--reader-paragraph-spacing', readerParagraphSpacingEm(settings))
    const palette = readerThemePalette(settings.theme)
    rootStyle.setProperty('--reader-background', palette.background)
    rootStyle.setProperty('--reader-foreground', palette.foreground)
    rootStyle.setProperty('--reader-accent', palette.accent)
    rootStyle.setProperty('--theme-bg-color', palette.background)
    const flowMode = readerFlowMode(settings)
    this.readerFlowModeValue = flowMode
    rootStyle.setProperty('--reader-scroll-gap', flowMode === ReaderFlowScrolledGaps ? '1.25rem' : '0rem')
    this.view?.renderer?.setAttribute('flow', readerFoliateFlow(flowMode))
    this.readerDirectionModeValue = readerDirectionMode(settings)
    this.applyReaderDirection(this.readerDirectionModeValue)
    this.applyReaderViewportLayout('settings')
    this.view?.renderer?.setStyles?.(readerContentCss(settings))
    this.applyThemeToLoadedContent(settings)
    this.renderSurfacePaperTextureLayers()
    this.renderTapZoneOverlayLayer()
    if (this.shellCoverVisible && this.shellCoverLayer && this.shellCoverBlobUrl) {
      updateReaderShellCoverLayer(
        this.shellCoverLayer,
        this.shellCoverBlobUrl,
        settings,
        this.view?.book?.metadata?.title || ''
      )
    }
    this.scheduleReaderPageNumberRefresh('settings')
  }

  applyThemeToLoadedContent(settings = this.readerSettings) {
    for (const content of this.contentEntries()) {
      this.applyDocumentTheme(content.doc, settings, content.index)
    }
    this.applyRendererTheme(settings)
  }

  applyRendererTheme(settings = this.readerSettings) {
    const palette = readerThemePalette(settings?.theme)
    setStylesImportant(this.view, {
      background: palette.background,
      'background-color': palette.background,
      color: palette.foreground,
    })
    const renderer = this.view?.renderer
    setStylesImportant(renderer, {
      background: palette.background,
      'background-color': palette.background,
      color: palette.foreground,
    })
    const shadowRoot = renderer?.shadowRoot
    if (!shadowRoot) return
    for (const element of shadowRoot.querySelectorAll('#background, #background > *')) {
      setStylesImportant(element, {
        background: palette.background,
        'background-color': palette.background,
      })
    }
  }

  applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {
    if (!doc?.documentElement) return
    const palette = readerThemePalette(settings?.theme)
    const root = doc.documentElement
    const body = doc.body
    const styleHost = doc.head || root
    applyReaderParagraphSpacing(doc, settings)
    root.dataset.navicReaderTheme = readerThemeKey(settings?.theme)
    delete root.dataset.navicPaperTextureKey
    delete root.dataset.navicPaperTextureAsset
    let themeStyle = doc.getElementById(ReaderDocumentThemeStyleId)
    if (!themeStyle) {
      themeStyle = doc.createElement('style')
      themeStyle.id = ReaderDocumentThemeStyleId
      styleHost.append(themeStyle)
    }
    themeStyle.textContent = readerContentCss(settings)
    for (const element of [root, body].filter(Boolean)) {
      setStylesImportant(element, {
        '--reader-background': palette.background,
        '--reader-foreground': palette.foreground,
        '--reader-accent': palette.accent,
        '--theme-bg-color': palette.background,
        '--reader-paragraph-spacing': readerParagraphSpacingEm(settings),
        background: palette.background,
        'background-color': palette.background,
        'background-image': 'none',
        color: palette.foreground,
      })
    }
    for (const element of doc.querySelectorAll('[style*="background"], [bgcolor]')) {
      if (!element || element === root || element === body || isThemeBackgroundMediaElement(element)) continue
      if (element.hasAttribute('bgcolor')) {
        if (element.dataset.navicOriginalBgcolor === undefined) {
          element.dataset.navicOriginalBgcolor = element.getAttribute('bgcolor') || ''
        }
        element.removeAttribute('bgcolor')
      }
      setStylesImportant(element, {
        background: 'transparent',
        'background-color': 'transparent',
        'background-image': 'none',
      })
    }
    for (const element of doc.querySelectorAll('canvas, svg')) {
      setStylesImportant(element, {
        'background-color': 'transparent',
      })
    }
  }

  currentRendererContainerPosition() {
    const renderer = this.view?.renderer
    const position = Number(renderer?.containerPosition)
    return Number.isFinite(position) ? position : 0
  }

  surfacePaperTextureScrollOffset() {
    const renderer = this.view?.renderer
    if (!renderer) {
      this.surfaceTextureScrollOffset = { x: 0, y: 0 }
      return this.surfaceTextureScrollOffset
    }
    const position = Number(renderer.containerPosition)
    if (renderer.scrolled || !Number.isFinite(position)) {
      this.surfaceTextureScrollOffset = { x: 0, y: 0 }
      return this.surfaceTextureScrollOffset
    }
    const { width, height } = readerViewportSize()
    this.surfaceTextureScrollOffset = readerSurfacePaperTextureScrollOffset({
      position,
      baseOffset: this.surfacePaperTextureBaseOffset,
      viewportWidth: width,
      viewportHeight: height,
      flowMode: this.readerFlowModeValue,
      pageTurnDirection: this.surfacePaperTextureTurnDirection || this.pageTurnDirection,
    })
    return this.surfaceTextureScrollOffset
  }

  attachSurfacePaperTextureDragDirection(doc) {
    if (!doc?.defaultView || doc.defaultView.__navicSurfacePaperTextureDragDirectionAttached) return
    doc.defaultView.__navicSurfacePaperTextureDragDirectionAttached = true
    let touchState = null
    doc.addEventListener('touchstart', event => {
      const touch = event.changedTouches?.[0]
      if (!touch || event.touches?.length > 1) {
        touchState = null
        return
      }
      touchState = {
        x: touch.screenX ?? touch.clientX ?? 0,
        y: touch.screenY ?? touch.clientY ?? 0,
      }
    }, { capture: true, passive: true })
    doc.addEventListener('touchmove', event => {
      if (!touchState || event.touches?.length > 1) {
        touchState = null
        return
      }
      const touch = event.changedTouches?.[0]
      if (!touch) return
      const direction = readerPaperTextureDragDirection({
        deltaX: (touch.screenX ?? touch.clientX ?? touchState.x) - touchState.x,
        deltaY: (touch.screenY ?? touch.clientY ?? touchState.y) - touchState.y,
        flowMode: this.readerFlowModeValue,
      })
      if (!direction) return
      this.surfacePaperTextureTurnDirection = direction
      readerTrace('texture:drag-direction', { direction })
    }, { capture: true, passive: true })
    doc.addEventListener('touchend', () => {
      touchState = null
    }, { capture: true, passive: true })
    doc.addEventListener('touchcancel', () => {
      touchState = null
    }, { capture: true, passive: true })
  }

  surfacePaperTextureDiagnosticState(reason = 'scroll') {
    const position = this.currentRendererContainerPosition()
    const pageIndex = Number(this.currentPagePosition?.pageIndex)
    const pageCount = Number(this.currentPagePosition?.pageCount)
    const detail = this.lastRelocateDetail || {}
    return {
      reason,
      offset: this.surfaceTextureScrollOffset || { x: 0, y: 0 },
      position: this.currentRendererContainerPosition(),
      baseOffset: this.surfacePaperTextureBaseOffset,
      delta: position - this.surfacePaperTextureBaseOffset,
      pageTurnDirection: this.surfacePaperTextureTurnDirection || this.pageTurnDirection || '',
      flowMode: this.readerFlowModeValue,
      pageIndex: Number.isFinite(pageIndex) ? pageIndex : null,
      pageCount: Number.isFinite(pageCount) ? pageCount : null,
      href: detail.href || this.sectionHrefForDetail(detail) || '',
      textureKey: readerRoot.dataset.navicSurfacePaperTextureKey || '',
    }
  }

  attachSurfacePaperTextureScrollSync() {
    const renderer = this.view?.renderer
    if (!renderer || renderer === this.surfacePaperTextureScrollRenderer) return
    this.detachSurfacePaperTextureScrollSync()
    this.surfacePaperTextureScrollRenderer = renderer
    this.surfacePaperTextureScrollListener = () => this.syncSurfacePaperTextureScrollOffset('scroll')
    renderer.addEventListener('scroll', this.surfacePaperTextureScrollListener, { passive: true })
  }

  detachSurfacePaperTextureScrollSync() {
    if (this.surfacePaperTextureScrollRenderer && this.surfacePaperTextureScrollListener) {
      this.surfacePaperTextureScrollRenderer.removeEventListener('scroll', this.surfacePaperTextureScrollListener)
    }
    this.surfacePaperTextureScrollRenderer = null
    this.surfacePaperTextureScrollListener = null
  }

  syncSurfacePaperTextureScrollOffset(reason = 'scroll') {
    if (!this.surfaceTextureVariant && !this.surfaceBorderOverlayVariant) return
    this.renderSurfacePaperTextureLayers()
    const offset = this.surfaceTextureScrollOffset
    if (Math.abs(offset.x || 0) > 1 || Math.abs(offset.y || 0) > 1) {
      const diagnostic = this.surfacePaperTextureDiagnosticState(reason)
      log(
        'surface-texture-scroll',
        reason,
        `x=${Math.round(offset.x || 0)}`,
        `y=${Math.round(offset.y || 0)}`,
        `pos=${Math.round(diagnostic.position)}`,
        `base=${Math.round(diagnostic.baseOffset)}`,
        `delta=${Math.round(diagnostic.delta)}`,
        `dir=${diagnostic.pageTurnDirection || 'none'}`,
        `page=${diagnostic.pageIndex ?? ''}/${diagnostic.pageCount ?? ''}`,
        `href=${diagnostic.href}`
      )
      readerTrace('texture:scroll', diagnostic)
    }
  }

  renderSurfacePaperTextureLayers() {
    if (!this.surfaceTextureVariant && !this.surfaceBorderOverlayVariant) return
    const scrollOffset = this.surfacePaperTextureScrollOffset()
    if (this.surfaceTextureVariant) {
      this.surfaceTextureLayer = this.surfaceTextureLayer && readerRoot.contains(this.surfaceTextureLayer)
        ? this.surfaceTextureLayer
        : ensureReaderSurfaceTextureLayer()
      updateReaderSurfaceTextureLayer(
        this.surfaceTextureLayer,
        this.surfaceTextureVariant,
        this.readerSettings,
        scrollOffset
      )
    }
    if (this.surfaceBorderOverlayVariant) {
      this.surfaceBorderOverlayLayer = this.surfaceBorderOverlayLayer && readerRoot.contains(this.surfaceBorderOverlayLayer)
        ? this.surfaceBorderOverlayLayer
        : ensureReaderSurfaceBorderOverlayLayer()
      updateReaderSurfaceBorderOverlayLayer(
        this.surfaceBorderOverlayLayer,
        this.surfaceBorderOverlayVariant,
        this.readerSettings,
        scrollOffset
      )
    }
  }

  surfacePaperTextureIndex(detail = {}) {
    const detailIndex = Number(detail?.index)
    if (Number.isFinite(detailIndex)) return Math.floor(detailIndex)
    const fixedLayoutIndex = this.fixedLayoutCurrentPageIndex()
    if (Number.isFinite(fixedLayoutIndex)) return fixedLayoutIndex
    const entry = this.contentEntries(detail).find(content => Number.isFinite(Number(content.index)))
    const entryIndex = Number(entry?.index)
    return Number.isFinite(entryIndex) ? Math.floor(entryIndex) : 0
  }

  updateSurfacePaperTexture(detail = {}, pagePosition = null) {
    const index = this.surfacePaperTextureIndex(detail)
    const section = this.view?.book?.sections?.[index]
    const textureDetail = pagePosition
      ? { ...detail, pageIndex: pagePosition.pageIndex, pageCount: pagePosition.pageCount }
      : detail
    const textureKey = readerPaperTextureVariantKey(this.publicationUrl, section, index, textureDetail)
    const textureVariant = readerPaperTextureVariantForPage(textureKey)
    const borderOverlayVariant = readerPageBorderOverlayVariantForPage(textureKey)
    this.surfaceTextureVariant = textureVariant
    this.surfaceBorderOverlayVariant = borderOverlayVariant
    this.surfaceTextureLayer = this.surfaceTextureLayer && readerRoot.contains(this.surfaceTextureLayer)
      ? this.surfaceTextureLayer
      : ensureReaderSurfaceTextureLayer()
    this.surfaceBorderOverlayLayer = this.surfaceBorderOverlayLayer && readerRoot.contains(this.surfaceBorderOverlayLayer)
      ? this.surfaceBorderOverlayLayer
      : ensureReaderSurfaceBorderOverlayLayer()
    this.surfacePaperTextureBaseOffset = this.currentRendererContainerPosition()
    this.surfaceTextureScrollOffset = { x: 0, y: 0 }
    readerRoot.dataset.navicSurfacePaperTextureKey = textureKey
    readerRoot.dataset.navicSurfacePaperTextureAsset = textureVariant.asset
    readerRoot.dataset.navicSurfacePageBorderOverlayAsset = borderOverlayVariant.asset
    readerRoot.dataset.navicSurfaceBorderOverlayAsset = borderOverlayVariant.asset
    const diagnostic = this.surfacePaperTextureDiagnosticState('update')
    log(
      'surface-texture-update',
      `page=${diagnostic.pageIndex ?? ''}/${diagnostic.pageCount ?? ''}`,
      `href=${diagnostic.href}`,
      `pos=${Math.round(diagnostic.position)}`,
      `base=${Math.round(diagnostic.baseOffset)}`,
      `key=${textureKey}`
    )
    readerTrace('texture:update', {
      ...diagnostic,
      index,
      key: textureKey,
      baseAsset: textureVariant.asset,
      borderAsset: borderOverlayVariant.asset,
    })
    this.renderSurfacePaperTextureLayers()
    this.surfacePaperTextureTurnDirection = null
  }

  applyReaderDirection(direction, rerender = true) {
    const normalized = direction === ReaderDirectionLtr || direction === ReaderDirectionRtl
      ? direction
      : ReaderDirectionDefault
    if (this.view?.book) {
      if (this.originalBookDir === null) this.originalBookDir = this.view.book.dir || ''
      this.view.book.dir = normalized === ReaderDirectionDefault ? this.originalBookDir : normalized
    }
    for (const doc of this.contentDocuments()) {
      this.applyDocumentDirection(doc, normalized)
    }
    if (rerender) this.view?.renderer?.render?.()
  }

  applyDocumentDirection(doc, direction) {
    if (!doc) return
    for (const element of [doc.documentElement, doc.body].filter(Boolean)) {
      if (direction === ReaderDirectionDefault) {
        if (element.dataset.navicOriginalDir !== undefined) {
          const original = element.dataset.navicOriginalDir
          if (original) element.setAttribute('dir', original)
          else element.removeAttribute('dir')
          delete element.dataset.navicOriginalDir
        }
      } else {
        if (element.dataset.navicOriginalDir === undefined) {
          element.dataset.navicOriginalDir = element.getAttribute('dir') || ''
        }
        element.setAttribute('dir', direction)
      }
    }
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

  postCurrentLocationSnapshot(reason = 'snapshot') {
    const detail = this.lastRelocateDetail || this.currentFixedLayoutLocationDetail()
    if (!detail) {
      log('location-snapshot:missing', reason)
      return
    }
    log('location-snapshot', reason)
    this.postLocationChanged(detail, reason)
  }

  postLocationChanged(detail, reason = 'relocate') {
    if (this.detailTargetsCover(detail) && this.hasNonCoverReadableContent()) {
      this.updateReaderPageNumberLayer(null)
      log('location-changed:cover-skipped', reason)
      readerTrace('location:cover-skipped', { reason, detail })
      return
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
      ...pageModelDiagnostics,
    }
    const locationKey = readerLocationPostKey(message)
    if (locationKey === this.lastPostedLocationKey) {
      log('location-changed:duplicate-skipped', reason)
      readerTrace('location:duplicate-skipped', { reason, message })
      return
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
  }

  onRelocate(detail) {
    readerTrace('relocate:raw', detail)
    this.lastRelocateDetail = detail
    if (this.pageTurnInProgress || this.pageTurnPromise) return
    this.scheduleCommittedRelocation(detail)
  }

  cancelPendingCommittedRelocation() {
    this.pendingRelocateDetail = null
    this.pendingRelocateReason = 'relocate-committed'
    this.relocatePostScheduled = false
    if (this.relocatePostTimer != null) {
      clearTimeout(this.relocatePostTimer)
      this.relocatePostTimer = null
    }
  }

  scheduleCommittedRelocation(detail, reason = 'relocate-committed') {
    if (!detail) return
    this.pendingRelocateDetail = detail
    this.pendingRelocateReason = reason
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
        post({ type: 'selectionChanged' })
        return
      }
      const range = selection.rangeCount ? selection.getRangeAt(0) : null
      const cfi = range && Number.isFinite(index)
        ? this.view?.getCFI?.(index, range)
        : undefined
      post({
        type: 'selectionChanged',
        text,
        cfi,
        href: this.view?.book?.sections?.[index]?.href,
      })
    })
  }

  onLoad(detail = {}) {
    this.applyReaderViewportLayout('load')
    this.applyReaderDirection(this.readerDirectionModeValue, false)
    if (detail.doc) log('load:event-doc', `index=${detail.index ?? 'unknown'}`)
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
