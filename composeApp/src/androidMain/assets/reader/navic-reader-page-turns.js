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

const ViewportScrollStepRatio = 0.75

function progressTargetForSections(fraction) {
  const sectionCount = Number(this.view?.book?.sections?.length)
  if (!Number.isFinite(sectionCount) || sectionCount <= 0) return null
  const index = Math.floor(Math.min(1, Math.max(0, fraction)) * sectionCount)
  return Math.min(sectionCount - 1, Math.max(0, index))
}

function fixedLayoutCurrentPageIndex() {
  if (this.view?.isFixedLayout !== true) return null
  try {
    const index = Number(this.view?.renderer?.index)
    return Number.isFinite(index) ? Math.floor(index) : null
  } catch (error) {
    log('fixed-layout-index:unavailable', error?.message || String(error))
    return null
  }
}

function fixedLayoutNavigationBasePageIndex() {
  if (this.view?.isFixedLayout !== true) return null
  const navigationIndex = Number(this.fixedLayoutNavigationPageIndex)
  if (Number.isFinite(navigationIndex)) return Math.floor(navigationIndex)
  const committedPageIndex = Number(this.currentPagePosition?.pageIndex)
  if (Number.isFinite(committedPageIndex)) return Math.floor(committedPageIndex)
  return this.fixedLayoutCurrentPageIndex()
}

function syncFixedLayoutNavigationPageIndex(pagePosition) {
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

function fixedLayoutAdjacentPageTarget(direction) {
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

async function goToProgress(progress) {
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
      this.beginControlledRelocation('progress-seek')
      await this.view.goToFraction(fraction)
    } else if (progressTarget != null) {
      log('progress-seek:fallback-section', progressTarget)
      this.beginControlledRelocation('progress-seek')
      await this.view.goTo(progressTarget)
    } else {
      this.beginControlledRelocation('progress-seek')
      await this.view.goTo({ fraction })
    }
    this.scheduleControlledRelocationFallback('progress-seek')
    this.applyReaderViewportLayout('progress-seek')
    requestAnimationFrame(() => {
      this.logContentLayout('progress-seek')
      log('progress-seek:done', fraction)
    })
  } catch (error) {
    reportError(error, 'navigation_failed')
  }
}

async function goToChapterProgress(href, progress) {
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
      this.beginControlledRelocation('chapter-progress-seek')
      await this.view.renderer.goTo({ index, anchor: fraction })
      this.view.history?.pushState?.({ href: targetHref, chapterFraction: fraction })
    } else {
      this.beginControlledRelocation('chapter-progress-seek')
      await this.view.goTo(targetHref)
    }
    this.scheduleControlledRelocationFallback('chapter-progress-seek')
    this.applyReaderViewportLayout('chapter-progress-seek')
    requestAnimationFrame(() => {
      this.logContentLayout('chapter-progress-seek')
      log('chapter-progress-seek:done', targetHref, fraction)
    })
  } catch (error) {
    reportError(error, 'navigation_failed')
  }
}

function nextPage() {
  return this.turnPage('next')
}

function previousPage() {
  return this.turnPage('previous')
}

function currentLoadedSectionIndex() {
  const contentIndex = Number(this.view?.renderer?.getContents?.()?.[0]?.index)
  if (Number.isFinite(contentIndex)) return Math.floor(contentIndex)
  const detailIndex = Number(this.lastRelocateDetail?.section?.current ?? this.lastRelocateDetail?.index)
  return Number.isFinite(detailIndex) ? Math.floor(detailIndex) : null
}

function adjacentReadableSectionIndex(direction) {
  const sections = Array.from(this.view?.book?.sections || [])
  const current = this.currentLoadedSectionIndex()
  if (!sections.length || current == null) return null
  const step = direction === 'previous' ? -1 : 1
  for (let index = current + step; index >= 0 && index < sections.length; index += step) {
    const section = sections[index]
    if (readerSectionIsReadable(section) && !this.sectionTargetsCover(section, index)) return index
  }
  return null
}

function nativeDragPreviewAtSectionBoundary(renderer, direction) {
  if (!renderer || renderer.scrolled) return false
  const page = Number(renderer.page)
  const pages = Number(renderer.pages)
  const start = Number(renderer.start)
  const end = Number(renderer.end)
  const viewSize = Number(renderer.viewSize)
  if (!Number.isFinite(page) || !Number.isFinite(pages) || pages <= 0) return false
  if (direction === 'previous') {
    return page <= 1 || (Number.isFinite(start) && start <= 2)
  }
  return page >= pages - 2 ||
    (Number.isFinite(end) && Number.isFinite(viewSize) && viewSize - end <= 2)
}

