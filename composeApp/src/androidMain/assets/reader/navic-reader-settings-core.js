export const ReaderDocumentThemeStyleId = 'navic-reader-document-theme'
export const ReaderSurfacePaperTextureLayerSelector = '[data-navic-surface-paper-texture-layer="true"]'
export const ReaderSurfacePageBorderOverlayLayerSelector = '[data-navic-surface-page-border-overlay-layer="true"]'
export const ReaderSurfacePageStainOverlayLayerSelector = '[data-navic-surface-page-stain-overlay-layer="true"]'
export const ReaderMovingPagePaperTextureLayerSelector = '[data-navic-moving-page-paper-texture-layer="true"]'
export const ReaderMovingPageBorderOverlayLayerSelector = '[data-navic-moving-page-border-overlay-layer="true"]'
export const ReaderMovingPageStainOverlayLayerSelector = '[data-navic-moving-page-stain-overlay-layer="true"]'
export const ReaderTapZoneOverlayLayerSelector = '[data-navic-tap-zone-overlay-layer="true"]'
export const ReaderPageNumberLayerSelector = '[data-navic-page-number-layer="true"]'
export const ReaderShellCoverLayerSelector = '[data-navic-shell-cover-layer="true"]'
export const ReaderThemeLight = 'light'
export const ReaderThemeSepia = 'sepia'
export const ScrollEdgeTurnSwipeThreshold = 60
export const ScrollEdgeTurnSlop = 2
export const FixedLayoutSurfaceSwipeThreshold = 56
export const CenterTapMovementSlop = 12
export const CenterTapSyntheticClickDedupeMs = 650
export const ReaderMediaSyntheticClickSuppressMs = 1200
export const ReaderShellCoverTransitionMs = 280
export const ReaderTapZoneDefault = 'default'
export const ReaderTapZoneEdge = 'edge'
export const ReaderTapZoneKindle = 'kindle'
export const ReaderTapZoneLShaped = 'l-shaped'
export const ReaderTapZoneRightLeft = 'right-left'
export const ReaderTapZoneDisabled = 'disabled'
export const KomikkuNavigationRegionMenu = 'menu'
export const KomikkuNavigationRegionPrevious = 'previous'
export const KomikkuNavigationRegionNext = 'next'
export const KomikkuNavigationRegionLeft = 'left'
export const KomikkuNavigationRegionRight = 'right'
export const ReaderFlowPaged = 'paged'
export const ReaderFlowPagedVertical = 'paged-vertical'
export const ReaderFlowScrolled = 'scrolled'
export const ReaderFlowScrolledGaps = 'scrolled-gaps'
export const ReaderPageTurnNone = 'none'
export const ReaderPageTurnCanvas = 'canvas'
export const ReaderPageTurnWebgl = 'webgl'
export const ReaderPageTurnDefault = ReaderPageTurnNone
export const ReaderSupportedPageTurnAnimations = [ReaderPageTurnNone, ReaderPageTurnCanvas, ReaderPageTurnWebgl]
export const ReaderDirectionDefault = 'default'
export const ReaderDirectionLtr = 'ltr'
export const ReaderDirectionRtl = 'rtl'
export const ReaderFontSourceNavic = 'navic'
export const ReaderFontSourceSystem = 'system'
export const ReaderFontSourcePublisher = 'publisher'
export const ReaderFontSourceCustom = 'custom'
export const ReaderReflowableReadableUnitsPerSyntheticPage = 1500
export const ReaderReflowableStartProgressPageOffsetThreshold = 0.006
export const ReaderReflowableProgressEpsilon = 0.0000001
export const ReaderPaperTextureAssets = [
  'paper-textures/paper-texture-01.jpg',
  'paper-textures/paper-texture-02.jpg',
  'paper-textures/paper-texture-03.jpg',
  'paper-textures/paper-texture-04.jpg',
  'paper-textures/paper-texture-05.jpg',
  'paper-textures/paper-texture-06.jpg',
  'paper-textures/paper-texture-07.jpg',
  'paper-textures/paper-texture-08.jpg',
  'paper-textures/paper-texture-09.jpg',
]
export const ReaderPaperTextureVariantCount = ReaderPaperTextureAssets.length * 2 * 2
export const ReaderPageEdgeOverlayAssets = [
  'paper-textures/page-edge-overlay-1.png',
  'paper-textures/page-edge-overlay-2.png',
  'paper-textures/page-edge-overlay-3.png',
  'paper-textures/page-edge-overlay-4.png',
]
export const ReaderPageEdgeOverlayVariantCount = ReaderPageEdgeOverlayAssets.length * 2 * 2
export const ReaderPageStainOverlayAssets = [
  'paper-textures/page-stain-overlay-1.png',
  'paper-textures/page-stain-overlay-2.png',
  'paper-textures/page-stain-overlay-3.png',
  'paper-textures/page-stain-overlay-4.png',
]
export const ReaderPageStainOverlayVariantCount = ReaderPageStainOverlayAssets.length * 2 * 2
export const ReaderPageBorderOverlayAssets = ReaderPageEdgeOverlayAssets
export const ReaderPageBorderOverlayVariantCount = ReaderPageBorderOverlayAssets.length * 2 * 2
export const ReaderThemePalettes = {
  light: {
    background: '#fbfaf8',
    foreground: '#1d1b18',
    accent: '#5b6fed',
  },
  sepia: {
    background: '#f3ead7',
    foreground: '#2b2118',
    accent: '#8a5a2b',
  },
  dusk: {
    background: '#252236',
    foreground: '#ece7f6',
    accent: '#d08cff',
  },
  dark: {
    background: '#111315',
    foreground: '#f2f0ea',
    accent: '#91a7ff',
  },
  black: {
    background: '#000000',
    foreground: '#f3f3f3',
    accent: '#7dd3fc',
  },
}

