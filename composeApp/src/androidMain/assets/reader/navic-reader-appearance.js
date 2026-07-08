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
  ReaderPageStainOverlayAssets,
  ReaderPageStainOverlayVariantCount,
  ReaderPaperTextureAssets,
  ReaderPaperTextureVariantCount,
  ReaderReflowableReadableUnitsPerSyntheticPage,
  ReaderReflowableStartProgressPageOffsetThreshold,
  ReaderReflowableProgressEpsilon,
  ReaderShellCoverLayerSelector,
  ReaderShellCoverTransitionMs,
  ReaderSurfacePageBorderOverlayLayerSelector,
  ReaderSurfacePageStainOverlayLayerSelector,
  ReaderSurfacePaperTextureLayerSelector,
  ReaderTapZoneDefault,
  ReaderTapZoneDisabled,
  ReaderThemeLight,
  ReaderThemeSepia,
  ScrollEdgeTurnSlop,
  ScrollEdgeTurnSwipeThreshold,
  optionalNumber,
  readerDirectionMode,
  readerDragAnimationMode,
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
  readerAdjacentPaperTextureSlots,
  readerPaperTextureVariantForPage,
  readerPageBorderOverlayVariantForPage,
  readerPageStainOverlayVariantForPage,
  readerSpreadGutterOverlayVariantForPage,
  readerSpreadPageTextureSlots,
  readerSurfaceSpreadGutterVisible,
  readerSurfaceSpreadMode,
  readerPageShellGeometryForViewport,
  readerShellGeometryDiagnosticState,
  applyReaderPageShellContentGeometry as applyReaderPageShellContentGeometryToDocument,
  readerPaperTextureTransform,
  readerPaperTextureCssOffset,
  readerPaperTextureBackgroundPosition,
  readerPaperTextureDragDirection,
  readerSurfacePaperTextureScrollOffset,
  readerSurfacePaperTextureOpacity,
  readerSurfacePageBorderOverlayOpacity,
  readerSurfacePageStainOverlayOpacity,
  readerPageNumberPageCount,
  readerPageNumberPositionWithPageCount,
  readerPageNumberLabel,
  readerPageNumberBlendMode,
  readerFontFaceCss,
  readerParagraphSpacingEm,
  normalizeReaderLineFragmentParagraphs,
  applyReaderParagraphSpacing,
  normalizeReaderInlineTypography,
  readerNormalizeChapterOpeningMargins,
  ensureReaderMovingPageBorderOverlayLayer,
  ensureReaderMovingPageStainOverlayLayer,
  ensureReaderMovingPageSpreadGutterOverlayLayer,
  ensureReaderMovingPageTextureLayer,
  ensureReaderSurfaceTextureLayer,
  ensureReaderSurfaceBorderOverlayLayer,
  ensureReaderSurfaceStainOverlayLayer,
  ensureReaderPageNumberLayer,
  ensureReaderShellCoverLayer,
  ensureReaderShellCoverImage,
  ensureTapZoneOverlayLayer,
  updateReaderShellCoverLayer,
  updateReaderStaticPaperBackingLayer,
  updateReaderMovingPageBorderOverlayLayer,
  updateReaderMovingPageStainOverlayLayer,
  updateReaderMovingPageSpreadGutterOverlayLayer,
  updateReaderMovingPageTextureLayer,
  updateReaderSurfaceTextureLayer,
  updateReaderSurfaceBorderOverlayLayer,
  updateReaderSurfaceStainOverlayLayer,
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

const ReaderRootFontFaceStyleId = 'navic-reader-root-font-face'

