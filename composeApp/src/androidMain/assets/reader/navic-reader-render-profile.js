import {
  ReaderDirectionDefault,
  ReaderDirectionLtr,
  ReaderDirectionRtl,
  ReaderDocumentThemeStyleId,
  readerDirectionMode,
  readerThemeKey,
  readerThemePalette,
} from './navic-reader-settings.js'
import {
  applyReaderParagraphSpacing,
  isThemeBackgroundMediaElement,
  normalizeReaderInlineTypography,
  normalizeReaderLineFragmentParagraphs,
  readerContentCss,
  readerParagraphSpacingEm,
  readerNormalizeChapterOpeningMargins,
  readerFitOverlongHeadingWords,
} from './navic-reader-typography.js'
import { stableHash } from './navic-reader-identity.js'
import {
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  readerFlowMode,
  readerIllustrationPlacements,
  readerIllustrationsDigest,
} from './navic-reader-settings-core.js'

// Bump when the content render changes pixels for the same settings, so cached rasters retire.
// Mirrored in pageTurnRasterDescriptor (navic-reader-page-turn-preview.js).
export const ReaderContentRenderRevision = 3
const originalPublicationDirections = new WeakMap()
const RasterDecorationSelectors = Object.freeze([
  '[data-navic-surface-paper-texture-layer="true"]',
  '[data-navic-moving-page-paper-texture-layer="true"]',
  '[data-navic-moving-page-border-overlay-layer="true"]',
  '[data-navic-moving-page-stain-overlay-layer="true"]',
  '[data-navic-surface-spread-gutter-overlay-layer="true"]',
])
const loadedRasterAssets = new WeakMap()
const rasterAssetVerificationRequests = new WeakMap()

const setStylesImportant = (element, styles) => {
  if (!element) return
  for (const [property, value] of Object.entries(styles)) {
    element.style.setProperty(property, value, 'important')
  }
}

const ReaderContrastLiftedAttribute = 'data-navic-contrast-lifted'
const ReaderMinimumTextContrast = 2

const readerRelativeLuminance = ([red, green, blue]) => {
  const linear = value => {
    const channel = value / 255
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
}

export const readerParseCssColor = value => {
  const text = String(value || '').trim()
  const hex = /^#([0-9a-f]{6})$/i.exec(text)
  if (hex) {
    const packed = parseInt(hex[1], 16)
    return [(packed >> 16) & 255, (packed >> 8) & 255, packed & 255, 1]
  }
  const rgb = /^rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)(?:\s*[,/]\s*([\d.]+)(%?))?\s*\)$/i.exec(text)
  if (!rgb) return null
  const alpha = rgb[4] === undefined ? 1 : parseFloat(rgb[4]) / (rgb[5] ? 100 : 1)
  return [Number(rgb[1]), Number(rgb[2]), Number(rgb[3]), alpha]
}

export const readerTextContrastRatio = (foreground, background) => {
  const first = readerRelativeLuminance(foreground)
  const second = readerRelativeLuminance(background)
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05)
}

const hasDirectText = element =>
  Array.from(element.childNodes).some(node => node.nodeType === 3 && node.nodeValue.trim())

// Book text colours are kept unless they are unreadable on the theme paper: the theme strips
// every book background, so dark-on-light or light-on-dark book text can vanish (title pages in
// Dark, white-on-box text in Light). Only those elements take the theme foreground; readable
// greys and link colours stay. The original inline colour is restored on every re-apply.
const liftUnreadableTextColours = (doc, palette) => {
  for (const element of doc.querySelectorAll(`[${ReaderContrastLiftedAttribute}]`)) {
    const saved = element.getAttribute(ReaderContrastLiftedAttribute) || ''
    const split = saved.indexOf(':')
    const original = split < 0 ? '' : saved.slice(split + 1)
    if (original) element.style.setProperty('color', original, saved.slice(0, split))
    else element.style.removeProperty('color')
    element.removeAttribute(ReaderContrastLiftedAttribute)
  }
  const view = doc.defaultView
  const background = readerParseCssColor(palette.background)
  if (!view?.getComputedStyle || !background || !doc.body) return 0
  // Read every colour before writing any, so the pass costs a single style recalculation.
  const unreadable = []
  for (const element of doc.body.querySelectorAll('*')) {
    if (isThemeBackgroundMediaElement(element) || element.closest('svg, math')) continue
    if (!hasDirectText(element)) continue
    const colour = readerParseCssColor(view.getComputedStyle(element).color)
    if (!colour || colour[3] === 0) continue
    if (readerTextContrastRatio(colour, background) < ReaderMinimumTextContrast) unreadable.push(element)
  }
  for (const element of unreadable) {
    element.setAttribute(
      ReaderContrastLiftedAttribute,
      `${element.style.getPropertyPriority('color')}:${element.style.getPropertyValue('color')}`
    )
    element.style.setProperty('color', 'var(--reader-foreground)', 'important')
  }
  return unreadable.length
}

