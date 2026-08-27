import '../vendor/foliate-js/view.js'
import {
  ensureReaderMovingPageBorderOverlayLayer,
  ensureReaderMovingPageStainOverlayLayer,
  ensureReaderMovingPageTextureLayer,
  ensureReaderSurfaceTextureLayer,
  ensureReaderSurfaceSpreadGutterOverlayLayer,
  readerAdjacentPaperTextureSlots,
  readerContentCss,
  readerPageBorderOverlayVariantForPage,
  readerPageStainOverlayVariantForPage,
  readerPaperLayoutProfile,
  readerPaperTextureVariantForPage,
  readerPhysicalPageSide,
  readerSpreadGutterOverlayVariantForPage,
  readerSpreadPageTextureSlots,
  readerSurfacePageDecorationGeometry,
  readerSurfaceSpreadGutterVisible,
  setStylesImportant,
  updateReaderMovingPageBorderOverlayLayer,
  updateReaderMovingPageStainOverlayLayer,
  updateReaderMovingPageTextureLayer,
  updateReaderStaticPaperBackingLayer,
  updateReaderSurfaceSpreadGutterOverlayLayer,
} from '../navic-reader-helpers.js'
import {
  applyReaderContentDocumentRenderProfile,
  applyReaderPublicationRenderDirection,
  readerLiveIssuedRasterPlan,
  readerRealizedRasterObservation,
  ReaderRasterProfileAuthorityLiveRealized,
  ReaderRasterProfileAuthorityPassiveRealized,
  waitForReaderRasterAssets,
} from '../navic-reader-render-profile.js'
import {
  readerDirectionMode,
  readerFlowMode,
  readerFoliateFlow,
  readerThemePalette,
} from '../navic-reader-settings.js'
import { throwIfOperationAborted } from './bounded-operation-runtime.js'
import { requiredString } from './synthetic-raster-foliate-session.js'

const MaximumExactCommitAttempts = 4
const MaximumStableObservationFrames = 90

const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve))

const parsedProductionTarget = opaqueCaptureTarget => {
  let target
  try {
    target = JSON.parse(requiredString(opaqueCaptureTarget, 'opaqueCaptureTarget'))
  } catch (_) {
    return null
  }
  return target && typeof target === 'object' &&
    typeof target.publicationUrl === 'string' && target.publicationUrl.trim()
    ? target
    : null
}

export const isProductionPassiveRasterTarget = opaqueCaptureTarget =>
  parsedProductionTarget(opaqueCaptureTarget) != null

const removePassiveDecorationLayer = attribute => {
  document.querySelector(`[${attribute}="true"]`)?.remove?.()
}

const currentSlots = slots => slots.filter(slot => slot?.slot === 'current')

