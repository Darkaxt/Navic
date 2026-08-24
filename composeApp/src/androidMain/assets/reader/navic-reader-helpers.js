import {
  KomikkuNavigationRegionLeft,
  KomikkuNavigationRegionNext,
  KomikkuNavigationRegionPrevious,
  KomikkuNavigationRegionRight,
  ReaderDirectionRtl,
  ReaderFlowPagedVertical,
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
  ReaderSurfacePaperTextureLayerSelector,
  ReaderSurfaceSpreadGutterOverlayLayerSelector,
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
  readerThemeUsesWarmPaperTreatment,
} from './navic-reader-settings-core.js'
import {
  readerCoverBackdropEnabled,
  readerPaperLayoutProfile,
  readerPortraitBindingHintBoxShadow,
  readerSurfaceBackCoverBackground,
  readerSurfacePageDecorationGeometry,
  readerSurfacePaperBaseBackground,
} from './navic-reader-paper-surface.js'
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
export * from './navic-reader-paper-surface.js'
export * from './navic-reader-typography.js'

export const readerAssetUrl = path => new URL(path, import.meta.url).href
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
  if (readerThemeUsesWarmPaperTreatment(settings?.theme)) return '0.58'
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
  if (readerThemeUsesWarmPaperTreatment(settings?.theme)) return '0.72'
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return '0'
    case ReaderThemeSepia:
      return '0.64'
    case 'dark':
    case 'dusk':
      return '0.42'
    default:
      return '0.58'
  }
}

export const readerSurfacePageStainOverlayOpacity = settings => {
  if (settings?.paperStainsEnabled === false) return '0'
  if (readerThemeUsesWarmPaperTreatment(settings?.theme)) return '0.62'
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
  if (readerThemeUsesWarmPaperTreatment(settings?.theme)) return '0.92'
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
  if (readerThemeUsesWarmPaperTreatment(settings?.theme)) {
    return 'contrast(1.42) saturate(1.12) brightness(0.96)'
  }
  switch (readerThemeKey(settings?.theme)) {
    case 'black':
      return 'none'
    case ReaderThemeSepia:
      return 'contrast(1.32) saturate(1.08) brightness(0.98)'
    case 'dark':
    case 'dusk':
      return 'contrast(1.35) saturate(1.08)'
    default:
      return 'contrast(1.3) saturate(1.06)'
  }
}

export const readerSurfacePageStainOverlayFilter = settings => {
  if (readerThemeUsesWarmPaperTreatment(settings?.theme)) {
    return 'contrast(1.35) saturate(1.05) brightness(0.98)'
  }
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
  if (readerThemeUsesWarmPaperTreatment(settings?.theme)) return 'multiply'
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
  const backdrop = ensureReaderShellCoverBackdrop(layer)
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
    background: 'linear-gradient(180deg, #16130f 0%, #100e0a 58%, #080705 100%)',
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
      inset: '0px',
      width: '100%',
      height: '100%',
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
  setStylesImportant(image, {
    display: 'block',
    'grid-area': '1 / 1',
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
    'z-index': '1',
  })
}

export const updateReaderStaticPaperBackingLayer = (layer, textureSlots, settings, decorationGeometry = null) => {
  if (!layer || !Array.isArray(textureSlots) || !textureSlots.some(slot => slot?.variant?.asset)) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
  const palette = readerThemePalette(settings?.theme)
  const backCoverVisible = decorationGeometry?.backCoverVisible === true
  const backCoverBackground = readerSurfaceBackCoverBackground(settings, decorationGeometry)
  setStylesImportant(layer, {
    position: 'fixed',
    inset: '0px',
    width: widthPx,
    'min-width': widthPx,
    height: heightPx,
    'min-height': heightPx,
    'z-index': backCoverVisible ? '2147483629' : '0',
    'pointer-events': 'none',
    background: backCoverVisible ? backCoverBackground : palette.background,
    'background-color': backCoverVisible ? 'transparent' : palette.background,
    'background-image': backCoverVisible ? backCoverBackground : 'none',
    'background-size': backCoverVisible ? '100% 100%' : 'auto',
    'background-position': backCoverVisible ? 'center center' : '0px 0px',
    'background-repeat': 'no-repeat',
    opacity: '1',
    'mix-blend-mode': 'normal',
    overflow: 'hidden',
    transform: 'none',
    'box-shadow': 'none',
  })
  if (backCoverVisible) layer.setAttribute('data-navic-surface-back-cover-plane', 'true')
  else layer.removeAttribute('data-navic-surface-back-cover-plane')
  layer.dataset.navicStaticPaperBackingAsset = ''
  layer.dataset.navicStaticPaperBackingOwner = 'margin'
}