const normalizedDirection = value =>
  value === ReaderDirectionLtr || value === ReaderDirectionRtl
    ? value
    : ReaderDirectionDefault

export const applyReaderPublicationRenderDirection = (view, direction) => {
  if (!view?.book) return ReaderDirectionDefault
  const normalized = normalizedDirection(direction)
  if (!originalPublicationDirections.has(view)) {
    originalPublicationDirections.set(view, String(view.book.dir || ''))
  }
  view.book.dir = normalized === ReaderDirectionDefault
    ? originalPublicationDirections.get(view)
    : normalized
  return normalized
}

export const applyReaderDocumentRenderDirection = (doc, direction) => {
  if (!doc) return
  const normalized = normalizedDirection(direction)
  for (const element of [doc.documentElement, doc.body].filter(Boolean)) {
    if (normalized === ReaderDirectionDefault) {
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
      element.setAttribute('dir', normalized)
    }
  }
}

// Chapter illustration plates (Darkaxt/Navic#74): one full-page plate before an illustrated
// chapter. Foliate renders each spine item as its own document starting at column 0, so a plate
// inserted as the first child of the section body is the left page of the opening spread (the
// page before the chapter in one-page layout). Only ebookStart 0 ("plate-before-chapter") is
// supported; anything that cannot be matched exactly to the section fails closed (no plate).
export const ReaderIllustrationAttribute = 'data-navic-illustration'
export const ReaderIllustrationImageAttribute = 'data-navic-illustration-image'
export const ReaderIllustrationSelector = `[${ReaderIllustrationAttribute}]`
export const ReaderIllustrationImageSelector = `img[${ReaderIllustrationImageAttribute}]`

const normalizedIllustrationHref = value => {
  let text = String(value ?? '').trim().split('#')[0]
  try {
    text = decodeURIComponent(text)
  } catch {
    return ''
  }
  return text.replace(/^(?:\.\/|\/)+/, '')
}

// Foliate's section index is compact (spine items without a manifest item are skipped); Bindery
// counts the raw OPF spine like WordSync's exactSectionMapping, so map through the spine and
// require the manifest href to be the section id.
export const readerIllustrationSectionIdentity = (book, sectionIndex) => {
  const index = Number(sectionIndex)
  if (!Number.isSafeInteger(index) || index < 0) return null
  const resources = book?.resources
  if (!resources || !Array.isArray(resources.spine) || typeof resources.getItemByID !== 'function') return null
  if (!Array.isArray(book.sections)) return null
  let compactIndex = 0
  for (let spineIndex = 0; spineIndex < resources.spine.length; spineIndex += 1) {
    const item = resources.getItemByID(resources.spine[spineIndex]?.idref)
    if (!item) continue
    if (compactIndex === index) {
      const section = book.sections[compactIndex]
      if (!section || section.id !== item.href) return null
      if (section.href != null && section.href !== item.href) return null
      const href = normalizedIllustrationHref(item.href)
      return href ? Object.freeze({ spineIndex, href }) : null
    }
    compactIndex += 1
  }
  return null
}

const positiveInteger = value => Number.isSafeInteger(value) && value > 0

export const readerIllustrationPlacementForSection = (settings, identity) => {
  if (!identity) return null
  return readerIllustrationPlacements(settings).find(placement =>
    placement && typeof placement === 'object' &&
    placement.spineIndex === identity.spineIndex &&
    placement.ebookStart === 0 &&
    normalizedIllustrationHref(placement.ebookHref) === identity.href &&
    typeof placement.sceneId === 'string' && placement.sceneId.trim() !== '' &&
    typeof placement.imageUrl === 'string' && placement.imageUrl.trim() !== '' &&
    positiveInteger(placement.width) && positiveInteger(placement.height)
  ) || null
}