function renderTapZoneOverlayLayer() {
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

function applyRootReaderFontFaces(settings = this.readerSettings) {
  const css = readerFontFaceCss(settings).trim()
  let style = document.getElementById(ReaderRootFontFaceStyleId)
  if (!css) {
    style?.remove?.()
    return
  }
  if (!style) {
    style = document.createElement('style')
    style.id = ReaderRootFontFaceStyleId
    ;(document.head || document.documentElement).append(style)
  }
  style.textContent = css
}

function applySettings(settings) {
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
  this.applyRootReaderFontFaces(settings)
  rootStyle.setProperty('--reader-page-number-font-family', this.readerPageNumberFontFamily(settings))
  rootStyle.setProperty('--reader-paragraph-spacing', readerParagraphSpacingEm(settings))
  const palette = readerThemePalette(settings.theme)
  rootStyle.setProperty('--reader-background', palette.background)
  rootStyle.setProperty('--reader-foreground', palette.foreground)
  rootStyle.setProperty('--reader-accent', palette.accent)
  rootStyle.setProperty('--theme-bg-color', palette.background)
  const flowMode = readerFlowMode(settings)
  this.readerFlowModeValue = flowMode
  readerRoot.dataset.navicReaderFlowMode = flowMode
  const dragAnimationMode = readerDragAnimationMode(settings)
  if (this.readerDragAnimationModeValue && this.readerDragAnimationModeValue !== dragAnimationMode) {
    this.removePageDragPreviewLayer?.()
  }
  this.readerDragAnimationModeValue = dragAnimationMode
  readerRoot.dataset.navicReaderDragAnimationMode = dragAnimationMode
  rootStyle.setProperty('--reader-scroll-gap', flowMode === ReaderFlowScrolledGaps ? '1.25rem' : '0rem')
  this.view?.renderer?.setAttribute('flow', readerFoliateFlow(flowMode))
  this.readerDirectionModeValue = readerDirectionMode(settings)
  this.applyReaderDirection(this.readerDirectionModeValue)
  this.view?.renderer?.setStyles?.(readerContentCss(settings))
  this.applyThemeToLoadedContent(settings)
  this.applyReaderViewportLayout('settings')
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

function applyThemeToLoadedContent(settings = this.readerSettings) {
  for (const content of this.contentEntries()) {
    this.applyDocumentTheme(content.doc, settings, content.index)
  }
  this.applyRendererTheme(settings)
}

function applyReaderPageShellContentGeometry(doc, settings = this.readerSettings, index = undefined) {
  const geometry = this.readerPageShellGeometry || readerPageShellGeometryForViewport(settings, {
    flowMode: this.readerFlowModeValue,
    spreadMode: this.surfaceSpreadMode,
  })
  return applyReaderPageShellContentGeometryToDocument(doc, settings, geometry, index)
}

function applyRendererTheme(settings = this.readerSettings) {
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

function applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {
  if (!doc?.documentElement) return
  const palette = readerThemePalette(settings?.theme)
  const root = doc.documentElement
  const body = doc.body
  const styleHost = doc.head || root
  this.applyReaderPageShellContentGeometry(doc, settings, index)
  normalizeReaderLineFragmentParagraphs(doc, settings)
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
  normalizeReaderInlineTypography(doc, settings)
  readerNormalizeChapterOpeningMargins(doc, settings)
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

function currentRendererContainerPosition() {
  const renderer = this.view?.renderer
  const position = Number(renderer?.containerPosition)
  return Number.isFinite(position) ? position : 0
}

function surfacePaperTextureScrollOffset() {
  const renderer = this.view?.renderer
  if (!renderer) {
    this.surfaceTextureScrollOffset = { x: 0, y: 0 }
    return this.surfaceTextureScrollOffset
  }
  if (this.surfaceLiveDragActive) {
    // Live lateral drag bypass: move the paper texture + border-overlay shadow by
    // the exact accumulated gesture displacement that drives renderer.scrollBy()
    // (see previewPageDrag in navic-reader-page-turns.js). This keeps texture and
    // text frame-locked on one signed source, eliminating the heuristic's
    // instantaneous-direction sign flips, rendererPageSize rescaling, and the
    // 0.75-viewport zero-clamp that caused texture/shadow to drift opposite or
    // freeze relative to the text. Sign below matches the heuristic's clean-case
    // (texture.x follows the finger / visual content). If a trace shows the
    // texture moving opposite the text, flip READER_LIVE_DRAG_TEXTURE_SIGN.
    const READER_LIVE_DRAG_TEXTURE_SIGN = 1
    const liveViewport = readerViewportSize()
    const liveMax = Math.max(
      1,
      this.readerFlowModeValue === ReaderFlowPagedVertical
        ? (Number.isFinite(liveViewport.height) ? liveViewport.height : 1)
        : (Number.isFinite(liveViewport.width) ? liveViewport.width : 1)
    )
    const dragX = READER_LIVE_DRAG_TEXTURE_SIGN * Math.max(-liveMax, Math.min(liveMax, Number(this.surfaceLiveDragOffset?.x) || 0))
    const dragY = READER_LIVE_DRAG_TEXTURE_SIGN * Math.max(-liveMax, Math.min(liveMax, Number(this.surfaceLiveDragOffset?.y) || 0))
    this.surfaceTextureScrollOffset = this.readerFlowModeValue === ReaderFlowPagedVertical
      ? { x: 0, y: dragY }
      : { x: dragX, y: 0 }
    return this.surfaceTextureScrollOffset
  }
  const position = Number(renderer.containerPosition)
  const continuousTextureFlow =
    this.readerFlowModeValue === ReaderFlowScrolled || this.readerFlowModeValue === ReaderFlowScrolledGaps
  if (continuousTextureFlow || !Number.isFinite(position)) {
    this.surfaceTextureScrollOffset = { x: 0, y: 0 }
    return this.surfaceTextureScrollOffset
  }
  const { width, height } = readerViewportSize()
  const rendererPageSize = Number(renderer.size)
  this.surfaceTextureScrollOffset = readerSurfacePaperTextureScrollOffset({
    position,
    baseOffset: this.surfacePaperTextureBaseOffset,
    viewportWidth: width,
    viewportHeight: height,
    rendererPageSize: Number.isFinite(rendererPageSize) ? rendererPageSize : null,
    flowMode: this.readerFlowModeValue,
    pageTurnDirection: this.surfacePaperTextureTurnDirection || this.pageTurnDirection,
    fallbackPageTurnDirection: this.surfacePaperTextureFallbackDirection || this.recentPageTurnDirection,
  })
  return this.surfaceTextureScrollOffset
}

function attachSurfacePaperTextureDragDirection(doc) {
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
      readerDirection: this.effectiveReaderDirection?.() || this.readerDirectionModeValue,
    })
    if (!direction) return
    this.surfacePaperTextureTurnDirection = direction
    this.surfacePaperTextureFallbackDirection = direction
    readerTrace('texture:drag-direction', { direction })
  }, { capture: true, passive: true })
  doc.addEventListener('touchend', () => {
    touchState = null
  }, { capture: true, passive: true })
  doc.addEventListener('touchcancel', () => {
    touchState = null
  }, { capture: true, passive: true })
}

