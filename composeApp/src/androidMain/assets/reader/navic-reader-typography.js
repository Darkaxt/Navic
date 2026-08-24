import {
  ReaderFlowPaged,
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  ReaderFontSourceCustom,
  ReaderFontSourceNavic,
  readerCssQuotedString,
  readerCustomFontFamily,
  readerCustomFontUrl,
  readerEffectiveFontFamily,
  readerFlowMode,
  readerFontFormat,
  readerFontSource,
  readerThemeKey,
  readerThemePalette,
  readerThemeUsesSepiaImageTreatment,
} from './navic-reader-settings-core.js'
import {
  overlayClass,
} from './navic-reader-bridge-core.js'
import {
  readerMediaSelector,
} from './navic-reader-media.js'

const readerAssetUrl = path => new URL(path, import.meta.url).href

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

const readerLineFragmentInlineTags = new Set([
  'A',
  'ABBR',
  'B',
  'BDI',
  'BDO',
  'BR',
  'CITE',
  'CODE',
  'DFN',
  'EM',
  'FONT',
  'I',
  'KBD',
  'MARK',
  'Q',
  'S',
  'SAMP',
  'SMALL',
  'SPAN',
  'STRONG',
  'SUB',
  'SUP',
  'TIME',
  'U',
  'VAR',
])

const readerLooseTextContainerSelector = 'body, section, article, div, center'

const readerLooseTextMinimumLength = 80

const readerLooseTextBoundaryTags = new Set([
  'ADDRESS',
  'ASIDE',
  'BLOCKQUOTE',
  'CANVAS',
  'DD',
  'DL',
  'DT',
  'FIGCAPTION',
  'FIGURE',
  'FOOTER',
  'FORM',
  'H1',
  'H2',
  'H3',
  'H4',
  'H5',
  'H6',
  'HEADER',
  'HR',
  'IFRAME',
  'IMG',
  'LI',
  'MAIN',
  'NAV',
  'OL',
  'P',
  'PICTURE',
  'PRE',
  'SECTION',
  'SVG',
  'TABLE',
  'UL',
  'VIDEO',
])

const readerLineFragmentText = element => String(element?.textContent || '')

