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
  readerCentreSpacePaddedNumerals,
} from './navic-reader-typography.js'
import { stableHash } from './navic-reader-identity.js'
import { readerExtractedByteWordStartPoint } from './navic-reader-wordsync-provenance.js'
import {
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  readerFlowMode,
  readerIllustrationPlacements,
  readerIllustrationsDigest,
} from './navic-reader-settings-core.js'

// Bump when the content render changes pixels for the same settings, so cached rasters retire.
// Mirrored in pageTurnRasterDescriptor (navic-reader-page-turn-preview.js).
export const ReaderContentRenderRevision = 10
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
// page before the chapter in one-page layout). "plate-before-chapter" sits at ebookStart 0;
// "plate-before-block" is a full-page plate mid-chapter, before the block holding ebookStart.
// Anything that cannot be matched exactly to the section or its text fails closed (no plate).
export const ReaderIllustrationAttribute = 'data-navic-illustration'
export const ReaderIllustrationImageAttribute = 'data-navic-illustration-image'
export const ReaderIllustrationSelector = `[${ReaderIllustrationAttribute}]`
export const ReaderIllustrationImageSelector = `img[${ReaderIllustrationImageAttribute}]`
export const ReaderLivingPlateGreyAttribute = 'data-navic-living-grey'

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

const ReaderIllustrationPositionChapter = 'plate-before-chapter'
const ReaderIllustrationPositionBlock = 'plate-before-block'
// Document figures (#82): a small drawing of an in-story note, letter or sign, in place of the
// blocks that print it. The blocks stay in the DOM (hidden), so WordSync and search offsets hold;
// a tap overlays their text inside the figure's own box, so the page never reflows.
const ReaderIllustrationPositionFigure = 'figure-replaces-blocks'
export const ReaderDocumentFigureAttribute = 'data-navic-document-figure'
export const ReaderDocumentFigureImageAttribute = 'data-navic-document-figure-image'
export const ReaderDocumentFigureHiddenAttribute = 'data-navic-figure-hidden'
export const ReaderDocumentFigureRevealedAttribute = 'data-navic-figure-revealed'
export const ReaderDocumentFigureTextAttribute = 'data-navic-figure-text'
export const ReaderIllustrationBlockAttribute = 'data-navic-illustration-block'
export const ReaderIllustrationSpacerAttribute = 'data-navic-illustration-spacer'

const readerIllustrationPlacementKind = placement => {
  const position = placement.position ?? ReaderIllustrationPositionChapter
  if (position === ReaderIllustrationPositionChapter) return placement.ebookStart === 0 ? position : null
  if (position === ReaderIllustrationPositionBlock) {
    return Number.isSafeInteger(placement.ebookStart) && placement.ebookStart >= 0 ? position : null
  }
  if (position === ReaderIllustrationPositionFigure) {
    return Number.isSafeInteger(placement.ebookStart) && placement.ebookStart >= 0 &&
      Number.isSafeInteger(placement.endBlock) && placement.endBlock >= placement.ebookStart ? position : null
  }
  return null
}

// Chapter plates first (byte 0), then mid-chapter plates in reading order.
export const readerIllustrationPlacementsForSection = (settings, identity) => {
  if (!identity) return []
  return readerIllustrationPlacements(settings).filter(placement =>
    placement && typeof placement === 'object' &&
    placement.spineIndex === identity.spineIndex &&
    readerIllustrationPlacementKind(placement) !== null &&
    normalizedIllustrationHref(placement.ebookHref) === identity.href &&
    typeof placement.sceneId === 'string' && placement.sceneId.trim() !== '' &&
    typeof placement.imageUrl === 'string' && placement.imageUrl.trim() !== '' &&
    positiveInteger(placement.width) && positiveInteger(placement.height)
  ).sort((left, right) => left.ebookStart - right.ebookStart)
}

const readerIllustrationPlateHeight = settings => {
  const flow = readerFlowMode(settings)
  if (flow !== ReaderFlowScrolled && flow !== ReaderFlowScrolledGaps) return '100vh'
  // Scrolled documents grow with their content, so their 100vh is not a screen; use the host
  // viewport for a plate block one screen tall.
  const height = Math.round(Number(globalThis.innerHeight) || 0)
  return height > 0 ? `${height}px` : '100vh'
}

const readerDocumentFigureMatches = (figure, placement) => {
  const image = figure?.firstElementChild
  return figure.localName === 'figure' &&
    figure.getAttribute(ReaderIllustrationAttribute) === placement.sceneId &&
    figure.hasAttribute(ReaderDocumentFigureAttribute) &&
    figure.childNodes.length === 1 &&
    image?.hasAttribute?.(ReaderDocumentFigureImageAttribute) === true &&
    image.getAttribute('src') === placement.imageUrl
}

