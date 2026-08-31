import {
  post,
  readerHrefMatches,
  readerMediaOverlayClampRangeBeforeNextCue,
  readerMediaOverlayNormalizedTextMap,
  readerMediaOverlayRawOffsetForNormalizedOffset,
  readerMediaOverlayResolvedTextRange,
  readerMediaOverlayTextEntries,
  readerMediaOverlayTextPoint,
  stableHash,
} from './navic-reader-helpers.js'
import {
  readerCssColorFromArgb,
  readerDrawMediaOverlayMarker,
  readerDrawMediaOverlaySelection,
} from './navic-reader-overlay-paint.js'
import {
  ReaderWordSyncV1ExtractedUtf8Mode,
  retryReaderWordSyncOverlayFragment,
  routeReaderOverlayCoordinateMode,
  validatedReaderOverlayCoordinateMode,
} from './navic-reader-wordsync-provenance.js'

export const ReaderMediaOverlayPlayedRangeKeyPrefix = 'navic-media-overlay-played-'

function mediaOverlayFollowShouldDeferForUserRelocation() {
  const reason = String(this.controlledRelocateReason || '').trim()
  return Boolean(reason) && reason !== 'media-overlay-follow'
}

function exactWordSyncBoundarySequence(fragment) {
  const sequence = fragment?.wordBoundarySequence
  return Number.isSafeInteger(sequence) && sequence >= 0 ? sequence : null
}

function exactWordSyncOverlayRelocationIsUnsettled() {
  return Boolean(this.activeExactWordSyncRelocation)
}

function beginExactWordSyncOverlayRelocation() {
  const previousEpoch = Number(this.exactWordSyncRelocationEpoch) || 0
  if (
    !Number.isSafeInteger(previousEpoch) || previousEpoch < 0 ||
    previousEpoch >= Number.MAX_SAFE_INTEGER
  ) {
    this.activeExactWordSyncRelocation = null
    this.deferredExactWordSyncOverlay = null
    return null
  }
  const epoch = previousEpoch + 1
  this.exactWordSyncRelocationEpoch = epoch
  this.activeExactWordSyncRelocation = {
    epoch,
    foliateSessionId: this.foliateSessionId,
  }
  this.deferredExactWordSyncOverlay = null
  return epoch
}

function deferExactWordSyncOverlayFragment(fragment) {
  if (validatedReaderOverlayCoordinateMode(fragment) !== ReaderWordSyncV1ExtractedUtf8Mode) {
    return false
  }
  const boundarySequence = exactWordSyncBoundarySequence(fragment)
  if (boundarySequence == null) return true
  const cleared = this.exactWordSyncClearedPresentation
  if (
    cleared?.overlayRequestId === fragment.overlayRequestId &&
    boundarySequence <= cleared.boundarySequence
  ) return true
  if (!this.exactWordSyncOverlayRelocationIsUnsettled()) return false
  const activeRequestId = this.exactWordSyncActiveRequestId ??
    this.mediaOverlayActiveFragment?.overlayRequestId ??
    this.deferredExactWordSyncOverlay?.fragment?.overlayRequestId
  if (
    activeRequestId != null &&
    fragment.overlayRequestId !== activeRequestId
  ) {
    if (
      !Number.isSafeInteger(fragment.overlayRequestId) ||
      !Number.isSafeInteger(activeRequestId) ||
      fragment.overlayRequestId < activeRequestId
    ) return true
  }
  const relocation = this.activeExactWordSyncRelocation
  if (relocation.foliateSessionId !== this.foliateSessionId) {
    this.activeExactWordSyncRelocation = null
    this.deferredExactWordSyncOverlay = null
    return true
  }
  const pending = this.deferredExactWordSyncOverlay
  if (
    pending?.epoch === relocation.epoch &&
    pending?.foliateSessionId === relocation.foliateSessionId
  ) {
    const pendingRequestId = pending.fragment?.overlayRequestId
    const requestMatches = pendingRequestId === fragment.overlayRequestId
    if (
      Number.isSafeInteger(pendingRequestId) &&
      Number.isSafeInteger(fragment.overlayRequestId) &&
      fragment.overlayRequestId < pendingRequestId
    ) return true
    if (requestMatches && pending.boundarySequence > boundarySequence) return true
  }
  this.deferredExactWordSyncOverlay = {
    epoch: relocation.epoch,
    foliateSessionId: relocation.foliateSessionId,
    boundarySequence,
    fragment,
  }
  this.exactWordSyncActiveRequestId = fragment.overlayRequestId
  return true
}

