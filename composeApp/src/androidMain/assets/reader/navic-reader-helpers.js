import {
  KomikkuNavigationRegionLeft,
  KomikkuNavigationRegionNext,
  KomikkuNavigationRegionPrevious,
  KomikkuNavigationRegionRight,
  ReaderDirectionRtl,
  ReaderFlowPagedVertical,
  ReaderFlowScrolled,
  ReaderFlowScrolledGaps,
  ReaderFontSourceCustom,
  ReaderFontSourceNavic,
  ReaderMovingPageBorderOverlayLayerSelector,
  ReaderMovingPagePaperTextureLayerSelector,
  ReaderMovingPageStainOverlayLayerSelector,
  ReaderMovingPageSpreadGutterOverlayLayerSelector,
  ReaderPageBorderOverlayAssets,
  ReaderPageBorderOverlayVariantCount,
  ReaderPageEdgeRimOverlayAssets,
  ReaderPageEdgeOverlayAssets,
  ReaderPageEdgeOverlayVariantCount,
  ReaderPageEdgeWearOverlayAssets,
  ReaderPageNumberLayerSelector,
  ReaderPageStainOverlayAssets,
  ReaderPageStainOverlayVariantCount,
  ReaderPaperTextureAssets,
  ReaderPaperTextureVariantCount,
  ReaderShellCoverLayerSelector,
  ReaderShellCoverTransitionMs,
  ReaderSpreadGutterHighlightOverlayAssets,
  ReaderSpreadGutterOverlayAssets,
  ReaderSpreadGutterOverlayVariantCount,
  ReaderSurfacePageBorderOverlayLayerSelector,
  ReaderSurfacePageStainOverlayLayerSelector,
  ReaderSurfaceSpreadGutterOverlayLayerSelector,
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
export * from './navic-reader-typography.js'

export const readerAssetUrl = path => new URL(path, document.baseURI).href
export const ReaderShellCoverProgressThreshold = 0.0015

export const readerTokenText = value => {
  if (value == null) return ''
  if (Array.isArray(value)) return value.map(readerTokenText).join(' ')
  if (typeof value === 'object') return Object.values(value).map(readerTokenText).join(' ')
  return String(value)
}

export const readerMediaOverlayTextEntries = doc => {
  const root = doc?.body
  if (!root || !doc.createTreeWalker) return []
  const nodeFilter = doc.defaultView?.NodeFilter || NodeFilter
  const walker = doc.createTreeWalker(root, nodeFilter.SHOW_TEXT)
  const entries = []
  let offset = 0
  let node = walker.nextNode()
  while (node) {
    const text = node.nodeValue || ''
    const start = offset
    const end = start + text.length
    entries.push({ node, start, end, text })
    offset = end
    node = walker.nextNode()
  }
  return entries
}

export const readerMediaOverlayTextPoint = (entries, requestedOffset) => {
  if (!entries.length) return null
  const last = entries[entries.length - 1]
  const offset = Math.max(0, Math.min(last.end, requestedOffset))
  for (const entry of entries) {
    if (offset >= entry.start && offset <= entry.end) {
      return {
        node: entry.node,
        offset: Math.max(0, Math.min(entry.text.length, offset - entry.start)),
      }
    }
  }
  return {
    node: last.node,
    offset: last.text.length,
  }
}

const readerMediaOverlayWordCharPattern = /[\p{L}\p{N}]/u
const readerMediaOverlayMarkPattern = /\p{M}/gu
export const ReaderMediaOverlayTextSearchPaddingMinimum = 96

const readerMediaOverlayAppendComparable = (state, value, rawOffset, rawEndOffset = rawOffset + 1) => {
  if (!value) return
  if (value === ' ') {
    if (!state.text || state.text.endsWith(' ')) return
    state.text += ' '
    state.offsets.push(rawOffset)
    state.endOffsets.push(rawEndOffset)
    return
  }
  state.text += value
  state.offsets.push(rawOffset)
  state.endOffsets.push(rawEndOffset)
}

const readerMediaOverlayComparableTextWithOffsets = (text, absoluteStart = 0) => {
  const state = { text: '', offsets: [], endOffsets: [] }
  const rawText = String(text || '')
  let rawOffset = 0
  for (const char of rawText) {
    const absoluteRawStart = absoluteStart + rawOffset
    const absoluteRawEnd = absoluteRawStart + char.length
    const normalized = char
      .normalize('NFKD')
      .replace(readerMediaOverlayMarkPattern, '')
      .toLowerCase()
    let appendedWordChar = false
    for (const normalizedChar of normalized) {
      if (readerMediaOverlayWordCharPattern.test(normalizedChar)) {
        readerMediaOverlayAppendComparable(state, normalizedChar, absoluteRawStart, absoluteRawEnd)
        appendedWordChar = true
      }
    }
    if (!appendedWordChar) {
      readerMediaOverlayAppendComparable(state, ' ', absoluteRawStart, absoluteRawEnd)
    }
    rawOffset += char.length
  }
  while (state.text.startsWith(' ')) {
    state.text = state.text.slice(1)
    state.offsets.shift()
    state.endOffsets.shift()
  }
  while (state.text.endsWith(' ')) {
    state.text = state.text.slice(0, -1)
    state.offsets.pop()
    state.endOffsets.pop()
  }
  return state
}

export const readerMediaOverlayNormalizedTextMap = entries => {
  if (!Array.isArray(entries) || !entries.length) return { text: '', offsets: [], endOffsets: [] }
  return readerMediaOverlayComparableTextWithOffsets(entries.map(entry => entry.text || '').join(''))
}

export const readerMediaOverlayRawOffsetForNormalizedOffset = (normalizedMap, requestedOffset, edge = 'start') => {
  const map = normalizedMap || {}
  const textLength = map.text?.length || 0
  if (!textLength) return 0
  const offset = Number.isFinite(Number(requestedOffset)) ? Number(requestedOffset) : 0
  if (edge === 'end') {
    const endIndex = Math.max(0, Math.min(textLength, Math.ceil(offset)))
    if (endIndex <= 0) return map.offsets[0] ?? 0
    return map.endOffsets[endIndex - 1] ?? ((map.offsets[endIndex - 1] ?? 0) + 1)
  }
  const startIndex = Math.max(0, Math.min(textLength - 1, Math.floor(offset)))
  return map.offsets[startIndex] ?? 0
}

export const readerMediaOverlayClosestTextMatch = (normalizedText, normalizedSpokenText, preferredCenter) => {
  if (!normalizedText || !normalizedSpokenText) return null
  let searchFrom = 0
  let best = null
  while (searchFrom <= normalizedText.length) {
    const matchIndex = normalizedText.indexOf(normalizedSpokenText, searchFrom)
    if (matchIndex < 0) break
    const normalizedTextStart = matchIndex
    const normalizedTextEnd = matchIndex + normalizedSpokenText.length
    if (normalizedTextEnd > normalizedTextStart) {
      const centerDistance = Math.abs(((normalizedTextStart + normalizedTextEnd) / 2) - preferredCenter)
      if (!best || centerDistance < best.centerDistance) best = { normalizedTextStart, normalizedTextEnd, centerDistance }
    }
    searchFrom = matchIndex + 1
  }
  return best
}

export const readerMediaOverlayEbookTextCandidates = ebookText => {
  const comparableEbook = readerMediaOverlayComparableTextWithOffsets(String(ebookText || '').trim())
  const text = comparableEbook.text || ''
  if (!text) return []
  const candidates = [{ text, locator: 'ebook-text', priority: 0 }]
  const minimumSuffixLength = Math.max(16, Math.min(48, Math.floor(text.length * 0.35)))
  for (let index = 0; index < text.length; index += 1) {
    if (text[index] !== ' ') continue
    const suffix = text.slice(index + 1).trim()
    if (suffix.length < minimumSuffixLength || suffix.length >= text.length) continue
    candidates.push({
      text: suffix,
      locator: 'ebook-text-suffix',
      priority: 1,
    })
  }
  return candidates
}

export const readerMediaOverlayResolvedTextRange = (normalizedMap, textStart, textEnd, ebookText) => {
  const map = Array.isArray(normalizedMap) ? readerMediaOverlayNormalizedTextMap(normalizedMap) : (normalizedMap || {})
  const mapLength = map.text?.length || 0
  const requestedStart = Number.isFinite(Number(textStart)) ? Number(textStart) : 0
  const requestedEnd = Number.isFinite(Number(textEnd)) ? Number(textEnd) : requestedStart
  const fallbackStart = Math.max(0, Math.min(mapLength, Math.floor(requestedStart)))
  const fallbackEnd = Math.max(fallbackStart, Math.min(mapLength, Math.ceil(requestedEnd)))
  const fallbackRawStart = readerMediaOverlayRawOffsetForNormalizedOffset(map, fallbackStart, 'start')
  const fallbackRawEnd = readerMediaOverlayRawOffsetForNormalizedOffset(map, fallbackEnd, 'end')
  const fallback = {
    textStart: fallbackRawStart,
    textEnd: Math.max(fallbackRawStart, fallbackRawEnd),
    normalizedTextStart: fallbackStart,
    normalizedTextEnd: fallbackEnd,
    matched: false,
    locator: 'offset',
  }
  const locatorText = String(ebookText || '').trim()
  if (!mapLength || !locatorText || fallbackEnd <= fallbackStart) return fallback
  const fallbackLength = fallbackEnd - fallbackStart
  const searchPadding = Math.max(
    ReaderMediaOverlayTextSearchPaddingMinimum,
    fallbackLength * 2,
    locatorText.length * 2
  )
  const searchStart = Math.max(0, fallbackStart - searchPadding)
  const searchEnd = Math.min(mapLength, fallbackEnd + searchPadding)
  const searchText = map.text.slice(searchStart, searchEnd)
  const candidates = readerMediaOverlayEbookTextCandidates(locatorText)
  if (!searchText || !candidates.length) return fallback
  const preferredCenter = (fallbackStart + fallbackEnd) / 2
  const preferredCenterInSearch = preferredCenter - searchStart
  let match = null
  let matchedCandidate = null
  for (const candidate of candidates.filter(candidate => candidate.priority === 0)) {
    const candidateMatch = readerMediaOverlayClosestTextMatch(
      searchText,
      candidate.text,
      preferredCenterInSearch
    )
    if (candidateMatch) {
      match = candidateMatch
      matchedCandidate = candidate
      break
    }
  }
  if (!match) {
    for (const candidate of candidates.filter(candidate => candidate.priority > 0)) {
      const candidateMatch = readerMediaOverlayClosestTextMatch(
        searchText,
        candidate.text,
        preferredCenterInSearch
      )
      if (!candidateMatch) continue
      if (
        !match ||
          candidate.text.length > matchedCandidate.text.length ||
          (candidate.text.length === matchedCandidate.text.length && candidateMatch.centerDistance < match.centerDistance)
      ) {
        match = candidateMatch
        matchedCandidate = candidate
      }
    }
  }
  if (!match || !matchedCandidate) return fallback
  const normalizedTextStart = searchStart + match.normalizedTextStart
  const normalizedTextEnd = searchStart + match.normalizedTextEnd
  const resolvedStart = readerMediaOverlayRawOffsetForNormalizedOffset(map, normalizedTextStart, 'start')
  const resolvedEnd = readerMediaOverlayRawOffsetForNormalizedOffset(map, normalizedTextEnd, 'end')
  if (!Number.isFinite(resolvedStart) || !Number.isFinite(resolvedEnd) || resolvedEnd <= resolvedStart) return fallback
  return {
    textStart: resolvedStart,
    textEnd: resolvedEnd,
    normalizedTextStart,
    normalizedTextEnd,
    matched: true,
    locator: matchedCandidate.locator,
  }
}

export const readerMediaOverlayClampRangeBeforeNextCue = (normalizedMap, range, nextRange) => {
  const map = Array.isArray(normalizedMap) ? readerMediaOverlayNormalizedTextMap(normalizedMap) : (normalizedMap || {})
  const mapLength = map.text?.length || 0
  const nextStart = Number(nextRange?.normalizedTextStart)
  const currentStart = Number(range?.normalizedTextStart)
  const currentEnd = Number(range?.normalizedTextEnd)
  if (
    !Number.isFinite(nextStart) ||
      !Number.isFinite(currentStart) ||
      !Number.isFinite(currentEnd)
  ) {
    return range
  }
  if (range?.locator === 'offset' && nextStart <= currentStart && nextStart > 0) {
    const currentLength = Math.max(1, currentEnd - currentStart)
    const anchoredEnd = Math.max(0, Math.min(mapLength, nextStart))
    const anchoredStart = Math.max(0, anchoredEnd - currentLength)
    const textStart = readerMediaOverlayRawOffsetForNormalizedOffset(map, anchoredStart, 'start')
    const textEnd = readerMediaOverlayRawOffsetForNormalizedOffset(map, nextStart, 'start')
    if (Number.isFinite(textStart) && Number.isFinite(textEnd) && textEnd > textStart) {
      return {
        ...range,
        textStart,
        textEnd,
        normalizedTextStart: anchoredStart,
        normalizedTextEnd: anchoredEnd,
        clampedByNextCue: true,
        locator: 'next-anchor-gap',
      }
    }
  }
  if (nextStart <= currentStart || nextStart >= currentEnd) return range
  const textEnd = readerMediaOverlayRawOffsetForNormalizedOffset(map, nextStart, 'start')
  return {
    ...range,
    textEnd: Math.max(range.textStart, textEnd),
    normalizedTextEnd: nextStart,
    clampedByNextCue: true,
  }
}

export const readerMediaOverlayTextOffsetForRange = (doc, range) => {
  const container = range?.startContainer
  const startOffset = Number(range?.startOffset)
  if (!doc || !container || !Number.isFinite(startOffset)) return null
  const entries = readerMediaOverlayTextEntries(doc)
  const entry = entries.find(candidate => candidate.node === container)
  if (!entry) return null
  return entry.start + Math.max(0, Math.min(entry.text.length, Math.floor(startOffset)))
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

const readerCoverTokenPattern = /(^|[\s._/-])(cover|cubierta|portada)([\s._/-]|$)|cover-image|coverpage|cover.xhtml|frontcover|cubierta.xhtml|portada.xhtml/

export const readerEmbeddedCoverImage = (doc, section = null, index = 0) => {
  if (!doc?.body || index !== 0 || doc.documentElement?.dataset?.navicEmbeddedCoverSuppressed === 'true') return null
  const first = readerFirstMeaningfulBodyElement(doc)
  if (!first || readerElementText(first).length > 40) return null
  const image = first.matches?.('img') ? first : first.querySelector?.('img')
  const coverTokenText = [
    readerSectionTokenText(section),
    doc.title,
    doc.documentElement?.getAttribute?.('epub:type'),
    doc.body?.getAttribute?.('epub:type'),
    image?.getAttribute?.('src'),
    image?.getAttribute?.('alt'),
    image?.getAttribute?.('title'),
    image?.getAttribute?.('aria-label'),
  ].filter(Boolean).join(' ').toLowerCase()
  if (!readerCoverTokenPattern.test(coverTokenText)) return null
  const metrics = readerImageAspect(image)
  if (!image || !metrics || !Number.isFinite(metrics.aspect)) return null
  const largeEnough = metrics.width >= 480 || metrics.height >= 640
  if (!largeEnough || metrics.aspect < 1.1 || metrics.aspect > 1.9) return null
  return image
}

export const suppressReaderEmbeddedCoverPage = (doc, section = null, index = 0) => {
  const image = readerEmbeddedCoverImage(doc, section, index)
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
  readerSurfaceTextureVariantForPage(`${key}|paper-base`, ReaderPaperTextureAssets, ReaderPaperTextureVariantCount)

export const readerPageBorderOverlayVariantForPage = key =>
  readerSurfaceTextureVariantForPage(
    `${key}|page-edge`,
    ReaderPageBorderOverlayAssets,
    ReaderPageBorderOverlayVariantCount
  )

export const readerPageEdgeOverlayVariantForPage = key =>
  readerSurfaceTextureVariantForPage(
    `${key}|page-edge`,
    ReaderPageEdgeOverlayAssets,
    ReaderPageEdgeOverlayVariantCount
  )

export const readerPageStainOverlayVariantForPage = key =>
  readerSurfaceTextureVariantForPage(
    `${key}|page-stain`,
    ReaderPageStainOverlayAssets,
    ReaderPageStainOverlayVariantCount
  )

export const readerSpreadGutterOverlayVariantForPage = key =>
  readerSurfaceTextureVariantForPage(
    `${key}|spread-gutter`,
    ReaderSpreadGutterOverlayAssets,
    ReaderSpreadGutterOverlayVariantCount
  )

const readerPaperTextureSlotDetail = (detail = {}, pageIndex = null, pageCount = null, section = null) => {
  const next = { ...detail }
  if (section?.href || section?.id) {
    next.href = section.href || section.id
  }
  if (Number.isFinite(pageIndex)) {
    next.pageIndex = Math.max(0, Math.floor(pageIndex))
  } else {
    delete next.pageIndex
  }
  if (Number.isFinite(pageCount)) {
    next.pageCount = Math.max(1, Math.floor(pageCount))
  } else {
    delete next.pageCount
  }
  return next
}

export const readerAdjacentPaperTextureSlots = ({
  publicationUrl,
  sections = [],
  index = 0,
  detail = {},
  pagePosition = null,
  resolveVariant = readerPaperTextureVariantForPage,
} = {}) => {
  const currentIndex = Number.isFinite(Number(index)) ? Math.max(0, Math.floor(Number(index))) : 0
  const pageIndex = Number(pagePosition?.pageIndex)
  const pageCount = Number(pagePosition?.pageCount)
  const hasPagePosition = Number.isFinite(pageIndex) && Number.isFinite(pageCount) && pageCount > 0
  const sectionFor = sectionIndex => sections?.[sectionIndex]
  const slotFor = (slot, sectionIndex, slotPageIndex = null) => {
    const section = sectionFor(sectionIndex)
    const slotDetail = hasPagePosition || Number.isFinite(slotPageIndex)
      ? readerPaperTextureSlotDetail(
        detail,
        Number.isFinite(slotPageIndex) ? slotPageIndex : pageIndex,
        sectionIndex === currentIndex ? pageCount : null,
        section
      )
      : readerPaperTextureSlotDetail(detail, null, null, section)
    const key = readerPaperTextureVariantKey(publicationUrl, section, sectionIndex, slotDetail)
    return {
      slot,
      key,
      variant: resolveVariant(key),
    }
  }
  const slots = [slotFor('current', currentIndex, hasPagePosition ? pageIndex : null)]
  const previousPageIndex = hasPagePosition && pageIndex > 0 ? pageIndex - 1 : null
  const nextPageIndex = hasPagePosition && pageIndex + 1 < pageCount ? pageIndex + 1 : null
  if (previousPageIndex != null) {
    slots.unshift(slotFor('previous', currentIndex, previousPageIndex))
  } else if (currentIndex > 0) {
    slots.unshift(slotFor('previous', currentIndex - 1, null))
  }
  if (nextPageIndex != null) {
    slots.push(slotFor('next', currentIndex, nextPageIndex))
  } else if (currentIndex + 1 < sections.length) {
    slots.push(slotFor('next', currentIndex + 1, null))
  }
  return slots
}

export const readerSurfaceSpreadMode = ({
  flowMode = '',
  width: rawWidth = null,
  height: rawHeight = null,
} = {}) => {
  if (flowMode === ReaderFlowScrolled) return 'single'
  if (flowMode === ReaderFlowScrolledGaps) return 'single'
  if (flowMode === ReaderFlowPagedVertical) return 'single'
  const width = Number(rawWidth)
  const height = Number(rawHeight)
  if (Number.isFinite(width) && Number.isFinite(height) && width >= height * 1.12) {
    return 'spread'
  }
  return 'single'
}

export const readerSurfaceSpreadGutterVisible = ({
  settings,
  spreadMode = '',
  flowMode = '',
  width = null,
  height = null,
} = {}) => {
  if (settings?.pageEdgesEnabled === false) return false
  if (spreadMode === 'spread') return true
  if (spreadMode) return false
  return readerSurfaceSpreadMode({ flowMode, width, height }) === 'spread'
}

export const ReaderPageShellDefaultOuterEdgePx = 32
export const ReaderPageShellDefaultGutterPx = 48

const readerPageShellClamp = (value, min, max) =>
  Math.min(max, Math.max(min, value))

const readerPageShellNumber = (value, fallback) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

const readerPageShellSideMarginPercent = settings =>
  readerPageShellClamp(readerPageShellNumber(settings?.sideMargin, 6), 0, 20)

const readerPageShellTopMarginPx = settings =>
  readerPageShellClamp(readerPageShellNumber(settings?.topMargin, 90), 0, 240)

const readerPageShellBottomMarginPx = settings =>
  readerPageShellClamp(readerPageShellNumber(settings?.bottomMargin, 50), 0, 240)

const readerPageShellRect = (left, top, width, height) => ({
  x: Math.round(left),
  y: Math.round(top),
  left: Math.round(left),
  top: Math.round(top),
  width: Math.max(1, Math.round(width)),
  height: Math.max(1, Math.round(height)),
})

const readerPageShellInsetRect = (rect, insets = {}) => {
  const left = Math.max(0, Math.round(Number(insets.left) || 0))
  const top = Math.max(0, Math.round(Number(insets.top) || 0))
  const right = Math.max(0, Math.round(Number(insets.right) || 0))
  const bottom = Math.max(0, Math.round(Number(insets.bottom) || 0))
  return readerPageShellRect(
    rect.left + left,
    rect.top + top,
    Math.max(1, rect.width - left - right),
    Math.max(1, rect.height - top - bottom)
  )
}

export const readerPageShellRectStyle = rect => ({
  left: `${Math.round(Number(rect?.left ?? rect?.x) || 0)}px`,
  top: `${Math.round(Number(rect?.top ?? rect?.y) || 0)}px`,
  width: `${Math.max(1, Math.round(Number(rect?.width) || 1))}px`,
  height: `${Math.max(1, Math.round(Number(rect?.height) || 1))}px`,
})

export const readerPageShellGeometry = ({
  settings = {},
  flowMode = '',
  spreadMode = '',
  width: rawWidth = null,
  height: rawHeight = null,
  coverMode = false,
} = {}) => {
  const viewport = readerViewportSize()
  const width = Math.max(1, Math.round(readerPageShellNumber(rawWidth, viewport.width)))
  const height = Math.max(1, Math.round(readerPageShellNumber(rawHeight, viewport.height)))
  const mode = coverMode
    ? 'cover'
    : (spreadMode || readerSurfaceSpreadMode({ flowMode, width, height }))
  const sideMarginPercent = readerPageShellSideMarginPercent(settings)
  const viewportRect = readerPageShellRect(0, 0, width, height)
  const shellInsetX = mode === 'cover'
    ? 0
    : readerPageShellClamp(Math.round(width * 0.04), 0, 140)
  const shellInsetY = mode === 'cover'
    ? 0
    : readerPageShellClamp(Math.round(height * 0.035), 0, 110)
  const shellRect = readerPageShellRect(
    shellInsetX,
    shellInsetY,
    width - shellInsetX * 2,
    height - shellInsetY * 2
  )
  const spreadGutterWidth = mode === 'spread'
    ? Math.max(
      ReaderPageShellDefaultGutterPx,
      Math.round(shellRect.width * sideMarginPercent / 100)
    )
    : 0
  const pageEdgesEnabled = settings?.pageEdgesEnabled !== false
  const outerEdge = pageEdgesEnabled
    ? readerPageShellClamp(
      Math.round(Math.min(shellRect.width, shellRect.height) * 0.024),
      18,
      ReaderPageShellDefaultOuterEdgePx
    )
    : 0
  const innerEdge = mode === 'spread'
    ? Math.max(outerEdge, Math.round(spreadGutterWidth * 0.18))
    : outerEdge
  const topMargin = readerPageShellTopMarginPx(settings)
  const bottomMargin = readerPageShellBottomMarginPx(settings)
  const pageHeight = shellRect.height
  const singlePage = readerPageShellRect(shellRect.left, shellRect.top, shellRect.width, pageHeight)
  const spreadPageWidth = mode === 'spread'
    ? Math.max(1, Math.round((shellRect.width - spreadGutterWidth) / 2))
    : shellRect.width
  const leftPage = readerPageShellRect(shellRect.left, shellRect.top, spreadPageWidth, pageHeight)
  const gutterRect = mode === 'spread'
    ? readerPageShellRect(shellRect.left + spreadPageWidth, shellRect.top, spreadGutterWidth, pageHeight)
    : null
  const rightPage = mode === 'spread'
    ? readerPageShellRect(
      shellRect.left + spreadPageWidth + spreadGutterWidth,
      shellRect.top,
      shellRect.width - spreadPageWidth - spreadGutterWidth,
      pageHeight
    )
    : null
  const contentTop = Math.round(topMargin)
  const contentBottom = Math.round(bottomMargin)
  const singleContent = readerPageShellInsetRect(singlePage, {
    left: outerEdge,
    top: contentTop,
    right: outerEdge,
    bottom: contentBottom,
  })
  const leftContent = readerPageShellInsetRect(leftPage, {
    left: outerEdge,
    top: contentTop,
    right: innerEdge,
    bottom: contentBottom,
  })
  const rightContent = rightPage
    ? readerPageShellInsetRect(rightPage, {
      left: innerEdge,
      top: contentTop,
      right: outerEdge,
      bottom: contentBottom,
    })
    : null
  const pageBoxWidth = mode === 'spread'
    ? Math.max(1, Math.min(leftContent.width, rightContent?.width || leftContent.width))
    : singleContent.width
  const pageBoxMaxColumnCount = mode === 'spread' ? 2 : undefined
  const coverWidth = Math.round(Math.min(width * 0.38, height * 0.72))
  const coverHeight = Math.round(Math.min(height * 0.86, Math.max(coverWidth * 1.42, height * 0.72)))
  const coverRect = readerPageShellRect(
    (width - coverWidth) / 2,
    (height - coverHeight) / 2,
    coverWidth,
    coverHeight
  )
  const backCoverOffset = Math.max(18, Math.round(Math.min(width, height) * 0.024))
  const backCoverRect = readerPageShellRect(
    Math.min(width - coverRect.width - outerEdge, coverRect.left + backCoverOffset),
    Math.min(height - coverRect.height - outerEdge, coverRect.top + backCoverOffset),
    coverRect.width,
    coverRect.height
  )
  return {
    mode,
    viewportRect,
    shellRect,
    pageRects: {
      single: singlePage,
      left: mode === 'spread' ? leftPage : singlePage,
      right: mode === 'spread' ? rightPage : null,
      full: shellRect,
    },
    contentRects: {
      single: singleContent,
      left: mode === 'spread' ? leftContent : singleContent,
      right: mode === 'spread' ? rightContent : null,
    },
    gutterRect,
    coverRect,
    backCoverRect,
    cover: {
      backdropRect: shellRect,
      foregroundRect: coverRect,
      backCoverRect,
    },
    edgeInsets: {
      outer: outerEdge,
      inner: innerEdge,
      gutter: spreadGutterWidth,
      top: topMargin,
      bottom: bottomMargin,
    },
    renderer: {
      gapPercent: sideMarginPercent,
      topMargin,
      bottomMargin,
      width: shellRect.width,
      height: shellRect.height,
      pageBoxWidth,
      pageBoxMaxColumnCount,
      rect: shellRect,
    },
  }
}

export const readerPageShellGeometryForViewport = (settings = {}, options = {}) => {
  const viewport = readerViewportSize()
  return readerPageShellGeometry({
    settings,
    width: viewport.width,
    height: viewport.height,
    ...options,
  })
}

export const readerPageShellRectForPage = (geometry, page = 'full') => {
  if (page === 'left') return geometry?.pageRects?.left || geometry?.pageRects?.single
  if (page === 'right') return geometry?.pageRects?.right || geometry?.pageRects?.single
  if (page === 'single') return geometry?.pageRects?.single
  return geometry?.pageRects?.full || geometry?.shellRect
}

export const readerPageShellContentRectForIndex = (geometry, index = undefined) => {
  if (geometry?.mode !== 'spread') return geometry?.contentRects?.single
  const number = Number(index)
  if (Number.isFinite(number) && Math.abs(Math.floor(number)) % 2 === 1) {
    return geometry?.contentRects?.right || geometry?.contentRects?.left
  }
  return geometry?.contentRects?.left || geometry?.contentRects?.right
}

export const applyReaderPageShellContentGeometry = (doc, settings = {}, geometry = null, index = undefined) => {
  if (!doc?.documentElement) return null
  const resolved = geometry || readerPageShellGeometryForViewport(settings, {
    flowMode: readerFlowMode(settings),
  })
  const contentRect = readerPageShellContentRectForIndex(resolved, index) || resolved?.contentRects?.single
  const root = doc.documentElement
  root.setAttribute('data-navic-reader-shell-content', 'true')
  root.dataset.navicReaderShellGeometryMode = resolved?.mode || ''
  root.style.setProperty('--navic-reader-shell-content-left', `${Math.round(contentRect?.left || 0)}px`)
  root.style.setProperty('--navic-reader-shell-content-top', `${Math.round(contentRect?.top || 0)}px`)
  root.style.setProperty('--navic-reader-shell-content-width', `${Math.max(1, Math.round(contentRect?.width || 1))}px`)
  root.style.setProperty('--navic-reader-shell-content-height', `${Math.max(1, Math.round(contentRect?.height || 1))}px`)
  root.style.setProperty('--navic-reader-shell-gutter-width', `${Math.round(resolved?.edgeInsets?.gutter || 0)}px`)
  return resolved
}

export const readerShellGeometryDiagnosticState = (geometry, reason = 'unknown') => ({
  reason,
  mode: geometry?.mode || '',
  viewportRect: geometry?.viewportRect || null,
  shellRect: geometry?.shellRect || null,
  pageRects: geometry?.pageRects || null,
  contentRects: geometry?.contentRects || null,
  gutterRect: geometry?.gutterRect || null,
  coverRect: geometry?.coverRect || null,
  backCoverRect: geometry?.backCoverRect || null,
  edgeInsets: geometry?.edgeInsets || null,
  cover: geometry?.cover || null,
  renderer: geometry?.renderer || null,
})

export const readerCoverBackdropEnabled = settings => settings?.coverBackdropEnabled !== false

const readerSpreadTextureSlotPageAttribute = 'data-navic-surface-texture-slot-page'

export const readerSpreadPageTextureSlots = (textureSlots, resolveVariant, spreadMode) => {
  if (spreadMode !== 'spread') return textureSlots || []
  return (textureSlots || []).map(slot => {
    if (!slot?.key) return slot
    return {
      ...slot,
      spreadPages: [
        { page: 'left', key: `${slot.key}|left`, variant: resolveVariant(`${slot.key}|left`) },
        { page: 'right', key: `${slot.key}|right`, variant: resolveVariant(`${slot.key}|right`) },
      ],
    }
  })
}

export const readerPaperTextureTransform = variant => {
  const transforms = []
  if (variant?.mirrored) transforms.push('scaleX(-1)')
  if (variant?.rotate180) transforms.push('rotate(180deg)')
  return transforms.length ? transforms.join(' ') : 'none'
}

export const readerSurfaceTextureSlotTransform = ({
  slot = 'current',
  scrollOffset = null,
  width = 0,
  height = 0,
  flowMode,
  readerDirection,
} = {}) => {
  const x = Number(scrollOffset?.x)
  const y = Number(scrollOffset?.y)
  const offsetX = Number.isFinite(x) ? Math.round(x) : 0
  const offsetY = Number.isFinite(y) ? Math.round(y) : 0
  const horizontal = flowMode !== ReaderFlowPagedVertical
  const nextSign = horizontal && readerDirection === ReaderDirectionRtl ? -1 : 1
  const slotSign = slot === 'next' ? nextSign : slot === 'previous' ? -nextSign : 0
  const slotX = horizontal ? Math.round(Number(width) || 0) * slotSign : 0
  const slotY = horizontal ? 0 : Math.round(Number(height) || 0) * slotSign
  return `translate3d(${offsetX + slotX}px, ${offsetY + slotY}px, 0)`
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
  if (settings?.paperTextureEnabled === false) return '0'
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return '0'
    case ReaderThemeSepia:
      return '0.46'
    case 'dark':
    case 'dusk':
      return '0.08'
    default:
      return '0.28'
  }
}

export const readerSurfacePageBorderOverlayOpacity = settings => {
  if (settings?.pageEdgesEnabled === false) return '0'
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return '0'
    case ReaderThemeSepia:
      return '1'
    case 'dark':
    case 'dusk':
      return '0.42'
    default:
      return '0.58'
  }
}

export const readerSurfacePageStainOverlayOpacity = settings => {
  if (settings?.paperStainsEnabled === false) return '0'
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return '0'
    case ReaderThemeSepia:
      return '0.72'
    case 'dark':
    case 'dusk':
      return '0.24'
    default:
      return '0.38'
  }
}

