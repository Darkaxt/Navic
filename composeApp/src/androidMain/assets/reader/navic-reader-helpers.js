import {
  KomikkuNavigationRegionLeft,
  KomikkuNavigationRegionNext,
  KomikkuNavigationRegionPrevious,
  KomikkuNavigationRegionRight,
  ReaderFlowPagedVertical,
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  ReaderFontSourceCustom,
  ReaderFontSourceNavic,
  ReaderPageBorderOverlayAssets,
  ReaderPageBorderOverlayVariantCount,
  ReaderPageNumberLayerSelector,
  ReaderPaperTextureAssets,
  ReaderPaperTextureVariantCount,
  ReaderShellCoverLayerSelector,
  ReaderShellCoverTransitionMs,
  ReaderSurfacePageBorderOverlayLayerSelector,
  ReaderSurfacePaperTextureLayerSelector,
  ReaderTapZoneOverlayLayerSelector,
  ReaderThemeLight,
  ReaderThemeSepia,
  readerCssQuotedString,
  readerCustomFontFamily,
  readerCustomFontUrl,
  readerEffectiveFontFamily,
  readerFlowMode,
  readerFontFormat,
  readerFontSource,
  readerThemeKey,
  readerThemePalette,
} from './navic-reader-settings-core.js'
import {
  closestElement,
  komikkuNavigationRegions,
  readerMediaSelector,
} from './navic-reader-media.js'
import {
  stableHash,
} from './navic-reader-identity.js'
import {
  overlayClass,
  readerRoot,
} from './navic-reader-bridge-core.js'

export * from './navic-reader-bridge-core.js'
export * from './navic-reader-settings-core.js'
export * from './navic-reader-media.js'
export * from './navic-reader-identity.js'
export * from './navic-reader-pagination-model.js'

export const readerAssetUrl = path => new URL(path, document.baseURI).href
export const ReaderShellCoverProgressThreshold = 0.0015

export const readerTokenText = value => {
  if (value == null) return ''
  if (Array.isArray(value)) return value.map(readerTokenText).join(' ')
  if (typeof value === 'object') return Object.values(value).map(readerTokenText).join(' ')
  return String(value)
}

export const readerSectionTokenText = section =>
  [
    section?.id,
    section?.href,
    section?.label,
    section?.title,
    section?.type,
    section?.properties,
  ].map(readerTokenText).filter(Boolean).join(' ')

export const readerSectionLooksLikeCover = (section, index = 0) => {
  const tokens = readerSectionTokenText(section).toLowerCase()
  if (!tokens) return false
  return /(^|[\s._/-])(cover|cubierta|portada)([\s._/-]|$)|cover-image|coverpage|cover.xhtml|frontcover|cubierta.xhtml|portada.xhtml/.test(tokens) ||
    (index === 0 && /(cover|cubierta|portada)/.test(tokens))
}

export const readerContentDocumentLooksLikeCover = (doc, section, index = 0) => {
  if (!doc || index !== 0) return false
  if (readerSectionLooksLikeCover(section, index)) return true
  const text = doc.body?.textContent?.replace(/\s+/g, ' ').trim() || ''
  const images = Array.from(doc.images || [])
  if (text.length > 40 || images.length < 1 || images.length > 2) return false
  const tokenText = [
    doc.title,
    doc.documentElement?.getAttribute?.('epub:type'),
    doc.body?.getAttribute?.('epub:type'),
    ...images.map(image => [
      image.getAttribute('src'),
      image.getAttribute('alt'),
      image.getAttribute('title'),
      image.getAttribute('aria-label'),
    ].filter(Boolean).join(' ')),
  ].filter(Boolean).join(' ').toLowerCase()
  if (/cover|frontcover|cover-image|coverpage|cubierta|portada/.test(tokenText)) return true
  const image = images[0]
  const width = Number(image.getAttribute('width') || image.naturalWidth)
  const height = Number(image.getAttribute('height') || image.naturalHeight)
  const aspect = width > 0 ? height / width : 0
  return Number.isFinite(aspect) && aspect >= 1.15 && aspect <= 1.85
}

