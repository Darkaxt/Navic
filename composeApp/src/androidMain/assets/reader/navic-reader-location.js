import {
  optionalNumber,
} from './navic-reader-settings.js'
import {
  log,
  post,
  readerHrefMatches,
  readerLocationPostKey,
  readerTrace,
} from './navic-reader-helpers.js'

const ReaderRelocationCommitDelayMs = 180

const readerRelocationReasonIsExplicit = reason => {
  const normalized = String(reason || '').trim()
  return normalized !== '' && normalized !== 'relocate-committed'
}

const diagnosticNumber = value => {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

const readerRectIntersects = (rect, bounds) =>
  rect &&
  bounds &&
  rect.width > 0 &&
  rect.height > 0 &&
  rect.right >= bounds.left &&
  rect.left <= bounds.right &&
  rect.bottom >= bounds.top &&
  rect.top <= bounds.bottom

const readerVisibleBoundsForDocument = doc => {
  const win = doc?.defaultView
  const frame = win?.frameElement
  const frameRect = frame?.getBoundingClientRect?.()
  if (!win || !frameRect) {
    return {
      left: 0,
      top: 0,
      right: win?.innerWidth || doc?.documentElement?.clientWidth || 0,
      bottom: win?.innerHeight || doc?.documentElement?.clientHeight || 0,
    }
  }
  return {
    left: Math.max(0, -frameRect.left),
    top: Math.max(0, -frameRect.top),
    right: Math.min(frameRect.width, (window.innerWidth || frameRect.width) - frameRect.left),
    bottom: Math.min(frameRect.height, (window.innerHeight || frameRect.height) - frameRect.top),
  }
}

const readerTextNodeEntries = doc => {
  const entries = []
  const root = doc?.body
  if (!root || !doc.createTreeWalker) return entries
  const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT)
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

const readerTextNodeEntryForNode = (entries, node) => {
  if (!node) return null
  let current = node.nodeType === Node.TEXT_NODE ? node : null
  if (!current && node.nodeType === Node.ELEMENT_NODE) {
    current = entries.find(entry => node.contains?.(entry.node))?.node || null
  }
  return entries.find(entry => entry.node === current) || null
}

const readerCaretRangeAtPoint = (doc, x, y) => {
  if (typeof doc?.caretRangeFromPoint === 'function') return doc.caretRangeFromPoint(x, y)
  const position = doc?.caretPositionFromPoint?.(x, y)
  if (!position) return null
  const range = doc.createRange()
  range.setStart(position.offsetNode, position.offset)
  range.collapse(true)
  return range
}

const readerVisibleCaretOffsets = (doc, entries, bounds) => {
  const offsets = []
  const width = bounds.right - bounds.left
  const height = bounds.bottom - bounds.top
  if (width <= 0 || height <= 0) return offsets
  const xPoints = [0.12, 0.5, 0.88].map(value => bounds.left + width * value)
  const ySteps = Math.max(4, Math.min(12, Math.ceil(height / 96)))
  for (let index = 0; index <= ySteps; index += 1) {
    const y = bounds.top + (height * index) / ySteps
    for (const x of xPoints) {
      const range = readerCaretRangeAtPoint(doc, x, y)
      const entry = readerTextNodeEntryForNode(entries, range?.startContainer)
      if (!entry) continue
      const localOffset = Math.max(0, Math.min(entry.text.length, Number(range.startOffset) || 0))
      offsets.push(entry.start + localOffset)
    }
  }
  return offsets
}

const readerVisibleTextNodeOffsets = (doc, entries, bounds) => {
  const offsets = []
  for (const entry of entries) {
    if (!entry.text.trim()) continue
    const range = doc.createRange()
    try {
      range.selectNodeContents(entry.node)
      const visible = Array.from(range.getClientRects?.() || [])
        .some(rect => readerRectIntersects(rect, bounds))
      if (visible) {
        offsets.push(entry.start, entry.end)
      }
    } finally {
      range.detach?.()
    }
  }
  return offsets
}

const readerTextPointForOffset = (entries, requestedOffset, side) => {
  const finalOffset = entries.at(-1)?.end || 0
  const offset = Math.max(0, Math.min(finalOffset, requestedOffset))
  const entry = side === 'start'
    ? entries.find(candidate => offset >= candidate.start && offset < candidate.end) || entries.at(-1)
    : entries.find(candidate => offset > candidate.start && offset <= candidate.end) || entries[0]
  if (!entry) return null
  return {
    node: entry.node,
    offset: Math.max(0, Math.min(entry.text.length, offset - entry.start)),
  }
}

const readerDomRangeForOffsets = (doc, entries, visibleStart, visibleEnd) => {
  const start = readerTextPointForOffset(entries, visibleStart, 'start')
  const end = readerTextPointForOffset(entries, visibleEnd, 'end')
  if (!start || !end) return null
  const range = doc.createRange()
  try {
    range.setStart(start.node, start.offset)
    range.setEnd(end.node, end.offset)
    return range.collapsed ? null : range
  } catch (_) {
    return null
  }
}

const readerVisibleTextRangeForDocument = doc => {
  const entries = readerTextNodeEntries(doc)
  if (!entries.length) return null
  const bounds = readerVisibleBoundsForDocument(doc)
  if (bounds.right <= bounds.left || bounds.bottom <= bounds.top) return null
  const caretOffsets = readerVisibleCaretOffsets(doc, entries, bounds)
  const nodeOffsets = readerVisibleTextNodeOffsets(doc, entries, bounds)
  const offsets = caretOffsets.length >= 2 ? caretOffsets : nodeOffsets
  if (!offsets.length) return null
  const visibleStart = Math.max(0, Math.floor(Math.min(...offsets)))
  const visibleEnd = Math.max(visibleStart, Math.ceil(Math.max(...offsets)))
  if (visibleEnd <= visibleStart) return null
  const domRange = readerDomRangeForOffsets(doc, entries, visibleStart, visibleEnd)
  return {
    visibleStart,
    visibleEnd,
    ...(domRange ? { domRange } : {}),
  }
}

const readerRangeDocument = range => {
  const node = range?.startContainer || range?.commonAncestorContainer
  if (node?.nodeType === 9) return node
  return node?.ownerDocument || null
}

const readerTextOffsetForDomBoundary = (doc, entries, container, requestedOffset) => {
  if (!container) return null
  const offset = Number(requestedOffset)
  if (!Number.isInteger(offset) || offset < 0) return null
  if (container.nodeType === 3 || container.nodeType === 4) {
    const entry = entries.find(candidate => candidate.node === container)
    if (!entry || offset > entry.text.length) return null
    return entry.start + offset
  }
  const childCount = container.childNodes?.length || 0
  if (offset > childCount) return null
  const boundary = doc.createRange()
  try {
    boundary.setStart(container, offset)
    boundary.collapse(true)
    const RangeClass = doc.defaultView?.Range || globalThis.Range
    for (const entry of entries) {
      const point = doc.createRange()
      try {
        point.setStart(entry.node, 0)
        point.collapse(true)
        if (boundary.compareBoundaryPoints(RangeClass.START_TO_START, point) <= 0) {
          return entry.start
        }
      } finally {
        point.detach?.()
      }
    }
    return entries.at(-1)?.end || 0
  } catch (_) {
    return null
  } finally {
    boundary.detach?.()
  }
}

export const readerVisibleTextRangeForDomRange = range => {
  const doc = readerRangeDocument(range)
  const endNode = range?.endContainer
  const endDocument = endNode?.nodeType === 9 ? endNode : endNode?.ownerDocument
  if (!doc || endDocument !== doc) return null
  const entries = readerTextNodeEntries(doc)
  if (!entries.length) return null
  const visibleStart = readerTextOffsetForDomBoundary(
    doc,
    entries,
    range.startContainer,
    range.startOffset
  )
  const visibleEnd = readerTextOffsetForDomBoundary(
    doc,
    entries,
    range.endContainer,
    range.endOffset
  )
  if (!Number.isInteger(visibleStart) || !Number.isInteger(visibleEnd) || visibleEnd <= visibleStart) {
    return null
  }
  return { visibleStart, visibleEnd, domRange: range }
}

export const readerCommittedVisibleDomRange = (
  detail = {},
  sampledVisibleRange = {},
  loadedDocuments = []
) => {
  const sampledRange = sampledVisibleRange?.domRange || null
  const relocationRange = detail?.range || null
  if (!relocationRange) return sampledRange
  const relocationDocument = readerRangeDocument(relocationRange)
  if (sampledRange) {
    const sampledDocument = readerRangeDocument(sampledRange)
    return sampledDocument && relocationDocument === sampledDocument
      ? relocationRange
      : sampledRange
  }
  return relocationDocument && loadedDocuments.some(candidate =>
    (candidate?.doc || candidate) === relocationDocument)
    ? relocationRange
    : null
}

function currentFixedLayoutLocationDetail() {
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


function postCurrentLocationSnapshot(reason = 'snapshot', options = {}) {
  const detail = this.lastRelocateDetail || this.currentFixedLayoutLocationDetail()
  if (!detail) {
    log('location-snapshot:missing', reason)
    return { posted: false, skipped: 'missing-location', reason }
  }
  log('location-snapshot', reason)
  return this.postLocationChanged(detail, reason, options)
}


function postLocationChanged(detail, reason = 'relocate', options = {}) {
  if (this.nativePageDragPreview || this.pendingPageDragPreviewCommand) {
    readerTrace('page-drag-preview:retained-for-location', { reason })
  } else {
    this.removePageDragPreviewLayer()
  }
  if (this.detailTargetsCover(detail) && this.hasNonCoverReadableContent()) {
    this.updateReaderPageNumberLayer(null)
    log('location-changed:cover-skipped', reason)
    readerTrace('location:cover-skipped', { reason, detail })
    return { posted: false, skipped: 'cover', reason }
  }
  const sectionHref = this.sectionHrefForDetail(detail)
  const rawTocItem = detail.tocItem || {}
  const tocItem = sectionHref && rawTocItem.href && !readerHrefMatches(sectionHref, rawTocItem.href)
    ? {}
    : rawTocItem
  const pagePosition = options.preserveCurrentPagePosition === true
    ? this.currentPagePosition
    : this.tryUpdateReaderPageNumberLayer(detail, this.currentPagePosition, reason)
  this.maybeCompleteNativePageTurnSettlement(pagePosition)
  const settlement = this.peekNativePageTurnSettlement(pagePosition)
  const chapterPosition = this.chapterPagePosition(detail, pagePosition)
  const pageModelDiagnostics = {
    pageCountSource: pagePosition?.pageCountSource || null,
    paginationFingerprint: this.paginationFingerprint || null,
    paginationProfilePageCount: diagnosticNumber(this.paginationProfile?.pageCount),
    paginationProfileObservedChapterCount: diagnosticNumber(this.paginationProfile?.observedChapterCount),
    paginationProfileEstimatedChapterCount: diagnosticNumber(this.paginationProfile?.estimatedChapterCount),
    rawLocationCurrent: diagnosticNumber(detail.location?.current),
    rawLocationTotal: diagnosticNumber(detail.location?.total),
  }
  const message = {
    type: 'locationChanged',
    href: detail.href || sectionHref || tocItem.href,
    cfi: detail.cfi,
    progress: optionalNumber(detail.fraction ?? detail.progress ?? detail.totalProgress),
    pageIndex: pagePosition?.pageIndex,
    pageCount: pagePosition?.pageCount,
    chapterProgress: chapterPosition?.progress,
    chapterPageIndex: chapterPosition?.pageIndex,
    chapterPageCount: chapterPosition?.pageCount,
    tocTitle: tocItem.label || tocItem.title,
    foliateSessionId: this.foliateSessionId,
    pageTurnSettleToken: settlement?.token,
    pageTurnSettleSessionId: settlement?.foliateSessionId,
    pageTurnSettleRasterGeneration: settlement?.rasterGeneration,
    pageTurnSettleTextureGeneration: settlement?.textureGeneration,
    rangeCfi: detail.cfi || null,
    reason: reason || null,
    fraction: optionalNumber(detail.fraction),
    size: optionalNumber(detail.size),
    tocItemLabel: tocItem.label || tocItem.title || null,
    pageItemLabel: detail.pageItem?.label || detail.pageItem?.text || null,
    ...pageModelDiagnostics,
  }
  const locationKey = readerLocationPostKey(message)
  if (locationKey === this.lastPostedLocationKey && !options.forceDuplicatePost) {
    log('location-changed:duplicate-skipped', reason)
    readerTrace('location:duplicate-skipped', { reason })
    const duplicateHandled = this.handleDuplicatePageTurnRelocation?.(detail, reason)
    if (duplicateHandled) {
      return { posted: false, skipped: 'duplicate-adjacent-fallback', reason }
    }
    return { posted: false, skipped: 'duplicate', reason }
  }
  this.updateSurfacePaperTexture(detail, pagePosition, reason)
  this.committedRelocateDetail = detail
  readerTrace('location:page-model', {
    reason,
    pageIndex: message.pageIndex,
    pageCount: message.pageCount,
    chapterPageIndex: message.chapterPageIndex,
    chapterPageCount: message.chapterPageCount,
    ...pageModelDiagnostics,
  })
  log('location-page-model',
    `reason=${reason}`,
    `source=${pageModelDiagnostics.pageCountSource || 'none'}`,
    `page=${message.pageIndex ?? 'n/a'}/${message.pageCount ?? 'n/a'}`,
    `chapter=${message.chapterPageIndex ?? 'n/a'}/${message.chapterPageCount ?? 'n/a'}`,
    `profile=${pageModelDiagnostics.paginationProfilePageCount ?? 'n/a'}`,
    `observed=${pageModelDiagnostics.paginationProfileObservedChapterCount ?? 'n/a'}`,
    `raw=${pageModelDiagnostics.rawLocationCurrent ?? 'n/a'}/${pageModelDiagnostics.rawLocationTotal ?? 'n/a'}`,
    `fingerprint=${pageModelDiagnostics.paginationFingerprint || 'none'}`
  )
  const delivered = post(message)
  if (!delivered) {
    return { posted: false, skipped: 'bridge-delivery-failed', reason }
  }
  this.lastPostedLocationKey = locationKey
  if (settlement) this.consumeNativePageTurnSettlement(settlement.token)
  readerTrace('location:post', { reason })
  this.compareCommittedDuplicatePage(detail, message)
  const visibleTextRangeResult = this.postCurrentVisibleTextRange(detail, { ...options, source: reason || null })
  log('location-changed:posted', reason)
  if (detail.cfi) post({ type: 'cfiChanged', cfi: detail.cfi })
  if (tocItem.href || tocItem.label || tocItem.title) {
    post({ type: 'tocItemChanged', href: tocItem.href, title: tocItem.label || tocItem.title })
  }
  return {
    posted: true,
    reason,
    href: message.href || null,
    pageIndex: message.pageIndex ?? null,
    message,
    visibleTextRangeResult,
  }
}

function captureDuplicatePageBaselines(detail = {}) {
  const contents = this.contentEntries(detail)
  let captured = 0
  for (const content of contents) {
    const index = Number(content?.index)
    const sectionIndex = Number.isFinite(index) ? Math.floor(index) : undefined
    const section = sectionIndex == null ? undefined : this.view?.book?.sections?.[sectionIndex]
    const cover = sectionIndex == null ? false : this.sectionTargetsCover(section, sectionIndex)
    if (this.duplicatePageFingerprint.captureDocument(content?.doc, {
      section,
      cover,
      fixedLayout: this.view?.isFixedLayout === true,
    })) captured += 1
  }
  return captured
}

function compareCommittedDuplicatePage(detail = {}, message = {}) {
  const pageIndex = Number(message.pageIndex)
  if (!Number.isInteger(pageIndex) || pageIndex < 0) return false
  void this.duplicatePageFingerprint.compareCommittedPage({
    range: detail.range,
    pageOrdinal: pageIndex + 1,
    locator: detail.cfi,
  })
  return true
}

function postCurrentVisibleTextRange(detail = {}, options = {}) {
  const targetHref = this.sectionHrefForDetail(detail) || detail?.href || detail?.tocItem?.href || ''
  const contents = this.view?.renderer?.getContents?.() || []
  const currentVisibleRange = this.currentVisibleTextRangeForHref(targetHref, detail?.range)
  const committedDomRange = readerCommittedVisibleDomRange(detail, currentVisibleRange, contents)
  const rawFields = committedDomRange
    ? this.rawTextProvenance.rawFieldsForRange(committedDomRange) || {}
    : {}
  const visibleRange = currentVisibleRange
    ? {
        ...currentVisibleRange,
        ...(committedDomRange ? { domRange: committedDomRange } : {}),
        ...rawFields,
        rangeCfi: detail?.cfi || null,
        source: options.source || null,
      }
    : null
  if (!visibleRange) {
    log('visible-text-range:missing', targetHref || 'none')
    readerTrace('visible-text-range:missing', { href: targetHref || null })
    return { posted: false, skipped: 'missing-visible-range' }
  }
  const key = [
    visibleRange.textHref,
    visibleRange.visibleStart,
    visibleRange.visibleEnd,
    visibleRange.rangeCfi || '',
    visibleRange.source || '',
    visibleRange.rawProvenanceId || '',
    visibleRange.rawSpineIndex ?? '',
    visibleRange.rawByteStart ?? '',
    visibleRange.rawByteEnd ?? '',
  ].join('|')
  this.committedVisibleTextRange = visibleRange
  if (key === this.lastPostedVisibleTextRangeKey && !options.forceDuplicatePost) {
    return { posted: false, skipped: 'duplicate', visibleRange }
  }
  this.lastPostedVisibleTextRangeKey = key
  post({
    type: 'visibleTextRange',
    textHref: visibleRange.textHref,
    visibleStart: visibleRange.visibleStart,
    visibleEnd: visibleRange.visibleEnd,
    rangeCfi: visibleRange.rangeCfi,
    source: visibleRange.source,
    ...(visibleRange.rawProvenanceId ? {
      rawProvenanceId: visibleRange.rawProvenanceId,
      rawSpineIndex: visibleRange.rawSpineIndex,
      rawByteStart: visibleRange.rawByteStart,
      rawByteEnd: visibleRange.rawByteEnd,
    } : {}),
  })
  if (visibleRange.rawProvenanceId) {
    log('visible-text-range:posted', 'raw-exact')
    readerTrace('visible-text-range:posted', {
      coordinateMode: 'wordsync-v1-extracted-utf8',
      source: visibleRange.source,
    })
  } else {
    log(
      'visible-text-range:posted',
      visibleRange.textHref,
      `${visibleRange.visibleStart}-${visibleRange.visibleEnd}`
    )
    readerTrace('visible-text-range:posted', visibleRange)
  }
  return { posted: true, visibleRange }
}

function currentVisibleTextRangeForHref(href = '', preferredDomRange = null) {
  const targetHref = String(href || '').trim()
  const preferredDocument = readerRangeDocument(preferredDomRange)
  const renderer = this.view?.renderer || {}
  const contents = renderer.getContents?.() || []
  const candidates = []
  for (const content of contents) {
    const index = Number(content?.index)
    const section = Number.isFinite(index) ? this.view?.book?.sections?.[Math.floor(index)] : null
    const textHref = section?.href || content?.href || targetHref
    if (!content?.doc || !textHref) continue
    if (targetHref && textHref && !readerHrefMatches(textHref, targetHref)) continue
    const range = readerVisibleTextRangeForDocument(content.doc) ||
      (content.doc === preferredDocument
        ? readerVisibleTextRangeForDomRange(preferredDomRange)
        : null)
    if (!range) continue
    candidates.push({
      textHref,
      ...range,
    })
  }
  return candidates
    .sort((left, right) => (right.visibleEnd - right.visibleStart) - (left.visibleEnd - left.visibleStart))[0]
}


function beginControlledRelocation(reason) {
  const owner = {}
  this.controlledRelocateOwner = owner
  this.controlledRelocateReason = reason || null
  if (!String(reason || '').startsWith('page-turn:')) {
    this.surfacePaperTextureFallbackDirection = null
  }
  this.controlledRelocateStartSequence = this.relocateSequence
  log('controlled-relocate:begin', this.controlledRelocateReason || 'none', `seq=${this.controlledRelocateStartSequence}`)
  return owner
}


function cancelControlledRelocation(owner) {
  if (!owner || this.controlledRelocateOwner !== owner) return false
  log(
    'controlled-relocate:cancel',
    this.controlledRelocateReason || 'none',
    `seq=${this.relocateSequence}`,
    `start=${this.controlledRelocateStartSequence}`
  )
  this.controlledRelocateOwner = null
  this.controlledRelocateReason = null
  this.controlledRelocateStartSequence = this.relocateSequence
  return true
}


function consumeControlledRelocationReason(fallback = 'relocate-committed') {
  const reason = this.controlledRelocateReason || fallback
  log(
    'controlled-relocate:consume',
    reason,
    `fallback=${fallback}`,
    `stored=${this.controlledRelocateReason || 'none'}`,
    `seq=${this.relocateSequence}`,
    `start=${this.controlledRelocateStartSequence}`
  )
  this.controlledRelocateOwner = null
  this.controlledRelocateReason = null
  return reason
}


function pageTurnRelocationDetailIsStale(detail, reason) {
  if (!String(reason || '').startsWith('page-turn:')) return false
  const currentSectionKey = this.detailSectionKey(this.committedRelocateDetail)
  const candidateSectionKey = this.detailSectionKey(detail)
  if (!currentSectionKey || currentSectionKey !== candidateSectionKey) return false
  const currentPageIndex = Number(this.currentPagePosition?.pageIndex)
  const targetPageIndex = Number(this.pageTurnTargetPageIndex)
  if (!Number.isFinite(currentPageIndex) || !Number.isFinite(targetPageIndex)) return false
  const direction = String(reason || '').includes(':previous') ? 'previous' : 'next'
  if (direction === 'next' && targetPageIndex <= currentPageIndex) return false
  if (direction === 'previous' && targetPageIndex >= currentPageIndex) return false
  const currentHref = this.sectionHrefForDetail(this.committedRelocateDetail)
  const candidateHref = this.sectionHrefForDetail(detail)
  const hrefDidNotMove =
    (!currentHref && !candidateHref) ||
    readerHrefMatches(currentHref, candidateHref) ||
    currentHref === candidateHref
  const currentCfi = String(this.committedRelocateDetail?.cfi || this.committedRelocateDetail?.rangeCfi || '')
  const candidateCfi = String(detail?.cfi || detail?.rangeCfi || '')
  const currentLocationCurrent = Number(this.committedRelocateDetail?.location?.current)
  const candidateLocationCurrent = Number(detail?.location?.current)
  const unchangedLocator =
    hrefDidNotMove &&
    ((currentCfi && candidateCfi && currentCfi === candidateCfi) ||
      (
        Number.isFinite(currentLocationCurrent) &&
        Number.isFinite(candidateLocationCurrent) &&
        currentLocationCurrent === candidateLocationCurrent
      ))
  if (unchangedLocator) {
    readerTrace('relocate:ignored-unchanged-page-turn', {
      reason,
      direction,
      currentSectionKey,
      candidateSectionKey,
      currentPageIndex,
      targetPageIndex,
      currentHref,
      candidateHref,
      currentLocationCurrent: Number.isFinite(currentLocationCurrent) ? currentLocationCurrent : null,
      candidateLocationCurrent: Number.isFinite(candidateLocationCurrent) ? candidateLocationCurrent : null,
    })
    return true
  }
  const currentChapterPageIndex = Number(this.currentPagePosition?.chapterPageIndex)
  const currentChapterPageCount = Number(this.currentPagePosition?.chapterPageCount)
  const sameSectionBoundaryTurn =
    (direction === 'previous' && Number.isFinite(currentChapterPageIndex) && currentChapterPageIndex <= 0) ||
    (
      direction === 'next' &&
      Number.isFinite(currentChapterPageIndex) &&
      Number.isFinite(currentChapterPageCount) &&
      currentChapterPageCount > 0 &&
      currentChapterPageIndex >= currentChapterPageCount - 1
    )
  if (sameSectionBoundaryTurn) {
    readerTrace('relocate:ignored-boundary-page-turn', {
      reason,
      direction,
      currentSectionKey,
      candidateSectionKey,
      currentPageIndex,
      targetPageIndex,
      currentChapterPageIndex: Number.isFinite(currentChapterPageIndex) ? currentChapterPageIndex : null,
      currentChapterPageCount: Number.isFinite(currentChapterPageCount) ? currentChapterPageCount : null,
      currentHref,
      candidateHref,
    })
    return true
  }
  if (Number.isFinite(currentLocationCurrent) && Number.isFinite(candidateLocationCurrent)) {
    if (direction === 'next' && candidateLocationCurrent > currentLocationCurrent) return false
    if (direction === 'previous' && candidateLocationCurrent < currentLocationCurrent) return false
  }
  const candidatePagePosition = this.readerPagePosition(detail)
  const candidatePageIndex = Number(candidatePagePosition?.pageIndex)
  if (!Number.isFinite(candidatePageIndex)) return false
  const stale = direction === 'next'
    ? candidatePageIndex <= currentPageIndex
    : candidatePageIndex >= currentPageIndex
  if (stale) {
    readerTrace('relocate:ignored-stale-page-turn', {
      reason,
      direction,
      currentSectionKey,
      candidateSectionKey,
      currentPageIndex,
      candidatePageIndex,
      targetPageIndex,
    })
  }
  return stale
}


function scheduleSettledControlledPageTurnRelocation(direction) {
  const reason = `page-turn:${direction}`
  if (this.controlledRelocateReason !== reason) return false
  const detail = this.lastRelocateDetail
  if (!detail) return false
  if (this.pageTurnRelocationDetailIsStale(detail, reason)) return false
  this.scheduleCommittedRelocation(detail, this.consumeControlledRelocationReason(reason))
  return true
}


function scheduleControlledRelocationFallback(
  reason,
  owner = this.controlledRelocateOwner
) {
  const startSequence = this.controlledRelocateStartSequence
  log('controlled-relocate:fallback-scheduled', reason, `start=${startSequence}`)
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      if (this.controlledRelocateOwner !== owner) {
        log('controlled-relocate:fallback-skipped', reason, 'owner-changed')
        return
      }
      if (this.controlledRelocateReason !== reason) {
        log('controlled-relocate:fallback-skipped', reason, `stored=${this.controlledRelocateReason || 'none'}`)
        return
      }
      if (this.relocateSequence > startSequence) {
        log('controlled-relocate:fallback-skipped', reason, `seq=${this.relocateSequence}`, `start=${startSequence}`)
        return
      }
      if (this.pageTurnRelocationDetailIsStale(this.lastRelocateDetail, reason)) {
        log('controlled-relocate:fallback-skipped', reason, 'stale-last-relocation')
        return
      }
      log('controlled-relocate:fallback-commit', reason, `seq=${this.relocateSequence}`, `start=${startSequence}`)
      this.scheduleCommittedRelocation(this.lastRelocateDetail, this.consumeControlledRelocationReason(reason))
    })
  })
}


