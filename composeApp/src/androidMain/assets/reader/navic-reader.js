import './vendor/foliate-js/view.js'

const readerRoot = document.body
const overlayClass = 'navic-active-overlay-fragment'
const ReaderDocumentThemeStyleId = 'navic-reader-document-theme'
const ReaderSurfacePaperTextureLayerSelector = '[data-navic-surface-paper-texture-layer="true"]'
const ReaderSurfacePageBorderOverlayLayerSelector = '[data-navic-surface-page-border-overlay-layer="true"]'
const ReaderTapZoneOverlayLayerSelector = '[data-navic-tap-zone-overlay-layer="true"]'
const ReaderThemeLight = 'light'
const ReaderThemeSepia = 'sepia'
const ScrollEdgeTurnSwipeThreshold = 60
const ScrollEdgeTurnSlop = 2
const FixedLayoutSurfaceSwipeThreshold = 56
const CenterTapMovementSlop = 12
const CenterTapSyntheticClickDedupeMs = 650
const ReaderMediaSyntheticClickSuppressMs = 1200
const ReaderTapZoneDefault = 'default'
const ReaderTapZoneEdge = 'edge'
const ReaderTapZoneKindle = 'kindle'
const ReaderTapZoneLShaped = 'l-shaped'
const ReaderTapZoneRightLeft = 'right-left'
const ReaderTapZoneDisabled = 'disabled'
const KomikkuNavigationRegionMenu = 'menu'
const KomikkuNavigationRegionPrevious = 'previous'
const KomikkuNavigationRegionNext = 'next'
const KomikkuNavigationRegionLeft = 'left'
const KomikkuNavigationRegionRight = 'right'
const ReaderFlowPaged = 'paged'
const ReaderFlowPagedVertical = 'paged-vertical'
const ReaderFlowScrolled = 'scrolled'
const ReaderFlowScrolledGaps = 'scrolled-gaps'
const ReaderDirectionDefault = 'default'
const ReaderDirectionLtr = 'ltr'
const ReaderDirectionRtl = 'rtl'
const ReaderFontSourceNavic = 'navic'
const ReaderFontSourceSystem = 'system'
const ReaderFontSourcePublisher = 'publisher'
const ReaderPaperTextureAssets = [
  'paper-textures/paper-texture-1.png',
  'paper-textures/paper-texture-2.png',
  'paper-textures/paper-texture-3.png',
]
const ReaderPaperTextureVariantCount = ReaderPaperTextureAssets.length * 2 * 2
const ReaderPageBorderOverlayAssets = [
  'paper-textures/page-border-overlay-1.png',
  'paper-textures/page-border-overlay-2.png',
  'paper-textures/page-border-overlay-3.png',
  'paper-textures/page-border-overlay-4.png',
]
const ReaderPageBorderOverlayVariantCount = ReaderPageBorderOverlayAssets.length * 2 * 2
const ReaderThemePalettes = {
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

const log = (label, ...details) => console.debug('[NavicReader]', label, ...details)
const logError = (label, ...details) => console.error('[NavicReader]', label, ...details)

const describeUrl = url => {
  try {
    const parsed = new URL(url)
    const fileName = parsed.pathname.split('/').filter(Boolean).pop() || ''
    return `${parsed.protocol}${fileName}`
  } catch {
    return typeof url === 'string' ? url.slice(0, 80) : typeof url
  }
}

const post = message => {
  const json = JSON.stringify(message)
  log('post', message.type, message.code || '')
  if (window.NavicAndroidBridge?.postMessage) {
    window.NavicAndroidBridge.postMessage(json)
  } else {
    log('bridge-unavailable', message)
  }
}

const reportError = (error, code = 'reader_error') => {
  const message = error?.message || String(error)
  logError('reportError', code, message, error?.stack || error)
  readerRoot.replaceChildren(errorElement(message))
  post({ type: 'error', code, message })
}

const errorElement = message => {
  const element = document.createElement('div')
  element.className = 'reader-error'
  element.textContent = message
  return element
}

const optionalNumber = value =>
  Number.isFinite(value) ? value : undefined

const readerThemeKey = theme =>
  ReaderThemePalettes[theme] ? theme : ReaderThemeLight

const readerThemePalette = theme =>
  ReaderThemePalettes[readerThemeKey(theme)]

const readerFlowMode = settings => {
  if (settings?.flowMode === ReaderFlowPagedVertical) return ReaderFlowPagedVertical
  if (settings?.flowMode === ReaderFlowScrolled) return ReaderFlowScrolled
  if (settings?.flowMode === ReaderFlowScrolledGaps) return ReaderFlowScrolledGaps
  if (settings?.paged === false) return ReaderFlowScrolled
  return ReaderFlowPaged
}

const readerFoliateFlow = flowMode =>
  flowMode === ReaderFlowScrolled || flowMode === ReaderFlowScrolledGaps
    ? 'scrolled'
    : 'paginated'

const readerDirectionMode = settings => {
  if (settings?.direction === ReaderDirectionLtr) return ReaderDirectionLtr
  if (settings?.direction === ReaderDirectionRtl) return ReaderDirectionRtl
  return ReaderDirectionDefault
}

const readerFontSource = settings => {
  if (settings?.fontSource === ReaderFontSourceSystem) return ReaderFontSourceSystem
  if (settings?.fontSource === ReaderFontSourcePublisher) return ReaderFontSourcePublisher
  return ReaderFontSourceNavic
}

const readerEffectiveFontFamily = settings => {
  const fontFamily = settings?.fontFamily || 'system-ui, sans-serif'
  if (readerFontSource(settings) === ReaderFontSourcePublisher || fontFamily === 'inherit') return ''
  if (readerFontSource(settings) !== ReaderFontSourceSystem) return fontFamily
  if (fontFamily.includes('ui-monospace') || fontFamily.includes('Consolas')) {
    return 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'
  }
  if (fontFamily.includes('Georgia') || fontFamily.includes('Literata') || fontFamily.includes('Bookerly')) {
    return 'Georgia, serif'
  }
  return 'system-ui, sans-serif'
}

const closestElement = (target, selector) =>
  target?.closest?.(selector) ||
  target?.parentElement?.closest?.(selector) ||
  target?.parentNode?.closest?.(selector) ||
  null

const readerMediaSelector = 'img,picture,svg,video,canvas,object,embed,[role="img"]'

const readerLinkHasMedia = anchor =>
  Boolean(anchor?.querySelector?.(readerMediaSelector))

const isReaderMediaAnchor = anchor =>
  Boolean(anchor && (anchor.dataset?.navicLinkKind === 'media' || readerLinkHasMedia(anchor)))

const isReaderMediaTapTarget = (target, anchor = closestElement(target, 'a[href]')) => {
  if (!anchor || !isReaderMediaAnchor(anchor)) return false
  const media = closestElement(target, readerMediaSelector)
  if (media && anchor.contains?.(media)) return true
  return target === anchor
}

const readerPointInsideRect = (x, y, rect, slop = 3) =>
  Number.isFinite(x) &&
  Number.isFinite(y) &&
  Boolean(rect) &&
  x >= rect.left - slop &&
  x <= rect.right + slop &&
  y >= rect.top - slop &&
  y <= rect.bottom + slop

const readerEventClientPoint = event => {
  const touch = event?.changedTouches?.[0] || event?.touches?.[0]
  const clientX = Number(touch?.clientX ?? event?.clientX)
  const clientY = Number(touch?.clientY ?? event?.clientY)
  return { clientX, clientY }
}

const readerRootTapPoint = (event, doc) => {
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

const readerPointInsideAnchorText = (anchor, event) => {
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

const readerMediaElementFromCandidate = candidate => {
  if (!candidate) return null
  if (candidate.matches?.(readerMediaSelector)) return candidate
  return candidate.querySelector?.(readerMediaSelector) || null
}

const readerImageFromMediaTarget = mediaTarget => {
  if (!mediaTarget) return null
  if (mediaTarget.matches?.('img')) return mediaTarget
  return mediaTarget.querySelector?.('img') || null
}

const readerMediaTapTargetForEvent = (doc, event, anchor) => {
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

const readerRectSnapshot = element => {
  const rect = element?.getBoundingClientRect?.()
  if (!rect) return null
  return {
    left: Number(rect.left),
    top: Number(rect.top),
    right: Number(rect.right),
    bottom: Number(rect.bottom),
  }
}

const markReaderMediaTapHandled = (doc, event, mediaTarget = null) => {
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

const readerLastMediaTapRectContainsPoint = (doc, event) => {
  const rect = doc?.defaultView?.__navicLastMediaTapRect
  if (!rect) return false
  const { clientX, clientY } = readerEventClientPoint(event)
  const x = Number(clientX)
  const y = Number(clientY)
  if (!Number.isFinite(x) || !Number.isFinite(y)) return false
  return readerPointInsideRect(x, y, rect, CenterTapMovementSlop * 2)
}

const readerShouldSuppressMediaSyntheticClick = (doc, event, anchor) => {
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

// Ported from Komikku's ViewerNavigation plus L/Kindlish/Edge/RightAndLeft region classes.
const komikkuNavigationRegion = (left, top, right, bottom, type) => ({
  left,
  top,
  right,
  bottom,
  type,
})

const komikkuConstantMenuRegion = komikkuNavigationRegion(
  0,
  0,
  1,
  0.05,
  KomikkuNavigationRegionMenu
)

const komikkuRegionContains = (region, x, y) =>
  x >= region.left && x <= region.right && y >= region.top && y <= region.bottom

const ReaderCenterMenuRegionSize = 0.42

const readerCenterMenuRegion = komikkuNavigationRegion(
  (1 - ReaderCenterMenuRegionSize) / 2,
  (1 - ReaderCenterMenuRegionSize) / 2,
  (1 + ReaderCenterMenuRegionSize) / 2,
  (1 + ReaderCenterMenuRegionSize) / 2,
  KomikkuNavigationRegionMenu
)

const komikkuRegionSize = smallerTapZone => smallerTapZone ? 0.2 : 0.25

const komikkuDefaultNavigationMode = flowMode =>
  flowMode === ReaderFlowPagedVertical ||
  flowMode === ReaderFlowScrolled ||
  flowMode === ReaderFlowScrolledGaps
    ? ReaderTapZoneLShaped
    : ReaderTapZoneRightLeft

const komikkuNavigationRegions = (
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

const komikkuTapAction = (
  tapZoneMode,
  x,
  y,
  smallerTapZone = false,
  flowMode = ReaderFlowPaged
) => {
  const regions = komikkuNavigationRegions(tapZoneMode, smallerTapZone, flowMode)
  if (komikkuRegionContains(komikkuConstantMenuRegion, x, y)) return KomikkuNavigationRegionMenu
  if (komikkuRegionContains(readerCenterMenuRegion, x, y)) return KomikkuNavigationRegionMenu
  const region = regions.find(candidate => komikkuRegionContains(candidate, x, y))
  if (region) return region.type
  return KomikkuNavigationRegionMenu
}

const readerAssetUrl = path => new URL(path, document.baseURI).href

const isInteractiveReaderTarget = target =>
  Boolean(closestElement(target, `a,button,input,textarea,select,summary,[role="button"],${readerMediaSelector}`))

const stableHash = value => {
  const text = String(value || '')
  let hash = 2166136261
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

const readerPaperTexturePageLocator = detail => {
  const cfi = String(detail?.cfi || '').trim()
  if (cfi) return `cfi:${cfi}`
  const progress = Number(detail?.fraction ?? detail?.progress ?? detail?.totalProgress)
  if (Number.isFinite(progress)) return `progress:${Math.round(Math.min(1, Math.max(0, progress)) * 100000)}`
  const href = String(detail?.href || detail?.tocItem?.href || '').trim()
  return href ? `href:${href}` : 'section'
}

const readerPaperTextureVariantKey = (publicationUrl, section, index, detail = {}) =>
  [
    publicationUrl || 'publication',
    Number.isFinite(index) ? index : 'unknown',
    section?.href || section?.id || section?.label || '',
    readerPaperTexturePageLocator(detail),
  ].join('|')

const readerSurfaceTextureVariantForPage = (key, assets, variantCount) => {
  const variant = stableHash(key) % variantCount
  const textureIndex = variant % assets.length
  const rotate180 = Math.floor(variant / assets.length) % 2 === 1
  const mirrored = Math.floor(variant / (assets.length * 2)) % 2 === 1
  return {
    textureIndex,
    asset: assets[textureIndex],
    rotate180,
    mirrored,
  }
}

const readerPaperTextureVariantForPage = key =>
  readerSurfaceTextureVariantForPage(key, ReaderPaperTextureAssets, ReaderPaperTextureVariantCount)

const readerPageBorderOverlayVariantForPage = key =>
  readerSurfaceTextureVariantForPage(
    `${key}|page-border-overlay`,
    ReaderPageBorderOverlayAssets,
    ReaderPageBorderOverlayVariantCount
  )

const readerPaperTextureTransform = variant => {
  const transforms = []
  if (variant?.mirrored) transforms.push('scaleX(-1)')
  if (variant?.rotate180) transforms.push('rotate(180deg)')
  return transforms.length ? transforms.join(' ') : 'none'
}

const readerPaperTextureBackgroundPosition = scrollOffset => {
  const x = Number(scrollOffset?.x)
  const y = Number(scrollOffset?.y)
  const xPx = Number.isFinite(x) ? Math.round(x) : 0
  const yPx = Number.isFinite(y) ? Math.round(y) : 0
  return `calc(50% + ${xPx}px) calc(50% + ${yPx}px)`
}

const readerSurfacePaperTextureOpacity = settings => {
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return '0'
    case ReaderThemeSepia:
      return '0.14'
    case 'dark':
    case 'dusk':
      return '0.08'
    default:
      return '0.1'
  }
}

const readerSurfacePageBorderOverlayOpacity = settings => {
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return '0'
    case 'dark':
    case 'dusk':
      return '0.55'
    default:
      return '1'
  }
}

const readerFontFaceCss = settings => readerFontSource(settings) === ReaderFontSourceNavic
  ? `
  @font-face {
    font-family: 'Navic Literata';
    src: url('${readerAssetUrl('fonts/navic-literata-regular.ttf')}') format('truetype');
    font-style: normal;
    font-weight: 400;
    font-display: swap;
  }
  @font-face {
    font-family: 'Navic Atkinson Hyperlegible';
    src: url('${readerAssetUrl('fonts/navic-atkinson-hyperlegible-regular.otf')}') format('opentype');
    font-style: normal;
    font-weight: 400;
    font-display: swap;
  }
  @font-face {
    font-family: 'Navic OpenDyslexic';
    src: url('${readerAssetUrl('fonts/navic-opendyslexic-regular.otf')}') format('opentype');
    font-style: normal;
    font-weight: 400;
    font-display: swap;
  }
`
  : ''

const readerParagraphSpacingEm = settings => {
  const percent = Number(settings.paragraphSpacingPercent)
  const normalized = Number.isFinite(percent)
    ? Math.min(200, Math.max(0, percent))
    : 100
  return `${normalized / 100}em`
}

const applyReaderParagraphSpacing = (doc, settings) => {
  const spacing = readerParagraphSpacingEm(settings)
  classifyReaderParagraphBlocks(doc)
  const blocks = Array.from(doc?.querySelectorAll?.('p,[data-navic-paragraph-block="true"]') || [])
  for (const element of blocks) {
    setStylesImportant(element, {
      'display': 'block',
      'margin-block-start': '0',
      'margin-block-end': spacing,
      'margin-top': '0',
      'margin-bottom': spacing,
      'padding-block-end': '0',
      'padding-bottom': '0',
    })
  }
  const adjacentBlocks = Array.from(doc?.querySelectorAll?.(`
    p + p,
    [data-navic-paragraph-block="true"] + [data-navic-paragraph-block="true"],
    p + [data-navic-paragraph-block="true"],
    [data-navic-paragraph-block="true"] + p
  `) || [])
  for (const element of adjacentBlocks) {
    setStylesImportant(element, {
      'margin-block-start': '0',
      'margin-top': '0',
    })
  }
}

const ensureReaderSurfaceTextureLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderSurfacePaperTextureLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicSurfacePaperTextureLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

const ensureReaderSurfaceBorderOverlayLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderSurfacePageBorderOverlayLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicSurfacePageBorderOverlayLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

const updateReaderSurfaceTextureLayer = (layer, textureVariant, settings, scrollOffset = null) => {
  if (!layer || !textureVariant?.asset) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const textureUrl = `url("${readerAssetUrl(textureVariant.asset)}")`
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': '2147483645',
    'pointer-events': 'none',
    'background-image': textureUrl,
    'background-size': 'cover',
    'background-position': readerPaperTextureBackgroundPosition(scrollOffset),
    'background-repeat': 'no-repeat',
    'background-color': 'transparent',
    opacity: readerSurfacePaperTextureOpacity(settings),
    'mix-blend-mode': 'multiply',
    transform: readerPaperTextureTransform(textureVariant),
    'transform-origin': 'center',
  })
}

const updateReaderSurfaceBorderOverlayLayer = (layer, textureVariant, settings, scrollOffset = null) => {
  if (!layer || !textureVariant?.asset) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const textureUrl = `url("${readerAssetUrl(textureVariant.asset)}")`
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': '2147483646',
    'pointer-events': 'none',
    'background-image': textureUrl,
    'background-size': 'cover',
    'background-position': readerPaperTextureBackgroundPosition(scrollOffset),
    'background-repeat': 'no-repeat',
    'background-color': 'transparent',
    opacity: readerSurfacePageBorderOverlayOpacity(settings),
    'mix-blend-mode': 'multiply',
    transform: readerPaperTextureTransform(textureVariant),
    'transform-origin': 'center',
  })
}

const readerTapZoneOverlayLabel = type => {
  switch (type) {
    case KomikkuNavigationRegionPrevious:
      return 'Previous'
    case KomikkuNavigationRegionNext:
      return 'Next'
    case KomikkuNavigationRegionLeft:
      return 'Left'
    case KomikkuNavigationRegionRight:
      return 'Right'
    default:
      return 'Menu'
  }
}

const readerTapZoneOverlayColor = type => {
  switch (type) {
    case KomikkuNavigationRegionPrevious:
    case KomikkuNavigationRegionLeft:
      return 'rgba(255, 128, 128, 0.24)'
    case KomikkuNavigationRegionNext:
    case KomikkuNavigationRegionRight:
      return 'rgba(96, 165, 250, 0.24)'
    default:
      return 'rgba(250, 204, 21, 0.24)'
  }
}

const readerTapZoneOverlayRegions = (tapZoneMode, smallerTapZone, flowMode) => [
  ...komikkuNavigationRegions(tapZoneMode, smallerTapZone, flowMode),
  komikkuConstantMenuRegion,
  readerCenterMenuRegion,
]

const ensureTapZoneOverlayLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderTapZoneOverlayLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicTapZoneOverlayLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

const updateTapZoneOverlayLayer = (
  layer,
  settings = {},
  tapZoneMode,
  smallerTapZone,
  flowMode,
  surfaceRect
) => {
  if (!layer) return
  const showTapZones = settings.showTapZones === true
  if (!showTapZones) {
    layer.remove()
    return
  }
  const rect = surfaceRect || {
    left: 0,
    top: 0,
    width: window.innerWidth || 1,
    height: window.innerHeight || 1,
  }
  setStylesImportant(layer, {
    position: 'fixed',
    left: `${Math.round(rect.left)}px`,
    top: `${Math.round(rect.top)}px`,
    width: `${Math.max(1, Math.round(rect.width))}px`,
    height: `${Math.max(1, Math.round(rect.height))}px`,
    'z-index': '2147483647',
    'pointer-events': 'none',
    overflow: 'hidden',
    contain: 'layout paint',
  })
  const children = readerTapZoneOverlayRegions(tapZoneMode, smallerTapZone, flowMode).map(region => {
    const element = document.createElement('div')
    element.textContent = readerTapZoneOverlayLabel(region.type)
    setStylesImportant(element, {
      position: 'absolute',
      left: `${Math.max(0, region.left * 100)}%`,
      top: `${Math.max(0, region.top * 100)}%`,
      width: `${Math.max(0, (region.right - region.left) * 100)}%`,
      height: `${Math.max(0, (region.bottom - region.top) * 100)}%`,
      display: 'grid',
      'place-items': 'center',
      'box-sizing': 'border-box',
      border: '1px solid rgba(255, 255, 255, 0.72)',
      background: readerTapZoneOverlayColor(region.type),
      color: '#ffffff',
      'font-family': 'system-ui, sans-serif',
      'font-size': '12px',
      'font-weight': '700',
      'text-shadow': '0 1px 2px rgba(0, 0, 0, 0.8)',
      'text-transform': 'uppercase',
      'letter-spacing': '0',
      'pointer-events': 'none',
    })
    return element
  })
  layer.replaceChildren(...children)
}

const isParagraphCandidate = element =>
  Boolean(element?.matches?.('p,[role="doc-p"],div'))

const isReaderParagraphBlock = element => {
  if (!isParagraphCandidate(element)) return false
  if (element.matches?.('figure,figcaption,blockquote,pre,code,nav,ol,ul,li,table,thead,tbody,tfoot,tr,td,th,h1,h2,h3,h4,h5,h6')) {
    return false
  }
  if (element.querySelector?.(readerMediaSelector)) return false
  const text = element.textContent?.replace(/\s+/g, ' ').trim() || ''
  if (text.length < 2) return false
  for (const child of element.children || []) {
    if (isParagraphCandidate(child) && (child.textContent?.trim()?.length || 0) > 0) return false
  }
  return true
}

const classifyReaderParagraphBlocks = doc => {
  if (!doc?.querySelectorAll) return
  for (const element of doc.querySelectorAll('p,[role="doc-p"],div')) {
    if (isReaderParagraphBlock(element)) {
      element.dataset.navicParagraphBlock = 'true'
    } else if (element.dataset?.navicParagraphBlock === 'true') {
      delete element.dataset.navicParagraphBlock
    }
  }
}

const setStylesImportant = (element, styles) => {
  if (!element) return
  for (const [property, value] of Object.entries(styles)) {
    element.style.setProperty(property, value, 'important')
  }
}

const readerViewportSize = () => {
  const viewport = window.visualViewport
  const width = Math.max(
    1,
    Math.round(viewport?.width || window.innerWidth || document.documentElement.clientWidth || 0)
  )
  const height = Math.max(
    1,
    Math.round(viewport?.height || window.innerHeight || document.documentElement.clientHeight || 0)
  )
  return { width, height }
}

const readerStartLocatorHasPosition = startLocator =>
  Boolean(
    startLocator?.cfi ||
    startLocator?.href ||
    Number.isFinite(Number(startLocator?.progress))
  )

class NavicReaderRuntime {
  view = null
  mediaOverlayEnabled = false
  readerSettings = {}
  readerTapZoneMode = ReaderTapZoneDefault
  readerFlowModeValue = ReaderFlowPaged
  readerDirectionModeValue = ReaderDirectionDefault
  smallerTapZone = false
  originalBookDir = null
  publicationUrl = ''
  surfaceTextureLayer = null
  surfaceBorderOverlayLayer = null
  surfaceTextureVariant = null
  surfaceBorderOverlayVariant = null
  surfacePaperTextureBaseOffset = 0
  surfaceTextureScrollOffset = { x: 0, y: 0 }
  surfacePaperTextureScrollRenderer = null
  surfacePaperTextureScrollListener = null
  tapZoneOverlayLayer = null
  lastRelocateDetail = null
  viewportResizeListener = () => this.applyReaderViewportLayout('resize')

  constructor() {
    window.visualViewport?.addEventListener('resize', this.viewportResizeListener)
    window.addEventListener('resize', this.viewportResizeListener)
    requestAnimationFrame(() => this.applyReaderViewportLayout('startup'))
  }

  dispatch(command) {
    log('dispatch', command?.type || 'invalid')
    if (!command || typeof command !== 'object') return
    switch (command.type) {
      case 'openPublication':
        return this.openPublication(command)
      case 'goToCfi':
        return this.goTo(command.cfi)
      case 'goToHref':
        return this.goTo(command.href)
      case 'goToProgress':
        return this.goToProgress(command.progress)
      case 'nextPage':
        return this.nextPage()
      case 'previousPage':
        return this.previousPage()
      case 'applyHighlight':
        return this.applyHighlight(command)
      case 'applyHighlights':
        return this.applyHighlights(command.highlights || [])
      case 'applyOverlayFragment':
        return this.applyOverlayFragment(command.fragment || command)
      case 'clearOverlay':
        return this.clearOverlay()
      case 'applySettings':
        return this.applySettings(command.settings || {})
      case 'search':
        return this.search(command.query)
      default:
        post({ type: 'error', code: 'unknown_command', message: `Unknown reader command: ${command.type}` })
    }
  }

  async openPublication({ url, mediaOverlayEnabled = false, startLocator = null, settings = null }) {
    if (!url) {
      logError('openPublication:missing-url')
      post({ type: 'error', code: 'missing_url', message: 'Reader publication URL is required.' })
      return
    }
    this.mediaOverlayEnabled = Boolean(mediaOverlayEnabled)
    log('openPublication:start', describeUrl(url), `overlay=${this.mediaOverlayEnabled}`)
    try {
      this.close()
      this.publicationUrl = url
      this.lastRelocateDetail = null
      if (settings) this.readerSettings = settings
      this.applyReaderViewportLayout('before-open')
      this.view = document.createElement('foliate-view')
      this.view.addEventListener('relocate', event => this.onRelocate(event.detail || {}))
      this.view.addEventListener('load', event => this.onLoad(event.detail || {}))
      this.view.addEventListener('external-link', event => event.preventDefault())
      readerRoot.replaceChildren(this.view)
      this.applyReaderViewportLayout('view-created')
      await this.view.open(url)
      this.attachSurfacePaperTextureScrollSync()
      this.applyReaderViewportLayout('view-opened')
      log('openPublication:view-opened', describeUrl(url))
      if (settings) this.applySettings(settings)
      this.postToc()
      const locator = startLocator?.cfi || startLocator?.href
      const progress = Number(startLocator?.progress)
      if (locator) {
        await this.view.goTo(locator)
      } else if (Number.isFinite(progress)) {
        await this.goToProgress(progress)
      } else {
        await this.view.init?.({ showTextStart: true })
      }
      this.attachSurfaceTapGesture(this.view)
      this.updateSurfacePaperTexture(this.lastRelocateDetail || {})
      log('openPublication:ready', describeUrl(url))
      this.applyReaderViewportLayout('ready')
      this.logContentLayout('ready')
      post({ type: 'ready' })
      post({ type: 'publicationReady' })
      if (readerStartLocatorHasPosition(startLocator)) {
        requestAnimationFrame(() => this.postCurrentLocationSnapshot('initial-resume'))
      }
    } catch (error) {
      reportError(error, 'open_failed')
    }
  }

  close() {
    this.clearOverlay()
    this.detachSurfacePaperTextureScrollSync()
    this.view?.close?.()
    this.view?.remove?.()
    this.view = null
    this.readerSettings = {}
    this.originalBookDir = null
    this.publicationUrl = ''
    this.readerDirectionModeValue = ReaderDirectionDefault
    this.surfaceTextureLayer?.remove?.()
    this.surfaceTextureLayer = null
    this.surfaceBorderOverlayLayer?.remove?.()
    this.surfaceBorderOverlayLayer = null
    this.tapZoneOverlayLayer?.remove?.()
    this.tapZoneOverlayLayer = null
    this.surfaceTextureVariant = null
    this.surfaceBorderOverlayVariant = null
    this.surfacePaperTextureBaseOffset = 0
    this.surfaceTextureScrollOffset = { x: 0, y: 0 }
    this.lastRelocateDetail = null
  }

  applyReaderViewportLayout(label = 'unknown') {
    const { width, height } = readerViewportSize()
    const widthPx = `${width}px`
    const heightPx = `${height}px`
    setStylesImportant(document.documentElement, {
      width: '100%',
      height: heightPx,
      'min-height': heightPx,
      margin: '0px',
      overflow: 'hidden',
    })
    setStylesImportant(document.body, {
      position: 'fixed',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      margin: '0px',
      overflow: 'hidden',
    })
    setStylesImportant(this.view, {
      position: 'fixed',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      overflow: 'hidden',
    })
    const renderer = this.view?.renderer
    const fixedLayout = this.view?.isFixedLayout === true || renderer?.localName === 'foliate-fxl'
    setStylesImportant(renderer, {
      position: 'absolute',
      inset: '0px',
      display: 'block',
      width: widthPx,
      'min-width': widthPx,
      height: heightPx,
      'min-height': heightPx,
      overflow: fixedLayout ? 'auto' : 'hidden',
    })
    if (renderer) requestAnimationFrame(() => renderer?.render?.())
    this.renderSurfacePaperTextureLayers()
    this.updateTapZoneOverlay()
    log('viewport-layout', `label=${label}`, `${width}x${height}`)
  }

  async goTo(locator) {
    if (!this.view || !locator) return
    try {
      await this.view.goTo(locator)
      this.logContentLayout('go-to')
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }

  progressTargetForSections(fraction) {
    const sectionCount = Number(this.view?.book?.sections?.length)
    if (!Number.isFinite(sectionCount) || sectionCount <= 0) return null
    const index = Math.floor(Math.min(1, Math.max(0, fraction)) * sectionCount)
    return Math.min(sectionCount - 1, Math.max(0, index))
  }

  fixedLayoutCurrentPageIndex() {
    if (this.view?.isFixedLayout !== true) return null
    try {
      const index = Number(this.view?.renderer?.index)
      return Number.isFinite(index) ? Math.floor(index) : null
    } catch (error) {
      log('fixed-layout-index:unavailable', error?.message || String(error))
      return null
    }
  }

  fixedLayoutAdjacentPageTarget(direction) {
    if (this.view?.isFixedLayout !== true) return null
    const current = this.fixedLayoutCurrentPageIndex()
    const sectionCount = Number(this.view?.book?.sections?.length)
    if (!Number.isFinite(current) || !Number.isFinite(sectionCount) || sectionCount <= 0) return null
    const forward = direction === 'next'
    const rtl = this.effectiveReaderDirection() === ReaderDirectionRtl
    const delta = forward === rtl ? -1 : 1
    const target = current + delta
    return target >= 0 && target < sectionCount ? target : null
  }

  async goToProgress(progress) {
    if (!this.view) return
    const numericProgress = Number(progress)
    const fraction = Number.isFinite(numericProgress)
      ? Math.min(1, Math.max(0, numericProgress))
      : 0
    try {
      log('progress-seek:start', fraction)
      const canUseFraction = typeof this.view?.goToFraction === 'function' &&
        this.view?.book?.splitTOCHref &&
        this.view?.book?.getTOCFragment
      const progressTarget = this.progressTargetForSections(fraction)
      if (canUseFraction) {
        await this.view.goToFraction(fraction)
      } else if (progressTarget != null) {
        log('progress-seek:fallback-section', progressTarget)
        await this.view.goTo(progressTarget)
      } else {
        await this.view.goTo({ fraction })
      }
      this.applyReaderViewportLayout('progress-seek')
      requestAnimationFrame(() => {
        this.logContentLayout('progress-seek')
        log('progress-seek:done', fraction)
      })
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }

  async nextPage() {
    return this.turnPage('next')
  }

  async previousPage() {
    return this.turnPage('previous')
  }

  async turnPage(direction) {
    if (!this.view) return
    try {
      log('page-turn:start', direction)
      const beforePageIndex = this.fixedLayoutCurrentPageIndex()
      const fallbackPageTarget = this.fixedLayoutAdjacentPageTarget(direction)
      if (direction === 'next') {
        await this.view?.next?.()
      } else {
        await this.view?.prev?.()
      }
      const afterPageIndex = this.fixedLayoutCurrentPageIndex()
      if (fallbackPageTarget != null && beforePageIndex === afterPageIndex) {
        log('page-turn:fixed-fallback', direction, fallbackPageTarget)
        await this.view.goTo(fallbackPageTarget)
      }
      this.applyReaderViewportLayout(`page-turn:${direction}`)
      requestAnimationFrame(() => {
        this.logContentLayout(`page-turn:${direction}`)
        log('page-turn:done', direction)
      })
    } catch (error) {
      reportError(error, 'navigation_failed')
    }
  }

  attachScrolledEdgeTurnGestures(doc) {
    if (!doc?.defaultView || doc.defaultView.__navicScrolledEdgeTurnGesturesAttached) return
    doc.defaultView.__navicScrolledEdgeTurnGesturesAttached = true
    let touchState = null
    doc.addEventListener('touchstart', event => {
      const touch = event.changedTouches?.[0]
      if (!touch || event.touches?.length > 1) {
        touchState = null
        return
      }
      touchState = {
        x: touch.screenX ?? touch.clientX ?? 0,
        y: touch.screenY ?? touch.clientY ?? 0,
      }
    }, { passive: true })
    doc.addEventListener('touchmove', event => {
      if (!touchState || event.touches?.length > 1) {
        touchState = null
        return
      }
      const touch = event.changedTouches?.[0]
      if (!touch) return
      touchState.lastX = touch.screenX ?? touch.clientX ?? touchState.x
      touchState.lastY = touch.screenY ?? touch.clientY ?? touchState.y
    }, { passive: true })
    doc.addEventListener('touchend', event => {
      const state = touchState
      touchState = null
      if (!state) return
      const touch = event.changedTouches?.[0]
      if (!touch) return
      const endX = touch.screenX ?? touch.clientX ?? state.lastX ?? state.x
      const endY = touch.screenY ?? touch.clientY ?? state.lastY ?? state.y
      const deltaX = endX - state.x
      const deltaY = endY - state.y
      const selection = doc.getSelection?.()
      if (selection && selection.rangeCount > 0 && !selection.isCollapsed) return
      if (Math.abs(deltaY) < ScrollEdgeTurnSwipeThreshold || Math.abs(deltaY) <= Math.abs(deltaX)) return
      this.turnScrolledEdgePage(deltaY)
    }, { passive: true })
    doc.addEventListener('touchcancel', () => {
      touchState = null
    }, { passive: true })
  }

  async handleReaderTapZone(event, doc, source = 'tap') {
    if (event.defaultPrevented || event.button > 0) return false
    if (isInteractiveReaderTarget(event.target)) return false
    const selection = doc?.getSelection?.()
    if (selection && selection.rangeCount > 0 && !selection.isCollapsed) return false
    const tapZone = this.readerTapZone(event, doc)
    if (!tapZone) return false
    event.preventDefault?.()
    event.stopPropagation?.()
    log(`${source}-tap`, tapZone)
    if (tapZone === 'previous') {
      await this.previousPage()
      return true
    }
    if (tapZone === 'next') {
      await this.nextPage()
      return true
    }
    if (tapZone === 'left') {
      if (this.effectiveReaderDirection() === ReaderDirectionRtl) await this.nextPage()
      else await this.previousPage()
      return true
    }
    if (tapZone === 'right') {
      if (this.effectiveReaderDirection() === ReaderDirectionRtl) await this.previousPage()
      else await this.nextPage()
      return true
    }
    post({ type: 'readerCenterTap' })
    return true
  }

  attachCenterTapGesture(doc) {
    const win = doc?.defaultView
    if (!win || win.__navicCenterTapGestureAttached) return
    win.__navicCenterTapGestureAttached = true
    let touchState = null
    doc.addEventListener('touchstart', event => {
      const touch = event.changedTouches?.[0]
      if (!touch || event.touches?.length > 1) {
        touchState = null
        return
      }
      touchState = {
        x: touch.screenX ?? touch.clientX ?? 0,
        y: touch.screenY ?? touch.clientY ?? 0,
        clientX: touch.clientX,
        clientY: touch.clientY,
        time: event.timeStamp || performance.now(),
        target: event.target,
      }
    }, { passive: true })
    doc.addEventListener('touchend', async event => {
      const state = touchState
      touchState = null
      if (!state) return
      const touch = event.changedTouches?.[0]
      if (!touch || event.touches?.length > 0) return
      const endX = touch.screenX ?? touch.clientX ?? state.x
      const endY = touch.screenY ?? touch.clientY ?? state.y
      if (Math.abs(endX - state.x) > CenterTapMovementSlop) return
      if (Math.abs(endY - state.y) > CenterTapMovementSlop) return
      const handled = await this.handleReaderTapZone({
        defaultPrevented: event.defaultPrevented,
        button: 0,
        target: state.target || event.target,
        clientX: touch.clientX ?? state.clientX,
        clientY: touch.clientY ?? state.clientY,
        preventDefault: () => event.preventDefault(),
        stopPropagation: () => event.stopPropagation(),
      }, doc, 'touch')
      if (handled) win.__navicLastTapHandledAt = event.timeStamp || performance.now()
    }, { passive: false })
    doc.addEventListener('touchcancel', () => {
      touchState = null
    }, { passive: true })
    doc.addEventListener('click', async event => {
      const timestamp = event.timeStamp || performance.now()
      const lastTap = Number(win.__navicLastTapHandledAt || 0)
      if (lastTap && Math.abs(timestamp - lastTap) < CenterTapSyntheticClickDedupeMs) return
      await this.handleReaderTapZone(event, doc, 'center')
    }, { passive: false })
  }

  attachSurfaceTapGesture(element) {
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
        const handled = await this.turnFixedLayoutSwipePage(deltaX)
        if (handled) {
          event.preventDefault()
          event.stopPropagation()
          element.__navicLastSurfaceTapHandledAt = event.timeStamp || performance.now()
        }
        return
      }
      if (Math.abs(deltaX) > CenterTapMovementSlop || Math.abs(deltaY) > CenterTapMovementSlop) return
      const handled = await this.handleReaderTapZone({
        defaultPrevented: event.defaultPrevented,
        button: 0,
        target: state.target || event.target,
        clientX: touch.clientX ?? state.clientX,
        clientY: touch.clientY ?? state.clientY,
        preventDefault: () => event.preventDefault(),
        stopPropagation: () => event.stopPropagation(),
      }, document, 'surface-touch')
      if (handled) element.__navicLastSurfaceTapHandledAt = event.timeStamp || performance.now()
    }, { passive: false })
    element.addEventListener('touchcancel', () => {
      touchState = null
    }, { passive: true })
    element.addEventListener('click', async event => {
      if (event.defaultPrevented || event.button !== 0) return
      const timestamp = event.timeStamp || performance.now()
      const lastTap = Number(element.__navicLastSurfaceTapHandledAt || 0)
      if (lastTap && Math.abs(timestamp - lastTap) < CenterTapSyntheticClickDedupeMs) {
        event.preventDefault()
        event.stopPropagation()
        return
      }
      await this.handleReaderTapZone(event, document, 'surface')
    }, { passive: false })
  }

  attachLinkNavigation(doc, index) {
    if (!doc?.defaultView || doc.defaultView.__navicLinkNavigationAttached) return
    doc.defaultView.__navicLinkNavigationAttached = true
    doc.addEventListener('click', async event => {
      if (event.defaultPrevented || event.button > 0) return
      const anchor = closestElement(event.target, 'a[href]')
      if (!anchor) return
      if (readerShouldSuppressMediaSyntheticClick(doc, event, anchor)) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
        log('link:media-synthetic-click-suppressed', describeUrl(anchor.getAttribute('href') || ''))
        return
      }
      const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
      if (mediaTapTarget) {
        const toggled = this.toggleSepiaImageOverlayFromEvent(doc, event)
        if (!toggled) {
          event.preventDefault()
          event.stopPropagation()
          event.stopImmediatePropagation()
        }
        log('link:media-tap', mediaTapTarget.tagName || 'media', describeUrl(anchor.getAttribute('href') || ''))
        return
      }
      if (!readerPointInsideAnchorText(anchor, event)) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
        log('link:text-hit-miss', describeUrl(anchor.getAttribute('href') || ''))
        return
      }
      const rawHref = anchor.getAttribute('href')
      if (!rawHref) return
      const section = this.view?.book?.sections?.[index]
      const href = section?.resolveHref?.(rawHref) ?? rawHref
      event.preventDefault()
      event.stopPropagation()
      try {
        if (this.view?.book?.isExternal?.(href)) {
          log('link:external', describeUrl(href))
          globalThis.open?.(href, '_blank')
          return
        }
        log('link:navigate', href)
        await this.goTo(href)
      } catch (error) {
        reportError(error, 'link_navigation_failed')
      }
    }, { capture: true })
  }

  classifyReaderLinks(doc) {
    if (!doc?.querySelectorAll) return
    for (const anchor of doc.querySelectorAll('a[href]')) {
      anchor.dataset.navicLinkKind = readerLinkHasMedia(anchor) ? 'media' : 'text'
    }
  }

  toggleSepiaImageOverlayFromEvent(doc, event, mediaTapTarget = null) {
    if (event.defaultPrevented || event.button > 0) return false
    if (readerThemeKey(this.readerSettings?.theme) !== ReaderThemeSepia) return false
    if (!mediaTapTarget) {
      const anchor = closestElement(event.target, 'a[href]')
      mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)
    }
    const image = readerImageFromMediaTarget(mediaTapTarget)
    if (!image) return false
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
    return true
  }

  attachSepiaImageOverlayToggle(doc) {
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
      this.toggleSepiaImageOverlayFromEvent(doc, {
        defaultPrevented: event.defaultPrevented,
        button: 0,
        target: state.target || event.target,
        clientX: touch.clientX ?? state.clientX,
        clientY: touch.clientY ?? state.clientY,
        preventDefault: () => event.preventDefault(),
        stopPropagation: () => event.stopPropagation(),
        stopImmediatePropagation: () => event.stopImmediatePropagation(),
        timeStamp: event.timeStamp,
      }, state.mediaTapTarget)
    }, { capture: true, passive: false })
    doc.addEventListener('touchcancel', () => {
      touchState = null
    }, { capture: true, passive: true })
    doc.addEventListener('click', event => {
      const lastMediaTap = Number(doc.defaultView?.__navicLastMediaTapHandledAt || 0)
      const timestamp = event.timeStamp || performance.now()
      if (lastMediaTap && Math.abs(timestamp - lastMediaTap) < CenterTapSyntheticClickDedupeMs) {
        event.preventDefault()
        event.stopPropagation()
        event.stopImmediatePropagation()
        return
      }
      this.toggleSepiaImageOverlayFromEvent(doc, event)
    }, { capture: true, passive: false })
  }

  readerTapSurfaceRect() {
    const rect = this.view?.getBoundingClientRect?.() || readerRoot?.getBoundingClientRect?.()
    const fallbackWidth = window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 1
    const fallbackHeight = window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 1
    const left = Number.isFinite(rect?.left) ? rect.left : 0
    const top = Number.isFinite(rect?.top) ? rect.top : 0
    const width = Math.max(1, Number.isFinite(rect?.width) ? rect.width : fallbackWidth)
    const height = Math.max(1, Number.isFinite(rect?.height) ? rect.height : fallbackHeight)
    return { left, top, width, height }
  }

  readerTapZone(event, doc) {
    const point = readerRootTapPoint(event, doc)
    const surfaceRect = this.readerTapSurfaceRect()
    if (!point) return null
    const xFraction = (point.x - surfaceRect.left) / surfaceRect.width
    const yFraction = (point.y - surfaceRect.top) / surfaceRect.height
    const tapZone = komikkuTapAction(
      this.readerTapZoneMode,
      xFraction,
      yFraction,
      this.smallerTapZone,
      this.readerFlowModeValue
    )
    log(
      'tap-zone',
      `source=${point.source}`,
      `doc=${doc === document ? 'surface' : 'content'}`,
      `x=${xFraction.toFixed(3)}`,
      `y=${yFraction.toFixed(3)}`,
      `zone=${tapZone}`,
      `mode=${this.readerTapZoneMode || ReaderTapZoneDefault}`,
      `flow=${this.readerFlowModeValue || ReaderFlowPaged}`
    )
    return tapZone
  }

  effectiveReaderDirection() {
    if (this.readerDirectionModeValue === ReaderDirectionLtr || this.readerDirectionModeValue === ReaderDirectionRtl) {
      return this.readerDirectionModeValue
    }
    return this.view?.book?.dir === ReaderDirectionRtl ? ReaderDirectionRtl : ReaderDirectionLtr
  }

  turnScrolledEdgePage(deltaY) {
    const renderer = this.view?.renderer
    if (!renderer || !renderer.scrolled) return false
    const atStart = renderer.start <= ScrollEdgeTurnSlop
    const atEnd = renderer.viewSize - renderer.end <= ScrollEdgeTurnSlop
    if (deltaY > ScrollEdgeTurnSwipeThreshold && atStart) {
      log('page-turn:edge-swipe', 'previous', `start=${renderer.start}`)
      void this.previousPage()
      return true
    }
    if (deltaY < -ScrollEdgeTurnSwipeThreshold && atEnd) {
      log('page-turn:edge-swipe', 'next', `remaining=${renderer.viewSize - renderer.end}`)
      void this.nextPage()
      return true
    }
    return false
  }

  async turnFixedLayoutSwipePage(deltaX) {
    if (this.view?.isFixedLayout !== true) return false
    if (Math.abs(deltaX) < FixedLayoutSurfaceSwipeThreshold) return false
    const swipedLeft = deltaX < 0
    const rtl = this.effectiveReaderDirection() === ReaderDirectionRtl
    log('page-turn:fixed-swipe', swipedLeft ? 'left' : 'right')
    if (swipedLeft === rtl) {
      await this.previousPage()
    } else {
      await this.nextPage()
    }
    return true
  }

  async applyHighlight({ id, cfi, color = null, note = null }) {
    if (!this.view || !id || !cfi) return
    try {
      await this.view.addAnnotation?.({ id, value: cfi, color, note }, false)
    } catch (error) {
      reportError(error, 'highlight_failed')
    }
  }

  async applyHighlights(highlights) {
    for (const highlight of highlights || []) {
      await this.applyHighlight(highlight)
    }
  }

  async applyOverlayFragment(fragment) {
    if (!this.view || !fragment) return
    const targetHref = fragment.textHref && fragment.fragmentId
      ? `${fragment.textHref}#${fragment.fragmentId}`
      : fragment.textHref
    if (targetHref) {
      await this.goTo(targetHref)
    }
    this.clearOverlay()
    if (fragment.fragmentId) {
      for (const doc of this.contentDocuments()) {
        const element = doc.getElementById(fragment.fragmentId)
        if (element) element.classList.add(overlayClass)
      }
    }
    post({ type: 'overlayFragmentActive', ...fragment })
  }

  clearOverlay() {
    for (const doc of this.contentDocuments()) {
      for (const element of doc.querySelectorAll(`.${overlayClass}`)) {
        element.classList.remove(overlayClass)
      }
    }
  }

  fixedLayoutPagePosition(detail) {
    if (this.view?.isFixedLayout !== true) return null
    const pageCount = Number(this.view?.book?.sections?.length)
    const pageIndex = Number(detail?.index ?? this.fixedLayoutCurrentPageIndex())
    if (!Number.isFinite(pageCount) || pageCount <= 0 || !Number.isFinite(pageIndex)) return null
    return {
      pageIndex: Math.min(pageCount - 1, Math.max(0, Math.floor(pageIndex))),
      pageCount,
    }
  }

  reflowablePagePosition() {
    if (this.view?.isFixedLayout === true) return null
    const renderer = this.view?.renderer
    if (!renderer || renderer.scrolled) return null
    const page = Number(renderer.page)
    const pages = Number(renderer.pages)
    if (!Number.isFinite(page) || !Number.isFinite(pages) || pages <= 2) return null
    const pageCount = Math.max(1, Math.round(pages) - 2)
    return {
      pageIndex: Math.min(pageCount - 1, Math.max(0, Math.floor(page - 1))),
      pageCount,
    }
  }

  readerPagePosition(detail) {
    return this.fixedLayoutPagePosition(detail) || this.reflowablePagePosition()
  }

  applySettings(settings) {
    settings = { ...this.readerSettings, ...settings }
    this.readerSettings = settings
    const rootStyle = document.documentElement.style
    if (typeof settings.tapZone === 'string') this.readerTapZoneMode = settings.tapZone || ReaderTapZoneDefault
    this.smallerTapZone = settings.smallerTapZone === true
    if (settings.fontSizePercent) rootStyle.setProperty('--reader-font-size', `${settings.fontSizePercent}%`)
    if (settings.lineHeight) rootStyle.setProperty('--reader-line-height', String(settings.lineHeight))
    rootStyle.setProperty('--reader-paragraph-spacing', readerParagraphSpacingEm(settings))
    const palette = readerThemePalette(settings.theme)
    rootStyle.setProperty('--reader-background', palette.background)
    rootStyle.setProperty('--reader-foreground', palette.foreground)
    rootStyle.setProperty('--reader-accent', palette.accent)
    rootStyle.setProperty('--theme-bg-color', palette.background)
    const flowMode = readerFlowMode(settings)
    this.readerFlowModeValue = flowMode
    rootStyle.setProperty('--reader-scroll-gap', flowMode === ReaderFlowScrolledGaps ? '1.25rem' : '0rem')
    this.view?.renderer?.setAttribute('flow', readerFoliateFlow(flowMode))
    this.readerDirectionModeValue = readerDirectionMode(settings)
    this.applyReaderDirection(this.readerDirectionModeValue)
    this.applyReaderViewportLayout('settings')
    this.view?.renderer?.setStyles?.(readerContentCss(settings))
    this.applyThemeToLoadedContent(settings)
    this.updateSurfacePaperTexture()
    this.updateTapZoneOverlay()
  }

  updateTapZoneOverlay() {
    if (this.readerSettings?.showTapZones !== true) {
      this.tapZoneOverlayLayer?.remove?.()
      this.tapZoneOverlayLayer = null
      return
    }
    this.tapZoneOverlayLayer = this.tapZoneOverlayLayer && readerRoot.contains(this.tapZoneOverlayLayer)
      ? this.tapZoneOverlayLayer
      : ensureTapZoneOverlayLayer()
    updateTapZoneOverlayLayer(
      this.tapZoneOverlayLayer,
      this.readerSettings,
      this.readerTapZoneMode,
      this.smallerTapZone,
      this.readerFlowModeValue,
      this.readerTapSurfaceRect()
    )
  }

  applyThemeToLoadedContent(settings = this.readerSettings) {
    for (const content of this.contentEntries()) {
      this.applyDocumentTheme(content.doc, settings, content.index)
    }
    this.applyRendererTheme(settings)
  }

  applyRendererTheme(settings = this.readerSettings) {
    const palette = readerThemePalette(settings?.theme)
    setStylesImportant(this.view, {
      background: palette.background,
      'background-color': palette.background,
      color: palette.foreground,
    })
    const renderer = this.view?.renderer
    setStylesImportant(renderer, {
      background: palette.background,
      'background-color': palette.background,
      color: palette.foreground,
    })
    const shadowRoot = renderer?.shadowRoot
    if (!shadowRoot) return
    for (const element of shadowRoot.querySelectorAll('#background, #background > *')) {
      setStylesImportant(element, {
        background: palette.background,
        'background-color': palette.background,
      })
    }
  }

  applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {
    if (!doc?.documentElement) return
    const palette = readerThemePalette(settings?.theme)
    const root = doc.documentElement
    const body = doc.body
    const styleHost = doc.head || root
    applyReaderParagraphSpacing(doc, settings)
    root.dataset.navicReaderTheme = readerThemeKey(settings?.theme)
    delete root.dataset.navicPaperTextureKey
    delete root.dataset.navicPaperTextureAsset
    let themeStyle = doc.getElementById(ReaderDocumentThemeStyleId)
    if (!themeStyle) {
      themeStyle = doc.createElement('style')
      themeStyle.id = ReaderDocumentThemeStyleId
      styleHost.append(themeStyle)
    }
    themeStyle.textContent = readerContentCss(settings)
    for (const element of [root, body].filter(Boolean)) {
      setStylesImportant(element, {
        '--reader-background': palette.background,
        '--reader-foreground': palette.foreground,
        '--reader-accent': palette.accent,
        '--theme-bg-color': palette.background,
        '--reader-paragraph-spacing': readerParagraphSpacingEm(settings),
        background: palette.background,
        'background-color': palette.background,
        'background-image': 'none',
        color: palette.foreground,
      })
    }
    for (const element of doc.querySelectorAll('[style*="background"], [bgcolor]')) {
      if (!element || element === root || element === body || isThemeBackgroundMediaElement(element)) continue
      if (element.hasAttribute('bgcolor')) {
        if (element.dataset.navicOriginalBgcolor === undefined) {
          element.dataset.navicOriginalBgcolor = element.getAttribute('bgcolor') || ''
        }
        element.removeAttribute('bgcolor')
      }
      setStylesImportant(element, {
        background: 'transparent',
        'background-color': 'transparent',
        'background-image': 'none',
      })
    }
    for (const element of doc.querySelectorAll('canvas, svg')) {
      setStylesImportant(element, {
        'background-color': 'transparent',
      })
    }
  }

  currentRendererContainerPosition() {
    const renderer = this.view?.renderer
    const position = Number(renderer?.containerPosition)
    return Number.isFinite(position) ? position : 0
  }

  surfacePaperTextureScrollOffset() {
    const renderer = this.view?.renderer
    if (!renderer) {
      this.surfaceTextureScrollOffset = { x: 0, y: 0 }
      return this.surfaceTextureScrollOffset
    }
    const position = Number(renderer.containerPosition)
    if (renderer.scrolled || !Number.isFinite(position)) {
      this.surfaceTextureScrollOffset = { x: 0, y: 0 }
      return this.surfaceTextureScrollOffset
    }
    const delta = position - this.surfacePaperTextureBaseOffset
    const { width, height } = readerViewportSize()
    const maxOffset = this.readerFlowModeValue === ReaderFlowPagedVertical ? height : width
    const bounded = Math.max(-maxOffset, Math.min(maxOffset, delta))
    this.surfaceTextureScrollOffset = this.readerFlowModeValue === ReaderFlowPagedVertical
      ? { x: 0, y: -bounded }
      : { x: -bounded, y: 0 }
    return this.surfaceTextureScrollOffset
  }

  attachSurfacePaperTextureScrollSync() {
    const renderer = this.view?.renderer
    if (!renderer || renderer === this.surfacePaperTextureScrollRenderer) return
    this.detachSurfacePaperTextureScrollSync()
    this.surfacePaperTextureScrollRenderer = renderer
    this.surfacePaperTextureScrollListener = () => this.syncSurfacePaperTextureScrollOffset('scroll')
    renderer.addEventListener('scroll', this.surfacePaperTextureScrollListener, { passive: true })
  }

  detachSurfacePaperTextureScrollSync() {
    if (this.surfacePaperTextureScrollRenderer && this.surfacePaperTextureScrollListener) {
      this.surfacePaperTextureScrollRenderer.removeEventListener('scroll', this.surfacePaperTextureScrollListener)
    }
    this.surfacePaperTextureScrollRenderer = null
    this.surfacePaperTextureScrollListener = null
  }

  syncSurfacePaperTextureScrollOffset(reason = 'scroll') {
    if (!this.surfaceTextureVariant && !this.surfaceBorderOverlayVariant) return
    this.renderSurfacePaperTextureLayers()
    const offset = this.surfaceTextureScrollOffset
    if (Math.abs(offset.x || 0) > 1 || Math.abs(offset.y || 0) > 1) {
      log('surface-texture-scroll', reason, `x=${Math.round(offset.x || 0)}`, `y=${Math.round(offset.y || 0)}`)
    }
  }

  renderSurfacePaperTextureLayers() {
    if (!this.surfaceTextureVariant && !this.surfaceBorderOverlayVariant) return
    const scrollOffset = this.surfacePaperTextureScrollOffset()
    if (this.surfaceTextureVariant) {
      this.surfaceTextureLayer = this.surfaceTextureLayer && readerRoot.contains(this.surfaceTextureLayer)
        ? this.surfaceTextureLayer
        : ensureReaderSurfaceTextureLayer()
      updateReaderSurfaceTextureLayer(
        this.surfaceTextureLayer,
        this.surfaceTextureVariant,
        this.readerSettings,
        scrollOffset
      )
    }
    if (this.surfaceBorderOverlayVariant) {
      this.surfaceBorderOverlayLayer = this.surfaceBorderOverlayLayer && readerRoot.contains(this.surfaceBorderOverlayLayer)
        ? this.surfaceBorderOverlayLayer
        : ensureReaderSurfaceBorderOverlayLayer()
      updateReaderSurfaceBorderOverlayLayer(
        this.surfaceBorderOverlayLayer,
        this.surfaceBorderOverlayVariant,
        this.readerSettings,
        scrollOffset
      )
    }
  }

  surfacePaperTextureIndex(detail = {}) {
    const detailIndex = Number(detail?.index)
    if (Number.isFinite(detailIndex)) return Math.floor(detailIndex)
    const fixedLayoutIndex = this.fixedLayoutCurrentPageIndex()
    if (Number.isFinite(fixedLayoutIndex)) return fixedLayoutIndex
    const entry = this.contentEntries(detail).find(content => Number.isFinite(Number(content.index)))
    const entryIndex = Number(entry?.index)
    return Number.isFinite(entryIndex) ? Math.floor(entryIndex) : 0
  }

  updateSurfacePaperTexture(detail = {}) {
    const index = this.surfacePaperTextureIndex(detail)
    const section = this.view?.book?.sections?.[index]
    const textureKey = readerPaperTextureVariantKey(this.publicationUrl, section, index, detail)
    const textureVariant = readerPaperTextureVariantForPage(textureKey)
    const borderOverlayVariant = readerPageBorderOverlayVariantForPage(textureKey)
    this.surfaceTextureVariant = textureVariant
    this.surfaceBorderOverlayVariant = borderOverlayVariant
    this.surfaceTextureLayer = this.surfaceTextureLayer && readerRoot.contains(this.surfaceTextureLayer)
      ? this.surfaceTextureLayer
      : ensureReaderSurfaceTextureLayer()
    this.surfaceBorderOverlayLayer = this.surfaceBorderOverlayLayer && readerRoot.contains(this.surfaceBorderOverlayLayer)
      ? this.surfaceBorderOverlayLayer
      : ensureReaderSurfaceBorderOverlayLayer()
    this.surfacePaperTextureBaseOffset = this.currentRendererContainerPosition()
    this.surfaceTextureScrollOffset = { x: 0, y: 0 }
    readerRoot.dataset.navicSurfacePaperTextureKey = textureKey
    readerRoot.dataset.navicSurfacePaperTextureAsset = textureVariant.asset
    readerRoot.dataset.navicSurfacePageBorderOverlayAsset = borderOverlayVariant.asset
    readerRoot.dataset.navicSurfaceBorderOverlayAsset = borderOverlayVariant.asset
    this.renderSurfacePaperTextureLayers()
  }

  applyReaderDirection(direction, rerender = true) {
    const normalized = direction === ReaderDirectionLtr || direction === ReaderDirectionRtl
      ? direction
      : ReaderDirectionDefault
    if (this.view?.book) {
      if (this.originalBookDir === null) this.originalBookDir = this.view.book.dir || ''
      this.view.book.dir = normalized === ReaderDirectionDefault ? this.originalBookDir : normalized
    }
    for (const doc of this.contentDocuments()) {
      this.applyDocumentDirection(doc, normalized)
    }
    if (rerender) this.view?.renderer?.render?.()
  }

  applyDocumentDirection(doc, direction) {
    if (!doc) return
    for (const element of [doc.documentElement, doc.body].filter(Boolean)) {
      if (direction === ReaderDirectionDefault) {
        if (element.dataset.navicOriginalDir !== undefined) {
          const original = element.dataset.navicOriginalDir
          if (original) element.setAttribute('dir', original)
          else element.removeAttribute('dir')
          delete element.dataset.navicOriginalDir
        }
      } else {
        if (element.dataset.navicOriginalDir === undefined) {
          element.dataset.navicOriginalDir = element.getAttribute('dir') || ''
        }
        element.setAttribute('dir', direction)
      }
    }
  }

  async search(query) {
    if (!this.view || !query) return
    try {
      const results = []
      for await (const result of this.view.search?.({ query }) || []) {
        results.push(...normalizeSearchResult(result, results.length, this.view))
      }
      post({ type: 'searchResults', query, results })
    } catch (error) {
      reportError(error, 'search_failed')
    }
  }

  postToc() {
    const items = flattenTocItems(this.view?.book?.toc || [])
    post({ type: 'toc', items })
  }

  currentFixedLayoutLocationDetail() {
    if (this.view?.isFixedLayout !== true) return null
    const index = this.fixedLayoutCurrentPageIndex()
    const pageCount = Number(this.view?.book?.sections?.length)
    if (!Number.isFinite(index) || !Number.isFinite(pageCount) || pageCount <= 0) return null
    const section = this.view?.book?.sections?.[index]
    return {
      index,
      href: section?.href || section?.id,
      fraction: pageCount <= 1 ? 0 : index / (pageCount - 1),
    }
  }

  postCurrentLocationSnapshot(reason = 'snapshot') {
    const detail = this.lastRelocateDetail || this.currentFixedLayoutLocationDetail()
    if (!detail) {
      log('location-snapshot:missing', reason)
      return
    }
    log('location-snapshot', reason)
    this.postLocationChanged(detail, reason)
  }

  postLocationChanged(detail, reason = 'relocate') {
    const tocItem = detail.tocItem || {}
    const pagePosition = this.readerPagePosition(detail)
    post({
      type: 'locationChanged',
      href: detail.href || tocItem.href,
      cfi: detail.cfi,
      progress: optionalNumber(detail.fraction ?? detail.progress ?? detail.totalProgress),
      pageIndex: pagePosition?.pageIndex,
      pageCount: pagePosition?.pageCount,
      tocTitle: tocItem.label || tocItem.title,
    })
    log('location-changed:posted', reason)
    if (detail.cfi) post({ type: 'cfiChanged', cfi: detail.cfi })
    if (tocItem.href || tocItem.label || tocItem.title) {
      post({ type: 'tocItemChanged', href: tocItem.href, title: tocItem.label || tocItem.title })
    }
  }

  onRelocate(detail) {
    this.lastRelocateDetail = detail
    this.updateSurfacePaperTexture(detail)
    this.postLocationChanged(detail)
  }

  attachContentDocumentBehaviors(doc, index) {
    if (!doc) return
    this.applyDocumentDirection(doc, this.readerDirectionModeValue)
    this.applyDocumentTheme(doc, this.readerSettings, index)
    this.classifyReaderLinks(doc)
    this.attachSepiaImageOverlayToggle(doc)
    this.attachLinkNavigation(doc, index)
    this.attachScrolledEdgeTurnGestures(doc)
    this.attachCenterTapGesture(doc)
    if (doc.defaultView?.__navicSelectionBridgeAttached) return
    doc.defaultView.__navicSelectionBridgeAttached = true
    doc.addEventListener('selectionchange', () => {
      const selection = doc.getSelection()
      const text = selection?.toString?.().trim()
      if (!text) {
        post({ type: 'selectionChanged' })
        return
      }
      const range = selection.rangeCount ? selection.getRangeAt(0) : null
      const cfi = range && Number.isFinite(index)
        ? this.view?.getCFI?.(index, range)
        : undefined
      post({
        type: 'selectionChanged',
        text,
        cfi,
        href: this.view?.book?.sections?.[index]?.href,
      })
    })
  }

  onLoad(detail = {}) {
    this.applyReaderViewportLayout('load')
    this.applyReaderDirection(this.readerDirectionModeValue, false)
    this.updateSurfacePaperTexture(detail)
    if (detail.doc) log('load:event-doc', `index=${detail.index ?? 'unknown'}`)
    for (const content of this.contentEntries(detail)) {
      this.attachContentDocumentBehaviors(content.doc, content.index)
    }
    requestAnimationFrame(() => {
      this.applyRendererTheme(this.readerSettings)
      requestAnimationFrame(() => this.logContentLayout('load'))
    })
    if (this.mediaOverlayEnabled) post({ type: 'overlayFragmentInactive' })
  }

  logContentLayout(label = 'unknown') {
    const describeRect = rect => rect
      ? `${Math.round(rect.left)},${Math.round(rect.top)},${Math.round(rect.width)}x${Math.round(rect.height)}`
      : 'missing'
    const contents = this.view?.renderer?.getContents?.() || []
    const surfaceRect = this.view?.getBoundingClientRect?.()
    const rendererRect = this.view?.renderer?.getBoundingClientRect?.()
    log(
      'surface-layout',
      `label=${label}`,
      `view=${describeRect(surfaceRect)}`,
      `renderer=${describeRect(rendererRect)}`,
      `inner=${Math.round(window.innerWidth)}x${Math.round(window.innerHeight)}`,
      `visual=${Math.round(window.visualViewport?.width || 0)}x${Math.round(window.visualViewport?.height || 0)}`
    )
    if (!contents.length) {
      log('content-layout', `label=${label}`, 'contents=0')
      return
    }
    for (const content of contents) {
      const doc = content.doc
      if (!doc) {
        log('content-layout', `label=${label}`, `index=${content.index}`, 'doc=missing')
        continue
      }
      const frameElement = doc.defaultView?.frameElement
      const frameRect = frameElement?.getBoundingClientRect?.()
      const bodyRect = doc.body?.getBoundingClientRect?.()
      const htmlRect = doc.documentElement?.getBoundingClientRect?.()
      const bodyStyle = doc.defaultView.getComputedStyle(doc.body)
      const htmlStyle = doc.defaultView.getComputedStyle(doc.documentElement)
      const paragraphSpacing = bodyStyle.getPropertyValue('--reader-paragraph-spacing') ||
        htmlStyle.getPropertyValue('--reader-paragraph-spacing') ||
        'unset'
      const surfaceTextureStyle = this.surfaceTextureLayer
        ? window.getComputedStyle(this.surfaceTextureLayer)
        : null
      const surfaceBorderOverlayStyle = this.surfaceBorderOverlayLayer
        ? window.getComputedStyle(this.surfaceBorderOverlayLayer)
        : null
      const paragraphBlocks = Array.from(doc.querySelectorAll?.('p,[data-navic-paragraph-block="true"]') || [])
      const firstParagraphStyle = paragraphBlocks[0]
        ? doc.defaultView.getComputedStyle(paragraphBlocks[0])
        : null
      const textLength = doc.body?.textContent?.replace(/\s+/g, ' ').trim().length ?? 0
      log(
        'content-layout',
        `label=${label}`,
        `index=${content.index}`,
        `frameElement=${frameElement?.localName || 'missing'}`,
        `iframe=${describeRect(frameRect)}`,
        `html=${describeRect(htmlRect)}`,
        `body=${describeRect(bodyRect)}`,
        `textLength=${textLength}`,
        `color=${bodyStyle.color}`,
        `background=${bodyStyle.backgroundColor || htmlStyle.backgroundColor}`,
        `paragraphSpacing=${paragraphSpacing}`,
        `paragraphBlockCount=${paragraphBlocks.length}`,
        `firstParagraphMarginEnd=${firstParagraphStyle?.marginBlockEnd || firstParagraphStyle?.marginBottom || 'unset'}`,
        `surfaceTextureOpacity=${surfaceTextureStyle?.opacity || 'unset'}`,
        `surfaceTextureImage=${surfaceTextureStyle?.backgroundImage === 'none' ? 'none' : surfaceTextureStyle ? 'set' : 'unset'}`,
        `surfaceBorderOverlayOpacity=${surfaceBorderOverlayStyle?.opacity || 'unset'}`,
        `surfaceBorderOverlayImage=${surfaceBorderOverlayStyle?.backgroundImage === 'none' ? 'none' : surfaceBorderOverlayStyle ? 'set' : 'unset'}`,
        `surfaceTextureLayer=${this.surfaceTextureLayer ? 'present' : 'missing'}`,
        `surfaceTextureAsset=${readerRoot.dataset.navicSurfacePaperTextureAsset || 'unset'}`,
        `surfaceBorderOverlayAsset=${readerRoot.dataset.navicSurfaceBorderOverlayAsset || 'unset'}`
      )
    }
  }

  contentEntries(detail = {}) {
    const entries = []
    const seen = new Set()
    const contents = this.view?.renderer?.getContents?.() || []
    const add = (doc, index) => {
      if (!doc || seen.has(doc)) return
      seen.add(doc)
      entries.push({ doc, index })
    }
    if (detail.doc) {
      const matchingContent = contents.find(content => content.doc === detail.doc)
      add(detail.doc, Number.isFinite(detail.index) ? detail.index : matchingContent?.index)
    }
    for (const content of contents) {
      add(content.doc, content.index)
    }
    return entries
  }

  contentDocuments() {
    return this.contentEntries().map(content => content.doc)
  }
}