const readerIllustrationPlateHeight = settings => {
  const flow = readerFlowMode(settings)
  if (flow !== ReaderFlowScrolled && flow !== ReaderFlowScrolledGaps) return '100vh'
  // Scrolled documents grow with their content, so their 100vh is not a screen; use the host
  // viewport for a plate block one screen tall.
  const height = Math.round(Number(globalThis.innerHeight) || 0)
  return height > 0 ? `${height}px` : '100vh'
}

const readerIllustrationPlateMatches = (figure, placement, height) => {
  const image = figure?.firstElementChild
  return figure.getAttribute(ReaderIllustrationAttribute) === placement.sceneId &&
    figure.childNodes.length === 1 &&
    image?.hasAttribute?.(ReaderIllustrationImageAttribute) === true &&
    image.getAttribute('src') === placement.imageUrl &&
    image.getAttribute('width') === String(placement.width) &&
    image.getAttribute('height') === String(placement.height) &&
    figure.style.getPropertyValue('height') === height
}

export const applyReaderIllustrationPlate = (doc, settings = {}, sectionIndex = undefined, book = undefined) => {
  const body = doc?.body
  if (!body) return null
  const placement = readerIllustrationPlacementForSection(
    settings,
    readerIllustrationSectionIdentity(book, sectionIndex),
  )
  const existing = Array.from(body.querySelectorAll(ReaderIllustrationSelector))
  const height = placement ? readerIllustrationPlateHeight(settings) : ''
  if (
    placement && existing.length === 1 && existing[0] === body.firstChild &&
    readerIllustrationPlateMatches(existing[0], placement, height)
  ) return existing[0]
  for (const element of existing) element.remove()
  if (!placement) return null
  const figure = doc.createElement('figure')
  figure.setAttribute(ReaderIllustrationAttribute, placement.sceneId)
  figure.setAttribute('aria-hidden', 'true')
  figure.style.setProperty('height', height, 'important')
  const image = doc.createElement('img')
  image.setAttribute(ReaderIllustrationImageAttribute, '')
  image.setAttribute('src', placement.imageUrl)
  image.setAttribute('width', String(placement.width))
  image.setAttribute('height', String(placement.height))
  image.setAttribute('alt', '')
  image.setAttribute('decoding', 'sync')
  figure.append(image)
  body.insertBefore(figure, body.firstChild)
  return figure
}

const ReaderIllustrationEdgeMask =
  'linear-gradient(to right, transparent, #000 7%, #000 93%, transparent), ' +
  'linear-gradient(to bottom, transparent, #000 7%, #000 93%, transparent)'

// One plate fills exactly one column/page: the paginator sizes every column to the section frame
// height (the figure's inline height, readerIllustrationPlateHeight) and the forced break starts
// the chapter on the next page. The paginator skips the plate in its own image sizing (vendor
// paginator.js setImageSize, which would cap it at the page minus twice its margin), so the image
// box is the contained image itself: never cropped, as large as the page allows, and its edges
// fade into the theme paper.
export const readerIllustrationCss = () => `
  html body figure${ReaderIllustrationSelector} {
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
    box-sizing: border-box !important;
    width: 100% !important;
    min-height: 0 !important;
    max-height: none !important;
    margin: 0 !important;
    padding: 0 !important;
    border: 0 !important;
    overflow: hidden !important;
    background: transparent !important;
    text-indent: 0 !important;
    break-inside: avoid !important;
    page-break-inside: avoid !important;
    -webkit-column-break-inside: avoid !important;
    break-after: column !important;
    page-break-after: always !important;
    -webkit-column-break-after: always !important;
    pointer-events: none !important;
    user-select: none !important;
    -webkit-user-select: none !important;
  }
  html body figure${ReaderIllustrationSelector} > ${ReaderIllustrationImageSelector} {
    display: block !important;
    width: auto !important;
    height: auto !important;
    max-width: 100% !important;
    max-height: 100% !important;
    margin: auto !important;
    object-fit: contain !important;
    object-position: center !important;
    background: transparent !important;
    -webkit-mask-image: ${ReaderIllustrationEdgeMask};
    -webkit-mask-composite: source-in;
    -webkit-mask-repeat: no-repeat;
    -webkit-mask-size: 100% 100%;
    mask-image: ${ReaderIllustrationEdgeMask};
    mask-composite: intersect;
    mask-repeat: no-repeat;
    mask-size: 100% 100%;
  }
`

