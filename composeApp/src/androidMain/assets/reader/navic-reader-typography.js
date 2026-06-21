import {
  ReaderFlowPagedVertical,
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  ReaderFontSourceCustom,
  ReaderFontSourceNavic,
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
  overlayClass,
} from './navic-reader-bridge-core.js'
import {
  readerMediaSelector,
} from './navic-reader-media.js'

const readerAssetUrl = path => new URL(path, document.baseURI).href

const clampNumber = (value, min, max) => Math.min(max, Math.max(min, value))

const readerStyleNumber = (settings, key, fallback, min, max) => {
  const value = Number(settings?.[key])
  return Number.isFinite(value) ? clampNumber(value, min, max) : fallback
}

const setStylesImportant = (element, styles) => {
  if (!element) return
  for (const [property, value] of Object.entries(styles)) {
    element.style.setProperty(property, value, 'important')
  }
}

const classifyReaderParagraphBlocks = doc => {
  const candidates = Array.from(doc?.body?.querySelectorAll?.('div') || [])
  for (const node of candidates) {
    if (node.children.length > 0) continue
    const text = String(node.textContent || '').trim()
    if (!text) continue
    const sentenceLike = /[.!?]["')\]]?$/.test(text) || text.split(/\s+/).length >= 8
    if (!sentenceLike) continue
    node.dataset.navicParagraphBlock = 'true'
    setStylesImportant(node, {
      'display': 'block',
    })
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

const readerInlineTypographyBlockTags = new Set([
  'BODY',
  'P',
  'LI',
  'BLOCKQUOTE',
  'DD',
  'TD',
  'TH',
  'MAIN',
  'SECTION',
  'ARTICLE',
  'CENTER',
  'DIV',
  'PRE',
])

const readerInlineTypographyTextTags = new Set([
  ...readerInlineTypographyBlockTags,
  'SPAN',
  'FONT',
  'CODE',
  'SAMP',
  'KBD',
  'A',
])

const readerInlineTypographyCandidateSelector = [
  '[style*="font-size"]',
  '[style*="font:"]',
  '[style*="font "]',
  'font[size]',
].join(',')

const readerElementHasInlineFontSize = element => {
  const style = element?.style
  if (!style) return false
  if (String(element.getAttribute?.('size') || '').trim()) return true
  const value = style.getPropertyValue('font-size')
  const priority = style.getPropertyPriority('font-size')
  if (String(value || '').trim() || String(priority || '').trim()) return true
  const fontPriority = style.getPropertyPriority('font')
  return Boolean(String(style.cssText || '').match(/(^|;)\s*font\s*:/i) || String(fontPriority || '').trim())
}

const readerInlineTypographyLooksLikeProse = element => {
  if (!element?.matches) return false
  if (element.closest?.('h1,h2,h3,h4,h5,h6')) return false
  if (element.matches?.(readerMediaSelector) || element.querySelector?.(readerMediaSelector)) return false
  const tagName = element.tagName || ''
  if (!readerInlineTypographyTextTags.has(tagName)) return false
  const text = String(element.textContent || '').replace(/\s+/g, ' ').trim()
  return text.length > 0
}

const readerInlineTypographyFontSize = element =>
  readerInlineTypographyBlockTags.has(element?.tagName || '') ? '1rem' : '1em'

export const normalizeReaderInlineTypography = (doc, settings = {}) => {
  if (!doc?.body) return 0
  const candidates = [
    doc.body,
    ...Array.from(doc.querySelectorAll?.(readerInlineTypographyCandidateSelector) || []),
  ]
  let normalized = 0
  for (const element of candidates) {
    if (!readerElementHasInlineFontSize(element)) continue
    if (!readerInlineTypographyLooksLikeProse(element)) continue
    if (element.dataset?.navicOriginalInlineFontSize === undefined) {
      element.dataset.navicOriginalInlineFontSize = element.style.getPropertyValue('font-size') || ''
      element.dataset.navicOriginalInlineFontSizePriority = element.style.getPropertyPriority('font-size') || ''
      element.dataset.navicOriginalInlineFont = element.style.getPropertyValue('font') || ''
      element.dataset.navicOriginalInlineFontPriority = element.style.getPropertyPriority('font') || ''
      element.dataset.navicOriginalFontSizeAttribute = element.getAttribute?.('size') || ''
    }
    if (element.hasAttribute?.('size')) {
      element.removeAttribute('size')
    }
    element.style.setProperty('font-size', readerInlineTypographyFontSize(element), 'important')
    element.dataset.navicInlineTypographyNormalized = 'true'
    normalized += 1
  }
  return normalized
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
  readerStyleNumber(settings, 'fontSizePercent', 140, 50, 250)

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
    line-height: ${settings.lineHeight || 1.8} !important;
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
