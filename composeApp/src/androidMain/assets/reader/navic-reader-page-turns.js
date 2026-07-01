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
  readerPageDragPreviewMotion,
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

async function goToChapterProgress(href, progress, chapterPageIndex = null, chapterPageCount = null) {
  if (!this.view) return
  const targetHref = String(href || '').trim()
  if (!targetHref) return
  const numericProgress = Number(progress)
  const fraction = Number.isFinite(numericProgress)
    ? Math.min(1, Math.max(0, numericProgress))
    : 0
  const targetPageIndex = Number(chapterPageIndex)
  const targetPageCount = Number(chapterPageCount)
  const hasExactTargetPage =
    Number.isFinite(targetPageIndex) &&
    Number.isFinite(targetPageCount) &&
    targetPageIndex >= 0 &&
    targetPageCount > 1
  const targetFraction = hasExactTargetPage
    ? Math.min(1, Math.max(0, targetPageIndex / (targetPageCount - 1)))
    : fraction
  try {
    log('chapter-progress-seek:start', targetHref, fraction, targetPageIndex, targetPageCount)
    const resolved = await Promise.resolve(
      this.view.resolveNavigation?.(targetHref) ||
      this.view.book?.resolveHref?.(targetHref)
    )
    const index = Number(resolved?.index)
    const targetAnchor = this.reflowableChapterProgressAnchor(targetFraction)
    if (Number.isFinite(index) && this.view.renderer?.goTo) {
      this.beginControlledRelocation('chapter-progress-seek')
      await this.view.renderer.goTo({ index, anchor: targetAnchor })
      this.view.history?.pushState?.({
        href: targetHref,
        chapterFraction: targetFraction,
        chapterPageIndex: hasExactTargetPage ? targetPageIndex : undefined,
        chapterPageCount: hasExactTargetPage ? targetPageCount : undefined,
      })
    } else {
      this.beginControlledRelocation('chapter-progress-seek')
      await this.view.goTo(targetHref)
    }
    this.scheduleControlledRelocationFallback('chapter-progress-seek')
    this.applyReaderViewportLayout('chapter-progress-seek')
    requestAnimationFrame(() => {
      this.logContentLayout('chapter-progress-seek')
      log('chapter-progress-seek:done', targetHref, targetFraction)
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

function handleDuplicatePageTurnRelocation(_detail, reason) {
  const reasonText = String(reason || '')
  if (!reasonText.startsWith('page-turn:')) return false
  if (this.pageTurnDuplicateFallbackInProgress) return false
  const direction = reasonText.includes(':previous') ? 'previous' : 'next'
  const targetIndex = this.adjacentReadableSectionIndex(direction)
  const currentIndex = this.currentLoadedSectionIndex()
  if (targetIndex == null || targetIndex === currentIndex) return false
  const fallbackReason = `page-turn:${direction}:adjacent`
  log('page-turn:duplicate-adjacent-fallback', direction, `from=${currentIndex ?? 'n/a'}`, `to=${targetIndex}`)
  readerTrace('page-turn:duplicate-adjacent-fallback', {
    direction,
    currentIndex,
    targetIndex,
    reason: reasonText,
  })
  this.pageTurnDuplicateFallbackInProgress = true
  this.beginControlledRelocation(fallbackReason)
  const navigationPromise = this.view?.renderer?.goTo
    ? this.view.renderer.goTo({ index: targetIndex })
    : this.view?.goTo?.(targetIndex)
  Promise.resolve(navigationPromise)
    .catch(error => reportError(error, 'navigation_failed'))
    .finally(() => {
      this.pageTurnDuplicateFallbackInProgress = false
    })
  this.scheduleControlledRelocationFallback(fallbackReason)
  return true
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
  const lastVisualPage = this.reflowableLastVisualRendererPage(renderer)
  return page >= lastVisualPage ||
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

function positiveRect(rect) {
  return rect &&
    Number.isFinite(Number(rect.width)) &&
    Number.isFinite(Number(rect.height)) &&
    Number(rect.width) > 0 &&
    Number(rect.height) > 0
}

function readerReflowablePageTurnReadiness() {
  if (!this.view) return { ready: false, reason: 'missing-view' }
  if (this.view?.isFixedLayout === true) return { ready: true, reason: 'fixed-layout' }
  const renderer = this.view?.renderer
  if (!renderer) return { ready: false, reason: 'missing-renderer' }
  if (typeof renderer.getContents !== 'function') return { ready: false, reason: 'missing-contents-api' }
  if (typeof renderer.getBoundingClientRect !== 'function') return { ready: false, reason: 'missing-renderer-rect' }
  if (typeof this.view.getBoundingClientRect !== 'function') return { ready: false, reason: 'missing-view-rect' }
  try {
    const viewRect = this.view.getBoundingClientRect()
    const rendererRect = renderer.getBoundingClientRect()
    if (!positiveRect(viewRect)) return { ready: false, reason: 'empty-view-rect' }
    if (!positiveRect(rendererRect)) return { ready: false, reason: 'empty-renderer-rect' }

    const contents = renderer.getContents() || []
    const activeContent = contents.find(content => content?.doc)
    const doc = activeContent?.doc
    if (!doc) return { ready: false, reason: 'missing-content-document' }
    if (!doc.defaultView || !doc.documentElement || !doc.body) {
      return { ready: false, reason: 'incomplete-content-document' }
    }
    if (!doc.defaultView.frameElement?.isConnected) {
      return { ready: false, reason: 'detached-content-frame' }
    }

    const size = Number(renderer.size)
    const viewSize = Number(renderer.viewSize)
    if (!Number.isFinite(size) || size <= 0) return { ready: false, reason: 'invalid-renderer-size' }
    if (!Number.isFinite(viewSize) || viewSize <= 0) return { ready: false, reason: 'invalid-renderer-view-size' }
    if (!renderer.scrolled) {
      const pages = Number(renderer.pages)
      const page = Number(renderer.page)
      if (!Number.isFinite(pages) || pages <= 0) return { ready: false, reason: 'invalid-renderer-pages' }
      if (!Number.isFinite(page)) return { ready: false, reason: 'invalid-renderer-page' }
    }
    return { ready: true, reason: 'ready' }
  } catch (error) {
    return {
      ready: false,
      reason: 'readiness-exception',
      message: error?.message || String(error),
    }
  }
}

function readerReflowablePageTurnReady() {
  return this.readerReflowablePageTurnReadiness().ready
}

function clearDeferredReflowablePageTurn() {
  const pending = this.deferredReflowablePageTurn
  if (!pending) return
  this.deferredReflowablePageTurn = null
  pending.cleanup?.()
}

function retryDeferredReflowablePageTurn(direction) {
  this.clearDeferredReflowablePageTurn()
  const token = ++this.deferredReflowablePageTurnToken
  const renderer = this.view?.renderer
  const resizeObserver = typeof ResizeObserver === 'function'
    ? new ResizeObserver(() => attempt())
    : null
  const cleanupCallbacks = []
  const addCleanup = cleanup => cleanupCallbacks.push(cleanup)
  const cleanup = () => {
    while (cleanupCallbacks.length) cleanupCallbacks.pop()?.()
  }
  const attempt = () => {
    requestAnimationFrame(() => {
      if (this.deferredReflowablePageTurn?.token !== token) return
      const readiness = this.readerReflowablePageTurnReadiness()
      if (!readiness.ready) {
        readerTrace('page-turn:deferred-still-not-ready', {
          direction,
          reason: readiness.reason,
          message: readiness.message,
        })
        return
      }
      this.clearDeferredReflowablePageTurn()
      void this.startPageTurn(direction)
    })
  }

  this.deferredReflowablePageTurn = { direction, token, cleanup }
  if (resizeObserver) {
    const observe = target => {
      if (!target) return
      resizeObserver.observe(target)
      addCleanup(() => resizeObserver.unobserve(target))
    }
    observe(this.view)
    observe(renderer)
    addCleanup(() => resizeObserver.disconnect())
  }
  const addEventListenerCleanup = (target, type) => {
    if (!target?.addEventListener) return
    target.addEventListener(type, attempt)
    addCleanup(() => target.removeEventListener(type, attempt))
  }
  addEventListenerCleanup(renderer, 'load')
  addEventListenerCleanup(renderer, 'relocate')
  addEventListenerCleanup(window, 'resize')
  addEventListenerCleanup(window.visualViewport, 'resize')
  attempt()
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
  const ensureSheet = role => {
    let sheet = layer.querySelector(`[data-navic-page-curl-sheet="${role}"]`)
    if (!sheet) {
      sheet = document.createElement('div')
      sheet.dataset.navicPageCurlSheet = role
      sheet.setAttribute('aria-hidden', 'true')
      sheet.style.pointerEvents = 'none'
      layer.append(sheet)
    }
    return sheet
  }
  const underneath = ensureSheet('underneath')
  const turningFront = ensureSheet('turning-front')
  const turningBack = ensureSheet('turning-back')
  ensureSheet('cast-shadow')
  const ensureSnapshot = (sheet, role) => {
    let snapshot = sheet.querySelector(`[data-navic-page-curl-snapshot="${role}"]`)
    if (!snapshot) {
      snapshot = document.createElement('iframe')
      snapshot.dataset.navicPageCurlSnapshot = role
      snapshot.dataset.navicPageCurlSnapshotReady = 'false'
      snapshot.setAttribute('aria-hidden', 'true')
      snapshot.setAttribute('tabindex', '-1')
      snapshot.style.pointerEvents = 'none'
      sheet.append(snapshot)
    }
    return snapshot
  }
  ensureSnapshot(turningFront, 'front')
  ensureSnapshot(turningBack, 'back')
  let frame = this.pageDragPreviewFrame
  if (!frame || !underneath.contains(frame)) {
    frame = document.createElement('iframe')
    frame.dataset.navicPageDragPreviewFrame = 'true'
    frame.setAttribute('aria-hidden', 'true')
    frame.setAttribute('tabindex', '-1')
    underneath.replaceChildren(frame)
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

function pageDragPreviewDimensions(viewWidth = null, viewHeight = null) {
  const viewport = readerViewportSize()
  return {
    width: Math.max(1, Math.round(Number(viewWidth) || viewport.width || window.innerWidth || 1)),
    height: Math.max(1, Math.round(Number(viewHeight) || viewport.height || window.innerHeight || 1)),
  }
}

function readerPageDragCurlMetrics({ direction, deltaX, deltaY, width, height, vertical }) {
  const axisDistance = Math.abs(Number(vertical ? deltaY : deltaX) || 0)
  const axisSize = Math.max(1, Number(vertical ? height : width) || 1)
  const progress = Math.max(0, Math.min(1, axisDistance / axisSize))
  const eased = progress < 0.5
    ? 2 * progress * progress
    : 1 - Math.pow(-2 * progress + 2, 2) / 2
  const sign = direction === 'previous' ? 1 : -1
  const angleLimit = vertical ? 72 : 88
  const angle = sign * angleLimit * eased
  const frontShadow = 0.10 + Math.sin(Math.PI * progress) * 0.30
  const spineShadow = 0.04 + progress * 0.08
  const shadowAlpha = Math.min(0.34, 0.08 + Math.sin(Math.PI * progress) * 0.22)
  const curlWidth = 16 + Math.sin(Math.PI * progress) * 36
  return { progress, eased, angle, frontShadow, spineShadow, shadowAlpha, curlWidth }
}

function applyPageDragCurlMetrics(layer, { direction, deltaX, deltaY, width, height, vertical }) {
  if (!layer) return null
  const metrics = readerPageDragCurlMetrics({ direction, deltaX, deltaY, width, height, vertical })
  layer.dataset.navicPageDragPreviewCurl = 'true'
  layer.dataset.navicPageDragPreviewCurlProgress = metrics.progress.toFixed(3)
  layer.dataset.navicPageDragPreviewCurlDirection = direction || ''
  const origin = vertical
    ? (direction === 'next' ? 'center bottom' : 'center top')
    : (direction === 'next' ? 'left center' : 'right center')
  const transform = vertical
    ? `perspective(1800px) rotateX(${metrics.angle.toFixed(2)}deg)`
    : `perspective(1800px) rotateY(${metrics.angle.toFixed(2)}deg)`
  layer.style.setProperty('--navic-page-curl-progress', metrics.progress.toFixed(3))
  layer.style.setProperty('--navic-page-curl-eased', metrics.eased.toFixed(3))
  layer.style.setProperty('--navic-page-curl-angle', `${metrics.angle.toFixed(2)}deg`)
  layer.style.setProperty('--navic-page-curl-width', `${metrics.curlWidth.toFixed(1)}px`)
  layer.style.setProperty('--navic-page-curl-front-shadow', metrics.frontShadow.toFixed(3))
  layer.style.setProperty('--navic-page-curl-spine-shadow', metrics.spineShadow.toFixed(3))
  layer.style.setProperty('--navic-page-curl-shadow-alpha', metrics.shadowAlpha.toFixed(3))
  layer.style.setProperty('--navic-page-curl-origin', origin)
  layer.style.setProperty('--navic-page-curl-transform', transform)
  return metrics
}

function applyPageDragCurlSheet(layer, { direction, width, height, vertical, palette }) {
  if (!layer) return null
  const mode = vertical || width < height * 1.12 ? 'single' : 'spread'
  const progress = Math.max(0, Math.min(1, Number(layer.style.getPropertyValue('--navic-page-curl-progress')) || 0))
  const frontFaceOpacity = mode === 'single'
    ? (progress < 0.78 ? 1 : progress > 0.98 ? 0 : 1 - ((progress - 0.78) / 0.20))
    : (progress < 0.46 ? 1 : progress > 0.52 ? 0 : 1 - ((progress - 0.46) / 0.06))
  const backFaceOpacity = mode === 'single'
    ? 0
    : (progress < 0.50 ? 0 : progress > 0.56 ? 1 : ((progress - 0.50) / 0.06))
  const roles = ['underneath', 'turning-front', 'turning-back', 'cast-shadow']
  const children = Object.fromEntries(roles.map(role => [
    role,
    layer.querySelector(`[data-navic-page-curl-sheet="${role}"]`),
  ]))
  layer.dataset.navicPageCurlSheetMode = mode
  layer.dataset.navicPageCurlSheetRoles = roles.filter(role => children[role]).join(',')
  layer.style.setProperty('--navic-page-curl-front-face-opacity', Math.max(0, Math.min(1, frontFaceOpacity)).toFixed(3))
  layer.style.setProperty('--navic-page-curl-back-face-opacity', Math.max(0, Math.min(1, backFaceOpacity)).toFixed(3))
  layer.style.setProperty('--navic-page-curl-sheet-width', `${Math.max(1, Math.round(width || 1))}px`)
  layer.style.setProperty('--navic-page-curl-sheet-height', `${Math.max(1, Math.round(height || 1))}px`)
  const axisGradient = vertical
    ? 'linear-gradient(180deg, rgba(20,11,3,var(--navic-page-curl-spine-shadow)) 0, transparent 18%, transparent 70%, rgba(255,255,255,.30) 82%, rgba(30,15,4,var(--navic-page-curl-front-shadow)) 100%)'
    : 'linear-gradient(90deg, rgba(20,11,3,var(--navic-page-curl-spine-shadow)) 0, transparent 18%, transparent 70%, rgba(255,255,255,.30) 82%, rgba(30,15,4,var(--navic-page-curl-front-shadow)) 100%)'
  const reverseGradient = vertical
    ? 'linear-gradient(180deg, rgba(38,20,6,.24) 0, rgba(255,255,255,.18) 24%, transparent 58%, rgba(36,18,5,.16) 100%)'
    : 'linear-gradient(90deg, rgba(38,20,6,.24) 0, rgba(255,255,255,.18) 24%, transparent 58%, rgba(36,18,5,.16) 100%)'
  const sheetBase = {
    position: 'absolute',
    inset: '0',
    width: '100%',
    height: '100%',
    overflow: 'hidden',
    'pointer-events': 'none',
    'box-sizing': 'border-box',
  }
  if (children.underneath) {
    setStylesImportant(children.underneath, {
      ...sheetBase,
      'z-index': '1',
      background: palette?.background || 'transparent',
      'background-color': palette?.background || 'transparent',
    })
  }
  if (children['cast-shadow']) {
    setStylesImportant(children['cast-shadow'], {
      ...sheetBase,
      'z-index': '2',
      opacity: 'var(--navic-page-curl-progress)',
      background: vertical
        ? 'linear-gradient(180deg, rgba(0,0,0,var(--navic-page-curl-shadow-alpha)), transparent 72%)'
        : 'linear-gradient(90deg, rgba(0,0,0,var(--navic-page-curl-shadow-alpha)), transparent 72%)',
      'mix-blend-mode': 'multiply',
    })
  }
  if (children['turning-front']) {
    setStylesImportant(children['turning-front'], {
      ...sheetBase,
      'z-index': '3',
      opacity: 'var(--navic-page-curl-front-face-opacity)',
      background: axisGradient,
      'box-shadow': '0 0 var(--navic-page-curl-width) rgba(0,0,0,var(--navic-page-curl-shadow-alpha))',
      transform: 'var(--navic-page-curl-transform)',
      'transform-origin': 'var(--navic-page-curl-origin)',
      'backface-visibility': 'hidden',
      'will-change': 'transform, opacity',
    })
  }
  if (children['turning-back']) {
    setStylesImportant(children['turning-back'], {
      ...sheetBase,
      'z-index': '4',
      opacity: 'var(--navic-page-curl-back-face-opacity)',
      background: reverseGradient,
      transform: vertical
        ? 'var(--navic-page-curl-transform) rotateX(180deg)'
        : 'var(--navic-page-curl-transform) rotateY(180deg)',
      'transform-origin': 'var(--navic-page-curl-origin)',
      'backface-visibility': 'hidden',
      'will-change': 'transform, opacity',
    })
  }
  return { mode, roles }
}

function pageDragCurlSnapshotScroll(doc) {
  const win = doc?.defaultView
  const root = doc?.documentElement
  const body = doc?.body
  return {
    x: Math.max(0, Math.round(Number(win?.scrollX ?? root?.scrollLeft ?? body?.scrollLeft) || 0)),
    y: Math.max(0, Math.round(Number(win?.scrollY ?? root?.scrollTop ?? body?.scrollTop) || 0)),
  }
}

function pageDragCurlSnapshotHtml(doc) {
  const sourceRoot = doc?.documentElement
  if (!sourceRoot) return ''
  const clone = sourceRoot.cloneNode(true)
  for (const script of Array.from(clone.querySelectorAll?.('script') || [])) {
    script.remove()
  }
  let head = clone.querySelector?.('head')
  if (!head) {
    head = doc.createElement('head')
    clone.insertBefore(head, clone.firstChild)
  }
  const style = doc.createElement('style')
  style.setAttribute('data-navic-page-curl-snapshot-style', 'true')
  style.textContent = [
    'html,body{',
    'margin:0!important;',
    'padding:0!important;',
    'box-sizing:border-box!important;',
    'background-color:var(--reader-background, transparent)!important;',
    'pointer-events:none!important;',
    '}',
    '*,*::before,*::after{pointer-events:none!important;}',
    'img,svg,canvas,video{max-width:100%;}',
  ].join('')
  head.append(style)
  return `<!doctype html>\n${clone.outerHTML}`
}

function pageDragCurlSnapshotKey({ role, direction, width, height, content, doc, renderer }) {
  return [
    role,
    direction || '',
    Number(content?.index),
    String(doc?.URL || doc?.baseURI || ''),
    Number(renderer?.page),
    Number(renderer?.start),
    Number(renderer?.end),
    `${Math.round(Number(width) || 0)}x${Math.round(Number(height) || 0)}`,
  ].join('|')
}

function syncPageDragCurlSnapshotFrame(snapshot, doc, { role, direction, width, height, key, palette, onReady }) {
  if (!snapshot || !doc?.documentElement) {
    if (snapshot) {
      snapshot.dataset.navicPageCurlSnapshotReady = 'false'
      snapshot.dataset.navicPageCurlSnapshotTextLength = '0'
    }
    onReady?.()
    return false
  }
  const scroll = pageDragCurlSnapshotScroll(doc)
  setStylesImportant(snapshot, {
    position: 'absolute',
    top: '0px',
    left: '0px',
    width: `${Math.max(1, Math.round(Number(width) || 1))}px`,
    height: `${Math.max(1, Math.round(Number(height) || 1))}px`,
    border: '0',
    margin: '0',
    padding: '0',
    overflow: 'hidden',
    background: palette?.background || 'transparent',
    'background-color': palette?.background || 'transparent',
    color: palette?.foreground || 'inherit',
    'pointer-events': 'none',
  })
  snapshot.dataset.navicPageCurlSnapshotRole = role
  snapshot.dataset.navicPageCurlSnapshotDirection = direction || ''
  snapshot.dataset.navicPageCurlSnapshotScrollX = String(scroll.x)
  snapshot.dataset.navicPageCurlSnapshotScrollY = String(scroll.y)
  const markSnapshotReady = () => {
    try {
      const snapshotDoc = snapshot.contentDocument
      const text = snapshotDoc?.body?.textContent?.replace(/\s+/g, ' ').trim() || ''
      snapshot.contentWindow?.scrollTo?.(scroll.x, scroll.y)
      snapshot.dataset.navicPageCurlSnapshotReady = snapshotDoc?.body ? 'true' : 'false'
      snapshot.dataset.navicPageCurlSnapshotTextLength = String(text.length)
    } catch {
      snapshot.dataset.navicPageCurlSnapshotReady = 'false'
      snapshot.dataset.navicPageCurlSnapshotTextLength = '0'
    }
    onReady?.()
    return snapshot.dataset.navicPageCurlSnapshotReady === 'true'
  }
  if (snapshot.dataset.navicPageCurlSnapshotKey !== key) {
    snapshot.dataset.navicPageCurlSnapshotKey = key
    snapshot.dataset.navicPageCurlSnapshotReady = 'false'
    snapshot.dataset.navicPageCurlSnapshotTextLength = '0'
    snapshot.onload = () => { markSnapshotReady() }
    snapshot.removeAttribute('src')
    snapshot.srcdoc = pageDragCurlSnapshotHtml(doc)
    requestAnimationFrame(() => { markSnapshotReady() })
  } else if (snapshot.contentDocument?.body) {
    markSnapshotReady()
  }
  return snapshot.dataset.navicPageCurlSnapshotReady === 'true'
}

function syncPageDragCurlSnapshots(layer, { renderer, frame, ready, mode, direction, width, height, palette }) {
  if (!layer) return null
  const frontSnapshot = layer.querySelector('[data-navic-page-curl-sheet="turning-front"] [data-navic-page-curl-snapshot="front"]')
  const backSnapshot = layer.querySelector('[data-navic-page-curl-sheet="turning-back"] [data-navic-page-curl-snapshot="back"]')
  const recordSnapshotState = () => {
    const frontReady = frontSnapshot?.dataset.navicPageCurlSnapshotReady === 'true'
    const backReady = backSnapshot?.dataset.navicPageCurlSnapshotReady === 'true'
    layer.dataset.navicPageCurlSnapshots = [
      frontReady ? 'front' : '',
      backReady ? 'back' : '',
    ].filter(Boolean).join(',')
    layer.dataset.navicPageCurlSnapshotFront = String(frontReady)
    layer.dataset.navicPageCurlSnapshotBack = String(backReady)
  }
  const contents = typeof renderer?.getContents === 'function' ? (renderer.getContents() || []) : []
  const frontContent = contents.find(content => content?.doc)
  const frontDoc = frontContent?.doc
  const frontReady = this.syncPageDragCurlSnapshotFrame(frontSnapshot, frontDoc, {
    role: 'front',
    direction,
    width,
    height,
    key: pageDragCurlSnapshotKey({ role: 'front', direction, width, height, content: frontContent, doc: frontDoc, renderer }),
    palette,
    onReady: recordSnapshotState,
  })
  let backReady = false
  if (mode === 'spread' && ready && frame?.contentDocument?.documentElement) {
    const backDoc = frame.contentDocument
    backReady = this.syncPageDragCurlSnapshotFrame(backSnapshot, backDoc, {
      role: 'back',
      direction,
      width,
      height,
      key: pageDragCurlSnapshotKey({ role: 'back', direction, width, height, content: { index: Number(layer.dataset.navicPageDragPreviewTargetIndex) }, doc: backDoc, renderer }),
      palette,
      onReady: recordSnapshotState,
    })
  } else if (backSnapshot) {
    backSnapshot.dataset.navicPageCurlSnapshotReady = 'false'
    backSnapshot.dataset.navicPageCurlSnapshotTextLength = '0'
    setStylesImportant(backSnapshot, {
      opacity: '0',
      'pointer-events': 'none',
    })
  }
  recordSnapshotState()
  return { frontReady, backReady }
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
        const pending = this.pendingPageDragPreviewCommand
        if (pending?.targetKey === targetKey && pending?.command) {
          requestAnimationFrame(() => {
            if (this.pendingPageDragPreviewCommand?.targetKey !== targetKey) return
            if (this.pageDragPreviewReadyKey !== targetKey) return
            const command = this.pendingPageDragPreviewCommand.command
            this.pendingPageDragPreviewCommand = null
            this.previewPageDrag(command)
          })
        }
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

function ensurePageDragPreviewTarget({ direction, viewWidth = null, viewHeight = null, hidden = false }) {
  if (!direction) return null
  const targetIndex = this.adjacentReadableSectionIndex(direction)
  if (targetIndex == null) return null
  const { width, height } = this.pageDragPreviewDimensions(viewWidth, viewHeight)
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

function updatePageDragPreviewLayer({ direction, deltaX, deltaY, viewWidth, viewHeight, renderer }) {
  if (!direction || !this.safeNativeDragPreviewAtSectionBoundary(renderer, direction)) {
    this.removePageDragPreviewLayer()
    return
  }
  const preview = this.ensurePageDragPreviewTarget({ direction, viewWidth, viewHeight })
  if (!preview) {
    this.removePageDragPreviewLayer()
    return
  }
  const { layer, frame, targetIndex, targetKey, side, width, height, palette } = preview
  const ready = this.pageDragPreviewReadyKey === targetKey
  const vertical = this.readerFlowModeValue === ReaderFlowPagedVertical
  const exposedWidth = vertical ? width : Math.max(1, Math.min(width, Math.round(Math.abs(Number(deltaX) || 0))))
  const exposedHeight = vertical ? Math.max(1, Math.min(height, Math.round(Math.abs(Number(deltaY) || 0)))) : height
  const left = vertical || side !== 'right' ? 0 : width - exposedWidth
  const top = vertical && direction === 'next' ? height - exposedHeight : 0
  this.applyPageDragCurlMetrics(layer, {
    direction,
    deltaX,
    deltaY,
    width,
    height,
    vertical,
  })
  this.applyPageDragCurlSheet(layer, {
    direction,
    width,
    height,
    vertical,
    palette,
  })
  layer.dataset.navicPageDragPreviewReady = String(ready)
  this.syncPageDragCurlSnapshots(layer, {
    renderer,
    frame,
    ready,
    mode: layer.dataset.navicPageCurlSheetMode,
    direction,
    width,
    height,
    palette,
  })
  if (!ready) {
    const fallbackWidth = exposedWidth
    const fallbackHeight = exposedHeight
    layer.dataset.navicPageDragPreviewFallback = 'paper'
    layer.dataset.navicPageDragPreviewExposedWidth = String(fallbackWidth)
    layer.dataset.navicPageDragPreviewExposedHeight = String(fallbackHeight)
    setStylesImportant(layer, {
      position: 'fixed',
      top: `${top}px`,
      left: `${left}px`,
      width: `${fallbackWidth}px`,
      height: `${fallbackHeight}px`,
      'min-height': `${fallbackHeight}px`,
      overflow: 'hidden',
      opacity: '1',
      'z-index': '2147483642',
      'pointer-events': 'none',
      background: palette.background,
      'background-color': palette.background,
      color: palette.foreground,
      'box-sizing': 'border-box',
    })
    setStylesImportant(frame, {
      opacity: '0',
      'pointer-events': 'none',
    })
    readerTrace('page-drag-preview:underlay-waiting', {
      direction,
      side,
      targetIndex,
      exposedWidth: fallbackWidth,
      exposedHeight: fallbackHeight,
      currentIndex: this.currentLoadedSectionIndex(),
    })
    return false
  }
  const frameLeft = vertical || side !== 'right' ? '0px' : `-${width - exposedWidth}px`
  const frameTop = vertical && direction === 'next' ? `-${height - exposedHeight}px` : '0px'
  layer.dataset.navicPageDragPreviewFallback = 'false'
  layer.dataset.navicPageDragPreviewExposedWidth = String(exposedWidth)
  layer.dataset.navicPageDragPreviewExposedHeight = String(exposedHeight)
  setStylesImportant(layer, {
    position: 'fixed',
    top: `${top}px`,
    left: `${left}px`,
    width: `${exposedWidth}px`,
    height: `${exposedHeight}px`,
    'min-height': `${exposedHeight}px`,
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
    top: frameTop,
    left: frameLeft,
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
    opacity: '1',
  })
  readerTrace('page-drag-preview:underlay', {
    direction,
    side,
    targetIndex,
    exposedWidth,
    exposedHeight,
    ready,
    currentIndex: this.currentLoadedSectionIndex(),
  })
  return true
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
    const previousDelta = this.nativePageDragPreview?.renderer === renderer
      ? {
        x: Number(this.nativePageDragPreview?.deltaX) || 0,
        y: Number(this.nativePageDragPreview?.deltaY) || 0,
      }
      : { x: 0, y: 0 }
    if (previousDelta.x !== 0 || previousDelta.y !== 0) {
      renderer.scrollBy(previousDelta.x, previousDelta.y)
    }
    readerTrace('page-drag-preview:cancel', {
      deltaX: previousDelta.x,
      deltaY: previousDelta.y,
    })
    this.nativePageDragPreview = null
    this.pendingPageDragPreviewCommand = null
    this.removePageDragPreviewLayer()
    this.surfacePaperTextureTurnDirection = null
    this.surfacePaperTextureFallbackDirection = null
    this.renderSurfacePaperTextureLayers()
    return
  }
  if (phase === 'release') {
    const previousDelta = this.nativePageDragPreview?.renderer === renderer
      ? {
        x: Number(this.nativePageDragPreview?.deltaX) || 0,
        y: Number(this.nativePageDragPreview?.deltaY) || 0,
      }
      : { x: 0, y: 0 }
    const releaseDeltaX = Number(command?.deltaX)
    const releaseDeltaY = Number(command?.deltaY)
    const releaseTextureDirection = readerPaperTextureDragDirection({
      deltaX: Number.isFinite(releaseDeltaX) ? releaseDeltaX : previousDelta.x,
      deltaY: Number.isFinite(releaseDeltaY) ? releaseDeltaY : previousDelta.y,
      flowMode: this.readerFlowModeValue,
      readerDirection: this.effectiveReaderDirection?.() || this.readerDirectionModeValue,
      threshold: 1,
    })
    if (releaseTextureDirection) {
      this.surfacePaperTextureFallbackDirection = releaseTextureDirection
      readerTrace('texture:drag-direction', {
        direction: releaseTextureDirection,
        source: 'native-preview-release',
      })
    }
    if (previousDelta.x !== 0 || previousDelta.y !== 0) {
      renderer.scrollBy(previousDelta.x, previousDelta.y)
    }
    readerTrace('page-drag-preview:release', {
      deltaX: previousDelta.x,
      deltaY: previousDelta.y,
    })
    this.nativePageDragPreview = null
    this.pendingPageDragPreviewCommand = null
    this.removePageDragPreviewLayer()
    this.surfacePaperTextureTurnDirection = null
    this.renderSurfacePaperTextureLayers()
    return
  }
  const deltaX = Number(command?.deltaX)
  const deltaY = Number(command?.deltaY)
  if (!Number.isFinite(deltaX) && !Number.isFinite(deltaY)) return
  const currentDeltaX = Number.isFinite(deltaX) ? deltaX : 0
  const currentDeltaY = Number.isFinite(deltaY) ? deltaY : 0
  const textureDirection = readerPaperTextureDragDirection({
    deltaX: currentDeltaX,
    deltaY: currentDeltaY,
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
  const lastDeltaY = this.nativePageDragPreview?.renderer === renderer
    ? Number(this.nativePageDragPreview?.deltaY) || 0
    : 0
  const { incrementalDelta } = readerPageDragPreviewMotion({
    deltaX: currentDeltaX,
    deltaY: currentDeltaY,
    lastDeltaX,
    lastDeltaY,
    flowMode: this.readerFlowModeValue,
  })
  const boundaryDirection = textureDirection && this.safeNativeDragPreviewAtSectionBoundary(renderer, textureDirection)
    ? textureDirection
    : ''
  let waitingForBoundaryPreview = false
  if (boundaryDirection) {
    const preview = this.ensurePageDragPreviewTarget({
      direction: boundaryDirection,
      viewWidth: command?.viewWidth,
      viewHeight: command?.viewHeight,
      hidden: true,
    })
    const previewReady = preview && this.pageDragPreviewReadyKey === preview.targetKey
    if (!previewReady) {
      this.pendingPageDragPreviewCommand = preview
        ? {
          targetKey: preview.targetKey,
          command: {
            type: 'previewPageDrag',
            phase: 'update',
            deltaX: currentDeltaX,
            deltaY: currentDeltaY,
            viewWidth: command?.viewWidth,
            viewHeight: command?.viewHeight,
          },
        }
        : null
      waitingForBoundaryPreview = true
      readerTrace('page-drag-preview:underlay-waiting', {
        direction: boundaryDirection,
        targetIndex: preview?.targetIndex ?? null,
        currentIndex: this.currentLoadedSectionIndex(),
      })
    }
  }
  this.updatePageDragPreviewLayer({
    direction: textureDirection,
    deltaX: currentDeltaX,
    deltaY: currentDeltaY,
    viewWidth: command?.viewWidth,
    viewHeight: command?.viewHeight,
    renderer,
  })
  if (incrementalDelta.x !== 0 || incrementalDelta.y !== 0) {
    renderer.scrollBy(-incrementalDelta.x, -incrementalDelta.y)
    this.syncSurfacePaperTextureScrollOffset('page-drag-preview')
  }
  this.nativePageDragPreview = phase === 'release'
    ? null
    : { deltaX: currentDeltaX, deltaY: currentDeltaY, renderer }
  readerTrace('page-drag-preview', {
    phase,
    deltaX: currentDeltaX,
    deltaY: currentDeltaY,
    incrementalDeltaX: incrementalDelta.x,
    incrementalDeltaY: incrementalDelta.y,
    start: renderer.start,
    end: renderer.end,
    viewSize: renderer.viewSize,
    source: waitingForBoundaryPreview ? 'boundary-preview-loading' : 'native-preview',
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
  const readiness = this.readerReflowablePageTurnReadiness()
  if (!readiness.ready) {
    log('page-turn:deferred-renderer-not-ready', direction, readiness.reason)
    readerTrace('page-turn:deferred-renderer-not-ready', {
      direction,
      reason: readiness.reason,
      message: readiness.message,
    })
    this.applyReaderViewportLayout(`page-turn:${direction}:deferred`)
    this.retryDeferredReflowablePageTurn(direction)
    return false
  }
  const navigationPromise = direction === 'next'
    ? this.view?.next?.()
    : this.view?.prev?.()
  navigationPromise?.catch?.(error => reportError(error, 'navigation_failed'))
  return true
}

async function performPageTurn(direction) {
  if (!this.view) return
  try {
    log('page-turn:start', direction)
    const directFixedLayoutPageTarget = this.fixedLayoutAdjacentPageTarget(direction)
    let pageTurnIssued = true
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
      await this.view.goTo({ index: directFixedLayoutPageTarget })
    } else if (direction === 'next') {
      this.beginControlledRelocation(`page-turn:${direction}`)
      pageTurnIssued = this.issueReflowablePageTurn(direction)
      if (pageTurnIssued) {
        this.scheduleControlledRelocationFallback(`page-turn:${direction}`)
      } else {
        this.controlledRelocateReason = null
      }
    } else {
      this.beginControlledRelocation(`page-turn:${direction}`)
      pageTurnIssued = this.issueReflowablePageTurn(direction)
      if (pageTurnIssued) {
        this.scheduleControlledRelocationFallback(`page-turn:${direction}`)
      } else {
        this.controlledRelocateReason = null
      }
    }
    if (!pageTurnIssued) return
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
  }, { capture: true, passive: true })
  doc.addEventListener('touchmove', event => {
    if (!touchState || event.touches?.length > 1) {
      touchState = null
      return
    }
    const touch = event.changedTouches?.[0]
    if (!touch) return
    touchState.lastX = touch.screenX ?? touch.clientX ?? touchState.x
    touchState.lastY = touch.screenY ?? touch.clientY ?? touchState.y
  }, { capture: true, passive: true })
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
  }, { capture: true, passive: true })
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
    post({ type: 'pullUp', source: 'scrolled-edge-swipe' })
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
  handleDuplicatePageTurnRelocation,
  nativeDragPreviewAtSectionBoundary,
  readerRendererReadyForPageDrag,
  safeNativeDragPreviewAtSectionBoundary,
  readerReflowablePageTurnReadiness,
  readerReflowablePageTurnReady,
  clearDeferredReflowablePageTurn,
  retryDeferredReflowablePageTurn,
  ensurePageDragPreviewLayer,
  removePageDragPreviewLayer,
  pageDragPreviewDimensions,
  readerPageDragCurlMetrics,
  applyPageDragCurlMetrics,
  applyPageDragCurlSheet,
  pageDragCurlSnapshotScroll,
  pageDragCurlSnapshotHtml,
  pageDragCurlSnapshotKey,
  syncPageDragCurlSnapshotFrame,
  syncPageDragCurlSnapshots,
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
