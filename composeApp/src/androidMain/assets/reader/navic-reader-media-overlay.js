function mediaOverlayFragmentAlreadyVisible(fragment) {
  const textStart = Number(fragment?.textStart)
  const textEnd = Number(fragment?.textEnd)
  if (!Number.isFinite(textStart) || !Number.isFinite(textEnd) || textEnd <= textStart) return false
  const visibleRange = this.currentVisibleTextRangeForHref?.(fragment?.textHref || '')
  if (!visibleRange) return false
  return textEnd > Number(visibleRange.visibleStart) && textStart < Number(visibleRange.visibleEnd)
}

function mediaOverlayFragmentProgressAlreadyVisible(fragment) {
  if (!this.mediaOverlayFragmentAlreadyVisible(fragment)) return false
  const progressEnd = Number(fragment?.textProgressEnd)
  if (!Number.isFinite(progressEnd)) return true
  const visibleRange = this.currentVisibleTextRangeForHref?.(fragment?.textHref || '')
  if (!visibleRange) return false
  return progressEnd < Number(visibleRange.visibleEnd)
}

export const NavicReaderMediaOverlayMethods = {
  mediaOverlayFragmentAlreadyVisible,
  mediaOverlayFragmentProgressAlreadyVisible,
}