const readerTypographyCss = settings => {
  if (settings.publisherStyles === true) return ''
  const fontFamily = readerEffectiveFontFamily(settings)
  return `
  ${readerFlowMode(settings) === ReaderFlowPagedVertical ? `
  html, body {
    writing-mode: vertical-rl !important;
  }
  ` : ''}
  body {
    line-height: ${settings.lineHeight || 1.55} !important;
    ${fontFamily ? `font-family: ${fontFamily} !important;` : ''}
    margin-inline: ${settings.marginPercent || 0}% !important;
    padding-block: var(--reader-scroll-gap, 0rem) !important;
  }
`
}

const readerParagraphSpacingCss = settings => `
  html body p,
  html body [data-navic-paragraph-block="true"] {
    display: block !important;
    margin-block-start: 0 !important;
    margin-block-end: var(--reader-paragraph-spacing, ${readerParagraphSpacingEm(settings)}) !important;
    margin-bottom: var(--reader-paragraph-spacing, ${readerParagraphSpacingEm(settings)}) !important;
    padding-block-end: 0 !important;
  }
  html body p + p,
  html body [data-navic-paragraph-block="true"] + [data-navic-paragraph-block="true"],
  html body p + [data-navic-paragraph-block="true"],
  html body [data-navic-paragraph-block="true"] + p {
    margin-block-start: 0 !important;
  }
`

