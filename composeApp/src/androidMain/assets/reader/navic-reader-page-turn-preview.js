import { readerPageLocatorForVisualIndex } from './navic-reader-page-turn-model.js'
import {
  readerRoot,
  readerTrace,
  setStylesImportant,
} from './navic-reader-helpers.js'

const nextAnimationFrame = () => new Promise(resolve => requestAnimationFrame(resolve))

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

function pageTurnPreviewContext() {
  return Object.freeze({
    pageIndex: Number(this.currentPagePosition?.pageIndex),
    pageCount: Number(this.currentPagePosition?.pageCount),
    previewGeneration: this.pageTurnPreviewGeneration,
    previewState: this.pageTurnPreviewStateValue,
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
    if (!previewView || generation !== this.pageTurnPreviewGeneration) return
    const locator = readerPageLocatorForVisualIndex(this.paginationProfile, pageIndex)
    if (!locator) throw new Error(`Passive preview page ${pageIndex} is unavailable`)
    this.applyReaderViewportLayoutToProfilerView(previewView, this.readerSettings)
    await previewView.renderer.goTo({
      index: locator.spineIndex,
      anchor: locator.anchor,
    })
    if (generation !== this.pageTurnPreviewGeneration) return
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

function exposePageTurnPreviewFinal(token = '') {
  const state = this.pageTurnPreviewState(token)
  const previewView = this.pageTurnPreviewView
  if (state.status !== 'ready' || !previewView || !this.view) return false
  this.pageTurnPreviewLiveVisibility = this.view.style.getPropertyValue('visibility')
  this.pageTurnPreviewLiveOpacity = this.view.style.getPropertyValue('opacity')
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
  pageTurnPreviewState,
  pageTurnPreviewContext,
  exposePageTurnPreviewFinal,
  restorePageTurnLiveComposition,
  destroyPageTurnPreviewRenderer,
}