function completeExactWordSyncOverlayRelocation(epoch) {
  const relocation = this.activeExactWordSyncRelocation
  if (!relocation || relocation.epoch !== epoch) return false
  this.activeExactWordSyncRelocation = null
  const pending = this.deferredExactWordSyncOverlay
  this.deferredExactWordSyncOverlay = null
  if (
    !pending || pending.epoch !== epoch ||
    pending.foliateSessionId !== this.foliateSessionId
  ) return false
  return retryReaderWordSyncOverlayFragment(this, pending.fragment)
}

function dropDeferredExactWordSyncOverlayFragment() {
  const dropped = this.deferredExactWordSyncOverlay != null ||
    this.activeExactWordSyncRelocation != null
  this.deferredExactWordSyncOverlay = null
  this.activeExactWordSyncRelocation = null
  return dropped
}

function clearExactWordSyncOverlayPresentation(
  overlayRequestId,
  clearedThroughBoundarySequence,
) {
  const activeRequestId = this.exactWordSyncActiveRequestId ??
    this.mediaOverlayActiveFragment?.overlayRequestId
  if (
    !Number.isSafeInteger(overlayRequestId) ||
    !Number.isSafeInteger(clearedThroughBoundarySequence) ||
    activeRequestId !== overlayRequestId
  ) return false
  const pending = this.deferredExactWordSyncOverlay
  if (
    pending?.fragment?.overlayRequestId === overlayRequestId &&
    pending.boundarySequence <= clearedThroughBoundarySequence
  ) {
    this.deferredExactWordSyncOverlay = null
  }
  this.exactWordSyncClearedPresentation = {
    overlayRequestId,
    boundarySequence: Math.max(
      this.exactWordSyncClearedPresentation?.overlayRequestId === overlayRequestId
        ? this.exactWordSyncClearedPresentation.boundarySequence
        : -1,
      clearedThroughBoundarySequence,
    ),
  }
  this.clearOverlay({ preserveActiveFragment: true })
  return true
}

function mediaOverlayFragmentHasTextRange(fragment) {
  return routeReaderOverlayCoordinateMode(fragment, {
    raw: () => true,
    cue: () => {
      const textStart = Number(fragment?.textStart)
      const textEnd = Number(fragment?.textEnd)
      return Number.isFinite(textStart) && Number.isFinite(textEnd) && textEnd > textStart
    },
    reject: () => false,
  })
}

function mediaOverlayPlayedKeyForFragment(fragment) {
  return `${this.mediaOverlayPlayedKeyPrefix}${stableHash([
    fragment?.textHref || '',
    fragment?.clipBeginSeconds ?? '',
    fragment?.clipEndSeconds ?? '',
    fragment?.textStart ?? '',
    fragment?.textEnd ?? '',
    fragment?.ebookText || '',
  ].join('|'))}`
}

function postOverlayFragmentInactive(fragment, reason) {
  post({
    type: 'overlayFragmentInactive',
    fragmentId: fragment?.fragmentId || null,
    overlayRequestId: fragment?.overlayRequestId ?? null,
    coordinateMode: fragment?.coordinateMode ?? null,
    reason,
  })
}

function mediaOverlayAnimationKeyForFragment(fragment) {
  if (!fragment) return ''
  return [
    fragment.textHref || '',
    fragment.resourceHref || '',
    fragment.clipBeginSeconds ?? '',
    fragment.clipEndSeconds ?? '',
    fragment.textStart ?? '',
    fragment.textEnd ?? '',
    fragment.ebookText || '',
  ].join('|')
}

function mediaOverlayFragmentAlreadyVisible(fragment) {
  const coordinateMode = validatedReaderOverlayCoordinateMode(fragment)
  if (!coordinateMode) return false
  if (coordinateMode === ReaderWordSyncV1ExtractedUtf8Mode) {
    return this.rawTextProvenance.rangeIsVisible(fragment, this.committedVisibleTextRange)
  }
  const textStart = Number(fragment?.textStart)
  const textEnd = Number(fragment?.textEnd)
  if (!Number.isFinite(textStart) || !Number.isFinite(textEnd) || textEnd <= textStart) return false
  const visibleRange = this.currentVisibleTextRangeForHref?.(fragment?.textHref || '')
  if (!visibleRange) return false
  return textEnd > Number(visibleRange.visibleStart) && textStart < Number(visibleRange.visibleEnd)
}

function mediaOverlayFragmentProgressAlreadyVisible(fragment) {
  return this.mediaOverlayFragmentAlreadyVisible(fragment)
}

