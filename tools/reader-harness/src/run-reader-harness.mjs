import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { spawnSync } from 'node:child_process'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { chromium } from 'playwright'
import {
  assertBridgePostType,
  assertFirstVisibleLocationStartsAtZero,
  assertForwardPageIndexesDoNotRegress,
  assertFullEpubTraversal,
  assertNoConsoleErrors,
  assertNoConsecutiveDuplicateLocations,
  assertNoConsecutiveDuplicateVisiblePageLabels,
  assertPdfFastSequentialTurns,
  assertPdfImageSettings,
  assertPdfSmoke,
  assertRendererCssSmoke,
  assertShellCoverDoesNotNavigateWebViewToCover,
  assertSurfaceTextureTracksForwardContentMovement,
  assertTextureTracksRealPageTurnSamples,
  assertTextureTracePayloadsTrackTurnDirection,
  assertTextureKeysIgnorePageCountOnlyRelabels,
  assertTextureUpdatesAreCommittedPageBounded,
  assertTraceType,
} from './reader-trace-assertions.mjs'
import { startReaderAssetServer } from './serve-reader-assets.mjs'

const currentFile = fileURLToPath(import.meta.url)
const currentDir = path.dirname(currentFile)
const repoRoot = path.resolve(currentDir, '../../..')
const readerBridge = path.join(repoRoot, 'composeApp/src/androidMain/assets/reader/navic-reader.js')
const readerHelpers = path.join(repoRoot, 'composeApp/src/androidMain/assets/reader/navic-reader-helpers.js')
const bridgeText = fs.readFileSync(readerBridge, 'utf8')
const helperText = fs.readFileSync(readerHelpers, 'utf8')

const modeArgIndex = process.argv.indexOf('--mode')
const modeFromFlag = modeArgIndex >= 0 ? process.argv[modeArgIndex + 1] : null
const positionalMode = process.argv[2]?.startsWith('--') === false
  ? process.argv[2]
  : null
const mode = modeFromFlag || positionalMode || 'smoke'

const argValue = name => {
  const index = process.argv.indexOf(name)
  return index >= 0 ? process.argv[index + 1] : null
}

const positiveNumberArg = (name, fallback) => {
  const value = Number(argValue(name))
  return Number.isFinite(value) && value > 0 ? value : fallback
}

const readerHarnessViewport = {
  viewport: {
    width: Math.round(positiveNumberArg('--viewport-width', 393)),
    height: Math.round(positiveNumberArg('--viewport-height', 873)),
  },
  deviceScaleFactor: positiveNumberArg('--device-scale-factor', 3),
  isMobile: true,
  hasTouch: true,
}

const logReaderHarnessViewport = modeName => {
  console.log(
    `reader harness ${modeName} viewport=` +
    `${readerHarnessViewport.viewport.width}x${readerHarnessViewport.viewport.height} ` +
    `dpr=${readerHarnessViewport.deviceScaleFactor}`
  )
}

if (
  process.argv.includes('--viewport-width') ||
  process.argv.includes('--viewport-height') ||
  process.argv.includes('--device-scale-factor')
) {
  logReaderHarnessViewport(mode)
}

const performReaderTouchDrag = async (
  page,
  {
    startX,
    startY,
    endX,
    endY,
    steps = 8,
    durationMs = 420,
  }
) => {
  const client = await page.context().newCDPSession(page)
  const point = (x, y) => ({
    x: Math.round(x),
    y: Math.round(y),
    radiusX: 1,
    radiusY: 1,
    force: 1,
    id: 1,
  })
  const stepCount = Math.max(2, Number(steps) || 8)
  const delay = Math.max(8, Math.round((Number(durationMs) || 420) / stepCount))
  try {
    await client.send('Input.dispatchTouchEvent', {
      type: 'touchStart',
      touchPoints: [point(startX, startY)],
    })
    for (let index = 1; index <= stepCount; index += 1) {
      const progress = index / stepCount
      const x = startX + (endX - startX) * progress
      const y = startY + (endY - startY) * progress
      await page.waitForTimeout(delay)
      await client.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [point(x, y)],
      })
    }
    await page.waitForTimeout(delay)
    await client.send('Input.dispatchTouchEvent', {
      type: 'touchEnd',
      touchPoints: [],
    })
  } finally {
    await client.detach().catch(() => {})
  }
}

if (mode === 'phase1-stabilization') {
  const epubFixturePath = path.resolve(argValue('--epub-fixture') || '')
  const pdfFixturePath = path.resolve(argValue('--pdf-fixture') || '')
  if (!epubFixturePath || !fs.existsSync(epubFixturePath)) {
    console.error('phase1-stabilization mode requires --epub-fixture <path-to-epub>')
    process.exit(1)
  }
  if (!pdfFixturePath || !fs.existsSync(pdfFixturePath)) {
    console.error('phase1-stabilization mode requires --pdf-fixture <path-to-pdf>')
    process.exit(1)
  }

  const defaultStepTimeoutMs = 120_000
  const longTraversalTimeoutMs = 360_000
  const steps = [
    { mode: 'trace-smoke' },
    { mode: 'pagination-profile-logic' },
    { mode: 'epub-frontmatter', fixture: epubFixturePath },
    { mode: 'epub-page-boundary', fixture: epubFixturePath, timeoutMs: defaultStepTimeoutMs },
    { mode: 'epub-pagination-profile', fixture: epubFixturePath, timeoutMs: defaultStepTimeoutMs },
    { mode: 'epub-shell-cover', fixture: epubFixturePath },
    { mode: 'epub-external-shell-cover', fixture: epubFixturePath },
    { mode: 'epub-native-tap-zone-open', fixture: epubFixturePath },
    { mode: 'css-smoke', fixture: epubFixturePath },
    { mode: 'epub-link-jump-drag', fixture: epubFixturePath, timeoutMs: defaultStepTimeoutMs },
    { mode: 'epub-native-drag-single-commit', fixture: epubFixturePath, timeoutMs: defaultStepTimeoutMs },
    { mode: 'epub-native-drag-preview-underlay', fixture: epubFixturePath, timeoutMs: defaultStepTimeoutMs },
    { mode: 'texture-offset-logic' },
    { mode: 'epub-texture-scroll', fixture: epubFixturePath },
    { mode: 'epub-texture-page-turns', fixture: epubFixturePath, timeoutMs: defaultStepTimeoutMs },
    { mode: 'epub-texture-frontmatter-transition', fixture: epubFixturePath },
    { mode: 'epub-full-traversal', fixture: epubFixturePath, timeoutMs: longTraversalTimeoutMs },
    { mode: 'pdf-smoke', fixture: pdfFixturePath },
    { mode: 'pdf-fast-sequential-turns', fixture: pdfFixturePath },
    { mode: 'pdf-image-settings', fixture: pdfFixturePath },
  ]

  for (const step of steps) {
    const args = [currentFile, '--mode', step.mode]
    if (step.fixture) args.push('--fixture', step.fixture)
    const timeoutMs = step.timeoutMs ?? defaultStepTimeoutMs
    const startedAt = Date.now()
    console.log(`reader harness phase1-stabilization running: ${step.mode} timeoutMs=${timeoutMs}`)
    const result = spawnSync(process.execPath, args, {
      cwd: repoRoot,
      stdio: 'inherit',
      env: process.env,
      timeout: timeoutMs,
    })
    const elapsed = ((Date.now() - startedAt) / 1000).toFixed(1)
    if (result.error?.code === 'ETIMEDOUT') {
      console.error(`reader harness phase1-stabilization timed out at ${step.mode} elapsed=${elapsed}s timeoutMs=${timeoutMs} error=ETIMEDOUT`)
      process.exit(124)
    }
    if (result.status !== 0) {
      console.error(`reader harness phase1-stabilization failed at ${step.mode} elapsed=${elapsed}s`)
      process.exit(result.status || 1)
    }
    console.log(`reader harness phase1-stabilization completed: ${step.mode} elapsed=${elapsed}s`)
  }

  console.log(`reader harness phase1-stabilization passed: ${steps.length} checks`)
  process.exit(0)
}

if (mode === 'texture-offset-logic') {
  globalThis.document = globalThis.document || {
    body: {},
    documentElement: { clientWidth: 560, clientHeight: 873 },
  }
  globalThis.window = globalThis.window || {
    innerWidth: 560,
    innerHeight: 873,
    visualViewport: { width: 560, height: 873 },
    location: { origin: 'http://127.0.0.1', href: 'http://127.0.0.1/index.html' },
  }
  const helpers = await import(`${pathToFileURL(readerHelpers).href}?texture-offset-logic=${Date.now()}`)
  if (typeof helpers.readerSurfacePaperTextureScrollOffset !== 'function') {
    throw new Error('readerSurfacePaperTextureScrollOffset helper is not exported')
  }
  if (typeof helpers.readerPaperTextureDragDirection !== 'function') {
    throw new Error('readerPaperTextureDragDirection helper is not exported')
  }

  const assertOffset = (name, actual, expected) => {
    if (actual?.x !== expected.x || actual?.y !== expected.y) {
      throw new Error(`${name} expected ${JSON.stringify(expected)} but got ${JSON.stringify(actual)}`)
    }
  }

  assertOffset(
    'forward movement offsets texture left',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 280,
      baseOffset: 0,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: 'next',
    }),
    { x: -280, y: 0 }
  )
  assertOffset(
    'forward movement keeps texture direction across inverted renderer coordinates',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: -280,
      baseOffset: 0,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: 'next',
    }),
    { x: -280, y: 0 }
  )
  assertOffset(
    'forward area boundary keeps texture moving left through renderer wrap',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: -750,
      baseOffset: 423,
      viewportWidth: 698,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: 'next',
    }),
    { x: -698, y: 0 }
  )
  assertOffset(
    'forward area boundary matches live tablet wrap trace without inversion',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 0,
      baseOffset: 720,
      viewportWidth: 720,
      viewportHeight: 1581,
      flowMode: 'paged',
      pageTurnDirection: 'next',
      fallbackPageTurnDirection: 'next',
    }),
    { x: -720, y: 0 }
  )
  assertOffset(
    'previous area boundary keeps texture moving right through renderer wrap',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 1395,
      baseOffset: 697,
      viewportWidth: 698,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: 'previous',
    }),
    { x: 698, y: 0 }
  )
  assertOffset(
    'directionless area-wrap jump does not invert texture',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 120,
      baseOffset: 2604,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: null,
    }),
    { x: 0, y: 0 }
  )
  assertOffset(
    'directionless reverse area-wrap jump does not invert texture',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 2604,
      baseOffset: 120,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: null,
    }),
    { x: 0, y: 0 }
  )
  assertOffset(
    'directionless near-wrap with fallback next keeps texture moving left through renderer wrap',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 40,
      baseOffset: 560,
      viewportWidth: 600,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: null,
      fallbackPageTurnDirection: 'next',
    }),
    { x: -520, y: 0 }
  )
  assertOffset(
    'directionless near-wrap with fallback previous keeps texture moving right through renderer wrap',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 560,
      baseOffset: 40,
      viewportWidth: 600,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: null,
      fallbackPageTurnDirection: 'previous',
    }),
    { x: 520, y: 0 }
  )
  assertOffset(
    'directionless near-wrap without fallback does not invert texture',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 40,
      baseOffset: 560,
      viewportWidth: 600,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: null,
    }),
    { x: 0, y: 0 }
  )
  assertOffset(
    'small directionless movement still counter-moves texture',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 120,
      baseOffset: 0,
      viewportWidth: 600,
      viewportHeight: 873,
      flowMode: 'paged',
      pageTurnDirection: null,
    }),
    { x: -120, y: 0 }
  )
  assertOffset(
    'vertical forward movement offsets texture upward',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 320,
      baseOffset: 0,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged-vertical',
      pageTurnDirection: 'next',
    }),
    { x: 0, y: -320 }
  )
  assertOffset(
    'vertical backward movement offsets texture downward',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: -320,
      baseOffset: 0,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged-vertical',
      pageTurnDirection: 'previous',
    }),
    { x: 0, y: 320 }
  )
  assertOffset(
    'vertical forward boundary keeps texture moving up through renderer wrap',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 0,
      baseOffset: 873,
      viewportWidth: 560,
      viewportHeight: 873,
      flowMode: 'paged-vertical',
      pageTurnDirection: 'next',
      fallbackPageTurnDirection: 'next',
    }),
    { x: 0, y: -873 }
  )
  assertOffset(
    'vertical directionless near-wrap with fallback previous keeps texture moving down through renderer wrap',
    helpers.readerSurfacePaperTextureScrollOffset({
      position: 873,
      baseOffset: 40,
      viewportWidth: 560,
      viewportHeight: 900,
      flowMode: 'paged-vertical',
      pageTurnDirection: null,
      fallbackPageTurnDirection: 'previous',
    }),
    { x: 0, y: 833 }
  )
  const assertDirection = (name, actual, expected) => {
    if (actual !== expected) {
      throw new Error(`${name} expected ${expected} but got ${actual}`)
    }
  }
  assertDirection(
    'horizontal left drag seeds next texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: -84,
      deltaY: 8,
      flowMode: 'paged',
    }),
    'next'
  )
  assertDirection(
    'horizontal right drag seeds previous texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: 84,
      deltaY: 8,
      flowMode: 'paged',
    }),
    'previous'
  )
  assertDirection(
    'rtl horizontal left drag seeds previous texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: -84,
      deltaY: 8,
      flowMode: 'paged',
      readerDirection: 'rtl',
    }),
    'previous'
  )
  assertDirection(
    'rtl horizontal right drag seeds next texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: 84,
      deltaY: 8,
      flowMode: 'paged',
      readerDirection: 'rtl',
    }),
    'next'
  )
  assertDirection(
    'vertical paged upward drag seeds next texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: 4,
      deltaY: -84,
      flowMode: 'paged-vertical',
    }),
    'next'
  )
  assertDirection(
    'small drag does not seed texture direction',
    helpers.readerPaperTextureDragDirection({
      deltaX: 8,
      deltaY: 3,
      flowMode: 'paged',
    }),
    null
  )

  console.log('reader harness texture-offset-logic passed')
  process.exit(0)
}

if (mode === 'pagination-profile-logic') {
  globalThis.document = globalThis.document || {
    body: {},
    documentElement: { clientWidth: 500, clientHeight: 960 },
  }
  globalThis.window = globalThis.window || {
    innerWidth: 500,
    innerHeight: 960,
    devicePixelRatio: 3,
    visualViewport: { width: 500, height: 960, scale: 1 },
    location: { origin: 'http://127.0.0.1', href: 'http://127.0.0.1/index.html' },
  }
  const helpers = await import(`${pathToFileURL(readerHelpers).href}?pagination-profile-logic=${Date.now()}`)
  const paginationModule = await import(
    `${pathToFileURL(path.join(repoRoot, 'composeApp/src/androidMain/assets/reader/navic-reader-pagination.js')).href}?` +
    `pagination-profile-logic=${Date.now()}`
  )
  const pageTurnModule = await import(
    `${pathToFileURL(path.join(repoRoot, 'composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js')).href}?` +
    `pagination-profile-logic=${Date.now()}`
  )
  if (typeof helpers.readerPaginationFingerprint !== 'function') {
    throw new Error('readerPaginationFingerprint helper is not exported')
  }
  const committedPageTurnPosition = paginationModule.NavicReaderPaginationMethods?.committedPageTurnPosition
  if (typeof committedPageTurnPosition !== 'function') {
    throw new Error('committedPageTurnPosition helper is not exported')
  }
  const handleDuplicatePageTurnRelocation =
    pageTurnModule.NavicReaderPageTurnMethods?.handleDuplicatePageTurnRelocation
  if (typeof handleDuplicatePageTurnRelocation !== 'function') {
    throw new Error('handleDuplicatePageTurnRelocation helper is not exported')
  }
  if (typeof helpers.readerBuildPaginationProfile !== 'function') {
    throw new Error('readerBuildPaginationProfile helper is not exported')
  }
  if (typeof helpers.readerPaginationPositionForLocator !== 'function') {
    throw new Error('readerPaginationPositionForLocator helper is not exported')
  }
  if (typeof helpers.readerPaginationObservedChapterEntries !== 'function') {
    throw new Error('readerPaginationObservedChapterEntries helper is not exported')
  }

  const fingerprintA = helpers.readerPaginationFingerprint({
    publicationKey: 'publication.epub',
    viewportWidth: 500,
    viewportHeight: 960,
    deviceScaleFactor: 3,
    orientation: 'portrait',
    spreadMode: 'single',
    flowMode: 'paged',
    fontSource: 'navic',
    fontFamily: 'dys',
    fontSizePercent: 100,
    lineHeight: 1.55,
    paragraphSpacingPercent: 0,
    marginPercent: 6,
    publisherCss: 'enabled',
    direction: 'default',
    runtimeVersion: 'test',
  })
  const fingerprintB = helpers.readerPaginationFingerprint({
    publicationKey: 'publication.epub',
    viewportWidth: 960,
    viewportHeight: 500,
    deviceScaleFactor: 3,
    orientation: 'landscape',
    spreadMode: 'dual',
    flowMode: 'paged',
    fontSource: 'navic',
    fontFamily: 'dys',
    fontSizePercent: 100,
    lineHeight: 1.55,
    paragraphSpacingPercent: 0,
    marginPercent: 6,
    publisherCss: 'enabled',
    direction: 'default',
    runtimeVersion: 'test',
  })
  if (fingerprintA === fingerprintB) {
    throw new Error('pagination fingerprint must change when viewport/spread state changes')
  }

  const profile = helpers.readerBuildPaginationProfile({
    fingerprint: fingerprintA,
    render: {
      publicationKey: 'publication.epub',
      viewportWidth: 500,
      viewportHeight: 960,
      deviceScaleFactor: 3,
      orientation: 'portrait',
      spreadMode: 'single',
      flowMode: 'paged',
      fontSource: 'navic',
      fontFamily: 'dys',
      fontSizePercent: 100,
      lineHeight: 1.55,
      paragraphSpacingPercent: 0,
      marginPercent: 6,
      publisherCss: 'enabled',
      direction: 'default',
      runtimeVersion: 'test',
    },
    chapters: [
      { spineIndex: 15, href: 'OEBPS/Text/Hobbit_chap-13.html', title: 'Chapter XIII: Not at Home', pageCount: 16, source: 'observed' },
      { spineIndex: 16, href: 'OEBPS/Text/Hobbit_chap-14.html', title: 'Chapter XIV: Fire and Water', pageCount: 14, source: 'observed' },
      { spineIndex: 17, href: 'OEBPS/Text/Hobbit_chap-15.html', title: 'Chapter XV: The Gathering of the Clouds', pageCount: 14, source: 'estimated' },
    ],
  })
  if (profile.render?.viewportWidth !== 500 || profile.render?.viewportHeight !== 960) {
    throw new Error(`expected pagination profile to store viewport render state, got ${JSON.stringify(profile.render)}`)
  }
  if (profile.observedChapterCount !== 2 || profile.estimatedChapterCount !== 1) {
    throw new Error(`expected pagination profile to retain observed/estimated chapter counts, got ${JSON.stringify(profile)}`)
  }
  if (profile.chapters[1]?.spineIndex !== 16 || profile.chapters[1]?.source !== 'observed') {
    throw new Error(`expected pagination profile to retain chapter spine index and source, got ${JSON.stringify(profile.chapters[1])}`)
  }
  const observedEntries = helpers.readerPaginationObservedChapterEntries(profile)
  if (observedEntries.length !== 2 || observedEntries[1]?.key !== '16:OEBPS/Text/Hobbit_chap-14.html') {
    throw new Error(`expected cached observed chapter entries to be reusable by runtime hydration, got ${JSON.stringify(observedEntries)}`)
  }
  const chapter14 = helpers.readerPaginationPositionForLocator(profile, {
    href: 'OEBPS/Text/Hobbit_chap-14.html',
    pageIndex: 1,
    pageCount: 1748,
    chapterPageIndex: 0,
    chapterPageCount: 14,
  })
  if (chapter14?.pageIndex !== 16 || chapter14?.pageCount !== 44) {
    throw new Error(`expected Chapter XIV page 1 to resolve to global 17 / 44, got ${JSON.stringify(chapter14)}`)
  }
  if (chapter14?.chapterPageIndex !== 0 || chapter14?.chapterPageCount !== 14) {
    throw new Error(`expected Chapter XIV local page 1 / 14, got ${JSON.stringify(chapter14)}`)
  }
  const chapter14BySpine = helpers.readerPaginationPositionForLocator(profile, {
    href: 'OEBPS/Text/current.xhtml',
    spineIndex: 16,
    pageIndex: 1,
    pageCount: 1748,
    chapterPageIndex: 0,
    chapterPageCount: 14,
  })
  if (chapter14BySpine?.pageIndex !== 16 || chapter14BySpine?.pageCount !== 44) {
    throw new Error(`expected Chapter XIV page 1 to resolve by spine index when href is stale, got ${JSON.stringify(chapter14BySpine)}`)
  }
  const chapter14WithStaleMatchingHref = helpers.readerPaginationPositionForLocator(profile, {
    href: 'OEBPS/Text/Hobbit_chap-13.html',
    spineIndex: 16,
    pageIndex: 1,
    pageCount: 1748,
    chapterPageIndex: 13,
    chapterPageCount: 14,
  })
  if (
    chapter14WithStaleMatchingHref?.pageIndex !== 29 ||
    chapter14WithStaleMatchingHref?.chapterPageIndex !== 13 ||
    chapter14WithStaleMatchingHref?.chapterPageCount !== 14
  ) {
    throw new Error(
      'expected current spine index to outrank a stale but matching previous href for chapter rail endpoints, got ' +
        JSON.stringify(chapter14WithStaleMatchingHref)
    )
  }
  const onePageSectionAfterTap = committedPageTurnPosition.call(
    {
      currentPagePosition: {
        pageIndex: 4,
        pageCount: 388,
        pageCountSource: 'pagination-profile',
        chapterPageIndex: 0,
        chapterPageCount: 1,
      },
    },
    {
      pageIndex: 3,
      pageCount: 388,
      pageCountSource: 'pagination-profile',
      chapterPageIndex: 0,
      chapterPageCount: 1,
    },
    'page-turn:next'
  )
  if (onePageSectionAfterTap?.pageIndex !== 3) {
    throw new Error(
      'expected page-turn override to leave a one-page section candidate unchanged; got ' +
        JSON.stringify(onePageSectionAfterTap)
    )
  }
  const unchangedProfileCandidateAfterTap = committedPageTurnPosition.call(
    {
      currentPagePosition: {
        pageIndex: 2,
        pageCount: 388,
        pageCountSource: 'pagination-profile',
        chapterPageIndex: 0,
        chapterPageCount: 1,
      },
    },
    {
      pageIndex: 2,
      pageCount: 388,
      pageCountSource: 'pagination-profile',
    },
    'page-turn:next'
  )
  if (unchangedProfileCandidateAfterTap?.pageIndex !== 2) {
    throw new Error(
      'expected page-turn override to leave an unchanged pagination-profile candidate unchanged; got ' +
        JSON.stringify(unchangedProfileCandidateAfterTap)
    )
  }
  let duplicateFallbackTarget = null
  let duplicateFallbackReason = null
  let duplicateFallbackScheduledReason = null
  const duplicateFallbackHandled = handleDuplicatePageTurnRelocation.call(
    {
      view: {
        book: {
          sections: [
            { href: 'cover.xhtml' },
            { href: 'title-1.xhtml' },
            { href: 'title-2.xhtml' },
            { href: 'title-3.xhtml' },
            { href: 'stalled-title.xhtml' },
            { href: 'chapter-1.xhtml' },
          ],
        },
        renderer: {
          getContents: () => [{ index: 4 }],
          goTo: target => {
            duplicateFallbackTarget = target
            return Promise.resolve()
          },
        },
      },
      sectionTargetsCover: (_section, index) => index === 0,
      currentLoadedSectionIndex: pageTurnModule.NavicReaderPageTurnMethods.currentLoadedSectionIndex,
      adjacentReadableSectionIndex: pageTurnModule.NavicReaderPageTurnMethods.adjacentReadableSectionIndex,
      beginControlledRelocation: reason => {
        duplicateFallbackReason = reason
      },
      scheduleControlledRelocationFallback: reason => {
        duplicateFallbackScheduledReason = reason
      },
    },
    { index: 4 },
    'page-turn:next'
  )
  if (!duplicateFallbackHandled || duplicateFallbackTarget?.index !== 5) {
    throw new Error(
      'expected duplicate page-turn relocation to fall back to adjacent readable section 5; got ' +
        JSON.stringify({ duplicateFallbackHandled, duplicateFallbackTarget })
    )
  }
  if (duplicateFallbackReason !== 'page-turn:next:adjacent' || duplicateFallbackScheduledReason !== 'page-turn:next:adjacent') {
    throw new Error(
      'expected duplicate page-turn relocation to use adjacent controlled relocation reason; got ' +
        JSON.stringify({ duplicateFallbackReason, duplicateFallbackScheduledReason })
    )
  }

  console.log('reader harness pagination-profile-logic passed')
  process.exit(0)
}

