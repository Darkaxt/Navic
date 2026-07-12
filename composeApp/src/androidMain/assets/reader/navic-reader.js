// Adapted from Anx Reader: tmp/references/anx-reader/lib/page/book_player/epub_player.dart:627-879
// (callback catalog, including translateText at 864)
// tmp/references/anx-reader/assets/foliate-js/src/view.js:115-194 (relocation)
// :216-327 (link/image taxonomy)
// :335-397 (annotations)

import './vendor/foliate-js/view.js'
import './navic-reader-motion.js'
import { Overlayer } from './vendor/foliate-js/overlayer.js'
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
  ReaderDragAnimationNone,
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
  readerPaperLayoutProfile,
  readerSurfacePageDecorationGeometry,
  readerSurfaceSpreadMode,
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
  readerMediaOverlayTextEntries,
  readerMediaOverlayTextPoint,
  readerMediaOverlayNormalizedTextMap,
  readerMediaOverlayRawOffsetForNormalizedOffset,
  readerMediaOverlayClampRangeBeforeNextCue,
  readerMediaOverlayResolvedTextRange,
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
import { NavicReaderShellCoverMethods } from './navic-reader-shell-cover.js'
import { NavicReaderViewportMethods } from './navic-reader-viewport.js'
import { NavicReaderLocationMethods } from './navic-reader-location.js'

const ReaderSvgNamespace = 'http://www.w3.org/2000/svg'
const ReaderMediaOverlayRangeAttribute = 'data-navic-media-overlay-range'
const ReaderMediaOverlayActiveRangeKey = 'navic-media-overlay-active'
const ReaderMediaOverlayPlayedRangeKeyPrefix = 'navic-media-overlay-played-'

const readerCssColorFromArgb = (argb, fallback) => {
  const value = Number(argb)
  if (!Number.isFinite(value)) return fallback
  const unsigned = value >>> 0
  const alpha = ((unsigned >>> 24) & 255) / 255
  const red = (unsigned >>> 16) & 255
  const green = (unsigned >>> 8) & 255
  const blue = unsigned & 255
  return `rgba(${red}, ${green}, ${blue}, ${Math.round(alpha * 1000) / 1000})`
}

const readerMediaOverlayUnwrapRangeMarker = marker => {
  const parent = marker?.parentNode
  if (!parent) return false
  while (marker.firstChild) {
    parent.insertBefore(marker.firstChild, marker)
  }
  parent.removeChild(marker)
  parent.normalize?.()
  return true
}

const readerDrawMediaOverlaySelection = (rects, options = {}) => {
  const { color = 'red', padding = 0 } = options
  const group = document.createElementNS(ReaderSvgNamespace, 'g')
  group.setAttribute('fill', color)
  group.style.mixBlendMode = 'var(--overlayer-highlight-blend-mode, normal)'
  for (const { left, top, height, width } of rects) {
    const element = document.createElementNS(ReaderSvgNamespace, 'rect')
    element.setAttribute('x', left - padding)
    element.setAttribute('y', top - padding)
    element.setAttribute('height', height + padding * 2)
    element.setAttribute('width', width + padding * 2)
    group.append(element)
  }
  return group
}

const readerDrawMediaOverlayMarker = (rects, options = {}) => {
  const { color = 'red', padding = 0 } = options
  const group = document.createElementNS(ReaderSvgNamespace, 'g')
  group.setAttribute('fill', color)
  group.style.mixBlendMode = 'var(--overlayer-highlight-blend-mode, normal)'
  for (const { left, top, height, width } of rects) {
    const markerTop = top - padding
    const markerLeft = left - padding
    const markerWidth = width + padding * 2
    const markerHeight = height + padding * 2
    const slant = Math.min(Math.max(markerHeight * 0.36, 2), 12)
    const element = document.createElementNS(ReaderSvgNamespace, 'polygon')
    element.setAttribute(
      'points',
      [
        `${markerLeft + slant},${markerTop}`,
        `${markerLeft + markerWidth},${markerTop}`,
        `${markerLeft + markerWidth - slant},${markerTop + markerHeight}`,
        `${markerLeft},${markerTop + markerHeight}`,
      ].join(' ')
    )
    group.append(element)
  }
  return group
}

const readerDrawNoteAnnotation = (rects, options = {}) => {
  const group = document.createElementNS(ReaderSvgNamespace, 'g')
  group.setAttribute('data-navic-note-annotation', 'true')
  group.append(
    Overlayer.highlight(rects, options),
    Overlayer.squiggly(rects, {
      color: options.noteColor || options.color || '#b86e00',
      width: 1.6,
      padding: 2,
      writingMode: options.writingMode,
    })
  )
  return group
}

const readerCssColorToArgb = color => {
  const normalized = String(color || '').trim()
  const rgb = /^#[0-9a-f]{6}$/i.test(normalized)
    ? parseInt(normalized.slice(1), 16)
    : /^#[0-9a-f]{3}$/i.test(normalized)
      ? parseInt(normalized.slice(1).split('').map(value => `${value}${value}`).join(''), 16)
      : 0xead9ae
  return (0xff000000 | rgb) >>> 0
}

