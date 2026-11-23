import { readerViewportSize, withReaderViewportOverride } from './navic-reader-helpers.js'
import { readerPageLocatorForVisualIndex } from './navic-reader-page-turn-model.js'
import { ReaderFlowPaged, readerFlowMode } from './navic-reader-settings-core.js'
import { readerTrace } from './navic-reader-bridge-core.js'

// #57 "Both layouts": each time a complete pagination profile is in use, its viewport is
// remembered per orientation. #70: an orientation never seen yet is predicted as the current
// viewport with width and height swapped, which is exact for the edge-to-edge reader. The other
// orientation is then described from that viewport and its cached complete profile.
// Version 2 drops viewports remembered before the reader went edge to edge (#70).
export const ReaderLayoutViewportKeyPrefix = 'navic-reader-layout-viewport-2:'

const orientationOf = ({ width, height }) => (width >= height ? 'landscape' : 'portrait')

const otherOrientation = () => (orientationOf(readerViewportSize()) === 'landscape' ? 'portrait' : 'landscape')

// The remembered viewport of `orientation` at the current DPR, else the current one swapped.
export const readerPredictedLayoutViewport = (orientation, current, remembered, devicePixelRatio) => {
  if (remembered && remembered.devicePixelRatio === devicePixelRatio) return remembered
  const swapped = { width: current.height, height: current.width, devicePixelRatio }
  return orientationOf(current) !== orientation && orientationOf(swapped) === orientation ? swapped : null
}

function rememberReaderLayoutViewport(profile) {
  const width = Math.round(Number(profile?.render?.viewportWidth))
  const height = Math.round(Number(profile?.render?.viewportHeight))
  if (!(width > 0 && height > 0)) return
  this.alternateLayoutProfileValue = null
  const devicePixelRatio = Number(profile.render.deviceScaleFactor) || 1
  try {
    window.localStorage?.setItem?.(
      `${ReaderLayoutViewportKeyPrefix}${orientationOf({ width, height })}`,
      JSON.stringify({ width, height, devicePixelRatio })
    )
  } catch (_) {
    // A missing remembered viewport only means the first rotation prepares normally.
  }
}

function rememberedReaderLayoutViewport(orientation) {
  try {
    const value = JSON.parse(
      window.localStorage?.getItem?.(`${ReaderLayoutViewportKeyPrefix}${orientation}`) || 'null'
    )
    const width = Math.round(Number(value?.width))
    const height = Math.round(Number(value?.height))
    if (!(width > 0 && height > 0) || orientationOf({ width, height }) !== orientation) return null
    return { width, height, devicePixelRatio: Number(value.devicePixelRatio) || 1 }
  } catch (_) {
    return null
  }
}

function readerAlternateLayoutViewport(orientation = otherOrientation()) {
  return readerPredictedLayoutViewport(
    orientation,
    readerViewportSize(),
    rememberedReaderLayoutViewport(orientation),
    Number(window.devicePixelRatio) || 1
  )
}

// The other orientation's layout, or null until its complete profile is cached for these settings.
function readerAlternateLayout() {
  if (
    !this.publicationUrl ||
    !this.paginationProfile ||
    this.view?.isFixedLayout === true ||
    readerFlowMode(this.readerSettings) !== ReaderFlowPaged
  ) return null
  const orientation = otherOrientation()
  const viewport = this.readerAlternateLayoutViewport(orientation)
  if (!viewport) return null
  const fingerprint = withReaderViewportOverride(viewport, () => this.readerPaginationRenderFingerprint())
  // Parsing a whole cached profile is not free, so a found profile is kept per fingerprint.
  // Misses are not kept: the profile appears once that orientation has been read.
  if (this.alternateLayoutProfileValue?.fingerprint !== fingerprint) {
    const cached = this.readCachedPaginationProfile(fingerprint)
    if (!cached?.chapters?.length || Number(cached.estimatedChapterCount) !== 0) return null
    this.alternateLayoutProfileValue = { fingerprint, profile: cached }
  }
  const profile = this.alternateLayoutProfileValue.profile
  return Object.freeze({
    orientation,
    viewport: Object.freeze({ width: viewport.width, height: viewport.height }),
    fingerprint,
    profile,
    captureGeometry: withReaderViewportOverride(viewport, () => this.pageTurnCaptureGeometry()),
  })
}

// The alternate page holding the same chapter anchor as the live current page.
function readerAlternateLayoutCenterPageIndex(layout) {
  const position = this.currentPagePosition
  const locator = readerPageLocatorForVisualIndex(this.paginationProfile, position?.pageIndex)
  if (!layout || !locator) return null
  const chapter = layout.profile.chapters.find(candidate =>
    Math.floor(Number(candidate?.spineIndex)) === locator.spineIndex &&
    Math.floor(Number(candidate?.pageCount)) > 0
  )
  if (!chapter) return null
  const count = Math.floor(Number(chapter.pageCount))
  return Math.max(0, Math.floor(Number(chapter.pageStartIndex) || 0)) +
    Math.min(count - 1, Math.round(locator.anchor * (count - 1)))
}