const renderPassiveDecorations = (view, target) => {
  const settings = target.readerSettings && typeof target.readerSettings === 'object'
    ? target.readerSettings
    : {}
  const sections = view.book?.sections || []
  const index = Math.max(0, Math.floor(Number(target.spineIndex) || 0))
  const pageIndex = Math.max(0, Math.floor(Number(target.chapterPageIndex) || 0))
  const pageCount = Math.max(1, Math.floor(Number(target.chapterPageCount) || 1))
  const detail = { href: target.href }
  const pagePosition = { pageIndex, pageCount }
  const spreadMode = target.layoutMode === 'spread' ? 'spread' : 'single'
  const flowMode = readerFlowMode(settings)
  const readerDirection = readerDirectionMode(settings)
  const render = target.render && typeof target.render === 'object' ? target.render : {}
  const pageBox = render.adaptivePageBox && typeof render.adaptivePageBox === 'object'
    ? render.adaptivePageBox
    : {}
  const layoutProfile = readerPaperLayoutProfile({
    flowMode,
    width: target.viewportWidth,
    height: target.viewportHeight,
    spreadMode,
    pageSide: readerPhysicalPageSide({
      pageIndex: target.visualPageOrdinal,
      readerDirection,
    }),
  })
  const decorationGeometry = readerSurfacePageDecorationGeometry({
    settings,
    spreadMode,
    foliateGap: pageBox.foliateGap,
    shellCoverVisible: false,
    coverTint: null,
    layoutProfile,
  })
  const slotsFor = resolveVariant => currentSlots(readerAdjacentPaperTextureSlots({
    publicationUrl: target.publicationUrl,
    sections,
    index,
    detail,
    pagePosition,
    resolveVariant,
  }))

  if (settings.paperTextureEnabled !== false) {
    const textureSlots = readerSpreadPageTextureSlots(
      slotsFor(readerPaperTextureVariantForPage),
      readerPaperTextureVariantForPage,
      spreadMode,
    )
    updateReaderStaticPaperBackingLayer(
      ensureReaderSurfaceTextureLayer(),
      textureSlots,
      settings,
      decorationGeometry,
    )
    updateReaderMovingPageTextureLayer(
      ensureReaderMovingPageTextureLayer(),
      textureSlots,
      settings,
      null,
      flowMode,
      readerDirection,
      decorationGeometry,
    )
  } else {
    removePassiveDecorationLayer('data-navic-surface-paper-texture-layer')
    removePassiveDecorationLayer('data-navic-moving-page-paper-texture-layer')
  }

  if (settings.pageEdgesEnabled !== false) {
    const borderSlots = readerSpreadPageTextureSlots(
      slotsFor(readerPageBorderOverlayVariantForPage),
      readerPageBorderOverlayVariantForPage,
      spreadMode,
    )
    updateReaderMovingPageBorderOverlayLayer(
      ensureReaderMovingPageBorderOverlayLayer(),
      borderSlots,
      settings,
      null,
      flowMode,
      readerDirection,
      decorationGeometry,
    )
  } else {
    removePassiveDecorationLayer('data-navic-moving-page-border-overlay-layer')
  }

  if (settings.paperStainsEnabled !== false) {
    const stainSlots = readerSpreadPageTextureSlots(
      slotsFor(readerPageStainOverlayVariantForPage),
      readerPageStainOverlayVariantForPage,
      spreadMode,
    )
    updateReaderMovingPageStainOverlayLayer(
      ensureReaderMovingPageStainOverlayLayer(),
      stainSlots,
      settings,
      null,
      flowMode,
      readerDirection,
      decorationGeometry,
    )
  } else {
    removePassiveDecorationLayer('data-navic-moving-page-stain-overlay-layer')
  }

  if (readerSurfaceSpreadGutterVisible({
    settings,
    spreadMode,
    flowMode,
    width: target.viewportWidth,
    height: target.viewportHeight,
  })) {
    const gutterSlots = slotsFor(readerSpreadGutterOverlayVariantForPage)
    updateReaderSurfaceSpreadGutterOverlayLayer(
      ensureReaderSurfaceSpreadGutterOverlayLayer(),
      gutterSlots,
      settings,
      null,
      flowMode,
      readerDirection,
    )
  } else {
    removePassiveDecorationLayer('data-navic-surface-spread-gutter-overlay-layer')
  }
}