function surfacePaperTextureDiagnosticState(reason = 'scroll') {
  const position = this.currentRendererContainerPosition()
  const pageIndex = Number(this.currentPagePosition?.pageIndex)
  const pageCount = Number(this.currentPagePosition?.pageCount)
  const detail = this.lastRelocateDetail || {}
  const { width, height } = readerViewportSize()
  const renderer = this.view?.renderer
  const rendererPageSize = Number(renderer?.size)
  return {
    reason,
    offset: this.surfaceTextureScrollOffset || { x: 0, y: 0 },
    position: this.currentRendererContainerPosition(),
    baseOffset: this.surfacePaperTextureBaseOffset,
    delta: position - this.surfacePaperTextureBaseOffset,
    rendererPageSize: Number.isFinite(rendererPageSize) ? rendererPageSize : null,
    pageTurnDirection: this.surfacePaperTextureTurnDirection || this.pageTurnDirection || '',
    viewportWidth: width,
    viewportHeight: height,
    flowMode: this.readerFlowModeValue,
    pageIndex: Number.isFinite(pageIndex) ? pageIndex : null,
    pageCount: Number.isFinite(pageCount) ? pageCount : null,
    href: detail.href || this.sectionHrefForDetail(detail) || '',
    textureKey: readerRoot.dataset.navicSurfacePaperTextureKey || '',
  }
}

function attachSurfacePaperTextureScrollSync() {
  const renderer = this.view?.renderer
  if (!renderer || renderer === this.surfacePaperTextureScrollRenderer) return
  this.detachSurfacePaperTextureScrollSync()
  this.surfacePaperTextureScrollRenderer = renderer
  this.surfacePaperTextureScrollListener = () => this.syncSurfacePaperTextureScrollOffset('scroll')
  renderer.addEventListener('scroll', this.surfacePaperTextureScrollListener, { passive: true })
}

function detachSurfacePaperTextureScrollSync() {
  if (this.surfacePaperTextureScrollRenderer && this.surfacePaperTextureScrollListener) {
    this.surfacePaperTextureScrollRenderer.removeEventListener('scroll', this.surfacePaperTextureScrollListener)
  }
  this.surfacePaperTextureScrollRenderer = null
  this.surfacePaperTextureScrollListener = null
}

