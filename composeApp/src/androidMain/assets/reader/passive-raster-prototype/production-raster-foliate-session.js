import '../vendor/foliate-js/view.js'
import { readerFontFaceCss } from '../navic-reader-typography.js'
import {
  applyReaderPageNumberLayer,
  ensureReaderPageNumberLayer,
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

const MaximumExactCommitAttempts = 12
const MaximumStableObservationFrames = 90
const MaximumContentImageWaitMillis = 4000

const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve))

// A chapter that opens with an illustration relayouts as its images load and decode, which
// invalidates a commit made in the meantime (#70). Retries wait for those images to settle.
const waitForContentImages = async view => {
  const images = []
  const fonts = []
  for (const content of view?.renderer?.getContents?.() || []) {
    const doc = content?.doc
    if (!doc) continue
    images.push(...doc.querySelectorAll('img, image'))
    if (doc.fonts?.ready) fonts.push(doc.fonts.ready.catch(() => {}))
  }
  const pending = fonts.concat(images.map(image => {
    if (typeof image.decode === 'function' && image.complete !== false) {
      return image.decode().catch(() => {})
    }
    if (image.complete !== false) return Promise.resolve()
    return new Promise(resolve => {
      image.addEventListener('load', resolve, { once: true })
      image.addEventListener('error', resolve, { once: true })
    })
  }))
  if (pending.length === 0) return
  await Promise.race([
    Promise.all(pending),
    new Promise(resolve => setTimeout(resolve, MaximumContentImageWaitMillis)),
  ])
}

// #70 diagnostics: the laid-out geometry of a renderer's current section, for comparing the
// alternate-orientation profiler with the passive capture view. Sizes only, no document text.
const readerSectionLayoutMetrics = renderer => {
  try {
    const content = renderer?.getContents?.()?.[0]
    const doc = content?.doc
    const win = doc?.defaultView
    const rect = element => {
      const box = element?.getBoundingClientRect?.()
      return box ? `${Math.round(box.width)}x${Math.round(box.height)}` : '-'
    }
    const heading = doc?.querySelector?.('[data-navic-chapter-opening-margin-capped]')
    return `size=${renderer?.size} pages=${renderer?.pages} host=${rect(renderer)} ` +
      `win=${win?.innerWidth}x${win?.innerHeight} html=${rect(doc?.documentElement)} ` +
      `body=${rect(doc?.body)} scroll=${doc?.documentElement?.scrollWidth}x${doc?.documentElement?.scrollHeight} ` +
      `cap=${heading?.dataset?.navicChapterOpeningMarginCap ?? '-'} top=${window.innerWidth}x${window.innerHeight}`
  } catch (error) {
    return `metrics-failed ${error?.name}`
  }
}

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

const PassiveRootFontFaceStyleId = 'navic-passive-root-font-face'

// The destination labels, drawn exactly as the live reader draws them, so the curl and the
// resting raster after a turn show page numbers (#31).
const renderPassivePageNumbers = async (target, settings) => {
  const presentation = target.pageNumber
  if (!presentation || !Array.isArray(presentation.labels) || presentation.labels.length === 0) {
    removePassiveDecorationLayer('data-navic-page-number-layer')
    return
  }
  const css = readerFontFaceCss(settings).trim()
  let style = document.getElementById(PassiveRootFontFaceStyleId)
  if (css) {
    if (!style) {
      style = document.createElement('style')
      style.id = PassiveRootFontFaceStyleId
      ;(document.head || document.documentElement).append(style)
    }
    style.textContent = css
  } else {
    style?.remove?.()
  }
  applyReaderPageNumberLayer(ensureReaderPageNumberLayer(), presentation)
  const sample = presentation.labels.map(label => label.text).join(' ')
  await document.fonts?.load?.(`${presentation.fontSize} ${presentation.fontFamily}`, sample)
}

// Returns true when the layout was (re)applied, which relayouts every loaded chapter; pages
// of one book under the same settings skip it, so consecutive pages do not move (#71).
const applyPassiveProfile = async (view, target, appliedLayoutKey) => {
  const renderer = view.renderer
  if (!renderer) throw new Error('passive-renderer-unavailable')
  const settings = target.readerSettings && typeof target.readerSettings === 'object'
    ? target.readerSettings
    : {}
  const render = target.render && typeof target.render === 'object' ? target.render : {}
  const pageBox = render.adaptivePageBox && typeof render.adaptivePageBox === 'object'
    ? render.adaptivePageBox
    : {}
  // Only what applyPassiveLayout reads: per-page decoration data must not defeat the reuse.
  const layoutKey = JSON.stringify([
    settings, pageBox, render.maxColumnCount, render.columnThreshold, render.topMargin,
    render.bottomMargin, target.viewportWidth, target.viewportHeight,
  ])
  if (layoutKey !== appliedLayoutKey) {
    applyPassiveLayout(view, target, settings, render, pageBox)
  }
  renderPassiveDecorations(view, target)
  await renderPassivePageNumbers(target, settings)
  if (!await waitForReaderRasterAssets(document)) {
    throw new Error('passive-raster-assets-unavailable')
  }
  return layoutKey
}