function readerRendererReadyForPageDrag(renderer) {
  if (!renderer || renderer.scrolled || typeof renderer.scrollBy !== 'function') return false
  try {
    const size = Number(renderer.size)
    const viewSize = Number(renderer.viewSize)
    const page = Number(renderer.page)
    const pages = Number(renderer.pages)
    const start = Number(renderer.start)
    const end = Number(renderer.end)
    return Number.isFinite(size) &&
      Number.isFinite(viewSize) &&
      Number.isFinite(page) &&
      Number.isFinite(pages) &&
      Number.isFinite(start) &&
      Number.isFinite(end) &&
      size > 0 &&
      viewSize > 0 &&
      pages > 0
  } catch (error) {
    readerTrace('page-drag-preview:renderer-not-ready', {
      message: error?.message || String(error),
    })
    return false
  }
}

function safeNativeDragPreviewAtSectionBoundary(renderer, direction) {
  if (!this.readerRendererReadyForPageDrag(renderer)) return false
  try {
    return this.nativeDragPreviewAtSectionBoundary(renderer, direction)
  } catch (error) {
    readerTrace('page-drag-preview:boundary-probe-skipped', {
      direction,
      message: error?.message || String(error),
    })
    return false
  }
}

function ensurePageDragPreviewLayer() {
  let layer = this.pageDragPreviewLayer
  if (!layer || !readerRoot.contains(layer)) {
    layer = document.createElement('div')
    layer.dataset.navicPageDragPreviewLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    this.pageDragPreviewLayer = layer
    readerRoot.append(layer)
  }
  let frame = this.pageDragPreviewFrame
  if (!frame || !layer.contains(frame)) {
    frame = document.createElement('iframe')
    frame.dataset.navicPageDragPreviewFrame = 'true'
    frame.setAttribute('aria-hidden', 'true')
    frame.setAttribute('tabindex', '-1')
    layer.replaceChildren(frame)
    this.pageDragPreviewFrame = frame
    this.pageDragPreviewTargetKey = ''
  }
  return { layer, frame }
}

function removePageDragPreviewLayer() {
  this.pageDragPreviewLoadToken += 1
  this.pageDragPreviewLayer?.remove?.()
  this.pageDragPreviewLayer = null
  this.pageDragPreviewFrame = null
  this.pageDragPreviewTargetKey = ''
  this.pageDragPreviewReadyKey = ''
}

function pageDragPreviewDimensions(viewWidth = null) {
  const viewport = readerViewportSize()
  return {
    width: Math.max(1, Math.round(Number(viewWidth) || viewport.width || window.innerWidth || 1)),
    height: Math.max(1, Math.round(viewport.height || window.innerHeight || 1)),
  }
}

function buildPageDragPreviewTargetKey(targetIndex, direction, width, height) {
  return `${targetIndex}:${direction}:${width}x${height}`
}

function loadPageDragPreviewFrame(frame, targetIndex, direction, token, targetKey) {
  const section = this.view?.book?.sections?.[targetIndex]
  if (!frame || !section?.load) return
  Promise.resolve(section.load())
    .then(async src => {
      if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return
      await new Promise((resolve, reject) => {
        frame.onload = () => resolve()
        frame.onerror = () => reject(new Error(`Failed to load page drag preview section ${targetIndex}`))
        if (typeof src === 'string' && src.startsWith('blob:')) {
          fetch(src)
            .then(response => response.ok ? response.text() : Promise.reject(new Error(`HTTP ${response.status}`)))
            .then(html => {
              if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return resolve()
              frame.removeAttribute('src')
              frame.srcdoc = html
            })
            .catch(() => {
              if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return resolve()
              frame.removeAttribute('srcdoc')
              frame.src = src
            })
        } else {
          frame.removeAttribute('srcdoc')
          frame.src = src
        }
      })
      if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return
      const doc = frame.contentDocument
      this.applyDocumentDirection(doc, this.readerDirectionModeValue)
      this.applyDocumentTheme(doc, this.readerSettings, targetIndex)
      if (doc?.documentElement) {
        setStylesImportant(doc.documentElement, {
          width: '100%',
          height: '100%',
          'min-height': '100%',
          overflow: 'hidden',
        })
      }
      if (doc?.body) {
        setStylesImportant(doc.body, {
          margin: '0',
          'box-sizing': 'border-box',
          overflow: 'hidden',
        })
      }
      if (direction === 'previous') {
        requestAnimationFrame(() => {
          try {
            const scrollHeight = doc?.documentElement?.scrollHeight || doc?.body?.scrollHeight || 0
            frame.contentWindow?.scrollTo?.(0, scrollHeight)
          } catch {
            // The preview is best-effort and pointer-events disabled; navigation remains authoritative.
          }
        })
      }
      readerTrace('page-drag-preview:underlay-loaded', {
        targetIndex,
        direction,
        href: section?.href || section?.id || '',
      })
      if (targetKey && token === this.pageDragPreviewLoadToken && frame === this.pageDragPreviewFrame) {
        this.pageDragPreviewReadyKey = targetKey
      }
    })
    .catch(error => {
      if (token !== this.pageDragPreviewLoadToken || frame !== this.pageDragPreviewFrame) return
      readerTrace('page-drag-preview:underlay-load-failed', {
        targetIndex,
        direction,
        message: error?.message || String(error),
      })
    })
}

