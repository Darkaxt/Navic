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
  this.removePageDragPreviewLayer()
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
  const pagePosition = this.tryUpdateReaderPageNumberLayer(detail, this.currentPagePosition, reason)
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
    readerTrace('location:duplicate-skipped', { reason, message })
    const duplicateHandled = this.handleDuplicatePageTurnRelocation?.(detail, reason)
    if (duplicateHandled) {
      return { posted: false, skipped: 'duplicate-adjacent-fallback', reason }
    }
    return { posted: false, skipped: 'duplicate', reason }
  }
  this.updateSurfacePaperTexture(detail, pagePosition)
  this.committedRelocateDetail = detail
  this.lastPostedLocationKey = locationKey
  readerTrace('location:page-model', {
    reason,
    href: message.href,
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
    `fingerprint=${pageModelDiagnostics.paginationFingerprint || 'none'}`,
    `href=${message.href || 'none'}`
  )
  readerTrace('location:post', { reason, message })
  post(message)
  log('location-changed:posted', reason)
  if (detail.cfi) post({ type: 'cfiChanged', cfi: detail.cfi })
  if (tocItem.href || tocItem.label || tocItem.title) {
    post({ type: 'tocItemChanged', href: tocItem.href, title: tocItem.label || tocItem.title })
  }
  return { posted: true, reason, href: message.href || null, pageIndex: message.pageIndex ?? null, message }
}


function beginControlledRelocation(reason) {
  this.controlledRelocateReason = reason || null
  if (!String(reason || '').startsWith('page-turn:')) {
    this.surfacePaperTextureFallbackDirection = null
  }
  this.controlledRelocateStartSequence = this.relocateSequence
  log('controlled-relocate:begin', this.controlledRelocateReason || 'none', `seq=${this.controlledRelocateStartSequence}`)
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
  this.controlledRelocateReason = null
  return reason
}


function scheduleControlledRelocationFallback(reason) {
  const startSequence = this.controlledRelocateStartSequence
  log('controlled-relocate:fallback-scheduled', reason, `start=${startSequence}`)
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      if (this.controlledRelocateReason !== reason) {
        log('controlled-relocate:fallback-skipped', reason, `stored=${this.controlledRelocateReason || 'none'}`)
        return
      }
      if (this.relocateSequence > startSequence) {
        log('controlled-relocate:fallback-skipped', reason, `seq=${this.relocateSequence}`, `start=${startSequence}`)
        return
      }
      log('controlled-relocate:fallback-commit', reason, `seq=${this.relocateSequence}`, `start=${startSequence}`)
      this.scheduleCommittedRelocation(this.lastRelocateDetail, this.consumeControlledRelocationReason(reason))
    })
  })
}


function onRelocate(detail) {
  readerTrace('relocate:raw', detail)
  this.lastRelocateDetail = detail
  this.relocateSequence += 1
  if (this.pageTurnInProgress || this.pageTurnPromise) return
  this.scheduleCommittedRelocation(detail, this.consumeControlledRelocationReason('relocate-committed'))
}


function cancelPendingCommittedRelocation() {
  this.pendingRelocateDetail = null
  this.pendingRelocateReason = 'relocate-committed'
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
        this.postLocationChanged(pendingDetail, pendingReason)
      }, ReaderRelocationCommitDelayMs)
    })
  })
}

export const NavicReaderLocationMethods = {
  currentFixedLayoutLocationDetail,
  postCurrentLocationSnapshot,
  postLocationChanged,
  beginControlledRelocation,
  consumeControlledRelocationReason,
  scheduleControlledRelocationFallback,
  onRelocate,
  cancelPendingCommittedRelocation,
  scheduleCommittedRelocation,
}