const readerIllustrationPlateMatches = (figure, placement, height) => {
  if (readerIllustrationPlacementKind(placement) === ReaderIllustrationPositionFigure) {
    return readerDocumentFigureMatches(figure, placement)
  }
  const image = figure?.firstElementChild
  return figure.localName === 'figure' &&
    figure.getAttribute(ReaderIllustrationAttribute) === placement.sceneId &&
    figure.hasAttribute(ReaderIllustrationBlockAttribute) ===
      (readerIllustrationPlacementKind(placement) === ReaderIllustrationPositionBlock) &&
    figure.childNodes.length === 1 &&
    image?.hasAttribute?.(ReaderIllustrationImageAttribute) === true &&
    image.getAttribute('src') === placement.imageUrl &&
    image.getAttribute('width') === String(placement.width) &&
    image.getAttribute('height') === String(placement.height) &&
    figure.style.getPropertyValue('height') === height
}

const ReaderIllustrationAnchorBlockSelector =
  'p, h1, h2, h3, h4, h5, h6, blockquote, li, dt, dd, pre, table, figure, div, section, article, header, aside'

// The block a mid-chapter plate goes before: the innermost block holding the anchor's first
// letter, or the body child holding it when the text sits directly in the body.
const readerIllustrationAnchorBlock = (doc, placement) => {
  const point = readerExtractedByteWordStartPoint(doc, placement.ebookStart)
  const body = doc.body
  if (!point || !body?.contains(point.node)) return null
  const block = point.node.parentElement?.closest?.(ReaderIllustrationAnchorBlockSelector)
  if (block && block !== body && body.contains(block)) return block
  let node = point.node
  while (node.parentNode && node.parentNode !== body) node = node.parentNode
  return node.parentNode === body ? node : null
}

const createReaderIllustrationPlate = (doc, placement, height, block) => {
  const figure = doc.createElement('figure')
  figure.setAttribute(ReaderIllustrationAttribute, placement.sceneId)
  if (block) figure.setAttribute(ReaderIllustrationBlockAttribute, '')
  figure.setAttribute('aria-hidden', 'true')
  figure.style.setProperty('height', height, 'important')
  const image = doc.createElement('img')
  image.setAttribute(ReaderIllustrationImageAttribute, '')
  image.setAttribute('src', placement.imageUrl)
  image.setAttribute('width', String(placement.width))
  image.setAttribute('height', String(placement.height))
  image.setAttribute('alt', '')
  image.setAttribute('decoding', 'sync')
  // Living plates (#77): the native overlay finds the loop of the plate on screen by these.
  if (placement.sha256) image.dataset.navicSha256 = placement.sha256
  if (placement.loopSha256) image.dataset.navicLivingLoop = placement.loopSha256
  if (placement.revealSha256) image.dataset.navicLivingReveal = placement.revealSha256
  if (placement.advancedLoopSha256) image.dataset.navicLivingAdvanced = placement.advancedLoopSha256
  figure.append(image)
  return figure
}

// A mid-chapter plate brings a hidden blank page. The paginator shows it (vendor paginator.js
// #alignNavicIllustrationPlates) when the plate would fall on the right page of a two-page spread,
// so the plate faces its text. Toggling an attribute, not inserting nodes, keeps WordSync's DOM
// points valid after mapping.
const createReaderIllustrationSpacer = (doc, placement, height) => {
  const spacer = doc.createElement('div')
  spacer.setAttribute(ReaderIllustrationAttribute, placement.sceneId)
  spacer.setAttribute(ReaderIllustrationSpacerAttribute, 'off')
  spacer.setAttribute('aria-hidden', 'true')
  spacer.style.setProperty('height', height, 'important')
  return spacer
}

// The blocks a document figure replaces: from the block holding ebookStart to the one holding
// endBlock, lifted to siblings of one parent with only whitespace between them. Anything else
// (an offset off a word start, a plate in between, stray text) places nothing.
const readerDocumentFigureBlocks = (doc, placement) => {
  const first = readerIllustrationAnchorBlock(doc, placement)
  const last = readerIllustrationAnchorBlock(doc, { ebookStart: placement.endBlock })
  const body = doc.body
  if (!first || !last) return null
  let start = first
  while (start.parentNode && start.parentNode !== body && !start.parentNode.contains(last)) start = start.parentNode
  const parent = start.parentNode
  if (!parent?.contains(last)) return null
  let end = last
  while (end.parentNode && end.parentNode !== parent) end = end.parentNode
  if (end.parentNode !== parent) return null
  const blocks = []
  for (let node = start; node; node = node.nextSibling) {
    if (node.nodeType === 1) {
      if (node.hasAttribute(ReaderIllustrationAttribute) || node.hasAttribute(ReaderDocumentFigureHiddenAttribute)) return null
      blocks.push(node)
    } else if ((node.nodeType === 3 || node.nodeType === 4) && node.textContent.trim() !== '') {
      return null
    }
    if (node === end) return blocks
  }
  return null
}