class NavicReaderRuntime {
  view = null
  mediaOverlayEnabled = false
  readerSettings = {}
  readerTapZoneMode = ReaderTapZoneDefault
  readerFlowModeValue = ReaderFlowPaged
  readerDragAnimationModeValue = ReaderDragAnimationNone
  readerDirectionModeValue = ReaderDirectionDefault
  smallerTapZone = false
  nativeTapZones = false
  originalBookDir = null
  publicationUrl = ''
  surfaceTextureLayer = null
  surfaceBorderOverlayLayer = null
  surfaceStainOverlayLayer = null
  surfaceSpreadGutterOverlayLayer = null
  movingPageTextureLayer = null
  movingPageBorderOverlayLayer = null
  movingPageStainOverlayLayer = null
  movingPageSpreadGutterOverlayLayer = null
  surfaceTextureVariant = null
  surfaceBorderOverlayVariant = null
  surfaceStainOverlayVariant = null
  surfaceSpreadGutterOverlayVariant = null
  surfaceSpreadMode = 'single'
  surfacePageDecorationGeometry = null
  surfaceTextureSlots = []
  surfaceBorderOverlaySlots = []
  surfaceStainOverlaySlots = []
  surfaceSpreadGutterOverlaySlots = []
  surfacePaperTextureBaseOffset = 0
  surfaceTextureScrollOffset = { x: 0, y: 0 }
  // Live lateral drag: the paper texture + border-overlay shadow ride the exact
  // accumulated gesture delta that drives renderer.scrollBy(), instead of the
  // rescaled/sign-overwritten heuristic used for animated page turns. See
  // surfacePaperTextureScrollOffset() in navic-reader-appearance.js.
  surfaceLiveDragActive = false
  surfaceLiveDragOffset = { x: 0, y: 0 }
  surfacePaperTextureScrollRenderer = null
  surfacePaperTextureScrollListener = null
  surfacePaperTextureMotionFrame = null
  surfacePaperTextureMotionSyncActive = false
  surfacePaperTextureTurnDirection = null
  surfacePaperTextureFallbackDirection = null
  surfacePaperTextureDeferredFrame = null
  pendingSurfacePaperTextureUpdate = null
  tapZoneOverlayLayer = null
  pageNumberLayer = null
  shellCoverLayer = null
  pageDragPreviewLayer = null
  pageDragPreviewFrame = null
  pageDragPreviewTargetKey = ''
  pageDragPreviewReadyKey = ''
  pageDragPreviewLoadToken = 0
  pendingPageDragPreviewCommand = null
  shellCoverBlobUrl = null
  shellCoverDominantColor = null
  shellCoverVisible = false
  externalShellCover = false
  suppressWebShellCover = false
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
  lastPostedVisibleTextRangeKey = null
  lastMediaOverlayRangeDiagnosticKey = null
  mediaOverlayActiveFragment = null
  mediaOverlayPlayedFragments = new Map()
  mediaOverlayProgressAnimationFrame = null
  mediaOverlayProgressAnimationToken = 0
  mediaOverlayProgressAnimationKey = ''
  mediaOverlayProgressDisplayKey = ''
  mediaOverlayProgressDisplayedFraction = 0
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
  pendingNativePageTurnSettleToken = null
  nativePageTurnSettledToken = null
  pendingExactPageTurnSettlement = null
  nativePageTurnSettledState = null
  deferredReflowablePageTurn = null
  deferredReflowablePageTurnToken = 0
  recentPageTurnDirection = null
  pageTurnTargetPageIndex = null
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
        return this.goToChapterProgress(
          command.href,
          command.progress,
          command.chapterPageIndex,
          command.chapterPageCount
        )
      case 'goToVisualPage':
        return this.goToVisualPage(command.pageIndex, command.settleToken)
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
        return this.handleNativeTapZoneContentLongPressAt(
          command.x,
          command.y,
          command.viewWidth,
          command.viewHeight,
          'native-long-press-command',
          command.selectText !== false,
        )
      case 'applyHighlight':
        return this.applyHighlight(command)
      case 'applyHighlights':
        return this.applyHighlights(command.highlights || [])
      case 'applyOverlayFragment':
        return this.applyOverlayFragment(command.fragment || command)
      case 'updateOverlayFragmentProgress':
        return this.updateOverlayFragmentProgress(command.fragment || command)
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