if (mode === 'adaptive-page-box-logic') {
  globalThis.document = globalThis.document || {
    body: {},
    documentElement: { clientWidth: 1232, clientHeight: 1974 },
  }
  globalThis.window = globalThis.window || {
    innerWidth: 1232,
    innerHeight: 1974,
    devicePixelRatio: 3,
    visualViewport: { width: 1232, height: 1974, scale: 1 },
    location: { origin: 'http://127.0.0.1', href: 'http://127.0.0.1/index.html' },
  }
  const helpers = await import(`${pathToFileURL(readerHelpers).href}?adaptive-page-box-logic=${Date.now()}`)
  if (typeof helpers.readerAdaptiveFoliatePageBox !== 'function') {
    throw new Error('readerAdaptiveFoliatePageBox helper is not exported')
  }

  const parsePx = value => Number(String(value).replace(/px$/, ''))
  const portrait = helpers.readerAdaptiveFoliatePageBox({ width: 1232, height: 1974 }, { marginPercent: 0 })
  const portraitInline = parsePx(portrait.maxInlineSize)
  const portraitBlock = parsePx(portrait.maxBlockSize)
  if (portraitInline <= 1000 || portraitBlock <= 1800) {
    throw new Error(
      'Expected large portrait EPUB surfaces to use available text capacity instead of Foliate defaults, got ' +
      JSON.stringify(portrait)
    )
  }
  if (portraitInline !== 1232 || portraitBlock !== 1974) {
    throw new Error(
      'Expected adaptive EPUB page box to use the full viewport and leave folio margins to renderer attributes, got ' +
      JSON.stringify({ portrait, portraitInline, portraitBlock })
    )
  }
  if (portraitInline === 720 || portraitBlock === 1440) {
    throw new Error('Adaptive page box must not preserve Foliate 720x1440 defaults')
  }
  if (portrait.maxColumnCount !== '0') {
    throw new Error(`Expected portrait auto composition to stay delegated to Foliate/Anx maxColumnCount=0, got ${JSON.stringify(portrait)}`)
  }
  const tabS9UltraPortrait = helpers.readerAdaptiveFoliatePageBox(
    { width: 1848, height: 2960 },
    { marginPercent: 0 }
  )
  const tabS9Inline = parsePx(tabS9UltraPortrait.maxInlineSize)
  const tabS9Block = parsePx(tabS9UltraPortrait.maxBlockSize)
  if (tabS9Inline < 1500 || tabS9Block < 2600) {
    throw new Error(
      'Expected large tablet portrait EPUB surfaces to avoid phone/fold hard caps and use folio-like capacity, got ' +
      JSON.stringify(tabS9UltraPortrait)
    )
  }
  if (tabS9Inline !== 1848 || tabS9Block !== 2960) {
    throw new Error(
      'Expected tablet EPUB page box to use the full viewport before Anx side/top/bottom margins are applied, got ' +
      JSON.stringify({ tabS9UltraPortrait, tabS9Inline, tabS9Block })
    )
  }
  if (tabS9UltraPortrait.maxColumnCount !== '0') {
    throw new Error(`Expected tablet portrait auto composition to let Foliate combine same-section pages by columnThreshold, got ${JSON.stringify(tabS9UltraPortrait)}`)
  }
  const explicitPortraitSpread = helpers.readerAdaptiveFoliatePageBox(
    { width: 1232, height: 1974 },
    { marginPercent: 0, maxColumnCount: 2 }
  )
  if (explicitPortraitSpread.maxColumnCount !== '2') {
    throw new Error(`Expected explicit portrait spread setting to survive adaptive shell resolution, got ${JSON.stringify(explicitPortraitSpread)}`)
  }
  const explicitLandscapeSingle = helpers.readerAdaptiveFoliatePageBox(
    { width: 1974, height: 1232 },
    { marginPercent: 0, maxColumnCount: 1 }
  )
  if (explicitLandscapeSingle.maxColumnCount !== '2') {
    throw new Error(`Expected wide landscape to force a two-page spread even when a stale single-column setting is persisted, got ${JSON.stringify(explicitLandscapeSingle)}`)
  }

  const userMargin = helpers.readerAdaptiveFoliatePageBox({ width: 1232, height: 1974 }, { marginPercent: 20 })
  if (parsePx(userMargin.maxInlineSize) !== portraitInline || parsePx(userMargin.maxBlockSize) !== portraitBlock) {
    throw new Error(
      'Legacy marginPercent must not shrink the page box; Anx side/top/bottom renderer attributes own folio margins, got ' +
      JSON.stringify({ portrait, userMargin })
    )
  }

  const normalPhone = helpers.readerAdaptiveFoliatePageBox({ width: 393, height: 873 }, { marginPercent: 0 })
  const normalPhoneInline = parsePx(normalPhone.maxInlineSize)
  const normalPhoneBlock = parsePx(normalPhone.maxBlockSize)
  if (normalPhoneInline !== 393 || normalPhoneBlock !== 873) {
    throw new Error(
      'Normal phone EPUB surfaces should use the viewport before renderer margins are applied, got ' +
      JSON.stringify({ normalPhone, normalPhoneInline, normalPhoneBlock })
    )
  }

  const landscape = helpers.readerAdaptiveFoliatePageBox({ width: 1974, height: 1232 }, { marginPercent: 0 })
  if (landscape.maxColumnCount !== '2') {
    throw new Error(`Expected wide landscape auto composition to request a two-page spread, got ${JSON.stringify(landscape)}`)
  }
  if (parsePx(landscape.maxInlineSize) !== 1974 || parsePx(landscape.maxBlockSize) !== 1232) {
    throw new Error(`Expected landscape page box to use wide viewport capacity, got ${JSON.stringify(landscape)}`)
  }

  console.log('reader harness adaptive-page-box-logic passed')
  process.exit(0)
}

if (mode === 'serve-smoke') {
  const server = await startReaderAssetServer({ repoRoot })
  try {
    const response = await fetch(`${server.origin}/index.html`)
    if (response.status !== 200) {
      console.error(`Expected /index.html to return 200, got ${response.status}`)
      process.exit(1)
    }
    const html = await response.text()
    if (!html.includes('navic-reader.js')) {
      console.error('Expected served /index.html to reference navic-reader.js')
      process.exit(1)
    }
    console.log(`reader harness serve-smoke passed: ${server.origin}`)
  } finally {
    await server.close()
  }
  process.exit(0)
}

