import {
  ReaderDirectionRtl,
  ReaderLogicalDirectionNext,
  ReaderLogicalDirectionPrevious,
  readerPageLocatorForVisualIndex,
  readerPageTurnPlan,
  readerPhysicalPageSide,
} from './navic-reader-page-turn-model.js'
import {
  readerRoot,
  readerTrace,
  setStylesImportant,
} from './navic-reader-helpers.js'
import { stableHash } from './navic-reader-identity.js'

const nextAnimationFrame = () => new Promise(resolve => requestAnimationFrame(resolve))

export async function readerGoToExactVisualPage(view, locator, reason = 'page-turn:exact') {
  const renderer = view?.renderer
  if (typeof renderer?.goToTextPage !== 'function') {
    throw new Error('Exact paginated text navigation is unavailable')
  }
  const committed = await renderer.goToTextPage(
    locator.spineIndex,
    locator.chapterPageIndex,
    reason
  )
  return committed === false ? null : locator
}

async function ensurePageTurnPreviewRenderer() {
  if (
    this.pageTurnPreviewView &&
    this.pageTurnPreviewPublicationUrl === this.publicationUrl
  ) return this.pageTurnPreviewView

  if (this.pageTurnPreviewView || this.pageTurnPreviewPublicationUrl) {
    this.destroyPageTurnPreviewRenderer('publication-replaced')
  }
  if (!this.publicationUrl) return null

  const previewView = document.createElement('foliate-view')
  previewView.dataset.navicPageTurnPreview = 'true'
  previewView.setAttribute('data-navic-page-turn-preview', 'true')
  previewView.setAttribute('aria-hidden', 'true')
  previewView.addEventListener('load', event => {
    const detail = event.detail || {}
    this.applyDocumentTheme(detail.doc, this.readerSettings, detail.index)
  })
  this.pageTurnPreviewView = previewView
  this.pageTurnPreviewPublicationUrl = this.publicationUrl
  readerRoot.append(previewView)
  this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)
  try {
    await previewView.open(this.publicationUrl)
    if (this.pageTurnPreviewView !== previewView) {
      previewView.close?.()
      previewView.remove?.()
      return null
    }
    this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)
    readerTrace('page-turn-preview:opened', {
      publication: this.pageTurnPreviewPublicationUrl,
    })
    return previewView
  } catch (error) {
    if (this.pageTurnPreviewView === previewView) {
      this.pageTurnPreviewView = null
      this.pageTurnPreviewPublicationUrl = ''
    }
    previewView.close?.()
    previewView.remove?.()
    throw error
  }
}

function pageTurnPreviewState(token = '') {
  const requestedToken = String(token || '')
  const state = this.pageTurnPreviewStateValue
  if (!state || (requestedToken && state.token !== requestedToken)) {
    return Object.freeze({ token: requestedToken, status: 'missing' })
  }
  return state
}

function pageTurnRasterDescriptor(pageIndex) {
  const normalizedPageIndex = Math.max(0, Math.floor(Number(pageIndex)))
  const locator = readerPageLocatorForVisualIndex(this.paginationProfile, normalizedPageIndex)
  if (!locator) return null
  const geometry = this.pageTurnCaptureGeometry()
  const render = this.paginationProfile?.render || null
  const settings = this.readerSettings || {}
  const layoutState = {
    render,
    mode: geometry.mode,
    pages: geometry.pages,
    viewportWidth: geometry.viewportWidth,
    viewportHeight: geometry.viewportHeight,
  }
  const decorationState = {
    theme: settings.theme || '',
    paperTextureEnabled: settings.paperTextureEnabled !== false,
    pageEdgesEnabled: settings.pageEdgesEnabled !== false,
    paperStainsEnabled: settings.paperStainsEnabled !== false,
    coverBackdropEnabled: settings.coverBackdropEnabled !== false,
  }
  return Object.freeze({
    publicationUrl: String(this.publicationUrl || ''),
    paginationFingerprint: String(this.paginationFingerprint || ''),
    layoutFingerprint: stableHash(JSON.stringify(layoutState)),
    decorationFingerprint: stableHash(JSON.stringify(decorationState)),
    viewportWidth: Math.max(1, Math.round(Number(geometry.viewportWidth) || 1)),
    viewportHeight: Math.max(1, Math.round(Number(geometry.viewportHeight) || 1)),
    pageCount: Math.max(1, Math.floor(Number(this.currentPagePosition?.pageCount) || 1)),
    spineIndex: locator.spineIndex,
    href: locator.href,
    chapterPageIndex: locator.chapterPageIndex,
    chapterPageCount: locator.chapterPageCount,
    visualPageOrdinal: locator.pageIndex,
  })
}

