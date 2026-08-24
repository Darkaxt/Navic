import { NavicReaderAppearanceMethods } from '../navic-reader-appearance.js'
import {
  readerAdaptiveFoliatePageBox,
  readerAdjacentPaperTextureSlots,
  readerContentCss,
  readerPageBorderOverlayVariantForPage,
  readerPageStainOverlayVariantForPage,
  readerPaperTextureVariantForPage,
  readerSpreadGutterOverlayVariantForPage,
  readerSpreadPageTextureSlots,
  readerSurfaceSpreadGutterVisible,
} from '../navic-reader-helpers.js'
import {
  readerFlowMode,
  readerFoliateFlow,
} from '../navic-reader-settings.js'
import {
  readerRealizedRasterObservation,
  waitForReaderRasterAssets,
} from '../navic-reader-render-profile.js'
import { ProductionRasterFoliateSessionCore } from './production-raster-foliate-session.js'
import { createReaderRenderParityPublication } from './synthetic-raster-foliate-session.js'

const PublicationUrl = 'public-render-parity-publication'
const MaximumCommitAttempts = 4

const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve))

const resetStage = () => {
  document.documentElement.style.cssText =
    'width:100%;height:100%;margin:0;overflow:hidden;background:#000'
  document.body.style.cssText =
    'position:fixed;inset:0;width:100vw;height:100vh;margin:0;overflow:hidden;background:#000'
  document.body.replaceChildren()
}

const targetForSettings = settings => {
  const pageBox = readerAdaptiveFoliatePageBox(
    { width: innerWidth, height: innerHeight },
    settings,
  )
  return {
    publicationUrl: PublicationUrl,
    spineIndex: 0,
    href: 'render-parity.xhtml#parity-prose',
    chapterPageIndex: 0,
    chapterPageCount: 1,
    visualPageOrdinal: 0,
    render: {
      adaptivePageBox: pageBox,
      topMargin: 32,
      bottomMargin: 32,
    },
    readerSettings: settings,
    layoutMode: 'spread',
    layoutPages: [{ slot: 'current', pageIndex: 0 }],
    viewportWidth: innerWidth,
    viewportHeight: innerHeight,
  }
}

const applyLiveRendererProfile = (view, target) => {
  const renderer = view.renderer
  const settings = target.readerSettings
  const pageBox = target.render.adaptivePageBox
  renderer.setAttribute('flow', readerFoliateFlow(readerFlowMode(settings)))
  renderer.setAttribute('max-inline-size', String(pageBox.maxInlineSize || `${innerWidth}px`))
  renderer.setAttribute('max-block-size', String(pageBox.maxBlockSize || `${innerHeight}px`))
  renderer.setAttribute('max-column-count', String(pageBox.maxColumnCount || 1))
  renderer.setAttribute('column-threshold', String(pageBox.columnThreshold || '720px'))
  renderer.setAttribute('top-margin', '32px')
  renderer.setAttribute('bottom-margin', '32px')
  if (pageBox.foliateGap) renderer.setAttribute('gap', pageBox.foliateGap)
  else renderer.removeAttribute('gap')
  if (pageBox.foliateContentGap) {
    renderer.setAttribute('content-gap', pageBox.foliateContentGap)
  } else {
    renderer.removeAttribute('content-gap')
  }
  renderer.setStyles(readerContentCss(settings))
  view.book.dir = settings.direction
  for (const content of renderer.getContents()) {
    NavicReaderAppearanceMethods.applyDocumentDirection(
      content.doc,
      settings.direction,
    )
    NavicReaderAppearanceMethods.applyDocumentTheme(
      content.doc,
      settings,
      content.index,
    )
  }
  NavicReaderAppearanceMethods.applyRendererTheme.call(
    { view, readerSettings: settings },
    settings,
  )
  renderer.render?.()
}

const commitFirstPage = async renderer => {
  for (let attempt = 0; attempt < MaximumCommitAttempts; attempt += 1) {
    const result = await renderer.commitTextPage(0, 0, 'navigation')
    if (result?.status === 'invalidated') continue
    if (result?.status === 'committed' &&
        renderer.validateTextPageCommit(result.receipt) === true) return result
  }
  throw new Error('render-parity-target-not-committed')
}

