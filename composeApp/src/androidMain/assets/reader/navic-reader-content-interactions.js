// Adapted from Anx Reader: tmp/references/anx-reader/lib/page/book_player/epub_player.dart:627-879
// (callback catalog, including translateText at 864)
// tmp/references/anx-reader/assets/foliate-js/src/view.js:216-327 (link/image taxonomy)
// :335-397 (annotations)
// tmp/references/anx-reader/assets/foliate-js/src/footnotes.js:45-109 (cross-section footnote resolution)

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

function attachSurfaceTapGesture(element) {
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

function readerTapZoneActionForPoint(clientX, clientY) {
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

function readerTapZoneCommand(action) {
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

function readerTapZoneGestureHost(target) {
  return target?.defaultView || target || null
}

function markReaderTapZoneTouchHandled(target, event) {
  const host = this.readerTapZoneGestureHost(target)
  if (!host) return
  host.__navicSuppressNextTapZoneClickUntil = (event?.timeStamp || performance.now()) + CenterTapSyntheticClickDedupeMs
}

function shouldSuppressReaderTapZoneClick(target, event) {
  const host = this.readerTapZoneGestureHost(target)
  const until = Number(host?.__navicSuppressNextTapZoneClickUntil || 0)
  return until > 0 && (event?.timeStamp || performance.now()) <= until
}

function shouldIgnoreReaderTapZoneTarget(event, sourceTarget) {
  const target = event?.target
  const doc = target?.ownerDocument || sourceTarget?.ownerDocument || sourceTarget
  const selection = doc?.getSelection?.()
  if (selection && selection.rangeCount > 0 && !selection.isCollapsed) return true
  const anchor = closestElement(target, 'a[href]')
  if (anchor && readerPointInsideAnchorText(anchor, event)) return true
  if (readerMediaTapTargetForEvent(doc, event, anchor)) return true
  return false
}

function attachNativeTapZoneTouchSuppressor(target) {
  const host = this.readerTapZoneGestureHost(target)
  if (!target || !host || host.__navicNativeTapZoneTouchSuppressorAttached) return
  host.__navicNativeTapZoneTouchSuppressorAttached = true
  const suppressFoliateTouch = event => {
    if (this.nativeTapZones !== true) return
    if (event.type === 'touchmove' && event.cancelable) {
      event.preventDefault?.()
    }
    event.stopPropagation?.()
    event.stopImmediatePropagation?.()
  }
  target.addEventListener('touchstart', suppressFoliateTouch, { capture: true, passive: true })
  target.addEventListener('touchmove', suppressFoliateTouch, { capture: true, passive: false })
  target.addEventListener('touchend', suppressFoliateTouch, { capture: true, passive: true })
  target.addEventListener('touchcancel', suppressFoliateTouch, { capture: true, passive: true })
}

function rememberReaderContentActionTouch(doc, event, detail = {}) {
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

function suppressReaderNativeTapZoneContentActivation(doc, event, source = 'content-click') {
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

async function handleNativeTapZoneContentLongPress(doc, event, index = null, source = 'content-long-press') {
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

async function handleNativeTapZoneContentLongPressAt(rootX, rootY, viewWidth = null, viewHeight = null, source = 'content-long-press-command') {
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
    if (hit.kind === 'text') {
      return this.selectReaderTextAtDocumentPoint(entry.doc, hit.x, hit.y, hit.index, source)
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

function readerContentActionClaimPayload(doc, event, detail = {}) {
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

function recentReaderContentActionAtRootPoint(rootPoint) {
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

function claimReaderInteractiveContentTouch(doc, event) {
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

function readerContentActionInDocumentAtPoint(doc, rootX, rootY, index = null) {
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
      target,
      x,
      y,
      index,
    }
  }
  if (target?.textContent?.trim?.()) {
    return {
      handled: true,
      kind: 'text',
      target,
      x,
      y,
      index,
    }
  }
  return null
}

function readerCaretRangeFromPoint(doc, x, y) {
  const localX = Number(x)
  const localY = Number(y)
  if (!doc || !Number.isFinite(localX) || !Number.isFinite(localY)) return null
  const rangeFromPoint = doc.caretRangeFromPoint?.(localX, localY)
  if (rangeFromPoint) return rangeFromPoint
  const position = doc.caretPositionFromPoint?.(localX, localY)
  if (position?.offsetNode) {
    const range = doc.createRange()
    range.setStart(position.offsetNode, position.offset)
    range.collapse(true)
    return range
  }
  return null
}

function readerTextNodeForRange(range) {
  const doc = range?.startContainer?.ownerDocument
  const textNodeType = doc?.defaultView?.Node?.TEXT_NODE || 3
  if (range?.startContainer?.nodeType === textNodeType) return range.startContainer
  const candidate = range?.startContainer?.childNodes?.[range.startOffset] ||
    range?.startContainer?.childNodes?.[Math.max(0, range.startOffset - 1)]
  if (candidate?.nodeType === textNodeType) return candidate
  const walker = doc?.createTreeWalker?.(candidate || range?.startContainer, textNodeType)
  return walker?.nextNode?.() || null
}

function readerWordRangeAroundCaret(doc, caretRange) {
  const textNode = readerTextNodeForRange(caretRange)
  const text = textNode?.textContent || ''
  if (!doc || !textNode || !text.trim()) return null
  let offset = Number(caretRange?.startContainer === textNode ? caretRange.startOffset : 0)
  offset = Math.max(0, Math.min(text.length, Number.isFinite(offset) ? offset : 0))
  if (offset >= text.length && offset > 0) offset -= 1
  while (offset > 0 && /\s/.test(text[offset]) && !/\s/.test(text[offset - 1])) offset -= 1
  while (offset < text.length && /\s/.test(text[offset])) offset += 1
  if (offset >= text.length) return null
  let start = offset
  let end = offset
  while (start > 0 && !/\s/.test(text[start - 1])) start -= 1
  while (end < text.length && !/\s/.test(text[end])) end += 1
  if (start === end) return null
  const range = doc.createRange()
  range.setStart(textNode, start)
  range.setEnd(textNode, end)
  return range
}

function selectReaderTextAtDocumentPoint(doc, x, y, index = null, source = 'content-long-press-command') {
  const selection = doc?.getSelection?.()
  if (!selection) return false
  const caretRange = this.readerCaretRangeFromPoint(doc, x, y)
  const range = this.readerWordRangeAroundCaret(doc, caretRange)
  if (!range) {
    readerTrace('native-tap-zones:text-long-press-selection-miss', {
      source,
      index,
      x: Math.round(Number(x) || 0),
      y: Math.round(Number(y) || 0),
    })
    return false
  }
  selection.removeAllRanges()
  selection.addRange(range)
  doc.dispatchEvent(new Event('selectionchange', { bubbles: true }))
  readerTrace('native-tap-zones:text-long-press-selection', {
    source,
    index,
    textLength: selection.toString?.().trim?.().length || 0,
    x: Math.round(Number(x) || 0),
    y: Math.round(Number(y) || 0),
  })
  return true
}

function normalizeReaderContentRootPoint(rootX, rootY, viewWidth = null, viewHeight = null) {
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

function readerContentActionAtRootPoint(rootX, rootY, viewWidth = null, viewHeight = null) {
  const rootPoint = this.normalizeReaderContentRootPoint(rootX, rootY, viewWidth, viewHeight)
  const recentHit = this.recentReaderContentActionAtRootPoint(rootPoint)
  if (recentHit?.handled) return recentHit
  for (const entry of this.contentEntries()) {
    const hit = this.readerContentActionInDocumentAtPoint(entry.doc, rootPoint.x, rootPoint.y, entry.index)
    if (!hit?.handled) continue
    if (hit.kind === 'text') continue
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

function handleReaderTapZoneTap(event, sourceTarget) {
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

function attachReaderTapZoneGesture(target) {
  const host = this.readerTapZoneGestureHost(target)
  if (!target || !host || host.__navicReaderTapZoneGestureAttached) return
  host.__navicReaderTapZoneGestureAttached = true
  if (this.nativeTapZones === true) {
    this.attachNativeTapZoneTouchSuppressor(target)
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

function attachLinkNavigation(doc, index) {
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

function readerElementTokenSet(element, attributeName, namespace = null) {
  const rawValue = namespace
    ? element?.getAttributeNS?.(namespace, attributeName)
    : element?.getAttribute?.(attributeName)
  return new Set(String(rawValue || '').split(/\s+/).filter(Boolean))
}

function readerFootnoteReferenceKind(anchor) {
  const roles = readerElementTokenSet(anchor, 'role')
  const epubTypes = readerElementTokenSet(anchor, 'type', 'http://www.idpf.org/2007/ops')
  const localTypes = readerElementTokenSet(anchor, 'type')
  const hasType = type => epubTypes.has(type) || localTypes.has(type)
  if (roles.has('doc-biblioref') || hasType('biblioref')) return 'biblioentry'
  if (roles.has('doc-glossref') || hasType('glossref')) return 'definition'
  if (roles.has('doc-noteref') || hasType('noteref')) return 'footnote'
  return null
}

function readerReferencedNoteType(element) {
  const roles = readerElementTokenSet(element, 'role')
  const epubTypes = readerElementTokenSet(element, 'type', 'http://www.idpf.org/2007/ops')
  const localTypes = readerElementTokenSet(element, 'type')
  const hasType = type => epubTypes.has(type) || localTypes.has(type)
  if (roles.has('doc-biblioentry') || hasType('biblioentry')) return 'biblioentry'
  if (roles.has('definition') || hasType('glossdef')) return 'definition'
  if (roles.has('doc-endnote') || hasType('endnote') || hasType('rearnote')) return 'endnote'
  if (roles.has('doc-footnote') || hasType('footnote')) return 'footnote'
  if (roles.has('note') || hasType('note')) return 'note'
  return null
}

function readerFootnoteElementForHref(doc, rawHref, resolvedHref) {
  const href = String(rawHref || resolvedHref || '')
  const fragment = href.includes('#') ? href.substring(href.indexOf('#') + 1) : ''
  if (!fragment) return null
  const decodedFragment = (() => {
    try {
      return decodeURIComponent(fragment)
    } catch {
      return fragment
    }
  })()
  const target = doc?.getElementById?.(decodedFragment) || doc?.getElementById?.(fragment)
  if (!target) return null
  return readerFootnoteElementFromTarget(doc, target)
}

function readerFootnoteElementFromTarget(doc, target) {
  if (!target) return null
  let element = target
  if (target.startContainer) {
    const ancestor = target.commonAncestorContainer || target.startContainer
    element = ancestor?.nodeType === 1 ? ancestor : ancestor?.parentElement
  }
  if (!element || element.nodeType !== 1) return null
  const originalTarget = element
  while (element?.matches?.('a, span, sup, sub, em, strong, i, b, small, big')) {
    const parent = element.parentElement
    if (!parent) break
    element = parent
  }
  if (element === doc.body) {
    const sibling = originalTarget.nextElementSibling
    if (sibling && !sibling.matches?.('a, span, sup, sub, em, strong, i, b, small, big')) {
      return sibling
    }
    return originalTarget
  }
  return element
}

function readerFootnoteOpenPayload(doc, anchor, rawHref, resolvedHref) {
  const referenceKind = readerFootnoteReferenceKind(anchor)
  const target = readerFootnoteElementForHref(doc, rawHref, resolvedHref)
  const noteType = readerReferencedNoteType(target) || referenceKind
  if (!noteType) return null
  const text = target?.textContent?.replace(/\s+/g, ' ')?.trim?.() || ''
  if (!text) return null
  return {
    type: 'footnoteOpen',
    href: resolvedHref || rawHref || '',
    text,
    noteType,
    hidden: Boolean(target?.matches?.('aside') && noteType === 'footnote'),
  }
}

async function readerFootnoteDocumentForResolvedTarget(book, resolvedTarget, currentDoc, currentIndex) {
  const index = Number(resolvedTarget?.index)
  if (!Number.isFinite(index) || index < 0) return null
  if (index === currentIndex) return currentDoc
  const section = book?.sections?.[index]
  if (!section) return null
  if (typeof section.createDocument === 'function') {
    return section.createDocument()
  }
  if (typeof section.load !== 'function') return null
  let loadedWithSectionLoad = false
  try {
    const src = await section.load()
    loadedWithSectionLoad = true
    if (typeof src !== 'string') return null
    const response = await fetch(src)
    if (!response.ok) return null
    const text = await response.text()
    return new DOMParser().parseFromString(text, 'text/html')
  } finally {
    if (loadedWithSectionLoad) section.unload?.()
  }
}

async function readerResolvedFootnoteOpenPayload(runtime, doc, anchor, rawHref, resolvedHref, currentIndex) {
  const sameDocumentPayload = readerFootnoteOpenPayload(doc, anchor, rawHref, resolvedHref)
  if (sameDocumentPayload) return sameDocumentPayload
  const referenceKind = readerFootnoteReferenceKind(anchor)
  if (!referenceKind) return null
  const book = runtime?.view?.book
  const targetHref = resolvedHref || rawHref || ''
  const resolvedTarget = await Promise.resolve(book?.resolveHref?.(targetHref))
  if (!resolvedTarget || typeof resolvedTarget.anchor !== 'function') return null
  const targetDoc = await readerFootnoteDocumentForResolvedTarget(book, resolvedTarget, doc, currentIndex)
  if (!targetDoc) return null
  const target = resolvedTarget.anchor(targetDoc)
  const element = readerFootnoteElementFromTarget(targetDoc, target)
  const noteType = readerReferencedNoteType(element) || referenceKind
  if (!noteType) return null
  const text = element?.textContent?.replace(/\s+/g, ' ')?.trim?.() || ''
  if (!text) return null
  return {
    type: 'footnoteOpen',
    href: targetHref,
    text,
    noteType,
    hidden: Boolean(element?.matches?.('aside') && noteType === 'footnote'),
  }
}

async function activateReaderLinkFromEvent(doc, event, index, source = 'link') {
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
    const footnoteOpen = await readerResolvedFootnoteOpenPayload(this, doc, anchor, rawHref, href, index)
    if (footnoteOpen) {
      this.rememberReaderContentActionTouch(doc, event, {
        kind: 'footnote',
        href,
        source,
      })
      post(this.readerContentActionClaimPayload(doc, event, {
        kind: 'footnote',
        href,
        source,
        anchor,
      }))
      post(footnoteOpen)
      event.preventDefault()
      event.stopPropagation()
      event.stopImmediatePropagation?.()
      readerTrace('footnote:open', {
        href,
        noteType: footnoteOpen.noteType,
        length: footnoteOpen.text.length,
      })
      return true
    }
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
      post({
        type: 'internalLink',
        href,
        prevented: false,
        source,
      })
      await this.goTo(href)
      return true
    } catch (error) {
      reportError(error, 'link_navigation_failed')
      return true
    }
}

function classifyReaderLinks(doc) {
  if (!doc?.querySelectorAll) return
  for (const anchor of doc.querySelectorAll('a[href]')) {
    anchor.dataset.navicLinkKind = readerLinkHasMedia(anchor) ? 'media' : 'text'
  }
}

function toggleSepiaImageOverlayFromEvent(doc, event, mediaTapTarget = null) {
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

function attachSepiaImageOverlayToggle(doc) {
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

export const NavicReaderContentInteractionMethods = {
  attachSurfaceTapGesture,
  readerTapZoneActionForPoint,
  readerTapZoneCommand,
  readerTapZoneGestureHost,
  markReaderTapZoneTouchHandled,
  shouldSuppressReaderTapZoneClick,
  shouldIgnoreReaderTapZoneTarget,
  attachNativeTapZoneTouchSuppressor,
  rememberReaderContentActionTouch,
  suppressReaderNativeTapZoneContentActivation,
  handleNativeTapZoneContentLongPress,
  handleNativeTapZoneContentLongPressAt,
  readerContentActionClaimPayload,
  recentReaderContentActionAtRootPoint,
  claimReaderInteractiveContentTouch,
  readerContentActionInDocumentAtPoint,
  readerCaretRangeFromPoint,
  readerTextNodeForRange,
  readerWordRangeAroundCaret,
  selectReaderTextAtDocumentPoint,
  normalizeReaderContentRootPoint,
  readerContentActionAtRootPoint,
  handleReaderTapZoneTap,
  attachReaderTapZoneGesture,
  attachLinkNavigation,
  activateReaderLinkFromEvent,
  classifyReaderLinks,
  toggleSepiaImageOverlayFromEvent,
  attachSepiaImageOverlayToggle
}
