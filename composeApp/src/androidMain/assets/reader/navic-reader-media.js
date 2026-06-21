import {
  CenterTapMovementSlop,
  CenterTapSyntheticClickDedupeMs,
  KomikkuNavigationRegionLeft,
  KomikkuNavigationRegionMenu,
  KomikkuNavigationRegionNext,
  KomikkuNavigationRegionPrevious,
  KomikkuNavigationRegionRight,
  ReaderFlowPaged,
  ReaderFlowPagedVertical,
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  ReaderMediaSyntheticClickSuppressMs,
  ReaderTapZoneDefault,
  ReaderTapZoneDisabled,
  ReaderTapZoneEdge,
  ReaderTapZoneKindle,
  ReaderTapZoneLShaped,
  ReaderTapZoneRightLeft,
} from './navic-reader-settings-core.js'

export const closestElement = (target, selector) =>
  target?.closest?.(selector) ||
  target?.parentElement?.closest?.(selector) ||
  target?.parentNode?.closest?.(selector) ||
  null

export const readerMediaSelector = 'img,picture,svg,video,canvas,object,embed,[role="img"]'

export const readerLinkHasMedia = anchor =>
  Boolean(anchor?.querySelector?.(readerMediaSelector))

export const isReaderMediaAnchor = anchor =>
  Boolean(anchor && (anchor.dataset?.navicLinkKind === 'media' || readerLinkHasMedia(anchor)))

export const isReaderMediaTapTarget = (target, anchor = closestElement(target, 'a[href]')) => {
  if (!anchor || !isReaderMediaAnchor(anchor)) return false
  const media = closestElement(target, readerMediaSelector)
  if (media && anchor.contains?.(media)) return true
  return target === anchor
}

export const readerPointInsideRect = (x, y, rect, slop = 3) =>
  Number.isFinite(x) &&
  Number.isFinite(y) &&
  Boolean(rect) &&
  x >= rect.left - slop &&
  x <= rect.right + slop &&
  y >= rect.top - slop &&
  y <= rect.bottom + slop

export const readerEventClientPoint = event => {
  const touch = event?.changedTouches?.[0] || event?.touches?.[0]
  const clientX = Number(touch?.clientX ?? event?.clientX)
  const clientY = Number(touch?.clientY ?? event?.clientY)
  return { clientX, clientY }
}

export const readerRootTapPoint = (event, doc) => {
  const { clientX, clientY } = readerEventClientPoint(event)
  if (!Number.isFinite(clientX) || !Number.isFinite(clientY)) return null
  const win = doc?.defaultView
  const frameElement = win?.frameElement
  const frameRect = frameElement?.getBoundingClientRect?.()
  if (frameRect) {
    return {
      x: frameRect.left + clientX,
      y: frameRect.top + clientY,
      source: 'frame',
    }
  }
  return {
    x: clientX,
    y: clientY,
    source: doc === document ? 'surface' : 'document',
  }
}

export const readerPointInsideAnchorText = (anchor, event) => {
  if (!anchor?.ownerDocument) return false
  const { clientX, clientY } = readerEventClientPoint(event)
  const x = Number(clientX)
  const y = Number(clientY)
  if (!Number.isFinite(x) || !Number.isFinite(y)) return true
  const doc = anchor.ownerDocument
  const walker = doc.createTreeWalker(anchor, NodeFilter.SHOW_TEXT, {
    acceptNode: node => node.textContent?.replace(/\s+/g, ' ').trim()
      ? NodeFilter.FILTER_ACCEPT
      : NodeFilter.FILTER_REJECT,
  })
  let node = walker.nextNode()
  let hasText = false
  while (node) {
    hasText = true
    const range = doc.createRange()
    try {
      range.selectNodeContents(node)
      for (const rect of range.getClientRects()) {
        if (readerPointInsideRect(x, y, rect, 6)) return true
      }
    } finally {
      range.detach?.()
    }
    node = walker.nextNode()
  }
  return false
}

export const readerMediaElementFromCandidate = candidate => {
  if (!candidate) return null
  if (candidate.matches?.(readerMediaSelector)) return candidate
  return candidate.querySelector?.(readerMediaSelector) || null
}

export const readerImageFromMediaTarget = mediaTarget => {
  if (!mediaTarget) return null
  if (mediaTarget.matches?.('img')) return mediaTarget
  return mediaTarget.querySelector?.('img') || null
}