function pageTurnRasterPreparationPlan(pageIndexOverride = null) {
  const geometry = this.pageTurnCaptureGeometry()
  const layoutMode = geometry.mode
  const readerDirection = this.effectiveReaderDirection?.() || this.readerDirectionModeValue
  const step = layoutMode === 'spread' ? 2 : 1
  const requestedCenter = Number(pageIndexOverride)
  const pageCount = Math.max(1, Math.floor(Number(this.currentPagePosition?.pageCount) || 1))
  const centerPageIndex = Number.isFinite(requestedCenter)
    ? Math.max(0, Math.min(pageCount - 1, Math.floor(requestedCenter)))
    : Math.max(0, Math.min(pageCount - 1, Math.floor(Number(this.currentPagePosition?.pageIndex) || 0)))
  const chapters = Array.isArray(this.paginationProfile?.chapters)
    ? this.paginationProfile.chapters
    : []
  const currentChapterIndex = chapters.findIndex(chapter => {
    const pageStartIndex = Math.max(0, Math.floor(Number(chapter?.pageStartIndex) || 0))
    const chapterPageCount = Math.max(0, Math.floor(Number(chapter?.pageCount) || 0))
    return chapterPageCount > 0 &&
      centerPageIndex >= pageStartIndex &&
      centerPageIndex < pageStartIndex + chapterPageCount
  })
  const targets = []
  const seen = new Set()
  const addTarget = (pageIndex, priority) => {
    const normalized = Math.floor(Number(pageIndex))
    if (!Number.isFinite(normalized) || normalized < 0 || normalized >= pageCount || seen.has(normalized)) return
    seen.add(normalized)
    targets.push(Object.freeze({ pageIndex: normalized, priority }))
  }
  const chapterPages = chapter => {
    const pageStartIndex = Math.max(0, Math.floor(Number(chapter?.pageStartIndex) || 0))
    const chapterPageCount = Math.max(0, Math.floor(Number(chapter?.pageCount) || 0))
    const pageEndIndex = Math.min(pageCount, pageStartIndex + chapterPageCount)
    const pages = []
    for (let pageIndex = pageStartIndex; pageIndex < pageEndIndex; pageIndex += 1) {
      if (Math.abs(pageIndex - centerPageIndex) % step === 0) pages.push(pageIndex)
    }
    return pages
  }

  addTarget(centerPageIndex, 'current')
  addTarget(centerPageIndex + step, 'next-transition')
  addTarget(centerPageIndex - step, 'previous-transition')
  addTarget(centerPageIndex + step * 2, 'next-lookahead')
  addTarget(centerPageIndex - step * 2, 'previous-lookahead')
  addTarget(centerPageIndex + step * 3, 'next-lookahead')
  addTarget(centerPageIndex - step * 3, 'previous-lookahead')
  if (currentChapterIndex >= 0) {
    const currentChapterPages = chapterPages(chapters[currentChapterIndex])
    const nextChapterPages = chapterPages(chapters[currentChapterIndex + 1])
    const previousChapterPages = chapterPages(chapters[currentChapterIndex - 1])
    currentChapterPages.forEach(pageIndex => addTarget(pageIndex, 'current-chapter'))
    nextChapterPages.slice(0, 3).forEach(pageIndex => addTarget(pageIndex, 'next-chapter'))
    previousChapterPages.slice(-3).reverse()
      .forEach(pageIndex => addTarget(pageIndex, 'previous-chapter'))
    nextChapterPages.slice(3)
      .forEach(pageIndex => addTarget(pageIndex, 'next-chapter-remainder'))
    previousChapterPages.slice(0, -3).reverse()
      .forEach(pageIndex => addTarget(pageIndex, 'previous-chapter-remainder'))
  }
  const currentChapter = currentChapterIndex >= 0 ? chapters[currentChapterIndex] : null
  const previousChapter = currentChapterIndex > 0 ? chapters[currentChapterIndex - 1] : null
  const nextChapter = currentChapterIndex >= 0 ? chapters[currentChapterIndex + 1] : null
  const chapterStart = chapter => chapter
    ? Math.max(0, Math.floor(Number(chapter.pageStartIndex) || 0))
    : -1
  const chapterCount = chapter => Math.max(0, Math.floor(Number(chapter?.pageCount) || 0))
  return Object.freeze({
    context: Object.freeze({
      centerPageIndex,
      pageCount,
      layoutMode,
      readerDirection,
      step,
      currentChapterIndex,
      currentChapterPageStartIndex: chapterStart(currentChapter),
      currentChapterPageCount: chapterCount(currentChapter),
      previousChapterPageStartIndex: chapterStart(previousChapter),
      previousChapterPageCount: chapterCount(previousChapter),
      nextChapterPageStartIndex: chapterStart(nextChapter),
      nextChapterPageCount: chapterCount(nextChapter),
    }),
    targets: Object.freeze(targets),
  })
}

