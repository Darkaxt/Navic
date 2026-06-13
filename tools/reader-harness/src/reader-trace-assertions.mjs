export const assertTraceType = (trace, type) => {
  if (!Array.isArray(trace)) {
    throw new Error('Expected reader trace to be an array')
  }
  if (!trace.some(event => event?.type === type)) {
    const observed = trace.map(event => event?.type).filter(Boolean).join(', ') || 'none'
    throw new Error(`Expected trace event ${type}; observed: ${observed}`)
  }
}

export const assertNoConsoleErrors = errors => {
  if (errors.length > 0) {
    throw new Error(`Expected no browser console errors; observed:\n${errors.join('\n')}`)
  }
}

export const assertBridgePostType = (messages, type) => {
  if (!Array.isArray(messages)) {
    throw new Error('Expected bridge messages to be an array')
  }
  if (!messages.some(message => message?.type === type)) {
    const observed = messages.map(message => message?.type).filter(Boolean).join(', ') || 'none'
    throw new Error(`Expected bridge message ${type}; observed: ${observed}`)
  }
}

const locationKey = message => [
  message.href || '',
  message.cfi || '',
  Number.isFinite(message.pageIndex) ? message.pageIndex : '',
  Number.isFinite(message.pageCount) ? message.pageCount : '',
  message.tocTitle || '',
].join('|')

const coverPathPattern = /cover|frontcover|coverpage|cubierta|portada/i