export const optionalNumber = value =>
  Number.isFinite(value) ? value : undefined

export const readerThemeKey = theme =>
  ReaderThemePalettes[theme] ? theme : ReaderThemeLight

export const readerThemePalette = theme =>
  ReaderThemePalettes[readerThemeKey(theme)]

export const readerFlowMode = settings => {
  if (settings?.flowMode === ReaderFlowPagedVertical) return ReaderFlowPagedVertical
  if (settings?.flowMode === ReaderFlowScrolled) return ReaderFlowScrolled
  if (settings?.flowMode === ReaderFlowScrolledGaps) return ReaderFlowScrolledGaps
  if (settings?.paged === false) return ReaderFlowScrolled
  return ReaderFlowPaged
}

export const readerFoliateFlow = flowMode =>
  flowMode === ReaderFlowScrolled || flowMode === ReaderFlowScrolledGaps
    ? 'scrolled'
    : 'paginated'

const migrateDragAnimationMode = value =>
  value === 'curl' ? ReaderPageTurnCanvas
    : value === 'standard' ? ReaderPageTurnNone
    : null

export const pageTurnAnimationFromSettings = settings => {
  const requested = settings?.["pageTurnAnimation"]
  const supported = ReaderSupportedPageTurnAnimations.includes(requested) ? requested : null
  if (supported) return supported
  const migrated = migrateDragAnimationMode(settings?.["dragAnimationMode"])
  return migrated || ReaderPageTurnDefault
}

export const readerDirectionMode = settings => {
  if (settings?.direction === ReaderDirectionLtr) return ReaderDirectionLtr
  if (settings?.direction === ReaderDirectionRtl) return ReaderDirectionRtl
  return ReaderDirectionDefault
}

export const readerFontSource = settings => {
  if (settings?.fontSource === ReaderFontSourceSystem) return ReaderFontSourceSystem
  if (settings?.fontSource === ReaderFontSourcePublisher) return ReaderFontSourcePublisher
  if (settings?.fontSource === ReaderFontSourceCustom) return ReaderFontSourceCustom
  return ReaderFontSourceNavic
}