function pageTurnPreviewContext() {
  return Object.freeze({
    pageIndex: Number(this.currentPagePosition?.pageIndex),
    pageCount: Number(this.currentPagePosition?.pageCount),
    layoutMode: this.pageTurnCaptureGeometry().mode,
    previewGeneration: this.pageTurnPreviewGeneration,
    previewState: this.pageTurnPreviewStateValue,
  })
}

function pageTurnTransitionPlan(physicalDirection = '', currentPageIndexOverride = null) {
  const geometry = this.pageTurnCaptureGeometry()
  const readerDirection = this.effectiveReaderDirection?.() || this.readerDirectionModeValue
  const towardLeft = physicalDirection === 'toward-left'
  const logicalDirection = readerDirection === ReaderDirectionRtl
    ? towardLeft ? ReaderLogicalDirectionPrevious : ReaderLogicalDirectionNext
    : towardLeft ? ReaderLogicalDirectionNext : ReaderLogicalDirectionPrevious
  const overridePageIndex = Number(currentPageIndexOverride)
  const currentPageIndex = Number.isFinite(Number(currentPageIndexOverride))
    ? overridePageIndex
    : Number(this.currentPagePosition?.pageIndex)
  return readerPageTurnPlan({
    currentPageIndex: currentPageIndex,
    pageCount: this.currentPagePosition?.pageCount,
    layoutMode: geometry.mode,
    logicalDirection,
    currentPageSide: readerPhysicalPageSide({
      pageIndex: currentPageIndex,
      readerDirection,
    }),
    readerDirection,
  })
}

function beginPageTurnPreviewPreparation(token, pageIndex) {
  const requestedToken = String(token || '')
  const generation = ++this.pageTurnPreviewGeneration
  this.pageTurnPreviewStateValue = Object.freeze({
    token: requestedToken,
    generation,
    status: 'preparing',
    pageIndex: Number(pageIndex),
  })
  void this.preparePageTurnPreview(generation, requestedToken, pageIndex)
  return this.pageTurnPreviewStateValue
}

async function preparePageTurnPreview(generation, token, pageIndex) {
  try {
    const previewView = await this.ensurePageTurnPreviewRenderer()
    if (generation !== this.pageTurnPreviewGeneration) return
    if (!previewView) throw new Error('Passive preview renderer is unavailable')
    const locator = readerPageLocatorForVisualIndex(this.paginationProfile, pageIndex)
    if (!locator) throw new Error(`Passive preview page ${pageIndex} is unavailable`)
    this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)
    const reached = await readerGoToExactVisualPage(previewView, locator, 'page-turn-preview')
    if (generation !== this.pageTurnPreviewGeneration) return
    if (!reached) throw new Error(`Passive preview navigation to page ${pageIndex} was canceled`)
    this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)
    await nextAnimationFrame()
    await nextAnimationFrame()
    if (generation !== this.pageTurnPreviewGeneration) return
    this.pageTurnPreviewStateValue = Object.freeze({
      token,
      generation,
      status: 'ready',
      pageIndex: locator.pageIndex,
      spineIndex: locator.spineIndex,
      href: locator.href,
      chapterPageIndex: locator.chapterPageIndex,
      chapterPageCount: locator.chapterPageCount,
    })
    readerTrace('page-turn-preview:ready', this.pageTurnPreviewStateValue)
  } catch (error) {
    if (generation !== this.pageTurnPreviewGeneration) return
    this.pageTurnPreviewStateValue = Object.freeze({
      token,
      generation,
      status: 'failed',
      pageIndex: Number(pageIndex),
      message: error?.message || String(error),
    })
    readerTrace('page-turn-preview:failed', this.pageTurnPreviewStateValue)
  }
}

function pageTurnPreviewBatchState(token = '') {
  const requestedToken = String(token || '')
  const state = this.pageTurnPreviewBatchStateValue
  if (!state || (requestedToken && state.token !== requestedToken)) {
    return Object.freeze({ token: requestedToken, status: 'missing' })
  }
  return state
}