export const readerSurfaceSpreadGutterOverlayOpacity = settings => {
  if (settings?.pageEdgesEnabled === false) return '0'
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return '0'
    case ReaderThemeSepia:
      return '0.88'
    case 'dark':
    case 'dusk':
      return '0.38'
    default:
      return '0.5'
  }
}

export const readerSurfacePageBorderOverlayFilter = settings => {
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return 'none'
    case ReaderThemeSepia:
      return 'contrast(1.9) saturate(1.16) brightness(0.94)'
    case 'dark':
    case 'dusk':
      return 'contrast(1.35) saturate(1.08)'
    default:
      return 'contrast(1.3) saturate(1.06)'
  }
}

export const readerSurfacePageStainOverlayFilter = settings => {
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return 'none'
    case ReaderThemeSepia:
      return 'contrast(1.55) saturate(1.08) brightness(0.98)'
    case 'dark':
    case 'dusk':
      return 'contrast(1.2) saturate(1.04)'
    default:
      return 'contrast(1.16) saturate(1.03)'
  }
}

const readerOverlaySiblingAsset = (variant, siblingAssets) => {
  const textureIndex = Number(variant?.textureIndex)
  if (Number.isFinite(textureIndex) && siblingAssets?.[textureIndex]) return siblingAssets[textureIndex]
  return variant?.asset || ''
}

