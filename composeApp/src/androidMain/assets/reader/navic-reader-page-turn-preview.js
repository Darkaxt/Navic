import {
  ReaderDirectionRtl,
  ReaderLogicalDirectionNext,
  ReaderLogicalDirectionPrevious,
  readerPageLocatorForVisualIndex,
  readerPageTurnPlan,
  readerPhysicalPageSide,
} from './navic-reader-page-turn-model.js'
import {
  readerAdaptiveFoliatePageBox,
  readerPaperLayoutProfile,
  readerRoot,
  readerSurfacePageDecorationGeometry,
  readerSurfaceSpreadMode,
  readerTrace,
  readerViewportSize,
  setStylesImportant,
} from './navic-reader-helpers.js'
import { readerThemePalette } from './navic-reader-settings.js'
import { stableHash } from './navic-reader-identity.js'
import {
  readerCommitTextPage,
  readerForgetTextPageCommit,
  readerRememberTextPageCommit,
  readerTextPageCommitIsValid,
  readerTextPageCommitMatches,
  readerTextPageCommitOwnerIsValid,
} from './navic-reader-paginator-commit.js'
import {
  ReaderPageTurnPresentationScopePreview,
  issueReaderPageTurnPresentationReceipt,
  readerPageTurnPresentationReceiptMatches,
} from './navic-reader-page-turn-presentation.js'

const ReaderPageTurnMaximumPaginationProfileRepairs = 2
const ReaderPageTurnMaximumCommitTransactionAttempts = 3

const readerPageTurnPreviewTraceState = state => ({
  status: state?.status || 'missing',
  generation: Number(state?.generation),
  pageIndex: Number(state?.pageIndex),
  spineIndex: Number(state?.spineIndex),
  chapterPageIndex: Number(state?.chapterPageIndex),
  chapterPageCount: Number(state?.chapterPageCount),
  cursor: Number(state?.cursor),
  total: Number(state?.total),
  transactionAttempts: Number(state?.transactionAttempts),
  profileRepairs: Number(state?.profileRepairs),
})

export async function readerGoToExactVisualPage(view, locator, reason = 'page-turn:exact') {
  return readerCommitTextPage(
    view?.renderer,
    locator.spineIndex,
    locator.chapterPageIndex,
    reason
  )
}