const applyPassiveProfile = async (view, target) => {
  const renderer = view.renderer
  if (!renderer) throw new Error('passive-renderer-unavailable')
  const settings = target.readerSettings && typeof target.readerSettings === 'object'
    ? target.readerSettings
    : {}
  const render = target.render && typeof target.render === 'object' ? target.render : {}
  const pageBox = render.adaptivePageBox && typeof render.adaptivePageBox === 'object'
    ? render.adaptivePageBox
    : {}
  renderer.setAttribute('flow', readerFoliateFlow(readerFlowMode(settings)))
  renderer.setAttribute('max-inline-size', String(pageBox.maxInlineSize || `${target.viewportWidth}px`))
  renderer.setAttribute('max-block-size', String(pageBox.maxBlockSize || `${target.viewportHeight}px`))
  renderer.setAttribute('max-column-count', String(pageBox.maxColumnCount || render.maxColumnCount || 1))
  renderer.setAttribute('column-threshold', String(pageBox.columnThreshold || render.columnThreshold || '720px'))
  renderer.setAttribute('top-margin', `${Number(render.topMargin ?? settings.topMargin ?? 90)}px`)
  renderer.setAttribute('bottom-margin', `${Number(render.bottomMargin ?? settings.bottomMargin ?? 50)}px`)
  if (pageBox.foliateGap) renderer.setAttribute('gap', pageBox.foliateGap)
  else renderer.removeAttribute('gap')
  if (pageBox.foliateContentGap) renderer.setAttribute('content-gap', pageBox.foliateContentGap)
  else renderer.removeAttribute('content-gap')
  renderer.setStyles(readerContentCss(settings))
  applyReaderPublicationRenderDirection(view, readerDirectionMode(settings))
  for (const content of renderer.getContents?.() || []) {
    applyReaderContentDocumentRenderProfile(content.doc, settings)
  }
  const palette = readerThemePalette(settings.theme)
  for (const element of [view, renderer]) {
    setStylesImportant(element, {
      background: palette.background,
      'background-color': palette.background,
      color: palette.foreground,
    })
  }
  renderer.render?.()
  renderPassiveDecorations(view, target)
  if (!await waitForReaderRasterAssets(document)) {
    throw new Error('passive-raster-assets-unavailable')
  }
}

const passiveObservation = (view, target, opaqueCaptureTarget, profileKey) => {
  const renderer = view.renderer
  const exactPosition = renderer?.exactTextPagePosition?.()
  if (!renderer || !exactPosition) return null
  if (
    exactPosition.index !== Math.floor(Number(target.spineIndex)) ||
    exactPosition.pageIndex !== Math.floor(Number(target.chapterPageIndex))
  ) return null
  const realized = readerRealizedRasterObservation(view, target, document)
  if (!realized) return null
  const requiredProfileKey = String(profileKey)
  let profile
  if (target.profileAuthority === ReaderRasterProfileAuthorityPassiveRealized) {
    const plan = readerLiveIssuedRasterPlan(target)
    if (
      !plan ||
      String(plan.rasterProfileKey) !== requiredProfileKey ||
      String(target.rasterProfileKey) !== requiredProfileKey ||
      String(target.paginationFingerprint) !== String(plan.paginationFingerprint) ||
      String(target.layoutFingerprint) !== String(plan.layoutFingerprint) ||
      String(target.decorationFingerprint) !== String(plan.decorationFingerprint)
    ) return null
    profile = realized
  } else if (
    target.profileAuthority === ReaderRasterProfileAuthorityLiveRealized ||
    target.profileAuthority == null
  ) {
    profile = realized
    if (
      String(realized.rasterProfileKey) !== requiredProfileKey ||
      String(target.rasterProfileKey) !== requiredProfileKey ||
      String(target.paginationFingerprint) !== String(realized.paginationFingerprint) ||
      String(target.layoutFingerprint) !== String(realized.layoutFingerprint) ||
      String(target.decorationFingerprint) !== String(realized.decorationFingerprint)
    ) return null
  } else {
    return null
  }
  return Object.freeze({
    opaqueCaptureTarget,
    visualPageOrdinal: Math.floor(Number(target.visualPageOrdinal)),
    rasterProfileKey: profile.rasterProfileKey,
    paginationFingerprint: profile.paginationFingerprint,
    layoutFingerprint: profile.layoutFingerprint,
    decorationFingerprint: profile.decorationFingerprint,
    viewportAndCaptureGeometry: realized.viewportAndCaptureGeometry,
    loadedAssetUrls: realized.loadedAssetUrls,
  })
}