const createReaderDocumentFigure = (doc, placement) => {
  const figure = doc.createElement('figure')
  figure.setAttribute(ReaderIllustrationAttribute, placement.sceneId)
  figure.setAttribute(ReaderDocumentFigureAttribute, '')
  figure.setAttribute('aria-hidden', 'true')
  const image = doc.createElement('img')
  image.setAttribute(ReaderDocumentFigureImageAttribute, '')
  image.setAttribute('src', placement.imageUrl)
  image.setAttribute('width', String(placement.width))
  image.setAttribute('height', String(placement.height))
  image.setAttribute('alt', '')
  image.setAttribute('decoding', 'sync')
  figure.append(image)
  return figure
}

const readerDocumentFigureBySceneId = (doc, sceneId) =>
  Array.from(doc?.body?.querySelectorAll?.(`figure[${ReaderDocumentFigureAttribute}]`) || [])
    .find(figure => figure.getAttribute(ReaderIllustrationAttribute) === sceneId) || null

const closeReaderDocumentFigure = figure => {
  for (const text of Array.from(figure.querySelectorAll(`[${ReaderDocumentFigureTextAttribute}]`))) text.remove()
  figure.removeAttribute(ReaderDocumentFigureRevealedAttribute)
}

// A tap on a document figure shows the text it replaces over the figure (copies, so the hidden
// originals keep their offsets); another tap brings the figure back. Returns whether it changed.
export const toggleReaderDocumentFigure = (doc, sceneId) => {
  const figure = readerDocumentFigureBySceneId(doc, sceneId)
  if (!figure) return false
  if (figure.hasAttribute(ReaderDocumentFigureRevealedAttribute)) {
    closeReaderDocumentFigure(figure)
    return true
  }
  const text = doc.createElement('div')
  text.setAttribute(ReaderDocumentFigureTextAttribute, '')
  for (let node = figure.nextSibling; node; node = node.nextSibling) {
    if (node.nodeType !== 1) continue
    if (!node.hasAttribute(ReaderDocumentFigureHiddenAttribute)) break
    const copy = node.cloneNode(true)
    copy.removeAttribute(ReaderDocumentFigureHiddenAttribute)
    copy.style.removeProperty('display')
    copy.removeAttribute('id')
    for (const element of copy.querySelectorAll('[id]')) element.removeAttribute('id')
    text.append(copy)
  }
  if (!text.childNodes.length) return false
  figure.append(text)
  figure.setAttribute(ReaderDocumentFigureRevealedAttribute, '')
  return true
}

export const closeReaderDocumentFigures = doc => {
  const revealed = Array.from(doc?.body?.querySelectorAll?.(`figure[${ReaderDocumentFigureRevealedAttribute}]`) || [])
  for (const figure of revealed) closeReaderDocumentFigure(figure)
  return revealed.length
}

// Plates are mapped once against the publication's own text; a re-theme keeps matching plates in
// place instead of re-mapping (later typography normalisation may restructure paragraphs). A
// mid-chapter offset that does not land on the start of a word places nothing.
export const applyReaderIllustrationPlate = (doc, settings = {}, sectionIndex = undefined, book = undefined) => {
  const body = doc?.body
  if (!body) return null
  const placements = readerIllustrationPlacementsForSection(
    settings,
    readerIllustrationSectionIdentity(book, sectionIndex),
  )
  const existing = Array.from(body.querySelectorAll(ReaderIllustrationSelector))
  const figures = existing.filter(element => !element.hasAttribute(ReaderIllustrationSpacerAttribute))
  const height = placements.length ? readerIllustrationPlateHeight(settings) : ''
  if (
    placements.length &&
    figures.every(figure => placements.some(placement => readerIllustrationPlateMatches(figure, placement, height))) &&
    (readerIllustrationPlacementKind(placements[0]) !== ReaderIllustrationPositionChapter ||
      figures[0] === body.firstChild) &&
    body.dataset.navicIllustrationsMapped === readerIllustrationsDigest(settings)
  ) {
    syncReaderLivingPlateGrey(figures, placements)
    return figures[0] || null
  }
  for (const element of existing) element.remove()
  for (const hidden of Array.from(body.querySelectorAll(`[${ReaderDocumentFigureHiddenAttribute}]`))) {
    hidden.removeAttribute(ReaderDocumentFigureHiddenAttribute)
  }
  delete body.dataset.navicIllustrationsMapped
  if (!placements.length) return null
  const inserted = []
  for (const placement of placements) {
    if (readerIllustrationPlacementKind(placement) === ReaderIllustrationPositionChapter) {
      const figure = createReaderIllustrationPlate(doc, placement, height, false)
      body.insertBefore(figure, body.firstChild)
      inserted.push(figure)
      continue
    }
    if (readerIllustrationPlacementKind(placement) === ReaderIllustrationPositionFigure) {
      const blocks = readerDocumentFigureBlocks(doc, placement)
      if (!blocks?.length) continue
      const figure = createReaderDocumentFigure(doc, placement)
      blocks[0].parentNode.insertBefore(figure, blocks[0])
      for (const block of blocks) block.setAttribute(ReaderDocumentFigureHiddenAttribute, '')
      inserted.push(figure)
      continue
    }
    const block = readerIllustrationAnchorBlock(doc, placement)
    if (!block) continue
    const figure = createReaderIllustrationPlate(doc, placement, height, true)
    block.parentNode.insertBefore(createReaderIllustrationSpacer(doc, placement, height), block)
    block.parentNode.insertBefore(figure, block)
    inserted.push(figure)
  }
  body.dataset.navicIllustrationsMapped = readerIllustrationsDigest(settings)
  syncReaderLivingPlateGrey(inserted, placements)
  return inserted[0] || null
}