export const readerPageEdgeWearOverlayAsset = variant =>
  readerOverlaySiblingAsset(variant, ReaderPageEdgeWearOverlayAssets)

export const readerPageEdgeRimOverlayAsset = variant =>
  readerOverlaySiblingAsset(variant, ReaderPageEdgeRimOverlayAssets)

export const readerSpreadGutterHighlightOverlayAsset = variant =>
  readerOverlaySiblingAsset(variant, ReaderSpreadGutterHighlightOverlayAssets)

export const readerSurfacePageBorderOverlayBackgroundImage = borderOverlayVariant => {
  if (!borderOverlayVariant?.asset) return 'none'
  const wearAsset = readerPageEdgeWearOverlayAsset(borderOverlayVariant)
  const rimAsset = readerPageEdgeRimOverlayAsset(borderOverlayVariant)
  return [
    `url("${readerAssetUrl(wearAsset)}")`,
    `url("${readerAssetUrl(rimAsset)}")`,
  ].join(', ')
}

export const readerPageEdgeOverlayMask = (page = 'full', geometry = null) => {
  if (!geometry) return 'none'
  const edgeInsets = geometry && geometry.edgeInsets ? geometry.edgeInsets : {}
  const outer = Math.max(1, Math.round(Number(edgeInsets.outer) || ReaderPageShellDefaultOuterEdgePx))
  const inner = Math.max(outer, Math.round(Number(edgeInsets.inner) || outer))
  const top = outer
  const bottom = outer
  const left = page === 'right' ? inner : outer
  const right = page === 'left' ? inner : outer
  const edgeScale = 1.35
  return [
    `linear-gradient(to bottom, #000 0%, #000 52%, transparent 100%) top / 100% ${Math.round(top * edgeScale)}px no-repeat`,
    `linear-gradient(to top, #000 0%, #000 52%, transparent 100%) bottom / 100% ${Math.round(bottom * edgeScale)}px no-repeat`,
    `linear-gradient(to right, #000 0%, #000 52%, transparent 100%) left / ${Math.round(left * edgeScale)}px 100% no-repeat`,
    `linear-gradient(to left, #000 0%, #000 52%, transparent 100%) right / ${Math.round(right * edgeScale)}px 100% no-repeat`,
  ].join(', ')
}

