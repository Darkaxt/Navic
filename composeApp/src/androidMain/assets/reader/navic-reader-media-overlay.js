import { post, stableHash } from './navic-reader-helpers.js'
import {
  ReaderWordSyncV1ExtractedUtf8Mode,
  routeReaderOverlayCoordinateMode,
  validatedReaderOverlayCoordinateMode,
} from './navic-reader-wordsync-provenance.js'

export const ReaderMediaOverlayPlayedRangeKeyPrefix = 'navic-media-overlay-played-'

function mediaOverlayFollowShouldDeferForUserRelocation() {
  const reason = String(this.controlledRelocateReason || '').trim()
  return Boolean(reason) && reason !== 'media-overlay-follow'
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
  mediaOverlayFragmentHasTextRange,
  mediaOverlayPlayedKeyForFragment,
  mediaOverlayAnimationKeyForFragment,
  postOverlayFragmentInactive,
  mediaOverlayFragmentAlreadyVisible,
  mediaOverlayFragmentProgressAlreadyVisible,
}
