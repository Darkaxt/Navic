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
const ReaderPdfFitWidth = 'width'
const ReaderPdfFitPage = 'page'
const ReaderPdfFitHeight = 'height'
const ReaderPdfFitOriginal = 'original'
const ReaderPdfPageGapMaxPercent = 48

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
  tapZoneOverlayLayer = null
  pageNumberLayer = null
  shellCoverLayer = null
  shellCoverBlobUrl = null
  shellCoverVisible = false
  externalShellCover = false
  shellCoverHideTimer = null
  pageNumberRefreshScheduled = false
  currentPagePosition = null
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
  fixedLayoutNavigationPageIndex = null
  fixedLayoutNavigationDirection = null
  suppressedCoverSectionIndexes = new Set()
  embeddedCoverSuppressedSectionIndexes = new Set()
  embeddedCoverRerenderScheduled = false
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
      this.applyReaderViewportLayout('view-created')
      await this.view.open(url)
      this.attachSurfacePaperTextureScrollSync()
      this.applyReaderViewportLayout('view-opened')
      log('openPublication:view-opened', describeUrl(url))
      if (settings) this.applySettings(settings)
      const startLocatorIsShellCover = this.startLocatorTargetsShellCover(startLocator)
      const shellCoverUrl = this.externalShellCover ? null : await this.loadShellCover()
      const hasShellCoverSurface = this.externalShellCover || Boolean(shellCoverUrl)
      this.postToc()
      const locator = startLocator?.cfi || startLocator?.href
      const progress = Number(startLocator?.progress)
      if (startLocatorIsShellCover) {
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
      this.attachSurfaceTapGesture(this.view)
      this.attachReaderTapZoneGesture(this.view)
      if (shellCoverUrl) this.showShellCover()
      log('openPublication:ready', describeUrl(url))
      this.applyReaderViewportLayout('ready')
      this.logContentLayout('ready')
      post({ type: 'ready' })
      post({ type: 'publicationReady' })
      if (readerStartLocatorHasPosition(startLocator) && !startLocatorIsShellCover) {
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
    this.fixedLayoutNavigationPageIndex = null
    this.fixedLayoutNavigationDirection = null
    this.suppressedCoverSectionIndexes = new Set()
    this.embeddedCoverSuppressedSectionIndexes = new Set()
    this.embeddedCoverRerenderScheduled = false
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
      this.applyReaderViewportLayout('progress-seek')
      requestAnimationFrame(() => {
        this.logContentLayout('progress-seek')
        log('progress-seek:done', fraction)
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
      this.updateTapZoneOverlayLayer()
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
    doc.addEventListener('click', async event => {
      if (event.defaultPrevented || event.button > 0) return
      const anchor = closestElement(event.target, 'a[href]')
      if (!anchor) return
      if (readerShouldSuppressMediaSyntheticClick(doc, event, anchor)) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
        log('link:media-synthetic-click-suppressed', describeUrl(anchor.getAttribute('href') || ''))
        readerTrace('link:media-synthetic-click-suppressed', {
          href: anchor.getAttribute('href') || '',
        })
        return
      }
      const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
      if (mediaTapTarget) {
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
        return
      }
      if (!readerPointInsideAnchorText(anchor, event)) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
        log('link:text-hit-miss', describeUrl(anchor.getAttribute('href') || ''))
        readerTrace('link:text-hit-miss', {
          href: anchor.getAttribute('href') || '',
        })
        return
      }
      const rawHref = anchor.getAttribute('href')
      if (!rawHref) return
      const section = this.view?.book?.sections?.[index]
      const href = section?.resolveHref?.(rawHref) ?? rawHref
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
      } catch (error) {
        reportError(error, 'link_navigation_failed')
      }
    }, { capture: true })
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
      this.toggleSepiaImageOverlayFromEvent(doc, tapEvent, state.mediaTapTarget)
    }, { capture: true, passive: false })
    doc.addEventListener('touchcancel', () => {
      touchState = null
    }, { capture: true, passive: true })
    doc.addEventListener('click', event => {
      const lastMediaTap = Number(doc.defaultView?.__navicLastMediaTapHandledAt || 0)
      const timestamp = event.timeStamp || performance.now()
      if (lastMediaTap && Math.abs(timestamp - lastMediaTap) < CenterTapSyntheticClickDedupeMs) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
        return
      }
      this.toggleSepiaImageOverlayFromEvent(doc, event)
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
    return this.reflowableWholeBookPagePosition(detail) || this.reflowableLocationPagePosition(detail) || this.readerPageListPosition(detail) || this.reflowableSectionPagePosition()
  }

  readerPagePosition(detail) {
    return this.fixedLayoutPagePosition(detail) || this.reflowablePagePosition(detail)
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
      const candidatePagePosition = (detail ? this.readerPagePosition(detail) : null) || fallback
      const pagePosition = this.committedPageTurnPosition(candidatePagePosition, reason)
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
    const delta = position - this.surfacePaperTextureBaseOffset
    const { width, height } = readerViewportSize()
    const maxOffset = this.readerFlowModeValue === ReaderFlowPagedVertical ? height : width
    const wrapsForwardBoundary = this.pageTurnDirection === 'next' && delta < -maxOffset * 0.5
    const wrapsBackwardBoundary = this.pageTurnDirection === 'previous' && delta > maxOffset * 0.5
    const bounded = wrapsForwardBoundary || wrapsBackwardBoundary
      ? 0
      : Math.max(-maxOffset, Math.min(maxOffset, delta))
    this.surfaceTextureScrollOffset = this.readerFlowModeValue === ReaderFlowPagedVertical
      ? { x: 0, y: -bounded }
      : { x: -bounded, y: 0 }
    return this.surfaceTextureScrollOffset
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
      log('surface-texture-scroll', reason, `x=${Math.round(offset.x || 0)}`, `y=${Math.round(offset.y || 0)}`)
      readerTrace('texture:scroll', { reason, offset })
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
    readerTrace('texture:update', {
      index,
      key: textureKey,
      baseAsset: textureVariant.asset,
      borderAsset: borderOverlayVariant.asset,
      baseOffset: this.surfacePaperTextureBaseOffset,
      scrollOffset: this.surfaceTextureScrollOffset,
    })
    this.renderSurfacePaperTextureLayers()
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
    const message = {
      type: 'locationChanged',
      href: detail.href || sectionHref || tocItem.href,
      cfi: detail.cfi,
      progress: optionalNumber(detail.fraction ?? detail.progress ?? detail.totalProgress),
      pageIndex: pagePosition?.pageIndex,
      pageCount: pagePosition?.pageCount,
      tocTitle: tocItem.label || tocItem.title,
    }
    const locationKey = readerLocationPostKey(message)
    if (locationKey === this.lastPostedLocationKey) {
      log('location-changed:duplicate-skipped', reason)
      readerTrace('location:duplicate-skipped', { reason, message })
      return
    }
    this.updateSurfacePaperTexture(detail, pagePosition)
    this.lastPostedLocationKey = locationKey
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
  postOverlayFragmentActive: fragment => post({ type: 'overlayFragmentActive', ...fragment }),
  postOverlayFragmentInactive: fragmentId => post({ type: 'overlayFragmentInactive', fragmentId }),
}

readerTrace('runtime:ready', { engine: 'foliate-js' })
log('module-loaded')
post({ type: 'ready' })