export const readerPaperTextureBackgroundImage = textureVariant =>
  textureVariant?.asset ? `url("${readerAssetUrl(textureVariant.asset)}")` : 'none'

export const readerSurfacePageStainOverlayBackgroundImage = stainOverlayVariant =>
  stainOverlayVariant?.asset ? `url("${readerAssetUrl(stainOverlayVariant.asset)}")` : 'none'

export const readerSurfaceSpreadGutterOverlayBackgroundImage = gutterOverlayVariant => {
  if (!gutterOverlayVariant?.asset) return 'none'
  const shadowAsset = gutterOverlayVariant.asset
  const highlightAsset = readerSpreadGutterHighlightOverlayAsset(gutterOverlayVariant)
  return [
    `url("${readerAssetUrl(shadowAsset)}")`,
    `url("${readerAssetUrl(highlightAsset)}")`,
  ].join(', ')
}

const readerStaticPaperShellTextureVariant = textureSlots =>
  (textureSlots || []).find(slot => slot?.slot === 'current' && slot?.variant?.asset)?.variant ||
  (textureSlots || []).find(slot => slot?.variant?.asset)?.variant ||
  null

const readerStaticPaperShellFoldBackground = (settings, geometry) => {
  const theme = readerThemeKey(settings?.theme)
  if (theme === 'black') return 'linear-gradient(180deg, #000000, #000000)'
  if (geometry?.mode === 'spread') {
    return [
      'linear-gradient(90deg,',
      'rgba(105,72,36,.14) 0%,',
      'rgba(255,248,226,.05) 18%,',
      'rgba(255,255,255,.04) 45%,',
      'rgba(32,20,12,.34) 49.2%,',
      'rgba(12,8,6,.46) 50%,',
      'rgba(32,20,12,.34) 50.8%,',
      'rgba(255,255,255,.04) 55%,',
      'rgba(255,248,226,.06) 82%,',
      'rgba(105,72,36,.14) 100%)',
    ].join(' ')
  }
  return [
    'linear-gradient(90deg,',
    'rgba(28,18,10,.34) 0%,',
    'rgba(98,64,32,.16) 4.5%,',
    'rgba(255,255,255,.03) 11%,',
    'rgba(255,255,255,.02) 100%)',
  ].join(' ')
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

const ensureReaderSurfaceTextureSlot = (layer, slotName, attributeName) => {
  const selector = `[${attributeName}="${slotName}"]`
  let slot = layer?.querySelector?.(selector)
  if (!slot) {
    slot = document.createElement('div')
    slot.setAttribute(attributeName, slotName)
    slot.setAttribute('aria-hidden', 'true')
    layer.append(slot)
  }
  return slot
}

const ensureReaderSurfaceTextureSlotArtwork = (slot, page = 'full') => {
  const selector = `[data-navic-surface-texture-slot-artwork="true"][${readerSpreadTextureSlotPageAttribute}="${page}"]`
  let artwork = slot?.querySelector?.(selector)
  if (!artwork) {
    artwork = document.createElement('div')
    artwork.dataset.navicSurfaceTextureSlotArtwork = 'true'
    artwork.setAttribute(readerSpreadTextureSlotPageAttribute, page)
    artwork.setAttribute('aria-hidden', 'true')
    slot.append(artwork)
  }
  return artwork
}

const pruneReaderSurfaceTextureSlotArtwork = (slotLayer, pages) => {
  const active = new Set(pages)
  for (const artwork of Array.from(slotLayer?.querySelectorAll?.('[data-navic-surface-texture-slot-artwork="true"]') || [])) {
    if (!active.has(artwork.getAttribute(readerSpreadTextureSlotPageAttribute) || 'full')) artwork.remove()
  }
}

const pruneReaderSurfaceTextureSlots = (layer, textureSlots, attributeName) => {
  const active = new Set((textureSlots || []).map(slot => slot?.slot).filter(Boolean))
  for (const slot of Array.from(layer?.querySelectorAll?.(`[${attributeName}]`) || [])) {
    if (!active.has(slot.getAttribute(attributeName))) slot.remove()
  }
}

const ensureReaderStaticPaperShell = layer => {
  let shell = layer?.querySelector?.('[data-navic-static-paper-shell="true"]')
  if (!shell) {
    shell = document.createElement('div')
    shell.dataset.navicStaticPaperShell = 'true'
    shell.setAttribute('aria-hidden', 'true')
    layer?.append?.(shell)
  }
  return shell
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

export const ensureReaderSurfaceStainOverlayLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderSurfacePageStainOverlayLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicSurfacePageStainOverlayLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

export const ensureReaderSurfaceSpreadGutterOverlayLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderSurfaceSpreadGutterOverlayLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicSurfaceSpreadGutterOverlayLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

export const ensureReaderMovingPageTextureLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderMovingPagePaperTextureLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicMovingPagePaperTextureLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

export const ensureReaderMovingPageBorderOverlayLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderMovingPageBorderOverlayLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicMovingPageBorderOverlayLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

export const ensureReaderMovingPageStainOverlayLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderMovingPageStainOverlayLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicMovingPageStainOverlayLayer = 'true'
    layer.setAttribute('aria-hidden', 'true')
    readerRoot.append(layer)
  }
  return layer
}