const readerElementText = element =>
  element?.textContent?.replace(/\s+/g, ' ').trim() || ''

const readerElementContainsMedia = element =>
  Boolean(element?.matches?.('img,svg,object,picture') || element?.querySelector?.('img,svg,object,picture'))

const readerFirstMeaningfulBodyElement = doc => {
  for (const element of Array.from(doc?.body?.children || [])) {
    if (element.tagName === 'BR') continue
    if (readerElementText(element) || readerElementContainsMedia(element)) return element
  }
  return null
}

const readerImageAspect = image => {
  const width = Number(image?.getAttribute?.('width') || image?.naturalWidth)
  const height = Number(image?.getAttribute?.('height') || image?.naturalHeight)
  return width > 0 ? { width, height, aspect: height / width } : null
}

export const readerEmbeddedCoverImage = (doc, index = 0) => {
  if (!doc?.body || index !== 0 || doc.documentElement?.dataset?.navicEmbeddedCoverSuppressed === 'true') return null
  const first = readerFirstMeaningfulBodyElement(doc)
  if (!first || readerElementText(first).length > 40) return null
  const image = first.matches?.('img') ? first : first.querySelector?.('img')
  const metrics = readerImageAspect(image)
  if (!image || !metrics || !Number.isFinite(metrics.aspect)) return null
  const largeEnough = metrics.width >= 480 || metrics.height >= 640
  if (!largeEnough || metrics.aspect < 1.1 || metrics.aspect > 1.9) return null
  return image
}

export const suppressReaderEmbeddedCoverPage = (doc, index = 0) => {
  const image = readerEmbeddedCoverImage(doc, index)
  if (!image) return false
  const body = doc.body
  const parent = image.parentElement
  const hideTarget = parent && parent !== body && !readerElementText(parent) && parent.children.length <= 2
    ? parent
    : image
  const hidden = [hideTarget]
  let sibling = hideTarget.nextSibling
  for (let count = 0; sibling && count < 4; count += 1) {
    const current = sibling
    sibling = sibling.nextSibling
    const isWhitespace = current.nodeType === Node.TEXT_NODE && !String(current.textContent || '').trim()
    const isBreak = current.nodeType === Node.ELEMENT_NODE && current.tagName === 'BR'
    if (!isWhitespace && !isBreak) break
    hidden.push(current)
  }
  doc.documentElement.dataset.navicEmbeddedCoverSuppressed = 'true'
  body?.setAttribute?.('data-navic-embedded-cover-suppressed', 'true')
  for (const node of hidden) {
    if (node.nodeType !== Node.ELEMENT_NODE) {
      node.textContent = ''
      continue
    }
    node.setAttribute('data-navic-embedded-cover-hidden', 'true')
    setStylesImportant(node, {
      display: 'none',
      visibility: 'hidden',
      width: '0',
      height: '0',
      margin: '0',
      padding: '0',
    })
  }
  return true
}

export const readerSectionIsReadable = section =>
  Boolean(section) && section.linear !== 'no'

export const isInteractiveReaderTarget = target =>
  Boolean(closestElement(target, `a,button,input,textarea,select,summary,[role="button"],${readerMediaSelector}`))

export const readerTargetInsideShellCover = target =>
  Boolean(closestElement(target, ReaderShellCoverLayerSelector))

export const readerPaperTexturePageLocator = detail => {
  const pageIndex = Number(detail?.pageIndex)
  if (Number.isFinite(pageIndex)) {
    return `page:${Math.max(0, Math.floor(pageIndex))}`
  }
  const cfi = String(detail?.cfi || '').trim()
  if (cfi) return `cfi:${cfi}`
  const progress = Number(detail?.fraction ?? detail?.progress ?? detail?.totalProgress)
  if (Number.isFinite(progress)) return `progress:${Math.round(Math.min(1, Math.max(0, progress)) * 100000)}`
  const href = String(detail?.href || detail?.tocItem?.href || '').trim()
  return href ? `href:${href}` : 'section'
}