function onRelocate(detail) {
  readerTrace('relocate:raw', detail)
  this.attachSurfacePaperTextureScrollSync()
  if (this.exactPageTurnNavigationInProgress) {
    this.relocateSequence += 1
    this.lastRelocateDetail = detail
    return
  }
  if (this.pageTurnRelocationDetailIsStale(detail, this.controlledRelocateReason)) {
    this.handleDuplicatePageTurnRelocation?.(detail, this.controlledRelocateReason)
    return
  }
  this.relocateSequence += 1
  this.lastRelocateDetail = detail
  if (
    this.pageTurnInProgress ||
    this.pageTurnPromise
  ) return
  this.scheduleCommittedRelocation(detail, this.consumeControlledRelocationReason('relocate-committed'))
}


function cancelPendingCommittedRelocation() {
  this.pendingRelocateDetail = null
  this.pendingRelocateReason = 'relocate-committed'
  this.controlledRelocateOwner = null
  this.controlledRelocateReason = null
  this.controlledRelocateStartSequence = this.relocateSequence
  this.relocatePostScheduled = false
  if (this.relocatePostTimer != null) {
    clearTimeout(this.relocatePostTimer)
    this.relocatePostTimer = null
  }
}


function scheduleCommittedRelocation(detail, reason = 'relocate-committed') {
  if (!detail) return
  const previousReason = this.pendingRelocateReason
  const preserveExplicitReason = this.relocatePostScheduled &&
    readerRelocationReasonIsExplicit(previousReason) &&
    !readerRelocationReasonIsExplicit(reason)
  this.pendingRelocateDetail = detail
  this.pendingRelocateReason = preserveExplicitReason ? previousReason : reason
  if (this.relocatePostScheduled) return
  this.relocatePostScheduled = true
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      this.relocatePostTimer = setTimeout(() => {
        this.relocatePostTimer = null
        this.relocatePostScheduled = false
        const pendingDetail = this.pendingRelocateDetail
        const pendingReason = this.pendingRelocateReason
        this.pendingRelocateDetail = null
        this.pendingRelocateReason = 'relocate-committed'
        if (!pendingDetail) return
        this.applyThemeToLoadedContent(this.readerSettings)
        const forceDuplicatePost = String(pendingReason || '').startsWith('shell-cover-dismiss:')
        this.postLocationChanged(pendingDetail, pendingReason, { forceDuplicatePost })
      }, ReaderRelocationCommitDelayMs)
    })
  })
}