export const ensureReaderMovingPageSpreadGutterOverlayLayer = () => {
  let layer = readerRoot.querySelector?.(ReaderMovingPageSpreadGutterOverlayLayerSelector)
  if (!layer) {
    layer = document.createElement('div')
    layer.dataset.navicMovingPageSpreadGutterOverlayLayer = 'true'
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

export const ensureReaderShellCoverBackdrop = layer => {
  let backdrop = layer?.querySelector?.('[data-navic-shell-cover-backdrop="true"]')
  if (!backdrop) {
    backdrop = document.createElement('div')
    backdrop.dataset.navicShellCoverBackdrop = 'true'
    backdrop.setAttribute('aria-hidden', 'true')
    layer?.append?.(backdrop)
  }
  return backdrop
}

export const ensureReaderShellCoverBackCover = layer => {
  let backCover = layer?.querySelector?.('[data-navic-shell-cover-back-cover="true"]')
  if (!backCover) {
    backCover = document.createElement('div')
    backCover.dataset.navicShellCoverBackCover = 'true'
    backCover.setAttribute('aria-hidden', 'true')
    layer?.append?.(backCover)
  }
  return backCover
}

export const ensureReaderShellCoverImage = layer => {
  let image = layer?.querySelector?.('[data-navic-shell-cover-image="true"]')
  if (!image) {
    image = document.createElement('img')
    image.dataset.navicShellCoverImage = 'true'
    image.decoding = 'async'
    image.loading = 'eager'
    layer?.append?.(image)
  }
  return image
}

export const updateReaderShellCoverLayer = (layer, coverUrl, settings, title = '') => {
  if (!layer || !coverUrl) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const shellGeometry = readerPageShellGeometryForViewport(settings, { coverMode: true })
  const coverRect = shellGeometry.cover?.foregroundRect || shellGeometry.coverRect || shellGeometry.shellRect
  const backCoverRect = shellGeometry.cover?.backCoverRect || shellGeometry.backCoverRect || shellGeometry.shellRect
  const backdropRect = shellGeometry.cover?.backdropRect || shellGeometry.shellRect
  const backdrop = ensureReaderShellCoverBackdrop(layer)
  const backCover = ensureReaderShellCoverBackCover(layer)
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
    display: 'grid',
    'align-items': 'center',
    'justify-content': 'center',
    overflow: 'hidden',
    background: 'linear-gradient(180deg, rgba(16,14,10,.98), rgba(6,5,4,.98))',
    'background-color': '#100e0a',
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
  if (readerCoverBackdropEnabled(settings)) {
    setStylesImportant(backdrop, {
      display: 'block',
      position: 'absolute',
      ...readerPageShellRectStyle(backdropRect),
      'background-image': `linear-gradient(rgba(0,0,0,.34), rgba(0,0,0,.46)), url("${coverUrl}")`,
      'background-size': 'cover, cover',
      'background-position': 'center center, center center',
      'background-repeat': 'no-repeat, no-repeat',
      filter: 'blur(30px) saturate(1.08)',
      transform: 'scale(1.1)',
      opacity: '0.86',
      'z-index': '0',
      'pointer-events': 'none',
    })
  } else {
    setStylesImportant(backdrop, {
      display: 'none',
      'background-image': 'none',
    })
  }
  setStylesImportant(backCover, {
    display: 'block',
    position: 'absolute',
    ...readerPageShellRectStyle(backCoverRect),
    'border-radius': `${Math.max(8, Math.round(Math.min(backCoverRect.width, backCoverRect.height) * 0.018))}px`,
    background: [
      'radial-gradient(circle at 18% 20%, rgba(255,255,255,.14), transparent 32%)',
      'radial-gradient(circle at 86% 78%, rgba(0,0,0,.18), transparent 34%)',
      'linear-gradient(135deg, rgba(255,255,255,.1), rgba(0,0,0,.22))',
      'linear-gradient(180deg, rgba(90,69,42,.92), rgba(48,34,22,.94))',
    ].join(', '),
    'background-blend-mode': 'screen, multiply, overlay, normal',
    border: '1px solid rgba(255,255,255,.12)',
    'box-shadow': '0 26px 72px rgba(0,0,0,.34), inset 0 0 0 1px rgba(255,255,255,.08), inset 0 0 56px rgba(0,0,0,.24)',
    opacity: '0.78',
    transform: 'translateX(-1.8%) rotate(-0.4deg)',
    'transform-origin': 'center',
    'z-index': '1',
    'pointer-events': 'none',
  })
  setStylesImportant(image, {
    display: 'block',
    position: 'absolute',
    ...readerPageShellRectStyle(coverRect),
    'max-width': `${Math.max(1, Math.round(coverRect.width))}px`,
    'max-height': `${Math.max(1, Math.round(coverRect.height))}px`,
    'object-fit': 'contain',
    'object-position': 'center center',
    background: 'transparent',
    'background-color': 'transparent',
    margin: '0',
    padding: '0',
    border: '0',
    'box-shadow': '0 18px 44px rgba(0,0,0,.42)',
    'border-radius': `${Math.max(6, Math.round(Math.min(coverRect.width, coverRect.height) * 0.014))}px`,
    'z-index': '2',
  })
}

export const updateReaderStaticPaperBackingLayer = (layer, textureSlots, settings, shellGeometry = null) => {
  if (!layer || !Array.isArray(textureSlots) || !textureSlots.some(slot => slot?.variant?.asset)) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const palette = readerThemePalette(settings?.theme)
  const geometry = shellGeometry || readerPageShellGeometryForViewport(settings)
  const shellStyle = readerPageShellRectStyle(geometry.shellRect)
  const textureVariant = readerStaticPaperShellTextureVariant(textureSlots)
  const textureEnabled = readerSurfacePaperTextureOpacity(settings) !== '0' && Boolean(textureVariant?.asset)
  const shell = ensureReaderStaticPaperShell(layer)
  const shellRadius = Math.max(12, Math.round(Math.min(geometry.shellRect.width, geometry.shellRect.height) * 0.014))
  const shellBackgroundImages = [
    readerStaticPaperShellFoldBackground(settings, geometry),
    textureEnabled ? readerPaperTextureBackgroundImage(textureVariant) : null,
  ].filter(Boolean)
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': '0',
    'pointer-events': 'none',
    background: palette.background,
    'background-color': palette.background,
    'background-image': 'none',
    'background-size': 'auto',
    'background-position': '0px 0px',
    'background-repeat': 'no-repeat',
    opacity: '1',
    'mix-blend-mode': 'normal',
    overflow: 'hidden',
    transform: 'none',
  })
  setStylesImportant(shell, {
    position: 'absolute',
    ...shellStyle,
    'border-radius': `${shellRadius}px`,
    overflow: 'hidden',
    'pointer-events': 'none',
    background: palette.background,
    'background-color': palette.background,
    'background-image': shellBackgroundImages.join(', '),
    'background-size': shellBackgroundImages.map(() => '100% 100%').join(', '),
    'background-position': shellBackgroundImages.map(() => 'center center').join(', '),
    'background-repeat': shellBackgroundImages.map(() => 'no-repeat').join(', '),
    'background-blend-mode': textureEnabled ? 'multiply, normal' : 'normal',
    'box-shadow': [
      '0 24px 70px rgba(0,0,0,.18)',
      'inset 0 0 0 1px rgba(70,48,24,.16)',
      'inset 0 0 56px rgba(91,62,31,.18)',
    ].join(', '),
    opacity: '1',
    transform: 'none',
  })
  layer.dataset.navicStaticPaperBackingShell = JSON.stringify(geometry.shellRect)
  shell.dataset.navicStaticPaperBackingShellMode = geometry.mode || ''
  shell.dataset.navicStaticPaperBackingAsset = textureEnabled ? textureVariant.asset || '' : ''
  layer.style.setProperty('--navic-reader-shell-left', shellStyle.left)
  layer.style.setProperty('--navic-reader-shell-top', shellStyle.top)
  layer.style.setProperty('--navic-reader-shell-width', shellStyle.width)
  layer.style.setProperty('--navic-reader-shell-height', shellStyle.height)
  layer.dataset.navicStaticPaperBackingAsset = textureEnabled ? textureVariant.asset || '' : ''
  layer.dataset.navicStaticPaperBackingOwner = 'shell'
}