// Mirrored by pageTurnRasterDescriptor (navic-reader-page-turn-preview.js).
export const readerLivingPlateGreyKey = settings =>
  readerIllustrationPlacements(settings)
    .filter(placement => placement.livingPlayable === true)
    .map(placement => placement.sceneId)
    .join(',')

// A plate the living overlay will colour in (#77) is drawn greyscale on the page, so the page
// rasters carry it grey through the turn and the overlay's reveal starts from what is on screen.
const syncReaderLivingPlateGrey = (figures, placements) => {
  for (const figure of figures) {
    const image = figure.querySelector(ReaderIllustrationImageSelector)
    if (!image) continue
    const sceneId = figure.getAttribute(ReaderIllustrationAttribute)
    const playable = placements.some(placement => placement.sceneId === sceneId && placement.livingPlayable === true)
    if (playable) image.setAttribute(ReaderLivingPlateGreyAttribute, '')
    else image.removeAttribute(ReaderLivingPlateGreyAttribute)
  }
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
  html body figure${ReaderIllustrationSelector} > ${ReaderIllustrationImageSelector}[${ReaderLivingPlateGreyAttribute}] {
    filter: grayscale(1) !important;
  }
  html body figure${ReaderIllustrationSelector}[${ReaderIllustrationBlockAttribute}] {
    break-before: column !important;
    page-break-before: always !important;
    -webkit-column-break-before: always !important;
  }
  html body [${ReaderIllustrationSpacerAttribute}] {
    display: none !important;
  }
  html body figure${ReaderIllustrationSelector}[${ReaderDocumentFigureAttribute}] {
    position: relative !important;
    display: block !important;
    height: auto !important;
    margin: 0.8em 0 !important;
    break-after: auto !important;
    page-break-after: auto !important;
    -webkit-column-break-after: auto !important;
  }
  html body figure${ReaderIllustrationSelector}[${ReaderDocumentFigureAttribute}] > img[${ReaderDocumentFigureImageAttribute}] {
    display: block !important;
    width: 100% !important;
    height: auto !important;
    max-width: 100% !important;
    margin: 0 auto !important;
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
  html body figure${ReaderIllustrationSelector}[${ReaderDocumentFigureRevealedAttribute}] > img[${ReaderDocumentFigureImageAttribute}] {
    visibility: hidden !important;
  }
  html body figure${ReaderIllustrationSelector}[${ReaderDocumentFigureAttribute}] > [${ReaderDocumentFigureTextAttribute}] {
    position: absolute !important;
    inset: 0 !important;
    box-sizing: border-box !important;
    display: flex !important;
    flex-direction: column !important;
    justify-content: center !important;
    overflow: hidden !important;
    margin: 0 !important;
    padding: 0 !important;
  }
  html body [${ReaderDocumentFigureHiddenAttribute}] {
    display: none !important;
  }
  html body [${ReaderIllustrationSpacerAttribute}="on"] {
    display: block !important;
    box-sizing: border-box !important;
    width: 100% !important;
    margin: 0 !important;
    padding: 0 !important;
    border: 0 !important;
    break-before: column !important;
    page-break-before: always !important;
    -webkit-column-break-before: always !important;
    break-inside: avoid !important;
    page-break-inside: avoid !important;
    -webkit-column-break-inside: avoid !important;
    pointer-events: none !important;
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
  readerCentreSpacePaddedNumerals(doc)
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
    livingPlateGrey: readerLivingPlateGreyKey(settings),
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