async function resolvePageTurnPreviewLocator(
  view,
  pageIndex,
  reason,
  label,
  isCurrent = () => true,
  initialTransactionAttempts = 0,
  initialProfileRepairs = 0
) {
  let profileRepairs = Math.max(0, Math.floor(Number(initialProfileRepairs) || 0))
  let transactionAttempts = Math.max(0, Math.floor(Number(initialTransactionAttempts) || 0))
  while (transactionAttempts < ReaderPageTurnMaximumCommitTransactionAttempts) {
    if (!isCurrent()) return null
    const locator = readerPageLocatorForVisualIndex(
      this.paginationProfile,
      pageIndex
    )
    if (!locator) {
      throw new Error(
        `${label} page ${pageIndex} is unavailable`
      )
    }

    this.applyReaderViewportLayoutToProfilerView(
      view,
      this.readerSettings
    )
    const result = await readerCommitTextPage(
      view?.renderer,
      locator.spineIndex,
      locator.chapterPageIndex,
      reason
    )
    transactionAttempts += 1
    if (!isCurrent()) return null
    if (result.status === 'unsupported') {
      return Object.freeze({
        status: 'unsupported',
        locator: null,
        actualPosition: null,
        receipt: null,
        transactionAttempts,
        profileRepairs,
      })
    }

    const receiptIsValid = readerTextPageCommitIsValid(view?.renderer, result)
    const expectedPosition = {
      index: locator.spineIndex,
      pageIndex: locator.chapterPageIndex,
      pageCount: locator.chapterPageCount,
    }
    if (
      result.status === 'committed' &&
      receiptIsValid &&
      readerTextPageCommitMatches(result, expectedPosition)
    ) {
      return Object.freeze({
        status: 'committed',
        locator,
        actualPosition: result.position,
        receipt: result.receipt,
        transactionAttempts,
        profileRepairs,
      })
    }

    const actualPosition = receiptIsValid ? result.position : null
    const canRepairProfile =
      profileRepairs < ReaderPageTurnMaximumPaginationProfileRepairs &&
      Number(actualPosition?.index) === Number(locator.spineIndex) &&
      Number(actualPosition?.pageCount) !== Number(locator.chapterPageCount)
    if (
      canRepairProfile &&
      this.repairPaginationProfileFromExactPosition?.(
        locator,
        actualPosition
      )
    ) {
      profileRepairs += 1
      continue
    }
    if (result.status === 'invalidated') continue
    if (result.status === 'cancelled' && !isCurrent()) return null

    throw new Error(
      `${label} position for page ${pageIndex} was not committed`
    )
  }

  throw new Error(
    `${label} pagination for page ${pageIndex} did not stabilize`
  )
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
      generation: this.pageTurnPreviewGeneration,
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

function forgetPageTurnPreviewCommitment(state) {
  if (state && typeof state === 'object') readerForgetTextPageCommit(state)
}

function restartInvalidatedPageTurnPreviewCommitment(state) {
  if (
    !state ||
    state.status !== 'ready' ||
    state.generation !== this.pageTurnPreviewGeneration
  ) return false
  const transactionAttempts = Math.max(0, Math.floor(Number(state.transactionAttempts) || 0))
  const profileRepairs = Math.max(0, Math.floor(Number(state.profileRepairs) || 0))
  const batch = this.pageTurnPreviewBatchStateValue
  const belongsToBatch = batch?.status === 'ready' && (
    batch === state ||
    batch.itemToken === state.token ||
    batch.itemToken === state.itemToken
  )
  this.clearPageTurnPreviewPresentationReceipt()
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)
  forgetPageTurnPreviewCommitment(batch)

  if (belongsToBatch) {
    if (transactionAttempts >= ReaderPageTurnMaximumCommitTransactionAttempts) {
      this.pageTurnPreviewStateValue = null
      this.pageTurnPreviewBatchStateValue = Object.freeze({
        token: batch.token,
        generation: batch.generation,
        status: 'failed',
        cursor: batch.cursor,
        total: batch.total,
        pageIndexes: batch.pageIndexes,
        pageIndex: batch.pageIndex,
        transactionAttempts,
        profileRepairs,
        message: 'Passive raster commitment was invalidated',
      })
      return true
    }
    const generation = ++this.pageTurnPreviewGeneration
    this.pageTurnPreviewStateValue = Object.freeze({
      token: batch.itemToken,
      generation,
      status: 'preparing',
      pageIndex: batch.pageIndex,
      transactionAttempts,
      profileRepairs,
    })
    this.pageTurnPreviewBatchStateValue = Object.freeze({
      token: batch.token,
      generation,
      status: 'preparing',
      cursor: batch.cursor,
      total: batch.total,
      pageIndexes: batch.pageIndexes,
      pageIndex: batch.pageIndex,
      transactionAttempts,
      profileRepairs,
    })
    void this.preparePageTurnPreviewBatchItem(
      generation,
      batch.token,
      batch.cursor,
      transactionAttempts,
      profileRepairs
    )
    return true
  }

  if (transactionAttempts >= ReaderPageTurnMaximumCommitTransactionAttempts) {
    this.pageTurnPreviewStateValue = Object.freeze({
      token: state.token,
      generation: state.generation,
      status: 'failed',
      pageIndex: state.pageIndex,
      transactionAttempts,
      profileRepairs,
      message: 'Passive preview commitment was invalidated',
    })
    return true
  }
  const generation = ++this.pageTurnPreviewGeneration
  this.pageTurnPreviewStateValue = Object.freeze({
    token: state.token,
    generation,
    status: 'preparing',
    pageIndex: state.pageIndex,
    transactionAttempts,
    profileRepairs,
  })
  void this.preparePageTurnPreview(
    generation,
    state.token,
    state.pageIndex,
    transactionAttempts,
    profileRepairs
  )
  return true
}

function pageTurnPreviewState(token = '') {
  const requestedToken = String(token || '')
  let state = this.pageTurnPreviewStateValue
  if (!state || (requestedToken && state.token !== requestedToken)) {
    return Object.freeze({ token: requestedToken, status: 'missing' })
  }
  if (
    state.status === 'ready' &&
    !readerTextPageCommitOwnerIsValid(state)
  ) {
    this.restartInvalidatedPageTurnPreviewCommitment(state)
    state = this.pageTurnPreviewStateValue
  }
  return state || Object.freeze({ token: requestedToken, status: 'missing' })
}

