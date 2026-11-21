import { readerViewportSize, withReaderViewportOverride } from './navic-reader-helpers.js'
import { readerPageLocatorForVisualIndex } from './navic-reader-page-turn-model.js'
import { ReaderFlowPaged, readerFlowMode } from './navic-reader-settings-core.js'

// #57 "Both layouts": the other orientation's geometry is learned, not predicted. Each time a
// complete pagination profile is in use, its viewport is remembered per orientation; the other
// orientation is then described from that viewport and its cached complete profile.
export const ReaderLayoutViewportKeyPrefix = 'navic-reader-layout-viewport:'

const orientationOf = ({ width, height }) => (width >= height ? 'landscape' : 'portrait')

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

// The other orientation's layout, or null when it has never been seen with these settings.
function readerAlternateLayout() {
  if (
    !this.publicationUrl ||
    !this.paginationProfile ||
    this.view?.isFixedLayout === true ||
    readerFlowMode(this.readerSettings) !== ReaderFlowPaged
  ) return null
  const orientation = orientationOf(readerViewportSize()) === 'landscape' ? 'portrait' : 'landscape'
  const viewport = rememberedReaderLayoutViewport(orientation)
  if (!viewport || viewport.devicePixelRatio !== (Number(window.devicePixelRatio) || 1)) return null
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
    const other = orientationOf(readerViewportSize()) === 'landscape' ? 'portrait' : 'landscape'
    const failureReason = layout
      ? 'no-alternate-center-page'
      : rememberedReaderLayoutViewport(other) ? 'no-alternate-profile' : 'no-remembered-viewport'
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

export const NavicReaderAlternateLayoutMethods = {
  rememberReaderLayoutViewport,
  readerAlternateLayout,
  readerAlternateLayoutCenterPageIndex,
  pageTurnAlternateLayoutPlan,
  pageTurnAlternatePassiveRasterDescriptor,
  pageTurnAlternatePassiveRasterManifestInputs,
}