const isThemeBackgroundMediaElement = element =>
  ['IMG', 'PICTURE', 'VIDEO', 'CANVAS', 'SVG', 'OBJECT', 'EMBED'].includes(element?.tagName) ||
  element?.getAttribute?.('role') === 'img'

const readerDocumentThemeCss = settings => {
  const palette = readerThemePalette(settings?.theme)
  return `
  html {
    --reader-background: ${palette.background};
    --reader-foreground: ${palette.foreground};
    --reader-accent: ${palette.accent};
    --theme-bg-color: ${palette.background};
    color-scheme: ${palette.background === '#fbfaf8' || palette.background === '#f3ead7' ? 'light' : 'dark'};
    color: var(--reader-foreground) !important;
    background: var(--reader-background) !important;
    background-color: var(--reader-background) !important;
  }
  html, body {
    color: var(--reader-foreground) !important;
    background-color: var(--reader-background) !important;
    background-image: none !important;
    position: relative !important;
  }
  body :not(img):not(picture):not(video):not(canvas):not(svg):not(object):not(embed):not([role="img"]) {
    background-color: transparent !important;
  }
  body [style*="background"]:not(img):not(picture):not(video):not(canvas):not(svg):not(object):not(embed):not([role="img"]),
  body [bgcolor]:not(img):not(picture):not(video):not(canvas):not(svg):not(object):not(embed):not([role="img"]) {
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
  }
  ${readerThemeKey(settings?.theme) === ReaderThemeSepia ? `
  img:not([data-navic-sepia-overlay="off"]) {
    mix-blend-mode: multiply;
  }
  ` : `
  img {
    mix-blend-mode: normal !important;
  }
  `}
  img[data-navic-sepia-overlay="off"] {
    background-color: transparent !important;
    mix-blend-mode: normal !important;
  }
  canvas, svg {
    background-color: transparent !important;
  }
`
}

