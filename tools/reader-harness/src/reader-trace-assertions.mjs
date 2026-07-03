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
  const beforeSlotOffset = textureOffsetForTransformSample({
    textureSlotTransform: result?.beforeTextureSlotTransform,
    computedTextureSlotTransform: result?.beforeComputedTextureSlotTransform,
    flowMode: result?.flowMode,
  })
  const afterSlotOffset = textureOffsetForTransformSample({
    textureSlotTransform: result?.afterTextureSlotTransform,
    computedTextureSlotTransform: result?.afterComputedTextureSlotTransform,
    flowMode: result?.flowMode,
  })
  if (Number.isFinite(beforeSlotOffset) && Number.isFinite(afterSlotOffset)) {
    const textureDelta = afterSlotOffset - beforeSlotOffset
    if (textureDelta > -1) {
      throw new Error(
        `Expected current texture slot to move left with forward content movement ${roundedDelta}px; ` +
        `observed textureDelta=${textureDelta}`
      )
    }
    return
  }
  const backgroundPosition = String(result?.textureBackgroundPosition || '')
  const negativeOffsetPattern = new RegExp(`(?:-\\s*${roundedDelta}px|\\+\\s*-${roundedDelta}px)`)
  if (!negativeOffsetPattern.test(backgroundPosition)) {
    throw new Error(`Expected texture background to move left with forward content movement ${roundedDelta}px; observed "${backgroundPosition}"`)
  }
}

const parseTransformOffsets = value => {
  const text = String(value || '').trim()
  if (!text || text === 'none') return null
  const matrix = text.match(/^matrix\(([^)]+)\)$/i)
  if (matrix) {
    const values = matrix[1].split(',').map(part => Number.parseFloat(part.trim()))
    if (values.length >= 6 && Number.isFinite(values[4]) && Number.isFinite(values[5])) {
      return { x: values[4], y: values[5] }
    }
  }
  const matrix3d = text.match(/^matrix3d\(([^)]+)\)$/i)
  if (matrix3d) {
    const values = matrix3d[1].split(',').map(part => Number.parseFloat(part.trim()))
    if (values.length >= 16 && Number.isFinite(values[12]) && Number.isFinite(values[13])) {
      return { x: values[12], y: values[13] }
    }
  }
  const translate3d = text.match(/^translate3d\(\s*(-?\d+(?:\.\d+)?)px\s*,\s*(-?\d+(?:\.\d+)?)px\s*,/i)
  if (translate3d) return { x: Number.parseFloat(translate3d[1]), y: Number.parseFloat(translate3d[2]) }
  const translate = text.match(/^translate(?:X|Y)?\(\s*(-?\d+(?:\.\d+)?)px(?:\s*,\s*(-?\d+(?:\.\d+)?)px)?\s*\)$/i)
  if (translate) {
    if (/^translateY/i.test(text)) return { x: 0, y: Number.parseFloat(translate[1]) }
    return { x: Number.parseFloat(translate[1]), y: Number.parseFloat(translate[2] || '0') }
  }
  return null
}

const textureOffsetForTransformSample = sample => {
  const offsets =
    parseTransformOffsets(sample?.textureSlotTransform) ??
    parseTransformOffsets(sample?.computedTextureSlotTransform)
  return String(sample?.flowMode || '') === 'paged-vertical' ? offsets?.y : offsets?.x
}

const parseBackgroundPositionOffsets = value => {
  const text = String(value || '')
  const matches = Array.from(text.matchAll(/calc\(50%\s*([+-])\s*(-?\d+(?:\.\d+)?)px\)/gi))
  if (matches.length === 0) return null
  const parseMatch = match => {
    const sign = match[1] === '-' ? -1 : 1
    return sign * Number.parseFloat(match[2])
  }
  const x = parseMatch(matches[0])
  const y = parseMatch(matches[1] || matches[0])
  return { x, y }
}

const textureOffsetForSample = sample => {
  const transformOffset = textureOffsetForTransformSample(sample)
  if (Number.isFinite(transformOffset)) return transformOffset
  const offsets =
    parseBackgroundPositionOffsets(sample?.textureBackgroundPosition) ??
    parseBackgroundPositionOffsets(sample?.computedTextureBackgroundPosition)
  return String(sample?.flowMode || '') === 'paged-vertical' ? offsets?.y : offsets?.x
}