// Unavailable plans name their reason so the native side can log why nothing was prefetched.
function pageTurnAlternateLayoutPlan() {
  const layout = this.readerAlternateLayout()
  const center = this.readerAlternateLayoutCenterPageIndex(layout)
  if (center == null) {
    const failureReason = layout
      ? 'no-alternate-center-page'
      : this.readerAlternateLayoutViewport() ? 'no-alternate-profile' : 'no-alternate-viewport'
    return Object.freeze({ failureReason })
  }
  const plan = this.pageTurnRasterPreparationPlan(center, layout)
  return Object.freeze({ ...plan, orientation: layout.orientation })
}

function pageTurnAlternatePassiveRasterDescriptor(visualPageOrdinal) {
  const layout = this.readerAlternateLayout()
  return layout
    ? withReaderViewportOverride(layout.viewport, () =>
        this.pageTurnPassiveRasterDescriptor(visualPageOrdinal, layout))
    : null
}

function pageTurnAlternatePassiveRasterManifestInputs(visualPageOrdinal, captureEpoch, rasterGeneration) {
  const layout = this.readerAlternateLayout()
  if (!layout) return Object.freeze({ failureReason: 'alternate-layout-unavailable' })
  // The override also gives the page-number label the other orientation's spread slots.
  return withReaderViewportOverride(layout.viewport, () =>
    this.pageTurnPassiveRasterManifestInputs(visualPageOrdinal, captureEpoch, rasterGeneration, layout))
}

// #70 "Prepare pages" with "Both layouts": measures the other orientation's complete profile in a
// profiler view sized to its viewport, so no rotation is needed. It has its own task token and
// posts no live profile status; native reads alternatePaginationProfileStatus() until it settles.
async function ensureAlternatePaginationProfile() {
  const url = this.publicationUrl
  const settings = this.readerSettings
  const viewport = this.readerAlternateLayoutViewport()
  if (
    !url ||
    !this.paginationProfile ||
    this.view?.isFixedLayout === true ||
    readerFlowMode(settings) !== ReaderFlowPaged ||
    !viewport
  ) return this.setAlternatePaginationProfileStatus('failed', 'alternate-layout-unsupported')
  if (this.readerAlternateLayout()) return this.setAlternatePaginationProfileStatus('ready')
  const liveFingerprint = this.readerPaginationRenderFingerprint()
  const fingerprint = withReaderViewportOverride(viewport, () => this.readerPaginationRenderFingerprint())
  const key = `${url}|${liveFingerprint}|${fingerprint}`
  if (
    this.alternatePaginationProfileStatusValue.state === 'building' &&
    this.alternatePaginationProfileTaskKey === key
  ) return this.alternatePaginationProfileStatus()
  const token = ++this.alternatePaginationProfileTaskToken
  this.alternatePaginationProfileTaskKey = key
  // A relayout or another book makes the build stale; the live profile task is never touched.
  const isCurrent = () =>
    token === this.alternatePaginationProfileTaskToken &&
    url === this.publicationUrl &&
    liveFingerprint === this.readerPaginationRenderFingerprint()
  this.setAlternatePaginationProfileStatus('building')
  try {
    const profile = await this.buildCompletePaginationProfileInProfilerView({
      url,
      fingerprint,
      settings,
      token,
      viewport,
      isCurrent,
      quiet: true,
    })
    if (token !== this.alternatePaginationProfileTaskToken) return this.alternatePaginationProfileStatus()
    if (!isCurrent()) return this.setAlternatePaginationProfileStatus('failed', 'superseded')
    if (!profile?.chapters?.length || Number(profile.estimatedChapterCount) !== 0) {
      return this.setAlternatePaginationProfileStatus('failed', 'incomplete-profile')
    }
    if (!this.writeCachedPaginationProfile(profile)) {
      return this.setAlternatePaginationProfileStatus('failed', 'profile-cache-write-failed')
    }
    this.rememberReaderLayoutViewport(profile)
    readerTrace('pagination-profile:alternate-ready', {
      fingerprint,
      pageCount: profile.pageCount,
      viewport: `${viewport.width}x${viewport.height}`,
    })
    return this.readerAlternateLayout()
      ? this.setAlternatePaginationProfileStatus('ready')
      : this.setAlternatePaginationProfileStatus('failed', 'alternate-layout-unavailable')
  } catch (error) {
    if (token !== this.alternatePaginationProfileTaskToken) return this.alternatePaginationProfileStatus()
    return this.setAlternatePaginationProfileStatus('failed', String(error?.message || error || 'build-failed'))
  }
}

function setAlternatePaginationProfileStatus(state, reason = '') {
  this.alternatePaginationProfileStatusValue = Object.freeze({ state, reason: state === 'failed' ? reason : '' })
  return this.alternatePaginationProfileStatusValue
}

// idle | building | ready | failed, with the failure reason; read synchronously by native.
function alternatePaginationProfileStatus() {
  return this.alternatePaginationProfileStatusValue
}

export const NavicReaderAlternateLayoutMethods = {
  rememberReaderLayoutViewport,
  readerAlternateLayoutViewport,
  readerAlternateLayout,
  readerAlternateLayoutCenterPageIndex,
  pageTurnAlternateLayoutPlan,
  pageTurnAlternatePassiveRasterDescriptor,
  pageTurnAlternatePassiveRasterManifestInputs,
  ensureAlternatePaginationProfile,
  setAlternatePaginationProfileStatus,
  alternatePaginationProfileStatus,
}