const readerContentCss = settings => {
  return `
  ${readerFontFaceCss(settings)}
  ${readerDocumentThemeCss(settings)}
  html {
    font-size: ${settings.fontSizePercent || 100}%;
  }
  ${readerTypographyCss(settings)}
  ${readerParagraphSpacingCss(settings)}
  a:any-link {
    color: inherit !important;
    text-decoration: none !important;
    border-bottom: 0 !important;
    box-shadow: none !important;
  }
  a:any-link[data-navic-link-kind="text"]::after {
    content: ' »';
    font-size: 0.72em;
    font-weight: 700;
    line-height: 0;
    vertical-align: sub;
    white-space: nowrap;
    opacity: 0.72;
  }
  a:any-link[data-navic-link-kind="media"]::after {
    content: '' !important;
  }
  a:any-link:empty::after {
    content: '';
  }
  .${overlayClass} {
    background: color-mix(in srgb, var(--reader-accent) 28%, transparent);
    border-radius: 3px;
  }
`
}

const normalizeSearchResult = (result, startIndex, view) => {
  if (!result || result === 'done' || result.progress != null) return []
  const sectionTitle = result.label || result.sectionTitle
  const items = result.subitems || [result]
  return items
    .filter(item => item?.cfi || item?.href)
    .map((item, index) => {
      const excerpt = normalizeExcerpt(item.excerpt)
      const cfi = item.cfi
      return {
        id: item.id || cfi || item.href || `search-${startIndex + index}`,
        cfi,
        href: item.href || hrefForCfi(view, cfi),
        excerpt,
        sectionTitle,
      }
    })
}