export const readerPaperTextureVariantKey = (publicationUrl, section, index, detail = {}) =>
  [
    publicationUrl || 'publication',
    Number.isFinite(index) ? index : 'unknown',
    section?.href || section?.id || section?.label || '',
    readerPaperTexturePageLocator(detail),
  ].join('|')

export const readerSurfaceTextureVariantForPage = (key, assets, variantCount) => {
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

export const readerPaperTextureVariantForPage = key =>
  readerSurfaceTextureVariantForPage(key, ReaderPaperTextureAssets, ReaderPaperTextureVariantCount)

export const readerPageBorderOverlayVariantForPage = key =>
  readerSurfaceTextureVariantForPage(
    `${key}|page-border-overlay`,
    ReaderPageBorderOverlayAssets,
    ReaderPageBorderOverlayVariantCount
  )

export const readerPaperTextureTransform = variant => {
  const transforms = []
  if (variant?.mirrored) transforms.push('scaleX(-1)')
  if (variant?.rotate180) transforms.push('rotate(180deg)')
  return transforms.length ? transforms.join(' ') : 'none'
}

export const readerPaperTextureCssOffset = value => {
  const offset = Number(value)
  if (!Number.isFinite(offset) || offset === 0) return '+ 0px'
  return offset < 0 ? `- ${Math.abs(offset)}px` : `+ ${offset}px`
}

export const readerPaperTextureBackgroundPosition = scrollOffset => {
  const x = Number(scrollOffset?.x)
  const y = Number(scrollOffset?.y)
  const xPx = Number.isFinite(x) ? Math.round(x) : 0
  const yPx = Number.isFinite(y) ? Math.round(y) : 0
  return `calc(50% ${readerPaperTextureCssOffset(xPx)}) calc(50% ${readerPaperTextureCssOffset(yPx)})`
}

export {
  readerPageDragPreviewMotion,
  readerPaperTextureDragDirection,
  readerSurfacePaperTextureScrollOffset,
} from './navic-reader-motion.js'

export const readerSurfacePaperTextureOpacity = settings => {
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return '0'
    case ReaderThemeSepia:
      return '0.54'
    case 'dark':
    case 'dusk':
      return '0.12'
    default:
      return '0.16'
  }
}

export const readerSurfacePageBorderOverlayOpacity = settings => {
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

export const readerSurfacePageBorderOverlayFilter = settings => {
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return 'none'
    case ReaderThemeSepia:
      return 'contrast(1.35) saturate(1.08)'
    case 'dark':
    case 'dusk':
      return 'contrast(1.2) saturate(1.05)'
    default:
      return 'contrast(1.18) saturate(1.04)'
  }
}

export const readerSurfacePageBorderOverlayBackgroundImage = borderOverlayVariant => {
  if (!borderOverlayVariant?.asset) return 'none'
  const textureUrl = `url("${readerAssetUrl(borderOverlayVariant.asset)}")`
  return [textureUrl, textureUrl].join(', ')
}

export const readerPageNumberPageCount = (pagePosition, fallbackPageCount = null) => {
  const pageCount = pagePosition?.pageCount
  if (Number.isFinite(pageCount) && pageCount > 0) return Math.floor(pageCount)
  if (Number.isFinite(fallbackPageCount) && fallbackPageCount > 0) return Math.floor(fallbackPageCount)
  return null
}

export const readerPageNumberPositionWithPageCount = (pagePosition, fallbackPageCount = null) => {
  if (!pagePosition) return null
  const prefersFallbackPageCount = pagePosition.pageCountSource === 'section'
  const fallback = Number(fallbackPageCount)
  const pageCount = prefersFallbackPageCount && Number.isFinite(fallback) && fallback > 0
      ? Math.floor(fallback)
      : readerPageNumberPageCount(pagePosition)
  if (!Number.isFinite(pageCount) || pageCount <= 0) return pagePosition
  return {
    ...pagePosition,
    pageIndex: Number.isFinite(Number(pagePosition.pageIndex))
      ? Math.min(pageCount - 1, Math.max(0, Math.floor(Number(pagePosition.pageIndex))))
      : pagePosition.pageIndex,
    pageCount,
  }
}