export const updateReaderSurfaceTextureLayer = (layer, textureSlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', shellGeometry = null) => {
  if (!layer || !Array.isArray(textureSlots) || !textureSlots.some(slot => slot?.variant?.asset)) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const geometry = shellGeometry || readerPageShellGeometryForViewport(settings, { flowMode })
  const shellRect = geometry.shellRect || { left: 0, top: 0, width, height }
  const shellWidthPx = `${Math.max(1, Math.round(shellRect.width))}px`
  const shellHeightPx = `${Math.max(1, Math.round(shellRect.height))}px`
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': '2147483630',
    'pointer-events': 'none',
    'background-color': 'transparent',
    opacity: readerSurfacePaperTextureOpacity(settings),
    'mix-blend-mode': 'multiply',
    overflow: 'hidden',
    transform: 'none',
  })
  pruneReaderSurfaceTextureSlots(layer, textureSlots, 'data-navic-surface-paper-texture-slot')
  for (const slot of textureSlots) {
    if (!slot?.variant?.asset) continue
    const slotLayer = ensureReaderSurfaceTextureSlot(layer, slot.slot || 'current', 'data-navic-surface-paper-texture-slot')
    const pages = slot.spreadPages?.length ? slot.spreadPages : [{ page: 'full', key: slot.key, variant: slot.variant }]
    pruneReaderSurfaceTextureSlotArtwork(slotLayer, pages.map(page => page.page))
    slotLayer.dataset.navicSurfacePaperTextureKey = slot.key || ''
    slotLayer.dataset.navicSurfacePaperTextureAsset = slot.variant.asset || ''
    setStylesImportant(slotLayer, {
      position: 'absolute',
      inset: '0px',
      width: widthPx,
      height: heightPx,
      transform: readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection }),
      'transform-origin': 'center',
      'will-change': 'transform',
    })
    for (const page of pages) {
      if (!page?.variant?.asset) continue
      const artwork = ensureReaderSurfaceTextureSlotArtwork(slotLayer, page.page)
      const pageRect = readerPageShellRectForPage(geometry, page.page)
      const pageStyle = readerPageShellRectStyle(pageRect)
      setStylesImportant(artwork, {
        position: 'absolute',
        ...pageStyle,
        'background-image': readerPaperTextureBackgroundImage(page.variant),
        'background-size': `${shellWidthPx} ${shellHeightPx}`,
        'background-position': readerPaperTextureBackgroundPosition(null),
        'background-repeat': 'no-repeat',
        'background-color': 'transparent',
        transform: readerPaperTextureTransform(page.variant),
        'transform-origin': 'center',
      })
    }
  }
}

