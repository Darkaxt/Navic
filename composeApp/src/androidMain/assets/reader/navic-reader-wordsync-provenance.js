import { post } from './navic-reader-helpers.js'

export const ReaderCueV1DomUtf16Mode = 'cue-v1-dom-utf16'
export const ReaderWordSyncV1ExtractedUtf8Mode = 'wordsync-v1-extracted-utf8'

const ReaderRawSourceByteLimit = 16 * 1024 * 1024
const CanonicalSha256 = /^sha256:[0-9a-f]{64}$/
const WordTokenPattern = /[A-Za-z0-9]+(?:['’][A-Za-z0-9]+)?/gu
const NumericEntityPattern = /&#(?:[xX][0-9A-Fa-f]+|[0-9]+);?/g
const AsciiWhitespacePattern = /[\t\n\f\r ]+/g
const ScriptOpeningTag = '<script'
const ScriptClosingTag = '</script>'
const StyleOpeningTag = '<style'
const StyleClosingTag = '</style>'
const SelectedClosingTags = [
  '</p>', '</h1>', '</h2>', '</h3>', '</h4>', '</h5>', '</h6>',
  '</div>', '</section>', '</li>', '</br>',
]
const GoC1ReplacementTable = [
  0x20ac, 0x0081, 0x201a, 0x0192, 0x201e, 0x2026, 0x2020, 0x2021,
  0x02c6, 0x2030, 0x0160, 0x2039, 0x0152, 0x008d, 0x017d, 0x008f,
  0x0090, 0x2018, 0x2019, 0x201c, 0x201d, 0x2022, 0x2013, 0x2014,
  0x02dc, 0x2122, 0x0161, 0x203a, 0x0153, 0x009d, 0x017e, 0x0178,
]
const RawFragmentFields = [
  'wordBoundarySequence', 'rawProvenanceId', 'rawSpineIndex', 'rawByteStart', 'rawByteEnd',
  'rawProgressByteEnd', 'rawProgressFraction',
]
const LegacyRawForbiddenFields = [
  'fragmentId', 'textStart', 'textEnd', 'textProgressEnd', 'textProgressFraction',
  'ebookText', 'epubText', 'nextTextHref', 'nextTextStart', 'nextTextEnd', 'nextEbookText',
]

const exactNonBlankString = value =>
  typeof value === 'string' && value !== '' && value === value.trim()
const nonNegativeInteger = value => Number.isInteger(value) && value >= 0
const hasValue = (object, key) => object?.[key] !== undefined && object?.[key] !== null

export const validatedReaderOverlayCoordinateMode = fragment => {
  if (!fragment || typeof fragment !== 'object') return null
  const mode = fragment.coordinateMode == null
    ? ReaderCueV1DomUtf16Mode
    : fragment.coordinateMode
  if (mode === ReaderCueV1DomUtf16Mode) {
    return RawFragmentFields.some(key => hasValue(fragment, key)) ? null : mode
  }
  if (mode !== ReaderWordSyncV1ExtractedUtf8Mode) return null
  if (LegacyRawForbiddenFields.some(key => hasValue(fragment, key))) return null
  if (!exactNonBlankString(fragment.textHref)) return null
  if (!exactNonBlankString(fragment.rawProvenanceId)) return null
  if (!nonNegativeInteger(fragment.rawSpineIndex)) return null
  if (hasValue(fragment, 'wordBoundarySequence') && !nonNegativeInteger(fragment.wordBoundarySequence)) return null
  if (!nonNegativeInteger(fragment.rawByteStart)) return null
  if (!nonNegativeInteger(fragment.rawByteEnd) || fragment.rawByteEnd <= fragment.rawByteStart) return null
  if (
    hasValue(fragment, 'rawProgressByteEnd') &&
    (!Number.isInteger(fragment.rawProgressByteEnd) ||
      fragment.rawProgressByteEnd < fragment.rawByteStart ||
      fragment.rawProgressByteEnd > fragment.rawByteEnd)
  ) return null
  if (
    hasValue(fragment, 'rawProgressFraction') &&
    (!Number.isFinite(fragment.rawProgressFraction) ||
      fragment.rawProgressFraction < 0 || fragment.rawProgressFraction > 1)
  ) return null
  return mode
}

export const routeReaderOverlayCoordinateMode = (fragment, handlers) => {
  const mode = validatedReaderOverlayCoordinateMode(fragment)
  if (mode === ReaderWordSyncV1ExtractedUtf8Mode) return handlers.raw(fragment)
  if (mode === ReaderCueV1DomUtf16Mode) return handlers.cue(fragment)
  return handlers.reject(fragment)
}

export const paintReaderWordSyncOverlayTextRange = (runtime, fragment, overlayKey) => {
  const mode = validatedReaderOverlayCoordinateMode(fragment)
  if (mode === ReaderCueV1DomUtf16Mode) return null
  if (mode !== ReaderWordSyncV1ExtractedUtf8Mode) return false
  return runtime.rawTextProvenance.paint(fragment, runtime.contentEntries(), {
    overlayKey: fragment?.overlayKey || overlayKey,
    draw: runtime.readerMediaOverlayHighlightDraw(),
    options: {
      color: runtime.readerMediaOverlayHighlightColor(),
      writingMode: runtime.view?.renderer?.writingMode,
    },
  })
}

export const paintReaderWordSyncActiveOverlay = (runtime, fragment, overlayKey) => {
  if (validatedReaderOverlayCoordinateMode(fragment) !== ReaderWordSyncV1ExtractedUtf8Mode) return null
  runtime.clearOverlay({ preservePlayed: false, preserveAnimation: true })
  const highlighted = paintReaderWordSyncOverlayTextRange(runtime, fragment, overlayKey)
  runtime.mediaOverlayActiveFragment = highlighted ? fragment : null
  return highlighted
}

export const rejectReaderWordSyncOverlay = (runtime, fragment, reason) => {
  if (fragment?.coordinateMode !== ReaderWordSyncV1ExtractedUtf8Mode) return false
  runtime.clearOverlay({ preservePlayed: false })
  runtime.postOverlayFragmentInactive(fragment, reason)
  return true
}

const readerMediaOverlayAnchorReceiptFromPresentation = (
  runtime,
  fragment,
  ranges,
  presentation
) => {
  const boundarySequence = fragment?.wordBoundarySequence ?? fragment?.overlayRequestId
  if (!nonNegativeInteger(boundarySequence)) return null
  if (
    presentation?.scope !== 'live' ||
    !exactNonBlankString(presentation.token) ||
    !exactNonBlankString(presentation.foliateSessionId) ||
    !nonNegativeInteger(presentation.pageIndex) ||
    !nonNegativeInteger(presentation.rasterGeneration) ||
    !nonNegativeInteger(presentation.textureGeneration) ||
    !Number.isSafeInteger(presentation.foregroundMutationGeneration) ||
    presentation.foregroundMutationGeneration <= 0 ||
    !Number.isSafeInteger(presentation.presentationSequence) ||
    presentation.presentationSequence <= 0
  ) return null
  const pagePosition = runtime.currentPagePosition
  const currentPageIndex = Number(pagePosition?.pageIndex)
  const currentSpineIndex = Number(pagePosition?.spineIndex)
  if (
    !Number.isSafeInteger(currentPageIndex) ||
    currentPageIndex !== presentation.pageIndex ||
    !nonNegativeInteger(currentSpineIndex)
  ) return null
  const descriptor = runtime.pageTurnRasterDescriptor?.(presentation.pageIndex)
  if (
    descriptor?.visualPageOrdinal !== presentation.pageIndex ||
    descriptor?.spineIndex !== currentSpineIndex ||
    !exactNonBlankString(descriptor.paginationFingerprint) ||
    !exactNonBlankString(descriptor.layoutFingerprint) ||
    !exactNonBlankString(descriptor.decorationFingerprint)
  ) return null
  const canonicalCommit = runtime.pageTurnTextPageCommitIdentity?.()
  if (
    !nonNegativeInteger(canonicalCommit?.layoutGeneration) ||
    !nonNegativeInteger(canonicalCommit?.viewGeneration) ||
    !nonNegativeInteger(canonicalCommit?.commitSequence) ||
    canonicalCommit?.flow !== 'paginated' ||
    canonicalCommit?.index !== currentSpineIndex ||
    !nonNegativeInteger(canonicalCommit?.pageIndex) ||
    !Number.isSafeInteger(canonicalCommit?.pageCount) ||
    canonicalCommit.pageCount <= 0 ||
    canonicalCommit.pageIndex >= canonicalCommit.pageCount ||
    canonicalCommit.pageIndex !== descriptor.chapterPageIndex ||
    canonicalCommit.pageCount !== descriptor.chapterPageCount
  ) return null
  const captureGeometry = runtime.pageTurnCaptureGeometry?.()
  const pages = Array.isArray(captureGeometry?.pages) ? captureGeometry.pages : []
  if (
    !Number.isFinite(captureGeometry?.viewportWidth) ||
    captureGeometry.viewportWidth <= 0 ||
    !Number.isFinite(captureGeometry?.viewportHeight) ||
    captureGeometry.viewportHeight <= 0 ||
    !['single', 'spread'].includes(captureGeometry?.mode) ||
    pages.length === 0
  ) return null
  const resolvedRanges = Array.isArray(ranges)
    ? ranges.filter(range => range && typeof range.getClientRects === 'function')
    : []
  if (resolvedRanges.length === 0) return null
  const pageLocalRects = []
  for (const range of resolvedRanges) {
    const rangeDocument = range.startContainer?.ownerDocument ||
      range.commonAncestorContainer?.ownerDocument
    const rangeFrame = rangeDocument?.defaultView?.frameElement
    const frameRect = rangeFrame?.getBoundingClientRect?.()
    const frameLeft = rangeFrame ? Number(frameRect?.left) : 0
    const frameTop = rangeFrame ? Number(frameRect?.top) : 0
    if (!Number.isFinite(frameLeft) || !Number.isFinite(frameTop)) return null
    for (const clientRect of Array.from(range.getClientRects())) {
      const clientLeft = Number(clientRect?.left) + frameLeft
      const clientTop = Number(clientRect?.top) + frameTop
      const clientRight = Number(clientRect?.right) + frameLeft
      const clientBottom = Number(clientRect?.bottom) + frameTop
      if (
        ![clientLeft, clientTop, clientRight, clientBottom].every(Number.isFinite) ||
        clientRight <= clientLeft || clientBottom <= clientTop
      ) continue
      for (const page of pages) {
        const pageLeft = Number(page?.left)
        const pageTop = Number(page?.top)
        const pageWidth = Number(page?.width)
        const pageHeight = Number(page?.height)
        if (
          !['full', 'left', 'right'].includes(page?.role) ||
          ![pageLeft, pageTop, pageWidth, pageHeight].every(Number.isFinite) ||
          pageWidth <= 0 || pageHeight <= 0
        ) return null
        const left = Math.max(clientLeft, pageLeft)
        const top = Math.max(clientTop, pageTop)
        const right = Math.min(clientRight, pageLeft + pageWidth)
        const bottom = Math.min(clientBottom, pageTop + pageHeight)
        if (right <= left || bottom <= top) continue
        pageLocalRects.push({
          role: page.role,
          left: left - pageLeft,
          top: top - pageTop,
          width: right - left,
          height: bottom - top,
        })
      }
    }
  }
  if (pageLocalRects.length === 0) return null
  const previousAnchorGeneration = Number(runtime.wordSyncAnchorGeneration) || 0
  if (!nonNegativeInteger(previousAnchorGeneration) || previousAnchorGeneration >= Number.MAX_SAFE_INTEGER) {
    return null
  }
  const anchorGeneration = previousAnchorGeneration + 1
  runtime.wordSyncAnchorGeneration = anchorGeneration
  return {
    foliateSessionId: presentation.foliateSessionId,
    destinationCommitToken: presentation.token,
    visualPageOrdinal: presentation.pageIndex,
    spineIndex: currentSpineIndex,
    rasterGeneration: presentation.rasterGeneration,
    textureGeneration: presentation.textureGeneration,
    presentationMutationGeneration: presentation.foregroundMutationGeneration,
    presentationSequence: presentation.presentationSequence,
    anchorGeneration,
    boundarySequence,
    layoutGeneration: canonicalCommit.layoutGeneration,
    viewGeneration: canonicalCommit.viewGeneration,
    commitSequence: canonicalCommit.commitSequence,
    committedSpineIndex: canonicalCommit.index,
    committedChapterPageIndex: canonicalCommit.pageIndex,
    committedChapterPageCount: canonicalCommit.pageCount,
    paginationFingerprint: descriptor.paginationFingerprint,
    layoutFingerprint: descriptor.layoutFingerprint,
    readerSettingsRasterKey: descriptor.decorationFingerprint,
    captureGeometry,
    pageLocalRects,
  }
}

export const readerMediaOverlayAnchorReceipt = (runtime, fragment, ranges) =>
  readerMediaOverlayAnchorReceiptFromPresentation(
    runtime,
    fragment,
    ranges,
    runtime.pageTurnLivePresentationReceipt?.()
  )

const readerWordSyncAnchorReceiptFromPresentation = (
  runtime,
  fragment,
  presentation
) => {
  const range = runtime.rawTextProvenance.resolveRange(fragment, { applyProgress: true })
  return readerMediaOverlayAnchorReceiptFromPresentation(
    runtime,
    fragment,
    range ? [range] : [],
    presentation
  )
}

export const readerWordSyncAnchorReceipt = (runtime, fragment) => {
  const range = runtime.rawTextProvenance.resolveRange(fragment, { applyProgress: true })
  return readerMediaOverlayAnchorReceipt(runtime, fragment, range ? [range] : [])
}

export const postReaderWordSyncOverlayActive = (
  runtime,
  fragment,
  postEvent = post,
  anchorReceipt = readerWordSyncAnchorReceipt(runtime, fragment)
) => {
  runtime.exactWordSyncActiveRequestId = fragment?.overlayRequestId ?? null
  if (!anchorReceipt) return null
  postEvent({
    type: 'overlayFragmentActive',
    ...fragment,
    anchorReceipt,
  })
  return anchorReceipt
}

const republishReaderMediaOverlayAnchorFromPresentation = (
  runtime,
  presentation,
  postEvent
) => {
  const fragment = runtime?.mediaOverlayActiveFragment
  const mode = validatedReaderOverlayCoordinateMode(fragment)
  if (mode === ReaderWordSyncV1ExtractedUtf8Mode) {
    const anchorReceipt = readerWordSyncAnchorReceiptFromPresentation(
      runtime,
      fragment,
      presentation
    )
    return postReaderWordSyncOverlayActive(
      runtime,
      fragment,
      postEvent,
      anchorReceipt
    ) != null
  }
  if (mode !== ReaderCueV1DomUtf16Mode) return false
  const anchorReceipt = readerMediaOverlayAnchorReceiptFromPresentation(
    runtime,
    fragment,
    runtime.mediaOverlayActiveRanges,
    presentation
  )
  if (!anchorReceipt) return false
  postEvent({
    type: 'overlayFragmentActive',
    ...fragment,
    anchorReceipt,
  })
  return true
}

export const republishReaderMediaOverlayAnchor = (
  runtime,
  postEvent = post
) => {
  const presentation = runtime?.pageTurnLivePresentationReceipt?.() || null
  return presentation
    ? republishReaderMediaOverlayAnchorFromPresentation(runtime, presentation, postEvent)
    : false
}

export const readerLivePresentationReceiptAndRepublishActiveMediaOverlayAnchor = (
  runtime,
  postEvent = post
) => {
  const presentation = runtime?.pageTurnLivePresentationReceipt?.() || null
  if (presentation) {
    republishReaderMediaOverlayAnchorFromPresentation(runtime, presentation, postEvent)
  }
  return presentation
}

const presentReaderWordSyncOverlayFragment = (runtime, fragment) => {
  runtime.exactWordSyncActiveRequestId = fragment.overlayRequestId
  if (!runtime.rawTextProvenance.rangeIsVisible(fragment, runtime.committedVisibleTextRange)) {
    return 'outside-visible-page'
  }
  const anchorReceipt = readerWordSyncAnchorReceipt(runtime, fragment)
  if (!anchorReceipt) return 'anchor-rejected'
  if (!runtime.paintActiveMediaOverlayFragment(fragment)) return 'paint-rejected'
  postReaderWordSyncOverlayActive(runtime, fragment, post, anchorReceipt)
  return null
}

export const retryReaderWordSyncOverlayFragment = (runtime, fragment) =>
  validatedReaderOverlayCoordinateMode(fragment) === ReaderWordSyncV1ExtractedUtf8Mode &&
  presentReaderWordSyncOverlayFragment(runtime, fragment) == null

export const applyReaderWordSyncOverlayFragment = (runtime, fragment) => {
  const mode = validatedReaderOverlayCoordinateMode(fragment)
  if (mode === ReaderCueV1DomUtf16Mode) return false
  if (mode !== ReaderWordSyncV1ExtractedUtf8Mode) {
    runtime.rejectOverlayFragment(fragment, 'invalid-coordinate-mode')
    return true
  }
  if (runtime.deferExactWordSyncOverlayFragment?.(fragment) === true) return true
  const rejectionReason = presentReaderWordSyncOverlayFragment(runtime, fragment)
  if (rejectionReason) runtime.rejectOverlayFragment(fragment, rejectionReason)
  return true
}

const isGoSpace = codePoint =>
  (codePoint >= 0x09 && codePoint <= 0x0d) || codePoint === 0x20 ||
  codePoint === 0x85 || codePoint === 0xa0 || codePoint === 0x1680 ||
  (codePoint >= 0x2000 && codePoint <= 0x200a) || codePoint === 0x2028 ||
  codePoint === 0x2029 || codePoint === 0x202f || codePoint === 0x205f ||
  codePoint === 0x3000

const goTrimSpace = value => {
  let start = 0
  let end = value.length
  while (start < end) {
    const codePoint = value.codePointAt(start)
    if (!isGoSpace(codePoint)) break
    start += codePoint > 0xffff ? 2 : 1
  }
  while (end > start) {
    const trailing = value.charCodeAt(end - 1)
    const codePoint = trailing >= 0xdc00 && trailing <= 0xdfff && end > 1
      ? value.codePointAt(end - 2)
      : trailing
    if (!isGoSpace(codePoint)) break
    end -= codePoint > 0xffff ? 2 : 1
  }
  return start === 0 && end === value.length ? value : value.slice(start, end)
}

const decodeGoNumericEntity = entity => {
  const hexadecimal = entity.length > 2 && (entity[2] === 'x' || entity[2] === 'X')
  const digitStart = hexadecimal ? 3 : 2
  const digitEnd = entity.endsWith(';') ? entity.length - 1 : entity.length
  const base = hexadecimal ? 16 : 10
  let value = 0
  let overflow = false
  for (let index = digitStart; index < digitEnd; index += 1) {
    const digit = Number.parseInt(entity[index], base)
    if (value > Math.floor((0x10ffff - digit) / base)) overflow = true
    else if (!overflow) value = value * base + digit
  }
  const codePoint = overflow || value === 0 || (value >= 0xd800 && value <= 0xdfff)
    ? 0xfffd
    : value >= 0x80 && value <= 0x9f
      ? GoC1ReplacementTable[value - 0x80]
      : value
  return String.fromCodePoint(codePoint)
}

const browserUnescapeNamedEntities = value => {
  if (!value.includes('&')) return value
  const parsed = new DOMParser().parseFromString(`<body>${value}</body>`, 'text/html')
  return parsed.body.textContent || ''
}

const goHtmlUnescapeString = value => {
  let output = ''
  let sourceIndex = 0
  NumericEntityPattern.lastIndex = 0
  for (const match of value.matchAll(NumericEntityPattern)) {
    output += browserUnescapeNamedEntities(value.slice(sourceIndex, match.index))
    output += decodeGoNumericEntity(match[0])
    sourceIndex = match.index + match[0].length
  }
  output += browserUnescapeNamedEntities(value.slice(sourceIndex))
  return output
}

const foldedCharacterEquals = (left, right) =>
  left === right || left.toLowerCase() === right.toLowerCase() ||
  left.toUpperCase() === right.toUpperCase()

const matchesFoldedLiteralAt = (source, index, literal) => {
  if (index < 0 || index + literal.length > source.length) return false
  for (let offset = 0; offset < literal.length; offset += 1) {
    if (!foldedCharacterEquals(source[index + offset], literal[offset])) return false
  }
  return true
}

const foldedLiteralPositions = (source, literal) => {
  const positions = []
  let scanIndex = 0
  while (scanIndex < source.length) {
    const candidate = source.indexOf('<', scanIndex)
    if (candidate < 0) break
    if (matchesFoldedLiteralAt(source, candidate, literal)) positions.push(candidate)
    scanIndex = candidate + 1
  }
  return positions
}

const replaceScriptAndStyleElements = source => {
  const scriptClosings = foldedLiteralPositions(source, ScriptClosingTag)
  const styleClosings = foldedLiteralPositions(source, StyleClosingTag)
  let scriptClosingIndex = 0
  let styleClosingIndex = 0
  let copyStart = 0
  let scanIndex = 0
  const output = []
  while (scanIndex < source.length) {
    if (source[scanIndex] !== '<') {
      scanIndex += 1
      continue
    }
    const isScript = matchesFoldedLiteralAt(source, scanIndex, ScriptOpeningTag)
    const isStyle = !isScript && matchesFoldedLiteralAt(source, scanIndex, StyleOpeningTag)
    if (!isScript && !isStyle) {
      scanIndex += 1
      continue
    }
    const openingTag = isScript ? ScriptOpeningTag : StyleOpeningTag
    const closingTag = isScript ? ScriptClosingTag : StyleClosingTag
    const closingPositions = isScript ? scriptClosings : styleClosings
    let closingIndex = isScript ? scriptClosingIndex : styleClosingIndex
    const minimumClosingIndex = scanIndex + openingTag.length
    while (closingIndex < closingPositions.length &&
      closingPositions[closingIndex] < minimumClosingIndex) closingIndex += 1
    if (isScript) scriptClosingIndex = closingIndex
    else styleClosingIndex = closingIndex
    if (closingIndex >= closingPositions.length) {
      scanIndex += 1
      continue
    }
    output.push(source.slice(copyStart, scanIndex), ' ')
    scanIndex = closingPositions[closingIndex] + closingTag.length
    copyStart = scanIndex
  }
  if (!output.length) return source
  output.push(source.slice(copyStart))
  return output.join('')
}

const replaceSelectedClosingTags = source => {
  let copyStart = 0
  let scanIndex = 0
  const output = []
  while (scanIndex < source.length) {
    if (source[scanIndex] !== '<') {
      scanIndex += 1
      continue
    }
    const tag = SelectedClosingTags.find(candidate =>
      matchesFoldedLiteralAt(source, scanIndex, candidate))
    if (!tag) {
      scanIndex += 1
      continue
    }
    output.push(source.slice(copyStart, scanIndex), '. ')
    scanIndex += tag.length
    copyStart = scanIndex
  }
  if (!output.length) return source
  output.push(source.slice(copyStart))
  return output.join('')
}

const replaceGenericTags = source => {
  let copyStart = 0
  let scanIndex = 0
  const output = []
  while (scanIndex < source.length) {
    const openingIndex = source.indexOf('<', scanIndex)
    if (openingIndex < 0) break
    const closingIndex = source.indexOf('>', openingIndex + 1)
    if (closingIndex < 0) break
    if (closingIndex === openingIndex + 1) {
      scanIndex = closingIndex + 1
      continue
    }
    output.push(source.slice(copyStart, openingIndex), ' ')
    scanIndex = closingIndex + 1
    copyStart = scanIndex
  }
  if (!output.length) return source
  output.push(source.slice(copyStart))
  return output.join('')
}

export const extractBinderyV1Text = source => {
  let text = replaceScriptAndStyleElements(source)
  text = replaceSelectedClosingTags(text)
  text = replaceGenericTags(text)
  text = goHtmlUnescapeString(text)
  text = text.replace(AsciiWhitespacePattern, ' ')
  return goTrimSpace(text)
}

const tokenizeExtractedText = text => {
  const encoder = new TextEncoder()
  const tokens = []
  let characterIndex = 0
  let byteOffset = 0
  WordTokenPattern.lastIndex = 0
  for (const match of text.matchAll(WordTokenPattern)) {
    const start = match.index
    const end = start + match[0].length
    byteOffset += encoder.encode(text.slice(characterIndex, start)).length
    const byteStart = byteOffset
    byteOffset += encoder.encode(text.slice(start, end)).length
    tokens.push({ index: tokens.length, text: match[0], byteStart, byteEnd: byteOffset })
    characterIndex = end
  }
  byteOffset += encoder.encode(text.slice(characterIndex)).length
  return { byteLength: byteOffset, tokens }
}

const nodeIsInExcludedElement = node => {
  let element = node?.nodeType === 1 ? node : node?.parentElement || node?.parentNode
  while (element?.nodeType === 1) {
    const name = String(element.localName || element.nodeName || '').toLowerCase()
    if (name === 'script' || name === 'style') return true
    element = element.parentElement || element.parentNode
  }
  return false
}

const textNodeIsExcluded = node => nodeIsInExcludedElement(node)

const mutationCanChangeDocumentTokens = mutation => {
  if (nodeIsInExcludedElement(mutation?.target)) return false
  if (mutation?.type === 'characterData') return true
  if (mutation?.type !== 'childList') return false
  return [...(mutation.addedNodes || []), ...(mutation.removedNodes || [])]
    .some(node => !nodeIsInExcludedElement(node) &&
      (node?.nodeType === 1 || node?.nodeType === 3 || node?.nodeType === 4))
}

const tokenizeDocument = doc => {
  const root = doc?.documentElement
  if (!root || typeof doc.createTreeWalker !== 'function') return []
  const showText = doc.defaultView?.NodeFilter?.SHOW_TEXT || 4
  const walker = doc.createTreeWalker(root, showText)
  const tokens = []
  let node = walker.nextNode()
  while (node) {
    if (!textNodeIsExcluded(node)) {
      const text = node.nodeValue || ''
      WordTokenPattern.lastIndex = 0
      for (const match of text.matchAll(WordTokenPattern)) {
        tokens.push({
          index: tokens.length,
          text: match[0],
          node,
          startOffset: match.index,
          endOffset: match.index + match[0].length,
        })
      }
    }
    node = walker.nextNode()
  }
  return tokens
}

const sha256 = async bytes => {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
  return `sha256:${Array.from(digest, byte => byte.toString(16).padStart(2, '0')).join('')}`
}

const validDescriptor = descriptor =>
  descriptor && typeof descriptor === 'object' &&
  exactNonBlankString(descriptor.id) && exactNonBlankString(descriptor.href) &&
  nonNegativeInteger(descriptor.spineIndex) &&
  typeof descriptor.sourceHash === 'string' && CanonicalSha256.test(descriptor.sourceHash) &&
  typeof descriptor.extractedTextHash === 'string' && CanonicalSha256.test(descriptor.extractedTextHash) &&
  nonNegativeInteger(descriptor.byteLength) && nonNegativeInteger(descriptor.tokenCount)

const exactSectionMapping = (book, descriptor) => {
  const resources = book?.resources
  if (!resources || !Array.isArray(resources.spine) || typeof resources.getItemByID !== 'function') return null
  if (!Array.isArray(book.sections)) return null
  let compactIndex = 0
  let target = null
  resources.spine.forEach((spineItem, spineIndex) => {
    const item = resources.getItemByID(spineItem?.idref)
    if (!item) return
    const section = book.sections[compactIndex]
    if (spineIndex === descriptor.spineIndex) target = { compactIndex, spineIndex, item, section }
    compactIndex += 1
  })
  if (!target || target.item?.href !== descriptor.href || target.section?.id !== descriptor.href) return null
  if (target.section?.href != null && target.section.href !== descriptor.href) return null
  return target
}

const loadedContentForMapping = (contents, mapping) =>
  (contents || []).find(content =>
    content?.doc && Number.isInteger(Number(content.index)) &&
    Number(content.index) === mapping.compactIndex
  ) || null

export class ReaderWordSyncProvenanceStore {
  constructor({ postStatus, maxSourceBytes = ReaderRawSourceByteLimit } = {}) {
    this.postStatus = typeof postStatus === 'function' ? postStatus : () => {}
    this.maxSourceBytes = Math.min(ReaderRawSourceByteLimit, Math.max(1, Number(maxSourceBytes) || 0))
    this.descriptors = new Map()
    this.readyById = new Map()
    this.attemptById = new Map()
  }

  clear() {
    for (const ready of this.readyById.values()) ready.observer?.disconnect()
    for (const id of this.descriptors.keys()) this.bumpAttempt(id)
    this.descriptors.clear()
    this.readyById.clear()
  }

  async install(descriptor, book, contents = []) {
    if (!validDescriptor(descriptor)) {
      if (exactNonBlankString(descriptor?.id)) {
        this.bumpAttempt(descriptor.id)
        this.descriptors.delete(descriptor.id)
        this.postRejected(descriptor.id, 'invalid-descriptor')
      }
      return false
    }
    const trusted = Object.freeze({ ...descriptor })
    this.descriptors.set(trusted.id, trusted)
    this.clearReady(trusted.id)
    this.postPending(trusted.id, 'content-not-loaded')
    return this.mapDescriptorSafely(trusted, book, contents)
  }

  async mapLoadedDocuments(book, contents = []) {
    const results = []
    for (const descriptor of this.descriptors.values()) {
      results.push(await this.mapDescriptorSafely(descriptor, book, contents))
    }
    return results.every(Boolean)
  }

  async mapDescriptorSafely(descriptor, book, contents) {
    const attempt = this.bumpAttempt(descriptor.id)
    this.clearReady(descriptor.id)
    try {
      return await this.mapDescriptor(descriptor, book, contents, attempt)
    } catch (_) {
      this.postRejectedIfCurrent(descriptor, attempt, 'source-unavailable')
      return false
    }
  }

  async mapDescriptor(descriptor, book, contents, attempt) {
    const mapping = exactSectionMapping(book, descriptor)
    if (!mapping) {
      this.postRejectedIfCurrent(descriptor, attempt, 'section-mismatch')
      return false
    }
    const content = loadedContentForMapping(contents, mapping)
    if (!content) {
      if (this.isCurrentAttempt(descriptor, attempt)) {
        this.postPending(descriptor.id, 'content-not-loaded')
      }
      return false
    }
    this.postPending(descriptor.id)
    let bytes
    try {
      const blob = await book.loadBlob(descriptor.href)
      if (!blob || typeof blob.arrayBuffer !== 'function') throw new Error('source unavailable')
      if (Number.isFinite(blob.size) && blob.size > this.maxSourceBytes) {
        this.postRejectedIfCurrent(descriptor, attempt, 'source-too-large')
        return false
      }
      bytes = new Uint8Array(await blob.arrayBuffer())
    } catch (_) {
      this.postRejectedIfCurrent(descriptor, attempt, 'source-unavailable')
      return false
    }
    if (!this.isCurrentAttempt(descriptor, attempt)) return false
    if (bytes.byteLength > this.maxSourceBytes) {
      this.postRejectedIfCurrent(descriptor, attempt, 'source-too-large')
      return false
    }
    const sourceHash = await sha256(bytes)
    if (!this.isCurrentAttempt(descriptor, attempt)) return false
    if (sourceHash !== descriptor.sourceHash) {
      this.postRejectedIfCurrent(descriptor, attempt, 'source-hash-mismatch')
      return false
    }
    let source
    try {
      source = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes)
    } catch (_) {
      this.postRejectedIfCurrent(descriptor, attempt, 'invalid-utf8')
      return false
    }
    const extracted = extractBinderyV1Text(source)
    const extractedBytes = new TextEncoder().encode(extracted)
    const extractedTextHash = await sha256(extractedBytes)
    if (!this.isCurrentAttempt(descriptor, attempt)) return false
    if (extractedTextHash !== descriptor.extractedTextHash) {
      this.postRejectedIfCurrent(descriptor, attempt, 'extracted-hash-mismatch')
      return false
    }
    if (extractedBytes.byteLength !== descriptor.byteLength) {
      this.postRejectedIfCurrent(descriptor, attempt, 'extracted-length-mismatch')
      return false
    }
    const sourceTokenization = tokenizeExtractedText(extracted)
    if (sourceTokenization.byteLength !== descriptor.byteLength ||
      sourceTokenization.tokens.length !== descriptor.tokenCount) {
      this.postRejectedIfCurrent(descriptor, attempt, 'token-count-mismatch')
      return false
    }
    const domTokens = tokenizeDocument(content.doc)
    if (domTokens.length !== descriptor.tokenCount) {
      this.postRejectedIfCurrent(descriptor, attempt, 'token-count-mismatch')
      return false
    }
    if (domTokens.some((token, index) => token.text !== sourceTokenization.tokens[index].text)) {
      this.postRejectedIfCurrent(descriptor, attempt, 'token-sequence-mismatch')
      return false
    }
    if (!this.isCurrentAttempt(descriptor, attempt)) return false
    const tokens = sourceTokenization.tokens.map((sourceToken, index) => ({
      ...sourceToken,
      ...domTokens[index],
      index,
    }))
    const tokensByNode = new Map()
    for (const token of tokens) {
      const nodeTokens = tokensByNode.get(token.node)
      if (nodeTokens) nodeTokens.push(token)
      else tokensByNode.set(token.node, [token])
    }
    const ready = {
      descriptor,
      mapping,
      doc: content.doc,
      tokens,
      tokensByNode,
      tokenByByteStart: new Map(tokens.map(token => [token.byteStart, token])),
      tokenByByteEnd: new Map(tokens.map(token => [token.byteEnd, token])),
      valid: true,
      observer: null,
    }
    const Observer = content.doc.defaultView?.MutationObserver || globalThis.MutationObserver
    if (typeof Observer === 'function') {
      ready.observer = new Observer(mutations => {
        if (mutations.some(mutationCanChangeDocumentTokens)) {
          this.invalidateDocument(descriptor.id, ready)
        }
      })
      ready.observer.observe(content.doc.documentElement, {
        subtree: true,
        childList: true,
        characterData: true,
      })
    }
    this.readyById.set(descriptor.id, ready)
    this.postReady(descriptor.id)
    return true
  }

  resolveRange(fragment, { applyProgress = false } = {}) {
    if (validatedReaderOverlayCoordinateMode(fragment) !== ReaderWordSyncV1ExtractedUtf8Mode) return null
    const ready = this.currentReady(fragment.rawProvenanceId)
    if (!ready || fragment.textHref !== ready.descriptor.href ||
      fragment.rawSpineIndex !== ready.descriptor.spineIndex) return null
    const startToken = ready.tokenByByteStart.get(fragment.rawByteStart)
    const fullEndToken = ready.tokenByByteEnd.get(fragment.rawByteEnd)
    if (!startToken || !fullEndToken || fullEndToken.index < startToken.index) return null
    let endToken = fullEndToken
    if (hasValue(fragment, 'rawProgressByteEnd')) {
      const progressToken = ready.tokenByByteEnd.get(fragment.rawProgressByteEnd)
      if (!progressToken || progressToken.index < startToken.index ||
        progressToken.index > fullEndToken.index) return null
      if (applyProgress) endToken = progressToken
    } else if (applyProgress && hasValue(fragment, 'rawProgressFraction')) {
      const tokenCount = fullEndToken.index - startToken.index + 1
      const progressedTokenCount = Math.max(
        1,
        Math.min(tokenCount, Math.ceil(tokenCount * fragment.rawProgressFraction))
      )
      endToken = ready.tokens[startToken.index + progressedTokenCount - 1]
    }
    const range = ready.doc.createRange()
    try {
      range.setStart(startToken.node, startToken.startOffset)
      range.setEnd(endToken.node, endToken.endOffset)
    } catch (_) {
      this.invalidateDocument(fragment.rawProvenanceId, ready)
      return null
    }
    return range.collapsed ? null : range
  }

  paint(fragment, contents, { overlayKey = 'navic-media-overlay-active', draw, options } = {}) {
    const range = this.resolveRange(fragment, { applyProgress: true })
    if (!range) return false
    const ready = this.readyById.get(fragment.rawProvenanceId)
    const content = (contents || []).find(candidate =>
      candidate?.doc === ready?.doc && Number(candidate.index) === ready.mapping.compactIndex
    )
    if (!content?.overlayer || typeof content.overlayer.add !== 'function') return false
    try {
      content.overlayer.add(overlayKey, range, draw, options)
      return true
    } catch (_) {
      return false
    }
  }

  rangeIsVisible(fragment, visibleRange) {
    const rawRange = this.resolveRange(fragment)
    const committedRange = visibleRange?.domRange || visibleRange
    if (!rawRange || !committedRange ||
      rawRange.startContainer?.ownerDocument !== committedRange.startContainer?.ownerDocument) return false
    try {
      const RangeClass = rawRange.startContainer.ownerDocument.defaultView?.Range || globalThis.Range
      return rawRange.compareBoundaryPoints(RangeClass.END_TO_START, committedRange) < 0 &&
        rawRange.compareBoundaryPoints(RangeClass.START_TO_END, committedRange) > 0
    } catch (_) {
      return false
    }
  }

  rawFieldsForRange(range) {
    const ready = this.readyForRange(range)
    if (!ready || range.collapsed || !ready.tokens.length) return null
    const RangeClass = ready.doc.defaultView?.Range || globalThis.Range
    const startBoundary = ready.doc.createRange()
    const endBoundary = ready.doc.createRange()
    const tokenPoint = ready.doc.createRange()
    try {
      startBoundary.setStart(range.startContainer, range.startOffset)
      startBoundary.collapse(true)
      endBoundary.setStart(range.endContainer, range.endOffset)
      endBoundary.collapse(true)
      const compareTokenPoint = (token, offset, boundary) => {
        tokenPoint.setStart(token.node, offset)
        tokenPoint.collapse(true)
        return tokenPoint.compareBoundaryPoints(RangeClass.START_TO_START, boundary)
      }
      let low = 0
      let high = ready.tokens.length
      while (low < high) {
        const middle = Math.floor((low + high) / 2)
        const token = ready.tokens[middle]
        if (compareTokenPoint(token, token.endOffset, startBoundary) > 0) high = middle
        else low = middle + 1
      }
      const start = ready.tokens[low]
      low = 0
      high = ready.tokens.length
      while (low < high) {
        const middle = Math.floor((low + high) / 2)
        const token = ready.tokens[middle]
        if (compareTokenPoint(token, token.startOffset, endBoundary) < 0) low = middle + 1
        else high = middle
      }
      const end = ready.tokens[low - 1]
      if (!start || !end || end.index < start.index) return null
      return {
        rawProvenanceId: ready.descriptor.id,
        rawSpineIndex: ready.descriptor.spineIndex,
        rawByteStart: start.byteStart,
        rawByteEnd: end.byteEnd,
      }
    } catch (_) {
      return null
    } finally {
      startBoundary.detach?.()
      endBoundary.detach?.()
      tokenPoint.detach?.()
    }
  }

  rawFieldsForPoint(range) {
    const ready = this.readyForRange(range)
    if (!ready || !range.collapsed) return null
    const nodeTokens = ready.tokensByNode.get(range.startContainer) || []
    let low = 0
    let high = nodeTokens.length
    while (low < high) {
      const middle = Math.floor((low + high) / 2)
      if (nodeTokens[middle].startOffset <= range.startOffset) low = middle + 1
      else high = middle
    }
    const token = nodeTokens[low - 1]
    if (!token || range.startOffset > token.endOffset) return null
    const prefix = (token.node.nodeValue || '').slice(token.startOffset, range.startOffset)
    const rawByteOffset = token.byteStart + new TextEncoder().encode(prefix).length
    if (rawByteOffset > token.byteEnd) return null
    return { rawProvenanceId: ready.descriptor.id, rawByteOffset }
  }

  readyForRange(range) {
    const doc = range?.startContainer?.ownerDocument
    if (!doc || range?.endContainer?.ownerDocument !== doc) return null
    for (const [id, candidate] of this.readyById.entries()) {
      const ready = this.currentReady(id)
      if (ready === candidate && ready.doc === doc) return ready
    }
    return null
  }

  currentReady(id) {
    const ready = this.readyById.get(id)
    if (!ready?.valid) return null
    const pendingMutations = ready.observer?.takeRecords?.() || []
    if (pendingMutations.some(mutationCanChangeDocumentTokens)) {
      this.invalidateDocument(id, ready)
      return null
    }
    return ready
  }

  invalidateDocument(id, ready) {
    if (!ready?.valid || this.readyById.get(id) !== ready) return
    ready.valid = false
    ready.observer?.disconnect()
    this.readyById.delete(id)
    this.bumpAttempt(id)
    this.postRejected(id, 'document-changed')
  }

  clearReady(id) {
    const ready = this.readyById.get(id)
    if (!ready) return
    ready.valid = false
    ready.observer?.disconnect()
    this.readyById.delete(id)
  }

  bumpAttempt(id) {
    const attempt = (this.attemptById.get(id) || 0) + 1
    this.attemptById.set(id, attempt)
    return attempt
  }

  isCurrentAttempt(descriptor, attempt) {
    return this.attemptById.get(descriptor.id) === attempt &&
      this.descriptors.get(descriptor.id) === descriptor
  }

  postRejectedIfCurrent(descriptor, attempt, reason) {
    if (this.isCurrentAttempt(descriptor, attempt)) this.postRejected(descriptor.id, reason)
  }

  postPending(provenanceId, reason) {
    this.postStatus({
      type: 'rawTextProvenanceStatus', provenanceId, status: 'pending',
      ...(reason ? { reason } : {}),
    })
  }

  postReady(provenanceId) {
    this.postStatus({ type: 'rawTextProvenanceStatus', provenanceId, status: 'ready' })
  }

  postRejected(provenanceId, reason) {
    this.clearReady(provenanceId)
    this.postStatus({
      type: 'rawTextProvenanceStatus', provenanceId, status: 'rejected', reason,
    })
  }
}