export class ProductionRasterFoliateSessionCore {
  constructor(host = document.getElementById('passive-raster-stage')) {
    if (!host) throw new Error('passive-host-unavailable')
    this.host = host
    this.sessionId = globalThis.crypto?.randomUUID?.() ||
      `passive-production-${Date.now()}-${Math.random().toString(36).slice(2)}`
    this.publicationUrl = null
    this.openTask = null
    this.activeTarget = null
    this.view = null
    this.replaceView()
  }

  createView() {
    const view = document.createElement('foliate-view')
    view.setAttribute('aria-label', 'Passive Foliate raster session')
    view.addEventListener('load', event => {
      const target = this.activeTarget
      const doc = event.detail?.doc
      if (!target || !doc) return
      const settings = target.readerSettings && typeof target.readerSettings === 'object'
        ? target.readerSettings
        : {}
      applyReaderPublicationRenderDirection(view, readerDirectionMode(settings))
      applyReaderContentDocumentRenderProfile(doc, settings)
    })
    return view
  }

  replaceView() {
    this.view = this.createView()
    this.host.replaceChildren(this.view)
    return this.view
  }

  async retireView(view) {
    try {
      await view?.close?.()
    } catch (_) {
      // The retired view is removed even when Foliate close reports a failure.
    }
    view?.remove?.()
  }

  async open(publicationUrl) {
    const normalizedUrl = requiredString(publicationUrl, 'publicationUrl')
    if (normalizedUrl === this.publicationUrl && this.openTask) return this.openTask
    if (this.publicationUrl != null && normalizedUrl !== this.publicationUrl) {
      const retiredView = this.view
      this.publicationUrl = null
      this.openTask = null
      await this.retireView(retiredView)
      if (this.view === retiredView) this.replaceView()
    }
    const openingView = this.view
    const task = Promise.resolve().then(() => openingView.open(normalizedUrl))
    this.publicationUrl = normalizedUrl
    this.openTask = task
    try {
      await task
      return task
    } catch (failure) {
      if (this.openTask === task) {
        this.publicationUrl = null
        this.openTask = null
        await this.retireView(openingView)
        if (this.view === openingView) this.replaceView()
      }
      throw failure
    }
  }

  async commitOpaqueTarget(opaqueCaptureTarget, profileKey, signal = null) {
    throwIfOperationAborted(signal)
    const target = parsedProductionTarget(opaqueCaptureTarget)
    if (!target) throw new TypeError('Invalid production capture target')
    const requiredProfileKey = requiredString(profileKey, 'profileKey')
    this.activeTarget = target
    await this.open(target.publicationUrl)
    throwIfOperationAborted(signal)
    await applyPassiveProfile(this.view, target)
    throwIfOperationAborted(signal)
    const renderer = this.view.renderer
    for (let attempt = 0; attempt < MaximumExactCommitAttempts; attempt += 1) {
      throwIfOperationAborted(signal)
      const result = await renderer.commitTextPage(
        Math.floor(Number(target.spineIndex)),
        Math.floor(Number(target.chapterPageIndex)),
        'navigation',
      )
      throwIfOperationAborted(signal)
      if (result?.status === 'invalidated') continue
      if (result?.status !== 'committed' ||
          renderer.validateTextPageCommit(result.receipt) !== true) continue
      let previousKey = null
      let stableFrames = 0
      for (let frame = 0; frame < MaximumStableObservationFrames; frame += 1) {
        throwIfOperationAborted(signal)
        if (renderer.validateTextPageCommit(result.receipt) !== true) break
        const observation = passiveObservation(
          this.view,
          target,
          opaqueCaptureTarget,
          requiredProfileKey,
        )
        const key = observation ? JSON.stringify(observation) : null
        stableFrames = key && key === previousKey ? stableFrames + 1 : 0
        if (observation && stableFrames >= 7) return observation
        previousKey = key
        await nextFrame()
        throwIfOperationAborted(signal)
      }
    }
    throw new Error('passive-target-not-committed')
  }
}