export const readerPageNumberLabel = pagePosition => {
  const pageIndex = pagePosition?.pageIndex
  if (!Number.isFinite(pageIndex) || pageIndex < 0) return ''
  const currentPage = pageIndex + 1
  const pageCount = readerPageNumberPageCount(pagePosition)
  if (!Number.isFinite(pageCount) || pageCount <= 0) return ''
  return `${currentPage} / ${pageCount}`
}

export const readerPageNumberBlendMode = settings => {
  switch (readerThemeKey(settings?.theme)) {
    case ReaderThemeSepia:
    case ReaderThemeLight:
      return 'multiply'
    default:
      return 'normal'
  }
}

export const readerFontFaceCss = settings => readerFontSource(settings) === ReaderFontSourceNavic
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
  : readerFontSource(settings) === ReaderFontSourceCustom && readerCustomFontUrl(settings)
    ? `
  @font-face {
    font-family: ${readerCssQuotedString(readerCustomFontFamily(settings))};
    src: url('${readerCustomFontUrl(settings)}') format('${readerFontFormat(readerCustomFontUrl(settings))}');
    font-style: normal;
    font-weight: 400;
    font-display: swap;
  }
`
  : ''

export const readerParagraphSpacingEm = settings => {
  const percent = Number(settings.paragraphSpacingPercent)
  const normalized = Number.isFinite(percent)
    ? Math.min(200, Math.max(0, percent))
    : 100
  return `${normalized / 100}em`
}

export const applyReaderParagraphSpacing = (doc, settings) => {
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

const readerChapterOpeningHeadingSelector = 'h1,h2,h3,h4,h5,h6'

const readerElementHasContent = element => {
  const tagName = element?.tagName || ''
  if (!tagName || ['SCRIPT', 'STYLE', 'LINK', 'META', 'TITLE'].includes(tagName)) return false
  if (element.matches?.(readerMediaSelector)) return true
  return Boolean(String(element.textContent || '').replace(/\s+/g, ''))
}

const readerVisibleContentElement = element => {
  if (!readerElementHasContent(element)) return false
  const win = element.ownerDocument?.defaultView
  const style = win?.getComputedStyle?.(element)
  if (style?.display === 'none' || style?.visibility === 'hidden') return false
  const rect = element.getBoundingClientRect?.()
  return Boolean(rect && rect.width > 0 && rect.height > 0)
}

const readerFirstVisibleContentElement = doc =>
  Array.from(doc?.body?.querySelectorAll?.('body *') || [])
    .slice(0, 160)
    .find(readerVisibleContentElement) || null

const readerChapterOpeningHeading = doc => {
  const heading = Array.from(doc?.body?.querySelectorAll?.(readerChapterOpeningHeadingSelector) || [])
    .slice(0, 12)
    .find(readerVisibleContentElement)
  if (!heading) return null
  const firstVisible = readerFirstVisibleContentElement(doc)
  if (!firstVisible) return heading
  if (firstVisible === heading || firstVisible.contains(heading) || heading.contains(firstVisible)) return heading
  return null
}

export const readerNormalizeChapterOpeningMargins = (doc, settings = {}) => {
  if (!doc?.body || readerFlowMode(settings) === ReaderFlowScrolled || readerFlowMode(settings) === ReaderFlowScrolledGaps) return null
  const heading = readerChapterOpeningHeading(doc)
  if (!heading) return null
  const win = doc.defaultView
  const viewportHeight = Number(win?.innerHeight || doc.documentElement?.clientHeight || doc.body?.clientHeight || 0)
  if (!Number.isFinite(viewportHeight) || viewportHeight <= 0) return null
  const style = win?.getComputedStyle?.(heading)
  const currentMargin = Number.parseFloat(style?.marginBlockStart || style?.marginTop || '0')
  if (!Number.isFinite(currentMargin) || currentMargin <= 0) return null
  const cap = Math.round(Math.min(96, Math.max(48, viewportHeight * 0.045)))
  if (currentMargin <= cap) return null
  heading.style.setProperty('margin-block-start', `${cap}px`, 'important')
  heading.style.setProperty('margin-top', `${cap}px`, 'important')
  heading.setAttribute('data-navic-chapter-opening-margin-capped', 'true')
  heading.dataset.navicChapterOpeningOriginalMargin = String(currentMargin)
  heading.dataset.navicChapterOpeningMarginCap = String(cap)
  return {
    tagName: heading.tagName,
    originalMargin: currentMargin,
    cap,
  }
}

export const ensureReaderSurfaceTextureLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderSurfacePaperTextureLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicSurfacePaperTextureLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

export const ensureReaderSurfaceBorderOverlayLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderSurfacePageBorderOverlayLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicSurfacePageBorderOverlayLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

export const ensureReaderPageNumberLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderPageNumberLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicPageNumberLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

export const ensureReaderShellCoverLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderShellCoverLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicShellCoverLayer = 'true'
    layer.setAttribute('aria-label', 'Book cover')
    readerRoot.append(layer)
  }
  return layer
}