export const readerMediaTapTargetForEvent = (doc, event, anchor) => {
  const target = event?.target
  const directMedia = closestElement(target, readerMediaSelector)
  if (directMedia) return directMedia
  if (anchor && isReaderMediaTapTarget(target, anchor)) {
    return readerMediaElementFromCandidate(anchor)
  }

  const { clientX, clientY } = readerEventClientPoint(event)
  const x = Number(clientX)
  const y = Number(clientY)
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null

  for (const candidate of doc?.elementsFromPoint?.(clientX, clientY) || []) {
    const media = readerMediaElementFromCandidate(candidate)
    if (readerPointInsideRect(x, y, media?.getBoundingClientRect?.())) return media
  }

  for (const media of doc?.querySelectorAll?.(readerMediaSelector) || []) {
    if (readerPointInsideRect(x, y, media?.getBoundingClientRect?.())) return media
  }
  return null
}

export const readerRectSnapshot = element => {
  const rect = element?.getBoundingClientRect?.()
  if (!rect) return null
  return {
    left: Number(rect.left),
    top: Number(rect.top),
    right: Number(rect.right),
    bottom: Number(rect.bottom),
  }
}

export const markReaderMediaTapHandled = (doc, event, mediaTarget = null) => {
  const win = doc?.defaultView
  if (!win) return
  win.__navicLastMediaTapHandledAt = event?.timeStamp || performance.now()
  win.__navicSuppressNextMediaClickUntil = performance.now() + ReaderMediaSyntheticClickSuppressMs
  const mediaRect = readerRectSnapshot(mediaTarget)
  if (mediaRect) win.__navicLastMediaTapRect = mediaRect
  const { clientX, clientY } = readerEventClientPoint(event)
  if (Number.isFinite(clientX) && Number.isFinite(clientY)) {
    win.__navicLastMediaTapClientX = clientX
    win.__navicLastMediaTapClientY = clientY
  }
}

export const readerLastMediaTapRectContainsPoint = (doc, event) => {
  const rect = doc?.defaultView?.__navicLastMediaTapRect
  if (!rect) return false
  const { clientX, clientY } = readerEventClientPoint(event)
  const x = Number(clientX)
  const y = Number(clientY)
  if (!Number.isFinite(x) || !Number.isFinite(y)) return false
  return readerPointInsideRect(x, y, rect, CenterTapMovementSlop * 2)
}

export const readerShouldSuppressMediaSyntheticClick = (doc, event, anchor) => {
  const win = doc?.defaultView
  if (!win) return false
  const timestamp = Number(event?.timeStamp || performance.now())
  const lastMediaTap = Number(win.__navicLastMediaTapHandledAt || 0)
  if (lastMediaTap && Math.abs(timestamp - lastMediaTap) < CenterTapSyntheticClickDedupeMs) return true

  const suppressUntil = Number(win.__navicSuppressNextMediaClickUntil || 0)
  if (suppressUntil && performance.now() <= suppressUntil) {
    win.__navicSuppressNextMediaClickUntil = 0
    return true
  }
  return false
}

export const markReaderDocumentTapHandled = (win, event) => {
  if (!win) return
  win.__navicLastTapHandledAt = event?.timeStamp || performance.now()
  win.__navicSuppressNextTapClickUntil = performance.now() + CenterTapSyntheticClickDedupeMs
}

export const shouldSuppressReaderDocumentClick = (win, event) => {
  if (!win) return false
  const timestamp = Number(event?.timeStamp || performance.now())
  const lastTap = Number(win.__navicLastTapHandledAt || 0)
  if (lastTap && Math.abs(timestamp - lastTap) < CenterTapSyntheticClickDedupeMs) return true

  const suppressUntil = Number(win.__navicSuppressNextTapClickUntil || 0)
  if (suppressUntil && performance.now() <= suppressUntil) {
    win.__navicSuppressNextTapClickUntil = 0
    return true
  }
  return false
}

export const markReaderSurfaceTapHandled = (element, event) => {
  if (!element) return
  element.__navicLastSurfaceTapHandledAt = event?.timeStamp || performance.now()
  element.__navicSuppressNextSurfaceClickUntil = performance.now() + CenterTapSyntheticClickDedupeMs
}