export const updateReaderMovingPageTextureLayer = (layer, textureSlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', shellGeometry = null) =>
  updateReaderSurfaceTextureLayer(layer, textureSlots, settings, scrollOffset, flowMode, readerDirection, shellGeometry)

export const updateReaderSurfaceBorderOverlayLayer = (layer, borderOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', shellGeometry = null) => {
  if (!layer || !Array.isArray(borderOverlaySlots) || !borderOverlaySlots.some(slot => slot?.variant?.asset)) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const geometry = shellGeometry || readerPageShellGeometryForViewport(settings, { flowMode })
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': '2147483631',
    'pointer-events': 'none',
    'background-color': 'transparent',
    opacity: readerSurfacePageBorderOverlayOpacity(settings),
    filter: readerSurfacePageBorderOverlayFilter(settings),
    'mix-blend-mode': 'multiply',
    overflow: 'hidden',
    transform: 'none',
  })
  pruneReaderSurfaceTextureSlots(layer, borderOverlaySlots, 'data-navic-surface-page-border-overlay-slot')
  for (const slot of borderOverlaySlots) {
    if (!slot?.variant?.asset) continue
    const slotLayer = ensureReaderSurfaceTextureSlot(layer, slot.slot || 'current', 'data-navic-surface-page-border-overlay-slot')
    const pages = slot.spreadPages?.length ? slot.spreadPages : [{ page: 'full', key: slot.key, variant: slot.variant }]
    pruneReaderSurfaceTextureSlotArtwork(slotLayer, pages.map(page => page.page))
    slotLayer.dataset.navicSurfacePageBorderOverlayKey = slot.key || ''
    slotLayer.dataset.navicSurfacePageBorderOverlayAsset = slot.variant.asset || ''
    setStylesImportant(slotLayer, {
      position: 'absolute',
      inset: '0px',
      width: widthPx,
      height: heightPx,
      transform: readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection }),
      'transform-origin': 'center',
      'will-change': 'transform',
    })
    for (const page of pages) {
      if (!page?.variant?.asset) continue
      const artwork = ensureReaderSurfaceTextureSlotArtwork(slotLayer, page.page)
      const pageStyle = readerPageShellRectStyle(readerPageShellRectForPage(geometry, page.page))
      const edgeMask = readerPageEdgeOverlayMask(page.page, geometry)
      setStylesImportant(artwork, {
        position: 'absolute',
        ...pageStyle,
        'background-image': readerSurfacePageBorderOverlayBackgroundImage(page.variant),
        'background-size': '100% 100%, 100% 100%',
        'background-position': [
          readerPaperTextureBackgroundPosition(null),
          readerPaperTextureBackgroundPosition(null),
        ].join(', '),
        'background-repeat': 'no-repeat, no-repeat',
        'background-blend-mode': 'multiply, screen',
        'background-color': 'transparent',
        '-webkit-mask': edgeMask,
        mask: edgeMask,
        transform: readerPaperTextureTransform(page.variant),
        'transform-origin': 'center',
      })
    }
  }
}