const textureSlotOffsetsForSample = sample => {
  const flowMode = String(sample?.flowMode || '')
  const axis = flowMode === 'paged-vertical' ? 'y' : 'x'
  const entries = []
  for (const item of Array.isArray(sample?.textureSlotTransforms) ? sample.textureSlotTransforms : []) {
    const offsets =
      parseTransformOffsets(item?.transform) ??
      parseTransformOffsets(item?.computedTransform)
    const offset = offsets?.[axis]
    if (Number.isFinite(offset)) {
      entries.push({
        slot: String(item?.slot || 'unknown'),
        offset,
      })
    }
  }
  if (entries.length > 0) return entries
  const fallback = textureOffsetForSample(sample)
  return Number.isFinite(fallback) ? [{ slot: 'current', offset: fallback }] : []
}

const textureDeltaForSamples = (before, sample) => {
  const beforeOffsets = new Map(textureSlotOffsetsForSample(before).map(item => [item.slot, item.offset]))
  const candidates = textureSlotOffsetsForSample(sample)
    .filter(item => beforeOffsets.has(item.slot))
    .map(item => ({
      slot: item.slot,
      textureDelta: item.offset - beforeOffsets.get(item.slot),
    }))
    .filter(item => Number.isFinite(item.textureDelta))
  if (candidates.length === 0) return Number.NaN
  candidates.sort((left, right) => Math.abs(right.textureDelta) - Math.abs(left.textureDelta))
  return candidates[0].textureDelta
}