export const applyReaderContentDocumentRenderProfile = (doc, settings = {}, sectionIndex = undefined, book = undefined) => {
  if (!doc?.documentElement) return false
  const palette = readerThemePalette(settings.theme)
  const root = doc.documentElement
  const body = doc.body
  const styleHost = doc.head || root
  applyReaderDocumentRenderDirection(doc, readerDirectionMode(settings))
  applyReaderIllustrationPlate(doc, settings, sectionIndex, book)
  normalizeReaderLineFragmentParagraphs(doc, settings)
  applyReaderParagraphSpacing(doc, settings)
  root.dataset.navicReaderTheme = readerThemeKey(settings.theme)
  delete root.dataset.navicPaperTextureKey
  delete root.dataset.navicPaperTextureAsset
  let themeStyle = doc.getElementById(ReaderDocumentThemeStyleId)
  if (!themeStyle) {
    themeStyle = doc.createElement('style')
    themeStyle.id = ReaderDocumentThemeStyleId
    styleHost.append(themeStyle)
  }
  themeStyle.textContent = `${readerContentCss(settings)}\n${readerIllustrationCss()}`
  normalizeReaderInlineTypography(doc, settings)
  readerNormalizeChapterOpeningMargins(doc, settings)
  readerFitOverlongHeadingWords(doc)
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
    if (!element || element === root || element === body || isThemeBackgroundMediaElement(element)) {
      continue
    }
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
    setStylesImportant(element, { 'background-color': 'transparent' })
  }
  liftUnreadableTextColours(doc, palette)
  return true
}

const roundedRect = rect => Object.freeze({
  left: Math.round(Number(rect?.left) || 0),
  top: Math.round(Number(rect?.top) || 0),
  width: Math.round(Number(rect?.width) || 0),
  height: Math.round(Number(rect?.height) || 0),
})

const roundedSize = rect => Object.freeze({
  width: Math.round(Number(rect?.width) || 0),
  height: Math.round(Number(rect?.height) || 0),
})

const styleProof = (style, properties) => Object.freeze(Object.fromEntries(
  properties.map(property => [property, String(style?.getPropertyValue(property) || '')]),
))

const urlsFromCssValue = value => Array.from(
  String(value || '').matchAll(/url\(["']?([^"')]+)["']?\)/g),
  match => new URL(match[1], document.baseURI).href,
)

const RasterDecorationStyleProperties = Object.freeze([
  'display',
  'visibility',
  'opacity',
  'background-color',
  'background-image',
  'background-position',
  'background-size',
  'box-shadow',
  'mix-blend-mode',
  'transform',
])

const rasterDecorationNodeProof = element => {
  const style = getComputedStyle(element)
  return Object.freeze({
    tagName: element.tagName,
    className: String(element.className || ''),
    rect: roundedRect(element.getBoundingClientRect()),
    style: styleProof(style, RasterDecorationStyleProperties),
  })
}

const rasterDecorationProof = (root = document) => RasterDecorationSelectors.map(selector => {
  const element = root.querySelector(selector)
  if (!element) return Object.freeze({ selector, present: false })
  return Object.freeze({
    selector,
    present: true,
    nodes: Object.freeze([
      element,
      ...element.querySelectorAll('*'),
    ].map(rasterDecorationNodeProof)),
  })
})

export const readerRasterDecorationAssetUrls = (root = document) => Array.from(new Set(
  rasterDecorationProof(root).flatMap(layer => layer.present
    ? layer.nodes.flatMap(node => urlsFromCssValue(node.style['background-image']))
    : [],
  ),
)).sort()

const loadRasterImage = url => new Promise(resolve => {
  const image = new Image()
  image.onload = () => resolve(true)
  image.onerror = () => resolve(false)
  image.src = url
})

const rasterAssetUrlsEqual = (left, right) =>
  left.length === right.length && left.every((url, index) => right[index] === url)

