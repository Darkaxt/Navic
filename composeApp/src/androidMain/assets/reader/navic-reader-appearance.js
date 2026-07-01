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
  readerAdjacentPaperTextureSlots,
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
  normalizeReaderLineFragmentParagraphs,
  applyReaderParagraphSpacing,
  normalizeReaderInlineTypography,
  readerNormalizeChapterOpeningMargins,
  ensureReaderMovingPageBorderOverlayLayer,
  ensureReaderMovingPageTextureLayer,
  ensureReaderSurfaceTextureLayer,
  ensureReaderSurfaceBorderOverlayLayer,
  ensureReaderPageNumberLayer,
  ensureReaderShellCoverLayer,
  ensureReaderShellCoverImage,
  ensureTapZoneOverlayLayer,
  updateReaderShellCoverLayer,
  updateReaderStaticPaperBackingLayer,
  updateReaderMovingPageBorderOverlayLayer,
  updateReaderMovingPageTextureLayer,
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
  const position = Number(renderer.containerPosition)
  const continuousTextureFlow =
    this.readerFlowModeValue === ReaderFlowScrolled || this.readerFlowModeValue === ReaderFlowScrolledGaps
  if (continuousTextureFlow || !Number.isFinite(position)) {
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
  return {
    reason,
    offset: this.surfaceTextureScrollOffset || { x: 0, y: 0 },
    position: this.currentRendererContainerPosition(),
    baseOffset: this.surfacePaperTextureBaseOffset,
    delta: position - this.surfacePaperTextureBaseOffset,
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

function renderSurfacePaperTextureLayers() {
  const textureSlots = this.surfaceTextureSlots || []
  const borderOverlaySlots = this.surfaceBorderOverlaySlots || []
  if (
    !this.surfaceTextureVariant &&
    !this.surfaceBorderOverlayVariant &&
    textureSlots.length === 0 &&
    borderOverlaySlots.length === 0
  ) return
  const scrollOffset = this.surfacePaperTextureScrollOffset()
  const readerDirection = this.effectiveReaderDirection?.() || this.readerDirectionModeValue
  if (this.surfaceTextureVariant) {
    this.surfaceTextureLayer = this.surfaceTextureLayer && readerRoot.contains(this.surfaceTextureLayer)
      ? this.surfaceTextureLayer
      : ensureReaderSurfaceTextureLayer()
    updateReaderStaticPaperBackingLayer(
      this.surfaceTextureLayer,
      textureSlots,
      this.readerSettings
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
      readerDirection
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
      readerDirection
    )
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

function updateSurfacePaperTexture(detail = {}, pagePosition = null) {
  const index = this.surfacePaperTextureIndex(detail)
  const section = this.view?.book?.sections?.[index]
  const textureDetail = pagePosition
    ? { ...detail, pageIndex: pagePosition.pageIndex, pageCount: pagePosition.pageCount }
    : detail
  const textureKey = readerPaperTextureVariantKey(this.publicationUrl, section, index, textureDetail)
  const textureSlots = readerAdjacentPaperTextureSlots({
    publicationUrl: this.publicationUrl,
    sections: this.view?.book?.sections || [],
    index,
    detail,
    pagePosition,
    resolveVariant: readerPaperTextureVariantForPage,
  })
  const borderOverlaySlots = readerAdjacentPaperTextureSlots({
    publicationUrl: this.publicationUrl,
    sections: this.view?.book?.sections || [],
    index,
    detail,
    pagePosition,
    resolveVariant: readerPageBorderOverlayVariantForPage,
  })
  const textureVariant = textureSlots.find(slot => slot.slot === 'current')?.variant || readerPaperTextureVariantForPage(textureKey)
  const borderOverlayVariant = borderOverlaySlots.find(slot => slot.slot === 'current')?.variant || readerPageBorderOverlayVariantForPage(textureKey)
  this.surfaceTextureSlots = textureSlots
  this.surfaceBorderOverlaySlots = borderOverlaySlots
  this.surfaceTextureVariant = textureVariant
  this.surfaceBorderOverlayVariant = borderOverlayVariant
  this.surfaceTextureLayer = this.surfaceTextureLayer && readerRoot.contains(this.surfaceTextureLayer)
    ? this.surfaceTextureLayer
    : ensureReaderSurfaceTextureLayer()
  this.movingPageTextureLayer = this.movingPageTextureLayer && readerRoot.contains(this.movingPageTextureLayer)
    ? this.movingPageTextureLayer
    : ensureReaderMovingPageTextureLayer()
  this.surfaceBorderOverlayLayer?.remove?.()
  this.surfaceBorderOverlayLayer = null
  this.movingPageBorderOverlayLayer = this.movingPageBorderOverlayLayer && readerRoot.contains(this.movingPageBorderOverlayLayer)
    ? this.movingPageBorderOverlayLayer
    : ensureReaderMovingPageBorderOverlayLayer()
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
  applyRendererTheme,
  applyDocumentTheme,
  currentRendererContainerPosition,
  surfacePaperTextureScrollOffset,
  attachSurfacePaperTextureDragDirection,
  surfacePaperTextureDiagnosticState,
  attachSurfacePaperTextureScrollSync,
  detachSurfacePaperTextureScrollSync,
  syncSurfacePaperTextureScrollOffset,
  renderSurfacePaperTextureLayers,
  surfacePaperTextureIndex,
  updateSurfacePaperTexture,
  applyReaderDirection,
  applyDocumentDirection
}