function clearPageTurnPreviewPresentationReceipt() {
  const cleared = this.pageTurnPreviewPresentationReceiptValue != null
  this.pageTurnPreviewPresentationReceiptValue = null
  return cleared
}

function issuePageTurnPreviewPresentationReceipt(target) {
  const receipt = issueReaderPageTurnPresentationReceipt(
    target,
    this.pageTurnPresentationSequence
  )
  this.pageTurnPresentationSequence = receipt.presentationSequence
  this.pageTurnPreviewPresentationReceiptValue = receipt
  return receipt
}

function pageTurnPreviewPresentationReceipt() {
  const receipt = this.pageTurnPreviewPresentationReceiptValue
  const state = this.pageTurnPreviewStateValue
  if (
    state?.status === 'ready' &&
    !readerTextPageCommitOwnerIsValid(state)
  ) {
    this.restartInvalidatedPageTurnPreviewCommitment(state)
    this.clearPageTurnPreviewPresentationReceipt()
    return null
  }
  if (
    !receipt ||
    !state ||
    state.status !== 'ready' ||
    state.generation !== this.pageTurnPreviewGeneration ||
    !this.pageTurnPreviewView ||
    this.pageTurnPreviewExposedToken !== state.token
  ) {
    this.clearPageTurnPreviewPresentationReceipt()
    return null
  }
  const target = {
    scope: ReaderPageTurnPresentationScopePreview,
    token: state.token,
    pageIndex: state.pageIndex,
    previewGeneration: state.generation,
  }
  if (!readerPageTurnPresentationReceiptMatches(receipt, target)) {
    this.clearPageTurnPreviewPresentationReceipt()
    return null
  }
  return receipt
}

const readerCssColorToArgb = color => {
  const normalized = String(color || '').trim()
  const rgb = /^#[0-9a-f]{6}$/i.test(normalized)
    ? parseInt(normalized.slice(1), 16)
    : /^#[0-9a-f]{3}$/i.test(normalized)
      ? parseInt(normalized.slice(1).split('').map(value => `${value}${value}`).join(''), 16)
      : 0xead9ae
  return (0xff000000 | rgb) >>> 0
}

function pageTurnCaptureGeometry() {
  const viewport = readerViewportSize()
  const pageBox = readerAdaptiveFoliatePageBox(viewport, this.readerSettings)
  const spreadMode = readerSurfaceSpreadMode({
    flowMode: this.readerFlowModeValue,
    width: viewport.width,
    height: viewport.height,
  })
  const layoutProfile = readerPaperLayoutProfile({
    flowMode: this.readerFlowModeValue,
    width: viewport.width,
    height: viewport.height,
    spreadMode,
  })
  const geometry = readerSurfacePageDecorationGeometry({
    settings: this.readerSettings,
    spreadMode,
    foliateGap: pageBox.foliateGap,
    shellCoverVisible: this.shellCoverVisible,
    coverTint: this.shellCoverDominantColor,
    layoutProfile,
  })
  const percentPixels = (value, axisSize) => {
    const text = String(value || '').trim()
    if (text.endsWith('%')) return Number.parseFloat(text) * axisSize / 100
    return Number.parseFloat(text) || 0
  }
  const pageRect = (role, page) => ({
    role,
    left: percentPixels(page?.left, viewport.width),
    top: 0,
    width: percentPixels(page?.width, viewport.width),
    height: viewport.height,
  })
  const pages = spreadMode === 'spread'
    ? [pageRect('left', geometry.pages.left), pageRect('right', geometry.pages.right)]
    : [pageRect('full', geometry.pages.full)]
  const background = readerThemePalette(this.readerSettings?.theme).background
  const reverseFaceColorArgb = readerCssColorToArgb(background)
  return {
    viewportWidth: viewport.width,
    viewportHeight: viewport.height,
    mode: spreadMode === 'spread' ? 'spread' : 'single',
    pages,
    reverseFaceColorArgb,
  }
}