export const waitForReaderRasterAssets = async (root = document) => {
  const urls = readerRasterDecorationAssetUrls(root)
  const request = Object.freeze({ urls: Object.freeze(urls) })
  rasterAssetVerificationRequests.set(root, request)
  const results = await Promise.all(urls.map(loadRasterImage))
  if (rasterAssetVerificationRequests.get(root) !== request) return false
  if (!rasterAssetUrlsEqual(readerRasterDecorationAssetUrls(root), urls)) return false
  if (results.some(loaded => !loaded)) {
    loadedRasterAssets.delete(root)
    return false
  }
  loadedRasterAssets.set(root, request.urls)
  return true
}

const transformedDocumentProof = doc => {
  const root = doc?.documentElement
  const body = doc?.body
  const win = doc?.defaultView
  if (!root || !body || !win) return null
  const rootStyle = win.getComputedStyle(root)
  const bodyStyle = win.getComputedStyle(body)
  const firstParagraph = doc.querySelector('p,[data-navic-paragraph-block="true"]')
  const paragraphStyle = firstParagraph ? win.getComputedStyle(firstParagraph) : null
  const heading = doc.querySelector('h1,h2,h3,h4,h5,h6')
  const inlineNormalized = doc.querySelector('[data-navic-inline-typography-normalized="true"]')
  return Object.freeze({
    rootDirection: root.getAttribute('dir') || '',
    bodyDirection: body.getAttribute('dir') || '',
    theme: root.dataset.navicReaderTheme || '',
    lineFragmentsNormalized: root.dataset.navicLineFragmentsNormalized === 'true',
    looseParagraphCount: doc.querySelectorAll('[data-navic-loose-text-paragraph="true"]').length,
    paragraphBlockCount: doc.querySelectorAll('[data-navic-paragraph-block="true"]').length,
    inlineTypographyNormalized: inlineNormalized != null,
    chapterOpeningCapped:
      heading?.getAttribute('data-navic-chapter-opening-margin-capped') === 'true',
    rootSize: roundedSize(root.getBoundingClientRect()),
    bodySize: roundedSize(body.getBoundingClientRect()),
    rootStyle: styleProof(rootStyle, [
      'direction',
      'font-family',
      'font-size',
      'line-height',
      'color',
      'background-color',
    ]),
    bodyStyle: styleProof(bodyStyle, [
      'direction',
      'font-family',
      'font-size',
      'line-height',
      'color',
      'background-color',
    ]),
    paragraphStyle: styleProof(paragraphStyle, [
      'display',
      'font-family',
      'font-size',
      'line-height',
      'margin-top',
      'margin-bottom',
    ]),
    chapterOpeningMargin: heading?.style?.getPropertyValue('margin-top') || '',
  })
}

export const ReaderRasterProfileAuthorityLiveRealized = 'live-realized-v1'
export const ReaderRasterProfileAuthorityPassiveRealized = 'passive-realized-v1'

export const readerLiveIssuedRasterPlan = target => {
  const publicationUrl = String(target?.publicationUrl || '')
  const paginationFingerprint = String(target?.paginationFingerprint || '')
  const viewportWidth = Math.max(1, Math.round(Number(target?.viewportWidth) || 1))
  const viewportHeight = Math.max(1, Math.round(Number(target?.viewportHeight) || 1))
  if (!publicationUrl || !paginationFingerprint) return null
  const render = target?.render && typeof target.render === 'object' ? target.render : null
  const settings = target?.readerSettings && typeof target.readerSettings === 'object'
    ? target.readerSettings
    : {}
  const layoutFingerprint = stableHash(JSON.stringify({
    render,
    mode: target?.layoutMode || 'single',
    pages: Array.isArray(target?.layoutPages) ? target.layoutPages : [],
    viewportWidth,
    viewportHeight,
  }))
  const decorationFingerprint = stableHash(JSON.stringify({
    theme: settings.theme || '',
    paperTextureEnabled: settings.paperTextureEnabled !== false,
    pageEdgesEnabled: settings.pageEdgesEnabled !== false,
    paperStainsEnabled: settings.paperStainsEnabled !== false,
    coverBackdropEnabled: settings.coverBackdropEnabled !== false,
    illustrationsDigest: readerIllustrationsDigest(settings),
    contentRenderRevision: ReaderContentRenderRevision,
  }))
  const rasterProfileKey = stableHash(JSON.stringify({
    publicationUrl,
    paginationFingerprint,
    layoutFingerprint,
    decorationFingerprint,
    viewportWidth,
    viewportHeight,
  }))
  return Object.freeze({
    profileAuthority: ReaderRasterProfileAuthorityPassiveRealized,
    rasterProfileKey,
    paginationFingerprint,
    layoutFingerprint,
    decorationFingerprint,
  })
}

