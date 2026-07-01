import {
  ReaderShellCoverTransitionMs,
} from './navic-reader-settings.js'
import {
  ReaderShellCoverProgressThreshold,
  ensureReaderShellCoverLayer,
  log,
  logError,
  readerContentDocumentLooksLikeCover,
  readerHrefMatchesSection,
  readerRoot,
  readerSectionIsReadable,
  readerSectionLooksLikeCover,
  readerStartLocatorHasPosition,
  readerTrace,
  reportError,
  setStylesImportant,
  suppressReaderEmbeddedCoverPage,
  updateReaderShellCoverLayer,
} from './navic-reader-helpers.js'

function clearShellCover({ revoke = true } = {}) {
  if (this.shellCoverHideTimer) {
    clearTimeout(this.shellCoverHideTimer)
    this.shellCoverHideTimer = null
  }
  this.shellCoverVisible = false
  this.shellCoverLayer?.remove?.()
  this.shellCoverLayer = null
  delete readerRoot.dataset.navicShellCoverVisible
  if (revoke && this.shellCoverBlobUrl) {
    URL.revokeObjectURL(this.shellCoverBlobUrl)
    this.shellCoverBlobUrl = null
  }
}


async function loadShellCover() {
  if (this.shellCoverBlobUrl) {
    URL.revokeObjectURL(this.shellCoverBlobUrl)
    this.shellCoverBlobUrl = null
  }
  try {
    const book = this.view?.book
    const blob = await book.getCover?.()
    if (!blob) {
      log('shell-cover:missing')
      return null
    }
    this.shellCoverBlobUrl = URL.createObjectURL(blob)
    log('shell-cover:loaded', blob.type || 'blob', blob.size || 0)
    return this.shellCoverBlobUrl
  } catch (error) {
    logError('shell-cover:load-failed', error?.message || error)
    return null
  }
}


function firstReadableContentTarget() {
  const sections = Array.from(this.view?.book?.sections || [])
  if (!sections.length) return null
  const firstNonCover = sections.findIndex((section, index) =>
    readerSectionIsReadable(section) && !this.sectionTargetsCover(section, index)
  )
  if (firstNonCover >= 0) return firstNonCover
  const firstReadable = sections.findIndex(readerSectionIsReadable)
  return firstReadable >= 0 ? firstReadable : 0
}


function coverSectionEntries() {
  return Array.from(this.view?.book?.sections || [])
    .map((section, index) => ({ section, index }))
    .filter(({ section, index }) => this.sectionTargetsCover(section, index))
}


function hasNonCoverReadableContent() {
  return Array.from(this.view?.book?.sections || []).some((section, index) =>
    readerSectionIsReadable(section) && !this.sectionTargetsCover(section, index)
  )
}


function sectionTargetsCover(section, index) {
  return readerSectionLooksLikeCover(section, index) || this.suppressedCoverSectionIndexes.has(index)
}


function startLocatorTargetsShellCover(startLocator) {
  if (!readerStartLocatorHasPosition(startLocator)) return false
  const coverSections = this.coverSectionEntries()
  if (!coverSections.length || !this.hasNonCoverReadableContent()) return false
  const href = startLocator?.href
  if (href && coverSections.some(({ section }) => readerHrefMatchesSection(href, section))) {
    log('shell-cover:start-locator-cover', `href=${href}`)
    return true
  }
  const cfi = String(startLocator?.cfi || '')
  if (/cover/i.test(cfi)) {
    log('shell-cover:start-locator-cover', 'cfi-token')
    return true
  }
  const progress = Number(startLocator?.progress)
  const firstCoverIndex = Math.min(...coverSections.map(({ index }) => index))
  const firstReadableIndex = Number(this.firstReadableContentTarget())
  if (
    Number.isFinite(progress) &&
    progress >= 0 &&
    progress <= ReaderShellCoverProgressThreshold &&
    firstCoverIndex === 0 &&
    Number.isFinite(firstReadableIndex) &&
    firstReadableIndex > firstCoverIndex
  ) {
    log('shell-cover:start-locator-cover', `progress=${progress}`)
    return true
  }
  return false
}