function pageTurnRasterDescriptor(pageIndex) {
  const normalizedPageIndex = Math.max(0, Math.floor(Number(pageIndex)))
  const readyState = this.pageTurnPreviewStateValue?.status === 'ready' &&
    this.pageTurnPreviewStateValue.pageIndex === normalizedPageIndex
    ? this.pageTurnPreviewStateValue
    : this.pageTurnPreviewBatchStateValue?.status === 'ready' &&
        this.pageTurnPreviewBatchStateValue.pageIndex === normalizedPageIndex
      ? this.pageTurnPreviewBatchStateValue
      : null
  if (readyState && !readerTextPageCommitOwnerIsValid(readyState)) {
    this.restartInvalidatedPageTurnPreviewCommitment(readyState)
    return null
  }
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
  const physicalPagesInRange = (start, end, reverse = false) => {
    const pages = []
    const pageStartIndex = Math.max(0, Math.floor(Number(start) || 0))
    const pageEndIndex = Math.min(pageCount, Math.max(pageStartIndex, Math.floor(Number(end) || 0)))
    for (let pageIndex = pageStartIndex; pageIndex < pageEndIndex; pageIndex += 1) {
      if (Math.abs(pageIndex - centerPageIndex) % step === 0) pages.push(pageIndex)
    }
    return reverse ? pages.reverse() : pages
  }
  const chapterPages = chapter => {
    const pageStartIndex = Math.max(0, Math.floor(Number(chapter?.pageStartIndex) || 0))
    const chapterPageCount = Math.max(0, Math.floor(Number(chapter?.pageCount) || 0))
    return physicalPagesInRange(pageStartIndex, pageStartIndex + chapterPageCount)
  }

  addTarget(centerPageIndex, 'current')
  addTarget(centerPageIndex + step, 'next-transition')
  addTarget(centerPageIndex - step, 'previous-transition')
  addTarget(centerPageIndex + step * 2, 'next-lookahead')
  addTarget(centerPageIndex + step * 3, 'next-lookahead')
  addTarget(centerPageIndex + step * 4, 'next-lookahead')
  addTarget(centerPageIndex + step * 5, 'next-lookahead')
  addTarget(centerPageIndex - step * 2, 'previous-lookahead')
  addTarget(centerPageIndex - step * 3, 'previous-lookahead')
  addTarget(centerPageIndex - step * 4, 'previous-lookahead')
  addTarget(centerPageIndex - step * 5, 'previous-lookahead')
  if (currentChapterIndex >= 0) {
    const currentChapter = chapters[currentChapterIndex]
    const currentChapterStart = Math.max(
      0,
      Math.floor(Number(currentChapter?.pageStartIndex) || 0)
    )
    const currentChapterEnd = Math.min(
      pageCount,
      currentChapterStart + Math.max(0, Math.floor(Number(currentChapter?.pageCount) || 0))
    )
    chapterPages(currentChapter)
      .forEach(pageIndex => addTarget(pageIndex, 'current-chapter'))
    physicalPagesInRange(currentChapterEnd, pageCount)
      .forEach(pageIndex => addTarget(pageIndex, 'next-chapter'))
    physicalPagesInRange(0, currentChapterStart, true)
      .forEach(pageIndex => addTarget(pageIndex, 'previous-chapter'))
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
    captureGeometry: geometry,
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
  if (this.pageTurnPreviewStateValue || this.pageTurnPreviewBatchStateValue) {
    this.restorePageTurnLiveComposition()
  }
  this.clearPageTurnPreviewPresentationReceipt()
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewBatchStateValue)
  this.pageTurnPreviewStateValue = null
  this.pageTurnPreviewBatchStateValue = null
  const generation = ++this.pageTurnPreviewGeneration
  this.pageTurnPreviewStateValue = Object.freeze({
    token: requestedToken,
    generation,
    status: 'preparing',
    pageIndex: Number(pageIndex),
    transactionAttempts: 0,
    profileRepairs: 0,
  })
  void this.preparePageTurnPreview(generation, requestedToken, pageIndex, 0, 0)
  return this.pageTurnPreviewStateValue
}