// Plates must be settled before a realized raster is trusted: loaded and decoded, or failed
// (a broken image settles as failed and is reported, so it cannot block capture forever).
export const readerIllustrationAssetObservation = renderer => {
  const assets = []
  for (const content of renderer?.getContents?.() || []) {
    for (const image of content?.doc?.querySelectorAll?.(ReaderIllustrationImageSelector) || []) {
      if (!image.complete) return null
      assets.push(Object.freeze({
        sectionIndex: Number(content.index),
        url: String(image.currentSrc || image.src || ''),
        loaded: Number(image.naturalWidth) > 0,
      }))
    }
  }
  assets.sort((left, right) =>
    left.sectionIndex - right.sectionIndex || (left.url < right.url ? -1 : left.url > right.url ? 1 : 0))
  return Object.freeze(assets)
}

export const readerRealizedRasterObservation = (view, target, root = document) => {
  const renderer = view?.renderer
  const index = Math.floor(Number(target?.spineIndex))
  const content = renderer?.getContents?.().find(entry => entry.index === index)
  const doc = content?.doc
  if (!renderer || !Number.isSafeInteger(index) || index < 0 || !doc?.documentElement ||
      (doc.fonts && doc.fonts.status !== 'loaded')) return null
  const documentProof = transformedDocumentProof(doc)
  if (!documentProof) return null
  const viewRect = view.getBoundingClientRect()
  const density = window.devicePixelRatio || 1
  const viewportWidth = Math.round(viewRect.width * density)
  const viewportHeight = Math.round(viewRect.height * density)
  if (viewportWidth <= 0 || viewportHeight <= 0) return null
  const decorationProof = rasterDecorationProof(root)
  const requiredAssetUrls = readerRasterDecorationAssetUrls(root)
  const loadedAssetUrls = loadedRasterAssets.get(root) || Object.freeze([])
  if (
    requiredAssetUrls.length !== loadedAssetUrls.length ||
    requiredAssetUrls.some((url, index) => loadedAssetUrls[index] !== url)
  ) return null
  const illustrationAssets = readerIllustrationAssetObservation(renderer)
  if (!illustrationAssets) return null
  const rendererProof = Object.freeze({
    publicationDirection: String(view.book?.dir || ''),
    sectionCount: Number(view.book?.sections?.length) || 0,
    flow: renderer.getAttribute('flow') || '',
    maxInlineSize: renderer.getAttribute('max-inline-size') || '',
    maxBlockSize: renderer.getAttribute('max-block-size') || '',
    maxColumnCount: renderer.getAttribute('max-column-count') || '',
    columnThreshold: renderer.getAttribute('column-threshold') || '',
    gap: renderer.getAttribute('gap') || '',
    contentGap: renderer.getAttribute('content-gap') || '',
    topMargin: renderer.getAttribute('top-margin') || '',
    bottomMargin: renderer.getAttribute('bottom-margin') || '',
    viewportWidth,
    viewportHeight,
  })
  const paginationFingerprint = stableHash(JSON.stringify(rendererProof))
  const layoutFingerprint = stableHash(JSON.stringify({ rendererProof, documentProof }))
  const decorationFingerprint = stableHash(JSON.stringify({
    layers: decorationProof,
    loadedAssetUrls,
    illustrationAssets,
  }))
  const rasterProfileKey = stableHash(JSON.stringify({
    publicationUrl: String(target.publicationUrl || ''),
    paginationFingerprint,
    layoutFingerprint,
    decorationFingerprint,
    viewportWidth,
    viewportHeight,
  }))
  return Object.freeze({
    rasterProfileKey,
    paginationFingerprint,
    layoutFingerprint,
    decorationFingerprint,
    viewportAndCaptureGeometry: Object.freeze({
      viewportWidth,
      viewportHeight,
      captureLeft: 0,
      captureTop: 0,
      captureRight: viewportWidth,
      captureBottom: viewportHeight,
    }),
    loadedAssetUrls,
    illustrationAssets,
    rendererProof,
    documentProof,
    decorationProof,
  })
}
