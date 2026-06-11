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
  if (result.imageOverlayDatasetAfterSecondClick === 'off' || result.imageMixBlendModeAfterSecondClick !== 'multiply') {
    throw new Error(
      `Expected clicking the image again to restore sepia overlay; observed dataset=${result.imageOverlayDatasetAfterSecondClick || 'unset'} blend=${result.imageMixBlendModeAfterSecondClick}`
    )
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