async function preparePageTurnPreview(
  generation,
  token,
  pageIndex,
  initialTransactionAttempts = 0,
  initialProfileRepairs = 0
) {
  try {
    const previewView = await this.ensurePageTurnPreviewRenderer()
    if (generation !== this.pageTurnPreviewGeneration) return
    if (!previewView) throw new Error('Passive preview renderer is unavailable')
    const commitment = await this.resolvePageTurnPreviewLocator(
      previewView,
      pageIndex,
      'page-turn-preview',
      'Passive preview',
      () => generation === this.pageTurnPreviewGeneration,
      initialTransactionAttempts,
      initialProfileRepairs
    )
    if (generation !== this.pageTurnPreviewGeneration) return
    if (!commitment) return
    if (commitment.status === 'unsupported') {
      this.pageTurnPreviewStateValue = Object.freeze({
        token,
        generation,
        status: 'unsupported',
        pageIndex: Number(pageIndex),
        transactionAttempts: commitment.transactionAttempts,
        profileRepairs: commitment.profileRepairs,
      })
      return
    }
    const locator = commitment.locator
    const state = Object.freeze({
      token,
      generation,
      status: 'ready',
      pageIndex: locator.pageIndex,
      spineIndex: locator.spineIndex,
      href: locator.href,
      chapterPageIndex: locator.chapterPageIndex,
      chapterPageCount: locator.chapterPageCount,
      transactionAttempts: commitment.transactionAttempts,
      profileRepairs: commitment.profileRepairs,
    })
    if (!readerRememberTextPageCommit(
      state,
      previewView.renderer,
      commitment.receipt
    )) {
      if (commitment.transactionAttempts < ReaderPageTurnMaximumCommitTransactionAttempts) {
        this.pageTurnPreviewStateValue = Object.freeze({
          token,
          generation,
          status: 'preparing',
          pageIndex: Number(pageIndex),
          transactionAttempts: commitment.transactionAttempts,
          profileRepairs: commitment.profileRepairs,
        })
        return this.preparePageTurnPreview(
          generation,
          token,
          pageIndex,
          commitment.transactionAttempts,
          commitment.profileRepairs
        )
      }
      throw new Error('Passive preview commitment was invalidated')
    }
    forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)
    this.pageTurnPreviewStateValue = state
    readerTrace('page-turn-preview:ready', readerPageTurnPreviewTraceState(this.pageTurnPreviewStateValue))
  } catch (error) {
    if (generation !== this.pageTurnPreviewGeneration) return
    this.pageTurnPreviewStateValue = Object.freeze({
      token,
      generation,
      status: 'failed',
      pageIndex: Number(pageIndex),
      message: error?.message || String(error),
    })
    readerTrace('page-turn-preview:failed', readerPageTurnPreviewTraceState(this.pageTurnPreviewStateValue))
  }
}

function pageTurnPreviewBatchState(token = '') {
  const requestedToken = String(token || '')
  let state = this.pageTurnPreviewBatchStateValue
  if (!state || (requestedToken && state.token !== requestedToken)) {
    return Object.freeze({ token: requestedToken, status: 'missing' })
  }
  if (
    state.status === 'ready' &&
    !readerTextPageCommitOwnerIsValid(state)
  ) {
    this.restartInvalidatedPageTurnPreviewCommitment(state)
    state = this.pageTurnPreviewBatchStateValue
  }
  return state || Object.freeze({ token: requestedToken, status: 'missing' })
}

function beginPageTurnPreviewBatch(token, pageIndexes = []) {
  const requestedToken = String(token || '')
  if (this.pageTurnPreviewStateValue || this.pageTurnPreviewBatchStateValue) {
    this.restorePageTurnLiveComposition()
  }
  this.clearPageTurnPreviewPresentationReceipt()
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewBatchStateValue)
  this.pageTurnPreviewStateValue = null
  this.pageTurnPreviewBatchStateValue = null
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
    transactionAttempts: 0,
    profileRepairs: 0,
  })
  if (requestedPageIndexes.length) {
    void this.preparePageTurnPreviewBatchItem(generation, requestedToken, 0, 0, 0)
  }
  return this.pageTurnPreviewBatchStateValue
}

