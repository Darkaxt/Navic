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
  readerThemeKey
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
import { NavicReaderPageTurnPreviewMethods } from './navic-reader-page-turn-preview.js'
import { NavicReaderContentInteractionMethods } from './navic-reader-content-interactions.js'
import { NavicReaderPaginationMethods } from './navic-reader-pagination.js'
import { NavicReaderAppearanceMethods } from './navic-reader-appearance.js'
import { NavicReaderShellCoverMethods } from './navic-reader-shell-cover.js'
import { NavicReaderViewportMethods } from './navic-reader-viewport.js'
import { NavicReaderLocationMethods } from './navic-reader-location.js'
import {
  NavicReaderMediaOverlayMethods,
  ReaderMediaOverlayPlayedRangeKeyPrefix,
} from './navic-reader-media-overlay.js'
import {
  ReaderWordSyncV1ExtractedUtf8Mode,
  ReaderWordSyncProvenanceStore,
  applyReaderWordSyncOverlayFragment,
  paintReaderWordSyncActiveOverlay,
  paintReaderWordSyncOverlayTextRange,
  rejectReaderWordSyncOverlay,
  validatedReaderOverlayCoordinateMode,
} from './navic-reader-wordsync-provenance.js'
import { ReaderDuplicatePageFingerprintDiagnostics } from './navic-reader-baseline-hmac.js'
const ReaderSvgNamespace = 'http://www.w3.org/2000/svg'
const ReaderMediaOverlayRangeAttribute = 'data-navic-media-overlay-range'
const ReaderMediaOverlayActiveRangeKey = 'navic-media-overlay-active'

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
  foliateSessionId = ''
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
  mediaOverlayPlayedKeyPrefix = ReaderMediaOverlayPlayedRangeKeyPrefix
  mediaOverlayPlayedFragments = new Map()
  rawTextProvenance = new ReaderWordSyncProvenanceStore({ postStatus: post })
  duplicatePageFingerprint = new ReaderDuplicatePageFingerprintDiagnostics({ postEvent: post })
  committedVisibleTextRange = null
  mediaOverlayProgressAnimationFrame = null
  mediaOverlayProgressAnimationToken = 0
  mediaOverlayProgressAnimationKey = ''
  mediaOverlayProgressDisplayKey = ''
  mediaOverlayProgressDisplayedFraction = 0
  pendingRelocateDetail = null
  pendingRelocateReason = 'relocate-committed'
  controlledRelocateOwner = null
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
  pendingExactPageTurnSettlements = new Map(); completedExactPageTurnSettlements = new Map(); retiredExactPageTurnSettlements = new Map()
  activeExactPageTurnSettlementToken = null; exactPageTurnNavigationToken = null; exactPageTurnNavigationInProgress = false
  liveTextPageCommitInvalidationTarget = null; liveTextPageCommitInvalidationListener = null
  liveTextPageCommitRetryToken = null; liveTextPageCommitRetryRequestedToken = null
  nativePageTurnSettledState = null
  lastTracedExactPageTurnGestureId = null
  pageTurnPresentationSequence = 0
  foregroundMutationGeneration = 0
  pageTurnPreviewPresentationReceiptValue = null
  pageTurnLivePresentationReceiptValue = null
  pageTurnLivePresentationTargetValue = null
  pageTurnPreviewView = null
  pageTurnPreviewPublicationUrl = ''
  pageTurnPreviewGeneration = 0
  pageTurnPreviewStateValue = null
  pageTurnPreviewBatchStateValue = null
  pageTurnPreviewExposedToken = ''
  pageTurnPreviewExposedMutationGeneration = null
  pageTurnPreviewLiveVisibility = ''
  pageTurnPreviewLiveOpacity = ''
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
      case 'goToLocator':
        return this.goToLocator(command.locator, command.reason || 'go-to')
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
        return this.goToVisualPage(command)
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
      case 'requestVisibleTextRange':
        return this.postCurrentVisibleTextRange(
          this.lastRelocateDetail || this.currentFixedLayoutLocationDetail?.() || {},
          { source: command.source || 'explicit-refresh', forceDuplicatePost: true }
        )
      case 'installRawTextProvenance':
        return this.rawTextProvenance.install(
          command.descriptor,
          this.view?.book,
          this.contentEntries()
        )
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
    foliateSessionId,
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
    const normalizedFoliateSessionId = typeof foliateSessionId === 'string'
      ? foliateSessionId.trim()
      : ''
    if (!normalizedFoliateSessionId) {
      logError('openPublication:missing-session')
      post({ type: 'error', code: 'missing_session', message: 'Reader runtime session is required.' })
      return
    }
    this.mediaOverlayEnabled = Boolean(mediaOverlayEnabled)
    log('openPublication:start', describeUrl(url), `overlay=${this.mediaOverlayEnabled}`)
    try {
      this.close()
      this.duplicatePageFingerprint.beginSession()
      this.foliateSessionId = normalizedFoliateSessionId
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
      this.attachLiveTextPageCommitInvalidationListener()
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
    this.invalidatePaginationProfileTask('reader-close')
    this.destroyPageTurnPreviewRenderer('reader-close')
    this.clearOverlay()
    this.rawTextProvenance.clear()
    this.duplicatePageFingerprint.endSession()
    this.committedVisibleTextRange = null
    this.clearShellCover()
    this.detachSurfacePaperTextureScrollSync()
    this.clearDeferredReflowablePageTurn()
    this.detachLiveTextPageCommitInvalidationListener()
    this.view?.close?.()
    this.view?.remove?.()
    this.view = null
    this.readerSettings = {}
    this.nativeTapZones = false
    this.originalBookDir = null
    this.publicationUrl = ''
    this.foliateSessionId = ''
    this.externalShellCover = false
    this.suppressWebShellCover = false
    this.pageTurnPromise = null
    this.pageTurnQueue = []
    this.pageTurnInProgress = false
    this.pageTurnDirection = null
    this.pendingNativePageTurnSettleToken = null
    this.nativePageTurnSettledToken = null
    this.pendingExactPageTurnSettlements.clear(); this.completedExactPageTurnSettlements.clear(); this.retiredExactPageTurnSettlements.clear()
    this.activeExactPageTurnSettlementToken = null; this.exactPageTurnNavigationToken = null; this.exactPageTurnNavigationInProgress = false
    this.liveTextPageCommitRetryToken = null; this.liveTextPageCommitRetryRequestedToken = null
    this.nativePageTurnSettledState = null
    this.lastTracedExactPageTurnGestureId = null
    this.pageTurnPreviewPresentationReceiptValue = null
    this.pageTurnLivePresentationReceiptValue = null
    this.pageTurnLivePresentationTargetValue = null
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
    let controlledRelocationOwner = null
    try {
      const navigationTarget = await this.resolveReaderNavigationTarget(locator)
      if (!navigationTarget) return
      controlledRelocationOwner = this.beginControlledRelocation(reason)
      let committed
      if (navigationTarget.rendererTarget && this.view.renderer?.goTo) {
        log('go-to:resolved', navigationTarget.target, `index=${navigationTarget.rendererTarget.index}`)
        committed = await this.view.renderer.goTo(navigationTarget.rendererTarget)
        if (committed !== false) this.view.history?.pushState?.(navigationTarget.target)
      } else {
        log('go-to:fallback', navigationTarget.target)
        committed = await this.view.goTo(navigationTarget.target)
      }
      if (committed === false) {
        this.cancelControlledRelocation(controlledRelocationOwner)
        return false
      }
      this.scheduleControlledRelocationFallback(reason)
      this.applyReaderViewportLayout(reason)
      requestAnimationFrame(() => this.logContentLayout(reason))
      return true
    } catch (error) {
      this.cancelControlledRelocation(controlledRelocationOwner)
      reportError(error, 'navigation_failed')
      return false
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
    const rawPainted = paintReaderWordSyncActiveOverlay(
      this, fragment, ReaderMediaOverlayActiveRangeKey
    )
    if (rawPainted != null) return rawPainted
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
    this.mediaOverlayActiveFragment = highlighted ? fragment : null
    return highlighted
  }

  startMediaOverlayProgressAnimation(fragment) {
    this.stopMediaOverlayProgressAnimation()
    if (validatedReaderOverlayCoordinateMode(fragment) === ReaderWordSyncV1ExtractedUtf8Mode) return
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
        textProgressEnd: textStart + ((textEnd - textStart) * progress),
        textProgressFraction: progress,
      }
      if (!this.mediaOverlayFragmentProgressAlreadyVisible(animatedFragment)) {
        this.rejectOverlayFragment(animatedFragment, 'animation-outside-visible-page')
        return
      }
      if (!this.paintActiveMediaOverlayFragment(animatedFragment)) {
        this.rejectOverlayFragment(animatedFragment, 'animation-paint-rejected')
        return
      }
      if (progress < 1) {
        this.mediaOverlayProgressAnimationFrame = requestAnimationFrame(tick)
      } else {
        this.mediaOverlayProgressAnimationFrame = null
      }
    }
    this.mediaOverlayProgressAnimationFrame = requestAnimationFrame(tick)
  }

  rejectOverlayFragment(fragment, reason) {
    if (rejectReaderWordSyncOverlay(this, fragment, reason)) return
    this.clearOverlay({ preservePlayed: this.readerMediaOverlayPersistentPlayed() })
    this.postOverlayFragmentInactive(fragment, reason)
  }

  async applyOverlayFragment(fragment) {
    if (!this.view || !fragment) return
    if (applyReaderWordSyncOverlayFragment(this, fragment)) return
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
        this.rejectOverlayFragment(fragment, 'user-relocation-active')
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
        this.rejectOverlayFragment(fragment, 'outside-visible-page')
        return
      }
    }
    this.prunePlayedMediaOverlayFragments(fragment)
    this.rememberPlayedMediaOverlayFragment(this.mediaOverlayActiveFragment, fragment)
    const painted = this.paintActiveMediaOverlayFragment(fragment)
    if (!painted) {
      this.rejectOverlayFragment(fragment, 'paint-rejected')
      return
    }
    this.startMediaOverlayProgressAnimation(fragment)
    post({ type: 'overlayFragmentActive', ...fragment })
  }

  updateOverlayFragmentProgress(fragment) {
    if (!this.view || !fragment) return
    const coordinateMode = validatedReaderOverlayCoordinateMode(fragment)
    if (!coordinateMode) {
      this.rejectOverlayFragment(fragment, 'invalid-coordinate-mode')
      return
    }
    const rawMode = coordinateMode === ReaderWordSyncV1ExtractedUtf8Mode
    const activeRequestId = this.mediaOverlayActiveFragment?.overlayRequestId
    if (
      fragment.overlayRequestId == null ||
        activeRequestId == null ||
        fragment.overlayRequestId !== activeRequestId
    ) {
      this.postOverlayFragmentInactive(fragment, 'stale-progress-request')
      return
    }
    if (!this.mediaOverlayFragmentProgressAlreadyVisible(fragment)) {
      this.rejectOverlayFragment(fragment, 'progress-outside-visible-page')
      return
    }
    if (!rawMode) this.prunePlayedMediaOverlayFragments(fragment)
    if (!this.paintActiveMediaOverlayFragment(fragment)) {
      this.rejectOverlayFragment(fragment, 'progress-paint-rejected')
      return
    }
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
    const rawPainted = paintReaderWordSyncOverlayTextRange(
      this, fragment, ReaderMediaOverlayActiveRangeKey
    )
    if (rawPainted != null) return rawPainted
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
    }
    this.mediaOverlayActiveFragment = null
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
    this.captureDuplicatePageBaselines(detail)
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
    const loadedContents = this.contentEntries(detail)
    for (const content of loadedContents) {
      this.attachContentDocumentBehaviors(content.doc, content.index)
    }
    this.committedVisibleTextRange = null
    void this.rawTextProvenance.mapLoadedDocuments(this.view?.book, loadedContents)
    requestAnimationFrame(() => {
      this.applyRendererTheme(this.readerSettings)
      this.updateReaderPageNumberLayer()
      requestAnimationFrame(() => this.logContentLayout('load'))
    })
    if (this.mediaOverlayEnabled) {
      const invalidatedFragment = this.mediaOverlayActiveFragment
      this.clearOverlay({ preservePlayed: this.readerMediaOverlayPersistentPlayed() })
      this.postOverlayFragmentInactive(invalidatedFragment, 'document-loaded')
    }
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
}

