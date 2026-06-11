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

export const assertShellCoverDoesNotNavigateWebViewToCover = result => {
  if (result?.initialShellVisible !== true) {
    throw new Error('Expected metadata cover to be shown as the initial shell cover overlay')
  }
  if (result?.afterNextShellVisible !== false) {
    throw new Error('Expected nextPage from shell cover to hide the shell cover overlay')
  }
  if (result?.afterPreviousShellVisible !== true) {
    throw new Error('Expected previousPage from first readable content to restore the shell cover overlay')
  }
  const hrefs = [
    result?.initialLocation?.href,
    result?.afterNextLocation?.href,
    result?.afterPreviousLocation?.href,
  ].filter(Boolean)
  const coverHref = hrefs.find(href => /cover|frontcover|coverpage/i.test(href))
  if (coverHref) {
    throw new Error(`Expected WebView location to remain on readable content, but observed cover href ${coverHref}`)
  }
}