function beginPageTurnPreviewBatch(token, pageIndexes = []) {
  const requestedToken = String(token || '')
  const requestedPageIndexes = Array.from(new Set(
    (Array.isArray(pageIndexes) ? pageIndexes : [])
      .map(value => Number(value))
      .filter(Number.isFinite)
      .map(value => Math.max(0, Math.floor(value)))
  ))
  const generation = ++this.pageTurnPreviewGeneration
  this.pageTurnPreviewBatchStateValue = Object.freeze({
    token: requestedToken,
    generation,
    status: requestedPageIndexes.length ? 'preparing' : 'complete',
    cursor: 0,
    total: requestedPageIndexes.length,
    pageIndexes: requestedPageIndexes,
  })
  if (requestedPageIndexes.length) {
    void this.preparePageTurnPreviewBatchItem(generation, requestedToken, 0)
  }
  return this.pageTurnPreviewBatchStateValue
}

async function preparePageTurnPreviewBatchItem(generation, token, cursor) {
  const state = this.pageTurnPreviewBatchState(token)
  if (state.generation !== generation || state.cursor !== cursor) return
  const pageIndex = state.pageIndexes[cursor]
  try {
    const previewView = await this.ensurePageTurnPreviewRenderer()
    if (generation !== this.pageTurnPreviewGeneration) return
    if (!previewView) throw new Error('Passive raster renderer is unavailable')
    const locator = readerPageLocatorForVisualIndex(this.paginationProfile, pageIndex)
    if (!locator) throw new Error(`Passive raster page ${pageIndex} is unavailable`)
    this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)
    const reached = await readerGoToExactVisualPage(previewView, locator, 'page-turn-raster-batch')
    if (generation !== this.pageTurnPreviewGeneration) return
    if (!reached) throw new Error(`Passive raster navigation to page ${pageIndex} was canceled`)
    this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)
    await nextAnimationFrame()
    await nextAnimationFrame()
    if (generation !== this.pageTurnPreviewGeneration) return
    const itemToken = `${token}:${generation}:${cursor}:${locator.pageIndex}`
    const identity = Object.freeze({
      token: itemToken,
      generation,
      status: 'ready',
      pageIndex: locator.pageIndex,
      visualPageOrdinal: locator.pageIndex,
      spineIndex: locator.spineIndex,
      href: locator.href,
      chapterPageIndex: locator.chapterPageIndex,
      chapterPageCount: locator.chapterPageCount,
    })
    this.pageTurnPreviewStateValue = identity
    this.pageTurnPreviewBatchStateValue = Object.freeze({
      ...state,
      ...identity,
      token,
      itemToken,
      cursor,
    })
    readerTrace('page-turn-raster-batch:ready', this.pageTurnPreviewBatchStateValue)
  } catch (error) {
    if (generation !== this.pageTurnPreviewGeneration) return
    this.pageTurnPreviewBatchStateValue = Object.freeze({
      ...state,
      status: 'failed',
      pageIndex,
      paginationReady: this.isCompletePaginationProfile?.(this.paginationProfile) === true,
      message: error?.message || String(error),
    })
    readerTrace('page-turn-raster-batch:failed', this.pageTurnPreviewBatchStateValue)
  }
}

function advancePageTurnPreviewBatch(token, pageIndex) {
  const state = this.pageTurnPreviewBatchState(token)
  const completedPageIndex = Math.max(0, Math.floor(Number(pageIndex)))
  if (state.status !== 'ready' || state.pageIndex !== completedPageIndex) return state
  const nextCursor = state.cursor + 1
  if (nextCursor >= state.total) {
    this.pageTurnPreviewBatchStateValue = Object.freeze({
      token: state.token,
      generation: state.generation,
      status: 'complete',
      cursor: nextCursor,
      total: state.total,
      pageIndexes: state.pageIndexes,
    })
  } else {
    this.pageTurnPreviewBatchStateValue = Object.freeze({
      token: state.token,
      generation: state.generation,
      status: 'preparing',
      cursor: nextCursor,
      total: state.total,
      pageIndexes: state.pageIndexes,
    })
    void this.preparePageTurnPreviewBatchItem(state.generation, state.token, nextCursor)
  }
  return this.pageTurnPreviewBatchStateValue
}