export const readerCustomFontFamily = settings => {
  const sanitized = String(settings?.customFontFamily || '')
    .replace(/[^A-Za-z0-9 _-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 80)
  return sanitized || 'Navic Custom Font'
}

export const readerCustomFontUrl = settings => {
  const raw = String(settings?.customFontUrl || '').trim()
  if (!raw) return ''
  try {
    const parsed = new URL(raw, document.baseURI)
    const extensionAllowed = /\.(?:ttf|otf|woff2?|ttc)$/i.test(parsed.pathname)
    if (!extensionAllowed) return ''
    const isAppAsset = parsed.origin === 'https://appassets.androidplatform.net' &&
      parsed.pathname.startsWith('/reader-cache/fonts/')
    const isSameOriginReaderFont = parsed.origin === window.location.origin &&
      parsed.pathname.startsWith('/reader-cache/fonts/')
    return isAppAsset || isSameOriginReaderFont ? parsed.href : ''
  } catch {
    return ''
  }
}

export const readerCssQuotedString = value =>
  `"${String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`

export const readerFontFormat = url => {
  const path = String(url || '').toLowerCase()
  if (path.endsWith('.otf')) return 'opentype'
  if (path.endsWith('.woff')) return 'woff'
  if (path.endsWith('.woff2')) return 'woff2'
  return 'truetype'
}

const ReaderSansFontFamily = 'system-ui, sans-serif'
const ReaderSerifFontFamily = 'Georgia, serif'
const ReaderBookFontFamily = '"Navic Literata", Literata, Bookerly, Georgia, serif'
const ReaderHumanistFontFamily = '"Navic Atkinson Hyperlegible", "Atkinson Hyperlegible", Lexend, system-ui, sans-serif'
const ReaderDyslexicFontFamily = '"Navic OpenDyslexic", OpenDyslexic, "Navic Atkinson Hyperlegible", system-ui, sans-serif'
const ReaderTypewriterFontFamily = '"American Typewriter", "Courier Prime", "Courier New", ui-monospace, monospace'
const ReaderMonoFontFamily = 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'

const readerFontFamilyAliases = new Map([
  ['sans', ReaderSansFontFamily],
  ['serif', ReaderSerifFontFamily],
  ['book', ReaderBookFontFamily],
  ['human', ReaderHumanistFontFamily],
  ['humanist', ReaderHumanistFontFamily],
  ['dys', ReaderDyslexicFontFamily],
  ['dyslexic', ReaderDyslexicFontFamily],
  ['dyx', ReaderTypewriterFontFamily],
  ['typewriter', ReaderTypewriterFontFamily],
  ['mono', ReaderMonoFontFamily],
  ['monospace', ReaderMonoFontFamily],
  ['literata, bookerly, georgia, serif', ReaderBookFontFamily],
  ['atkinson hyperlegible, lexend, system-ui, sans-serif', ReaderHumanistFontFamily],
  ['opendyslexic, atkinson hyperlegible, lexend, system-ui, sans-serif', ReaderDyslexicFontFamily],
])

const readerNormalizedFontFamily = fontFamily => {
  const raw = String(fontFamily || '').trim()
  if (!raw) return ReaderSansFontFamily
  const key = raw.replace(/\s+/g, ' ').replace(/["']/g, '').toLowerCase()
  return readerFontFamilyAliases.get(key) || raw
}

export const readerEffectiveFontFamily = settings => {
  const fontFamily = readerNormalizedFontFamily(settings?.fontFamily || ReaderSansFontFamily)
  if (readerFontSource(settings) === ReaderFontSourcePublisher || fontFamily === 'inherit') return ''
  if (readerFontSource(settings) === ReaderFontSourceCustom) {
    return `${readerCssQuotedString(readerCustomFontFamily(settings))}, system-ui, sans-serif`
  }
  if (readerFontSource(settings) !== ReaderFontSourceSystem) return fontFamily
  if (fontFamily.includes('American Typewriter') || fontFamily.includes('Courier Prime') || fontFamily.includes('Courier New')) {
    return '"American Typewriter", "Courier Prime", "Courier New", ui-monospace, monospace'
  }
  if (fontFamily.includes('ui-monospace') || fontFamily.includes('Consolas')) {
    return 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'
  }
  if (fontFamily.includes('Georgia') || fontFamily.includes('Literata') || fontFamily.includes('Bookerly')) {
    return 'Georgia, serif'
  }
  return 'system-ui, sans-serif'
}