if (mode === 'trace-smoke') {
  const server = await startReaderAssetServer({ repoRoot })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.stack || error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(250)
    const trace = await page.evaluate(() => window.__navicReaderTrace || [])
    assertNoConsoleErrors(errors)
    assertTraceType(trace, 'runtime:ready')
    console.log(`reader harness trace-smoke passed: ${server.origin}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'font-css-smoke') {
  const server = await startReaderAssetServer({ repoRoot })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.stack || error?.message || String(error)))
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    const result = await page.evaluate(async helperUrl => {
      const helpers = await import(helperUrl)
      const safeSettings = {
        fontSource: 'custom',
        customFontFamily: 'Storyteller Serif',
        customFontUrl: 'https://appassets.androidplatform.net/reader-cache/fonts/storyteller-serif.ttf',
      }
      const unsafeSettings = {
        fontSource: 'custom',
        customFontFamily: 'Bad Font"; color:red;/*',
        customFontUrl: 'https://evil.example/fonts/bad.ttf',
      }
      const measurePublisherSpanText = async fontSizePercent => {
        const frame = document.createElement('iframe')
        document.body.appendChild(frame)
        const doc = frame.contentDocument
        doc.open()
        doc.write(`
          <!doctype html>
          <html>
            <head>
              <style>
                .publisher-body-text { font-size: 10px; }
                .publisher-block-text { font-size: 10px; }
                .publisher-fixed-paragraph { font-size: 10px; }
                .publisher-wrapper-text { font-size: 10px; }
                .publisher-table-text td { font-size: 9px; }
                .publisher-important-wrapper p,
                .publisher-important-wrapper span {
                  font-size: 10px !important;
                }
              </style>
            </head>
            <body>
              <span class="publisher-body-text" data-probe="direct-body">Publisher direct inline body text.</span>
              <span data-probe="inline-important-body" style="font-size: 10px !important;">
                Publisher inline-important body text.
              </span>
              <p><span class="publisher-body-text" data-probe="body">Publisher span-wrapped body text.</span></p>
              <div class="publisher-block-text" data-probe="body-block">Publisher block-wrapped body text.<br/>Second line.</div>
              <section class="publisher-wrapper-text">
                <p><span data-probe="nested-wrapper-body">Publisher nested wrapper body text.</span></p>
              </section>
              <p class="publisher-fixed-paragraph" data-probe="fixed-paragraph">
                <span>Publisher fixed-size paragraph text.</span>
              </p>
              <section class="publisher-important-wrapper">
                <p data-probe="important-class-body">
                  <span>Publisher class-important body text.</span>
                </p>
              </section>
              <table class="publisher-table-text">
                <tr>
                  <td data-probe="table-body">Publisher table-cell body text.</td>
                </tr>
              </table>
              <h1 data-probe="heading">Chapter title</h1>
            </body>
          </html>
        `)
        doc.close()
        const style = doc.createElement('style')
        style.textContent = helpers.readerContentCss({
          fontSizePercent,
          lineHeight: 1.55,
          paragraphSpacingPercent: 0,
        })
        doc.head.append(style)
        helpers.normalizeReaderInlineTypography(doc, { fontSizePercent })
        await new Promise(resolve => frame.contentWindow.requestAnimationFrame(resolve))
        const directBodyStyle = frame.contentWindow.getComputedStyle(doc.querySelector('[data-probe="direct-body"]'))
        const inlineImportantBodyStyle = frame.contentWindow.getComputedStyle(doc.querySelector('[data-probe="inline-important-body"]'))
        const bodySpanStyle = frame.contentWindow.getComputedStyle(doc.querySelector('[data-probe="body"]'))
        const bodyBlockStyle = frame.contentWindow.getComputedStyle(doc.querySelector('[data-probe="body-block"]'))
        const nestedWrapperBodyStyle = frame.contentWindow.getComputedStyle(doc.querySelector('[data-probe="nested-wrapper-body"]'))
        const fixedParagraphStyle = frame.contentWindow.getComputedStyle(doc.querySelector('[data-probe="fixed-paragraph"] span'))
        const importantClassBodyStyle = frame.contentWindow.getComputedStyle(doc.querySelector('[data-probe="important-class-body"] span'))
        const tableBodyStyle = frame.contentWindow.getComputedStyle(doc.querySelector('[data-probe="table-body"]'))
        const headingStyle = frame.contentWindow.getComputedStyle(doc.querySelector('[data-probe="heading"]'))
        const result = {
          directBodyFontSize: directBodyStyle.fontSize,
          directBodyFontSizeValue: Number.parseFloat(directBodyStyle.fontSize || '0'),
          inlineImportantBodyFontSize: inlineImportantBodyStyle.fontSize,
          inlineImportantBodyFontSizeValue: Number.parseFloat(inlineImportantBodyStyle.fontSize || '0'),
          bodySpanFontSize: bodySpanStyle.fontSize,
          bodySpanFontSizeValue: Number.parseFloat(bodySpanStyle.fontSize || '0'),
          bodyBlockFontSize: bodyBlockStyle.fontSize,
          bodyBlockFontSizeValue: Number.parseFloat(bodyBlockStyle.fontSize || '0'),
          nestedWrapperBodyFontSize: nestedWrapperBodyStyle.fontSize,
          nestedWrapperBodyFontSizeValue: Number.parseFloat(nestedWrapperBodyStyle.fontSize || '0'),
          fixedParagraphFontSize: fixedParagraphStyle.fontSize,
          fixedParagraphFontSizeValue: Number.parseFloat(fixedParagraphStyle.fontSize || '0'),
          importantClassBodyFontSize: importantClassBodyStyle.fontSize,
          importantClassBodyFontSizeValue: Number.parseFloat(importantClassBodyStyle.fontSize || '0'),
          tableBodyFontSize: tableBodyStyle.fontSize,
          tableBodyFontSizeValue: Number.parseFloat(tableBodyStyle.fontSize || '0'),
          headingFontSize: headingStyle.fontSize,
          headingFontSizeValue: Number.parseFloat(headingStyle.fontSize || '0'),
        }
        frame.remove()
        return result
      }
      const publisherSpanAt100 = await measurePublisherSpanText(100)
      const publisherSpanAt140 = await measurePublisherSpanText(140)
      return {
        customSource: helpers.readerFontSource(safeSettings),
        customFamily: helpers.readerEffectiveFontFamily(safeSettings),
        dysAliasFamily: helpers.readerEffectiveFontFamily({ fontSource: 'navic', fontFamily: 'dys' }),
        dyxAliasFamily: helpers.readerEffectiveFontFamily({ fontSource: 'navic', fontFamily: 'dyx' }),
        bookAliasFamily: helpers.readerEffectiveFontFamily({ fontSource: 'navic', fontFamily: 'book' }),
        legacyDysFamily: helpers.readerEffectiveFontFamily({
          fontSource: 'navic',
          fontFamily: 'OpenDyslexic, Atkinson Hyperlegible, Lexend, system-ui, sans-serif',
        }),
        safeCss: helpers.readerFontFaceCss(safeSettings),
        unsafeCss: helpers.readerFontFaceCss(unsafeSettings),
        navicCss: helpers.readerFontFaceCss({ fontSource: 'navic' }),
        publisherSpanAt100,
        publisherSpanAt140,
        publisherDirectBodyDelta: publisherSpanAt140.directBodyFontSizeValue - publisherSpanAt100.directBodyFontSizeValue,
        publisherInlineImportantBodyDelta:
          publisherSpanAt140.inlineImportantBodyFontSizeValue - publisherSpanAt100.inlineImportantBodyFontSizeValue,
        publisherSpanBodyDelta: publisherSpanAt140.bodySpanFontSizeValue - publisherSpanAt100.bodySpanFontSizeValue,
        publisherBlockBodyDelta: publisherSpanAt140.bodyBlockFontSizeValue - publisherSpanAt100.bodyBlockFontSizeValue,
        publisherNestedWrapperBodyDelta:
          publisherSpanAt140.nestedWrapperBodyFontSizeValue - publisherSpanAt100.nestedWrapperBodyFontSizeValue,
        publisherFixedParagraphDelta: publisherSpanAt140.fixedParagraphFontSizeValue - publisherSpanAt100.fixedParagraphFontSizeValue,
        publisherImportantClassBodyDelta:
          publisherSpanAt140.importantClassBodyFontSizeValue - publisherSpanAt100.importantClassBodyFontSizeValue,
        publisherTableBodyDelta: publisherSpanAt140.tableBodyFontSizeValue - publisherSpanAt100.tableBodyFontSizeValue,
        publisherSpanHeadingDelta: publisherSpanAt140.headingFontSizeValue - publisherSpanAt100.headingFontSizeValue,
      }
    }, `${server.origin}/navic-reader-helpers.js`)
    assertNoConsoleErrors(errors)
    if (result.customSource !== 'custom') {
      throw new Error(`Expected custom font source; observed ${result.customSource}`)
    }
    if (!String(result.customFamily || '').includes('Storyteller Serif')) {
      throw new Error(`Expected custom effective font family; observed ${result.customFamily || 'unset'}`)
    }
    if (!String(result.safeCss || '').includes('@font-face') || !String(result.safeCss || '').includes('storyteller-serif.ttf')) {
      throw new Error(`Expected safe custom font CSS; observed ${result.safeCss || 'unset'}`)
    }
    if (String(result.unsafeCss || '').includes('@font-face') || String(result.unsafeCss || '').includes('evil.example')) {
      throw new Error(`Expected unsafe custom font URL to be rejected; observed ${result.unsafeCss || 'unset'}`)
    }
    if (!String(result.navicCss || '').includes('Navic Literata')) {
      throw new Error('Expected bundled Navic font CSS to remain available')
    }
    if (!String(result.dysAliasFamily || '').includes('Navic OpenDyslexic')) {
      throw new Error(`Expected Dys alias to resolve to bundled OpenDyslexic stack; observed ${result.dysAliasFamily || 'unset'}`)
    }
    if (!String(result.dyxAliasFamily || '').includes('American Typewriter')) {
      throw new Error(`Expected Dyx alias to resolve to typewriter stack; observed ${result.dyxAliasFamily || 'unset'}`)
    }
    if (!String(result.bookAliasFamily || '').includes('Navic Literata')) {
      throw new Error(`Expected Book alias to resolve to bundled Literata stack; observed ${result.bookAliasFamily || 'unset'}`)
    }
    if (!String(result.legacyDysFamily || '').includes('Navic OpenDyslexic')) {
      throw new Error(`Expected legacy dyslexic stack to normalize to bundled OpenDyslexic; observed ${result.legacyDysFamily || 'unset'}`)
    }
    if (!Number.isFinite(result.publisherDirectBodyDelta) || result.publisherDirectBodyDelta <= 1) {
      throw new Error(
        `Expected font-size control to scale publisher direct body text; ` +
        `observed ${result.publisherSpanAt100?.directBodyFontSize || 'unset'} -> ${result.publisherSpanAt140?.directBodyFontSize || 'unset'}`
      )
    }
    if (!Number.isFinite(result.publisherInlineImportantBodyDelta) || result.publisherInlineImportantBodyDelta <= 1) {
      throw new Error(
        `Expected font-size control to scale publisher inline-important body text; ` +
        `observed ${result.publisherSpanAt100?.inlineImportantBodyFontSize || 'unset'} -> ${result.publisherSpanAt140?.inlineImportantBodyFontSize || 'unset'}`
      )
    }
    if (!Number.isFinite(result.publisherSpanBodyDelta) || result.publisherSpanBodyDelta <= 1) {
      throw new Error(
        `Expected font-size control to scale publisher span-wrapped body text; ` +
        `observed ${result.publisherSpanAt100?.bodySpanFontSize || 'unset'} -> ${result.publisherSpanAt140?.bodySpanFontSize || 'unset'}`
      )
    }
    if (!Number.isFinite(result.publisherBlockBodyDelta) || result.publisherBlockBodyDelta <= 1) {
      throw new Error(
        `Expected font-size control to scale publisher block-wrapped body text; ` +
        `observed ${result.publisherSpanAt100?.bodyBlockFontSize || 'unset'} -> ${result.publisherSpanAt140?.bodyBlockFontSize || 'unset'}`
      )
    }
    if (!Number.isFinite(result.publisherNestedWrapperBodyDelta) || result.publisherNestedWrapperBodyDelta <= 1) {
      throw new Error(
        `Expected font-size control to scale publisher nested-wrapper body text; ` +
        `observed ${result.publisherSpanAt100?.nestedWrapperBodyFontSize || 'unset'} -> ${result.publisherSpanAt140?.nestedWrapperBodyFontSize || 'unset'}`
      )
    }
    if (!Number.isFinite(result.publisherFixedParagraphDelta) || result.publisherFixedParagraphDelta <= 1) {
      throw new Error(
        `Expected font-size control to scale publisher fixed-size paragraph text; ` +
        `observed ${result.publisherSpanAt100?.fixedParagraphFontSize || 'unset'} -> ${result.publisherSpanAt140?.fixedParagraphFontSize || 'unset'}`
      )
    }
    if (!Number.isFinite(result.publisherImportantClassBodyDelta) || result.publisherImportantClassBodyDelta <= 1) {
      throw new Error(
        `Expected font-size control to scale publisher class-important body text; ` +
        `observed ${result.publisherSpanAt100?.importantClassBodyFontSize || 'unset'} -> ${result.publisherSpanAt140?.importantClassBodyFontSize || 'unset'}`
      )
    }
    if (!Number.isFinite(result.publisherTableBodyDelta) || result.publisherTableBodyDelta <= 1) {
      throw new Error(
        `Expected font-size control to scale publisher table-cell body text; ` +
        `observed ${result.publisherSpanAt100?.tableBodyFontSize || 'unset'} -> ${result.publisherSpanAt140?.tableBodyFontSize || 'unset'}`
      )
    }
    if (!Number.isFinite(result.publisherSpanHeadingDelta) || result.publisherSpanHeadingDelta <= 1) {
      throw new Error(
        `Expected heading probe to keep scaling with font-size control; ` +
        `observed ${result.publisherSpanAt100?.headingFontSize || 'unset'} -> ${result.publisherSpanAt140?.headingFontSize || 'unset'}`
      )
    }
    console.log('reader harness font-css-smoke passed')
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'line-fragment-prose-smoke') {
  const server = await startReaderAssetServer({ repoRoot })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.stack || error?.message || String(error)))
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    const result = await page.evaluate(async helperUrl => {
      const helpers = await import(helperUrl)
      const frame = document.createElement('iframe')
      document.body.appendChild(frame)
      const doc = frame.contentDocument
      doc.open()
      doc.write(`
        <!doctype html>
        <html>
          <body>
            <section>
              <p class="TX">The point is, I was not th</p>
              <p class="TX">e warmest cinnamon roll, but I made an extra-spe</p>
              <p class="TX">cial effort.</p>
              <p class="TX">The answer is simple.</p>
              <p class="TX"> I wanted to be Alcatraz.</p>
              <p class="TX"></p>
              <p class="TX">All my life, I’d wishe</p>
              <p class="TX">d to be an Oculator with enhanced</p>
              <p class="TX"> skills and resolve.</p>
              <p class="OTHER">Other block.</p>
            </section>
            <section data-probe="loose-body-text">
              Text before loose prose.
              <br>
              <br>
              In a hole in the ground there lived a hobbit. Not a nasty, dirty, wet hole, filled with the ends of worms and an oozy smell.
              <br>
              <br>
              <img alt="" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32'%3E%3Crect width='32' height='32' fill='white'/%3E%3C/svg%3E">
              <br>
              It had a perfectly round door like a porthole, painted green, with a shiny yellow brass knob in the exact middle.
              <br>
              <br>
            </section>
          </body>
        </html>
      `)
      doc.close()
      const normalized = helpers.normalizeReaderLineFragmentParagraphs(doc)
      const paragraphs = Array.from(doc.querySelectorAll('p'))
        .map(p => ({
          className: p.className,
          loose: p.dataset.navicLooseTextParagraph === 'true',
          text: p.textContent,
        }))
      const looseParagraphs = Array.from(doc.querySelectorAll('[data-navic-loose-text-paragraph="true"]'))
        .map(p => p.textContent.replace(/\s+/g, ' ').trim())
      frame.remove()
      return {
        normalized,
        paragraphs,
        looseParagraphs,
      }
    }, `${server.origin}/navic-reader-helpers.js`)
    assertNoConsoleErrors(errors)
    if (!Number.isFinite(result.normalized) || result.normalized < 4) {
      throw new Error(`Expected line-fragment normalizer to merge multiple continuation blocks; observed ${result.normalized}`)
    }
    const texts = result.paragraphs.map(item => item.text)
    if (!texts.includes('The point is, I was not the warmest cinnamon roll, but I made an extra-special effort.')) {
      throw new Error(`Expected word-fragment paragraphs to merge without inserted spaces: ${JSON.stringify(texts)}`)
    }
    if (!texts.includes('All my life, I’d wished to be an Oculator with enhanced skills and resolve.')) {
      throw new Error(`Expected source-leading whitespace to preserve a real word space: ${JSON.stringify(texts)}`)
    }
    if (!texts.includes('The answer is simple.') || !texts.includes(' I wanted to be Alcatraz.')) {
      throw new Error(`Expected terminal punctuation to preserve paragraph breaks: ${JSON.stringify(texts)}`)
    }
    if (!result.looseParagraphs.includes('In a hole in the ground there lived a hobbit. Not a nasty, dirty, wet hole, filled with the ends of worms and an oozy smell.')) {
      throw new Error(`Expected loose body prose separated by double breaks to become paragraph blocks: ${JSON.stringify(result.looseParagraphs)}`)
    }
    if (!result.looseParagraphs.includes('It had a perfectly round door like a porthole, painted green, with a shiny yellow brass knob in the exact middle.')) {
      throw new Error(`Expected loose body prose after media breaks to become paragraph blocks: ${JSON.stringify(result.looseParagraphs)}`)
    }
    console.log('reader harness line-fragment-prose-smoke passed')
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-frontmatter') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-frontmatter mode requires --fixture <path>')
    process.exit(1)
  }
  const fixturePath = path.resolve(fixture)
  if (!fs.existsSync(fixturePath) || !fs.statSync(fixturePath).isFile()) {
    console.error(`Fixture file not found: ${fixturePath}`)
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  let page = null
  try {
    page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.stack || error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage: message => {
          let parsed = message
          try {
            parsed = JSON.parse(message)
          } catch {
            parsed = { type: 'unparseable', message }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacing: 0.35,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForTimeout(500)
    for (let index = 0; index < 8; index += 1) {
      await page.evaluate(async () => {
        await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      await page.waitForTimeout(350)
    }

    const result = await page.evaluate(() => ({
      trace: window.__navicReaderTrace || [],
      messages: window.__navicReaderPostedMessages || [],
      rootDataset: { ...document.body.dataset },
    }))
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-frontmatter.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      ...result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertTraceType(result.trace, 'runtime:ready')
    assertTraceType(result.trace, 'relocate:raw')
    assertTraceType(result.trace, 'texture:update')
    assertBridgePostType(result.messages, 'publicationReady')
    assertBridgePostType(result.messages, 'locationChanged')
    assertFirstVisibleLocationStartsAtZero(result.messages)
    assertNoConsecutiveDuplicateLocations(result.messages)
    assertNoConsecutiveDuplicateVisiblePageLabels(result.messages)
    assertForwardPageIndexesDoNotRegress(result.messages)

    console.log(`reader harness epub-frontmatter passed: ${outputPath}`)
  } catch (error) {
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-texture-frontmatter-transition.failure.json')
    let state = null
    try {
      state = page
        ? await page.evaluate(() => {
          const view = document.querySelector('foliate-view')
          const renderer = view?.renderer
          const messages = (window.__navicReaderPostedMessages || [])
            .filter(message => message?.type === 'locationChanged')
          return {
            bodyDataset: { ...document.body.dataset },
            postedMessages: messages.slice(-12),
            trace: (window.__navicReaderTrace || []).slice(-140),
            renderer: {
              index: Number(renderer?.getContents?.()?.[0]?.index),
              page: Number(renderer?.page),
              pages: Number(renderer?.pages),
              start: Number(renderer?.start),
              end: Number(renderer?.end),
              viewSize: Number(renderer?.viewSize),
              containerPosition: Number(renderer?.containerPosition),
            },
          }
        })
        : null
    } catch (diagnosticError) {
      state = { diagnosticError: diagnosticError?.message || String(diagnosticError) }
    }
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      error: error?.stack || error?.message || String(error),
      errors,
      state,
    }, null, 2))
    console.error(error?.message || String(error))
    console.error(`reader harness epub-texture-frontmatter-transition failure diagnostics: ${outputPath}`)
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-page-boundary') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-page-boundary mode requires --fixture <path>')
    process.exit(1)
  }
  const fixturePath = path.resolve(fixture)
  if (!fs.existsSync(fixturePath) || !fs.statSync(fixturePath).isFile()) {
    console.error(`Fixture file not found: ${fixturePath}`)
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  let page = null
  try {
    page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage: message => {
          let parsed = message
          try {
            parsed = JSON.parse(message)
          } catch {
            parsed = { type: 'unparseable', message }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacing: 0.35,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForTimeout(650)
    await page.evaluate(async () => {
      await window.NavicReaderBridge.dispatch({ type: 'goToProgress', progress: 0.74 })
    })
    await page.waitForTimeout(700)
    for (let index = 0; index < 130; index += 1) {
      await page.evaluate(async () => {
        await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      await page.waitForTimeout(220)
    }

    const result = await page.evaluate(() => ({
      trace: window.__navicReaderTrace || [],
      messages: window.__navicReaderPostedMessages || [],
    }))
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-page-boundary.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      ...result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertBridgePostType(result.messages, 'locationChanged')
    assertNoConsecutiveDuplicateVisiblePageLabels(result.messages)
    assertForwardPageIndexesDoNotRegress(result.messages)
    assertTextureUpdatesAreCommittedPageBounded(result.trace)

    console.log(`reader harness epub-page-boundary passed: ${outputPath}`)
  } catch (error) {
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-texture-frontmatter-transition.failure.json')
    let state = null
    try {
      state = page
        ? await page.evaluate(() => {
          const view = document.querySelector('foliate-view')
          const renderer = view?.renderer
          const messages = (window.__navicReaderPostedMessages || [])
            .filter(message => message?.type === 'locationChanged')
          return {
            bodyDataset: { ...document.body.dataset },
            postedMessages: messages.slice(-12),
            trace: (window.__navicReaderTrace || []).slice(-160),
            renderer: {
              index: Number(renderer?.getContents?.()?.[0]?.index),
              page: Number(renderer?.page),
              pages: Number(renderer?.pages),
              start: Number(renderer?.start),
              end: Number(renderer?.end),
              viewSize: Number(renderer?.viewSize),
              containerPosition: Number(renderer?.containerPosition),
            },
          }
        })
        : null
    } catch (diagnosticError) {
      state = { diagnosticError: diagnosticError?.message || String(diagnosticError) }
    }
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      error: error?.stack || error?.message || String(error),
      errors,
      state,
    }, null, 2))
    console.error(error?.message || String(error))
    console.error(`reader harness epub-texture-frontmatter-transition failure diagnostics: ${outputPath}`)
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-pagination-profile') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-pagination-profile mode requires --fixture <path>')
    process.exit(1)
  }
  const fixturePath = path.resolve(fixture)
  if (!fs.existsSync(fixturePath) || !fs.statSync(fixturePath).isFile()) {
    console.error(`Fixture file not found: ${fixturePath}`)
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage: message => {
          let parsed = message
          try {
            parsed = JSON.parse(message)
          } catch {
            parsed = { type: 'unparseable', message }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      window.localStorage.clear()
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 0,
          marginPercent: 6,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForTimeout(700)
    await page.evaluate(async () => {
      await window.NavicReaderBridge.dispatch({ type: 'goToProgress', progress: 0.5 })
    })
    await page.waitForTimeout(900)

    const result = await page.evaluate(() => ({
      trace: window.__navicReaderTrace || [],
      messages: window.__navicReaderPostedMessages || [],
      pageNumberLabel: document.querySelector('[data-navic-page-number-layer="true"]')?.textContent || '',
      paginationCacheKeys: Object.keys(window.localStorage || {})
        .filter(key => key.startsWith('navic-reader-pagination-profile:')),
    }))
    const profileUpdates = result.trace.filter(entry => entry?.type === 'pagination-profile:updated')
    const profilePositions = result.trace.filter(entry => entry?.type === 'pagination-profile:position')
    const lastProfilePosition = profilePositions.at(-1)?.payload
    const lastLocation = [...result.messages].reverse().find(message => message?.type === 'locationChanged')
    if (!profileUpdates.length) {
      throw new Error('Expected pagination-profile:updated trace after opening the EPUB')
    }
    if (!lastProfilePosition) {
      throw new Error('Expected pagination-profile:position trace after relocating inside the EPUB')
    }
    if (!lastLocation) {
      throw new Error('Expected at least one locationChanged message after relocating inside the EPUB')
    }
    if (lastLocation.pageIndex !== lastProfilePosition.pageIndex || lastLocation.pageCount !== lastProfilePosition.pageCount) {
      throw new Error(
        'Expected locationChanged page numbers to use deterministic profile, got ' +
        `location=${JSON.stringify(lastLocation)} profile=${JSON.stringify(lastProfilePosition)}`
      )
    }
    if (lastLocation.pageCountSource !== 'pagination-profile') {
      throw new Error(
        'Expected locationChanged diagnostics to expose pagination-profile source, got ' +
        JSON.stringify(lastLocation)
      )
    }
    if (!lastLocation.paginationFingerprint || lastLocation.paginationProfilePageCount !== lastProfilePosition.pageCount) {
      throw new Error(
        'Expected locationChanged diagnostics to expose pagination fingerprint and profile page count, got ' +
        `location=${JSON.stringify(lastLocation)} profile=${JSON.stringify(lastProfilePosition)}`
      )
    }
    if (!Object.hasOwn(lastLocation, 'rawLocationTotal')) {
      throw new Error(
        'Expected locationChanged diagnostics to include raw Foliate location totals, got ' +
        JSON.stringify(lastLocation)
      )
    }
    if (!result.paginationCacheKeys.length) {
      throw new Error('Expected deterministic pagination profile to be stored in localStorage')
    }

    await page.evaluate(async publicationUrl => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 0,
          marginPercent: 6,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForTimeout(900)
    const cacheHitResult = await page.evaluate(() => ({
      trace: window.__navicReaderTrace || [],
      messages: window.__navicReaderPostedMessages || [],
    }))
    const cacheHits = cacheHitResult.trace.filter(entry => entry?.type === 'pagination-profile:cache-hit')
    if (!cacheHits.length) {
      throw new Error('Expected deterministic pagination profile cache hit after reopening the same EPUB')
    }
    const cacheHitPayload = cacheHits[0]?.payload || {}
    const postCacheProfileUpdate = cacheHitResult.trace.find(entry => entry?.type === 'pagination-profile:updated')
    if (
      postCacheProfileUpdate &&
      Number(postCacheProfileUpdate.payload?.observedChapterCount || 0) <= Number(cacheHitPayload.observedChapterCount || 0) &&
      Number(postCacheProfileUpdate.payload?.pageCount || 0) !== Number(cacheHitPayload.pageCount || 0)
    ) {
      throw new Error(
        'Expected cached pagination totals to remain stable until a new chapter is observed, got ' +
        `cache=${JSON.stringify(cacheHitPayload)} updated=${JSON.stringify(postCacheProfileUpdate.payload)}`
      )
    }
    assertNoConsoleErrors(errors)

    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-pagination-profile.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      ...result,
      cacheHitTrace: cacheHitResult.trace,
      cacheHitMessages: cacheHitResult.messages,
    }, null, 2))
    console.log(`reader harness epub-pagination-profile passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-native-tap-zone-open') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-native-tap-zone-open mode requires --fixture <path>')
    process.exit(1)
  }
  const fixturePath = path.resolve(fixture)
  if (!fs.existsSync(fixturePath) || !fs.statSync(fixturePath).isFile()) {
    console.error(`Fixture file not found: ${fixturePath}`)
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage: message => {
          let parsed = message
          try {
            parsed = JSON.parse(message)
          } catch {
            parsed = { type: 'unparseable', message }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          nativeTapZones: true,
          showTapZones: true,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 20000 })
    const result = await page.evaluate(() => ({
      trace: window.__navicReaderTrace || [],
      messages: window.__navicReaderPostedMessages || [],
      tapOverlayExists: Boolean(document.querySelector('[data-navic-tap-zone-overlay-layer="true"]')),
    }))
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-native-tap-zone-open.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      ...result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertBridgePostType(result.messages, 'publicationReady')
    assertBridgePostType(result.messages, 'locationChanged')
    if (!result.tapOverlayExists) {
      throw new Error('Expected visible tap-zone overlay layer in native tap-zone diagnostic mode')
    }
    console.log(`reader harness epub-native-tap-zone-open passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-link-jump-drag') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-link-jump-drag mode requires --fixture <path>')
    process.exit(1)
  }
  const fixturePath = path.resolve(fixture)
  if (!fs.existsSync(fixturePath) || !fs.statSync(fixturePath).isFile()) {
    console.error(`Fixture file not found: ${fixturePath}`)
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          tapZone: 'default',
          nativeTapZones: false,
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    })
    if (await page.evaluate(() => document.body.dataset.navicShellCoverVisible === 'true')) {
      await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
      await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true')
    }

    const collectState = async label => page.evaluate(sampleLabel => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const location = messages.at(-1) || null
      const view = document.querySelector('foliate-view')
      const contents = view?.renderer?.getContents?.() || []
      return {
        label: sampleLabel,
        location,
        href: location?.href || '',
        pageIndex: location?.pageIndex,
        pageCount: location?.pageCount,
        rendererPage: Number(view?.renderer?.page),
        rendererPages: Number(view?.renderer?.pages),
        contentIndexes: contents.map(content => Number(content?.index)).filter(Number.isFinite),
        traceLength: (window.__navicReaderTrace || []).length,
        messageLength: (window.__navicReaderPostedMessages || []).length,
      }
    }, label)

    const beforeLink = await collectState('before-link')
    const linkClickResult = await page.evaluate(async () => {
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      const contents = renderer?.getContents?.() || []
      const sourceContent = contents.find(content => content?.doc?.body)
      if (!sourceContent?.doc) throw new Error('Missing source content document for link-jump drag probe')
      const sourceIndex = Number(sourceContent.index)
      const sections = Array.from(view?.book?.sections || [])
      const sectionHref = section => section?.href || section?.id || ''
      const coverPattern = /cover|frontcover|coverpage|cubierta|portada/i
      const candidates = sections
        .map((section, index) => ({ index, href: sectionHref(section) }))
        .filter(section =>
          section.href &&
          section.index > sourceIndex + 1 &&
          !coverPattern.test(section.href)
        )
      const fallbackCandidates = sections
        .map((section, index) => ({ index, href: sectionHref(section) }))
        .filter(section => section.href && section.index !== sourceIndex && !coverPattern.test(section.href))
      const target = candidates[0] || fallbackCandidates[0]
      if (!target) throw new Error(`Missing non-cover target section for source index ${sourceIndex}`)
      const sourceHref = sectionHref(sections[sourceIndex])
      const sourceFolder = sourceHref.includes('/') ? sourceHref.slice(0, sourceHref.lastIndexOf('/') + 1) : ''
      const rawLinkHref = sourceFolder && target.href.startsWith(sourceFolder)
        ? target.href.slice(sourceFolder.length)
        : target.href

      const doc = sourceContent.doc
      const win = doc.defaultView
      const anchor = doc.createElement('a')
      anchor.setAttribute('data-navic-link-jump-drag-probe', 'true')
      anchor.setAttribute('href', rawLinkHref)
      anchor.textContent = `Navic link jump drag probe ${target.index}`
      anchor.style.display = 'inline-block'
      anchor.style.padding = '12px'
      anchor.style.margin = '12px'
      doc.body.prepend(anchor)
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      const rect = anchor.getBoundingClientRect()
      anchor.dispatchEvent(new win.MouseEvent('click', {
        bubbles: true,
        cancelable: true,
        button: 0,
        clientX: Math.round(rect.left + rect.width / 2),
        clientY: Math.round(rect.top + rect.height / 2),
      }))
      return {
        sourceIndex,
        rawHref: rawLinkHref,
        resolvedHref: target.href,
        sourceHref,
        targetIndex: target.index,
      }
    })
    await page.waitForFunction(
      beforeTraceLength => (window.__navicReaderTrace || [])
        .slice(beforeTraceLength)
        .some(event => event?.type === 'link:navigate'),
      beforeLink.traceLength
    )
    await page.waitForFunction(
      beforeMessageLength => (window.__navicReaderPostedMessages || [])
        .slice(beforeMessageLength)
        .some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex)),
      beforeLink.messageLength
    )
    const afterLink = await collectState('after-link')

    const viewport = page.viewportSize() || readerHarnessViewport.viewport
    await performReaderTouchDrag(page, {
      startX: viewport.width * 0.82,
      startY: viewport.height * 0.52,
      endX: viewport.width * 0.18,
      endY: viewport.height * 0.52,
      durationMs: 520,
      steps: 10,
    })
    await page.waitForFunction(
      previousPageIndex => {
        const messages = (window.__navicReaderPostedMessages || [])
          .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
        const latest = messages.at(-1)
        return latest && Number(latest.pageIndex) > Number(previousPageIndex)
      },
      afterLink.pageIndex
    )
    const afterDrag = await collectState('after-drag')
    const trace = await page.evaluate(() => window.__navicReaderTrace || [])
    const postedMessages = await page.evaluate(() => window.__navicReaderPostedMessages || [])
    const linkNavigateCount = trace.filter(event => event?.type === 'link:navigate').length
    if (linkNavigateCount < 1) {
      throw new Error('Expected link-jump drag to exercise link:navigate before dragging')
    }
    if (Number(afterDrag.pageIndex) <= Number(afterLink.pageIndex)) {
      throw new Error(
        `Expected link-jump drag to advance after EPUB link relocation; ` +
        `observed ${afterLink.pageIndex}/${afterLink.pageCount} -> ${afterDrag.pageIndex}/${afterDrag.pageCount}`
      )
    }
    const linkJumpDrag = {
      beforeLink,
      linkClickResult,
      afterLink,
      afterDrag,
      linkNavigateCount,
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-link-jump-drag.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      linkJumpDrag,
      trace,
      postedMessages,
    }, null, 2))
    assertNoConsoleErrors(errors)
    console.log(`reader harness epub-link-jump-drag passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-native-drag-preview-underlay') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-native-drag-preview-underlay mode requires --fixture <path>')
    process.exit(1)
  }
  const fixturePath = path.resolve(fixture)
  if (!fs.existsSync(fixturePath) || !fs.statSync(fixturePath).isFile()) {
    console.error(`Fixture file not found: ${fixturePath}`)
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          tapZone: 'default',
          nativeTapZones: false,
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    })
    if (await page.evaluate(() => document.body.dataset.navicShellCoverVisible === 'true')) {
      await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
      await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true')
    }

    const boundaryTarget = await page.evaluate(() => {
      const view = document.querySelector('foliate-view')
      const sections = Array.from(view?.book?.sections || [])
      const sectionLabel = section => String(section?.href || section?.id || section?.url || '')
      const readable = section => section?.linear !== 'no' && !/cover|frontcover|coverpage|cubierta|portada/i.test(sectionLabel(section))
      const currentIndex = Number(view?.renderer?.getContents?.()?.[0]?.index)
      const candidates = sections
        .map((section, index) => ({ section, index }))
        .filter(({ section, index }) => readable(section) && sections.slice(index + 1).some(readable))
      return candidates.find(candidate => candidate.index >= currentIndex)?.index ?? candidates[0]?.index ?? null
    })
    if (!Number.isFinite(boundaryTarget)) {
      throw new Error('Missing readable EPUB section with a following section for native drag preview probe')
    }
    await page.evaluate(async index => {
      const renderer = document.querySelector('foliate-view')?.renderer
      await renderer?.goTo?.({ index, anchor: () => 1 })
    }, boundaryTarget)
    await page.waitForFunction(index => {
      const renderer = document.querySelector('foliate-view')?.renderer
      const contentIndex = Number(renderer?.getContents?.()?.[0]?.index)
      const page = Number(renderer?.page)
      const pages = Number(renderer?.pages)
      return contentIndex === index &&
        Number.isFinite(page) &&
        Number.isFinite(pages) &&
        pages > 0 &&
        page >= pages - 2
    }, boundaryTarget)

    const readPreviewState = async before => page.evaluate(beforeState => {
      const layer = document.querySelector('[data-navic-page-drag-preview-layer="true"]')
      const iframe = layer?.querySelector?.('iframe[data-navic-page-drag-preview-frame="true"]')
      const style = layer ? getComputedStyle(layer) : null
      const curlSheets = Array.from(layer?.querySelectorAll?.('[data-navic-page-curl-sheet]') || [])
        .map(element => element?.dataset?.navicPageCurlSheet || '')
        .filter(Boolean)
      const snapshotState = role => {
        const snapshot = layer?.querySelector?.(`[data-navic-page-curl-snapshot="${role}"]`)
        const snapshotDoc = snapshot?.contentDocument || null
        const snapshotBodyRect = snapshotDoc?.body?.getBoundingClientRect?.()
        const snapshotHtmlRect = snapshotDoc?.documentElement?.getBoundingClientRect?.()
        const snapshotText = snapshotDoc?.body?.textContent?.replace(/\s+/g, ' ').trim() || ''
        return {
          present: Boolean(snapshot),
          ready: snapshot?.dataset.navicPageCurlSnapshotReady === 'true',
          textLength: snapshotText.length,
          datasetTextLength: Number(snapshot?.dataset.navicPageCurlSnapshotTextLength) || 0,
          bodyHeight: Number(snapshotBodyRect?.height) || 0,
          htmlHeight: Number(snapshotHtmlRect?.height) || 0,
        }
      }
      const iframeDoc = iframe?.contentDocument || null
      const iframeBodyRect = iframeDoc?.body?.getBoundingClientRect?.()
      const iframeHtmlRect = iframeDoc?.documentElement?.getBoundingClientRect?.()
      const iframeText = iframeDoc?.body?.textContent?.replace(/\s+/g, ' ').trim() || ''
      const frontSnapshot = snapshotState('front')
      const backSnapshot = snapshotState('back')
      return {
        before: beforeState,
        layerPresent: Boolean(layer),
        iframePresent: Boolean(iframe),
        ready: layer?.dataset.navicPageDragPreviewReady === 'true',
        targetIndex: Number(layer?.dataset.navicPageDragPreviewTargetIndex),
        direction: layer?.dataset.navicPageDragPreviewDirection || '',
        side: layer?.dataset.navicPageDragPreviewSide || '',
        curl: layer?.dataset.navicPageDragPreviewCurl === 'true',
        curlProgress: Number(layer?.dataset.navicPageDragPreviewCurlProgress),
        curlDirection: layer?.dataset.navicPageDragPreviewCurlDirection || '',
        curlAngle: style?.getPropertyValue('--navic-page-curl-angle')?.trim() || '',
        curlWidth: style?.getPropertyValue('--navic-page-curl-width')?.trim() || '',
        curlTransform: style?.getPropertyValue('--navic-page-curl-transform')?.trim() || '',
        curlSheetMode: layer?.dataset.navicPageCurlSheetMode || '',
        curlSheetRoles: layer?.dataset.navicPageCurlSheetRoles || '',
        curlSheetRoleCount: curlSheets.length,
        curlFrontFaceOpacity: Number(style?.getPropertyValue('--navic-page-curl-front-face-opacity')) || 0,
        curlBackFaceOpacity: Number(style?.getPropertyValue('--navic-page-curl-back-face-opacity')) || 0,
        curlSnapshots: layer?.dataset.navicPageCurlSnapshots || '',
        curlSnapshotFront: layer?.dataset.navicPageCurlSnapshotFront === 'true',
        curlSnapshotBack: layer?.dataset.navicPageCurlSnapshotBack === 'true',
        frontSnapshot,
        backSnapshot,
        width: style?.width || '',
        left: style?.left || '',
        right: style?.right || '',
        opacity: style?.opacity || '',
        background: style?.backgroundColor || '',
        iframeTextLength: iframeText.length,
        iframeBodyHeight: Number(iframeBodyRect?.height) || 0,
        iframeHtmlHeight: Number(iframeHtmlRect?.height) || 0,
        trace: (window.__navicReaderTrace || [])
          .filter(event => String(event?.type || '').startsWith('page-drag-preview'))
          .slice(-5),
      }
    }, before)

    const before = await page.evaluate(() => {
      const renderer = document.querySelector('foliate-view')?.renderer
      return {
        index: Number(renderer?.getContents?.()?.[0]?.index),
        page: Number(renderer?.page),
        pages: Number(renderer?.pages),
        size: Number(renderer?.size),
        start: Number(renderer?.start),
        end: Number(renderer?.end),
        viewSize: Number(renderer?.viewSize),
      }
    })
    await page.evaluate(async () => {
      const width = window.visualViewport?.width || window.innerWidth || 500
      await window.NavicReaderBridge.dispatch({
        type: 'previewPageDrag',
        deltaX: -Math.round(width * 0.36),
        viewWidth: width,
        phase: 'update',
      })
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    })
    let previewState = await readPreviewState(before)

    if (!previewState.layerPresent || !previewState.iframePresent) {
      throw new Error(
        `Expected native drag preview to mount a clipped adjacent-page underlay; ` +
        `layer=${previewState.layerPresent} iframe=${previewState.iframePresent} ` +
        `state=${JSON.stringify(previewState)}`
      )
    }
    if (previewState.direction !== 'next' || previewState.side !== 'right') {
      throw new Error(
        `Expected next-page native drag preview on the right side; ` +
        `observed direction=${previewState.direction || 'missing'} side=${previewState.side || 'missing'}`
      )
    }
    if (!Number.isFinite(previewState.targetIndex) || previewState.targetIndex <= boundaryTarget) {
      throw new Error(
        `Expected drag preview target to point at a following section; ` +
        `target=${previewState.targetIndex} boundary=${boundaryTarget}`
      )
    }
    if (!previewState.ready) {
      if (previewState.opacity === '0' || previewState.width === '1px' || previewState.left === '-1px') {
        throw new Error(
          `Expected not-ready boundary preview to expose the paper fallback while the adjacent page renders; ` +
          `state=${JSON.stringify(previewState)}`
        )
      }
      await page.waitForFunction(() => {
        const layer = document.querySelector('[data-navic-page-drag-preview-layer="true"]')
        const iframe = layer?.querySelector?.('iframe[data-navic-page-drag-preview-frame="true"]')
        const style = layer ? getComputedStyle(layer) : null
        const frontSnapshot = layer?.querySelector?.('[data-navic-page-curl-snapshot="front"]')
        const textLength = iframe?.contentDocument?.body?.textContent?.replace(/\s+/g, ' ').trim().length || 0
        const frontTextLength = frontSnapshot?.contentDocument?.body?.textContent?.replace(/\s+/g, ' ').trim().length || 0
        return layer?.dataset.navicPageDragPreviewReady === 'true' &&
          style?.opacity !== '0' &&
          style?.width !== '1px' &&
          textLength > 0 &&
          frontTextLength > 0
      })
      previewState = await readPreviewState(before)
    }
    if (!previewState.ready || previewState.iframeTextLength <= 0 || previewState.iframeBodyHeight <= 0) {
      throw new Error(
        `Expected boundary drag preview to expose a rendered adjacent page, not a blank loading underlay; ` +
        `ready=${previewState.ready} textLength=${previewState.iframeTextLength} ` +
        `bodyHeight=${previewState.iframeBodyHeight} state=${JSON.stringify(previewState)}`
      )
    }
    if (!previewState.frontSnapshot.present || !previewState.curlSnapshotFront || previewState.frontSnapshot.textLength <= 0) {
      await page.waitForFunction(() => {
        const layer = document.querySelector('[data-navic-page-drag-preview-layer="true"]')
        const frontSnapshot = layer?.querySelector?.('[data-navic-page-curl-snapshot="front"]')
        const textLength = frontSnapshot?.contentDocument?.body?.textContent?.replace(/\s+/g, ' ').trim().length || 0
        return layer?.dataset.navicPageCurlSnapshotFront === 'true' &&
          frontSnapshot?.dataset.navicPageCurlSnapshotReady === 'true' &&
          textLength > 0
      })
      previewState = await readPreviewState(before)
    }
    if (!previewState.curl || previewState.curlDirection !== 'next') {
      throw new Error(
        `Expected drag preview to expose curl state on the preview layer; ` +
        `curl=${previewState.curl} curlDirection=${previewState.curlDirection || 'missing'} ` +
        `state=${JSON.stringify(previewState)}`
      )
    }
    if (!Number.isFinite(previewState.curlProgress) || previewState.curlProgress <= 0 || previewState.curlProgress >= 1) {
      throw new Error(
        `Expected drag preview curl progress to be a non-terminal drag fraction; ` +
        `progress=${previewState.curlProgress} state=${JSON.stringify(previewState)}`
      )
    }
    if (!previewState.curlAngle.includes('deg') || !previewState.curlWidth.includes('px') || !previewState.curlTransform.includes('rotateY')) {
      throw new Error(
        `Expected drag preview curl CSS vars to include angle, width, and horizontal rotateY transform; ` +
        `angle=${previewState.curlAngle || 'missing'} width=${previewState.curlWidth || 'missing'} ` +
        `transform=${previewState.curlTransform || 'missing'}`
      )
    }
    const requiredSheetRoles = ['underneath', 'turning-front', 'turning-back', 'cast-shadow']
    const observedSheetRoles = String(previewState.curlSheetRoles || '').split(',').filter(Boolean)
    const missingSheetRoles = requiredSheetRoles.filter(role => !observedSheetRoles.includes(role))
    if (missingSheetRoles.length || previewState.curlSheetRoleCount < requiredSheetRoles.length) {
      throw new Error(
        `Expected mockup-style curl sheet roles during native drag; ` +
        `missing=${missingSheetRoles.join(',') || 'none'} state=${JSON.stringify(previewState)}`
      )
    }
    if (!['single', 'spread'].includes(previewState.curlSheetMode)) {
      throw new Error(
        `Expected drag preview to expose single/spread sheet mode; ` +
        `mode=${previewState.curlSheetMode || 'missing'} state=${JSON.stringify(previewState)}`
      )
    }
    if (previewState.curlFrontFaceOpacity <= 0 || previewState.curlFrontFaceOpacity > 1) {
      throw new Error(
        `Expected curl front face opacity to be active and normalized; ` +
        `opacity=${previewState.curlFrontFaceOpacity} state=${JSON.stringify(previewState)}`
      )
    }
    if (previewState.curlBackFaceOpacity < 0 || previewState.curlBackFaceOpacity > 1) {
      throw new Error(
        `Expected curl back face opacity to stay normalized; ` +
        `opacity=${previewState.curlBackFaceOpacity} state=${JSON.stringify(previewState)}`
      )
    }
    if (!previewState.frontSnapshot.present || !previewState.curlSnapshotFront) {
      throw new Error(
        `Expected native drag curl to capture a current-page front snapshot; ` +
        `front=${JSON.stringify(previewState.frontSnapshot)} state=${JSON.stringify(previewState)}`
      )
    }
    if (previewState.frontSnapshot.textLength <= 0 || previewState.frontSnapshot.bodyHeight <= 0) {
      throw new Error(
        `Expected current-page front snapshot to contain visible cloned content; ` +
        `front=${JSON.stringify(previewState.frontSnapshot)} state=${JSON.stringify(previewState)}`
      )
    }
    if (previewState.curlSheetMode === 'spread') {
      if (!previewState.backSnapshot.present || !previewState.curlSnapshotBack) {
        throw new Error(
          `Expected spread-mode curl to capture a reverse page snapshot; ` +
          `back=${JSON.stringify(previewState.backSnapshot)} state=${JSON.stringify(previewState)}`
        )
      }
      if (previewState.backSnapshot.textLength <= 0 || previewState.backSnapshot.bodyHeight <= 0) {
        throw new Error(
          `Expected spread-mode back snapshot to contain adjacent page content; ` +
          `back=${JSON.stringify(previewState.backSnapshot)} state=${JSON.stringify(previewState)}`
        )
      }
    } else if (previewState.curlSnapshotBack) {
      throw new Error(
        `Expected single-page curl mode to suppress reverse snapshot capture; ` +
        `mode=${previewState.curlSheetMode} back=${JSON.stringify(previewState.backSnapshot)}`
      )
    }

    await page.evaluate(async () => {
      await window.NavicReaderBridge.dispatch({
        type: 'previewPageDrag',
        deltaX: 0,
        phase: 'cancel',
      })
    })
    await page.waitForFunction(() => !document.querySelector('[data-navic-page-drag-preview-layer="true"]'))
    const snapshotsAfterCancel = await page.evaluate(() =>
      document.querySelectorAll('[data-navic-page-curl-snapshot]').length
    )
    if (snapshotsAfterCancel !== 0) {
      throw new Error(`Expected cancel to remove curl snapshots with the preview layer; remaining=${snapshotsAfterCancel}`)
    }
    assertNoConsoleErrors(errors)
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-native-drag-preview-underlay.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      boundaryTarget,
      previewState,
    }, null, 2))
    console.log(`reader harness epub-native-drag-preview-underlay passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-native-drag-single-commit') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-native-drag-single-commit mode requires --fixture <path>')
    process.exit(1)
  }
  const fixturePath = path.resolve(fixture)
  if (!fs.existsSync(fixturePath) || !fs.statSync(fixturePath).isFile()) {
    console.error(`Fixture file not found: ${fixturePath}`)
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  let page = null
  const errors = []
  try {
    page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          tapZone: 'default',
          nativeTapZones: false,
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    })
    if (await page.evaluate(() => document.body.dataset.navicShellCoverVisible === 'true')) {
      await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
      await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true')
    }

    const readState = async () => page.evaluate(() => {
      const pointTextForDocument = (doc, x, y) => {
        if (!doc?.body) return ''
        const range = typeof doc.caretRangeFromPoint === 'function'
          ? doc.caretRangeFromPoint(x, y)
          : null
        const position = !range && typeof doc.caretPositionFromPoint === 'function'
          ? doc.caretPositionFromPoint(x, y)
          : null
        const node = range?.startContainer || position?.offsetNode || doc.elementFromPoint?.(x, y)
        const offset = Number(range?.startOffset ?? position?.offset ?? 0)
        const text = node?.nodeType === Node.TEXT_NODE
          ? String(node.nodeValue || '')
          : String(node?.textContent || '')
        const midpoint = Math.max(0, Math.min(text.length, offset || Math.floor(text.length / 2)))
        return text
          .slice(Math.max(0, midpoint - 220), Math.min(text.length, midpoint + 420))
          .replace(/\s+/g, ' ')
          .trim()
      }
      const visibleTextForDocument = (doc, { viewportWidth, viewportHeight } = {}) => {
        if (!doc?.body) return ''
        const frameRect = doc.defaultView?.frameElement?.getBoundingClientRect?.()
        const frameLeft = Number(frameRect?.left) || 0
        const frameTop = Number(frameRect?.top) || 0
        const width = Number(viewportWidth || window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0)
        const height = Number(viewportHeight || window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0)
        const screenRectFor = rect => ({
          left: frameLeft + rect.left,
          top: frameTop + rect.top,
          right: frameLeft + rect.right,
          bottom: frameTop + rect.bottom,
          width: rect.width,
          height: rect.height,
        })
        const intersectionArea = rect => {
          const left = Math.max(0, rect.left)
          const right = Math.min(width, rect.right)
          const top = Math.max(0, rect.top)
          const bottom = Math.min(height, rect.bottom)
          return Math.max(0, right - left) * Math.max(0, bottom - top)
        }
        return Array.from(doc.body.querySelectorAll('body *'))
          .filter(element => {
            const rect = screenRectFor(element.getBoundingClientRect())
            const area = intersectionArea(rect)
            const elementArea = Math.max(1, rect.width * rect.height)
            return area > 48 && area / elementArea > 0.15
          })
          .map(element => element.textContent?.replace(/\s+/g, ' ').trim() || '')
          .filter(Boolean)
          .join(' ')
          .replace(/\s+/g, ' ')
          .trim()
      }
      const renderer = document.querySelector('foliate-view')?.renderer
      const pageNumberLayer = document.querySelector('[data-navic-page-number-layer="true"]')
      const viewportWidth = Number(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0)
      const viewportHeight = Number(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0)
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const visibleDocuments = []
      for (const content of renderer?.getContents?.() || []) {
        const text = visibleTextForDocument(content?.doc, { viewportWidth, viewportHeight })
        const frameRect = content?.doc?.defaultView?.frameElement?.getBoundingClientRect?.()
        const pointText = pointTextForDocument(
          content?.doc,
          Math.max(1, Math.round((viewportWidth - (Number(frameRect?.left) || 0)) * 0.5)),
          Math.max(1, Math.round((viewportHeight - (Number(frameRect?.top) || 0)) * 0.5))
        )
        if (text) {
          visibleDocuments.push({
            index: Number(content?.index),
            href: content?.section?.href || '',
            visibleText: text.slice(0, 900),
            pointText: pointText.slice(0, 900),
          })
        }
      }
      const visibleText = visibleDocuments
        .map(document => document.visibleText)
        .join(' ')
        .replace(/\s+/g, ' ')
        .trim()
      return {
        index: Number(renderer?.getContents?.()?.[0]?.index),
        page: Number(renderer?.page),
        pages: Number(renderer?.pages),
        size: Number(renderer?.size),
        start: Number(renderer?.start),
        end: Number(renderer?.end),
        viewSize: Number(renderer?.viewSize),
        location: messages.at(-1) || null,
        locationCount: messages.length,
        pageNumberLabel: String(pageNumberLayer?.textContent || '').trim(),
        visibleText: visibleText.slice(0, 1200),
        pointText: visibleDocuments
          .map(document => document.pointText)
          .filter(Boolean)
          .join(' ')
          .replace(/\s+/g, ' ')
          .trim()
          .slice(0, 1200),
        visibleDocuments,
        trace: (window.__navicReaderTrace || [])
          .filter(event => String(event?.type || '').startsWith('page-drag-preview') || String(event?.type || '').startsWith('page-turn'))
          .slice(-12),
      }
    })
    const waitForGlobalLocationAdvance = async previousState => {
      await page.waitForFunction(({ previousLocationCount, previousPageIndex, previousHref, previousRawCurrent }) => {
        const messages = (window.__navicReaderPostedMessages || [])
          .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
        const latest = messages.at(-1)
        const rawCurrent = Number(latest?.rawLocationCurrent)
        return messages.length > previousLocationCount &&
          Number.isFinite(latest?.pageIndex) &&
          (
            !Number.isFinite(previousPageIndex) ||
            latest.pageIndex !== previousPageIndex ||
            (previousHref && latest?.href && latest.href !== previousHref) ||
            (Number.isFinite(previousRawCurrent) && Number.isFinite(rawCurrent) && rawCurrent !== previousRawCurrent)
          )
      }, {
        previousLocationCount: Number(previousState?.locationCount) || 0,
        previousPageIndex: Number(previousState?.location?.pageIndex),
        previousHref: String(previousState?.location?.href || ''),
        previousRawCurrent: Number(previousState?.location?.rawLocationCurrent),
      })
    }

    const firstReady = await readState()
    const targetGlobalPageIndex = Math.max(
      1,
      Math.floor(Number(argValue('--target-global-page-index')) || 4)
    )
    let before = firstReady
    for (let attempt = 0; attempt < 80; attempt += 1) {
      const globalPageIndex = Number(before.location?.pageIndex)
      const chapterPageIndex = Number(before.location?.chapterPageIndex)
      const chapterPageCount = Number(before.location?.chapterPageCount)
      const sectionPage = Number(before.page)
      const sectionPageCount = Number(before.pages)
      if (
        Number.isFinite(globalPageIndex) &&
        globalPageIndex >= targetGlobalPageIndex &&
        Number.isFinite(chapterPageIndex) &&
        Number.isFinite(chapterPageCount) &&
        chapterPageCount > 3 &&
        chapterPageIndex > 0 &&
        chapterPageIndex < chapterPageCount - 1 &&
        Number.isFinite(sectionPage) &&
        Number.isFinite(sectionPageCount) &&
        sectionPageCount >= 3 &&
        sectionPage < sectionPageCount - 1
      ) {
        break
      }
      const previousState = before
      await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
      await waitForGlobalLocationAdvance(previousState)
      before = await readState()
    }
    await page.evaluate(async () => {
      for (let index = 0; index < 6; index += 1) {
        await new Promise(resolve => requestAnimationFrame(resolve))
      }
    })
    before = await readState()
    if (
      !Number.isFinite(before.location?.pageIndex) ||
      before.location.pageIndex < targetGlobalPageIndex ||
      !Number.isFinite(before.location?.chapterPageIndex) ||
      !Number.isFinite(before.location?.chapterPageCount) ||
      before.location.chapterPageCount <= 3 ||
      before.location.chapterPageIndex <= 0 ||
      before.location.chapterPageIndex >= before.location.chapterPageCount - 1 ||
      !Number.isFinite(before.page) ||
      !Number.isFinite(before.pages) ||
      before.pages < 3 ||
      before.page >= before.pages - 1
    ) {
      throw new Error(
        `Expected a global page ${targetGlobalPageIndex + 1}+ interior section page before drag commit probe; ` +
        `observed ${JSON.stringify(before)}`
      )
    }

    await page.evaluate(async () => {
      const width = window.visualViewport?.width || window.innerWidth || 500
      const height = window.visualViewport?.height || window.innerHeight || 800
      await window.NavicReaderBridge.dispatch({
        type: 'previewPageDrag',
        phase: 'update',
        deltaX: -Math.round(width * 0.42),
        deltaY: 0,
        viewWidth: width,
        viewHeight: height,
      })
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    })
    const afterPreview = await readState()
    const previewPageDelta = Number(afterPreview.page) - Number(before.page)
    const previewStartDelta = Number(afterPreview.start) - Number(before.start)
    const previewEndDelta = Number(afterPreview.end) - Number(before.end)
    if (
      Math.abs(previewPageDelta) > 0 ||
      Math.abs(previewStartDelta) > 1 ||
      Math.abs(previewEndDelta) > 1
    ) {
      throw new Error(
        `Expected drag preview update to leave committed Foliate page stable before native release; ` +
        `pageDelta=${previewPageDelta} startDelta=${previewStartDelta} endDelta=${previewEndDelta} ` +
        `before=${JSON.stringify(before)} afterPreview=${JSON.stringify(afterPreview)}`
      )
    }
    const readPreviewVisual = () => page.evaluate(() => {
      const pointTextForFrame = frame => {
        const frameDoc = frame?.contentDocument
        if (!frameDoc?.body) return ''
        const frameRect = typeof frame?.getBoundingClientRect === 'function'
          ? frame.getBoundingClientRect()
          : null
        const frameWidth = Number(frame?.clientWidth || frameRect?.width || 0)
        const frameHeight = Number(frame?.clientHeight || frameRect?.height || 0)
        const x = Math.max(1, Math.round(frameWidth * 0.5))
        const y = Math.max(1, Math.round(frameHeight * 0.5))
        const range = typeof frameDoc.caretRangeFromPoint === 'function'
          ? frameDoc.caretRangeFromPoint(x, y)
          : null
        const position = !range && typeof frameDoc.caretPositionFromPoint === 'function'
          ? frameDoc.caretPositionFromPoint(x, y)
          : null
        const node = range?.startContainer || position?.offsetNode || frameDoc.elementFromPoint?.(x, y)
        const offset = Number(range?.startOffset ?? position?.offset ?? 0)
        const text = node?.nodeType === Node.TEXT_NODE
          ? String(node.nodeValue || '')
          : String(node?.textContent || '')
        const midpoint = Math.max(0, Math.min(text.length, offset || Math.floor(text.length / 2)))
        return text
          .slice(Math.max(0, midpoint - 220), Math.min(text.length, midpoint + 420))
          .replace(/\s+/g, ' ')
          .trim()
      }
      const pointTextForFrameAtScroll = (frame, scrollX) => {
        const frameWin = frame?.contentWindow
        const beforeX = Number(frameWin?.scrollX || 0)
        const beforeY = Number(frameWin?.scrollY || 0)
        try {
          frameWin?.scrollTo?.(Math.max(0, Math.round(Number(scrollX) || 0)), beforeY)
          return pointTextForFrame(frame)
        } finally {
          frameWin?.scrollTo?.(beforeX, beforeY)
        }
      }
      const visibleTextForFrame = frame => {
        const frameDoc = frame?.contentDocument
        if (!frameDoc?.body) return ''
        const frameRect = typeof frame?.getBoundingClientRect === 'function'
          ? frame.getBoundingClientRect()
          : null
        const frameWidth = Number(frame?.clientWidth || frameRect?.width || 0)
        const frameHeight = Number(frame?.clientHeight || frameRect?.height || 0)
        const intersectionArea = rect => {
          const left = Math.max(0, rect.left)
          const right = Math.min(frameWidth, rect.right)
          const top = Math.max(0, rect.top)
          const bottom = Math.min(frameHeight, rect.bottom)
          return Math.max(0, right - left) * Math.max(0, bottom - top)
        }
        return Array.from(frameDoc.body.querySelectorAll('body *'))
          .filter(element => {
            const rect = element.getBoundingClientRect()
            const area = intersectionArea(rect)
            const elementArea = Math.max(1, rect.width * rect.height)
            return area > 48 && area / elementArea > 0.15
          })
          .map(element => element.textContent?.replace(/\s+/g, ' ').trim() || '')
          .filter(Boolean)
          .join(' ')
          .replace(/\s+/g, ' ')
          .trim()
      }
      const layer = document.querySelector('[data-navic-page-drag-preview-layer="true"]')
      const frame = layer?.querySelector?.('iframe[data-navic-page-drag-preview-frame="true"]')
      const frameDoc = frame?.contentDocument
      const frameWin = frame?.contentWindow
      const frontSnapshot = layer?.querySelector?.('[data-navic-page-curl-snapshot="front"]')
      const frontSnapshotDoc = frontSnapshot?.contentDocument || null
      const frontSnapshotWin = frontSnapshot?.contentWindow || null
      const paperLayer = layer?.querySelector?.('[data-navic-page-drag-preview-paper-layer="true"]')
      const borderLayer = layer?.querySelector?.('[data-navic-page-drag-preview-border-layer="true"]')
      const direction = layer?.dataset.navicPageDragPreviewDirection || ''
      const targetSlot = direction === 'previous' ? 'previous' : 'next'
      const paperTargetSlot = paperLayer?.querySelector?.(`[data-navic-surface-paper-texture-slot="${targetSlot}"]`)
      const borderTargetSlot = borderLayer?.querySelector?.(`[data-navic-surface-page-border-overlay-slot="${targetSlot}"]`)
      const style = layer ? getComputedStyle(layer) : null
      const paperTargetSlotStyle = paperTargetSlot ? getComputedStyle(paperTargetSlot) : null
      const borderTargetSlotStyle = borderTargetSlot ? getComputedStyle(borderTargetSlot) : null
      const mappedX = Number(frame?.dataset.navicPageDragPreviewFrameMappedScrollX)
      const cloneMaxX = Number(frame?.dataset.navicPageDragPreviewFrameCloneMaxX)
      const axisStep = Number(frame?.dataset.navicPageDragPreviewFrameAxisStep)
      const samplePositions = [0, mappedX - axisStep, mappedX, mappedX + axisStep, cloneMaxX]
        .filter(value => Number.isFinite(value))
        .map(value => Math.max(0, Math.round(value)))
        .filter((value, index, array) => array.indexOf(value) === index)
      return {
        layerPresent: Boolean(layer),
        framePresent: Boolean(frame),
        frameReady: frame?.dataset.navicPageDragPreviewFrameReady === 'true',
        frameTextLength: Number(frame?.dataset.navicPageDragPreviewFrameTextLength) || 0,
        frameAxisStep: Number(frame?.dataset.navicPageDragPreviewFrameAxisStep),
        frameRendererPage: Number(frame?.dataset.navicPageDragPreviewFrameRendererPage),
        frameRendererPages: Number(frame?.dataset.navicPageDragPreviewFrameRendererPages),
        frameTargetScrollX: Number(frame?.dataset.navicPageDragPreviewFrameTargetScrollX),
        frameTargetScrollY: Number(frame?.dataset.navicPageDragPreviewFrameTargetScrollY),
        frameMappedScrollX: Number(frame?.dataset.navicPageDragPreviewFrameMappedScrollX),
        frameMappedScrollY: Number(frame?.dataset.navicPageDragPreviewFrameMappedScrollY),
        frameSourceMax: Number(frame?.dataset.navicPageDragPreviewFrameSourceMax),
        frameCloneMaxX: Number(frame?.dataset.navicPageDragPreviewFrameCloneMaxX),
        frameCloneMaxY: Number(frame?.dataset.navicPageDragPreviewFrameCloneMaxY),
        frameScrollX: Number(frameWin?.scrollX ?? frameDoc?.documentElement?.scrollLeft ?? frameDoc?.body?.scrollLeft),
        frameScrollY: Number(frameWin?.scrollY ?? frameDoc?.documentElement?.scrollTop ?? frameDoc?.body?.scrollTop),
        frameScrollWidth: Number(frameDoc?.documentElement?.scrollWidth || frameDoc?.body?.scrollWidth),
        frameScrollHeight: Number(frameDoc?.documentElement?.scrollHeight || frameDoc?.body?.scrollHeight),
        frameClientWidth: Number(frameDoc?.documentElement?.clientWidth || frameDoc?.body?.clientWidth),
        frameClientHeight: Number(frameDoc?.documentElement?.clientHeight || frameDoc?.body?.clientHeight),
        frameVisibleText: visibleTextForFrame(frame).slice(0, 1200),
        framePointText: pointTextForFrame(frame).slice(0, 1200),
        framePointSamples: samplePositions.map(position => ({
          position,
          text: pointTextForFrameAtScroll(frame, position).slice(0, 360),
        })),
        mode: layer?.dataset.navicPageDragPreviewMode || '',
        fallback: layer?.dataset.navicPageDragPreviewFallback || '',
        ready: layer?.dataset.navicPageDragPreviewReady === 'true',
        curl: layer?.dataset.navicPageDragPreviewCurl === 'true',
        curlDirection: layer?.dataset.navicPageDragPreviewCurlDirection || '',
        curlProgress: Number(layer?.dataset.navicPageDragPreviewCurlProgress),
        curlSheetMode: layer?.dataset.navicPageCurlSheetMode || '',
        frontSnapshotPresent: Boolean(frontSnapshot),
        frontSnapshotReady: frontSnapshot?.dataset.navicPageCurlSnapshotReady === 'true',
        frontSnapshotTextLength: Number(frontSnapshot?.dataset.navicPageCurlSnapshotTextLength) || 0,
        frontSnapshotScrollX: Number(frontSnapshotWin?.scrollX ?? frontSnapshotDoc?.documentElement?.scrollLeft ?? frontSnapshotDoc?.body?.scrollLeft),
        frontSnapshotScrollY: Number(frontSnapshotWin?.scrollY ?? frontSnapshotDoc?.documentElement?.scrollTop ?? frontSnapshotDoc?.body?.scrollTop),
        frontSnapshotMappedScrollX: Number(frontSnapshot?.dataset.navicPageCurlSnapshotMappedScrollX),
        frontSnapshotMappedScrollY: Number(frontSnapshot?.dataset.navicPageCurlSnapshotMappedScrollY),
        frontSnapshotSourceScrollX: Number(frontSnapshot?.dataset.navicPageCurlSnapshotScrollX),
        frontSnapshotSourceScrollY: Number(frontSnapshot?.dataset.navicPageCurlSnapshotScrollY),
        paperLayerPresent: Boolean(paperLayer),
        borderLayerPresent: Boolean(borderLayer),
        targetTextureSlot: targetSlot,
        paperTargetSlotTransform: paperTargetSlotStyle?.transform || '',
        borderTargetSlotTransform: borderTargetSlotStyle?.transform || '',
        textureSurface: layer?.dataset.navicPageDragPreviewTextureSurface || '',
        opacity: style?.opacity || '',
        width: style?.width || '',
      }
    })
    let previewVisual = await readPreviewVisual()
    if (previewVisual.layerPresent && previewVisual.mode === 'interior' && (!previewVisual.frameReady || !previewVisual.ready)) {
      await page.waitForFunction(() => {
        const layer = document.querySelector('[data-navic-page-drag-preview-layer="true"]')
        const frame = layer?.querySelector?.('iframe[data-navic-page-drag-preview-frame="true"]')
        return layer?.dataset.navicPageDragPreviewMode === 'interior' &&
          frame?.dataset.navicPageDragPreviewFrameReady === 'true' &&
          Number(frame?.dataset.navicPageDragPreviewFrameTextLength) > 0
      })
      await page.evaluate(async () => {
        const width = window.visualViewport?.width || window.innerWidth || 500
        const height = window.visualViewport?.height || window.innerHeight || 800
        await window.NavicReaderBridge.dispatch({
          type: 'previewPageDrag',
          phase: 'update',
          deltaX: -Math.round(width * 0.42),
          deltaY: 0,
          viewWidth: width,
          viewHeight: height,
        })
        await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      })
      previewVisual = await readPreviewVisual()
    }
    if (!previewVisual.layerPresent || previewVisual.mode !== 'interior') {
      throw new Error(
        `Expected interior native drag preview to mount a non-committing visual layer; ` +
        `observed ${JSON.stringify(previewVisual)}`
      )
    }
    if (!previewVisual.curl || previewVisual.curlDirection !== 'next') {
      throw new Error(
        `Expected interior native drag preview to expose curl state; ` +
        `observed ${JSON.stringify(previewVisual)}`
      )
    }
    if (!Number.isFinite(previewVisual.curlProgress) || previewVisual.curlProgress <= 0 || previewVisual.curlProgress >= 1) {
      throw new Error(
        `Expected interior native drag preview curl progress to reflect the current drag fraction; ` +
        `observed ${JSON.stringify(previewVisual)}`
      )
    }
    if (!previewVisual.frontSnapshotPresent || !previewVisual.frontSnapshotReady || previewVisual.frontSnapshotTextLength <= 0) {
      throw new Error(
        `Expected interior native drag preview to snapshot the current readable page; ` +
        `observed ${JSON.stringify(previewVisual)}`
      )
    }
    if (
      Number.isFinite(before.start) &&
      before.start > 1 &&
      (
        !Number.isFinite(previewVisual.frontSnapshotSourceScrollX) ||
        previewVisual.frontSnapshotSourceScrollX <= 1 ||
        !Number.isFinite(previewVisual.frontSnapshotMappedScrollX) ||
        previewVisual.frontSnapshotMappedScrollX <= 1
      )
    ) {
      throw new Error(
        `Expected curl front snapshot to use the current Foliate renderer page, not the chapter start; ` +
        `rendererStart=${before.start} sourceScroll=${previewVisual.frontSnapshotSourceScrollX} ` +
        `mappedScroll=${previewVisual.frontSnapshotMappedScrollX} observed=${JSON.stringify(previewVisual)}`
      )
    }
    if (!previewVisual.framePresent || !previewVisual.frameReady || previewVisual.frameTextLength <= 0) {
      throw new Error(
        `Expected interior native drag preview to render the adjacent in-section underlay, not a black void; ` +
        `observed ${JSON.stringify(previewVisual)}`
      )
    }
    if (!previewVisual.paperLayerPresent || !previewVisual.borderLayerPresent || !previewVisual.textureSurface.includes('paper')) {
      throw new Error(
        `Expected interior native drag preview to carry paper and border texture layers; ` +
        `observed ${JSON.stringify(previewVisual)}`
      )
    }
    const matrixX = transform => {
      const match = String(transform || '').match(/matrix\([^,]+,\s*[^,]+,\s*[^,]+,\s*[^,]+,\s*(-?\d+(?:\.\d+)?)/)
      return match ? Number(match[1]) : Number.NaN
    }
    const paperTargetX = matrixX(previewVisual.paperTargetSlotTransform)
    const borderTargetX = matrixX(previewVisual.borderTargetSlotTransform)
    if (!Number.isFinite(paperTargetX) || paperTargetX >= 0) {
      throw new Error(
        `Expected next-page preview paper texture slot to enter the clipped drag surface; ` +
        `target=${previewVisual.targetTextureSlot} x=${paperTargetX} ` +
        `transform=${previewVisual.paperTargetSlotTransform || 'missing'} observed=${JSON.stringify(previewVisual)}`
      )
    }
    if (!Number.isFinite(borderTargetX) || borderTargetX >= 0) {
      throw new Error(
        `Expected next-page preview border texture slot to enter the clipped drag surface; ` +
        `target=${previewVisual.targetTextureSlot} x=${borderTargetX} ` +
        `transform=${previewVisual.borderTargetSlotTransform || 'missing'} observed=${JSON.stringify(previewVisual)}`
      )
    }
    if (
      !Number.isFinite(previewVisual.frameAxisStep) ||
      Math.abs(previewVisual.frameAxisStep - Math.round(before.size)) > 1
    ) {
      throw new Error(
        `Expected interior native drag preview to use Foliate renderer page stride; ` +
        `axisStep=${previewVisual.frameAxisStep} rendererSize=${before.size} ` +
        `viewportWidth=${previewVisual.width} before=${JSON.stringify(before)} preview=${JSON.stringify(previewVisual)}`
      )
    }
    if (Number.isFinite(before.viewSize) && Number.isFinite(before.pages) && before.pages > 0) {
      const derivedStride = Math.round(before.viewSize / before.pages)
      if (Math.abs(previewVisual.frameAxisStep - derivedStride) > 1) {
        throw new Error(
          `Expected interior native drag preview stride to match viewSize/pages; ` +
          `axisStep=${previewVisual.frameAxisStep} derivedStride=${derivedStride} ` +
          `before=${JSON.stringify(before)} preview=${JSON.stringify(previewVisual)}`
        )
      }
    }
    const beforeGlobalPageIndex = Number(before.location?.pageIndex)
    const beforeLocationCount = Number(before.locationCount) || 0
    await page.evaluate(async () => {
      const width = window.visualViewport?.width || window.innerWidth || 500
      const height = window.visualViewport?.height || window.innerHeight || 800
      const deltaX = -Math.round(width * 0.42)
      await window.NavicReaderBridge.dispatch({
        type: 'previewPageDrag',
        phase: 'release',
        deltaX,
        deltaY: 0,
        viewWidth: width,
        viewHeight: height,
      })
      await new Promise(resolve => requestAnimationFrame(resolve))
      await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      for (let index = 0; index < 6; index += 1) {
        await new Promise(resolve => requestAnimationFrame(resolve))
      }
    })
    await page.waitForFunction(({ beforeLocationCount, beforeGlobalPageIndex }) => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const latest = messages.at(-1)
      return messages.length > beforeLocationCount &&
        Number.isFinite(latest?.pageIndex) &&
        latest.pageIndex !== beforeGlobalPageIndex
    }, { beforeLocationCount, beforeGlobalPageIndex })
    const afterCommit = await readState()
    const expectedCommitStart = Number(previewVisual.frameTargetScrollX)
    const actualCommitStart = Number(afterCommit.start)
    if (
      Number.isFinite(expectedCommitStart) &&
      Number.isFinite(actualCommitStart) &&
      Math.abs(actualCommitStart - expectedCommitStart) > 2
    ) {
      throw new Error(
        `Expected native drag release to commit the previewed renderer offset; ` +
        `expectedStart=${expectedCommitStart} actualStart=${actualCommitStart} ` +
        `stride=${previewVisual.frameAxisStep} ` +
        `before=${JSON.stringify(before)} preview=${JSON.stringify(previewVisual)} afterCommit=${JSON.stringify(afterCommit)}`
      )
    }
    const commitGlobalPageDelta = Number(afterCommit.location?.pageIndex) - beforeGlobalPageIndex
    if (commitGlobalPageDelta !== 1) {
      throw new Error(
        `Expected native drag release plus page action to commit exactly one global page; ` +
        `observed globalPageDelta=${commitGlobalPageDelta} before=${JSON.stringify(before)} ` +
        `afterCommit=${JSON.stringify(afterCommit)}`
      )
    }
    const expectedLabelPrefix = `${beforeGlobalPageIndex + 2} /`
    if (!afterCommit.pageNumberLabel.startsWith(expectedLabelPrefix)) {
      throw new Error(
        `Expected page-number label to match committed one-page turn prefix "${expectedLabelPrefix}"; ` +
        `observed label="${afterCommit.pageNumberLabel}" before=${JSON.stringify(before)} ` +
        `afterCommit=${JSON.stringify(afterCommit)}`
      )
    }

    await page.evaluate(async () => {
      await window.NavicReaderBridge.dispatch({
        type: 'previewPageDrag',
        phase: 'cancel',
        deltaX: 0,
        deltaY: 0,
      })
    })
    assertNoConsoleErrors(errors)
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-native-drag-single-commit.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      before,
      afterPreview,
      previewVisual,
      afterCommit,
    }, null, 2))
    console.log(`reader harness epub-native-drag-single-commit passed: ${outputPath}`)
  } catch (error) {
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-native-drag-single-commit.failure.json')
    let state = null
    try {
      state = page
        ? await page.evaluate(() => ({
          bodyDataset: { ...document.body.dataset },
          rootDataset: { ...document.documentElement.dataset },
          postedMessages: (window.__navicReaderPostedMessages || []).slice(-12),
          trace: (window.__navicReaderTrace || []).slice(-60),
          renderer: (() => {
            const renderer = document.querySelector('foliate-view')?.renderer
            return {
              index: Number(renderer?.getContents?.()?.[0]?.index),
              page: Number(renderer?.page),
              pages: Number(renderer?.pages),
              start: Number(renderer?.start),
              end: Number(renderer?.end),
              viewSize: Number(renderer?.viewSize),
            }
          })(),
        }))
        : null
    } catch (diagnosticError) {
      state = { diagnosticError: diagnosticError?.message || String(diagnosticError) }
    }
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      error: error?.stack || error?.message || String(error),
      errors,
      state,
    }, null, 2))
    console.error(error?.message || String(error))
    console.error(`reader harness epub-native-drag-single-commit failure diagnostics: ${outputPath}`)
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-full-traversal') {
  const fixture = argValue('--fixture')
  if (!fixture) {
    console.error('epub-full-traversal mode requires --fixture <path>')
    process.exit(1)
  }
  const fixturePath = path.resolve(fixture)
  if (!fs.existsSync(fixturePath) || !fs.statSync(fixturePath).isFile()) {
    console.error(`Fixture file not found: ${fixturePath}`)
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage: message => {
          let parsed = message
          try {
            parsed = JSON.parse(message)
          } catch {
            parsed = { type: 'unparseable', message }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 15000 })
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })

    const collectLocationSnapshot = async () => page.evaluate(() => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const location = messages.at(-1)
      const view = document.querySelector('foliate-view')
      const contents = view?.renderer?.getContents?.() || []
      const coverImageHits = []
      for (const content of contents) {
        const doc = content?.doc
        if (!doc?.body) continue
        for (const image of Array.from(doc.images || [])) {
          const src = image.getAttribute('src') || image.currentSrc || image.src || ''
          if (/cover|frontcover|coverpage|cubierta|portada/i.test(src)) {
            coverImageHits.push({ pageIndex: location?.pageIndex, href: location?.href, src })
          }
        }
      }
      return {
        location,
        shellCoverVisible: document.body.dataset.navicShellCoverVisible === 'true',
        documents: [],
        coverImageHits,
        coverLikePages: [],
      }
    })

    const collectCoverScanSnapshot = async () => page.evaluate(() => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const location = messages.at(-1)
      const view = document.querySelector('foliate-view')
      const contents = view?.renderer?.getContents?.() || []
      const documents = []
      const coverImageHits = []
      const coverLikePages = []
      for (const content of contents) {
        const doc = content?.doc
        if (!doc?.body) continue
        const frameRect = doc.defaultView?.frameElement?.getBoundingClientRect?.()
        const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0
        const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0
        const text = doc.body.textContent?.replace(/\s+/g, ' ').trim() || ''
        const screenRectFor = rect => ({
          left: (frameRect?.left || 0) + rect.left,
          top: (frameRect?.top || 0) + rect.top,
          right: (frameRect?.left || 0) + rect.right,
          bottom: (frameRect?.top || 0) + rect.bottom,
          width: rect.width,
          height: rect.height,
        })
        const intersectionArea = rect => {
          const left = Math.max(0, rect.left)
          const right = Math.min(viewportWidth, rect.right)
          const top = Math.max(0, rect.top)
          const bottom = Math.min(viewportHeight, rect.bottom)
          return Math.max(0, right - left) * Math.max(0, bottom - top)
        }
        const images = Array.from(doc.images || []).map(image => {
          const width = Number(image.getAttribute('width') || image.naturalWidth)
          const height = Number(image.getAttribute('height') || image.naturalHeight)
          const src = image.getAttribute('src') || image.currentSrc || image.src || ''
          const rect = image.getBoundingClientRect()
          const screenRect = screenRectFor(rect)
          return {
            src,
            width,
            height,
            aspect: width > 0 ? height / width : 0,
            screenArea: intersectionArea(screenRect),
            screenRect,
          }
        })
        const visibleText = Array.from(doc.body.querySelectorAll('body *'))
          .filter(element => {
            const rect = screenRectFor(element.getBoundingClientRect())
            const area = intersectionArea(rect)
            const elementArea = Math.max(1, rect.width * rect.height)
            return area > 48 && area / elementArea > 0.15
          })
          .map(element => element.textContent?.replace(/\s+/g, ' ').trim() || '')
          .filter(Boolean)
          .join(' ')
          .replace(/\s+/g, ' ')
          .trim()
        for (const image of images) {
          if (/cover|frontcover|coverpage|cubierta|portada/i.test(image.src)) {
            coverImageHits.push({ pageIndex: location?.pageIndex, href: location?.href, src: image.src })
          }
        }
        const dominantVisibleImage = images
          .filter(image => image.screenArea > 0)
          .sort((left, right) => right.screenArea - left.screenArea)[0]
        const viewportArea = Math.max(1, viewportWidth * viewportHeight)
        const firstVisiblePage = Number(location?.pageIndex) === 0
        const coverLike =
          firstVisiblePage &&
          visibleText.length <= 40 &&
          dominantVisibleImage &&
          dominantVisibleImage.screenArea / viewportArea >= 0.45 &&
          (
            /cover|frontcover|coverpage|cubierta|portada/i.test(dominantVisibleImage.src) ||
            (Number.isFinite(dominantVisibleImage.aspect) && dominantVisibleImage.aspect >= 1.15 && dominantVisibleImage.aspect <= 1.85)
          )
        if (coverLike) {
          coverLikePages.push({
            pageIndex: location?.pageIndex,
            href: location?.href,
            visibleText,
            dominantVisibleImage,
            images,
          })
        }
        documents.push({
          index: content.index,
          textLength: text.length,
          textSample: text.slice(0, 220),
          images,
        })
      }
      return {
        location,
        shellCoverVisible: document.body.dataset.navicShellCoverVisible === 'true',
        documents,
        coverImageHits,
        coverLikePages,
      }
    })

    const pages = []
    const coverImageHits = []
    const coverLikePages = []
    let snapshot = await collectCoverScanSnapshot()
    if (snapshot.coverLikePages.length > 0) {
      throw new Error(`Expected first WebView-visible page not to be the EPUB cover; observed ${JSON.stringify(snapshot.coverLikePages[0])}`)
    }
    const maxTurns = Math.max(1, Number(snapshot.location?.pageCount || 0) + 12)
    for (let turn = 0; turn < maxTurns; turn += 1) {
      if (!snapshot.location) throw new Error('Missing location during full EPUB traversal')
      pages.push({
        location: snapshot.location,
        shellCoverVisible: snapshot.shellCoverVisible,
        documents: snapshot.documents,
      })
      coverImageHits.push(...snapshot.coverImageHits)
      coverLikePages.push(...snapshot.coverLikePages)
      if (snapshot.location.pageIndex % 50 === 0) {
        console.log(`epub-full-traversal progress: ${snapshot.location.pageIndex + 1}/${snapshot.location.pageCount}`)
      }
      if (snapshot.location.pageIndex >= snapshot.location.pageCount - 1) break
      const previousPageIndex = snapshot.location.pageIndex
      await page.evaluate(async () => {
        const renderer = document.querySelector('foliate-view')?.renderer
        renderer?.removeAttribute?.('animated')
        await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      await page.waitForFunction(previous => {
        const messages = (window.__navicReaderPostedMessages || [])
          .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
        const current = messages.at(-1)
        return current && current.pageIndex > previous
      }, previousPageIndex, { timeout: 8000 })
      snapshot = await collectLocationSnapshot()
    }
    const trace = await page.evaluate(() => window.__navicReaderTrace || [])
    const paginationProfileEvents = trace
      .filter(event => typeof event?.type === 'string' && event.type.startsWith('pagination-profile:'))
      .map(event => ({
        type: event.type,
        payload: event.payload || {},
      }))
    const result = {
      pages,
      coverImageHits,
      coverLikePages,
      paginationProfileEvents,
      traceSummary: {
        rawRelocations: trace.filter(event => event?.type === 'relocate:raw').length,
        committedLocations: trace.filter(event => event?.type === 'location:post').length,
        suppressedCoverDocuments: trace.filter(event => event?.type === 'cover:document-suppressed').length,
      },
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-full-traversal.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertFullEpubTraversal(result)
    console.log(`reader harness epub-full-traversal passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-texture-scroll') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-texture-scroll mode requires --fixture <path-to-epub>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          try {
            window.__navicReaderPostedMessages.push(JSON.parse(value))
          } catch {
            window.__navicReaderPostedMessages.push({ raw: value })
          }
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacing: 0.35,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForTimeout(500)
    const result = await page.evaluate(async () => {
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      const staticLayer = document.querySelector('[data-navic-surface-paper-texture-layer="true"]')
      const layer = document.querySelector('[data-navic-moving-page-paper-texture-layer="true"]')
      if (!renderer) throw new Error('Missing foliate renderer')
      if (!staticLayer) throw new Error('Missing static surface paper backing layer')
      if (!layer) throw new Error('Missing moving page paper texture layer')
      const textureSlot = () => layer.querySelector?.('[data-navic-surface-paper-texture-slot="current"]')
      const beforePosition = Number(renderer.containerPosition)
      const beforeSlot = textureSlot()
      const beforeSlotStyle = beforeSlot ? getComputedStyle(beforeSlot) : null
      const beforeTextureSlotTransform = beforeSlot?.style?.transform || ''
      const beforeComputedTextureSlotTransform = beforeSlotStyle?.transform || ''
      const delta = Math.min(120, Math.max(48, Math.round(window.innerWidth * 0.25)))
      renderer.containerPosition = beforePosition + delta
      renderer.dispatchEvent(new Event('scroll'))
      await new Promise(resolve => requestAnimationFrame(resolve))
      const afterSlot = textureSlot()
      const afterSlotStyle = afterSlot ? getComputedStyle(afterSlot) : null
      const trace = window.__navicReaderTrace || []
      const recentTextureTrace = trace
        .filter(entry => String(entry?.type || '').startsWith('texture:'))
        .slice(-8)
      return {
        beforePosition,
        afterPosition: Number(renderer.containerPosition),
        delta,
        flowMode: document.body.dataset.navicReaderFlowMode || '',
        beforeTextureSlotTransform,
        beforeComputedTextureSlotTransform,
        afterTextureSlotTransform: afterSlot?.style?.transform || '',
        afterComputedTextureSlotTransform: afterSlotStyle?.transform || '',
        staticTextureBackgroundPosition: staticLayer.style.backgroundPosition,
        computedStaticTextureBackgroundPosition: getComputedStyle(staticLayer).backgroundPosition,
        textureBackgroundPosition: layer.style.backgroundPosition,
        computedTextureBackgroundPosition: getComputedStyle(layer).backgroundPosition,
        recentTextureTrace,
      }
    })
    assertNoConsoleErrors(errors)
    try {
      assertSurfaceTextureTracksForwardContentMovement(result)
    } catch (error) {
      throw new Error(`${error?.message || String(error)}; result=${JSON.stringify(result)}`)
    }
    console.log(`reader harness epub-texture-scroll passed: ${JSON.stringify(result)}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-texture-page-turns') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-texture-page-turns mode requires --fixture <path-to-epub>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 15000 })
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })

    const collectState = async label => page.evaluate(sampleLabel => {
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      const staticLayer = document.querySelector('[data-navic-surface-paper-texture-layer="true"]')
      const layer = document.querySelector('[data-navic-moving-page-paper-texture-layer="true"]')
      const borderLayer = document.querySelector('[data-navic-moving-page-border-overlay-layer="true"]')
      const textureSlot = layer?.querySelector?.('[data-navic-surface-paper-texture-slot="current"]')
      const textureSlotStyle = textureSlot ? getComputedStyle(textureSlot) : null
      const borderSlot = borderLayer?.querySelector?.('[data-navic-surface-page-border-overlay-slot="current"]')
      const borderSlotStyle = borderSlot ? getComputedStyle(borderSlot) : null
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const location = messages.at(-1) || null
      return {
        label: sampleLabel,
        timestamp: performance.now(),
        viewportWidth: Number(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0),
        viewportHeight: Number(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0),
        flowMode: document.body.dataset.navicReaderFlowMode || '',
        location,
        href: location?.href || '',
        pageIndex: location?.pageIndex,
        pageCount: location?.pageCount,
        position: Number(renderer?.containerPosition),
        rendererPage: Number(renderer?.page),
        rendererPages: Number(renderer?.pages),
        textureKey: document.body.dataset.navicSurfacePaperTextureKey || '',
        textureSlotTransform: textureSlot?.style?.transform || '',
        computedTextureSlotTransform: textureSlotStyle?.transform || '',
        borderSlotTransform: borderSlot?.style?.transform || '',
        computedBorderSlotTransform: borderSlotStyle?.transform || '',
        staticTextureLayerPresent: Boolean(staticLayer),
        staticTextureBackgroundPosition: staticLayer?.style.backgroundPosition || '',
        computedStaticTextureBackgroundPosition: staticLayer ? getComputedStyle(staticLayer).backgroundPosition : '',
        textureBackgroundPosition: layer?.style.backgroundPosition || '',
        computedTextureBackgroundPosition: layer ? getComputedStyle(layer).backgroundPosition : '',
        borderBackgroundPosition: borderLayer?.style.backgroundPosition || '',
        computedBorderBackgroundPosition: borderLayer ? getComputedStyle(borderLayer).backgroundPosition : '',
      }
    }, label)

    const locationIdentity = location => ({
      href: location?.href || '',
      pageIndex: Number.isFinite(location?.pageIndex) ? location.pageIndex : null,
      cfi: location?.cfi || location?.rangeCfi || '',
    })

    const waitForReaderLocationChange = async (beforeLocation, label) => {
      const before = locationIdentity(beforeLocation)
      try {
        await page.waitForFunction(previous => {
          const messages = (window.__navicReaderPostedMessages || [])
            .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
          const current = messages.at(-1)
          return current && (
            (current.href || '') !== previous.href ||
            current.pageIndex !== previous.pageIndex ||
            (current.cfi || current.rangeCfi || '') !== previous.cfi
          )
        }, before, { timeout: 8000 })
      } catch (error) {
        const latest = await collectState(`wait-failed-${label}`)
        const recentLocations = await page.evaluate(() => (window.__navicReaderPostedMessages || [])
          .filter(message => message?.type === 'locationChanged')
          .slice(-5)
          .map(message => ({
            href: message?.href || '',
            pageIndex: message?.pageIndex,
            pageCount: message?.pageCount,
            cfi: message?.cfi || message?.rangeCfi || '',
          })))
        throw new Error(
          `Timed out waiting for reader location change at ${label}; ` +
          `before=${JSON.stringify(before)} latest=${JSON.stringify(locationIdentity(latest.location))} ` +
          `recent=${JSON.stringify(recentLocations)} cause=${error?.message || String(error)}`
        )
      }
    }

    const dragProbe = await page.evaluate(async delta => {
      const sample = label => {
        const view = document.querySelector('foliate-view')
        const renderer = view?.renderer
        const layer = document.querySelector('[data-navic-moving-page-paper-texture-layer="true"]')
        const textureSlot = layer?.querySelector?.('[data-navic-surface-paper-texture-slot="current"]')
        const textureSlotStyle = textureSlot ? getComputedStyle(textureSlot) : null
        const messages = (window.__navicReaderPostedMessages || [])
          .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
        const location = messages.at(-1) || null
        return {
          label,
          timestamp: performance.now(),
          viewportWidth: Number(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0),
          viewportHeight: Number(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0),
          flowMode: document.body.dataset.navicReaderFlowMode || '',
          location,
          href: location?.href || '',
          pageIndex: location?.pageIndex,
          pageCount: location?.pageCount,
          position: Number(renderer?.containerPosition),
          textureKey: document.body.dataset.navicSurfacePaperTextureKey || '',
          textureSlotTransform: textureSlot?.style?.transform || '',
          computedTextureSlotTransform: textureSlotStyle?.transform || '',
          textureBackgroundPosition: layer?.style.backgroundPosition || '',
          computedTextureBackgroundPosition: layer ? getComputedStyle(layer).backgroundPosition : '',
        }
      }
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      if (!renderer) throw new Error('Missing foliate renderer for texture drag probe')
      const samples = [sample('before')]
      renderer.scrollBy(delta, 0)
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      samples.push(sample('after-scrollBy'))
      renderer.scrollBy(-delta, 0)
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      return { name: 'renderer-scrollBy-forward', direction: 'forward', samples, delta }
    }, Math.min(160, Math.max(80, Math.round(readerHarnessViewport.viewport.width * 0.32))))

    const bridgeProbe = async name => {
      const samples = [await collectState('before')]
      await page.evaluate(() => {
        const renderer = document.querySelector('foliate-view')?.renderer
        renderer?.setAttribute?.('animated', '')
        window.__navicTexturePageTurnPromise = window.NavicReaderBridge.dispatch({ type: 'nextPage' })
        return true
      })
      for (const delay of [40, 80, 120, 180, 260]) {
        await page.waitForTimeout(delay)
        samples.push(await collectState(`t+${delay}`))
      }
      await page.evaluate(async () => {
        await window.__navicTexturePageTurnPromise
      })
      await page.waitForTimeout(260)
      samples.push(await collectState('settled'))
      return { name, direction: 'forward', samples }
    }

    const bridgeNextProbe = await bridgeProbe('bridge-next-animated')

    const boundarySamples = []
    let bridgeBoundaryProbe = null
    let previous = await collectState('boundary-search-start')
    for (let index = 0; index < 180; index += 1) {
      await page.evaluate(async () => {
        document.querySelector('foliate-view')?.renderer?.removeAttribute?.('animated')
        await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      await waitForReaderLocationChange(previous.location, `boundary-search-${index}`)
      const current = await collectState(`boundary-search-${index}`)
      if (current.href && previous.href && current.href !== previous.href) {
        boundarySamples.push(previous, current)
        await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'previousPage' }))
        await waitForReaderLocationChange(current.location, 'boundary-return')
        boundarySamples.push(await collectState('boundary-returned-before'))
        bridgeBoundaryProbe = await bridgeProbe('bridge-next-boundary-animated')
        break
      }
      previous = current
    }

    const result = {
      probes: [dragProbe, bridgeNextProbe, bridgeBoundaryProbe].filter(Boolean),
      boundarySamples,
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-texture-page-turns.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertTextureKeysIgnorePageCountOnlyRelabels(result.trace)
    assertTextureTracksRealPageTurnSamples(result)
    console.log(`reader harness epub-texture-page-turns passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-texture-frontmatter-transition') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-texture-frontmatter-transition mode requires --fixture <path-to-epub>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  let page = null
  try {
    page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 15000 })
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })
    const collectState = async label => page.evaluate(sampleLabel => {
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      const staticLayer = document.querySelector('[data-navic-surface-paper-texture-layer="true"]')
      const layer = document.querySelector('[data-navic-moving-page-paper-texture-layer="true"]')
      const textureSlot = layer?.querySelector?.('[data-navic-surface-paper-texture-slot="current"]')
      const textureSlotStyle = textureSlot ? getComputedStyle(textureSlot) : null
      const textureSlotTransforms = Array.from(layer?.querySelectorAll?.('[data-navic-surface-paper-texture-slot]') || [])
        .map(slot => {
          const style = getComputedStyle(slot)
          return {
            slot: slot.getAttribute('data-navic-surface-paper-texture-slot') || '',
            transform: slot.style.transform || '',
            computedTransform: style.transform || '',
          }
        })
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const location = messages.at(-1) || null
      const viewportWidth = Number(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 0)
      const viewportHeight = Number(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 0)
      const visibleDocuments = []
      const contents = view?.renderer?.getContents?.() || []
      for (const content of contents) {
        const doc = content?.doc
        if (!doc?.body) continue
        const frameRect = doc.defaultView?.frameElement?.getBoundingClientRect?.()
        const screenRectFor = rect => ({
          left: (frameRect?.left || 0) + rect.left,
          top: (frameRect?.top || 0) + rect.top,
          right: (frameRect?.left || 0) + rect.right,
          bottom: (frameRect?.top || 0) + rect.bottom,
          width: rect.width,
          height: rect.height,
        })
        const intersectionArea = rect => {
          const left = Math.max(0, rect.left)
          const right = Math.min(viewportWidth, rect.right)
          const top = Math.max(0, rect.top)
          const bottom = Math.min(viewportHeight, rect.bottom)
          return Math.max(0, right - left) * Math.max(0, bottom - top)
        }
        const visibleText = Array.from(doc.body.querySelectorAll('body *'))
          .filter(element => {
            const rect = screenRectFor(element.getBoundingClientRect())
            const area = intersectionArea(rect)
            const elementArea = Math.max(1, rect.width * rect.height)
            return area > 48 && area / elementArea > 0.15
          })
          .map(element => element.textContent?.replace(/\s+/g, ' ').trim() || '')
          .filter(Boolean)
          .join(' ')
          .replace(/\s+/g, ' ')
          .trim()
        if (visibleText) {
          visibleDocuments.push({
            index: content.index,
            href: content.section?.href || '',
            visibleText: visibleText.slice(0, 600),
          })
        }
      }
      const visibleText = visibleDocuments
        .map(document => document.visibleText)
        .join(' ')
        .replace(/\s+/g, ' ')
        .trim()
      return {
        label: sampleLabel,
        timestamp: performance.now(),
        viewportWidth,
        viewportHeight,
        flowMode: document.body.dataset.navicReaderFlowMode || '',
        location,
        href: location?.href || '',
        pageIndex: location?.pageIndex,
        pageCount: location?.pageCount,
        position: Number(renderer?.containerPosition),
        rendererPage: Number(renderer?.page),
        rendererPages: Number(renderer?.pages),
        textureKey: document.body.dataset.navicSurfacePaperTextureKey || '',
        textureSlotTransform: textureSlot?.style?.transform || '',
        computedTextureSlotTransform: textureSlotStyle?.transform || '',
        textureSlotTransforms,
        staticTextureLayerPresent: Boolean(staticLayer),
        staticTextureBackgroundPosition: staticLayer?.style.backgroundPosition || '',
        computedStaticTextureBackgroundPosition: staticLayer ? getComputedStyle(staticLayer).backgroundPosition : '',
        textureBackgroundPosition: layer?.style.backgroundPosition || '',
        computedTextureBackgroundPosition: layer ? getComputedStyle(layer).backgroundPosition : '',
        visibleText: visibleText.slice(0, 1000),
        visibleDocuments,
      }
    }, label)

    const firstState = await collectState('transition-scan-start')
    const findTextureTransitionBoundary = async () => {
      let previous = firstState
      for (let turn = 0; turn < 80; turn += 1) {
        await page.evaluate(async () => {
          document.querySelector('foliate-view')?.renderer?.removeAttribute?.('animated')
          await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
        })
        await page.waitForTimeout(360)
        const current = await collectState(`transition-scan-${turn + 1}`)
        const previousPage = Number(previous?.pageIndex)
        const currentPage = Number(current?.pageIndex)
        const previousHref = String(previous?.href || previous?.visibleDocuments?.[0]?.href || '')
        const currentHref = String(current?.href || current?.visibleDocuments?.[0]?.href || '')
        const previousVisibleIndex = Number(previous?.visibleDocuments?.find(document => document?.visibleText)?.index)
        const currentVisibleIndex = Number(current?.visibleDocuments?.find(document => document?.visibleText)?.index)
        const changedSection =
          (previousHref && currentHref && previousHref !== currentHref) ||
          (Number.isFinite(previousVisibleIndex) &&
            Number.isFinite(currentVisibleIndex) &&
            previousVisibleIndex !== currentVisibleIndex)
        const previousHasVisibleText = String(previous?.visibleText || '').replace(/\s+/g, ' ').trim().length > 80
        const currentHasVisibleText = String(current?.visibleText || '').replace(/\s+/g, ' ').trim().length > 80
        if (
          Number.isFinite(previousPage) &&
          Number.isFinite(currentPage) &&
          previousPage > 1 &&
          currentPage > previousPage &&
          changedSection &&
          previousHasVisibleText &&
          currentHasVisibleText
        ) {
          return {
            turns: turn + 1,
            before: previous,
            entry: current,
          }
        }
        previous = current
      }
      throw new Error(`Expected texture transition probe to discover a visible section boundary; last=${JSON.stringify(previous)}`)
    }

    const transitionBoundary = await findTextureTransitionBoundary()
    const transitionEntryState = await collectState('transition-boundary-entry')
    if (!Number.isFinite(Number(transitionEntryState?.pageIndex))) {
      throw new Error(`Expected discovered texture transition to produce a page location; observed ${JSON.stringify(transitionEntryState)}`)
    }
    await page.evaluate(async () => {
      document.querySelector('foliate-view')?.renderer?.removeAttribute?.('animated')
      await window.NavicReaderBridge.dispatch({ type: 'previousPage' })
    })
    await page.waitForTimeout(420)
    const beforeTransitionState = await collectState('transition-boundary-before')
    if (Number(beforeTransitionState?.pageIndex) >= Number(transitionEntryState?.pageIndex)) {
      throw new Error(
        `Expected previousPage to return before the discovered texture boundary; ` +
        `before=${beforeTransitionState?.pageIndex}/${beforeTransitionState?.pageCount} ` +
        `entry=${transitionEntryState?.pageIndex}/${transitionEntryState?.pageCount}`
      )
    }

    const bridgeProbe = async name => {
      const samples = [await collectState('before')]
      await page.evaluate(() => {
        const renderer = document.querySelector('foliate-view')?.renderer
        renderer?.setAttribute?.('animated', '')
        window.__navicTextureFrontmatterTurnPromise = window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      })
      for (const delay of [40, 80, 120, 180, 260]) {
        await page.waitForTimeout(delay)
        samples.push(await collectState(`t+${delay}`))
      }
      await page.evaluate(async () => {
        await window.__navicTextureFrontmatterTurnPromise
      })
      await page.waitForTimeout(260)
      samples.push(await collectState('settled'))
      return { name, direction: 'forward', samples }
    }

    const dragProbe = async (name, direction = 'forward') => {
      const traceStart = await page.evaluate(() => (window.__navicReaderTrace || []).length)
      const samples = [await collectState('before')]
      await page.evaluate(() => {
        const renderer = document.querySelector('foliate-view')?.renderer
        renderer?.setAttribute?.('animated', '')
      })
      const reverse = direction === 'backward'
      const dragPromise = page.evaluate(async ({ reverse }) => {
        const width = window.visualViewport?.width || window.innerWidth || 500
        const height = window.visualViewport?.height || window.innerHeight || 800
        const totalDeltaX = Math.round(width * 0.64) * (reverse ? 1 : -1)
        for (let step = 1; step <= 10; step += 1) {
          const deltaX = Math.round(totalDeltaX * (step / 10))
          await window.NavicReaderBridge.dispatch({
            type: 'previewPageDrag',
            phase: 'update',
            deltaX,
            deltaY: 0,
            viewWidth: width,
            viewHeight: height,
          })
          await new Promise(resolve => requestAnimationFrame(resolve))
        }
        await window.NavicReaderBridge.dispatch({
          type: 'previewPageDrag',
          phase: 'release',
          deltaX: totalDeltaX,
          deltaY: 0,
          viewWidth: width,
          viewHeight: height,
        })
        await new Promise(resolve => requestAnimationFrame(resolve))
        await window.NavicReaderBridge.dispatch({ type: reverse ? 'previousPage' : 'nextPage' })
      }, { reverse })
      for (const delay of [40, 80, 120, 180, 260, 360]) {
        await page.waitForTimeout(delay)
        samples.push(await collectState(`t+${delay}`))
      }
      await dragPromise
      await page.waitForTimeout(520)
      samples.push(await collectState('settled'))
      const trace = await page.evaluate(start => (window.__navicReaderTrace || []).slice(start), traceStart)
      return { name, direction, samples, trace }
    }

    const dragTransitionEntryProbe = await dragProbe('drag-transition-entry-boundary')
    if (Number(dragTransitionEntryProbe.samples.at(-1)?.pageIndex) < Number(transitionEntryState?.pageIndex)) {
      throw new Error(
        `Expected drag-transition-entry-boundary probe to settle on the discovered transition page; ` +
        `settled=${dragTransitionEntryProbe.samples.at(-1)?.pageIndex}/${dragTransitionEntryProbe.samples.at(-1)?.pageCount} ` +
        `entry=${transitionEntryState?.pageIndex}/${transitionEntryState?.pageCount}`
      )
    }
    const dragPostTransitionBoundaryProbe = await dragProbe('drag-post-transition-boundary')
    const dragTransitionEntryPage = Number(dragTransitionEntryProbe.samples.at(-1)?.pageIndex)
    const dragPostTransitionPage = Number(dragPostTransitionBoundaryProbe.samples.at(-1)?.pageIndex)
    if (!Number.isFinite(dragPostTransitionPage) || dragPostTransitionPage <= dragTransitionEntryPage) {
      throw new Error(
        `Expected drag-post-transition-boundary probe to advance after the discovered boundary page; ` +
        `before=${dragTransitionEntryPage}/${dragTransitionEntryProbe.samples.at(-1)?.pageCount} ` +
        `settled=${dragPostTransitionBoundaryProbe.samples.at(-1)?.pageIndex}/${dragPostTransitionBoundaryProbe.samples.at(-1)?.pageCount}`
      )
    }
    const dragReverseTransitionBoundaryProbe = await dragProbe('drag-reverse-transition-boundary', 'backward')
    const dragReverseTransitionPage = Number(dragReverseTransitionBoundaryProbe.samples.at(-1)?.pageIndex)
    if (!Number.isFinite(dragReverseTransitionPage) || dragReverseTransitionPage >= dragPostTransitionPage) {
      throw new Error(
        `Expected drag-reverse-transition-boundary probe to move back across the discovered boundary; ` +
        `before=${dragPostTransitionPage}/${dragPostTransitionBoundaryProbe.samples.at(-1)?.pageCount} ` +
        `settled=${dragReverseTransitionBoundaryProbe.samples.at(-1)?.pageIndex}/${dragReverseTransitionBoundaryProbe.samples.at(-1)?.pageCount}`
      )
    }
    const postTransitionProbe = await bridgeProbe('frontmatter-post-transition')
    const result = {
      probes: [dragTransitionEntryProbe, dragPostTransitionBoundaryProbe, dragReverseTransitionBoundaryProbe, postTransitionProbe],
      transitionBoundarySearch: {
        first: firstState,
        boundary: transitionBoundary,
        before: beforeTransitionState,
        entry: transitionEntryState,
      },
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const dragProbeMissingDirection = [dragTransitionEntryProbe, dragPostTransitionBoundaryProbe, dragReverseTransitionBoundaryProbe]
      .find(probe => !probe.trace?.some(event => event?.type === 'texture:drag-direction'))
    if (dragProbeMissingDirection) {
      throw new Error(`Expected ${dragProbeMissingDirection.name} to emit texture:drag-direction before checking texture movement`)
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-texture-frontmatter-transition.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertTextureKeysIgnorePageCountOnlyRelabels(result.trace)
    assertTextureTracksRealPageTurnSamples(result)
    assertTextureTracePayloadsTrackTurnDirection(result.trace)
    console.log(`reader harness epub-texture-frontmatter-transition passed: ${outputPath}`)
  } catch (error) {
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'epub-texture-frontmatter-transition.failure.json')
    let state = null
    try {
      state = page
        ? await page.evaluate(() => {
          const view = document.querySelector('foliate-view')
          const renderer = view?.renderer
          const messages = (window.__navicReaderPostedMessages || [])
            .filter(message => message?.type === 'locationChanged')
          return {
            bodyDataset: { ...document.body.dataset },
            postedMessages: messages.slice(-12),
            trace: (window.__navicReaderTrace || []).slice(-160),
            renderer: {
              index: Number(renderer?.getContents?.()?.[0]?.index),
              page: Number(renderer?.page),
              pages: Number(renderer?.pages),
              start: Number(renderer?.start),
              end: Number(renderer?.end),
              viewSize: Number(renderer?.viewSize),
              containerPosition: Number(renderer?.containerPosition),
            },
          }
        })
        : null
    } catch (diagnosticError) {
      state = { diagnosticError: diagnosticError?.message || String(diagnosticError) }
    }
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      error: error?.stack || error?.message || String(error),
      errors,
      state,
    }, null, 2))
    console.error(error?.message || String(error))
    console.error(`reader harness epub-texture-frontmatter-transition failure diagnostics: ${outputPath}`)
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'pdf-smoke') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('pdf-smoke mode requires --fixture <path-to-pdf>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.pdf'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'dark',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 20000 })
    if (await page.evaluate(() => document.body.dataset.navicShellCoverVisible === 'true')) {
      await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
      await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })
    }
    await page.waitForTimeout(1000)

    const currentLocation = async () => page.evaluate(() => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      return messages.at(-1) || null
    })

    const measurePageBounds = async () => {
      const buffer = await page.screenshot({ type: 'png' })
      const dataUrl = `data:image/png;base64,${buffer.toString('base64')}`
      return page.evaluate(async imageUrl => {
        const image = new Image()
        image.src = imageUrl
        await image.decode()
        const canvas = document.createElement('canvas')
        canvas.width = image.naturalWidth
        canvas.height = image.naturalHeight
        const context = canvas.getContext('2d', { willReadFrequently: true })
        context.drawImage(image, 0, 0)
        const { width, height } = canvas
        const data = context.getImageData(0, 0, width, height).data
        let left = width
        let right = -1
        let top = height
        let bottom = -1
        let hits = 0
        for (let y = 0; y < height; y += 1) {
          for (let x = 0; x < width; x += 1) {
            const offset = (y * width + x) * 4
            const red = data[offset]
            const green = data[offset + 1]
            const blue = data[offset + 2]
            const alpha = data[offset + 3]
            const brightPagePixel = alpha > 240 && red > 210 && green > 210 && blue > 210
            if (!brightPagePixel) continue
            hits += 1
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
          }
        }
        if (right < left || bottom < top) {
          return { width, height, coverage: 0, centerError: Number.NaN }
        }
        const boxWidth = right - left + 1
        const boxHeight = bottom - top + 1
        const leftMargin = left
        const rightMargin = width - right - 1
        return {
          width,
          height,
          left,
          right,
          top,
          bottom,
          boxWidth,
          boxHeight,
          leftMargin,
          rightMargin,
          centerError: (left + right) / 2 - width / 2,
          coverage: hits / (width * height),
        }
      }, dataUrl)
    }

    const initialLocation = await currentLocation()
    const initialPageBounds = await measurePageBounds()
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForFunction(previousPageIndex => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const current = messages.at(-1)
      return current && current.pageIndex > previousPageIndex
    }, initialLocation.pageIndex, { timeout: 10000 })
    const afterNextLocation = await currentLocation()
    await page.evaluate(() => {
      window.__navicPdfDoubleNext = Promise.all([
        window.NavicReaderBridge.dispatch({ type: 'nextPage' }),
        window.NavicReaderBridge.dispatch({ type: 'nextPage' }),
      ])
      return true
    })
    await page.evaluate(async () => window.__navicPdfDoubleNext)
    await page.waitForFunction(previousPageIndex => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      const current = messages.at(-1)
      return current && current.pageIndex > previousPageIndex
    }, afterNextLocation.pageIndex, { timeout: 10000 })
    const afterDoubleNextLocation = await currentLocation()
    const result = {
      initialLocation,
      initialPageBounds,
      afterNextLocation,
      afterDoubleNextLocation,
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'pdf-smoke.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertPdfSmoke(result)
    console.log(`reader harness pdf-smoke passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'pdf-image-settings') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('pdf-image-settings mode requires --fixture <path-to-pdf>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.pdf'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'dark',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          pdfFitMode: 'height',
          pdfCropBorders: true,
          pdfPageGapPercent: 12,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 20000 })
    await page.waitForTimeout(1000)

    const pageBounds = await page.screenshot({ type: 'png' }).then(buffer => {
      const dataUrl = `data:image/png;base64,${buffer.toString('base64')}`
      return page.evaluate(async imageUrl => {
        const image = new Image()
        image.src = imageUrl
        await image.decode()
        const canvas = document.createElement('canvas')
        canvas.width = image.naturalWidth
        canvas.height = image.naturalHeight
        const context = canvas.getContext('2d', { willReadFrequently: true })
        context.drawImage(image, 0, 0)
        const { width, height } = canvas
        const data = context.getImageData(0, 0, width, height).data
        let left = width
        let right = -1
        let top = height
        let bottom = -1
        let hits = 0
        for (let y = 0; y < height; y += 1) {
          for (let x = 0; x < width; x += 1) {
            const offset = (y * width + x) * 4
            const red = data[offset]
            const green = data[offset + 1]
            const blue = data[offset + 2]
            const alpha = data[offset + 3]
            const brightPagePixel = alpha > 240 && red > 210 && green > 210 && blue > 210
            if (!brightPagePixel) continue
            hits += 1
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
          }
        }
        return {
          width,
          height,
          left,
          right,
          top,
          bottom,
          coverage: hits / (width * height),
        }
      }, dataUrl)
    })

    const rendererState = await page.evaluate(() => {
      const view = document.querySelector('foliate-view')
      const renderer = view?.renderer
      const pageGapPx = Number.parseFloat(renderer?.getAttribute('data-navic-pdf-page-gap-px') || '')
      return {
        zoom: renderer?.getAttribute('zoom'),
        cropBorders: renderer?.getAttribute('data-navic-pdf-crop-borders'),
        pageGapPx,
        rootPageGap: getComputedStyle(document.documentElement).getPropertyValue('--reader-pdf-page-gap'),
      }
    })

    const result = {
      pageBounds,
      rendererState,
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'pdf-image-settings.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertPdfImageSettings(result)
    console.log(`reader harness pdf-image-settings passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'pdf-fast-sequential-turns') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('pdf-fast-sequential-turns mode requires --fixture <path-to-pdf>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.pdf'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          let parsed = value
          try {
            parsed = JSON.parse(value)
          } catch {
            parsed = { raw: value }
          }
          window.__navicReaderPostedMessages.push(parsed)
          window.__navicReaderTrace.push({
            type: 'bridge:post',
            timestamp: Date.now(),
            payload: parsed,
          })
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'dark',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 20000 })
    if (await page.evaluate(() => document.body.dataset.navicShellCoverVisible === 'true')) {
      await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
      await page.waitForFunction(() => document.body.dataset.navicShellCoverVisible !== 'true', null, { timeout: 5000 })
    }
    await page.waitForTimeout(500)

    const currentLocation = async () => page.evaluate(() => {
      const messages = (window.__navicReaderPostedMessages || [])
        .filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
      return messages.at(-1) || null
    })

    const initialLocation = await currentLocation()
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.evaluate(async () => window.NavicReaderBridge.dispatch({ type: 'nextPage' }))
    await page.waitForTimeout(1000)
    const finalLocation = await currentLocation()
    const result = {
      initialLocation,
      finalLocation,
      trace: await page.evaluate(() => window.__navicReaderTrace || []),
    }
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'pdf-fast-sequential-turns.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertPdfFastSequentialTurns(result)
    console.log(`reader harness pdf-fast-sequential-turns passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-shell-cover') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-shell-cover mode requires --fixture <path-to-epub>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          try {
            window.__navicReaderPostedMessages.push(JSON.parse(value))
          } catch {
            window.__navicReaderPostedMessages.push({ raw: value })
          }
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacing: 0.35,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForTimeout(500)
    const result = await page.evaluate(async () => {
      const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
      const shellVisible = () => document.body.dataset.navicShellCoverVisible === 'true'
      const location = () => {
        const messages = window.__navicReaderPostedMessages || []
        return [...messages].reverse().find(message => message?.type === 'locationChanged') || null
      }
      const initialShellVisible = shellVisible()
      const initialLocation = location()
      await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      await wait(900)
      const afterNextShellVisible = shellVisible()
      const afterNextLocation = location()
      await window.NavicReaderBridge.dispatch({ type: 'nextPage' })
      await wait(900)
      const afterSecondNextShellVisible = shellVisible()
      const afterSecondNextLocation = location()
      await window.NavicReaderBridge.dispatch({ type: 'previousPage' })
      await wait(900)
      const afterSecondPreviousShellVisible = shellVisible()
      const afterSecondPreviousLocation = location()
      await window.NavicReaderBridge.dispatch({ type: 'previousPage' })
      await wait(900)
      const afterPreviousShellVisible = shellVisible()
      const afterPreviousLocation = location()
      return {
        initialShellVisible,
        initialLocation,
        afterNextShellVisible,
        afterNextLocation,
        afterSecondNextShellVisible,
        afterSecondNextLocation,
        afterSecondPreviousShellVisible,
        afterSecondPreviousLocation,
        afterPreviousShellVisible,
        afterPreviousLocation,
      }
    })
    assertNoConsoleErrors(errors)
    assertShellCoverDoesNotNavigateWebViewToCover(result)
    console.log(`reader harness epub-shell-cover passed: ${JSON.stringify(result)}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'epub-external-shell-cover') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('epub-external-shell-cover mode requires --fixture <path-to-epub>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          try {
            window.__navicReaderPostedMessages.push(JSON.parse(value))
          } catch {
            window.__navicReaderPostedMessages.push({ raw: value })
          }
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        externalShellCover: true,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacing: 0.35,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const messages = window.__navicReaderPostedMessages || []
      return messages.some(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
    }, null, { timeout: 15000 })
    const result = await page.evaluate(async () => {
      const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
      const shellVisible = () => document.body.dataset.navicShellCoverVisible === 'true'
      const shellLayerExists = () => Boolean(document.querySelector('[data-navic-shell-cover-layer="true"]'))
      const location = () => {
        const messages = window.__navicReaderPostedMessages || []
        return [...messages].reverse().find(message => message?.type === 'locationChanged') || null
      }
      const initial = {
        shellVisible: shellVisible(),
        shellLayerExists: shellLayerExists(),
        location: location(),
      }
      await window.NavicReaderBridge.dispatch({ type: 'previousPage' })
      await wait(900)
      const afterPrevious = {
        shellVisible: shellVisible(),
        shellLayerExists: shellLayerExists(),
        location: location(),
      }
      return { initial, afterPrevious }
    })
    assertNoConsoleErrors(errors)
    if (result.initial.shellVisible || result.initial.shellLayerExists) {
      throw new Error(`Expected external shell-cover mode not to show a WebView shell cover initially; observed ${JSON.stringify(result.initial)}`)
    }
    if (result.initial.location?.pageIndex !== 0) {
      throw new Error(`Expected external shell-cover mode to start WebView on first readable pageIndex 0; observed ${JSON.stringify(result.initial.location)}`)
    }
    if (result.afterPrevious.shellVisible || result.afterPrevious.shellLayerExists) {
      throw new Error(`Expected previousPage in external shell-cover mode not to show the WebView fallback cover; observed ${JSON.stringify(result.afterPrevious)}`)
    }
    console.log(`reader harness epub-external-shell-cover passed: ${JSON.stringify(result)}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode === 'css-smoke') {
  const fixturePath = path.resolve(argValue('--fixture') || '')
  if (!fixturePath || !fs.existsSync(fixturePath)) {
    console.error('css-smoke mode requires --fixture <path-to-epub>')
    process.exit(1)
  }

  const fixtureRoute = '/fixtures/local/input.epub'
  const server = await startReaderAssetServer({
    repoRoot,
    extraFiles: new Map([[fixtureRoute, fixturePath]]),
  })
  const browser = await chromium.launch()
  const errors = []
  try {
    const page = await browser.newPage(readerHarnessViewport)
    page.on('console', message => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', error => errors.push(error?.stack || error?.message || String(error)))
    await page.addInitScript(() => {
      window.__navicReaderTrace = []
      window.__navicReaderPostedMessages = []
      window.NavicAndroidBridge = {
        postMessage(value) {
          try {
            window.__navicReaderPostedMessages.push(JSON.parse(value))
          } catch {
            window.__navicReaderPostedMessages.push({ raw: value })
          }
        },
      }
    })
    await page.goto(`${server.origin}/index.html`, { waitUntil: 'domcontentloaded' })
    await page.evaluate(async publicationUrl => {
      await window.NavicReaderBridge.dispatch({
        type: 'openPublication',
        url: publicationUrl,
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          tapZone: 'disabled',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 100,
          lineHeight: 1.55,
          paragraphSpacingPercent: 150,
        },
      })
    }, `${server.origin}${fixtureRoute}`)
    await page.waitForFunction(() => {
      const contents = document.querySelector('foliate-view')?.renderer?.getContents?.() || []
      return contents.some(content => content?.doc?.body)
    }, null, { timeout: 10000 })
    await page.waitForTimeout(500)
    const result = await page.evaluate(async () => {
      const view = document.querySelector('foliate-view')
      const contents = view?.renderer?.getContents?.() || []
      const content = contents.find(entry => entry?.doc?.body)
      if (!content?.doc) throw new Error('Missing loaded EPUB content document')
      const doc = content.doc
      const win = doc.defaultView
      const probe = doc.createElement('section')
      probe.setAttribute('data-navic-css-smoke', 'true')
      probe.innerHTML = `
        <p data-navic-css-smoke-paragraph="true">Navic paragraph spacing probe one.</p>
        <p data-navic-css-smoke-paragraph="true">Navic paragraph spacing probe two.</p>
        <a data-navic-link-kind="text" data-navic-css-smoke-link="true" href="#navic-css-smoke-target">Probe link</a>
        <a data-navic-link-kind="media" data-navic-css-smoke-media-link="true" href="#navic-css-smoke-target">
          <img data-navic-css-smoke-media-image="true" alt="" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16'%3E%3Crect width='16' height='16' fill='white'/%3E%3C/svg%3E">
        </a>
        <span id="navic-css-smoke-target">target</span>
      `
      doc.body.prepend(probe)
      await new Promise(resolve => win.requestAnimationFrame(resolve))

      const htmlStyle = win.getComputedStyle(doc.documentElement)
      const bodyStyle = win.getComputedStyle(doc.body)
      const paragraph = doc.querySelector('[data-navic-css-smoke-paragraph="true"]')
      const paragraphStyle = win.getComputedStyle(paragraph)
      const paragraphRectAt100 = paragraph.getBoundingClientRect()
      const bodyRectAt100 = doc.body.getBoundingClientRect()
      const htmlRectAt100 = doc.documentElement.getBoundingClientRect()
      const paragraphFontSizeAt100 = Number.parseFloat(paragraphStyle.fontSize || '0')
      const bodyFontSizeAt100 = Number.parseFloat(bodyStyle.fontSize || '0')
      const textLink = doc.querySelector('[data-navic-css-smoke-link="true"]')
      const textLinkStyle = win.getComputedStyle(textLink)
      const textLinkAfterStyle = win.getComputedStyle(textLink, '::after')
      const mediaLink = doc.querySelector('[data-navic-css-smoke-media-link="true"]')
      const mediaLinkAfterStyle = win.getComputedStyle(mediaLink, '::after')
      const image = doc.querySelector('[data-navic-css-smoke-media-image="true"]')
      const imageMixBlendModeBefore = win.getComputedStyle(image).mixBlendMode
      await win.parent.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: {
          theme: 'sepia',
          paged: true,
          flowMode: 'paged',
          tapZone: 'disabled',
          fontSource: 'publisher',
          fontFamily: 'serif',
          fontSizePercent: 140,
          lineHeight: 1.55,
          paragraphSpacingPercent: 150,
        },
      })
      await new Promise(resolve => win.requestAnimationFrame(() => win.requestAnimationFrame(resolve)))
      const htmlStyleAt140 = win.getComputedStyle(doc.documentElement)
      const bodyStyleAt140 = win.getComputedStyle(doc.body)
      const paragraphStyleAt140 = win.getComputedStyle(paragraph)
      const paragraphFontSizeAt140 = Number.parseFloat(paragraphStyleAt140.fontSize || '0')
      const bodyFontSizeAt140 = Number.parseFloat(bodyStyleAt140.fontSize || '0')
      const postedMessages = () => Array.isArray(win.parent.__navicReaderPostedMessages)
        ? win.parent.__navicReaderPostedMessages
        : []
      const contentTapHandledSources = messages => messages
        .filter(message => message?.type === 'readerContentTapHandled')
        .map(message => message?.source || '')
      const clickOptionsFor = element => {
        const rect = element.getBoundingClientRect()
        return {
          bubbles: true,
          cancelable: true,
          button: 0,
          clientX: Math.round(rect.left + rect.width / 2),
          clientY: Math.round(rect.top + rect.height / 2),
        }
      }
      const touchPointFor = element => {
        const rect = element.getBoundingClientRect()
        const clientX = Math.round(rect.left + rect.width / 2)
        const clientY = Math.round(rect.top + rect.height / 2)
        return {
          identifier: 1,
          target: element,
          clientX,
          clientY,
          pageX: clientX + win.scrollX,
          pageY: clientY + win.scrollY,
          screenX: clientX,
          screenY: clientY,
        }
      }
      const rootPointFor = element => {
        const rect = element.getBoundingClientRect()
        const frameRect = win.frameElement?.getBoundingClientRect?.()
        return {
          x: Math.round((frameRect?.left || 0) + rect.left + rect.width / 2),
          y: Math.round((frameRect?.top || 0) + rect.top + rect.height / 2),
        }
      }
      const dispatchTouchEvent = (element, type, touches, changedTouches) => {
        const event = new win.Event(type, { bubbles: true, cancelable: true })
        Object.defineProperty(event, 'touches', { value: touches, configurable: true })
        Object.defineProperty(event, 'targetTouches', { value: touches, configurable: true })
        Object.defineProperty(event, 'changedTouches', { value: changedTouches, configurable: true })
        element.dispatchEvent(event)
      }
      const dispatchSyntheticTouchTap = element => {
        const touch = touchPointFor(element)
        dispatchTouchEvent(element, 'touchstart', [touch], [touch])
        dispatchTouchEvent(element, 'touchend', [], [touch])
      }
      const dispatchContextMenu = element => {
        element.dispatchEvent(new win.MouseEvent('contextmenu', clickOptionsFor(element)))
      }
      const traceLengthBeforeImageClicks = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.length
        : 0
      const postedLengthBeforeImageClicks = postedMessages().length
      image.dispatchEvent(new win.MouseEvent('click', clickOptionsFor(image)))
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      const imageOverlayDatasetAfterFirstClick = image.dataset.navicSepiaOverlay || ''
      const imageMixBlendModeAfterFirstClick = win.getComputedStyle(image).mixBlendMode
      await new Promise(resolve => win.setTimeout(resolve, 700))
      image.dispatchEvent(new win.MouseEvent('click', clickOptionsFor(image)))
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      const imageOverlayDatasetAfterSecondClick = image.dataset.navicSepiaOverlay || ''
      const imageMixBlendModeAfterSecondClick = win.getComputedStyle(image).mixBlendMode
      const traceAfterImageClicks = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.slice(traceLengthBeforeImageClicks)
        : []
      const postedAfterImageClicks = postedMessages().slice(postedLengthBeforeImageClicks)
      const traceLengthBeforeTextLinkClick = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.length
        : 0
      const postedLengthBeforeTextLinkClick = postedMessages().length
      win.__navicLastMediaTapHandledAt = 0
      win.__navicSuppressNextMediaClickUntil = 0
      textLink.dispatchEvent(new win.MouseEvent('click', clickOptionsFor(textLink)))
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const traceAfterTextLinkClick = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.slice(traceLengthBeforeTextLinkClick)
        : []
      const postedAfterTextLinkClick = postedMessages().slice(postedLengthBeforeTextLinkClick)
      const imageContentTapHandledSources = contentTapHandledSources(postedAfterImageClicks)
      const textLinkContentTapHandledSources = contentTapHandledSources(postedAfterTextLinkClick)
      const postedLengthBeforeImageTouch = postedMessages().length
      dispatchSyntheticTouchTap(image)
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const postedAfterImageTouch = postedMessages().slice(postedLengthBeforeImageTouch)
      const postedLengthBeforeTextLinkTouch = postedMessages().length
      dispatchSyntheticTouchTap(textLink)
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const postedAfterTextLinkTouch = postedMessages().slice(postedLengthBeforeTextLinkTouch)
      const imageTouchContentTapHandledSources = contentTapHandledSources(postedAfterImageTouch)
      const textLinkTouchContentTapHandledSources = contentTapHandledSources(postedAfterTextLinkTouch)
      const imageRootPoint = rootPointFor(image)
      const textLinkRootPoint = rootPointFor(textLink)
      const paragraphRootPoint = rootPointFor(paragraph)
      const imageNativeCenterContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        imageRootPoint.x,
        imageRootPoint.y
      )
      const nativeCoordinateScale = Number(win.parent.devicePixelRatio || win.devicePixelRatio || 1)
      const nativeViewWidth = Math.round((win.parent.visualViewport?.width || win.parent.innerWidth) * nativeCoordinateScale)
      const nativeViewHeight = Math.round((win.parent.visualViewport?.height || win.parent.innerHeight) * nativeCoordinateScale)
      const imageNativeScaledContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        Math.round(imageRootPoint.x * nativeCoordinateScale),
        Math.round(imageRootPoint.y * nativeCoordinateScale),
        nativeViewWidth,
        nativeViewHeight
      )
      const textLinkNativeCenterContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        textLinkRootPoint.x,
        textLinkRootPoint.y
      )
      const textLinkNativeScaledContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        Math.round(textLinkRootPoint.x * nativeCoordinateScale),
        Math.round(textLinkRootPoint.y * nativeCoordinateScale),
        nativeViewWidth,
        nativeViewHeight
      )
      const paragraphNativeCenterContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        paragraphRootPoint.x,
        paragraphRootPoint.y
      )
      const paragraphNativeScaledContentHit = win.parent.NavicReaderBridge.readerContentActionAtPoint(
        Math.round(paragraphRootPoint.x * nativeCoordinateScale),
        Math.round(paragraphRootPoint.y * nativeCoordinateScale),
        nativeViewWidth,
        nativeViewHeight
      )
      const recentTouchContentHitAfterRemoval = async (html, selector) => {
        const wrapper = doc.createElement('span')
        wrapper.setAttribute('data-navic-css-smoke-transient-touch', 'true')
        wrapper.innerHTML = html
        doc.body.prepend(wrapper)
        await new Promise(resolve => win.requestAnimationFrame(resolve))
        const target = wrapper.querySelector(selector)
        if (!target) throw new Error(`Missing transient target for ${selector}`)
        const rootPoint = rootPointFor(target)
        dispatchSyntheticTouchTap(target)
        wrapper.remove()
        await new Promise(resolve => win.setTimeout(resolve, 25))
        return win.parent.NavicReaderBridge.readerContentActionAtPoint(
          Math.round(rootPoint.x * nativeCoordinateScale),
          Math.round(rootPoint.y * nativeCoordinateScale),
          nativeViewWidth,
          nativeViewHeight
        )
      }
      const imageRecentTouchContentHitAfterRemoval = await recentTouchContentHitAfterRemoval(
        `<img alt="" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='20'%3E%3Crect width='20' height='20' fill='white'/%3E%3C/svg%3E">`,
        'img'
      )
      const textLinkRecentTouchContentHitAfterRemoval = await recentTouchContentHitAfterRemoval(
        `<a href="#navic-css-smoke-target">Transient link</a>`,
        'a'
      )
      await win.parent.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: {
          nativeTapZones: true,
        },
      })
      const nativeProbe = doc.createElement('section')
      nativeProbe.setAttribute('data-navic-css-smoke-native-tap-zones', 'true')
      nativeProbe.innerHTML = `
        <a data-navic-css-smoke-native-link="true" href="#navic-css-smoke-target">Native probe link</a>
        <a data-navic-css-smoke-native-media-link="true" href="#navic-css-smoke-target">
          <img data-navic-css-smoke-native-media-image="true" alt="" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16'%3E%3Crect width='16' height='16' fill='white'/%3E%3C/svg%3E">
        </a>
      `
      doc.body.prepend(nativeProbe)
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      const nativeTextLink = nativeProbe.querySelector('[data-navic-css-smoke-native-link="true"]')
      const nativeImage = nativeProbe.querySelector('[data-navic-css-smoke-native-media-image="true"]')
      const traceLengthBeforeNativeTapZones = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.length
        : 0
      const postedLengthBeforeNativeTapZones = postedMessages().length
      nativeImage.dispatchEvent(new win.MouseEvent('click', clickOptionsFor(nativeImage)))
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      dispatchSyntheticTouchTap(nativeImage)
      await new Promise(resolve => win.setTimeout(resolve, 100))
      win.__navicLastMediaTapHandledAt = 0
      win.__navicSuppressNextMediaClickUntil = 0
      nativeTextLink.dispatchEvent(new win.MouseEvent('click', clickOptionsFor(nativeTextLink)))
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const traceAfterNativeTapZonesShortTap = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.slice(traceLengthBeforeNativeTapZones)
        : []
      const postedAfterNativeTapZonesShortTap = postedMessages().slice(postedLengthBeforeNativeTapZones)
      const traceLengthBeforeNativeLongPress = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.length
        : 0
      const postedLengthBeforeNativeLongPress = postedMessages().length
      dispatchContextMenu(nativeImage)
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      win.__navicLastMediaTapHandledAt = 0
      win.__navicSuppressNextMediaClickUntil = 0
      dispatchContextMenu(nativeTextLink)
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const traceAfterNativeLongPress = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.slice(traceLengthBeforeNativeLongPress)
        : []
      const postedAfterNativeLongPress = postedMessages().slice(postedLengthBeforeNativeLongPress)
      const nativeCoordinateProbe = doc.createElement('section')
      nativeCoordinateProbe.setAttribute('data-navic-css-smoke-native-coordinate-long-press', 'true')
      nativeCoordinateProbe.innerHTML = `
        <a data-navic-css-smoke-native-coordinate-link="true" href="#navic-css-smoke-target">Native coordinate probe link</a>
        <a data-navic-css-smoke-native-coordinate-media-link="true" href="#navic-css-smoke-target">
          <img data-navic-css-smoke-native-coordinate-media-image="true" alt="" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16'%3E%3Crect width='16' height='16' fill='white'/%3E%3C/svg%3E">
        </a>
      `
      doc.body.prepend(nativeCoordinateProbe)
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      const nativeCoordinateTextLink = nativeCoordinateProbe.querySelector('[data-navic-css-smoke-native-coordinate-link="true"]')
      const nativeCoordinateImage = nativeCoordinateProbe.querySelector('[data-navic-css-smoke-native-coordinate-media-image="true"]')
      const traceLengthBeforeNativeCoordinateLongPress = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.length
        : 0
      const postedLengthBeforeNativeCoordinateLongPress = postedMessages().length
      const nativeCoordinateImagePoint = rootPointFor(nativeCoordinateImage)
      await win.parent.NavicReaderBridge.dispatch({
        type: 'contentLongPressAt',
        x: nativeCoordinateImagePoint.x,
        y: nativeCoordinateImagePoint.y,
        viewWidth: Math.round(win.parent.visualViewport?.width || win.parent.innerWidth),
        viewHeight: Math.round(win.parent.visualViewport?.height || win.parent.innerHeight),
      })
      await new Promise(resolve => win.requestAnimationFrame(resolve))
      win.__navicLastMediaTapHandledAt = 0
      win.__navicSuppressNextMediaClickUntil = 0
      const nativeCoordinateTextLinkPoint = rootPointFor(nativeCoordinateTextLink)
      await win.parent.NavicReaderBridge.dispatch({
        type: 'contentLongPressAt',
        x: nativeCoordinateTextLinkPoint.x,
        y: nativeCoordinateTextLinkPoint.y,
        viewWidth: Math.round(win.parent.visualViewport?.width || win.parent.innerWidth),
        viewHeight: Math.round(win.parent.visualViewport?.height || win.parent.innerHeight),
      })
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const traceAfterNativeCoordinateLongPress = Array.isArray(win.parent.__navicReaderTrace)
        ? win.parent.__navicReaderTrace.slice(traceLengthBeforeNativeCoordinateLongPress)
        : []
      const postedAfterNativeCoordinateLongPress = postedMessages().slice(postedLengthBeforeNativeCoordinateLongPress)
      const dispatchFoliateLinkEvent = (href, anchorElement) => {
        const event = new CustomEvent('link', {
          cancelable: true,
          detail: {
            href,
            a: anchorElement,
          },
        })
        view.dispatchEvent(event)
        return event.defaultPrevented
      }
      const internalLinkMessages = messages => messages.filter(message => message?.type === 'internalLink')
      const internalLinkSources = messages => internalLinkMessages(messages).map(message => message?.source || '')
      const postedLengthBeforeNativeInternalLink = postedMessages().length
      const nativeTapZonesFoliateLinkDefaultPrevented = dispatchFoliateLinkEvent(
        '#navic-css-smoke-target',
        nativeTextLink
      )
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const nativeTapZonesInternalLinkMessages = internalLinkMessages(
        postedMessages().slice(postedLengthBeforeNativeInternalLink)
      )
      await win.parent.NavicReaderBridge.dispatch({
        type: 'applySettings',
        settings: {
          nativeTapZones: false,
        },
      })
      const postedLengthBeforeNonNativeInternalLink = postedMessages().length
      const nonNativeFoliateLinkDefaultPrevented = dispatchFoliateLinkEvent(
        '#navic-css-smoke-target',
        nativeTextLink
      )
      await new Promise(resolve => win.setTimeout(resolve, 100))
      const nonNativeInternalLinkMessages = internalLinkMessages(
        postedMessages().slice(postedLengthBeforeNonNativeInternalLink)
      )
      const nativeTapZoneSuppressionSources = traceAfterNativeTapZonesShortTap
        .filter(event => event?.type === 'native-tap-zones:content-click-suppressed')
        .map(event => event?.payload?.source || '')
      const nativeTapZoneLongPressSources = traceAfterNativeLongPress
        .filter(event => event?.type === 'native-tap-zones:content-long-press')
        .map(event => event?.payload?.source || '')
      const nativeTapZoneCoordinateLongPressSources = traceAfterNativeCoordinateLongPress
        .filter(event => event?.type === 'native-tap-zones:content-long-press-at')
        .map(event => event?.payload?.source || '')
      const surfaceTextureLayer = document.querySelector('[data-navic-surface-paper-texture-layer="true"]')
      const movingPageTextureLayer = document.querySelector('[data-navic-moving-page-paper-texture-layer="true"]')
      const movingPageBorderLayer = document.querySelector('[data-navic-moving-page-border-overlay-layer="true"]')
      const movingPageTextureSlot = movingPageTextureLayer?.querySelector?.('[data-navic-surface-paper-texture-slot="current"]')
      const movingPageBorderSlot = movingPageBorderLayer?.querySelector?.('[data-navic-surface-page-border-overlay-slot="current"]')
      const movingPageTextureArtwork = movingPageTextureSlot?.querySelector?.('[data-navic-surface-texture-slot-artwork="true"]')
      const movingPageBorderArtwork = movingPageBorderSlot?.querySelector?.('[data-navic-surface-texture-slot-artwork="true"]')
      const surfaceTextureStyle = surfaceTextureLayer ? getComputedStyle(surfaceTextureLayer) : null
      const movingPageTextureStyle = movingPageTextureLayer ? getComputedStyle(movingPageTextureLayer) : null
      const movingPageBorderStyle = movingPageBorderLayer ? getComputedStyle(movingPageBorderLayer) : null
      const movingPageTextureSlotStyle = movingPageTextureSlot ? getComputedStyle(movingPageTextureSlot) : null
      const movingPageBorderSlotStyle = movingPageBorderSlot ? getComputedStyle(movingPageBorderSlot) : null
      const movingPageTextureArtworkStyle = movingPageTextureArtwork ? getComputedStyle(movingPageTextureArtwork) : null
      const movingPageBorderArtworkStyle = movingPageBorderArtwork ? getComputedStyle(movingPageBorderArtwork) : null
      return {
        contentDocumentCount: contents.length,
        viewportWidth: Number(win.parent.visualViewport?.width || win.parent.innerWidth || 0),
        viewportHeight: Number(win.parent.visualViewport?.height || win.parent.innerHeight || 0),
        bodyWidthAt100: bodyRectAt100.width,
        htmlWidthAt100: htmlRectAt100.width,
        paragraphWidthAt100: paragraphRectAt100.width,
        theme: doc.documentElement.dataset.navicReaderTheme || '',
        htmlBackground: htmlStyle.backgroundColor,
        bodyBackground: bodyStyle.backgroundColor,
        rootFontSizeAt100: htmlStyle.fontSize,
        bodyFontSizeAt100: bodyStyle.fontSize,
        paragraphFontSizeAt100: paragraphStyle.fontSize,
        rootFontSizeAt140: htmlStyleAt140.fontSize,
        bodyFontSizeAt140: bodyStyleAt140.fontSize,
        paragraphFontSizeAt140: paragraphStyleAt140.fontSize,
        paragraphFontSizeDelta: Number.isFinite(paragraphFontSizeAt100) && Number.isFinite(paragraphFontSizeAt140)
          ? paragraphFontSizeAt140 - paragraphFontSizeAt100
          : null,
        bodyFontSizeDelta: Number.isFinite(bodyFontSizeAt100) && Number.isFinite(bodyFontSizeAt140)
          ? bodyFontSizeAt140 - bodyFontSizeAt100
          : null,
        paragraphSpacingVariable: bodyStyle.getPropertyValue('--reader-paragraph-spacing') ||
          htmlStyle.getPropertyValue('--reader-paragraph-spacing'),
        paragraphMarginBottom: paragraphStyle.marginBlockEnd || paragraphStyle.marginBottom,
        textLinkColor: textLinkStyle.color,
        textLinkDecoration: textLinkStyle.textDecorationLine,
        textLinkAfterContent: textLinkAfterStyle.content,
        textLinkAfterVerticalAlign: textLinkAfterStyle.verticalAlign,
        mediaLinkAfterContent: mediaLinkAfterStyle.content,
        imageMixBlendModeBefore,
        imageOverlayDatasetAfterFirstClick,
        imageMixBlendModeAfterFirstClick,
        imageOverlayDatasetAfterSecondClick,
        imageMixBlendModeAfterSecondClick,
        imageOverlayTraceCount: traceAfterImageClicks.filter(event => event?.type === 'image:sepia-overlay').length,
        imageNavigationTraceCount: traceAfterImageClicks.filter(event => event?.type === 'link:navigate').length,
        imageContentTapHandledCount: imageContentTapHandledSources.length,
        imageContentTapHandledSources,
        imageTouchContentTapHandledCount: imageTouchContentTapHandledSources.length,
        imageTouchContentTapHandledSources,
        textLinkNavigationTraceCount: traceAfterTextLinkClick.filter(event => event?.type === 'link:navigate').length,
        textLinkHitMissTraceCount: traceAfterTextLinkClick.filter(event => event?.type === 'link:text-hit-miss').length,
        textLinkContentTapHandledCount: textLinkContentTapHandledSources.length,
        textLinkContentTapHandledSources,
        textLinkTouchContentTapHandledCount: textLinkTouchContentTapHandledSources.length,
        textLinkTouchContentTapHandledSources,
        nativeTapZonesSuppressedImageClickCount: nativeTapZoneSuppressionSources.filter(source => source === 'image-click').length,
        nativeTapZonesSuppressedImageTouchCount: nativeTapZoneSuppressionSources.filter(source => source === 'image-touchend').length,
        nativeTapZonesSuppressedTextLinkClickCount: nativeTapZoneSuppressionSources.filter(source => source === 'link-click').length,
        nativeTapZonesImageOverlayTraceCount: traceAfterNativeTapZonesShortTap.filter(event => event?.type === 'image:sepia-overlay').length,
        nativeTapZonesTextLinkNavigationTraceCount: traceAfterNativeTapZonesShortTap.filter(event => event?.type === 'link:navigate').length,
        nativeTapZonesContentPostCount: postedAfterNativeTapZonesShortTap.filter(message => message?.type === 'readerContentTapHandled').length,
        nativeTapZonesPostedMessageCount: postedAfterNativeTapZonesShortTap.length,
        nativeTapZonesLongPressImageOverlayTraceCount: traceAfterNativeLongPress.filter(event => event?.type === 'image:sepia-overlay').length,
        nativeTapZonesLongPressTextLinkNavigationTraceCount: traceAfterNativeLongPress.filter(event => event?.type === 'link:navigate').length,
        nativeTapZonesLongPressContentPostSources: contentTapHandledSources(postedAfterNativeLongPress),
        nativeTapZonesLongPressSources: nativeTapZoneLongPressSources,
        nativeTapZonesCoordinateLongPressImageOverlayTraceCount: traceAfterNativeCoordinateLongPress.filter(event => event?.type === 'image:sepia-overlay').length,
        nativeTapZonesCoordinateLongPressTextLinkNavigationTraceCount: traceAfterNativeCoordinateLongPress.filter(event => event?.type === 'link:navigate').length,
        nativeTapZonesCoordinateLongPressContentPostSources: contentTapHandledSources(postedAfterNativeCoordinateLongPress),
        nativeTapZonesCoordinateLongPressSources: nativeTapZoneCoordinateLongPressSources,
        nativeTapZonesFoliateLinkDefaultPrevented,
        nativeTapZonesInternalLinkPreventedCount: nativeTapZonesInternalLinkMessages
          .filter(message => message?.prevented === true).length,
        nativeTapZonesInternalLinkSources: internalLinkSources(nativeTapZonesInternalLinkMessages),
        nonNativeFoliateLinkDefaultPrevented,
        nonNativeInternalLinkAllowedCount: nonNativeInternalLinkMessages
          .filter(message => message?.prevented === false).length,
        nonNativeInternalLinkSources: internalLinkSources(nonNativeInternalLinkMessages),
        imageNativeCenterContentHit,
        imageNativeScaledContentHit,
        textLinkNativeCenterContentHit,
        textLinkNativeScaledContentHit,
        paragraphNativeCenterContentHit,
        paragraphNativeScaledContentHit,
        imageRecentTouchContentHitAfterRemoval,
        textLinkRecentTouchContentHitAfterRemoval,
        surfaceTextureBackgroundImage: surfaceTextureStyle?.backgroundImage || '',
        surfaceTextureOpacity: surfaceTextureStyle?.opacity || '',
        movingPageTextureLayerPresent: Boolean(movingPageTextureLayer),
        movingPageBorderLayerPresent: Boolean(movingPageBorderLayer),
        movingPageTextureBackgroundImage: movingPageTextureStyle?.backgroundImage || '',
        movingPageTextureOpacity: movingPageTextureStyle?.opacity || '',
        movingPageTextureSlotPresent: Boolean(movingPageTextureSlot),
        movingPageTextureSlotBackgroundImage: movingPageTextureSlotStyle?.backgroundImage || '',
        movingPageTextureSlotOpacity: movingPageTextureSlotStyle?.opacity || '',
        movingPageTextureArtworkPresent: Boolean(movingPageTextureArtwork),
        movingPageTextureArtworkBackgroundImage: movingPageTextureArtworkStyle?.backgroundImage || '',
        movingPageTextureArtworkOpacity: movingPageTextureArtworkStyle?.opacity || '',
        movingPageBorderBackgroundImage: movingPageBorderStyle?.backgroundImage || '',
        movingPageBorderOpacity: movingPageBorderStyle?.opacity || '',
        movingPageBorderSlotPresent: Boolean(movingPageBorderSlot),
        movingPageBorderSlotBackgroundImage: movingPageBorderSlotStyle?.backgroundImage || '',
        movingPageBorderSlotOpacity: movingPageBorderSlotStyle?.opacity || '',
        movingPageBorderArtworkPresent: Boolean(movingPageBorderArtwork),
        movingPageBorderArtworkBackgroundImage: movingPageBorderArtworkStyle?.backgroundImage || '',
        movingPageBorderArtworkOpacity: movingPageBorderArtworkStyle?.opacity || '',
        surfaceTextureAsset: document.body.dataset.navicSurfacePaperTextureAsset || '',
        surfaceBorderAsset: document.body.dataset.navicSurfaceBorderOverlayAsset || '',
        documentTextureBackgroundImage: htmlStyle.backgroundImage || '',
        documentTextureAsset: doc.documentElement.dataset.navicDocumentPaperTextureAsset || '',
        documentBorderAsset: doc.documentElement.dataset.navicDocumentPaperBorderAsset || '',
      }
    })
    const trace = await page.evaluate(() => window.__navicReaderTrace || [])
    const postedMessages = await page.evaluate(() => window.__navicReaderPostedMessages || [])
    const outputDir = path.join(repoRoot, 'tools/reader-harness/output')
    fs.mkdirSync(outputDir, { recursive: true })
    const outputPath = path.join(outputDir, 'css-smoke.trace.json')
    fs.writeFileSync(outputPath, JSON.stringify({
      fixture: fixturePath,
      generatedAt: new Date().toISOString(),
      result,
      trace,
      postedMessages,
    }, null, 2))
    assertNoConsoleErrors(errors)
    assertTraceType(trace, 'runtime:ready')
    assertTraceType(trace, 'texture:update')
    assertRendererCssSmoke(result)
    console.log(`reader harness css-smoke passed: ${outputPath}`)
  } catch (error) {
    console.error(error?.message || String(error))
    process.exitCode = 1
  } finally {
    await browser.close()
    await server.close()
  }
  process.exit(process.exitCode || 0)
}

if (mode !== 'smoke') {
  console.error(`Unsupported reader harness mode: ${mode}`)
  process.exit(1)
}

if (!bridgeText.includes('readerTrace') || !helperText.includes('__navicReaderTrace')) {
  console.error('Reader harness requires readerTrace instrumentation in the reader runtime and helpers')
  process.exit(1)
}

console.log('reader harness smoke passed')