function cancelPageTurnPreviewBatch(token = '') {
  const state = this.pageTurnPreviewBatchState(token)
  if (state.status === 'missing') return false
  const generation = ++this.pageTurnPreviewGeneration
  this.restorePageTurnLiveComposition()
  this.pageTurnPreviewStateValue = null
  this.pageTurnPreviewBatchStateValue = Object.freeze({
    token: state.token,
    generation,
    status: 'cancelled',
    cursor: state.cursor ?? 0,
    total: state.total ?? 0,
    pageIndexes: state.pageIndexes ?? [],
  })
  readerTrace('page-turn-raster-batch:cancelled', this.pageTurnPreviewBatchStateValue)
  return true
}

function exposePageTurnPreviewFinal(token = '') {
  const state = this.pageTurnPreviewState(token)
  const previewView = this.pageTurnPreviewView
  if (state.status !== 'ready' || !previewView || !this.view) return false
  this.pageTurnPreviewLiveVisibility = this.view.style.getPropertyValue('visibility')
  this.pageTurnPreviewLiveOpacity = this.view.style.getPropertyValue('opacity')
  this.pageTurnPreviewLivePagePosition = this.currentPagePosition
  this.pageTurnPreviewDecorationPageIndex = state.pageIndex
  const previewPagePosition = Object.freeze({
    ...(this.currentPagePosition || {}),
    pageIndex: state.pageIndex,
  })
  this.updateReaderPageNumberLayer(previewPagePosition)
  this.renderSurfacePaperTextureLayers()
  setStylesImportant(this.view, {
    visibility: 'hidden',
    opacity: '0',
  })
  const { width, height } = this.pageDragPreviewDimensions()
  setStylesImportant(previewView, {
    position: 'fixed',
    inset: '0px',
    display: 'block',
    width: `${width}px`,
    'min-width': `${width}px`,
    height: `${height}px`,
    'min-height': `${height}px`,
    overflow: 'hidden',
    visibility: 'visible',
    opacity: '1',
    'pointer-events': 'none',
    'z-index': '1',
  })
  this.pageTurnPreviewExposedToken = state.token
  readerTrace('page-turn-preview:exposed', state)
  return true
}

function restorePageTurnLiveComposition(token = '') {
  const requestedToken = String(token || '')
  if (
    requestedToken &&
    this.pageTurnPreviewExposedToken &&
    requestedToken !== this.pageTurnPreviewExposedToken
  ) return false
  const previewView = this.pageTurnPreviewView
  if (previewView) this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)
  if (this.pageTurnPreviewLivePagePosition) {
    this.updateReaderPageNumberLayer(this.pageTurnPreviewLivePagePosition)
  }
  if (this.view) {
    if (this.pageTurnPreviewLiveVisibility) {
      this.view.style.setProperty('visibility', this.pageTurnPreviewLiveVisibility, 'important')
    } else {
      this.view.style.removeProperty('visibility')
    }
    if (this.pageTurnPreviewLiveOpacity) {
      this.view.style.setProperty('opacity', this.pageTurnPreviewLiveOpacity, 'important')
    } else {
      this.view.style.removeProperty('opacity')
    }
  }
  this.pageTurnPreviewLiveVisibility = ''
  this.pageTurnPreviewLiveOpacity = ''
  this.pageTurnPreviewExposedToken = ''
  this.pageTurnPreviewLivePagePosition = null
  this.pageTurnPreviewDecorationPageIndex = null
  this.renderSurfacePaperTextureLayers()
  readerTrace('page-turn-preview:restored', { token: requestedToken })
  return true
}

function destroyPageTurnPreviewRenderer(reason = 'destroy') {
  this.pageTurnPreviewGeneration += 1
  this.restorePageTurnLiveComposition()
  const previewView = this.pageTurnPreviewView
  this.pageTurnPreviewView = null
  this.pageTurnPreviewPublicationUrl = ''
  this.pageTurnPreviewStateValue = null
  this.pageTurnPreviewBatchStateValue = null
  if (previewView) {
    previewView.close?.()
    previewView.remove?.()
  }
  readerTrace('page-turn-preview:destroyed', { reason })
}

export const NavicReaderPageTurnPreviewMethods = {
  ensurePageTurnPreviewRenderer,
  beginPageTurnPreviewPreparation,
  preparePageTurnPreview,
  pageTurnRasterDescriptor,
  pageTurnRasterPreparationPlan,
  beginPageTurnPreviewBatch,
  preparePageTurnPreviewBatchItem,
  pageTurnPreviewBatchState,
  advancePageTurnPreviewBatch,
  cancelPageTurnPreviewBatch,
  pageTurnPreviewState,
  pageTurnPreviewContext,
  pageTurnTransitionPlan,
  exposePageTurnPreviewFinal,
  restorePageTurnLiveComposition,
  destroyPageTurnPreviewRenderer,
}