async function preparePageTurnPreviewBatchItem(
  generation,
  token,
  cursor,
  initialTransactionAttempts = 0,
  initialProfileRepairs = 0
) {
  const state = this.pageTurnPreviewBatchState(token)
  if (state.generation !== generation || state.cursor !== cursor) return
  const pageIndex = state.pageIndexes[cursor]
  try {
    const previewView = await this.ensurePageTurnPreviewRenderer()
    if (generation !== this.pageTurnPreviewGeneration) return
    if (!previewView) throw new Error('Passive raster renderer is unavailable')
    const commitment = await this.resolvePageTurnPreviewLocator(
      previewView,
      pageIndex,
      'page-turn-raster-batch',
      'Passive raster',
      () => generation === this.pageTurnPreviewGeneration,
      initialTransactionAttempts,
      initialProfileRepairs
    )
    if (generation !== this.pageTurnPreviewGeneration) return
    if (!commitment) return
    if (commitment.status === 'unsupported') {
      this.pageTurnPreviewStateValue = null
      this.pageTurnPreviewBatchStateValue = Object.freeze({
        token,
        generation,
        status: 'cancelled',
        cursor,
        total: state.total,
        pageIndexes: state.pageIndexes,
        transactionAttempts: commitment.transactionAttempts,
        profileRepairs: commitment.profileRepairs,
      })
      return
    }
    const locator = commitment.locator
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
      transactionAttempts: commitment.transactionAttempts,
      profileRepairs: commitment.profileRepairs,
    })
    const batchState = Object.freeze({
      ...state,
      ...identity,
      token,
      itemToken,
      cursor,
    })
    const identityRemembered = readerRememberTextPageCommit(
      identity,
      previewView.renderer,
      commitment.receipt
    )
    const batchRemembered = identityRemembered && readerRememberTextPageCommit(
      batchState,
      previewView.renderer,
      commitment.receipt
    )
    if (!batchRemembered) {
      forgetPageTurnPreviewCommitment(identity)
      forgetPageTurnPreviewCommitment(batchState)
      if (commitment.transactionAttempts < ReaderPageTurnMaximumCommitTransactionAttempts) {
        this.pageTurnPreviewBatchStateValue = Object.freeze({
          token,
          generation,
          status: 'preparing',
          cursor,
          total: state.total,
          pageIndexes: state.pageIndexes,
          transactionAttempts: commitment.transactionAttempts,
          profileRepairs: commitment.profileRepairs,
        })
        return this.preparePageTurnPreviewBatchItem(
          generation,
          token,
          cursor,
          commitment.transactionAttempts,
          commitment.profileRepairs
        )
      }
      throw new Error('Passive raster commitment was invalidated')
    }
    forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)
    forgetPageTurnPreviewCommitment(this.pageTurnPreviewBatchStateValue)
    this.pageTurnPreviewStateValue = identity
    this.pageTurnPreviewBatchStateValue = batchState
    readerTrace('page-turn-raster-batch:ready', readerPageTurnPreviewTraceState(this.pageTurnPreviewBatchStateValue))
  } catch (error) {
    if (generation !== this.pageTurnPreviewGeneration) return
    forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)
    forgetPageTurnPreviewCommitment(this.pageTurnPreviewBatchStateValue)
    this.pageTurnPreviewStateValue = null
    this.pageTurnPreviewBatchStateValue = Object.freeze({
      ...state,
      status: 'failed',
      pageIndex,
      paginationReady: this.isCompletePaginationProfile?.(this.paginationProfile) === true,
      message: error?.message || String(error),
    })
    readerTrace('page-turn-raster-batch:failed', readerPageTurnPreviewTraceState(this.pageTurnPreviewBatchStateValue))
  }
}

function advancePageTurnPreviewBatch(token, pageIndex) {
  const state = this.pageTurnPreviewBatchState(token)
  const completedPageIndex = Math.max(0, Math.floor(Number(pageIndex)))
  if (state.status !== 'ready' || state.pageIndex !== completedPageIndex) return state
  this.clearPageTurnPreviewPresentationReceipt()
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)
  forgetPageTurnPreviewCommitment(state)
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
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewBatchStateValue)
  this.pageTurnPreviewStateValue = null
  this.pageTurnPreviewBatchStateValue = Object.freeze({
    token: state.token,
    generation,
    status: 'cancelled',
    cursor: state.cursor ?? 0,
    total: state.total ?? 0,
    pageIndexes: state.pageIndexes ?? [],
  })
  readerTrace('page-turn-raster-batch:cancelled', readerPageTurnPreviewTraceState(this.pageTurnPreviewBatchStateValue))
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
  this.clearPageTurnLivePresentationReceipt()
  readerTrace('page-turn-preview:exposed', readerPageTurnPreviewTraceState(state))
  return true
}