function ensurePageDragPreviewTarget({ direction, viewWidth = null, hidden = false }) {
  if (!direction) return null
  const targetIndex = this.adjacentReadableSectionIndex(direction)
  if (targetIndex == null) return null
  const { width, height } = this.pageDragPreviewDimensions(viewWidth)
  const side = direction === 'previous' ? 'left' : 'right'
  const palette = readerThemePalette(this.readerSettings?.theme)
  const { layer, frame } = this.ensurePageDragPreviewLayer()
  const targetKey = this.buildPageDragPreviewTargetKey(targetIndex, direction, width, height)

  layer.dataset.navicPageDragPreviewDirection = direction
  layer.dataset.navicPageDragPreviewSide = side
  layer.dataset.navicPageDragPreviewTargetIndex = String(targetIndex)
  setStylesImportant(layer, {
    position: 'fixed',
    top: '0px',
    left: hidden ? '-1px' : '0px',
    width: hidden ? '1px' : `${width}px`,
    height: `${height}px`,
    'min-height': `${height}px`,
    overflow: 'hidden',
    opacity: hidden ? '0' : '1',
    'z-index': '2147483642',
    'pointer-events': 'none',
    background: palette.background,
    'background-color': palette.background,
    color: palette.foreground,
    'box-sizing': 'border-box',
  })
  setStylesImportant(frame, {
    position: 'absolute',
    top: '0px',
    left: '0px',
    width: `${width}px`,
    height: `${height}px`,
    border: '0',
    margin: '0',
    padding: '0',
    overflow: 'hidden',
    background: palette.background,
    'background-color': palette.background,
    color: palette.foreground,
    'pointer-events': 'none',
  })

  if (this.pageDragPreviewTargetKey !== targetKey) {
    this.pageDragPreviewTargetKey = targetKey
    this.pageDragPreviewReadyKey = ''
    const token = ++this.pageDragPreviewLoadToken
    this.loadPageDragPreviewFrame(frame, targetIndex, direction, token, targetKey)
  }
  return { layer, frame, targetIndex, targetKey, side, width, height, palette }
}

function preloadPageDragPreviewTargets(label = 'unknown') {
  if (!this.view || this.shellCoverVisible) {
    this.removePageDragPreviewLayer()
    return
  }
  const renderer = this.view?.renderer
  if (!this.readerRendererReadyForPageDrag(renderer)) {
    this.removePageDragPreviewLayer()
    return
  }
  const directions = ['previous', 'next']
    .filter(direction => this.safeNativeDragPreviewAtSectionBoundary(renderer, direction))
  if (!directions.length) {
    this.removePageDragPreviewLayer()
    return
  }
  const direction = directions.includes('next') ? 'next' : directions[0]
  const preview = this.ensurePageDragPreviewTarget({ direction, hidden: true })
  if (preview) {
    readerTrace('page-drag-preview:preload', {
      label,
      direction,
      targetIndex: preview.targetIndex,
      currentIndex: this.currentLoadedSectionIndex(),
    })
  }
}