export const updateReaderMovingPageBorderOverlayLayer = (layer, borderOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', shellGeometry = null) =>
  updateReaderSurfaceBorderOverlayLayer(layer, borderOverlaySlots, settings, scrollOffset, flowMode, readerDirection, shellGeometry)

export const updateReaderSurfaceStainOverlayLayer = (layer, stainOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', shellGeometry = null) => {
  if (!layer || !Array.isArray(stainOverlaySlots) || !stainOverlaySlots.some(slot => slot?.variant?.asset)) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const geometry = shellGeometry || readerPageShellGeometryForViewport(settings, { flowMode })
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': '2147483630',
    'pointer-events': 'none',
    'background-color': 'transparent',
    opacity: readerSurfacePageStainOverlayOpacity(settings),
    filter: readerSurfacePageStainOverlayFilter(settings),
    'mix-blend-mode': 'multiply',
    overflow: 'hidden',
    transform: 'none',
  })
  pruneReaderSurfaceTextureSlots(layer, stainOverlaySlots, 'data-navic-surface-page-stain-overlay-slot')
  for (const slot of stainOverlaySlots) {
    if (!slot?.variant?.asset) continue
    const slotLayer = ensureReaderSurfaceTextureSlot(layer, slot.slot || 'current', 'data-navic-surface-page-stain-overlay-slot')
    const pages = slot.spreadPages?.length ? slot.spreadPages : [{ page: 'full', key: slot.key, variant: slot.variant }]
    pruneReaderSurfaceTextureSlotArtwork(slotLayer, pages.map(page => page.page))
    slotLayer.dataset.navicSurfacePageStainOverlayKey = slot.key || ''
    slotLayer.dataset.navicSurfacePageStainOverlayAsset = slot.variant.asset || ''
    setStylesImportant(slotLayer, {
      position: 'absolute',
      inset: '0px',
      width: widthPx,
      height: heightPx,
      transform: readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection }),
      'transform-origin': 'center',
      'will-change': 'transform',
    })
    for (const page of pages) {
      if (!page?.variant?.asset) continue
      const artwork = ensureReaderSurfaceTextureSlotArtwork(slotLayer, page.page)
      const pageStyle = readerPageShellRectStyle(readerPageShellRectForPage(geometry, page.page))
      setStylesImportant(artwork, {
        position: 'absolute',
        ...pageStyle,
        'background-image': readerSurfacePageStainOverlayBackgroundImage(page.variant),
        'background-size': '100% 100%',
        'background-position': readerPaperTextureBackgroundPosition(null),
        'background-repeat': 'no-repeat',
        'background-color': 'transparent',
        transform: readerPaperTextureTransform(page.variant),
        'transform-origin': 'center',
      })
    }
  }
}

export const updateReaderMovingPageStainOverlayLayer = (layer, stainOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', shellGeometry = null) =>
  updateReaderSurfaceStainOverlayLayer(layer, stainOverlaySlots, settings, scrollOffset, flowMode, readerDirection, shellGeometry)

export const updateReaderSurfaceSpreadGutterOverlayLayer = (layer, spreadGutterOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', shellGeometry = null) => {
  if (!layer || !Array.isArray(spreadGutterOverlaySlots) || !spreadGutterOverlaySlots.some(slot => slot?.variant?.asset)) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const geometry = shellGeometry || readerPageShellGeometryForViewport(settings, { flowMode })
  if (!geometry.gutterRect) return
  const gutterStyle = readerPageShellRectStyle(geometry.gutterRect)
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': '2147483632',
    'pointer-events': 'none',
    'background-color': 'transparent',
    opacity: readerSurfaceSpreadGutterOverlayOpacity(settings),
    filter: readerSurfacePageBorderOverlayFilter(settings),
    'mix-blend-mode': 'multiply',
    overflow: 'hidden',
    transform: 'none',
  })
  pruneReaderSurfaceTextureSlots(layer, spreadGutterOverlaySlots, 'data-navic-surface-spread-gutter-overlay-slot')
  for (const slot of spreadGutterOverlaySlots) {
    if (!slot?.variant?.asset) continue
    const slotLayer = ensureReaderSurfaceTextureSlot(layer, slot.slot || 'current', 'data-navic-surface-spread-gutter-overlay-slot')
    const artwork = ensureReaderSurfaceTextureSlotArtwork(slotLayer)
    slotLayer.dataset.navicSurfaceSpreadGutterOverlayKey = slot.key || ''
    slotLayer.dataset.navicSurfaceSpreadGutterOverlayAsset = slot.variant.asset || ''
    setStylesImportant(slotLayer, {
      position: 'absolute',
      ...gutterStyle,
      transform: readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection }),
      'transform-origin': 'center',
      'will-change': 'transform',
    })
    setStylesImportant(artwork, {
      position: 'absolute',
      inset: '0px',
      width: gutterStyle.width,
      height: gutterStyle.height,
      'background-image': readerSurfaceSpreadGutterOverlayBackgroundImage(slot.variant),
      'background-size': '100% 100%, 100% 100%',
      'background-position': [
        readerPaperTextureBackgroundPosition(null),
        readerPaperTextureBackgroundPosition(null),
      ].join(', '),
      'background-repeat': 'no-repeat, no-repeat',
      'background-blend-mode': 'multiply, screen',
      'background-color': 'transparent',
      transform: readerPaperTextureTransform(slot.variant),
      'transform-origin': 'center',
    })
  }
}

export const updateReaderMovingPageSpreadGutterOverlayLayer = (layer, spreadGutterOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', shellGeometry = null) =>
  updateReaderSurfaceSpreadGutterOverlayLayer(layer, spreadGutterOverlaySlots, settings, scrollOffset, flowMode, readerDirection, shellGeometry)

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