function detailTargetsCover(detail) {
  const coverSections = this.coverSectionEntries()
  if (!coverSections.length) return false
  const index = Number(detail?.section?.current ?? detail?.index)
  if (Number.isFinite(index)) {
    const section = this.view?.book?.sections?.[Math.floor(index)]
    return this.sectionTargetsCover(section, Math.floor(index))
  }
  const href = detail?.href || detail?.tocItem?.href || detail?.section?.href
  return Boolean(href && coverSections.some(({ section }) => readerHrefMatchesSection(href, section)))
}


function sectionHrefForDetail(detail) {
  const index = Number(detail?.section?.current ?? detail?.index)
  if (!Number.isFinite(index)) return ''
  const section = this.view?.book?.sections?.[Math.floor(index)]
  return section?.href || section?.id || section?.url || section?.name || ''
}


async function goToFirstReadableContent() {
  const target = this.firstReadableContentTarget()
  if (target == null) {
    await this.view?.init?.({ showTextStart: true })
    return
  }
  log('shell-cover:first-readable', target)
  await this.view?.goTo?.(target)
}


function showShellCover({ animate = true } = {}) {
  if (!this.shellCoverBlobUrl) return false
  if (this.shellCoverHideTimer) {
    clearTimeout(this.shellCoverHideTimer)
    this.shellCoverHideTimer = null
  }
  this.shellCoverVisible = true
  readerRoot.dataset.navicShellCoverVisible = 'true'
  this.pageNumberLayer?.remove?.()
  this.pageNumberLayer = null
  this.shellCoverLayer = this.shellCoverLayer && readerRoot.contains(this.shellCoverLayer)
    ? this.shellCoverLayer
    : ensureReaderShellCoverLayer()
  updateReaderShellCoverLayer(
    this.shellCoverLayer,
    this.shellCoverBlobUrl,
    this.readerSettings,
    this.view?.book?.metadata?.title || ''
  )
  this.attachSurfaceTapGesture(this.shellCoverLayer)
  this.shellCoverLayer.dataset.navicShellCoverState = animate ? 'entering' : 'visible'
  if (animate) {
    setStylesImportant(this.shellCoverLayer, {
      opacity: '0',
      transform: 'translateX(4%) scale(0.985)',
    })
    requestAnimationFrame(() => {
      if (!this.shellCoverVisible || !this.shellCoverLayer) return
      this.shellCoverLayer.dataset.navicShellCoverState = 'visible'
      setStylesImportant(this.shellCoverLayer, {
        opacity: '1',
        transform: 'translateX(0) scale(1)',
        'pointer-events': 'auto',
      })
    })
  }
  log('shell-cover:show', animate ? 'animated' : 'static')
  return true
}


function hideShellCover({ animate = true } = {}) {
  if (!this.shellCoverVisible && !this.shellCoverLayer) return false
  this.shellCoverVisible = false
  delete readerRoot.dataset.navicShellCoverVisible
  const layer = this.shellCoverLayer
  const finish = () => {
    if (this.shellCoverVisible || this.shellCoverLayer !== layer) return
    layer?.remove?.()
    this.shellCoverLayer = null
    this.updateReaderPageNumberLayer()
  }
  if (layer && animate) {
    layer.dataset.navicShellCoverState = 'exiting'
    setStylesImportant(layer, {
      opacity: '0',
      transform: 'translateX(-8%) scale(1.018)',
      'pointer-events': 'none',
    })
    this.shellCoverHideTimer = setTimeout(() => {
      this.shellCoverHideTimer = null
      finish()
    }, ReaderShellCoverTransitionMs + 40)
  } else {
    finish()
  }
  log('shell-cover:hide', animate ? 'animated' : 'static')
  return true
}


