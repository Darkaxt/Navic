import { post, stableHash } from './navic-reader-helpers.js'
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
  if (!this.mediaOverlayFragmentAlreadyVisible(fragment)) return false
  if (validatedReaderOverlayCoordinateMode(fragment) === ReaderWordSyncV1ExtractedUtf8Mode) return true
  const progressFraction = Number(fragment?.textProgressFraction)
  if (!Number.isFinite(progressFraction)) return true
  const progressEnd = Number(fragment?.textProgressEnd)
  if (!Number.isFinite(progressEnd)) return true
  const visibleRange = this.currentVisibleTextRangeForHref?.(fragment?.textHref || '')
  if (!visibleRange) return false
  return progressEnd < Number(visibleRange.visibleEnd)
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
}