function goToLocator(locator, reason = 'go-to') {
  if (typeof locator === 'string') return this.goTo(locator, reason)
  if (!locator || typeof locator !== 'object') return false
  const cfi = String(locator?.cfi || '').trim()
  if (cfi) return this.goTo(cfi, reason)
  const href = String(locator?.href || '').trim()
  const chapterProgress = Number(locator?.chapterProgress)
  const chapterPageIndex = Number(locator?.chapterPageIndex)
  const chapterPageCount = Number(locator?.chapterPageCount)
  const hasChapterPosition = Number.isFinite(chapterProgress) || (
    Number.isFinite(chapterPageIndex) &&
    Number.isFinite(chapterPageCount) &&
    chapterPageIndex >= 0 &&
    chapterPageCount > 1
  )
  if (href && hasChapterPosition) {
    return this.goToChapterProgress(
      href,
      locator.chapterProgress,
      locator.chapterPageIndex,
      locator.chapterPageCount,
      reason,
    )
  }
  const progress = Number(locator?.progress)
  if (locator?.progress != null && Number.isFinite(progress)) {
    return this.goToProgress(progress, reason)
  }
  const pageIndex = Number(locator?.pageIndex)
  const pageCount = Number(locator?.pageCount)
  if (
    Number.isFinite(pageIndex) &&
    Number.isFinite(pageCount) &&
    pageIndex >= 0 &&
    pageCount > 1
  ) {
    return this.goToProgress(pageIndex / (pageCount - 1), reason)
  }
  if (href) return this.goTo(href, reason)
  return false
}


export const NavicReaderLocationMethods = {
  goToLocator,
  currentFixedLayoutLocationDetail,
  postCurrentLocationSnapshot,
  postLocationChanged,
  beginControlledRelocation,
  cancelControlledRelocation,
  consumeControlledRelocationReason,
  pageTurnRelocationDetailIsStale,
  scheduleSettledControlledPageTurnRelocation,
  scheduleControlledRelocationFallback,
  onRelocate,
  cancelPendingCommittedRelocation,
  scheduleCommittedRelocation,
  captureDuplicatePageBaselines,
  compareCommittedDuplicatePage,
  postCurrentVisibleTextRange,
  currentVisibleTextRangeForHref,
}