export const updateReaderSurfaceTextureLayer = (layer, textureSlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', decorationGeometry = null) => {
  if (!layer || !Array.isArray(textureSlots) || !textureSlots.some(slot => slot?.variant?.asset)) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
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
      const pageBounds = decorationGeometry?.pages?.[page.page] || null
      const pageWidth = pageBounds?.width || (page.page === 'full' ? widthPx : '50%')
      const pageLeft = pageBounds?.left || (page.page === 'right' ? '50%' : '0px')
      const paperBase = readerSurfacePaperBaseBackground(settings, decorationGeometry?.boundedSpread ? 'spread' : 'single')
      const layoutProfile = decorationGeometry?.layoutProfile || null
      setStylesImportant(artwork, {
        position: 'absolute',
        top: '0px',
        bottom: '0px',
        left: pageLeft,
        width: pageWidth,
        height: heightPx,
        'background-image': paperBase.image === 'none'
          ? readerPaperTextureBackgroundImage(page.variant)
          : `${paperBase.image}, ${readerPaperTextureBackgroundImage(page.variant)}`,
        'background-size': paperBase.image === 'none' ? 'cover' : '100% 100%, cover',
        'background-position': paperBase.image === 'none'
          ? readerPaperTextureBackgroundPosition(null)
          : `center center, ${readerPaperTextureBackgroundPosition(null)}`,
        'background-repeat': paperBase.image === 'none' ? 'no-repeat' : 'no-repeat, no-repeat',
        'background-color': paperBase.image === 'none' ? 'transparent' : paperBase.color,
        transform: readerPaperTextureTransform(page.variant),
        'transform-origin': 'center',
        'box-shadow': readerPortraitBindingHintBoxShadow(settings, layoutProfile),
      })
    }
  }
}

export const updateReaderMovingPageTextureLayer = (layer, textureSlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', decorationGeometry = null) =>
  updateReaderSurfaceTextureLayer(layer, textureSlots, settings, scrollOffset, flowMode, readerDirection, decorationGeometry)

export const updateReaderSurfaceBorderOverlayLayer = (layer, borderOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', decorationGeometry = null) => {
  if (!layer || !Array.isArray(borderOverlaySlots) || !borderOverlaySlots.some(slot => slot?.variant?.asset)) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
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
      const pageBounds = decorationGeometry?.pages?.[page.page] || null
      const pageWidth = pageBounds?.width || (page.page === 'full' ? widthPx : '50%')
      const pageLeft = pageBounds?.left || (page.page === 'right' ? '50%' : '0px')
      setStylesImportant(artwork, {
        position: 'absolute',
        top: '0px',
        bottom: '0px',
        left: pageLeft,
        width: pageWidth,
        height: heightPx,
        'background-image': readerSurfacePageBorderOverlayBackgroundImage(page.variant),
        'background-size': '100% 100%, 100% 100%',
        'background-position': [
          readerPaperTextureBackgroundPosition(null),
          readerPaperTextureBackgroundPosition(null),
        ].join(', '),
        'background-repeat': 'no-repeat, no-repeat',
        'background-blend-mode': 'multiply, screen',
        'background-color': 'transparent',
        transform: readerPaperTextureTransform(page.variant),
        'transform-origin': 'center',
      })
    }
  }
}

export const updateReaderMovingPageBorderOverlayLayer = (layer, borderOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', decorationGeometry = null) =>
  updateReaderSurfaceBorderOverlayLayer(layer, borderOverlaySlots, settings, scrollOffset, flowMode, readerDirection, decorationGeometry)

export const updateReaderSurfaceStainOverlayLayer = (layer, stainOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', decorationGeometry = null) => {
  if (!layer || !Array.isArray(stainOverlaySlots) || !stainOverlaySlots.some(slot => slot?.variant?.asset)) return
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
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
      const pageBounds = decorationGeometry?.pages?.[page.page] || null
      const pageWidth = pageBounds?.width || (page.page === 'full' ? widthPx : '50%')
      const pageLeft = pageBounds?.left || (page.page === 'right' ? '50%' : '0px')
      setStylesImportant(artwork, {
        position: 'absolute',
        top: '0px',
        bottom: '0px',
        left: pageLeft,
        width: pageWidth,
        height: heightPx,
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

export const updateReaderMovingPageStainOverlayLayer = (layer, stainOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '', decorationGeometry = null) =>
  updateReaderSurfaceStainOverlayLayer(layer, stainOverlaySlots, settings, scrollOffset, flowMode, readerDirection, decorationGeometry)

export const updateReaderSurfaceSpreadGutterOverlayLayer = (layer, spreadGutterOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '') => {
  if (!layer || !Array.isArray(spreadGutterOverlaySlots) || !spreadGutterOverlaySlots.some(slot => slot?.variant?.asset)) return
  const settledCurrentSlot = scrollOffset === null && spreadGutterOverlaySlots.every(slot => slot?.slot === 'current')
  const { width, height } = readerViewportSize()
  const widthPx = `${width}px`
  const heightPx = `${height}px`
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
      inset: '0px',
      width: widthPx,
      height: heightPx,
      transform: settledCurrentSlot ? 'none' : readerSurfaceTextureSlotTransform({ slot: slot.slot, scrollOffset, width, height, flowMode, readerDirection }),
      'transform-origin': 'center',
      'will-change': settledCurrentSlot ? 'auto' : 'transform',
    })
    setStylesImportant(artwork, {
      position: 'absolute',
      inset: '0px',
      width: widthPx,
      height: heightPx,
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

export const updateReaderMovingPageSpreadGutterOverlayLayer = (layer, spreadGutterOverlaySlots, settings, scrollOffset = null, flowMode = '', readerDirection = '') =>
  updateReaderSurfaceSpreadGutterOverlayLayer(layer, spreadGutterOverlaySlots, settings, scrollOffset, flowMode, readerDirection)

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