const applyPassiveLayout = (view, target, settings, render, pageBox) => {
  const renderer = view.renderer
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
    applyReaderContentDocumentRenderProfile(content.doc, settings, content.index, view.book)
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
    this.appliedLayoutKey = null
    this.lastSpineIndex = null
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
      applyReaderContentDocumentRenderProfile(doc, settings, event.detail?.index, view.book)
    })
    return view
  }

  replaceView() {
    this.appliedLayoutKey = null
    this.lastSpineIndex = null
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
    const startedAt = performance.now()
    const target = parsedProductionTarget(opaqueCaptureTarget)
    if (!target) throw new TypeError('Invalid production capture target')
    const requiredProfileKey = requiredString(profileKey, 'profileKey')
    this.activeTarget = target
    await this.open(target.publicationUrl)
    throwIfOperationAborted(signal)
    const previousLayoutKey = this.appliedLayoutKey
    this.appliedLayoutKey = null
    const layoutKey = await applyPassiveProfile(this.view, target, previousLayoutKey)
    this.appliedLayoutKey = layoutKey
    const profiledAt = performance.now()
    throwIfOperationAborted(signal)
    const spineIndex = Math.floor(Number(target.spineIndex))
    // A page of the chapter just captured, under unchanged layout, has nothing left to load:
    // one confirming frame is enough. A new chapter or layout still waits for fonts, images
    // and a settled layout before it is captured.
    const settledChapter = layoutKey === previousLayoutKey && spineIndex === this.lastSpineIndex
    this.lastSpineIndex = null
    const requiredStableFrames = settledChapter ? 1 : 7
    const renderer = this.view.renderer
    const commitRenderedDestination = renderer.commitRenderedDestination ?? renderer.commitTextPage
    const validateRenderedDestinationCommit =
      renderer.validateRenderedDestinationCommit ?? renderer.validateTextPageCommit
    if (
      typeof commitRenderedDestination !== 'function' ||
      typeof validateRenderedDestinationCommit !== 'function'
    ) throw new Error('rendered-destination-commit-unavailable')
    let lastOutcome = 'none'
    let settleMillis = 0
    let commitMillis = 0
    for (let attempt = 0; attempt < MaximumExactCommitAttempts; attempt += 1) {
      throwIfOperationAborted(signal)
      const settleStartedAt = performance.now()
      if (attempt > 0 || !settledChapter) {
        if (attempt === 0) {
          // Navigating loads the chapter; its fonts and images are awaited before committing.
          await renderer.goTo?.({ index: spineIndex })?.catch?.(() => {})
          throwIfOperationAborted(signal)
        }
        await waitForContentImages(this.view)
        await nextFrame()
        throwIfOperationAborted(signal)
      }
      const commitStartedAt = performance.now()
      settleMillis += commitStartedAt - settleStartedAt
      const result = await commitRenderedDestination.call(
        renderer,
        spineIndex,
        Math.floor(Number(target.chapterPageIndex)),
        'navigation',
      )
      throwIfOperationAborted(signal)
      const committedAt = performance.now()
      commitMillis += committedAt - commitStartedAt
      const at = result?.position
      if (result?.status === 'mismatch' && attempt === 0) {
        console.info(`[NavicPassive] mismatch section=${spineIndex} want=${target.chapterPageIndex}/` +
          `${target.chapterPageCount} ${readerSectionLayoutMetrics(renderer)}`)
      }
      lastOutcome = `${result?.status ?? 'none'}/${result?.reason ?? ''}` +
        (at ? ` at ${at.index}.${at.pageIndex}/${at.pageCount} want ${spineIndex}.` +
          `${Math.floor(Number(target.chapterPageIndex))}` : '')
      if (result?.status !== 'committed' ||
          validateRenderedDestinationCommit.call(renderer, result.receipt) !== true) continue
      lastOutcome = 'unobserved'
      let previousKey = null
      let stableFrames = 0
      for (let frame = 0; frame < MaximumStableObservationFrames; frame += 1) {
        throwIfOperationAborted(signal)
        if (validateRenderedDestinationCommit.call(renderer, result.receipt) !== true) break
        const observation = passiveObservation(
          this.view,
          target,
          opaqueCaptureTarget,
          requiredProfileKey,
        )
        const key = observation ? JSON.stringify(observation) : null
        stableFrames = key && key === previousKey ? stableFrames + 1 : 0
        if (observation && stableFrames >= requiredStableFrames) {
          if (!settledChapter) {
            console.info(`[NavicPassive] committed section=${spineIndex} page=${target.chapterPageIndex}/` +
              `${target.chapterPageCount} ${readerSectionLayoutMetrics(renderer)}`)
          }
          this.lastSpineIndex = spineIndex
          // Per-step cost of one passive page (all in milliseconds): open + layout/decorations,
          // waiting for content, the foliate commit and the stable-frame observation.
          const ms = value => Math.round(value)
          console.info(`[NavicPassiveTiming] section=${spineIndex} page=${target.chapterPageIndex} ` +
            `attempts=${attempt + 1} frames=${frame + 1} profile=${ms(profiledAt - startedAt)} ` +
            `settle=${ms(settleMillis)} commit=${ms(commitMillis)} ` +
            `observe=${ms(performance.now() - committedAt)} total=${ms(performance.now() - startedAt)}`)
          return observation
        }
        previousKey = key
        await nextFrame()
        throwIfOperationAborted(signal)
      }
    }
    throw new Error(`passive-target-not-committed ${lastOutcome}`)
  }
}