function rememberPlayedMediaOverlayFragment(previousFragment, nextFragment) {
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

function prunePlayedMediaOverlayFragments(fragment) {
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

function paintPlayedMediaOverlayFragments() {
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

function readerMediaOverlayPersistentPlayed(settings = this.readerSettings) {
  return settings?.whispersyncHighlightLoading === 'persistent-played-text'
}

function resolveCanonicalCueTextRange(content, cue) {
  const provenance = this.rawTextProvenance
  if (!provenance || typeof provenance.resolveRange !== 'function' ||
    typeof provenance.resolvedRangeMatchesText !== 'function') return null
  const locator = typeof cue?.ebookText === 'string' ? cue.ebookText : ''
  if (!locator.trim()) return null
  const range = provenance.resolveRange({ ...cue, ebookText: undefined })
  if (!range || range.collapsed || range.startContainer?.ownerDocument !== content?.doc) return null
  if (!provenance.resolvedRangeMatchesText(range, locator, cue)) return null
  return { range }
}

function resolveMediaOverlayTextRange(content, fragment, paintEnd = fragment?.textEnd) {
  if (fragment?.coordinateMode === ReaderWordSyncV1ExtractedUtf8Mode) {
    return resolveCanonicalCueTextRange.call(this, content, fragment)
  }
  if (!validatedReaderOverlayCoordinateMode(fragment)) return null
  const textStart = Number(fragment?.textStart)
  const textEnd = Number(fragment?.textEnd)
  if (!Number.isFinite(textStart) || !Number.isFinite(textEnd) || textEnd <= textStart) return null
  const section = Number.isFinite(Number(content?.index))
    ? this.view?.book?.sections?.[Math.floor(Number(content.index))]
    : null
  const sectionHref = section?.href || content?.href || ''
  if (fragment.textHref && sectionHref && !readerHrefMatches(sectionHref, fragment.textHref)) return null
  const entries = readerMediaOverlayTextEntries(content?.doc)
  if (!entries.length) return null
  const normalizedMap = readerMediaOverlayNormalizedTextMap(entries)
  const resolvedRangeBeforeClamp = readerMediaOverlayResolvedTextRange(
    normalizedMap,
    textStart,
    textEnd,
    fragment.ebookText
  )
  if (!resolvedRangeBeforeClamp) return null
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
  const resolvedPaintEnd = this.mediaOverlayPaintEndForResolvedRange(
    textStart,
    textEnd,
    Math.min(textEnd, Math.max(textStart + 1, Number(paintEnd))),
    resolvedRange.normalizedTextStart,
    resolvedRange.normalizedTextEnd,
    fragment
  )
  const resolvedRawPaintEnd = readerMediaOverlayRawOffsetForNormalizedOffset(
    normalizedMap,
    resolvedPaintEnd,
    'end'
  )
  const start = readerMediaOverlayTextPoint(entries, Math.floor(resolvedRange.textStart))
  const end = readerMediaOverlayTextPoint(
    entries,
    Math.ceil(Math.min(resolvedRange.textEnd, resolvedRawPaintEnd))
  )
  if (!start || !end || !content?.doc?.createRange) return null
  try {
    const range = content.doc.createRange()
    range.setStart(start.node, start.offset)
    range.setEnd(end.node, end.offset)
    if (range.collapsed) return null
    return { range, resolvedRange, resolvedPaintEnd, resolvedRawPaintEnd, textStart, textEnd }
  } catch (_) {
    return null
  }
}

function readerMediaOverlayHighlightColor(settings = this.readerSettings) {
  return readerCssColorFromArgb(settings?.whispersyncHighlightColorArgb, 'rgba(246, 195, 67, 0.4)')
}

function readerMediaOverlayHighlightDraw(settings = this.readerSettings) {
  return settings?.whispersyncHighlightStyle === 'marker'
    ? readerDrawMediaOverlayMarker
    : readerDrawMediaOverlaySelection
}

export const NavicReaderMediaOverlayMethods = {
  mediaOverlayFollowShouldDeferForUserRelocation,
  exactWordSyncOverlayRelocationIsUnsettled,
  beginExactWordSyncOverlayRelocation,
  deferExactWordSyncOverlayFragment,
  completeExactWordSyncOverlayRelocation,
  dropDeferredExactWordSyncOverlayFragment,
  clearExactWordSyncOverlayPresentation,
  mediaOverlayFragmentHasTextRange,
  mediaOverlayPlayedKeyForFragment,
  mediaOverlayAnimationKeyForFragment,
  postOverlayFragmentInactive,
  mediaOverlayFragmentAlreadyVisible,
  mediaOverlayFragmentProgressAlreadyVisible,
  rememberPlayedMediaOverlayFragment,
  prunePlayedMediaOverlayFragments,
  paintPlayedMediaOverlayFragments,
  resolveMediaOverlayTextRange,
  readerMediaOverlayPersistentPlayed,
  readerMediaOverlayHighlightColor,
  readerMediaOverlayHighlightDraw,
}