function updatePageDragPreviewLayer({ direction, deltaX, viewWidth, renderer }) {
  if (!direction || !this.safeNativeDragPreviewAtSectionBoundary(renderer, direction)) {
    this.removePageDragPreviewLayer()
    return
  }
  const preview = this.ensurePageDragPreviewTarget({ direction, viewWidth })
  if (!preview) {
    this.removePageDragPreviewLayer()
    return
  }
  const { layer, frame, targetIndex, targetKey, side, width, height, palette } = preview
  const exposedWidth = Math.max(1, Math.min(width, Math.round(Math.abs(Number(deltaX) || 0))))
  const left = side === 'right' ? width - exposedWidth : 0
  layer.dataset.navicPageDragPreviewExposedWidth = String(exposedWidth)
  layer.dataset.navicPageDragPreviewReady = String(this.pageDragPreviewReadyKey === targetKey)
  setStylesImportant(layer, {
    position: 'fixed',
    top: '0px',
    left: `${left}px`,
    width: `${exposedWidth}px`,
    height: `${height}px`,
    'min-height': `${height}px`,
    overflow: 'hidden',
    'z-index': '2147483642',
    'pointer-events': 'none',
    background: palette.background,
    'background-color': palette.background,
    color: palette.foreground,
    'box-sizing': 'border-box',
  })
  setStylesImportant(frame, {
    position: 'absolute',
    top: '0px',
    left: side === 'right' ? `-${width - exposedWidth}px` : '0px',
    width: `${width}px`,
    height: `${height}px`,
    border: '0',
    margin: '0',
    padding: '0',
    overflow: 'hidden',
    background: palette.background,
    'background-color': palette.background,
    color: palette.foreground,
    'pointer-events': 'none',
  })
  readerTrace('page-drag-preview:underlay', {
    direction,
    side,
    targetIndex,
    exposedWidth,
    ready: this.pageDragPreviewReadyKey === targetKey,
    currentIndex: this.currentLoadedSectionIndex(),
  })
}

function previewPageDrag(command) {
  if (!this.view || this.shellCoverVisible) return
  const renderer = this.view?.renderer
  if (!this.readerRendererReadyForPageDrag(renderer)) return
  const phase = command?.phase === 'release'
    ? 'release'
    : command?.phase === 'cancel'
      ? 'cancel'
      : 'update'
  if (phase === 'cancel') {
    const previousDeltaX = this.nativePageDragPreview?.renderer === renderer
      ? Number(this.nativePageDragPreview?.deltaX) || 0
      : 0
    if (previousDeltaX !== 0) renderer.scrollBy(previousDeltaX, 0)
    readerTrace('page-drag-preview:cancel', { deltaX: previousDeltaX })
    this.nativePageDragPreview = null
    this.removePageDragPreviewLayer()
    this.surfacePaperTextureTurnDirection = null
    this.renderSurfacePaperTextureLayers()
    return
  }
  if (phase === 'release') {
    const previousDeltaX = this.nativePageDragPreview?.renderer === renderer
      ? Number(this.nativePageDragPreview?.deltaX) || 0
      : 0
    if (previousDeltaX !== 0) renderer.scrollBy(previousDeltaX, 0)
    readerTrace('page-drag-preview:release', { deltaX: previousDeltaX })
    this.nativePageDragPreview = null
    this.removePageDragPreviewLayer()
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
    readerDirection: this.effectiveReaderDirection?.() || this.readerDirectionModeValue,
    threshold: 1,
  })
  if (textureDirection) {
    this.surfacePaperTextureTurnDirection = textureDirection
    this.surfacePaperTextureFallbackDirection = textureDirection
    readerTrace('texture:drag-direction', {
      direction: textureDirection,
      source: 'native-preview',
    })
  }
  const lastDeltaX = this.nativePageDragPreview?.renderer === renderer
    ? Number(this.nativePageDragPreview?.deltaX) || 0
    : 0
  const incrementalDeltaX = deltaX - lastDeltaX
  if (incrementalDeltaX !== 0) renderer.scrollBy(-incrementalDeltaX, 0)
  this.updatePageDragPreviewLayer({
    direction: textureDirection,
    deltaX,
    viewWidth: command?.viewWidth,
    renderer,
  })
  this.nativePageDragPreview = phase === 'release'
    ? null
    : { deltaX, renderer }
  readerTrace('page-drag-preview', {
    phase,
    deltaX,
    incrementalDeltaX,
    start: renderer.start,
    end: renderer.end,
    viewSize: renderer.viewSize,
  })
}