Object.assign(NavicReaderRuntime.prototype,
  NavicReaderShellCoverMethods,
  NavicReaderViewportMethods,
  NavicReaderLocationMethods,
  NavicReaderMediaOverlayMethods,
  NavicReaderPageTurnMethods,
  NavicReaderPageTurnPreviewMethods,
  NavicReaderContentInteractionMethods,
  NavicReaderPaginationMethods,
  NavicReaderAppearanceMethods
)

const runtime = new NavicReaderRuntime()
const acknowledgedCommandIds = new Set()

window.NavicReaderBridge = {
  dispatch: command => {
    const commandId = typeof command?.commandId === 'string' ? command.commandId.trim() : ''
    if (commandId && acknowledgedCommandIds.has(commandId)) {
      post({ type: 'commandAck', commandId })
      return Promise.resolve(null)
    }
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
    return Promise.resolve(result)
      .then(value => {
        if (commandId) {
          acknowledgedCommandIds.add(commandId)
          post({ type: 'commandAck', commandId })
        }
        return value
      })
      .finally(() => {
        if (settleToken) runtime.nativePageTurnSettledToken = settleToken
      })
  },
  armNativePageTurnSettle: token => {
    runtime.clearPageTurnLivePresentationTarget()
    runtime.pendingNativePageTurnSettleToken = String(token || '') || null
    runtime.nativePageTurnSettledToken = null
    runtime.pendingExactPageTurnSettlements.clear()
    runtime.activeExactPageTurnSettlementToken = null
    runtime.nativePageTurnSettledState = null
  },
  nativePageTurnSettledToken: () => runtime.nativePageTurnSettledToken,
  nativePageTurnSettledState: () => runtime.nativePageTurnSettledState,
  nativePageTurnPendingState: () => runtime.activeExactPageTurnSettlement(),
  pageTurnPreviewPresentationReceipt: () => runtime.pageTurnPreviewPresentationReceipt(),
  pageTurnLivePresentationReceipt: () => runtime.pageTurnLivePresentationReceipt(),
  beginPageTurnPreviewPreparation: (token, pageIndex, foregroundMutationGeneration) =>
    runtime.beginPageTurnPreviewPreparation(
      token,
      pageIndex,
      foregroundMutationGeneration
    ),
  beginPageTurnPreviewBatch: (token, pageIndexes, foregroundMutationGeneration) =>
    runtime.beginPageTurnPreviewBatch(
      token,
      pageIndexes,
      foregroundMutationGeneration
    ),
  pageTurnPreviewBatchState: token => runtime.pageTurnPreviewBatchState(token),
  advancePageTurnPreviewBatch: (token, pageIndex, foregroundMutationGeneration) =>
    runtime.advancePageTurnPreviewBatch(
      token,
      pageIndex,
      foregroundMutationGeneration
    ),
  cancelPageTurnPreviewBatch: (token, foregroundMutationGeneration) =>
    runtime.cancelPageTurnPreviewBatch(token, foregroundMutationGeneration),
  pageTurnPreviewState: token => runtime.pageTurnPreviewState(token),
  pageTurnPreviewContext: () => runtime.pageTurnPreviewContext(),
  pageTurnRasterDescriptor: pageIndex => runtime.pageTurnRasterDescriptor(pageIndex),
  pageTurnRasterPreparationPlan: pageIndex => runtime.pageTurnRasterPreparationPlan(pageIndex),
  pageTurnTransitionPlan: (physicalDirection, currentPageIndexOverride = null) =>
    runtime.pageTurnTransitionPlan(physicalDirection, currentPageIndexOverride),
  exposePageTurnPreviewFinal: (token, foregroundMutationGeneration) =>
    runtime.exposePageTurnPreviewFinal(token, foregroundMutationGeneration),
  confirmPageTurnPreviewPresentation: (token, foregroundMutationGeneration) =>
    runtime.confirmPageTurnPreviewPresentation(
      token,
      foregroundMutationGeneration
    ),
  restorePageTurnLiveComposition: (token, foregroundMutationGeneration) =>
    runtime.restorePageTurnLiveComposition(token, foregroundMutationGeneration),
  pageTurnCaptureGeometry: () => runtime.pageTurnCaptureGeometry(),
  readerContentActionAtPoint: (x, y, viewWidth, viewHeight) =>
    runtime.readerContentActionAtRootPoint(x, y, viewWidth, viewHeight)?.handled === true,
  activeMediaOverlaySnapshot: () =>
    runtime.mediaOverlayActiveFragment ? { ...runtime.mediaOverlayActiveFragment } : null,
  postOverlayFragmentActive: fragment => post({ type: 'overlayFragmentActive', ...fragment }),
  postOverlayFragmentInactive: (
    fragmentId,
    overlayRequestId = null,
    reason = null,
    coordinateMode = null
  ) => post({ type: 'overlayFragmentInactive', fragmentId, overlayRequestId, coordinateMode, reason }),
}

readerTrace('runtime:ready', { engine: 'foliate-js' })
log('module-loaded')
post({ type: 'ready' })