function syncSurfacePaperTextureScrollOffset(reason = 'scroll') {
  if (!this.surfaceTextureVariant && !this.surfaceBorderOverlayVariant && !this.surfaceStainOverlayVariant && !this.surfaceSpreadGutterOverlayVariant) return
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

function startSurfacePaperTextureMotionSync(reason = 'page-turn-animation') {
  if (!this.surfaceTextureVariant && !this.surfaceBorderOverlayVariant && !this.surfaceStainOverlayVariant && !this.surfaceSpreadGutterOverlayVariant) return
  this.surfacePaperTextureMotionSyncActive = true
  if (this.surfacePaperTextureMotionFrame != null) {
    cancelAnimationFrame(this.surfacePaperTextureMotionFrame)
    this.surfacePaperTextureMotionFrame = null
  }
  const tick = () => {
    if (!this.surfacePaperTextureMotionSyncActive) {
      this.surfacePaperTextureMotionFrame = null
      return
    }
    this.syncSurfacePaperTextureScrollOffset(reason)
    this.surfacePaperTextureMotionFrame = requestAnimationFrame(tick)
  }
  this.surfacePaperTextureMotionFrame = requestAnimationFrame(tick)
}

function stopSurfacePaperTextureMotionSync(reason = 'page-turn-settled') {
  this.surfacePaperTextureMotionSyncActive = false
  if (this.surfacePaperTextureMotionFrame != null) {
    cancelAnimationFrame(this.surfacePaperTextureMotionFrame)
    this.surfacePaperTextureMotionFrame = null
  }
  this.syncSurfacePaperTextureScrollOffset(reason)
}

function renderSurfacePaperTextureLayers() {
  const textureSlots = this.surfaceTextureSlots || []
  const borderOverlaySlots = this.surfaceBorderOverlaySlots || []
  const stainOverlaySlots = this.surfaceStainOverlaySlots || []
  const spreadGutterOverlaySlots = this.surfaceSpreadGutterOverlaySlots || []
  if (
    !this.surfaceTextureVariant &&
    !this.surfaceBorderOverlayVariant &&
    !this.surfaceStainOverlayVariant &&
    !this.surfaceSpreadGutterOverlayVariant &&
    textureSlots.length === 0 &&
    borderOverlaySlots.length === 0 &&
    stainOverlaySlots.length === 0 &&
    spreadGutterOverlaySlots.length === 0
  ) return
  const scrollOffset = this.surfacePaperTextureScrollOffset()
  const readerDirection = this.effectiveReaderDirection?.() || this.readerDirectionModeValue
  const shellGeometry = this.readerPageShellGeometry || readerPageShellGeometryForViewport(this.readerSettings, {
    flowMode: this.readerFlowModeValue,
    spreadMode: this.surfaceSpreadMode,
  })
  if (this.surfaceTextureVariant) {
    this.surfaceTextureLayer = this.surfaceTextureLayer && readerRoot.contains(this.surfaceTextureLayer)
      ? this.surfaceTextureLayer
      : ensureReaderSurfaceTextureLayer()
    updateReaderStaticPaperBackingLayer(
      this.surfaceTextureLayer,
      textureSlots,
      this.readerSettings,
      shellGeometry
    )
    this.movingPageTextureLayer = this.movingPageTextureLayer && readerRoot.contains(this.movingPageTextureLayer)
      ? this.movingPageTextureLayer
      : ensureReaderMovingPageTextureLayer()
    updateReaderMovingPageTextureLayer(
      this.movingPageTextureLayer,
      textureSlots,
      this.readerSettings,
      scrollOffset,
      this.readerFlowModeValue,
      readerDirection,
      shellGeometry
    )
  }
  if (this.surfaceBorderOverlayVariant) {
    this.surfaceBorderOverlayLayer?.remove?.()
    this.surfaceBorderOverlayLayer = null
    this.movingPageBorderOverlayLayer = this.movingPageBorderOverlayLayer && readerRoot.contains(this.movingPageBorderOverlayLayer)
      ? this.movingPageBorderOverlayLayer
      : ensureReaderMovingPageBorderOverlayLayer()
    updateReaderMovingPageBorderOverlayLayer(
      this.movingPageBorderOverlayLayer,
      borderOverlaySlots,
      this.readerSettings,
      scrollOffset,
      this.readerFlowModeValue,
      readerDirection,
      shellGeometry
    )
  }
  if (this.surfaceStainOverlayVariant) {
    this.surfaceStainOverlayLayer?.remove?.()
    this.surfaceStainOverlayLayer = null
    this.movingPageStainOverlayLayer = this.movingPageStainOverlayLayer && readerRoot.contains(this.movingPageStainOverlayLayer)
      ? this.movingPageStainOverlayLayer
      : ensureReaderMovingPageStainOverlayLayer()
    updateReaderMovingPageStainOverlayLayer(
      this.movingPageStainOverlayLayer,
      stainOverlaySlots,
      this.readerSettings,
      scrollOffset,
      this.readerFlowModeValue,
      readerDirection,
      shellGeometry
    )
  }
  if (this.surfaceSpreadGutterOverlayVariant) {
    this.movingPageSpreadGutterOverlayLayer = this.movingPageSpreadGutterOverlayLayer && readerRoot.contains(this.movingPageSpreadGutterOverlayLayer)
      ? this.movingPageSpreadGutterOverlayLayer
      : ensureReaderMovingPageSpreadGutterOverlayLayer()
    updateReaderMovingPageSpreadGutterOverlayLayer(
      this.movingPageSpreadGutterOverlayLayer,
      spreadGutterOverlaySlots,
      this.readerSettings,
      scrollOffset,
      this.readerFlowModeValue,
      readerDirection,
      shellGeometry
    )
  } else {
    this.movingPageSpreadGutterOverlayLayer?.remove?.()
    this.movingPageSpreadGutterOverlayLayer = null
  }
}

function surfacePaperTextureIndex(detail = {}) {
  const detailIndex = Number(detail?.index)
  if (Number.isFinite(detailIndex)) return Math.floor(detailIndex)
  const fixedLayoutIndex = this.fixedLayoutCurrentPageIndex()
  if (Number.isFinite(fixedLayoutIndex)) return fixedLayoutIndex
  const entry = this.contentEntries(detail).find(content => Number.isFinite(Number(content.index)))
  const entryIndex = Number(entry?.index)
  return Number.isFinite(entryIndex) ? Math.floor(entryIndex) : 0
}

function applySurfacePaperTextureUpdate(detail = {}, pagePosition = null) {
  const index = this.surfacePaperTextureIndex(detail)
  const section = this.view?.book?.sections?.[index]
  const textureDetail = pagePosition
    ? { ...detail, pageIndex: pagePosition.pageIndex, pageCount: pagePosition.pageCount }
    : detail
  const textureKey = readerPaperTextureVariantKey(this.publicationUrl, section, index, textureDetail)
  const { width, height } = readerViewportSize()
  const spreadMode = readerSurfaceSpreadMode({
    flowMode: this.readerFlowModeValue,
    width,
    height,
  })
  const shellGeometry = readerPageShellGeometryForViewport(this.readerSettings, {
    flowMode: this.readerFlowModeValue,
    spreadMode,
  })
  let textureSlots = readerAdjacentPaperTextureSlots({
    publicationUrl: this.publicationUrl,
    sections: this.view?.book?.sections || [],
    index,
    detail,
    pagePosition,
    resolveVariant: readerPaperTextureVariantForPage,
  })
  let borderOverlaySlots = readerAdjacentPaperTextureSlots({
    publicationUrl: this.publicationUrl,
    sections: this.view?.book?.sections || [],
    index,
    detail,
    pagePosition,
    resolveVariant: readerPageBorderOverlayVariantForPage,
  })
  let stainOverlaySlots = readerAdjacentPaperTextureSlots({
    publicationUrl: this.publicationUrl,
    sections: this.view?.book?.sections || [],
    index,
    detail,
    pagePosition,
    resolveVariant: readerPageStainOverlayVariantForPage,
  })
  textureSlots = readerSpreadPageTextureSlots(textureSlots, readerPaperTextureVariantForPage, spreadMode)
  borderOverlaySlots = readerSpreadPageTextureSlots(borderOverlaySlots, readerPageBorderOverlayVariantForPage, spreadMode)
  stainOverlaySlots = readerSpreadPageTextureSlots(stainOverlaySlots, readerPageStainOverlayVariantForPage, spreadMode)
  const spreadGutterVisible = readerSurfaceSpreadGutterVisible({
    settings: this.readerSettings,
    spreadMode,
    flowMode: this.readerFlowModeValue,
    width,
    height,
  })
  const spreadGutterOverlaySlots = spreadGutterVisible
    ? readerAdjacentPaperTextureSlots({
      publicationUrl: this.publicationUrl,
      sections: this.view?.book?.sections || [],
      index,
      detail,
      pagePosition,
      resolveVariant: readerSpreadGutterOverlayVariantForPage,
    })
    : []
  const textureVariant = textureSlots.find(slot => slot.slot === 'current')?.variant || readerPaperTextureVariantForPage(textureKey)
  const borderOverlayVariant = borderOverlaySlots.find(slot => slot.slot === 'current')?.variant || readerPageBorderOverlayVariantForPage(textureKey)
  const stainOverlayVariant = stainOverlaySlots.find(slot => slot.slot === 'current')?.variant || readerPageStainOverlayVariantForPage(textureKey)
  const spreadGutterOverlayVariant = spreadGutterOverlaySlots.find(slot => slot.slot === 'current')?.variant || null
  this.surfaceSpreadMode = spreadMode
  this.readerPageShellGeometry = shellGeometry
  this.surfaceTextureSlots = textureSlots
  this.surfaceBorderOverlaySlots = borderOverlaySlots
  this.surfaceStainOverlaySlots = stainOverlaySlots
  this.surfaceSpreadGutterOverlaySlots = spreadGutterOverlaySlots
  this.surfaceTextureVariant = textureVariant
  this.surfaceBorderOverlayVariant = borderOverlayVariant
  this.surfaceStainOverlayVariant = stainOverlayVariant
  this.surfaceSpreadGutterOverlayVariant = spreadGutterOverlayVariant
  this.surfaceTextureLayer = this.surfaceTextureLayer && readerRoot.contains(this.surfaceTextureLayer)
    ? this.surfaceTextureLayer
    : ensureReaderSurfaceTextureLayer()
  this.movingPageTextureLayer = this.movingPageTextureLayer && readerRoot.contains(this.movingPageTextureLayer)
    ? this.movingPageTextureLayer
    : ensureReaderMovingPageTextureLayer()
  this.surfaceBorderOverlayLayer?.remove?.()
  this.surfaceBorderOverlayLayer = null
  this.surfaceStainOverlayLayer?.remove?.()
  this.surfaceStainOverlayLayer = null
  this.movingPageBorderOverlayLayer = this.movingPageBorderOverlayLayer && readerRoot.contains(this.movingPageBorderOverlayLayer)
    ? this.movingPageBorderOverlayLayer
    : ensureReaderMovingPageBorderOverlayLayer()
  this.movingPageStainOverlayLayer = this.movingPageStainOverlayLayer && readerRoot.contains(this.movingPageStainOverlayLayer)
    ? this.movingPageStainOverlayLayer
    : ensureReaderMovingPageStainOverlayLayer()
  if (spreadGutterOverlayVariant) {
    this.movingPageSpreadGutterOverlayLayer = this.movingPageSpreadGutterOverlayLayer && readerRoot.contains(this.movingPageSpreadGutterOverlayLayer)
      ? this.movingPageSpreadGutterOverlayLayer
      : ensureReaderMovingPageSpreadGutterOverlayLayer()
  } else {
    this.movingPageSpreadGutterOverlayLayer?.remove?.()
    this.movingPageSpreadGutterOverlayLayer = null
  }
  this.surfacePaperTextureBaseOffset = this.currentRendererContainerPosition()
  this.surfaceTextureScrollOffset = { x: 0, y: 0 }
  readerRoot.dataset.navicSurfacePaperTextureKey = textureKey
  readerRoot.dataset.navicSurfacePaperTextureAsset = textureVariant.asset
  readerRoot.dataset.navicSurfacePageBorderOverlayAsset = borderOverlayVariant.asset
  readerRoot.dataset.navicSurfaceBorderOverlayAsset = borderOverlayVariant.asset
  readerRoot.dataset.navicSurfacePageStainOverlayAsset = stainOverlayVariant.asset
  readerRoot.dataset.navicSurfaceSpreadMode = spreadMode
  readerRoot.dataset.navicReaderShellGeometryMode = shellGeometry.mode
  readerRoot.dataset.navicReaderShellGutterWidth = String(shellGeometry.edgeInsets?.gutter || 0)
  readerRoot.dataset.navicReaderShellGeometry = JSON.stringify(readerShellGeometryDiagnosticState(shellGeometry, 'reader-shell-geometry'))
  readerRoot.dataset.navicSurfaceSpreadGutterOverlayAsset = spreadGutterOverlayVariant?.asset || ''
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
    shellGeometry: readerShellGeometryDiagnosticState(shellGeometry, 'texture:update'),
    index,
    key: textureKey,
    baseAsset: textureVariant.asset,
    borderAsset: borderOverlayVariant.asset,
    gutterAsset: spreadGutterOverlayVariant?.asset || '',
    spreadMode,
  })
  readerTrace('reader-shell-geometry', readerShellGeometryDiagnosticState(shellGeometry, 'texture:update'))
  this.applyThemeToLoadedContent(this.readerSettings)
  this.renderSurfacePaperTextureLayers()
  this.stopSurfacePaperTextureMotionSync('texture-update')
  this.surfacePaperTextureTurnDirection = null
}