async function scrollViewport(direction) {
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

function turnPage(direction) {
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
    if (this.view?.isFixedLayout === true && this.pageTurnDirection === direction) {
      log('page-turn:coalesced', direction)
      readerTrace('page-turn:coalesced', {
        direction,
        navigationIndex: this.fixedLayoutNavigationPageIndex,
        rendererIndex: this.fixedLayoutCurrentPageIndex(),
      })
      return this.pageTurnPromise
    }
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

function startPageTurn(direction) {
  readerTrace('page-turn:start-request', {
    direction,
    hasPromise: Boolean(this.pageTurnPromise),
    queueLength: this.pageTurnQueue.length,
  })
  this.cancelPendingCommittedRelocation()
  this.pageTurnInProgress = true
  this.pageTurnDirection = direction
  this.surfacePaperTextureTurnDirection = direction
  this.surfacePaperTextureFallbackDirection = direction
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

function startNextQueuedPageTurn() {
  if (this.pageTurnPromise || this.pageTurnQueue.length === 0) return
  const next = this.pageTurnQueue.shift()
  this.startPageTurn(next.direction).then(next.resolve, next.reject)
}

function issueReflowablePageTurn(direction) {
  const navigationPromise = direction === 'next'
    ? this.view?.next?.()
    : this.view?.prev?.()
  navigationPromise?.catch?.(error => reportError(error, 'navigation_failed'))
}

async function performPageTurn(direction) {
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
      this.beginControlledRelocation(`page-turn:${direction}`)
      this.issueReflowablePageTurn(direction)
      this.scheduleControlledRelocationFallback(`page-turn:${direction}`)
    } else {
      this.beginControlledRelocation(`page-turn:${direction}`)
      this.issueReflowablePageTurn(direction)
      this.scheduleControlledRelocationFallback(`page-turn:${direction}`)
    }
    this.recentPageTurnDirection = direction
    this.applyReaderViewportLayout(`page-turn:${direction}`)
    requestAnimationFrame(() => {
      this.logContentLayout(`page-turn:${direction}`)
      if (directFixedLayoutPageTarget != null) {
        this.scheduleCommittedRelocation(this.lastRelocateDetail, `page-turn:${direction}`)
      }
      log('page-turn:done', direction)
    })
  } catch (error) {
    reportError(error, 'navigation_failed')
  }
}

function attachScrolledEdgeTurnGestures(doc) {
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

function effectiveReaderDirection() {
  if (this.readerDirectionModeValue === ReaderDirectionLtr || this.readerDirectionModeValue === ReaderDirectionRtl) {
    return this.readerDirectionModeValue
  }
  return this.view?.book?.dir === ReaderDirectionRtl ? ReaderDirectionRtl : ReaderDirectionLtr
}

function turnScrolledEdgePage(deltaY) {
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
    post({ type: 'pullUp' })
    void this.nextPage()
    return true
  }
  return false
}

function overrideReaderRendererValue(renderer, key, value) {
  const ownDescriptor = Object.getOwnPropertyDescriptor(renderer, key)
  Object.defineProperty(renderer, key, {
    configurable: true,
    value,
  })
  return () => {
    if (ownDescriptor) {
      Object.defineProperty(renderer, key, ownDescriptor)
    } else {
      delete renderer[key]
    }
  }
}

function diagnosticScrolledEdgePullUp() {
  const renderer = this.view?.renderer
  if (!renderer) {
    return { posted: false, reason: 'missing-renderer' }
  }
  const restoreRendererValues = [
    overrideReaderRendererValue(renderer, 'scrolled', true),
    overrideReaderRendererValue(renderer, 'start', 100),
    overrideReaderRendererValue(renderer, 'end', 1000),
    overrideReaderRendererValue(renderer, 'viewSize', 1000),
  ]
  try {
    const posted = this.turnScrolledEdgePage(-(ScrollEdgeTurnSwipeThreshold + 10))
    return {
      posted,
      reason: 'diagnostic-scrolled-edge-pull-up',
    }
  } finally {
    restoreRendererValues.reverse().forEach(restore => restore())
  }
}

async function turnFixedLayoutSwipePage(deltaX) {
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

export const NavicReaderPageTurnMethods = {
  progressTargetForSections,
  fixedLayoutCurrentPageIndex,
  fixedLayoutNavigationBasePageIndex,
  syncFixedLayoutNavigationPageIndex,
  fixedLayoutAdjacentPageTarget,
  goToProgress,
  goToChapterProgress,
  nextPage,
  previousPage,
  currentLoadedSectionIndex,
  adjacentReadableSectionIndex,
  nativeDragPreviewAtSectionBoundary,
  readerRendererReadyForPageDrag,
  safeNativeDragPreviewAtSectionBoundary,
  ensurePageDragPreviewLayer,
  removePageDragPreviewLayer,
  pageDragPreviewDimensions,
  buildPageDragPreviewTargetKey,
  loadPageDragPreviewFrame,
  ensurePageDragPreviewTarget,
  preloadPageDragPreviewTargets,
  updatePageDragPreviewLayer,
  previewPageDrag,
  scrollViewport,
  turnPage,
  startPageTurn,
  startNextQueuedPageTurn,
  issueReflowablePageTurn,
  performPageTurn,
  attachScrolledEdgeTurnGestures,
  effectiveReaderDirection,
  turnScrolledEdgePage,
  diagnosticScrolledEdgePullUp,
  turnFixedLayoutSwipePage
}
