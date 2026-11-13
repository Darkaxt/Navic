// "Persistent played text": on one page the highlight starts at the first segment painted there and
// grows to the live progress point. The anchor is that first segment's raw byte span. It survives while
// the span still overlaps the committed visible range, so a late repost of the same page (raw
// provenance arriving after open, a relayout) keeps it. A page change, another document or a backward
// seek before its start re-anchors at the current segment.

const rawByte = value => Number.isSafeInteger(value) && value >= 0

export const readerWordSyncAnchorOnPage = (anchor, visibleRange) => {
  // No committed raw range yet: nothing proves the page changed.
  if (!rawByte(visibleRange?.rawByteStart) || !rawByte(visibleRange?.rawByteEnd)) return true
  return visibleRange.rawProvenanceId === anchor.rawProvenanceId &&
    anchor.rawByteEnd > visibleRange.rawByteStart &&
    anchor.rawByteStart < visibleRange.rawByteEnd
}

export const readerWordSyncPersistentStartByte = (anchor, fragment, visibleRange, persistent) =>
  persistent && anchor &&
    anchor.rawProvenanceId === fragment.rawProvenanceId &&
    fragment.rawByteStart >= anchor.rawByteStart &&
    readerWordSyncAnchorOnPage(anchor, visibleRange)
    ? anchor.rawByteStart
    : null

export const readerWordSyncNextAnchor = (anchor, fragment, startByte, persistent) => {
  if (!persistent) return null
  if (startByte != null) return anchor
  return {
    rawProvenanceId: fragment.rawProvenanceId,
    rawByteStart: fragment.rawByteStart,
    rawByteEnd: fragment.rawByteEnd,
  }
}