function shouldDeferSurfacePaperTextureUpdate(reason = '') {
  if (!String(reason || '').startsWith('page-turn:')) return false
  if (!this.surfacePaperTextureMotionSyncActive) return false
  const position = this.currentRendererContainerPosition()
  const baseOffset = Number(this.surfacePaperTextureBaseOffset)
  return Number.isFinite(position) &&
    Number.isFinite(baseOffset) &&
    Math.abs(position - baseOffset) > 1
}

function scheduleDeferredSurfacePaperTextureUpdate(reason = 'page-turn') {
  if (this.surfacePaperTextureDeferredFrame != null) return
  const tick = (previousPosition = null, stableFrames = 0) => {
    this.surfacePaperTextureDeferredFrame = requestAnimationFrame(() => {
      this.surfacePaperTextureDeferredFrame = null
      const pending = this.pendingSurfacePaperTextureUpdate
      if (!pending) return
      const position = this.currentRendererContainerPosition()
      const stable = Number.isFinite(previousPosition) && Math.abs(position - previousPosition) <= 1
      const nextStableFrames = stable ? stableFrames + 1 : 0
      if (nextStableFrames < 2) {
        tick(position, nextStableFrames)
        return
      }
      this.pendingSurfacePaperTextureUpdate = null
      readerTrace('texture:update-deferred-applied', {
        reason,
        position,
        baseOffset: this.surfacePaperTextureBaseOffset,
      })
      this.applySurfacePaperTextureUpdate(pending.detail, pending.pagePosition)
    })
  }
  tick()
}