export const assertFirstVisibleLocationStartsAtZero = messages => {
  const firstLocation = messages.find(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
  if (!firstLocation) {
    throw new Error('Expected at least one locationChanged message with a finite pageIndex')
  }
  if (firstLocation.pageIndex !== 0) {
    throw new Error(`Expected first visible WebView pageIndex to be 0; observed ${firstLocation.pageIndex}`)
  }
}

export const assertNoConsecutiveDuplicateLocations = messages => {
  const locations = messages.filter(message => message?.type === 'locationChanged')
  for (let index = 1; index < locations.length; index += 1) {
    const previous = locations[index - 1]
    const current = locations[index]
    if (locationKey(previous) === locationKey(current)) {
      throw new Error(`Expected duplicate consecutive locationChanged messages to be suppressed; duplicate at index ${index}: ${locationKey(current)}`)
    }
  }
}

export const assertNoConsecutiveDuplicateVisiblePageLabels = messages => {
  const locations = messages.filter(message =>
    message?.type === 'locationChanged' &&
    Number.isFinite(message.pageIndex) &&
    Number.isFinite(message.pageCount)
  )
  for (let index = 1; index < locations.length; index += 1) {
    const previous = locations[index - 1]
    const current = locations[index]
    if (previous.pageIndex === current.pageIndex && previous.pageCount === current.pageCount) {
      throw new Error(`Expected each forward page turn to advance the visible page label; repeated ${current.pageIndex}/${current.pageCount} at location index ${index}`)
    }
  }
}

export const assertForwardPageIndexesDoNotRegress = messages => {
  const locations = messages.filter(message => message?.type === 'locationChanged' && Number.isFinite(message.pageIndex))
  for (let index = 1; index < locations.length; index += 1) {
    const previous = locations[index - 1]
    const current = locations[index]
    if (current.pageIndex < previous.pageIndex) {
      throw new Error(`Expected forward page indexes not to regress; ${previous.pageIndex} -> ${current.pageIndex} at location index ${index}`)
    }
  }
}

export const assertSurfaceTextureTracksForwardContentMovement = result => {
  const delta = Number(result?.delta)
  if (!Number.isFinite(delta) || delta <= 0) {
    throw new Error(`Expected a positive renderer movement delta; observed ${result?.delta}`)
  }
  const roundedDelta = Math.round(delta)
  const backgroundPosition = String(result?.textureBackgroundPosition || '')
  const negativeOffsetPattern = new RegExp(`(?:-\\s*${roundedDelta}px|\\+\\s*-${roundedDelta}px)`)
  if (!negativeOffsetPattern.test(backgroundPosition)) {
    throw new Error(`Expected texture background to move left with forward content movement ${roundedDelta}px; observed "${backgroundPosition}"`)
  }
}

const parseBackgroundPositionOffset = value => {
  const text = String(value || '')
  const match = text.match(/calc\(50%\s*([+-])\s*(-?\d+(?:\.\d+)?)px\)/i)
  if (!match) return null
  const sign = match[1] === '-' ? -1 : 1
  return sign * Number.parseFloat(match[2])
}

const textureOffsetForSample = sample =>
  parseBackgroundPositionOffset(sample?.textureBackgroundPosition) ??
  parseBackgroundPositionOffset(sample?.computedTextureBackgroundPosition)

export const assertTextureTracksRealPageTurnSamples = result => {
  if (!result || !Array.isArray(result.probes) || result.probes.length === 0) {
    throw new Error('Expected texture page-turn probes')
  }

  for (const probe of result.probes) {
    if (!Array.isArray(probe.samples) || probe.samples.length < 2) {
      throw new Error(`Expected probe ${probe.name || 'unknown'} to include before/after samples`)
    }
    const before = probe.samples[0]
    const movedSamples = probe.samples
      .slice(1)
      .map(sample => ({
        sample,
        positionDelta: Number(sample.position) - Number(before.position),
        textureDelta: textureOffsetForSample(sample) - textureOffsetForSample(before),
      }))
      .filter(({ sample, positionDelta, textureDelta }) => {
        const viewportSpan = Math.max(
          1,
          Number(sample?.viewportWidth),
          Number(sample?.viewportHeight),
          Number(before?.viewportWidth),
          Number(before?.viewportHeight)
        )
        const rendererWrapJump = Math.abs(positionDelta) > viewportSpan * 2
        return Number.isFinite(positionDelta) &&
          Math.abs(positionDelta) > 1 &&
          Number.isFinite(textureDelta) &&
          !rendererWrapJump
      })
    if (movedSamples.length === 0) {
      throw new Error(`Expected probe ${probe.name || 'unknown'} to observe renderer movement with texture CSS samples`)
    }
    if (probe.direction === 'forward') {
      const forwardInversion = movedSamples.find(({ textureDelta }) => textureDelta > 1)
      if (forwardInversion) {
        throw new Error(
          `Expected forward texture movement not to invert in probe ${probe.name || 'unknown'}; ` +
          `observed textureDelta=${forwardInversion.textureDelta} at ${forwardInversion.sample?.label || 'unknown'}`
        )
      }
    }
    const inverted = movedSamples.find(({ positionDelta, textureDelta }) =>
      Math.sign(textureDelta) !== 0 &&
      Math.sign(positionDelta) === Math.sign(textureDelta)
    )
    if (inverted) {
      throw new Error(
        `Expected texture to counter-move with renderer in probe ${probe.name || 'unknown'}; ` +
        `observed positionDelta=${inverted.positionDelta}, textureDelta=${inverted.textureDelta}`
      )
    }
    const stationary = movedSamples.every(({ textureDelta }) => Math.abs(textureDelta) <= 1)
    if (stationary) {
      throw new Error(`Expected texture to move during probe ${probe.name || 'unknown'}; observed stationary texture`)
    }
  }
}

export const assertTextureTracePayloadsTrackTurnDirection = trace => {
  if (!Array.isArray(trace)) {
    throw new Error('Expected reader trace to be an array')
  }
  const scrollEvents = trace
    .filter(event => event?.type === 'texture:scroll')
    .map(event => event?.payload)
    .filter(payload => payload && typeof payload === 'object')
  if (scrollEvents.length === 0) {
    throw new Error('Expected texture:scroll trace payloads')
  }
  const directedScrollEvents = scrollEvents.filter(payload =>
    payload.pageTurnDirection === 'next' || payload.pageTurnDirection === 'previous'
  )
  if (directedScrollEvents.length === 0) {
    throw new Error('Expected directed texture:scroll payloads')
  }
  for (const payload of directedScrollEvents) {
    const xOffset = Number(payload.offset?.x)
    const yOffset = Number(payload.offset?.y)
    const position = Number(payload.position)
    const baseOffset = Number(payload.baseOffset)
    const delta = Number.isFinite(position) && Number.isFinite(baseOffset)
      ? position - baseOffset
      : Number(payload.delta)
    if (payload.pageTurnDirection === 'next' && Number.isFinite(xOffset) && xOffset > 1) {
      throw new Error(
        `Expected next texture trace to move left/counter-forward; ` +
        `observed x=${xOffset} delta=${Number.isFinite(delta) ? delta : 'unknown'} ` +
        `page=${payload.pageIndex ?? ''}/${payload.pageCount ?? ''} href=${payload.href || ''}`
      )
    }
    if (payload.pageTurnDirection === 'previous' && Number.isFinite(xOffset) && xOffset < -1) {
      throw new Error(
        `Expected previous texture trace to move right/counter-back; ` +
        `observed x=${xOffset} delta=${Number.isFinite(delta) ? delta : 'unknown'} ` +
        `page=${payload.pageIndex ?? ''}/${payload.pageCount ?? ''} href=${payload.href || ''}`
      )
    }
    if (Number.isFinite(delta) && Number.isFinite(xOffset) && Math.abs(delta) > 1 && Math.abs(xOffset) > 1) {
      if (Math.sign(delta) === Math.sign(xOffset)) {
        throw new Error(
          `Expected texture trace to counter-move renderer; ` +
          `observed x=${xOffset} delta=${delta} direction=${payload.pageTurnDirection} ` +
          `page=${payload.pageIndex ?? ''}/${payload.pageCount ?? ''} href=${payload.href || ''}`
        )
      }
    }
    if (payload.flowMode === 'paged-vertical' && Number.isFinite(delta) && Number.isFinite(yOffset) && Math.abs(yOffset) > 1) {
      if (Math.sign(delta) === Math.sign(yOffset)) {
        throw new Error(
          `Expected vertical texture trace to counter-move renderer; ` +
          `observed y=${yOffset} delta=${delta} direction=${payload.pageTurnDirection} ` +
          `page=${payload.pageIndex ?? ''}/${payload.pageCount ?? ''} href=${payload.href || ''}`
        )
      }
    }
  }
}

export const assertTextureUpdatesAreCommittedPageBounded = trace => {
  if (!Array.isArray(trace)) {
    throw new Error('Expected reader trace to be an array')
  }
  const committedLocations = trace.filter(event => event?.type === 'location:post')
  const textureUpdates = trace.filter(event => event?.type === 'texture:update')
  if (committedLocations.length === 0) {
    throw new Error('Expected committed location trace events before checking texture updates')
  }
  if (textureUpdates.length === 0) {
    throw new Error('Expected texture update trace events')
  }
  const allowedStartupUpdates = 8
  const maxTextureUpdates = committedLocations.length + allowedStartupUpdates
  if (textureUpdates.length > maxTextureUpdates) {
    throw new Error(
      `Expected texture updates to be bounded by committed visible pages; observed ${textureUpdates.length} texture updates for ${committedLocations.length} committed locations`
    )
  }
}

export const assertPdfSmoke = result => {
  if (!result?.initialLocation || !Number.isFinite(result.initialLocation.pageIndex)) {
    throw new Error('Expected PDF smoke to receive an initial page location')
  }
  if (!Number.isFinite(result.initialLocation.pageCount) || result.initialLocation.pageCount < 2) {
    throw new Error(`Expected PDF smoke fixture to expose multiple pages; observed ${result.initialLocation.pageCount}`)
  }
  if (!result?.initialPageBounds || !Number.isFinite(result.initialPageBounds.centerError)) {
    throw new Error('Expected PDF smoke to measure visible page bounds from screenshot')
  }
  if (Math.abs(result.initialPageBounds.centerError) > 10) {
    throw new Error(
      `Expected PDF page to be horizontally centered; ` +
      `left=${result.initialPageBounds.leftMargin} right=${result.initialPageBounds.rightMargin} ` +
      `centerError=${result.initialPageBounds.centerError}`
    )
  }
  if (result.initialPageBounds.coverage < 0.2) {
    throw new Error(`Expected screenshot to contain a visible PDF page; coverage=${result.initialPageBounds.coverage}`)
  }
  if (result.afterNextLocation?.pageIndex !== result.initialLocation.pageIndex + 1) {
    throw new Error(
      `Expected PDF nextPage to advance one page; ` +
      `observed ${result.initialLocation.pageIndex} -> ${result.afterNextLocation?.pageIndex}`
    )
  }
  if (result.afterDoubleNextLocation?.pageIndex !== result.afterNextLocation.pageIndex + 1) {
    throw new Error(
      `Expected coalesced PDF double nextPage to advance only one page; ` +
      `observed ${result.afterNextLocation.pageIndex} -> ${result.afterDoubleNextLocation?.pageIndex}`
    )
  }
}

export const assertPdfFastSequentialTurns = result => {
  if (!result?.initialLocation || !Number.isFinite(result.initialLocation.pageIndex)) {
    throw new Error('Expected PDF fast-turn test to receive an initial page location')
  }
  if (!Number.isFinite(result.initialLocation.pageCount) || result.initialLocation.pageCount < 3) {
    throw new Error(`Expected PDF fast-turn fixture to expose at least 3 pages; observed ${result.initialLocation.pageCount}`)
  }
  if (result.finalLocation?.pageIndex !== result.initialLocation.pageIndex + 2) {
    throw new Error(
      `Expected two sequential fast PDF nextPage commands to advance two pages; ` +
      `observed ${result.initialLocation.pageIndex} -> ${result.finalLocation?.pageIndex}`
    )
  }
  const postedIndexes = (result.trace || [])
    .filter(event => event?.type === 'location:post')
    .map(event => event?.payload?.message?.pageIndex)
    .filter(Number.isFinite)
  for (let index = 1; index < postedIndexes.length; index += 1) {
    if (postedIndexes[index] < postedIndexes[index - 1]) {
      throw new Error(`Expected PDF posted page indexes to be monotonic; observed ${postedIndexes.join(', ')}`)
    }
  }
}

export const assertPdfImageSettings = result => {
  if (!result?.rendererState) {
    throw new Error('Expected PDF/Image settings test to collect renderer state')
  }
  if (result.rendererState.zoom !== 'fit-height') {
    throw new Error(`Expected PDF fit height to set fixed-layout zoom=fit-height; observed ${result.rendererState.zoom || 'unset'}`)
  }
  if (result.rendererState.cropBorders !== 'true') {
    throw new Error(`Expected PDF crop borders to reach fixed-layout renderer; observed ${result.rendererState.cropBorders || 'unset'}`)
  }
  const pageGapPx = Number(result.rendererState.pageGapPx)
  if (!Number.isFinite(pageGapPx) || pageGapPx < 40) {
    throw new Error(`Expected PDF page gap to be converted into visible pixels; observed ${result.rendererState.pageGapPx}`)
  }
  if (!result?.pageBounds || !Number.isFinite(result.pageBounds.top)) {
    throw new Error('Expected PDF/Image settings test to measure visible page bounds')
  }
  if (result.pageBounds.top < pageGapPx * 0.45) {
    throw new Error(
      `Expected PDF page gap to move the rendered page down; ` +
      `top=${result.pageBounds.top} gap=${pageGapPx}`
    )
  }
}

export const assertFullEpubTraversal = result => {
  if (!result || !Array.isArray(result.pages) || result.pages.length === 0) {
    throw new Error('Expected full EPUB traversal to collect rendered pages')
  }
  const first = result.pages[0]?.location
  const last = result.pages.at(-1)?.location
  if (!first || !last) {
    throw new Error('Expected full EPUB traversal pages to include locations')
  }
  if (first.pageIndex !== 0) {
    throw new Error(`Expected traversal to start on WebView page 0 after shell cover handoff; observed ${first.pageIndex}`)
  }
  if (!Number.isFinite(last.pageCount) || last.pageCount <= 0) {
    throw new Error(`Expected finite final page count; observed ${last.pageCount}`)
  }
  if (last.pageIndex !== last.pageCount - 1) {
    throw new Error(`Expected traversal to reach final page ${last.pageCount - 1}; observed ${last.pageIndex}`)
  }
  const pageCounts = new Set(result.pages.map(page => page.location?.pageCount).filter(Number.isFinite))
  if (pageCounts.size !== 1) {
    throw new Error(`Expected stable page count during full traversal; observed ${[...pageCounts].join(', ')}`)
  }
  for (let index = 1; index < result.pages.length; index += 1) {
    const previous = result.pages[index - 1].location
    const current = result.pages[index].location
    if (current.pageIndex <= previous.pageIndex) {
      throw new Error(`Expected full traversal page labels to strictly advance; observed ${previous.pageIndex} -> ${current.pageIndex} at sample ${index}`)
    }
    if (current.pageIndex !== previous.pageIndex + 1) {
      throw new Error(`Expected full traversal not to skip labels; observed ${previous.pageIndex} -> ${current.pageIndex} at sample ${index}`)
    }
  }
  if (result.pages.length !== last.pageCount) {
    throw new Error(`Expected one rendered sample per visible page; collected ${result.pages.length} for ${last.pageCount} pages`)
  }
  if (Array.isArray(result.coverImageHits) && result.coverImageHits.length > 0) {
    throw new Error(`Expected WebView not to render cover image assets; observed ${JSON.stringify(result.coverImageHits.slice(0, 3))}`)
  }
  if (Array.isArray(result.coverLikePages) && result.coverLikePages.length > 0) {
    throw new Error(`Expected WebView not to render cover-like image-only pages; observed ${JSON.stringify(result.coverLikePages.slice(0, 3))}`)
  }
}

export const assertShellCoverDoesNotNavigateWebViewToCover = result => {
  if (result?.initialShellVisible !== true) {
    throw new Error('Expected metadata cover to be shown as the initial shell cover overlay')
  }
  if (result?.afterNextShellVisible !== false) {
    throw new Error('Expected nextPage from shell cover to hide the shell cover overlay')
  }
  if (!result?.afterNextLocation?.href && !result?.afterNextLocation?.cfi) {
    throw new Error('Expected shell-cover handoff to post a readable WebView location')
  }
  if (result?.afterSecondNextShellVisible !== false) {
    throw new Error('Expected second nextPage to stay inside readable content')
  }
  if (!result?.afterSecondNextLocation?.href && !result?.afterSecondNextLocation?.cfi) {
    throw new Error('Expected second nextPage to post a readable WebView location')
  }
  if (result?.afterSecondPreviousShellVisible !== false) {
    throw new Error('Expected previousPage from the second readable page to stay in content instead of returning to the cover')
  }
  if (
    Number.isFinite(result?.afterSecondNextLocation?.pageIndex) &&
    Number.isFinite(result?.afterSecondPreviousLocation?.pageIndex) &&
    result.afterSecondPreviousLocation.pageIndex >= result.afterSecondNextLocation.pageIndex
  ) {
    throw new Error(
      `Expected previousPage from second readable page to decrement page index; observed ${result.afterSecondNextLocation.pageIndex} -> ${result.afterSecondPreviousLocation.pageIndex}`
    )
  }
  if (result?.afterPreviousShellVisible !== true) {
    throw new Error('Expected previousPage from first readable content to restore the shell cover overlay')
  }
  const hrefs = [
    result?.initialLocation?.href,
    result?.afterNextLocation?.href,
    result?.afterPreviousLocation?.href,
  ].filter(Boolean)
  const coverHref = hrefs.find(href => coverPathPattern.test(href))
  if (coverHref) {
    throw new Error(`Expected WebView location to remain on readable content, but observed cover href ${coverHref}`)
  }
}

const numericCss = value => {
  const parsed = Number.parseFloat(String(value || ''))
  return Number.isFinite(parsed) ? parsed : null
}

const isTransparent = color =>
  !color || color === 'transparent' || color === 'rgba(0, 0, 0, 0)'

const isNativeBlueLinkColor = color =>
  String(color || '').trim().toLowerCase() === 'rgb(0, 0, 238)' ||
  String(color || '').trim().toLowerCase() === '#0000ee'

export const assertRendererCssSmoke = result => {
  if (!result || result.contentDocumentCount < 1) {
    throw new Error('Expected css-smoke to inspect at least one loaded EPUB content document')
  }
  if (result.theme !== 'sepia') {
    throw new Error(`Expected content document to use sepia theme; observed ${result.theme || 'unset'}`)
  }
  if (isTransparent(result.htmlBackground) || isTransparent(result.bodyBackground)) {
    throw new Error(`Expected sepia backgrounds on html/body; observed html=${result.htmlBackground} body=${result.bodyBackground}`)
  }
  if (result.bodyBackground === 'rgb(255, 255, 255)' || result.htmlBackground === 'rgb(255, 255, 255)') {
    throw new Error(`Expected sepia backgrounds instead of white pages; observed html=${result.htmlBackground} body=${result.bodyBackground}`)
  }
  const paragraphMargin = numericCss(result.paragraphMarginBottom)
  if (paragraphMargin == null || paragraphMargin <= 0) {
    throw new Error(`Expected positive paragraph spacing; observed ${result.paragraphMarginBottom || 'unset'}`)
  }
  if (!String(result.paragraphSpacingVariable || '').includes('1.5em')) {
    throw new Error(`Expected paragraph spacing variable to use 1.5em for 150%; observed ${result.paragraphSpacingVariable || 'unset'}`)
  }
  if (String(result.textLinkDecoration || '').toLowerCase() !== 'none') {
    throw new Error(`Expected text links to remove native underline; observed ${result.textLinkDecoration}`)
  }
  if (isNativeBlueLinkColor(result.textLinkColor)) {
    throw new Error(`Expected text links not to use native browser blue; observed ${result.textLinkColor}`)
  }
  if (!String(result.textLinkAfterContent || '').includes('»')) {
    throw new Error(`Expected text links to expose the organic continuation marker; observed ${result.textLinkAfterContent || 'unset'}`)
  }
  if (!String(result.textLinkAfterVerticalAlign || '').toLowerCase().includes('sub')) {
    throw new Error(`Expected text link marker to be subscript-like; observed vertical-align=${result.textLinkAfterVerticalAlign || 'unset'}`)
  }
  if (result.textLinkNavigationTraceCount !== 1) {
    throw new Error(`Expected styled text link click to emit one navigation trace; observed ${result.textLinkNavigationTraceCount}`)
  }
  if (result.textLinkHitMissTraceCount !== 0) {
    throw new Error(`Expected styled text link click not to be rejected as a text hit miss; observed ${result.textLinkHitMissTraceCount}`)
  }
  if (String(result.mediaLinkAfterContent || '').replaceAll('"', '').trim() !== '') {
    throw new Error(`Expected media links not to inherit text-link marker; observed ${result.mediaLinkAfterContent}`)
  }
  if (result.imageMixBlendModeBefore !== 'multiply') {
    throw new Error(`Expected sepia image overlay to start with multiply blend; observed ${result.imageMixBlendModeBefore}`)
  }
  if (result.imageOverlayDatasetAfterFirstClick !== 'off' || result.imageMixBlendModeAfterFirstClick !== 'normal') {
    throw new Error(
      `Expected clicking an image to disable sepia overlay; observed dataset=${result.imageOverlayDatasetAfterFirstClick || 'unset'} blend=${result.imageMixBlendModeAfterFirstClick}`
    )
  }
  if (result.imageOverlayTraceCount !== 2) {
    throw new Error(`Expected two image tint-toggle traces; observed ${result.imageOverlayTraceCount}`)
  }
  if (result.imageNavigationTraceCount !== 0) {
    throw new Error(`Expected image tint toggles not to emit link navigation traces; observed ${result.imageNavigationTraceCount}`)
  }
  if (!Number.isFinite(Number(result.imageContentTapHandledCount)) || result.imageContentTapHandledCount < 2) {
    throw new Error(
      `Expected image tint toggle to send readerContentTapHandled for native chrome suppression; observed ${result.imageContentTapHandledCount || 0}`
    )
  }
  if (!Array.isArray(result.imageContentTapHandledSources) || !result.imageContentTapHandledSources.includes('image')) {
    throw new Error(
      `Expected image tint toggle to send readerContentTapHandled from image source; observed ${JSON.stringify(result.imageContentTapHandledSources || [])}`
    )
  }
  if (!Number.isFinite(Number(result.imageTouchContentTapHandledCount)) || result.imageTouchContentTapHandledCount < 1) {
    throw new Error(
      `Expected image touch to send readerContentTapHandled before native chrome dispatch; observed ${result.imageTouchContentTapHandledCount || 0}`
    )
  }
  if (!Array.isArray(result.imageTouchContentTapHandledSources) || !result.imageTouchContentTapHandledSources.includes('media-touch')) {
    throw new Error(
      `Expected image touch to send readerContentTapHandled from media-touch source; observed ${JSON.stringify(result.imageTouchContentTapHandledSources || [])}`
    )
  }
  if (result.imageOverlayDatasetAfterSecondClick === 'off' || result.imageMixBlendModeAfterSecondClick !== 'multiply') {
    throw new Error(
      `Expected clicking the image again to restore sepia overlay; observed dataset=${result.imageOverlayDatasetAfterSecondClick || 'unset'} blend=${result.imageMixBlendModeAfterSecondClick}`
    )
  }
  if (result.textLinkContentTapHandledCount !== 1) {
    throw new Error(
      `Expected styled text link click to send readerContentTapHandled for native chrome suppression; observed ${result.textLinkContentTapHandledCount || 0}`
    )
  }
  if (!Array.isArray(result.textLinkContentTapHandledSources) || !result.textLinkContentTapHandledSources.includes('link')) {
    throw new Error(
      `Expected styled text link click to send readerContentTapHandled from link source; observed ${JSON.stringify(result.textLinkContentTapHandledSources || [])}`
    )
  }
  if (!Number.isFinite(Number(result.textLinkTouchContentTapHandledCount)) || result.textLinkTouchContentTapHandledCount < 1) {
    throw new Error(
      `Expected styled text link touch to send readerContentTapHandled before native chrome dispatch; observed ${result.textLinkTouchContentTapHandledCount || 0}`
    )
  }
  if (!Array.isArray(result.textLinkTouchContentTapHandledSources) || !result.textLinkTouchContentTapHandledSources.includes('link-touch')) {
    throw new Error(
      `Expected styled text link touch to send readerContentTapHandled from link-touch source; observed ${JSON.stringify(result.textLinkTouchContentTapHandledSources || [])}`
    )
  }
  if (result.imageNativeCenterContentHit !== true) {
    throw new Error('Expected native center hit-test to suppress image chrome')
  }
  if (result.imageNativeScaledContentHit !== true) {
    throw new Error('Expected scaled native center hit-test to suppress image chrome')
  }
  if (result.textLinkNativeCenterContentHit !== true) {
    throw new Error('Expected native center hit-test to suppress link chrome')
  }
  if (result.textLinkNativeScaledContentHit !== true) {
    throw new Error('Expected scaled native center hit-test to suppress link chrome')
  }
  if (result.paragraphNativeCenterContentHit !== false) {
    throw new Error('Expected native center hit-test not to suppress ordinary paragraph text')
  }
  if (result.paragraphNativeScaledContentHit !== false) {
    throw new Error('Expected scaled native center hit-test not to suppress ordinary paragraph text')
  }
  if (result.imageRecentTouchContentHitAfterRemoval !== true) {
    throw new Error('Expected recent image touch ownership to suppress native chrome after DOM removal')
  }
  if (result.textLinkRecentTouchContentHitAfterRemoval !== true) {
    throw new Error('Expected recent text link touch ownership to suppress native chrome after DOM removal')
  }
  if (!String(result.surfaceTextureBackgroundImage || '').includes('paper-texture')) {
    throw new Error(`Expected surface paper texture layer background image; observed ${result.surfaceTextureBackgroundImage || 'unset'}`)
  }
  if (!String(result.surfaceBorderBackgroundImage || '').includes('page-border-overlay')) {
    throw new Error(`Expected surface border overlay background image; observed ${result.surfaceBorderBackgroundImage || 'unset'}`)
  }
  const textureOpacity = numericCss(result.surfaceTextureOpacity)
  const borderOpacity = numericCss(result.surfaceBorderOpacity)
  if (textureOpacity == null || textureOpacity <= 0) {
    throw new Error(`Expected visible paper texture opacity; observed ${result.surfaceTextureOpacity || 'unset'}`)
  }
  if (borderOpacity == null || borderOpacity <= 0) {
    throw new Error(`Expected visible border overlay opacity; observed ${result.surfaceBorderOpacity || 'unset'}`)
  }
  if (!String(result.surfaceTextureAsset || '').includes('paper-texture')) {
    throw new Error(`Expected paper texture asset dataset to be populated; observed ${result.surfaceTextureAsset || 'unset'}`)
  }
  if (!String(result.surfaceBorderAsset || '').includes('page-border-overlay')) {
    throw new Error(`Expected border overlay asset dataset to be populated; observed ${result.surfaceBorderAsset || 'unset'}`)
  }
}