export const shouldSuppressReaderSurfaceClick = (element, event) => {
  if (!element) return false
  const timestamp = Number(event?.timeStamp || performance.now())
  const lastTap = Number(element.__navicLastSurfaceTapHandledAt || 0)
  if (lastTap && Math.abs(timestamp - lastTap) < CenterTapSyntheticClickDedupeMs) return true

  const suppressUntil = Number(element.__navicSuppressNextSurfaceClickUntil || 0)
  if (suppressUntil && performance.now() <= suppressUntil) {
    element.__navicSuppressNextSurfaceClickUntil = 0
    return true
  }
  return false
}

// Ported from Komikku's ViewerNavigation plus L/Kindlish/Edge/RightAndLeft region classes.
export const komikkuNavigationRegion = (left, top, right, bottom, type) => ({
  left,
  top,
  right,
  bottom,
  type,
})

export const komikkuConstantMenuRegion = komikkuNavigationRegion(
  0,
  0,
  1,
  0.05,
  KomikkuNavigationRegionMenu
)

export const komikkuRegionContains = (region, x, y) =>
  x >= region.left && x <= region.right && y >= region.top && y <= region.bottom

export const komikkuRegionSize = smallerTapZone => smallerTapZone ? 0.25 : 0.33

export const komikkuDefaultNavigationMode = flowMode =>
  flowMode === ReaderFlowPagedVertical ||
  flowMode === ReaderFlowScrolled ||
  flowMode === ReaderFlowScrolledGaps
    ? ReaderTapZoneLShaped
    : ReaderTapZoneRightLeft

export const komikkuNavigationRegions = (
  tapZoneMode,
  smallerTapZone = false,
  flowMode = ReaderFlowPaged
) => {
  const mode = tapZoneMode === ReaderTapZoneDefault
    ? komikkuDefaultNavigationMode(flowMode)
    : tapZoneMode
  const regionSize1 = komikkuRegionSize(smallerTapZone)
  const regionSize2 = 1 - regionSize1
  switch (mode) {
    case ReaderTapZoneLShaped:
      return [
        komikkuNavigationRegion(0, regionSize1, regionSize1, regionSize2, KomikkuNavigationRegionPrevious),
        komikkuNavigationRegion(0, 0, 1, regionSize1, KomikkuNavigationRegionPrevious),
        komikkuNavigationRegion(regionSize2, regionSize1, 1, regionSize2, KomikkuNavigationRegionNext),
        komikkuNavigationRegion(0, regionSize2, 1, 1, KomikkuNavigationRegionNext),
      ]
    case ReaderTapZoneKindle:
      return [
        komikkuNavigationRegion(regionSize1, regionSize1, 1, 1, KomikkuNavigationRegionNext),
        komikkuNavigationRegion(0, regionSize1, regionSize1, 1, KomikkuNavigationRegionPrevious),
      ]
    case ReaderTapZoneEdge:
      return [
        komikkuNavigationRegion(0, 0, regionSize1, 1, KomikkuNavigationRegionNext),
        komikkuNavigationRegion(regionSize1, regionSize2, regionSize2, 1, KomikkuNavigationRegionPrevious),
        komikkuNavigationRegion(regionSize2, 0, 1, 1, KomikkuNavigationRegionNext),
      ]
    case ReaderTapZoneRightLeft:
      return [
        komikkuNavigationRegion(0, 0, regionSize1, 1, KomikkuNavigationRegionLeft),
        komikkuNavigationRegion(regionSize2, 0, 1, 1, KomikkuNavigationRegionRight),
      ]
    case ReaderTapZoneDisabled:
      return []
    default:
      return komikkuNavigationRegions(ReaderTapZoneDefault, smallerTapZone, flowMode)
  }
}

export const komikkuTapAction = (
  tapZoneMode,
  x,
  y,
  smallerTapZone = false,
  flowMode = ReaderFlowPaged
) => {
  const regions = komikkuNavigationRegions(tapZoneMode, smallerTapZone, flowMode)
  const region = regions.find(candidate => komikkuRegionContains(candidate, x, y))
  if (region) return region.type
  if (komikkuRegionContains(komikkuConstantMenuRegion, x, y)) return KomikkuNavigationRegionMenu
  return KomikkuNavigationRegionMenu
}

export const readerTapZoneIsPageTurn = tapZone =>
  tapZone === KomikkuNavigationRegionPrevious ||
  tapZone === KomikkuNavigationRegionNext ||
  tapZone === KomikkuNavigationRegionLeft ||
  tapZone === KomikkuNavigationRegionRight