const normalizeExcerpt = excerpt => {
  if (!excerpt) return undefined
  if (typeof excerpt === 'string') return excerpt
  return [excerpt.pre, excerpt.match, excerpt.post]
    .filter(Boolean)
    .join('')
    .replace(/\s+/g, ' ')
    .trim()
}

const hrefForCfi = (view, cfi) => {
  if (!view || !cfi) return undefined
  try {
    const parsed = view.resolveCFI?.(cfi)
    const section = view.book?.sections?.[parsed?.index]
    return section?.href
  } catch {
    return undefined
  }
}

const flattenTocItems = (items, level = 0, path = 'toc') =>
  (items || []).flatMap((item, index) => {
    const id = `${path}-${index}`
    const title = tocLabel(item)
    const href = item?.href
    const row = title || href
      ? [{ id: item?.id || href || id, title: title || href, href, level }]
      : []
    return [
      ...row,
      ...flattenTocItems(item?.subitems || [], level + 1, id),
    ]
  })

const tocLabel = item => {
  const label = item?.label || item?.title
  if (!label) return undefined
  if (typeof label === 'string') return label.trim() || undefined
  if (typeof label === 'object') {
    const value = Object.values(label).find(value => typeof value === 'string' && value.trim())
    return value?.trim()
  }
  return String(label).trim() || undefined
}

const runtime = new NavicReaderRuntime()

window.NavicReaderBridge = {
  dispatch: command => runtime.dispatch(command),
  postOverlayFragmentActive: fragment => post({ type: 'overlayFragmentActive', ...fragment }),
  postOverlayFragmentInactive: fragmentId => post({ type: 'overlayFragmentInactive', fragmentId }),
}

log('module-loaded')
post({ type: 'ready' })