const currentSlots = (view, target, resolveVariant) =>
  readerAdjacentPaperTextureSlots({
    publicationUrl: target.publicationUrl,
    sections: view.book.sections,
    index: target.spineIndex,
    detail: { index: target.spineIndex, href: target.href },
    pagePosition: {
      pageIndex: target.chapterPageIndex,
      pageCount: target.chapterPageCount,
    },
    resolveVariant,
  }).filter(slot => slot?.slot === 'current')

const renderLiveDecorations = (view, target) => {
  const settings = target.readerSettings
  const spreadMode = 'spread'
  const textureSlots = settings.paperTextureEnabled !== false
    ? readerSpreadPageTextureSlots(
      currentSlots(view, target, readerPaperTextureVariantForPage),
      readerPaperTextureVariantForPage,
      spreadMode,
    )
    : []
  const borderSlots = settings.pageEdgesEnabled !== false
    ? readerSpreadPageTextureSlots(
      currentSlots(view, target, readerPageBorderOverlayVariantForPage),
      readerPageBorderOverlayVariantForPage,
      spreadMode,
    )
    : []
  const stainSlots = settings.paperStainsEnabled !== false
    ? readerSpreadPageTextureSlots(
      currentSlots(view, target, readerPageStainOverlayVariantForPage),
      readerPageStainOverlayVariantForPage,
      spreadMode,
    )
    : []
  const gutterSlots = readerSurfaceSpreadGutterVisible({
    settings,
    spreadMode,
    flowMode: readerFlowMode(settings),
    width: innerWidth,
    height: innerHeight,
  })
    ? currentSlots(view, target, readerSpreadGutterOverlayVariantForPage)
    : []
  NavicReaderAppearanceMethods.renderSurfacePaperTextureLayers.call({
    view,
    readerSettings: settings,
    readerFlowModeValue: readerFlowMode(settings),
    readerDirectionModeValue: settings.direction,
    effectiveReaderDirection: () => settings.direction,
    currentPagePosition: {
      pageIndex: target.chapterPageIndex,
      pageCount: target.chapterPageCount,
    },
    surfaceSpreadMode: spreadMode,
    surfaceTextureSlots: textureSlots,
    surfaceBorderOverlaySlots: borderSlots,
    surfaceStainOverlaySlots: stainSlots,
    surfaceSpreadGutterOverlaySlots: gutterSlots,
    surfaceTextureVariant: textureSlots[0]?.variant || null,
    surfaceBorderOverlayVariant: borderSlots[0]?.variant || null,
    surfaceStainOverlayVariant: stainSlots[0]?.variant || null,
    surfaceSpreadGutterOverlayVariant: gutterSlots[0]?.variant || null,
    shellCoverVisible: false,
    shellCoverDominantColor: null,
    surfacePaperTextureScrollOffset: () => ({ x: 0, y: 0 }),
  })
}

const stableObservation = async (view, target) => {
  for (let frame = 0; frame < 120; frame += 1) {
    const observation = readerRealizedRasterObservation(view, target, document)
    if (observation) return observation
    await nextFrame()
  }
  throw new Error('render-parity-observation-not-stable')
}

const documentState = view => {
  const exact = view.renderer?.exactTextPagePosition?.()
  const content = view.renderer?.getContents?.().find(entry => entry.index === exact?.index)
  const doc = content?.doc
  const inline = doc?.getElementById('inline-typography-probe')
  const heading = doc?.querySelector('h1')
  const proseRect = doc?.getElementById('parity-prose')?.getBoundingClientRect?.()
  const bodyRect = doc?.body?.getBoundingClientRect?.()
  return {
    publicationDirection: view.book?.dir || '',
    rootDirection: doc?.documentElement?.getAttribute('dir') || '',
    bodyDirection: doc?.body?.getAttribute('dir') || '',
    inlineNormalized: inline?.dataset?.navicInlineTypographyNormalized === 'true',
    inlineFontSizePriority: inline?.style?.getPropertyPriority('font-size') || '',
    inlineFontFamilyPriority: inline?.style?.getPropertyPriority('font-family') || '',
    lineFragmentsNormalized:
      doc?.documentElement?.dataset?.navicLineFragmentsNormalized === 'true',
    mergedFragmentText: Array.from(doc?.querySelectorAll?.('p') || [])
      .map(node => node.textContent)
      .find(text => text.includes('The public parity chapter')) || '',
    looseParagraphCount:
      doc?.querySelectorAll?.('[data-navic-loose-text-paragraph="true"]').length || 0,
    paragraphBlockCount:
      doc?.querySelectorAll?.('[data-navic-paragraph-block="true"]').length || 0,
    chapterOpeningCapped:
      heading?.getAttribute?.('data-navic-chapter-opening-margin-capped') === 'true',
    chapterOpeningMargin: heading?.style?.getPropertyValue('margin-top') || '',
    contentGeometry: {
      bodyWidth: Math.round(bodyRect?.width || 0),
      bodyHeight: Math.round(bodyRect?.height || 0),
      proseLeft: Math.round(proseRect?.left || 0),
      proseTop: Math.round(proseRect?.top || 0),
      proseWidth: Math.round(proseRect?.width || 0),
      proseHeight: Math.round(proseRect?.height || 0),
    },
  }
}