function updateSurfacePaperTexture(detail = {}, pagePosition = null, reason = '') {
  if (this.shouldDeferSurfacePaperTextureUpdate(reason)) {
    this.pendingSurfacePaperTextureUpdate = { detail, pagePosition }
    readerTrace('texture:update-deferred', {
      reason,
      position: this.currentRendererContainerPosition(),
      baseOffset: this.surfacePaperTextureBaseOffset,
      pageIndex: pagePosition?.pageIndex ?? null,
      pageCount: pagePosition?.pageCount ?? null,
    })
    this.scheduleDeferredSurfacePaperTextureUpdate(reason)
    return
  }
  if (this.surfacePaperTextureDeferredFrame != null) {
    cancelAnimationFrame(this.surfacePaperTextureDeferredFrame)
    this.surfacePaperTextureDeferredFrame = null
  }
  this.pendingSurfacePaperTextureUpdate = null
  this.applySurfacePaperTextureUpdate(detail, pagePosition)
}

function applyReaderDirection(direction, rerender = true) {
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

function applyDocumentDirection(doc, direction) {
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

export const NavicReaderAppearanceMethods = {
  renderTapZoneOverlayLayer,
  applyRootReaderFontFaces,
  applySettings,
  applyThemeToLoadedContent,
  applyReaderPageShellContentGeometry,
  applyRendererTheme,
  applyDocumentTheme,
  currentRendererContainerPosition,
  surfacePaperTextureScrollOffset,
  attachSurfacePaperTextureDragDirection,
  surfacePaperTextureDiagnosticState,
  attachSurfacePaperTextureScrollSync,
  detachSurfacePaperTextureScrollSync,
  syncSurfacePaperTextureScrollOffset,
  startSurfacePaperTextureMotionSync,
  stopSurfacePaperTextureMotionSync,
  renderSurfacePaperTextureLayers,
  surfacePaperTextureIndex,
  applySurfacePaperTextureUpdate,
  shouldDeferSurfacePaperTextureUpdate,
  scheduleDeferredSurfacePaperTextureUpdate,
  updateSurfacePaperTexture,
  applyReaderDirection,
  applyDocumentDirection
}