const readerLineFragmentTerminalPattern = /[.!?…]["'”’)\]]*$/

const readerLineFragmentCanMergeAfter = element => {
  const text = readerLineFragmentText(element).trim()
  return Boolean(text) && !readerLineFragmentTerminalPattern.test(text)
}

const readerLineFragmentCandidate = element => {
  if (element?.tagName !== 'P') return false
  if (element.querySelector?.(readerMediaSelector)) return false
  for (const child of element.children || []) {
    const tagName = child.tagName || ''
    if (!readerLineFragmentInlineTags.has(tagName)) return false
  }
  return true
}

const readerLineFragmentRuns = doc => {
  const parents = Array.from(doc?.body?.querySelectorAll?.('body, body *') || [])
  const runs = []
  for (const parent of parents) {
    let currentRun = []
    let currentClass = null
    const flush = () => {
      if (currentRun.length >= 3) runs.push(currentRun)
      currentRun = []
      currentClass = null
    }
    for (const child of Array.from(parent.children || [])) {
      if (!readerLineFragmentCandidate(child) || !readerLineFragmentText(child).trim()) {
        flush()
        continue
      }
      const className = String(child.className || '')
      if (currentRun.length > 0 && className !== currentClass) {
        flush()
      }
      currentRun.push(child)
      currentClass = className
    }
    flush()
  }
  return runs
}

const readerLineFragmentJoinEvidenceCount = run =>
  run.slice(1).filter((element, index) => readerLineFragmentCanMergeAfter(run[index]) && readerLineFragmentText(element).trim()).length

const moveReaderLineFragmentChildren = (target, source) => {
  while (source.firstChild) {
    target.append(source.firstChild)
  }
}

const readerLooseTextNodeText = node => String(node?.textContent || '').replace(/\s+/g, ' ').trim()

const readerLooseTextSegmentText = nodes =>
  nodes.map(readerLooseTextNodeText).filter(Boolean).join(' ').replace(/\s+/g, ' ').trim()

const readerLooseTextCanWrapSegment = nodes => {
  const text = readerLooseTextSegmentText(nodes)
  if (text.length < readerLooseTextMinimumLength) return false
  return /[.!?…]["'”’)\]]?(\s|$)/.test(text) || text.split(/\s+/).length >= 14
}

const readerLooseTextInlineCandidate = node => {
  if (!node) return false
  if (node.nodeType === Node.TEXT_NODE) return Boolean(readerLooseTextNodeText(node))
  if (node.nodeType !== Node.ELEMENT_NODE) return false
  if (node.matches?.(readerMediaSelector) || node.querySelector?.(readerMediaSelector)) return false
  const tagName = node.tagName || ''
  if (tagName === 'BR') return false
  if (readerLooseTextBoundaryTags.has(tagName)) return false
  return readerLineFragmentInlineTags.has(tagName)
}

const readerLooseTextIsBoundary = node => {
  if (!node || node.nodeType !== Node.ELEMENT_NODE) return false
  const tagName = node.tagName || ''
  return tagName !== 'BR' && (
    readerLooseTextBoundaryTags.has(tagName) ||
    node.matches?.(readerMediaSelector) ||
    node.querySelector?.(readerMediaSelector)
  )
}

const wrapReaderLooseTextSegment = (parent, segment) => {
  if (!parent || !segment?.nodes?.length || !readerLooseTextCanWrapSegment(segment.nodes)) return 0
  const firstNode = segment.nodes.find(node => node?.parentNode === parent)
  if (!firstNode) return 0
  const paragraph = parent.ownerDocument.createElement('p')
  paragraph.dataset.navicLooseTextParagraph = 'true'
  parent.insertBefore(paragraph, firstNode)
  for (const node of segment.nodes) {
    if (node?.parentNode === parent) paragraph.append(node)
  }
  for (const node of segment.breaks) {
    if (node?.parentNode === parent) node.remove()
  }
  return 1
}

const normalizeReaderLooseTextContainer = parent => {
  if (!parent || parent.dataset?.navicLooseTextNormalized === 'true') return 0
  const segments = []
  let nodes = []
  let breaks = []
  let consecutiveBreaks = 0
  const flush = () => {
    if (nodes.length > 0) segments.push({ nodes, breaks })
    nodes = []
    breaks = []
    consecutiveBreaks = 0
  }
  for (const node of Array.from(parent.childNodes || [])) {
    if (node.nodeType === Node.ELEMENT_NODE && node.tagName === 'BR') {
      if (nodes.length === 0) {
        breaks = []
        consecutiveBreaks = 0
        continue
      }
      breaks.push(node)
      consecutiveBreaks += 1
      if (consecutiveBreaks >= 2) flush()
      continue
    }
    if (readerLooseTextIsBoundary(node)) {
      flush()
      continue
    }
    if (readerLooseTextInlineCandidate(node)) {
      nodes.push(node)
      consecutiveBreaks = 0
      continue
    }
    if (readerLooseTextNodeText(node)) {
      flush()
    }
  }
  flush()
  const normalized = segments.reduce((count, segment) => count + wrapReaderLooseTextSegment(parent, segment), 0)
  if (parent.dataset && normalized > 0) parent.dataset.navicLooseTextNormalized = 'true'
  return normalized
}

export const normalizeReaderLooseTextParagraphs = doc => {
  if (!doc?.body) return 0
  let normalized = 0
  for (const parent of Array.from(doc.querySelectorAll?.(readerLooseTextContainerSelector) || [])) {
    normalized += normalizeReaderLooseTextContainer(parent)
  }
  return normalized
}

export const normalizeReaderLineFragmentParagraphs = doc => {
  if (!doc?.body) return 0
  if (doc.documentElement?.dataset?.navicLineFragmentsNormalized === 'true') return 0
  let normalized = 0
  normalized += normalizeReaderLooseTextParagraphs(doc)
  for (const run of readerLineFragmentRuns(doc)) {
    if (readerLineFragmentJoinEvidenceCount(run) < 2) continue
    let current = run[0]
    for (const next of run.slice(1)) {
      if (!readerLineFragmentCanMergeAfter(current) || !readerLineFragmentText(next).trim()) {
        current = next
        continue
      }
      moveReaderLineFragmentChildren(current, next)
      next.remove()
      normalized += 1
    }
  }
  if (doc.documentElement?.dataset && normalized > 0) {
    doc.documentElement.dataset.navicLineFragmentsNormalized = 'true'
  }
  return normalized
}

const readerNavicFontFacesRequired = settings =>
  readerFontSource(settings) === ReaderFontSourceNavic ||
  String(settings?.fontFamily || '').includes('Navic ')

export const readerFontFaceCss = settings => readerNavicFontFacesRequired(settings)
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

const readerInlineTypographyHeadingTags = new Set([
  'H1',
  'H2',
  'H3',
  'H4',
  'H5',
  'H6',
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

const readerInlineTypographyFontFamilyTags = new Set([
  ...readerInlineTypographyTextTags,
  ...readerInlineTypographyHeadingTags,
])

const readerInlineTypographyCandidateSelector = [
  'body',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'p',
  'li',
  'blockquote',
  'dd',
  'td',
  'th',
  'main',
  'section',
  'article',
  'center',
  'div',
  'pre',
  'span',
  'font',
  'code',
  'samp',
  'kbd',
  'a',
  '[style*="font-family"]',
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

const readerInlineTypographyLooksLikeReaderFontFamilyTarget = element => {
  if (!element?.matches) return false
  if (element.matches?.(readerMediaSelector) || element.closest?.(readerMediaSelector)) return false
  const tagName = element.tagName || ''
  if (!readerInlineTypographyFontFamilyTags.has(tagName)) return false
  const text = String(element.textContent || '').replace(/\s+/g, ' ').trim()
  return text.length > 0
}

const readerInlineTypographyFontSize = element => '1em'

export const normalizeReaderInlineTypography = (doc, settings = {}) => {
  if (!doc?.body) return 0
  const fontFamily = readerEffectiveFontFamily(settings)
  const preservePublisherFontFamily = settings.publisherStyles === true || !fontFamily
  const candidates = Array.from(new Set([
    doc.body,
    ...Array.from(doc.querySelectorAll?.(readerInlineTypographyCandidateSelector) || []),
  ]))
  let normalized = 0
  for (const element of candidates) {
    const fontSizeTarget = readerInlineTypographyLooksLikeProse(element)
    const fontFamilyTarget = readerInlineTypographyLooksLikeReaderFontFamilyTarget(element)
    if (!fontSizeTarget && !fontFamilyTarget) continue
    if (element.dataset?.navicOriginalInlineFontSize === undefined) {
      element.dataset.navicOriginalInlineFontSize = element.style.getPropertyValue('font-size') || ''
      element.dataset.navicOriginalInlineFontSizePriority = element.style.getPropertyPriority('font-size') || ''
      element.dataset.navicOriginalInlineFont = element.style.getPropertyValue('font') || ''
      element.dataset.navicOriginalInlineFontPriority = element.style.getPropertyPriority('font') || ''
      element.dataset.navicOriginalFontSizeAttribute = element.getAttribute?.('size') || ''
      element.dataset.navicHadInlineFontSize = String(readerElementHasInlineFontSize(element))
    }
    let changed = false
    if (!preservePublisherFontFamily && fontFamilyTarget) {
      if (element.dataset?.navicOriginalFontFamily === undefined) {
        element.dataset.navicOriginalFontFamily = element.style.getPropertyValue('font-family') || ''
        element.dataset.navicOriginalFontFamilyPriority = element.style.getPropertyPriority('font-family') || ''
      }
      element.style.setProperty('font-family', fontFamily, 'important')
      element.dataset.navicFontFamilyNormalized = 'true'
      changed = true
    }
    if (fontSizeTarget) {
      if (element.hasAttribute?.('size')) {
        element.removeAttribute('size')
      }
      element.style.setProperty('font-size', readerInlineTypographyFontSize(element), 'important')
      element.dataset.navicInlineTypographyNormalized = 'true'
      changed = true
    }
    if (changed) normalized += 1
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

const readerLandscapeSpreadColumnCount = (maxColumnCount, inlineViewport, blockViewport, columnThreshold) => {
  if (inlineViewport <= blockViewport || maxColumnCount > 0) return maxColumnCount
  return Math.min(2, Math.max(1, Math.ceil(inlineViewport / columnThreshold)))
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

export const readerResolvedFoliateGap = ({ flowMode, width, height, columnCount } = {}) =>
  flowMode === ReaderFlowPaged && width >= height * 1.12 && columnCount >= 2
    ? '2%'
    : null

export const readerResolvedFoliateContentGap = ({ flowMode, width, height, columnCount } = {}) =>
  flowMode === ReaderFlowPaged && width >= height * 1.12 && columnCount >= 2
    ? '6%'
    : null

export const readerAdaptiveFoliatePageBox = (viewport = readerViewportSize(), settings = {}) => {
  const width = Math.max(1, Math.round(Number(viewport?.width) || 1))
  const height = Math.max(1, Math.round(Number(viewport?.height) || 1))
  const flowMode = readerFlowMode(settings)
  const inlineViewport = width
  const blockViewport = height
  const maxColumnCount = readerMaxColumnCountValue(settings)
  const columnThreshold = readerColumnThresholdValue(settings)
  const resolvedMaxColumnCount = readerLandscapeSpreadColumnCount(
    maxColumnCount,
    inlineViewport,
    blockViewport,
    columnThreshold
  )
  const maxInline = Math.max(320, inlineViewport)
  const maxBlock = Math.max(320, blockViewport)
  return {
    maxInlineSize: `${Math.round(maxInline)}px`,
    maxBlockSize: `${Math.round(maxBlock)}px`,
    maxColumnCount: String(resolvedMaxColumnCount),
    columnThreshold: `${Math.round(columnThreshold)}px`,
    viewportWidth: width,
    viewportHeight: height,
    flowMode,
    foliateGap: readerResolvedFoliateGap({
      flowMode,
      width,
      height,
      columnCount: resolvedMaxColumnCount,
    }),
    foliateContentGap: readerResolvedFoliateContentGap({
      flowMode,
      width,
      height,
      columnCount: resolvedMaxColumnCount,
    }),
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
  html {
    font-size: var(--reader-content-font-size, ${fontSizePercent}%) !important;
    ${usePublisherStyles ? '' : `
    letter-spacing: ${letterSpacing}px !important;
    `}
  }
  body {
    font-size: 1em !important;
    line-height: ${settings.lineHeight || 1.8} !important;
    width: auto !important;
    max-width: none !important;
    ${usePublisherStyles || !fontFamily ? '' : `font-family: ${fontFamily} !important;`}
    ${usePublisherStyles ? '' : `
    word-spacing: ${wordSpacing}px !important;
    `}
    margin: 0 !important;
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
  main,
  section,
  article,
  center,
  div:not(:has(*:not(b, a, em, i, strong, u, span))):not(:has(img)):not(:has(svg)):not(:has(canvas)),
  div:has(> br):not(:has(> img)):not(:has(> svg)):not(:has(> canvas)),
  body > span:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  body > a:any-link:not(:has(img)):not(:has(svg)):not(:has(canvas)),
  font,
  [data-navic-paragraph-block="true"] {
    min-width: min(32em, 100%) !important;
    overflow-wrap: normal !important;
    word-break: normal !important;
    hyphens: manual !important;
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
    font-size: 1em !important;
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
  body > h1:first-child,
  body > h2:first-child,
  body > h3:first-child,
  body > h4:first-child,
  body > h5:first-child,
  body > h6:first-child {
    margin-block-start: clamp(48px, 4.5vh, 96px) !important;
    margin-top: clamp(48px, 4.5vh, 96px) !important;
  }
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
  body :not(img):not(picture):not(video):not(canvas):not(svg):not(object):not(embed):not([role="img"]):not(.${overlayClass}) {
    background-color: transparent !important;
  }
  body [style*="background"]:not(img):not(picture):not(video):not(canvas):not(svg):not(object):not(embed):not([role="img"]):not(.${overlayClass}),
  body [bgcolor]:not(img):not(picture):not(video):not(canvas):not(svg):not(object):not(embed):not([role="img"]):not(.${overlayClass}) {
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
  }
  ${readerThemeUsesSepiaImageTreatment(settings?.theme) ? `
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
    background-color: color-mix(in srgb, var(--reader-accent) 28%, transparent) !important;
    background-image: none !important;
    border-radius: 3px;
    -webkit-box-decoration-break: clone;
    box-decoration-break: clone;
  }
`
}