const runLiveScenario = async settings => {
  resetStage()
  const view = document.createElement('foliate-view')
  view.setAttribute('aria-label', 'Passive Foliate raster session')
  view.style.cssText = 'position:fixed;inset:0;width:100vw;height:100vh;display:block'
  view.addEventListener('load', event => {
    const doc = event.detail?.doc
    if (!doc) return
    NavicReaderAppearanceMethods.applyDocumentDirection(doc, settings.direction)
    NavicReaderAppearanceMethods.applyDocumentTheme(doc, settings, event.detail?.index)
  })
  document.body.append(view)
  await view.open(createReaderRenderParityPublication())
  const target = targetForSettings(settings)
  applyLiveRendererProfile(view, target)
  await nextFrame()
  await nextFrame()
  const commit = await commitFirstPage(view.renderer)
  target.chapterPageCount = commit.position.pageCount
  renderLiveDecorations(view, target)
  if (!await waitForReaderRasterAssets(document)) {
    throw new Error('live-render-parity-assets-unavailable')
  }
  const observation = await stableObservation(view, target)
  target.rasterProfileKey = observation.rasterProfileKey
  target.paginationFingerprint = observation.paginationFingerprint
  target.layoutFingerprint = observation.layoutFingerprint
  target.decorationFingerprint = observation.decorationFingerprint
  return {
    target,
    observation,
    documentState: documentState(view),
  }
}

const runPassiveScenario = async target => {
  resetStage()
  const prototype = customElements.get('foliate-view').prototype
  const originalOpen = prototype.open
  prototype.open = function (publication) {
    if (publication === target.publicationUrl) {
      return originalOpen.call(this, createReaderRenderParityPublication())
    }
    return originalOpen.call(this, publication)
  }
  try {
    const host = document.createElement('section')
    host.id = 'passive-raster-stage'
    host.style.cssText = 'position:fixed;inset:0;width:100vw;height:100vh'
    document.body.append(host)
    const core = new ProductionRasterFoliateSessionCore(host)
    core.view.style.cssText =
      'position:fixed;inset:0;width:100vw;height:100vh;display:block'
    let observation
    try {
      observation = await core.commitOpaqueTarget(
        JSON.stringify(target),
        String(target.rasterProfileKey),
      )
    } catch (failure) {
      const realized = readerRealizedRasterObservation(core.view, target, document)
      throw new Error(`passive-parity-proof-rejected:${JSON.stringify({
        failure: failure?.message || String(failure),
        expected: {
          rasterProfileKey: target.rasterProfileKey,
          paginationFingerprint: target.paginationFingerprint,
          layoutFingerprint: target.layoutFingerprint,
          decorationFingerprint: target.decorationFingerprint,
        },
        realized,
        documentState: documentState(core.view),
      })}`)
    }
    return {
      observation,
      documentState: documentState(core.view),
    }
  } finally {
    prototype.open = originalOpen
  }
}

export const runReaderRenderParityScenario = async input => {
  if (input?.mode === 'live') return runLiveScenario(input.settings || {})
  if (input?.mode === 'passive') return runPassiveScenario(input.target || {})
  throw new TypeError('Unknown render parity scenario mode')
}