const parseBackgroundPositionOffset = value => {
  const offsets = parseBackgroundPositionOffsets(value)
  if (!offsets) return null
  return offsets.x
}

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
        textureDelta: textureDeltaForSamples(before, sample),
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
      .map(sample => {
        const viewportSpan = Math.max(
          1,
          Number(sample.sample?.viewportWidth),
          Number(sample.sample?.viewportHeight),
          Number(before?.viewportWidth),
          Number(before?.viewportHeight)
        )
        const expectedDirectionSign = probe.direction === 'forward' ? 1 : probe.direction === 'backward' ? -1 : 0
        const deltaSign = Math.sign(sample.positionDelta)
        return {
          ...sample,
          rendererBoundaryWrap: expectedDirectionSign !== 0 &&
            Math.abs(sample.positionDelta) >= viewportSpan * 0.9 &&
            Math.abs(sample.positionDelta) <= viewportSpan * 2 &&
            deltaSign !== 0 &&
            deltaSign !== expectedDirectionSign,
        }
      })
    if (movedSamples.length === 0) {
      throw new Error(`Expected probe ${probe.name || 'unknown'} to observe renderer movement with texture CSS samples`)
    }
    if (probe.direction === 'forward') {
      const forwardInversion = movedSamples.find(({ textureDelta, rendererBoundaryWrap }) =>
        textureDelta > 1 && !rendererBoundaryWrap
      )
      if (forwardInversion) {
        throw new Error(
          `Expected forward texture movement not to invert in probe ${probe.name || 'unknown'}; ` +
          `observed textureDelta=${forwardInversion.textureDelta} at ${forwardInversion.sample?.label || 'unknown'}`
        )
      }
    }
    if (probe.direction === 'backward') {
      const backwardInversion = movedSamples.find(({ textureDelta, rendererBoundaryWrap }) =>
        textureDelta < -1 && !rendererBoundaryWrap
      )
      if (backwardInversion) {
        throw new Error(
          `Expected backward texture movement not to invert in probe ${probe.name || 'unknown'}; ` +
          `observed textureDelta=${backwardInversion.textureDelta} at ${backwardInversion.sample?.label || 'unknown'}`
        )
      }
    }
    const inverted = movedSamples.find(({ positionDelta, textureDelta }) =>
      probe.direction !== 'forward' &&
      probe.direction !== 'backward' &&
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
    const flowMode = String(payload.flowMode || '')
    const xOffset = Number(payload.offset?.x)
    const yOffset = Number(payload.offset?.y)
    const textureOffset = flowMode === 'paged-vertical' ? yOffset : xOffset
    const textureAxis = flowMode === 'paged-vertical' ? 'y' : 'x'
    const position = Number(payload.position)
    const baseOffset = Number(payload.baseOffset)
    const delta = Number.isFinite(position) && Number.isFinite(baseOffset)
      ? position - baseOffset
      : Number(payload.delta)
    const viewportSpan = Math.max(
      1,
      flowMode === 'paged-vertical' ? Number(payload.viewportHeight) : Number(payload.viewportWidth),
      Number(payload.viewportWidth),
      Number(payload.viewportHeight)
    )
    const expectedDirectionSign = payload.pageTurnDirection === 'next' ? 1 : -1
    const deltaSign = Math.sign(delta)
    const directedBoundaryWrap = Number.isFinite(delta) &&
      Math.abs(delta) >= viewportSpan * 0.75 &&
      Math.abs(delta) <= viewportSpan * 2 &&
      deltaSign !== 0 &&
      deltaSign !== expectedDirectionSign
    if (payload.pageTurnDirection === 'next' && Number.isFinite(textureOffset) && textureOffset > 1 && !directedBoundaryWrap) {
      throw new Error(
        `Expected next texture trace to move left/counter-forward; ` +
        `observed ${textureAxis}=${textureOffset} delta=${Number.isFinite(delta) ? delta : 'unknown'} ` +
        `page=${payload.pageIndex ?? ''}/${payload.pageCount ?? ''} href=${payload.href || ''}`
      )
    }
    if (payload.pageTurnDirection === 'previous' && Number.isFinite(textureOffset) && textureOffset < -1 && !directedBoundaryWrap) {
      throw new Error(
        `Expected previous texture trace to move right/counter-back; ` +
        `observed ${textureAxis}=${textureOffset} delta=${Number.isFinite(delta) ? delta : 'unknown'} ` +
        `page=${payload.pageIndex ?? ''}/${payload.pageCount ?? ''} href=${payload.href || ''}`
      )
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

const textureUpdateStablePageIdentity = payload => [
  payload?.href || '',
  Number.isFinite(payload?.pageIndex) ? payload.pageIndex : '',
].join('|')

const textureUpdatePageTotal = payload =>
  Number.isFinite(payload?.pageCount) ? payload.pageCount : null

export const assertTextureKeysIgnorePageCountOnlyRelabels = trace => {
  if (!Array.isArray(trace)) {
    throw new Error('Expected reader trace to be an array')
  }
  const textureUpdates = trace
    .filter(event => event?.type === 'texture:update')
    .map(event => event?.payload)
    .filter(payload => payload && typeof payload === 'object')
  if (textureUpdates.length === 0) {
    throw new Error('Expected texture update trace events')
  }
  const seenByStablePage = new Map()
  for (const payload of textureUpdates) {
    const identity = textureUpdateStablePageIdentity(payload)
    if (!identity || identity === '|') continue
    const previous = seenByStablePage.get(identity)
    if (
      previous &&
      previous.key &&
      payload.key &&
      previous.key !== payload.key &&
      textureUpdatePageTotal(previous) !== textureUpdatePageTotal(payload)
    ) {
      throw new Error(
        `Expected same-page texture key to ignore changing page totals; ` +
        `${identity} changed ${previous.key} -> ${payload.key}`
      )
    }
    seenByStablePage.set(identity, payload)
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
  const nonProfilePage = result.pages.find(page => page.location?.pageCountSource !== 'pagination-profile')
  if (nonProfilePage) {
    throw new Error(
      `Expected full traversal page labels to use pagination-profile; ` +
      `observed ${nonProfilePage.location?.pageCountSource || 'none'} at ` +
      `${nonProfilePage.location?.pageIndex}/${nonProfilePage.location?.pageCount} ` +
      `href=${nonProfilePage.location?.href || ''}`
    )
  }
  const paginationProfileEvents = Array.isArray(result.paginationProfileEvents)
    ? result.paginationProfileEvents
    : []
  const completeProfileUpdates = paginationProfileEvents
    .map((event, index) => ({ event, index }))
    .filter(({ event }) =>
      event?.type === 'pagination-profile:updated' &&
      event?.payload?.complete === true &&
      Number.isFinite(event?.payload?.pageCount)
    )
  if (completeProfileUpdates.length === 0) {
    throw new Error('Expected full traversal to build a complete pagination profile before traversal')
  }
  const firstCompleteProfile = completeProfileUpdates[0]
  const incompleteProfileBeforeComplete = paginationProfileEvents
    .slice(0, firstCompleteProfile.index)
    .find(event =>
      event?.type === 'pagination-profile:updated' &&
      event?.payload?.complete !== true &&
      Number.isFinite(event?.payload?.pageCount)
    )
  if (incompleteProfileBeforeComplete) {
    throw new Error(
      `Expected pagination measurement not to publish provisional totals before the complete profile; ` +
      `observed provisional ${incompleteProfileBeforeComplete.payload.pageCount}`
    )
  }
  const completeFingerprint = firstCompleteProfile.event.payload.fingerprint || ''
  const completePageCount = firstCompleteProfile.event.payload.pageCount
  const replacingCompleteProfile = completeProfileUpdates
    .slice(1)
    .find(({ event }) =>
      (event.payload.fingerprint || '') === completeFingerprint &&
      event.payload.pageCount !== completePageCount
    )
  if (replacingCompleteProfile) {
    throw new Error(
      `Expected complete pagination profile to remain authoritative; ` +
      `observed ${completePageCount} -> ${replacingCompleteProfile.event.payload.pageCount}`
    )
  }
  const pageWithDifferentCompleteCount = result.pages.find(page =>
    Number.isFinite(page.location?.paginationProfilePageCount) &&
    page.location.paginationProfilePageCount !== completePageCount
  )
  if (pageWithDifferentCompleteCount) {
    throw new Error(
      `Expected page labels to use complete pagination profile count ${completePageCount}; ` +
      `observed ${pageWithDifferentCompleteCount.location.paginationProfilePageCount} at ` +
      `${pageWithDifferentCompleteCount.location.pageIndex}/${pageWithDifferentCompleteCount.location.pageCount}`
    )
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

const assertWideLandscapeContentWidth = result => {
  const viewportWidth = Number(result?.viewportWidth)
  const viewportHeight = Number(result?.viewportHeight)
  if (!Number.isFinite(viewportWidth) ||
    !Number.isFinite(viewportHeight) ||
    viewportWidth < 1200 ||
    viewportWidth <= viewportHeight) {
    return
  }
  const paragraphWidth = Number(result?.paragraphWidthAt100)
  const bodyWidth = Number(result?.bodyWidthAt100)
  const htmlWidth = Number(result?.htmlWidthAt100)
  const minimumParagraphWidth = Math.min(720, viewportWidth * 0.32)
  const minimumDocumentWidth = Math.min(960, viewportWidth * 0.48)
  if (!Number.isFinite(htmlWidth) || htmlWidth < minimumDocumentWidth) {
    throw new Error(
      `Expected landscape EPUB html box to use tablet width; ` +
      `observed html=${htmlWidth} viewport=${viewportWidth}x${viewportHeight}`
    )
  }
  if (!Number.isFinite(bodyWidth) || bodyWidth < minimumDocumentWidth) {
    throw new Error(
      `Expected landscape EPUB body box to use tablet width; ` +
      `observed body=${bodyWidth} viewport=${viewportWidth}x${viewportHeight}`
    )
  }
  if (!Number.isFinite(paragraphWidth) || paragraphWidth < minimumParagraphWidth) {
    throw new Error(
      `Expected landscape EPUB paragraph to avoid min-content collapse; ` +
      `observed paragraph=${paragraphWidth} viewport=${viewportWidth}x${viewportHeight}`
    )
  }
}

export const assertRendererCssSmoke = result => {
  if (!result || result.contentDocumentCount < 1) {
    throw new Error('Expected css-smoke to inspect at least one loaded EPUB content document')
  }
  assertWideLandscapeContentWidth(result)
  if (result.theme !== 'sepia') {
    throw new Error(`Expected content document to use sepia theme; observed ${result.theme || 'unset'}`)
  }
  if (isTransparent(result.htmlBackground) || isTransparent(result.bodyBackground)) {
    throw new Error(`Expected sepia backgrounds on html/body; observed html=${result.htmlBackground} body=${result.bodyBackground}`)
  }
  if (result.bodyBackground === 'rgb(255, 255, 255)' || result.htmlBackground === 'rgb(255, 255, 255)') {
    throw new Error(`Expected sepia backgrounds instead of white pages; observed html=${result.htmlBackground} body=${result.bodyBackground}`)
  }
  const paragraphFontDelta = Number(result.paragraphFontSizeDelta)
  if (!Number.isFinite(paragraphFontDelta) || paragraphFontDelta <= 1) {
    throw new Error(
      `Expected font-size control to scale EPUB paragraph text; ` +
      `observed paragraph ${result.paragraphFontSizeAt100 || 'unset'} -> ${result.paragraphFontSizeAt140 || 'unset'}`
    )
  }
  const bodyFontDelta = Number(result.bodyFontSizeDelta)
  if (!Number.isFinite(bodyFontDelta) || bodyFontDelta <= 1) {
    throw new Error(
      `Expected font-size control to scale EPUB body inheritance, not only headings; ` +
      `observed body ${result.bodyFontSizeAt100 || 'unset'} -> ${result.bodyFontSizeAt140 || 'unset'}`
    )
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
  if (Number(result.imageTouchContentTapHandledCount || 0) !== 0) {
    throw new Error(
      `Expected image short touch to stay native-owned without readerContentTapHandled; observed ${result.imageTouchContentTapHandledCount || 0}`
    )
  }
  if (Array.isArray(result.imageTouchContentTapHandledSources) && result.imageTouchContentTapHandledSources.length > 0) {
    throw new Error(
      `Expected image short touch to avoid content-action sources; observed ${JSON.stringify(result.imageTouchContentTapHandledSources || [])}`
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
  if (Number(result.textLinkTouchContentTapHandledCount || 0) !== 0) {
    throw new Error(
      `Expected styled text link short touch to stay native-owned without readerContentTapHandled; observed ${result.textLinkTouchContentTapHandledCount || 0}`
    )
  }
  if (Array.isArray(result.textLinkTouchContentTapHandledSources) && result.textLinkTouchContentTapHandledSources.length > 0) {
    throw new Error(
      `Expected styled text link short touch to avoid content-action sources; observed ${JSON.stringify(result.textLinkTouchContentTapHandledSources || [])}`
    )
  }
  if (!Number.isFinite(Number(result.nativeTapZonesSuppressedImageClickCount)) || result.nativeTapZonesSuppressedImageClickCount < 1) {
    throw new Error(
      `Expected native tap zones to suppress ordinary image clicks; observed ${result.nativeTapZonesSuppressedImageClickCount || 0}`
    )
  }
  if (!Number.isFinite(Number(result.nativeTapZonesSuppressedImageTouchCount)) || result.nativeTapZonesSuppressedImageTouchCount < 1) {
    throw new Error(
      `Expected native tap zones to suppress ordinary image touchend events; observed ${result.nativeTapZonesSuppressedImageTouchCount || 0}`
    )
  }
  if (result.nativeTapZonesSuppressedTextLinkClickCount !== 1) {
    throw new Error(
      `Expected native tap zones to suppress ordinary text-link clicks; observed ${result.nativeTapZonesSuppressedTextLinkClickCount || 0}`
    )
  }
  if (result.nativeTapZonesImageOverlayTraceCount !== 0) {
    throw new Error(
      `Expected native tap zones to block ordinary image clicks from toggling sepia overlay; observed ${result.nativeTapZonesImageOverlayTraceCount}`
    )
  }
  if (result.nativeTapZonesTextLinkNavigationTraceCount !== 0) {
    throw new Error(
      `Expected native tap zones to block ordinary text-link clicks from WebView navigation; observed ${result.nativeTapZonesTextLinkNavigationTraceCount}`
    )
  }
  if (result.nativeTapZonesContentPostCount !== 0) {
    throw new Error(
      `Expected native tap zones to keep ordinary short taps out of Android content-action posts; observed ${result.nativeTapZonesContentPostCount}`
    )
  }
  if (!Number.isFinite(Number(result.nativeTapZonesLongPressImageOverlayTraceCount)) || result.nativeTapZonesLongPressImageOverlayTraceCount < 1) {
    throw new Error(
      `Expected native tap zones long-press image action to toggle sepia overlay; observed ${result.nativeTapZonesLongPressImageOverlayTraceCount || 0}`
    )
  }
  if (!Number.isFinite(Number(result.nativeTapZonesLongPressTextLinkNavigationTraceCount)) || result.nativeTapZonesLongPressTextLinkNavigationTraceCount < 1) {
    throw new Error(
      `Expected native tap zones long-press text-link navigation; observed ${result.nativeTapZonesLongPressTextLinkNavigationTraceCount || 0}`
    )
  }
  if (!Array.isArray(result.nativeTapZonesLongPressContentPostSources) ||
    !result.nativeTapZonesLongPressContentPostSources.includes('image') ||
    !result.nativeTapZonesLongPressContentPostSources.includes('link-long-press')) {
    throw new Error(
      `Expected native tap zones long-press content posts for image and link; observed ${JSON.stringify(result.nativeTapZonesLongPressContentPostSources || [])}`
    )
  }
  if (!Array.isArray(result.nativeTapZonesLongPressSources) ||
    !result.nativeTapZonesLongPressSources.includes('image-long-press') ||
    !result.nativeTapZonesLongPressSources.includes('link-long-press')) {
    throw new Error(
      `Expected native tap zones long-press traces for image and link; observed ${JSON.stringify(result.nativeTapZonesLongPressSources || [])}`
    )
  }
  if (!Number.isFinite(Number(result.nativeTapZonesCoordinateLongPressImageOverlayTraceCount)) ||
    result.nativeTapZonesCoordinateLongPressImageOverlayTraceCount < 1) {
    throw new Error(
      `Expected native coordinate long-press image action to toggle sepia overlay; observed ${result.nativeTapZonesCoordinateLongPressImageOverlayTraceCount || 0}`
    )
  }
  if (!Number.isFinite(Number(result.nativeTapZonesCoordinateLongPressTextLinkNavigationTraceCount)) ||
    result.nativeTapZonesCoordinateLongPressTextLinkNavigationTraceCount < 1) {
    throw new Error(
      `Expected native coordinate long-press text-link navigation; observed ${result.nativeTapZonesCoordinateLongPressTextLinkNavigationTraceCount || 0}`
    )
  }
  if (!Array.isArray(result.nativeTapZonesCoordinateLongPressContentPostSources) ||
    !result.nativeTapZonesCoordinateLongPressContentPostSources.includes('image') ||
    !result.nativeTapZonesCoordinateLongPressContentPostSources.includes('native-long-press-command')) {
    throw new Error(
      `Expected native coordinate long-press content posts for image and link; observed ${JSON.stringify(result.nativeTapZonesCoordinateLongPressContentPostSources || [])}`
    )
  }
  if (!Array.isArray(result.nativeTapZonesCoordinateLongPressSources) ||
    result.nativeTapZonesCoordinateLongPressSources.filter(source => source === 'native-long-press-command').length < 2) {
    throw new Error(
      `Expected native coordinate long-press traces for image and link; observed ${JSON.stringify(result.nativeTapZonesCoordinateLongPressSources || [])}`
    )
  }
  if (result.nativeTapZonesFoliateLinkDefaultPrevented !== true) {
    throw new Error('Expected native tap zones Foliate link event to be canceled')
  }
  if (!Number.isFinite(Number(result.nativeTapZonesInternalLinkPreventedCount)) ||
    result.nativeTapZonesInternalLinkPreventedCount < 1 ||
    !Array.isArray(result.nativeTapZonesInternalLinkSources) ||
    !result.nativeTapZonesInternalLinkSources.includes('native-short-tap')) {
    throw new Error(
      `Expected native tap zones internalLink bridge post with native-short-tap source; observed count=${result.nativeTapZonesInternalLinkPreventedCount || 0} sources=${JSON.stringify(result.nativeTapZonesInternalLinkSources || [])}`
    )
  }
  if (result.nonNativeFoliateLinkDefaultPrevented !== false) {
    throw new Error('Expected non-native Foliate link event to remain uncanceled')
  }
  if (!Number.isFinite(Number(result.nonNativeInternalLinkAllowedCount)) ||
    result.nonNativeInternalLinkAllowedCount < 1 ||
    !Array.isArray(result.nonNativeInternalLinkSources) ||
    !result.nonNativeInternalLinkSources.includes('foliate-link')) {
    throw new Error(
      `Expected non-native internalLink bridge post with foliate-link source; observed count=${result.nonNativeInternalLinkAllowedCount || 0} sources=${JSON.stringify(result.nonNativeInternalLinkSources || [])}`
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
  if (result.imageRecentLongPressContentHitAfterRemoval !== true) {
    throw new Error('Expected recent image long-press ownership to suppress native chrome after DOM removal')
  }
  if (result.textLinkRecentLongPressContentHitAfterRemoval !== true) {
    throw new Error('Expected recent text link long-press ownership to suppress native chrome after DOM removal')
  }
  if (String(result.surfaceTextureBackgroundImage || '').includes('paper-texture')) {
    throw new Error(`Expected static paper backing to be color-only; observed ${result.surfaceTextureBackgroundImage || 'unset'}`)
  }
  if (result.movingPageTextureLayerPresent !== true) {
    throw new Error('Expected moving page paper texture layer to be present')
  }
  if (result.movingPageBorderLayerPresent !== true) {
    throw new Error('Expected moving page border overlay layer to be present')
  }
  if (result.movingPageTextureSlotPresent !== true) {
    throw new Error('Expected moving page paper texture current slot to be present')
  }
  if (result.movingPageBorderSlotPresent !== true) {
    throw new Error('Expected moving page border overlay current slot to be present')
  }
  if (result.movingPageTextureArtworkPresent !== true) {
    throw new Error('Expected moving page paper texture artwork to be present')
  }
  if (result.movingPageBorderArtworkPresent !== true) {
    throw new Error('Expected moving page border overlay artwork to be present')
  }
  if (!String(result.movingPageTextureArtworkBackgroundImage || '').includes('paper-texture')) {
    throw new Error(`Expected moving page paper texture artwork background image; observed ${result.movingPageTextureArtworkBackgroundImage || 'unset'}`)
  }
  if (!String(result.movingPageBorderArtworkBackgroundImage || '').includes('page-border-overlay')) {
    throw new Error(`Expected moving page border overlay artwork background image; observed ${result.movingPageBorderArtworkBackgroundImage || 'unset'}`)
  }
  if (String(result.documentTextureBackgroundImage || '').includes('paper-texture')) {
    throw new Error(`Expected no document-owned paper texture background image; observed ${result.documentTextureBackgroundImage || 'unset'}`)
  }
  if (String(result.documentTextureAsset || '').includes('paper-texture')) {
    throw new Error(`Expected no document paper texture asset dataset; observed ${result.documentTextureAsset || 'unset'}`)
  }
  const textureOpacity = numericCss(result.surfaceTextureOpacity)
  const movingTextureOpacity = numericCss(result.movingPageTextureOpacity)
  const borderOpacity = numericCss(result.movingPageBorderOpacity)
  if (textureOpacity == null || textureOpacity < 0.9) {
    throw new Error(`Expected opaque root paper backing texture; observed ${result.surfaceTextureOpacity || 'unset'}`)
  }
  if (movingTextureOpacity == null || movingTextureOpacity <= 0 || movingTextureOpacity > 0.7) {
    throw new Error(`Expected subtle moving page texture opacity; observed ${result.movingPageTextureOpacity || 'unset'}`)
  }
  if (borderOpacity == null || borderOpacity <= 0) {
    throw new Error(`Expected visible border overlay opacity; observed ${result.movingPageBorderOpacity || 'unset'}`)
  }
  if (!String(result.surfaceTextureAsset || '').includes('paper-texture')) {
    throw new Error(`Expected paper texture asset dataset to be populated; observed ${result.surfaceTextureAsset || 'unset'}`)
  }
  if (!String(result.surfaceBorderAsset || '').includes('page-border-overlay')) {
    throw new Error(`Expected border overlay asset dataset to be populated; observed ${result.surfaceBorderAsset || 'unset'}`)
  }
}