function confirmPageTurnPreviewPresentation(token = '') {
  const state = this.pageTurnPreviewState(token)
  const previewView = this.pageTurnPreviewView
  if (
    state.status !== 'ready' ||
    !previewView ||
    !this.view ||
    this.pageTurnPreviewExposedToken !== state.token
  ) return false
  const previewStyle = window.getComputedStyle(previewView)
  const liveStyle = window.getComputedStyle(this.view)
  if (
    previewStyle.display === 'none' ||
    previewStyle.visibility !== 'visible' ||
    Number.parseFloat(previewStyle.opacity || '0') <= 0 ||
    liveStyle.visibility !== 'hidden' ||
    Number.parseFloat(liveStyle.opacity || '1') > 0
  ) return false
  this.issuePageTurnPreviewPresentationReceipt({
    scope: ReaderPageTurnPresentationScopePreview,
    token: state.token,
    pageIndex: state.pageIndex,
    previewGeneration: state.generation,
  })
  readerTrace('page-turn-preview:confirmed', readerPageTurnPreviewTraceState(state))
  return true
}

function restorePageTurnLiveComposition(token = '') {
  const requestedToken = String(token || '')
  if (
    requestedToken &&
    this.pageTurnPreviewExposedToken &&
    requestedToken !== this.pageTurnPreviewExposedToken
  ) return false
  const restoringExposedPreview = Boolean(this.pageTurnPreviewExposedToken)
  const previewView = this.pageTurnPreviewView
  if (previewView) {
    setStylesImportant(previewView, {
      visibility: 'hidden',
      opacity: '0',
      'pointer-events': 'none',
      'z-index': '-1',
    })
  }
  const retainedLivePositionIsCurrent = restoringExposedPreview &&
    this.pageTurnLivePresentationTargetMatchesCurrent(
      this.pageTurnLivePresentationTargetValue
    )
  const restoredLivePagePosition = retainedLivePositionIsCurrent
    ? this.currentPagePosition
    : this.pageTurnPreviewLivePagePosition
  if (restoredLivePagePosition) {
    this.updateReaderPageNumberLayer(restoredLivePagePosition)
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
  this.clearPageTurnPreviewPresentationReceipt()
  this.pageTurnPreviewLivePagePosition = null
  this.pageTurnPreviewDecorationPageIndex = null
  this.renderSurfacePaperTextureLayers()
  if (restoringExposedPreview) this.restorePageTurnLivePresentationReceipt()
  readerTrace('page-turn-preview:restored', {
    restoredExposedPreview: restoringExposedPreview,
  })
  return true
}

function destroyPageTurnPreviewRenderer(reason = 'destroy') {
  this.pageTurnPreviewGeneration += 1
  this.restorePageTurnLiveComposition()
  this.clearPageTurnPreviewPresentationReceipt()
  this.clearPageTurnLivePresentationTarget()
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewStateValue)
  forgetPageTurnPreviewCommitment(this.pageTurnPreviewBatchStateValue)
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
  resolvePageTurnPreviewLocator,
  beginPageTurnPreviewPreparation,
  preparePageTurnPreview,
  pageTurnCaptureGeometry,
  pageTurnRasterDescriptor,
  pageTurnRasterPreparationPlan,
  beginPageTurnPreviewBatch,
  preparePageTurnPreviewBatchItem,
  pageTurnPreviewBatchState,
  restartInvalidatedPageTurnPreviewCommitment,
  advancePageTurnPreviewBatch,
  cancelPageTurnPreviewBatch,
  pageTurnPreviewState,
  clearPageTurnPreviewPresentationReceipt,
  issuePageTurnPreviewPresentationReceipt,
  pageTurnPreviewPresentationReceipt,
  pageTurnPreviewContext,
  pageTurnTransitionPlan,
  exposePageTurnPreviewFinal,
  confirmPageTurnPreviewPresentation,
  restorePageTurnLiveComposition,
  destroyPageTurnPreviewRenderer,
}