  async openPublication({
    url,
    mediaOverlayEnabled = false,
    externalShellCover = false,
    suppressWebShellCover = false,
    nativeShellCoverTint = null,
    startLocator = null,
    settings = null
  }) {
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
      this.suppressWebShellCover = Boolean(suppressWebShellCover)
      this.shellCoverDominantColor = /^#[0-9a-f]{6}$/i.test(String(nativeShellCoverTint || '').trim())
        ? String(nativeShellCoverTint).trim().toLowerCase()
        : null
      if (this.shellCoverDominantColor) {
        readerRoot.dataset.navicShellCoverDominantColor = this.shellCoverDominantColor
      } else {
        delete readerRoot.dataset.navicShellCoverDominantColor
      }
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
      const shellCoverAllowed = this.view?.isFixedLayout !== true
      const shellCoverUrl = this.externalShellCover || this.suppressWebShellCover ? null : shellCoverAllowed ? await this.loadShellCover() : null
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
      if (shellCoverAllowed && shellCoverUrl) this.showShellCover()
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
    const annotation = detail.annotation || {}
    const color = annotation.color || detail.color || '#f4d35e'
    const hasNote = String(annotation.note || '').trim().length > 0
    try {
      detail.draw?.(hasNote ? readerDrawNoteAnnotation : Overlayer.highlight, {
        color,
        noteColor: color,
        writingMode: this.view?.renderer?.writingMode,
      })
    } catch (error) {
      logError('annotation:draw-failed', error?.message || String(error))
    }
    const index = Number(detail.index)
    post({
      type: 'annotationDrawn',
      value: detail.value || annotation.value || '',
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
    this.clearDeferredReflowablePageTurn()
    this.view?.close?.()
    this.view?.remove?.()
    this.view = null
    this.readerSettings = {}
    this.nativeTapZones = false
    this.originalBookDir = null
    this.publicationUrl = ''
    this.externalShellCover = false
    this.suppressWebShellCover = false
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
    this.surfaceStainOverlayLayer?.remove?.()
    this.surfaceStainOverlayLayer = null
    this.surfaceSpreadGutterOverlayLayer?.remove?.()
    this.surfaceSpreadGutterOverlayLayer = null
    this.movingPageTextureLayer?.remove?.()
    this.movingPageTextureLayer = null
    this.movingPageBorderOverlayLayer?.remove?.()
    this.movingPageBorderOverlayLayer = null
    this.movingPageStainOverlayLayer?.remove?.()
    this.movingPageStainOverlayLayer = null
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
    this.lastPostedVisibleTextRangeKey = null
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
    this.surfaceStainOverlayVariant = null
    this.surfaceSpreadGutterOverlayVariant = null
    this.surfaceSpreadMode = 'single'
    this.surfacePageDecorationGeometry = null
    this.surfaceTextureSlots = []
    this.surfaceBorderOverlaySlots = []
    this.surfaceStainOverlaySlots = []
    this.surfaceSpreadGutterOverlaySlots = []
    this.movingPageSpreadGutterOverlayLayer?.remove?.()
    this.movingPageSpreadGutterOverlayLayer = null
    this.surfacePaperTextureBaseOffset = 0
    this.surfaceTextureScrollOffset = { x: 0, y: 0 }
    this.surfaceLiveDragActive = false
    this.surfaceLiveDragOffset = { x: 0, y: 0 }
    this.stopSurfacePaperTextureMotionSync?.('runtime-reset')
    this.surfacePaperTextureMotionFrame = null
    this.surfacePaperTextureMotionSyncActive = false
    this.surfacePaperTextureTurnDirection = null
    this.surfacePaperTextureFallbackDirection = null
    this.lastRelocateDetail = null
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

  async goTo(locator, reason = 'go-to') {
    if (!this.view || !locator) return
    try {
      const navigationTarget = await this.resolveReaderNavigationTarget(locator)
      if (!navigationTarget) return
      if (navigationTarget.rendererTarget && this.view.renderer?.goTo) {
        log('go-to:resolved', navigationTarget.target, `index=${navigationTarget.rendererTarget.index}`)
        this.beginControlledRelocation(reason)
        await this.view.renderer.goTo(navigationTarget.rendererTarget)
        this.view.history?.pushState?.(navigationTarget.target)
      } else {
        log('go-to:fallback', navigationTarget.target)
        this.beginControlledRelocation(reason)
        await this.view.goTo(navigationTarget.target)
      }
      this.scheduleControlledRelocationFallback(reason)
      this.applyReaderViewportLayout(reason)
      requestAnimationFrame(() => this.logContentLayout(reason))
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

  mediaOverlayFollowShouldDeferForUserRelocation() {
    const reason = String(this.controlledRelocateReason || '').trim()
    return Boolean(reason) && reason !== 'media-overlay-follow'
  }

  mediaOverlayFragmentAlreadyVisible(fragment) {
    const textStart = Number(fragment?.textStart)
    const textEnd = Number(fragment?.textEnd)
    if (!Number.isFinite(textStart) || !Number.isFinite(textEnd) || textEnd <= textStart) return false
    const visibleRange = this.currentVisibleTextRangeForHref?.(fragment?.textHref || '')
    if (!visibleRange) return false
    return textStart >= Number(visibleRange.visibleStart) && textEnd <= Number(visibleRange.visibleEnd)
  }

  mediaOverlayFragmentHasTextRange(fragment) {
    const textStart = Number(fragment?.textStart)
    const textEnd = Number(fragment?.textEnd)
    return Number.isFinite(textStart) && Number.isFinite(textEnd) && textEnd > textStart
  }

  mediaOverlayPlayedKeyForFragment(fragment) {
    return `${ReaderMediaOverlayPlayedRangeKeyPrefix}${stableHash([
      fragment?.textHref || '',
      fragment?.clipBeginSeconds ?? '',
      fragment?.clipEndSeconds ?? '',
      fragment?.textStart ?? '',
      fragment?.textEnd ?? '',
      fragment?.ebookText || '',
    ].join('|'))}`
  }

  rememberPlayedMediaOverlayFragment(previousFragment, nextFragment) {
    if (!this.readerMediaOverlayPersistentPlayed()) return
    if (!previousFragment || !this.mediaOverlayFragmentHasTextRange(previousFragment)) return
    const previousStart = Number(previousFragment.clipBeginSeconds)
    const nextStart = Number(nextFragment?.clipBeginSeconds)
    if (Number.isFinite(previousStart) && Number.isFinite(nextStart) && nextStart < previousStart) return
    const previousHref = previousFragment.textHref || ''
    const nextHref = nextFragment?.textHref || ''
    if (previousHref && nextHref && !readerHrefMatches(previousHref, nextHref)) return
    const key = this.mediaOverlayPlayedKeyForFragment(previousFragment)
    this.mediaOverlayPlayedFragments.set(key, {
      ...previousFragment,
      textProgressEnd: previousFragment.textEnd,
      textProgressFraction: 1,
    })
  }

  prunePlayedMediaOverlayFragments(fragment) {
    const currentStart = Number(fragment?.clipBeginSeconds)
    const currentHref = fragment?.textHref || ''
    if (!Number.isFinite(currentStart) || !currentHref) return
    for (const [key, played] of Array.from(this.mediaOverlayPlayedFragments.entries())) {
      const playedStart = Number(played?.clipBeginSeconds)
      const playedHref = played?.textHref || ''
      if (Number.isFinite(playedStart) && playedStart >= currentStart && readerHrefMatches(playedHref, currentHref)) {
        this.mediaOverlayPlayedFragments.delete(key)
      }
    }
  }

  paintPlayedMediaOverlayFragments() {
    for (const [key, fragment] of this.mediaOverlayPlayedFragments.entries()) {
      this.highlightMediaOverlayTextRange({
        ...fragment,
        overlayKey: key,
        suppressDiagnostic: true,
        textProgressEnd: fragment.textEnd,
        textProgressFraction: 1,
      })
    }
  }

  readerMediaOverlayPersistentPlayed(settings = this.readerSettings) {
    return settings?.whispersyncHighlightLoading === 'persistent-played-text'
  }

  readerMediaOverlayHighlightColor(settings = this.readerSettings) {
    return readerCssColorFromArgb(settings?.whispersyncHighlightColorArgb, 'rgba(246, 195, 67, 0.4)')
  }

  readerMediaOverlayHighlightDraw(settings = this.readerSettings) {
    return settings?.whispersyncHighlightStyle === 'marker'
      ? readerDrawMediaOverlayMarker
      : readerDrawMediaOverlaySelection
  }

  mediaOverlayAnimationKeyForFragment(fragment) {
    if (!fragment) return ''
    return [
      fragment.textHref || '',
      fragment.resourceHref || '',
      fragment.clipBeginSeconds ?? '',
      fragment.clipEndSeconds ?? '',
      fragment.textStart ?? '',
      fragment.textEnd ?? '',
      fragment.ebookText || '',
    ].join('|')
  }

  stopMediaOverlayProgressAnimation() {
    this.mediaOverlayProgressAnimationToken += 1
    this.mediaOverlayProgressAnimationKey = ''
    if (this.mediaOverlayProgressAnimationFrame != null && typeof cancelAnimationFrame === 'function') {
      cancelAnimationFrame(this.mediaOverlayProgressAnimationFrame)
    }
    this.mediaOverlayProgressAnimationFrame = null
  }

  mediaOverlayProgressFraction(textStart, textEnd, paintEnd, fragment) {
    const fraction = Number(fragment?.textProgressFraction)
    const rawProgress = Number.isFinite(fraction)
      ? fraction
      : (paintEnd - textStart) / Math.max(1, textEnd - textStart)
    const clampedProgress = Math.max(0, Math.min(1, rawProgress))
    const key = this.mediaOverlayAnimationKeyForFragment(fragment)
    if (key && key === this.mediaOverlayProgressDisplayKey) {
      const displayedProgress = this.mediaOverlayProgressDisplayedFraction
      const smallBackwardJitter = clampedProgress < displayedProgress && clampedProgress + 0.12 >= displayedProgress
      const progress = smallBackwardJitter ? displayedProgress : clampedProgress
      this.mediaOverlayProgressDisplayedFraction = Math.max(displayedProgress, progress)
      return progress
    }
    this.mediaOverlayProgressDisplayKey = key
    this.mediaOverlayProgressDisplayedFraction = clampedProgress
    return clampedProgress
  }

  paintActiveMediaOverlayFragment(fragment) {
    const preservePlayed = this.readerMediaOverlayPersistentPlayed()
    this.clearOverlay({ preservePlayed, preserveAnimation: true })
    if (preservePlayed) this.paintPlayedMediaOverlayFragments()
    let highlighted = this.highlightMediaOverlayTextRange(fragment)
    if (!highlighted && fragment.fragmentId && !this.mediaOverlayFragmentHasTextRange(fragment)) {
      for (const doc of this.contentDocuments()) {
        const element = doc.getElementById(fragment.fragmentId)
        if (element) {
          element.classList.add(overlayClass)
          highlighted = true
        }
      }
    }
    this.mediaOverlayActiveFragment = fragment
    return highlighted
  }

  startMediaOverlayProgressAnimation(fragment) {
    this.stopMediaOverlayProgressAnimation()
    if (
      !fragment ||
        !this.mediaOverlayFragmentHasTextRange(fragment) ||
        typeof requestAnimationFrame !== 'function'
    ) {
      return
    }
    const clipBegin = Number(fragment.clipBeginSeconds)
    const clipEnd = Number(fragment.clipEndSeconds)
    const speed = Math.max(0.05, Number(fragment.playbackSpeed) || 1)
    if (!Number.isFinite(clipBegin) || !Number.isFinite(clipEnd) || clipEnd <= clipBegin) return
    const textStart = Number(fragment.textStart)
    const textEnd = Number(fragment.textEnd)
    const paintEnd = this.clampedMediaOverlayProgressEnd(textStart, textEnd, fragment)
    const startFraction = this.mediaOverlayProgressFraction(textStart, textEnd, paintEnd, fragment)
    if (startFraction >= 1) return
    const durationMs = Math.max(1, (clipEnd - clipBegin) * 1000)
    const startAt = typeof performance !== 'undefined' && typeof performance.now === 'function'
      ? performance.now()
      : Date.now()
    const key = this.mediaOverlayAnimationKeyForFragment(fragment)
    const token = this.mediaOverlayProgressAnimationToken
    this.mediaOverlayProgressAnimationKey = key
    const tick = now => {
      if (token !== this.mediaOverlayProgressAnimationToken) return
      if (this.mediaOverlayAnimationKeyForFragment(this.mediaOverlayActiveFragment) !== key) return
      const elapsedMs = Math.max(0, now - startAt)
      const progress = Math.min(1, startFraction + ((elapsedMs * speed) / durationMs))
      const animatedFragment = {
        ...this.mediaOverlayActiveFragment,
        textProgressFraction: progress,
      }
      this.paintActiveMediaOverlayFragment(animatedFragment)
      if (progress < 1) {
        this.mediaOverlayProgressAnimationFrame = requestAnimationFrame(tick)
      } else {
        this.mediaOverlayProgressAnimationFrame = null
      }
    }
    this.mediaOverlayProgressAnimationFrame = requestAnimationFrame(tick)
  }

  async applyOverlayFragment(fragment) {
    if (!this.view || !fragment) return
    const targetHref = fragment.textHref && fragment.fragmentId
      ? `${fragment.textHref}#${fragment.fragmentId}`
      : fragment.textHref
    if (targetHref) {
      if (this.mediaOverlayFollowShouldDeferForUserRelocation()) {
        log('media-overlay-follow:deferred', targetHref, `reason=${this.controlledRelocateReason}`)
        readerTrace('media-overlay-follow:deferred', {
          targetHref,
          reason: this.controlledRelocateReason,
        })
        return
      }
      if (this.mediaOverlayFragmentAlreadyVisible(fragment)) {
        log('media-overlay-follow:already-visible', targetHref)
        readerTrace('media-overlay-follow:already-visible', {
          targetHref,
          textStart: Number(fragment?.textStart),
          textEnd: Number(fragment?.textEnd),
        })
      } else {
        log('media-overlay-follow:outside-visible-page', targetHref)
        readerTrace('media-overlay-follow:outside-visible-page', {
          targetHref,
          textStart: Number(fragment?.textStart),
          textEnd: Number(fragment?.textEnd),
        })
        post({ type: 'overlayFragmentInactive', fragmentId: fragment.fragmentId })
        return
      }
    }
    this.prunePlayedMediaOverlayFragments(fragment)
    this.rememberPlayedMediaOverlayFragment(this.mediaOverlayActiveFragment, fragment)
    this.paintActiveMediaOverlayFragment(fragment)
    this.startMediaOverlayProgressAnimation(fragment)
    post({ type: 'overlayFragmentActive', ...fragment })
  }

  updateOverlayFragmentProgress(fragment) {
    if (!this.view || !fragment) return
    this.prunePlayedMediaOverlayFragments(fragment)
    this.paintActiveMediaOverlayFragment(fragment)
    this.startMediaOverlayProgressAnimation(fragment)
  }

  clampedMediaOverlayProgressEnd(textStart, textEnd, fragment) {
    const textProgressEnd = Number(fragment?.textProgressEnd)
    if (!Number.isFinite(textProgressEnd)) return textEnd
    return Math.max(textStart, Math.min(textEnd, textProgressEnd))
  }

  mediaOverlayPaintEndForResolvedRange(textStart, textEnd, paintEnd, resolvedNormalizedTextStart, resolvedNormalizedTextEnd, fragment) {
    if (resolvedNormalizedTextEnd <= resolvedNormalizedTextStart) return resolvedNormalizedTextEnd
    const progress = this.mediaOverlayProgressFraction(textStart, textEnd, paintEnd, fragment)
    const resolvedPaintEnd = resolvedNormalizedTextStart + ((resolvedNormalizedTextEnd - resolvedNormalizedTextStart) * progress)
    return Math.min(resolvedNormalizedTextEnd, Math.max(resolvedNormalizedTextStart + 1, resolvedPaintEnd))
  }

  postMediaOverlayRangeDiagnostic(fragment, sidecarRange, resolvedRange, paintNormalized, paintRaw) {
    const key = [
      fragment?.textHref || '',
      sidecarRange.start,
      sidecarRange.end,
      resolvedRange.textStart,
      resolvedRange.textEnd,
      resolvedRange.matched ? 'matched' : 'fallback',
      resolvedRange.locator || '',
      resolvedRange.clampedByNextCue ? 'next-clamp' : '',
      Math.floor(paintNormalized),
    ].join('|')
    if (key === this.lastMediaOverlayRangeDiagnosticKey) return
    this.lastMediaOverlayRangeDiagnosticKey = key
    const diagnostic = {
      href: fragment?.textHref || null,
      matched: Boolean(resolvedRange.matched),
      locator: resolvedRange.locator || 'offset',
      spokenLength: String(fragment?.spokenText || '').length,
      ebookLength: String(fragment?.ebookText || '').length,
      sidecarRange: `${sidecarRange.start}-${sidecarRange.end}`,
      resolvedRange: `${resolvedRange.textStart}-${resolvedRange.textEnd}`,
      normalizedRange: `${resolvedRange.normalizedTextStart}-${resolvedRange.normalizedTextEnd}`,
      clampedByNextCue: Boolean(resolvedRange.clampedByNextCue),
      paintNormalized: Math.round(paintNormalized * 100) / 100,
      paintRaw: Math.round(paintRaw * 100) / 100,
    }
    log('media-overlay-range:resolved', JSON.stringify(diagnostic))
    readerTrace('media-overlay-range:resolved', diagnostic)
  }

  highlightMediaOverlayTextRange(fragment) {
    const textStart = Number(fragment?.textStart)
    const textEnd = Number(fragment?.textEnd)
    if (!Number.isFinite(textStart) || !Number.isFinite(textEnd) || textEnd <= textStart) return false
    const overlayKey = fragment?.overlayKey || ReaderMediaOverlayActiveRangeKey
    const suppressDiagnostic = Boolean(fragment?.suppressDiagnostic)
    const highlightColor = this.readerMediaOverlayHighlightColor()
    const highlightDraw = this.readerMediaOverlayHighlightDraw()
    const rawPaintEnd = this.clampedMediaOverlayProgressEnd(textStart, textEnd, fragment)
    const paintEnd = Math.min(textEnd, Math.max(textStart + 1, rawPaintEnd))
    if (paintEnd <= textStart) return true
    let highlighted = false
    for (const content of this.contentEntries()) {
      const section = Number.isFinite(Number(content.index))
        ? this.view?.book?.sections?.[Math.floor(Number(content.index))]
        : null
      const sectionHref = section?.href || content.href || ''
      if (fragment.textHref && sectionHref && !readerHrefMatches(sectionHref, fragment.textHref)) continue
      const entries = readerMediaOverlayTextEntries(content.doc)
      if (!entries.length) continue
      const normalizedMap = readerMediaOverlayNormalizedTextMap(entries)
      const resolvedRangeBeforeClamp = readerMediaOverlayResolvedTextRange(
        normalizedMap,
        textStart,
        textEnd,
        fragment.ebookText
      )
      const hasNextTextRange = Number.isFinite(Number(fragment.nextTextStart)) &&
        Number.isFinite(Number(fragment.nextTextEnd)) &&
        Number(fragment.nextTextEnd) > Number(fragment.nextTextStart)
      const nextRange = hasNextTextRange &&
        (!fragment.nextTextHref || !fragment.textHref || readerHrefMatches(fragment.nextTextHref, fragment.textHref))
        ? readerMediaOverlayResolvedTextRange(
          normalizedMap,
          fragment.nextTextStart,
          fragment.nextTextEnd,
          fragment.nextEbookText
        )
        : null
      const resolvedRange = readerMediaOverlayClampRangeBeforeNextCue(
        normalizedMap,
        resolvedRangeBeforeClamp,
        nextRange
      )
      const resolvedTextStart = resolvedRange.textStart
      const resolvedTextEnd = resolvedRange.textEnd
      const resolvedPaintEnd = this.mediaOverlayPaintEndForResolvedRange(
        textStart,
        textEnd,
        paintEnd,
        resolvedRange.normalizedTextStart,
        resolvedRange.normalizedTextEnd,
        fragment
      )
      const resolvedRawPaintEnd = readerMediaOverlayRawOffsetForNormalizedOffset(normalizedMap, resolvedPaintEnd, 'end')
      if (!suppressDiagnostic) {
        this.postMediaOverlayRangeDiagnostic(
          fragment,
          { start: textStart, end: textEnd },
          resolvedRange,
          resolvedPaintEnd,
          resolvedRawPaintEnd
        )
      }
      const start = readerMediaOverlayTextPoint(entries, Math.floor(resolvedTextStart))
      const end = readerMediaOverlayTextPoint(entries, Math.ceil(Math.min(resolvedTextEnd, resolvedRawPaintEnd)))
      if (!start || !end) continue
      const overlayer = content.overlayer
      if (!overlayer) continue
      const range = content.doc.createRange()
      try {
        range.setStart(start.node, start.offset)
        range.setEnd(end.node, end.offset)
        if (range.collapsed) continue
        overlayer.add(overlayKey, range, highlightDraw, {
          color: highlightColor,
          writingMode: this.view?.renderer?.writingMode,
        })
        highlighted = true
      } catch (error) {
        logError('media-overlay-range:failed', error?.message || String(error))
      }
    }
    if (!highlighted) {
      log('media-overlay-range:missing', fragment.textHref || 'unknown', `${textStart}-${textEnd}`)
      readerTrace('media-overlay-range:missing', {
        textHref: fragment.textHref || null,
        textStart,
        textEnd,
      })
    }
    return highlighted
  }

  clearOverlay() {
    const { preservePlayed = false, preserveAnimation = false } = arguments[0] || {}
    if (!preserveAnimation) this.stopMediaOverlayProgressAnimation()
    let removedAny = false
    for (const content of this.contentEntries()) {
      content.overlayer?.remove?.(ReaderMediaOverlayActiveRangeKey)
      if (!preservePlayed) {
        for (const key of this.mediaOverlayPlayedFragments.keys()) {
          content.overlayer?.remove?.(key)
        }
      }
    }
    if (!preservePlayed) {
      this.mediaOverlayPlayedFragments.clear()
      this.mediaOverlayActiveFragment = null
    }
    for (const doc of this.contentDocuments()) {
      for (const marker of Array.from(doc.querySelectorAll(`[${ReaderMediaOverlayRangeAttribute}="true"]`))) {
        removedAny = readerMediaOverlayUnwrapRangeMarker(marker) || removedAny
      }
      for (const element of doc.querySelectorAll(`.${overlayClass}`)) {
        element.classList.remove(overlayClass)
        removedAny = true
      }
    }
    if (removedAny) post({ type: 'footnoteClose' })
  }

  postSearchResults({ query, results, progress = null, complete = false }) {
    const normalizedProgress = Number.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : null
    post({
      type: 'searchResults',
      query,
      results,
      progress: normalizedProgress,
      complete: Boolean(complete),
    })
  }

  async search(query) {
    if (!this.view || !query) return
    try {
      const results = []
      let progress = 0
      let completed = false
      this.postSearchResults({ query, results, progress, complete: false })
      for await (const result of this.view.search?.({ query }) || []) {
        if (result === 'done') {
          completed = true
          this.postSearchResults({ query, results, progress: 1, complete: true })
        } else if (result?.progress != null) {
          progress = Number(result.progress)
          this.postSearchResults({ query, results, progress, complete: false })
        } else {
          const nextResults = normalizeSearchResult(result, results.length, this.view)
          if (nextResults.length > 0) {
            results.push(...nextResults)
            this.postSearchResults({ query, results, progress, complete: false })
          }
        }
      }
      if (!completed) this.postSearchResults({ query, results, progress: 1, complete: true })
    } catch (error) {
      reportError(error, 'search_failed')
    }
  }

  clearSearch() {
    try {
      this.view?.clearSearch?.()
      this.postSearchResults({ query: '', results: [], progress: null, complete: true })
    } catch (error) {
      reportError(error, 'clear_search_failed')
    }
  }

  postToc() {
    const items = flattenTocItems(this.view?.book?.toc || [])
    post({ type: 'toc', items })
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
    this.attachSurfacePaperTextureScrollSync()
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
      if (!doc || !doc.defaultView || !doc.body || !doc.documentElement) {
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
      const movingPageTextureStyle = this.movingPageTextureLayer
        ? window.getComputedStyle(this.movingPageTextureLayer)
        : null
      const movingPageBorderOverlayStyle = this.movingPageBorderOverlayLayer
        ? window.getComputedStyle(this.movingPageBorderOverlayLayer)
        : null
      const movingPageStainOverlayStyle = this.movingPageStainOverlayLayer
        ? window.getComputedStyle(this.movingPageStainOverlayLayer)
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
        `movingPageTextureOpacity=${movingPageTextureStyle?.opacity || 'unset'}`,
        `movingPageTextureImage=${movingPageTextureStyle?.backgroundImage === 'none' ? 'none' : movingPageTextureStyle ? 'set' : 'unset'}`,
        `movingPageBorderOverlayOpacity=${movingPageBorderOverlayStyle?.opacity || 'unset'}`,
        `movingPageBorderOverlayImage=${movingPageBorderOverlayStyle?.backgroundImage === 'none' ? 'none' : movingPageBorderOverlayStyle ? 'set' : 'unset'}`,
        `movingPageStainOverlayOpacity=${movingPageStainOverlayStyle?.opacity || 'unset'}`,
        `movingPageStainOverlayImage=${movingPageStainOverlayStyle?.backgroundImage === 'none' ? 'none' : movingPageStainOverlayStyle ? 'set' : 'unset'}`,
        `surfaceTextureLayer=${this.surfaceTextureLayer ? 'present' : 'missing'}`,
        `movingPageTextureLayer=${this.movingPageTextureLayer ? 'present' : 'missing'}`,
        `surfaceTextureAsset=${readerRoot.dataset.navicSurfacePaperTextureAsset || 'unset'}`,
        `surfaceBorderOverlayAsset=${readerRoot.dataset.navicSurfaceBorderOverlayAsset || 'unset'}`,
        `surfaceStainOverlayAsset=${readerRoot.dataset.navicSurfacePageStainOverlayAsset || 'unset'}`
      )
    }
  }

  contentEntries(detail = {}) {
    const entries = []
    const seen = new Set()
    const contents = this.view?.renderer?.getContents?.() || []
    const add = (source, doc, index) => {
      if (!doc || seen.has(doc)) return
      seen.add(doc)
      entries.push({
        ...(source || {}),
        doc,
        index,
      })
    }
    if (detail.doc) {
      const matchingContent = contents.find(content => content.doc === detail.doc)
      add(matchingContent || detail, detail.doc, Number.isFinite(detail.index) ? detail.index : matchingContent?.index)
    }
    for (const content of contents) {
      add(content, content.doc, content.index)
    }
    return entries
  }

  contentDocuments() {
    return this.contentEntries().map(content => content.doc)
  }

  pageTurnCaptureGeometry() {
    const viewport = readerViewportSize()
    const pageBox = readerAdaptiveFoliatePageBox(viewport, this.readerSettings)
    const spreadMode = this.surfaceSpreadMode || readerSurfaceSpreadMode({
      flowMode: this.readerFlowModeValue,
      width: viewport.width,
      height: viewport.height,
    })
    const layoutProfile = this.surfacePaperLayoutProfile || readerPaperLayoutProfile({
      flowMode: this.readerFlowModeValue,
      width: viewport.width,
      height: viewport.height,
      spreadMode,
    })
    const geometry = this.surfacePageDecorationGeometry || readerSurfacePageDecorationGeometry({
      settings: this.readerSettings,
      spreadMode,
      foliateGap: pageBox.foliateGap,
      shellCoverVisible: this.shellCoverVisible,
      coverTint: this.shellCoverDominantColor,
      layoutProfile,
    })
    const percentPixels = (value, axisSize) => {
      const text = String(value || '').trim()
      if (text.endsWith('%')) return Number.parseFloat(text) * axisSize / 100
      return Number.parseFloat(text) || 0
    }
    const pageRect = (role, page) => ({
      role,
      left: percentPixels(page?.left, viewport.width),
      top: 0,
      width: percentPixels(page?.width, viewport.width),
      height: viewport.height,
    })
    const pages = spreadMode === 'spread'
      ? [pageRect('left', geometry.pages.left), pageRect('right', geometry.pages.right)]
      : [pageRect('full', geometry.pages.full)]
    const background = readerThemePalette(this.readerSettings?.theme).background
    const reverseFaceColorArgb = readerCssColorToArgb(background)
    return {
      viewportWidth: viewport.width,
      viewportHeight: viewport.height,
      mode: spreadMode === 'spread' ? 'spread' : 'single',
      pages,
      reverseFaceColorArgb,
    }
  }
}

Object.assign(NavicReaderRuntime.prototype,
  NavicReaderShellCoverMethods,
  NavicReaderViewportMethods,
  NavicReaderLocationMethods,
  NavicReaderPageTurnMethods,
  NavicReaderContentInteractionMethods,
  NavicReaderPaginationMethods,
  NavicReaderAppearanceMethods
)

const runtime = new NavicReaderRuntime()

window.NavicReaderBridge = {
  dispatch: command => {
    const pageTurnCommand = command?.type === 'nextPage' || command?.type === 'previousPage'
    const settleToken = pageTurnCommand ? runtime.pendingNativePageTurnSettleToken : null
    if (settleToken) runtime.pendingNativePageTurnSettleToken = null
    let result = null
    try {
      result = runtime.dispatch(command)
    } catch (error) {
      if (settleToken) runtime.nativePageTurnSettledToken = settleToken
      throw error
    }
    if (settleToken) {
      Promise.resolve(result).finally(() => {
        runtime.nativePageTurnSettledToken = settleToken
      })
    }
    return result
  },
  armNativePageTurnSettle: token => {
    runtime.pendingNativePageTurnSettleToken = String(token || '') || null
    runtime.nativePageTurnSettledToken = null
    runtime.pendingExactPageTurnSettlement = null
    runtime.nativePageTurnSettledState = null
  },
  nativePageTurnSettledToken: () => runtime.nativePageTurnSettledToken,
  nativePageTurnSettledState: () => runtime.nativePageTurnSettledState,
  pageTurnCaptureGeometry: () => runtime.pageTurnCaptureGeometry(),
  readerContentActionAtPoint: (x, y, viewWidth, viewHeight) =>
    runtime.readerContentActionAtRootPoint(x, y, viewWidth, viewHeight)?.handled === true,
  postOverlayFragmentActive: fragment => post({ type: 'overlayFragmentActive', ...fragment }),
  postOverlayFragmentInactive: fragmentId => post({ type: 'overlayFragmentInactive', fragmentId }),
}

readerTrace('runtime:ready', { engine: 'foliate-js' })
log('module-loaded')
post({ type: 'ready' })