function canReturnToShellCover() {
  if (!this.shellCoverBlobUrl || this.shellCoverVisible) return false
  const pageIndex = Number(this.currentPagePosition?.pageIndex)
  if (Number.isFinite(pageIndex)) return pageIndex <= 0
  const sectionIndex = Number(this.lastRelocateDetail?.section?.current ?? this.lastRelocateDetail?.index)
  const firstContent = Number(this.firstReadableContentTarget())
  if (
    Number.isFinite(sectionIndex) &&
    Number.isFinite(firstContent) &&
    Math.floor(sectionIndex) <= firstContent
  ) {
    return true
  }
  return false
}


function suppressLoadedCoverDocument(doc, index) {
  const normalizedIndex = Number(index)
  if (!Number.isFinite(normalizedIndex)) return false
  const sectionIndex = Math.floor(normalizedIndex)
  const section = this.view?.book?.sections?.[sectionIndex]
  if (!readerContentDocumentLooksLikeCover(doc, section, sectionIndex)) return false
  this.suppressedCoverSectionIndexes.add(sectionIndex)
  doc.documentElement.dataset.navicSuppressedCover = 'true'
  doc.body?.setAttribute?.('data-navic-suppressed-cover', 'true')
  setStylesImportant(doc.documentElement, {
    background: 'transparent',
    color: 'transparent',
  })
  if (doc.body) {
    setStylesImportant(doc.body, {
      display: 'none',
      visibility: 'hidden',
      background: 'transparent',
      color: 'transparent',
    })
  }
  readerTrace('cover:document-suppressed', {
    index: sectionIndex,
    href: section?.href || section?.id || '',
  })
  log('cover-document:suppressed', `index=${sectionIndex}`, section?.href || section?.id || '')
  if (this.hasNonCoverReadableContent()) {
    requestAnimationFrame(() => {
      if (!this.view || this.shellCoverVisible) return
      const current = Number(this.lastRelocateDetail?.section?.current ?? this.lastRelocateDetail?.index)
      if (!Number.isFinite(current) || Math.floor(current) === sectionIndex) {
        this.goToFirstReadableContent().catch(error => reportError(error, 'navigation_failed'))
      }
    })
  }
  return true
}


function suppressLoadedEmbeddedCoverPage(doc, index) {
  const normalizedIndex = Number(index)
  if (!Number.isFinite(normalizedIndex)) return false
  const sectionIndex = Math.floor(normalizedIndex)
  const section = this.view?.book?.sections?.[sectionIndex]
  const suppressed = suppressReaderEmbeddedCoverPage(doc, section, sectionIndex)
  if (!suppressed) return false
  const firstSuppression = !this.embeddedCoverSuppressedSectionIndexes.has(sectionIndex)
  this.embeddedCoverSuppressedSectionIndexes.add(sectionIndex)
  readerTrace('cover:embedded-page-suppressed', {
    index: sectionIndex,
    href: section?.href || section?.id || '',
    rerender: firstSuppression,
  })
  log('cover-embedded-page:suppressed', `index=${sectionIndex}`, section?.href || section?.id || '')
  if (firstSuppression && !this.embeddedCoverRerenderScheduled) {
    this.embeddedCoverRerenderScheduled = true
    requestAnimationFrame(() => {
      this.embeddedCoverRerenderScheduled = false
      if (!this.view) return
      this.view.renderer?.render?.()
      this.applyReaderViewportLayout('embedded-cover-suppressed')
      this.scheduleReaderPageNumberRefresh('embedded-cover-suppressed')
      this.scheduleCommittedRelocation(this.lastRelocateDetail, 'embedded-cover-suppressed')
    })
  }
  return true
}

export const NavicReaderShellCoverMethods = {
  clearShellCover,
  loadShellCover,
  firstReadableContentTarget,
  coverSectionEntries,
  hasNonCoverReadableContent,
  sectionTargetsCover,
  startLocatorTargetsShellCover,
  detailTargetsCover,
  sectionHrefForDetail,
  goToFirstReadableContent,
  showShellCover,
  hideShellCover,
  canReturnToShellCover,
  suppressLoadedCoverDocument,
  suppressLoadedEmbeddedCoverPage,
}