export const ensureReaderShellCoverImage = layer => {
  let image = layer?.querySelector?.('[data-navic-shell-cover-image="true"]')
  if (!image) {
    image = document.createElement('img')
    image.dataset.navicShellCoverImage = 'true'
    image.decoding = 'async'
    image.loading = 'eager'
    layer.replaceChildren(image)
  }
  return image
}

export const updateReaderShellCoverLayer = (layer, coverUrl, settings, title = '') => {
  if (!layer || !coverUrl) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const image = ensureReaderShellCoverImage(layer)
  if (image.getAttribute('src') !== coverUrl) image.setAttribute('src', coverUrl)
  image.setAttribute('alt', title || 'Book cover')
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': '2147483643',
    display: 'flex',
    'align-items': 'center',
    'justify-content': 'center',
    overflow: 'hidden',
    background: '#000000',
    'background-color': '#000000',
    color: '#ffffff',
    padding: '0px',
    'box-sizing': 'border-box',
    opacity: '1',
    transform: 'translateX(0) scale(1)',
    'transform-origin': 'center',
    transition: `opacity ${ReaderShellCoverTransitionMs}ms ease, transform ${ReaderShellCoverTransitionMs}ms ease`,
    'pointer-events': 'auto',
    'touch-action': 'manipulation',
  })
  setStylesImportant(image, {
    display: 'block',
    width: '100%',
    height: '100%',
    'max-width': '100%',
    'max-height': '100%',
    'object-fit': 'contain',
    'object-position': 'center center',
    background: 'transparent',
    'background-color': 'transparent',
    margin: '0',
    padding: '0',
    border: '0',
    'box-shadow': 'none',
  })
}

export const updateReaderSurfaceTextureLayer = (layer, textureVariant, settings, scrollOffset = null) => {
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

export const updateReaderSurfaceBorderOverlayLayer = (layer, borderOverlayVariant, settings, scrollOffset = null) => {
  if (!layer || !borderOverlayVariant?.asset) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const texturePosition = readerPaperTextureBackgroundPosition(scrollOffset)
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': '2147483646',
    'pointer-events': 'none',
    'background-image': readerSurfacePageBorderOverlayBackgroundImage(borderOverlayVariant),
    'background-size': 'cover, cover',
    'background-position': [texturePosition, texturePosition].join(', '),
    'background-repeat': 'no-repeat, no-repeat',
    'background-color': 'transparent',
    opacity: readerSurfacePageBorderOverlayOpacity(settings),
    filter: readerSurfacePageBorderOverlayFilter(settings),
    'mix-blend-mode': 'multiply',
    transform: readerPaperTextureTransform(borderOverlayVariant),
    'transform-origin': 'center',
  })
}

