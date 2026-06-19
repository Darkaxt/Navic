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
import { NavicReaderShellCoverMethods } from './navic-reader-shell-cover.js'
import { NavicReaderViewportMethods } from './navic-reader-viewport.js'
import { NavicReaderLocationMethods } from './navic-reader-location.js'

const ReaderSvgNamespace = 'http://www.w3.org/2000/svg'

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
  surfacePaperTextureFallbackDirection = null
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
  deferredReflowablePageTurn = null
  deferredReflowablePageTurnToken = 0
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
  dispatch: command => runtime.dispatch(command),
  readerContentActionAtPoint: (x, y, viewWidth, viewHeight) =>
    runtime.readerContentActionAtRootPoint(x, y, viewWidth, viewHeight)?.handled === true,
  postOverlayFragmentActive: fragment => post({ type: 'overlayFragmentActive', ...fragment }),
  postOverlayFragmentInactive: fragmentId => post({ type: 'overlayFragmentInactive', fragmentId }),
}

readerTrace('runtime:ready', { engine: 'foliate-js' })
log('module-loaded')
post({ type: 'ready' })