export const readerTapZoneOverlayLabel = type => {
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

export const readerTapZoneOverlayColor = type => {
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

export const readerTapZoneOverlayRegions = (tapZoneMode, smallerTapZone, flowMode) => [
  ...komikkuNavigationRegions(tapZoneMode, smallerTapZone, flowMode),
]

export const ensureTapZoneOverlayLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderTapZoneOverlayLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicTapZoneOverlayLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

export const updateTapZoneOverlayLayer = (
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

export const isParagraphCandidate = element =>
  Boolean(element?.matches?.('p,[role="doc-p"],div'))

export const isReaderParagraphBlock = element => {
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

export const classifyReaderParagraphBlocks = doc => {
  if (!doc?.querySelectorAll) return
  for (const element of doc.querySelectorAll('p,[role="doc-p"],div')) {
    if (isReaderParagraphBlock(element)) {
      element.dataset.navicParagraphBlock = 'true'
    } else if (element.dataset?.navicParagraphBlock === 'true') {
      delete element.dataset.navicParagraphBlock
    }
  }
}

export const setStylesImportant = (element, styles) => {
  if (!element) return
  for (const [property, value] of Object.entries(styles)) {
    element.style.setProperty(property, value, 'important')
  }
}

export const readerViewportSize = () => {
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

const clampNumber = (value, min, max) => Math.min(max, Math.max(min, value))

const readerStyleNumber = (settings, key, fallback, min, max) => {
  const value = Number(settings?.[key])
  return Number.isFinite(value) ? clampNumber(value, min, max) : fallback
}

export const readerFontWeightValue = settings =>
  readerStyleNumber(settings, 'fontWeight', 400, 100, 900)

export const readerLetterSpacingValue = settings =>
  readerStyleNumber(settings, 'letterSpacing', 0, -3, 7)

export const readerWordSpacingValue = settings =>
  readerStyleNumber(settings, 'wordSpacing', 0, -4, 12)

export const readerSideMarginValue = settings =>
  readerStyleNumber(settings, 'sideMargin', 6, 0, 20)

export const readerTopMarginValue = settings =>
  readerStyleNumber(settings, 'topMargin', 90, 0, 200)

export const readerBottomMarginValue = settings =>
  readerStyleNumber(settings, 'bottomMargin', 50, 0, 200)

export const readerTextIndentValue = settings =>
  readerStyleNumber(settings, 'indent', 0, -0.5, 8)

export const readerHeadingFontSizeValue = settings =>
  readerStyleNumber(settings, 'headingFontSize', 1, 0.5, 2)

export const readerFontSizePercentValue = settings =>
  readerStyleNumber(settings, 'fontSizePercent', 100, 50, 250)

export const readerMaxColumnCountValue = settings => {
  const value = Number(settings?.maxColumnCount)
  return Number.isFinite(value) ? Math.min(2, Math.max(0, Math.floor(value))) : 0
}

export const readerColumnThresholdValue = settings =>
  readerStyleNumber(settings, 'columnThreshold', 720, 400, 1200)

export const readerEffectiveMaxColumnCount = ({ inlineViewport, blockViewport, settings }) => {
  const maxColumnCount = readerMaxColumnCountValue(settings)
  if (maxColumnCount > 0) return maxColumnCount
  const columnThreshold = readerColumnThresholdValue(settings)
  const landscapeSpread = inlineViewport >= blockViewport && inlineViewport >= columnThreshold
  return landscapeSpread ? 2 : 1
}

export const readerAdaptiveFoliatePageBox = (viewport = readerViewportSize(), settings = {}) => {
  const width = Math.max(1, Math.round(Number(viewport?.width) || 1))
  const height = Math.max(1, Math.round(Number(viewport?.height) || 1))
  const flowMode = readerFlowMode(settings)
  const vertical = flowMode === ReaderFlowPagedVertical
  const inlineViewport = vertical ? height : width
  const blockViewport = vertical ? width : height
  const userMargin = clampNumber(Number(settings?.marginPercent) || 0, 0, 35) / 100
  const naturalInlineReserve = Math.max(32, Math.round(inlineViewport * (0.08 + userMargin * 0.35)))
  const naturalBlockReserve = Math.max(48, Math.round(blockViewport * 0.065))
  const maxColumnCount = readerEffectiveMaxColumnCount({ inlineViewport, blockViewport, settings })
  const columnThreshold = readerColumnThresholdValue(settings)
  const naturalInline = clampNumber(inlineViewport - naturalInlineReserve, 320, 1600)
  const maxInline = naturalInline
  const maxBlock = Math.max(720, blockViewport - naturalBlockReserve)
  return {
    maxInlineSize: `${Math.round(maxInline)}px`,
    maxBlockSize: `${Math.round(maxBlock)}px`,
    maxColumnCount: String(maxColumnCount),
    columnThreshold: `${Math.round(columnThreshold)}px`,
    viewportWidth: width,
    viewportHeight: height,
    flowMode,
  }
}

export const readerStartLocatorHasPosition = startLocator =>
  Boolean(
    startLocator?.cfi ||
    startLocator?.href ||
    Number.isFinite(Number(startLocator?.progress))
  )

export const flattenReaderNavigationItems = items => {
  const results = []
  const append = item => {
    if (!item) return
    results.push(item)
    for (const subitem of item.subitems || []) append(subitem)
  }
  for (const item of items || []) append(item)
  return results
}

export const readerNavigationItemMatches = (left, right) => {
  if (!left || !right) return false
  if (left === right) return true
  if (left.id != null && right.id != null && left.id === right.id) return true
  return Boolean(left.href && right.href && left.href === right.href && left.label === right.label)
}


export const readerTypographyCss = settings => {
  const usePublisherStyles = settings.publisherStyles === true
  const fontFamily = readerEffectiveFontFamily(settings)
  const fontWeight = readerFontWeightValue(settings)
  const letterSpacing = readerLetterSpacingValue(settings)
  const wordSpacing = readerWordSpacingValue(settings)
  const textIndent = readerTextIndentValue(settings)
  const headingFontSize = readerHeadingFontSizeValue(settings)
  const fontSizePercent = readerFontSizePercentValue(settings)
  return `
  ${readerFlowMode(settings) === ReaderFlowPagedVertical ? `
  html, body {
    writing-mode: vertical-rl !important;
  }
  ` : ''}
  html {
    font-size: var(--reader-content-font-size, ${fontSizePercent}%) !important;
    ${usePublisherStyles ? '' : `
    letter-spacing: ${letterSpacing}px !important;
    `}
  }
  body {
    font-size: 1rem !important;
    line-height: ${settings.lineHeight || 1.55} !important;
    width: auto !important;
    max-width: none !important;
    ${usePublisherStyles || !fontFamily ? '' : `font-family: ${fontFamily} !important;`}
    ${usePublisherStyles ? '' : `
    word-spacing: ${wordSpacing}px !important;
    `}
    margin-inline: ${settings.marginPercent || 0}% !important;
    padding-block: var(--reader-scroll-gap, 0rem) !important;
  }
  main,
  section,
  article,
  center,
  div:not(:has(*:not(b, a, em, i, strong, u, span))),
  div:has(> br):not(:has(> img)):not(:has(> svg)):not(:has(> canvas)),
  body > span:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  body > a:any-link:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  pre,
  body > code:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  body > samp:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  body > kbd:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  table:not(:has(img)):not(:has(svg)):not(:has(canvas)) {
    width: auto !important;
    max-width: none !important;
  }
  table:not(:has(img)):not(:has(svg)):not(:has(canvas)) {
    width: 100% !important;
  }
  p,
  li,
  blockquote,
  dd,
  td,
  th,
  main,
  section,
  article,
  center,
  div:not(:has(*:not(b, a, em, i, strong, u, span))),
  div:has(> br):not(:has(> img)):not(:has(> svg)):not(:has(> canvas)),
  body > span:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  body > a:any-link:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  pre,
  body > code:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  body > samp:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  body > kbd:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  font {
    font-size: 1rem !important;
    ${usePublisherStyles ? '' : `
    font-weight: ${fontWeight} !important;
    `}
    ${usePublisherStyles || textIndent < 0 ? '' : `text-indent: ${textIndent}em !important;`}
  }
  p span,
  p font,
  li span,
  li font,
  blockquote span,
  blockquote font,
  dd span,
  dd font,
  td span,
  td font,
  th span,
  th font,
  main span,
  main font,
  section span,
  section font,
  article span,
  article font,
  center span,
  center font,
  div:has(> br):not(:has(> img)):not(:has(> svg)):not(:has(> canvas)) span,
  div:has(> br):not(:has(> img)):not(:has(> svg)):not(:has(> canvas)) font,
  body > span:not(:has(img)):not(:has(svg)):not(:has(canvas)) span,
  body > span:not(:has(img)):not(:has(svg)):not(:has(canvas)) font,
  body > a:any-link:not(:has(img)):not(:has(svg)):not(:has(canvas)) span,
  body > a:any-link:not(:has(img)):not(:has(svg)):not(:has(canvas)) font,
  pre span,
  pre font,
  pre code,
  pre samp,
  pre kbd,
  p code,
  p samp,
  p kbd,
  li code,
  li samp,
  li kbd,
  blockquote code,
  blockquote samp,
  blockquote kbd,
  [data-navic-paragraph-block="true"] span,
  [data-navic-paragraph-block="true"] font {
    font-size: 1em !important;
  }
  pre {
    white-space: pre-wrap !important;
    overflow-wrap: anywhere !important;
  }
  p:has(> img:only-child),
  p:has(> span:only-child > img:only-child),
  p:has(> img:not(.has-text-siblings)),
  p:has(> a:first-child + img:last-child),
  div:has(> img:only-child),
  div:has(> span:only-child > img:only-child),
  div:has(> img:not(.has-text-siblings)),
  div:has(> a:first-child + img:last-child),
  li > p,
  ol > p,
  ul > p {
    text-indent: 0 !important;
  }
  ${usePublisherStyles ? '' : `
  h1 { font-size: calc(2em * ${headingFontSize}) !important; }
  h2 { font-size: calc(1.5em * ${headingFontSize}) !important; }
  h3 { font-size: calc(1.17em * ${headingFontSize}) !important; }
  h4 { font-size: calc(1em * ${headingFontSize}) !important; }
  h5 { font-size: calc(0.83em * ${headingFontSize}) !important; }
  h6 { font-size: calc(0.67em * ${headingFontSize}) !important; }
  `}
`
}

export const readerParagraphSpacingCss = settings => `
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

export const isThemeBackgroundMediaElement = element =>
  ['IMG', 'PICTURE', 'VIDEO', 'CANVAS', 'SVG', 'OBJECT', 'EMBED'].includes(element?.tagName) ||
  element?.getAttribute?.('role') === 'img'

export const readerDocumentThemeCss = settings => {
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

export const readerContentCss = settings => {
  const fontSizePercent = readerFontSizePercentValue(settings)
  return `
  ${readerFontFaceCss(settings)}
  ${readerDocumentThemeCss(settings)}
  html {
    --reader-content-font-size: ${fontSizePercent}%;
    font-size: var(--reader-content-font-size, ${fontSizePercent}%) !important;
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

export const normalizeSearchResult = (result, startIndex, view) => {
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

export const normalizeExcerpt = excerpt => {
  if (!excerpt) return undefined
  if (typeof excerpt === 'string') return excerpt
  return [excerpt.pre, excerpt.match, excerpt.post]
    .filter(Boolean)
    .join('')
    .replace(/\s+/g, ' ')
    .trim()
}

export const hrefForCfi = (view, cfi) => {
  if (!view || !cfi) return undefined
  try {
    const parsed = view.resolveCFI?.(cfi)
    const section = view.book?.sections?.[parsed?.index]
    return section?.href
  } catch {
    return undefined
  }
}

export const flattenTocItems = (items, level = 0, path = 'toc') =>
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

export const tocLabel = item => {
  const label = item?.label || item?.title
  if (!label) return undefined
  if (typeof label === 'string') return label.trim() || undefined
  if (typeof label === 'object') {
    const value = Object.values(label).find(value => typeof value === 'string' && value.trim())
    return value?.trim()
  }
  return String(label).trim() || undefined
}
