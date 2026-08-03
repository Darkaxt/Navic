const readerNextAnimationFrame = () =>
  new Promise(resolve => requestAnimationFrame(resolve))

const readerTextPagePosition = value => {
  const index = Number(value?.index)
  const pageIndex = Number(value?.pageIndex)
  const pageCount = Number(value?.pageCount)
  if (
    !Number.isInteger(index) ||
    !Number.isInteger(pageIndex) ||
    !Number.isInteger(pageCount) ||
    index < 0 ||
    pageIndex < 0 ||
    pageCount <= 0
  ) return null
  return Object.freeze({ index, pageIndex, pageCount })
}

export const readerExactTextPagePosition = renderer => {
  if (!renderer) return null
  try {
    const exact = readerTextPagePosition(
      renderer.exactTextPagePosition?.()
    )
    if (exact) return exact

    const content = renderer.getContents?.()?.[0]
    return readerTextPagePosition({
      index: content?.index,
      pageIndex: Number(renderer.page) - 1,
      pageCount: Number(renderer.pages) - 2,
    })
  } catch (_) {
    return null
  }
}

export async function readerWaitForStableTextPagePosition(
  renderer,
  {
    isCurrent = () => true,
    nextFrame = readerNextAnimationFrame,
    requiredStableSamples = 2,
    maxSamples = 8,
  } = {}
) {
  if (!renderer) return null

  try {
    const fontsReady = renderer.getContents?.()?.[0]
      ?.doc?.fonts?.ready
    if (fontsReady?.then) await fontsReady
  } catch (_) {
    // A failed publication font must not prevent fallback-font pagination.
  }

  if (!isCurrent()) return null
  renderer.render?.()

  const required = Math.max(
    1,
    Math.floor(Number(requiredStableSamples) || 1)
  )
  const limit = Math.max(
    required,
    Math.floor(Number(maxSamples) || required)
  )
  let previousKey = ''
  let stableSamples = 0

  for (let sample = 0; sample < limit; sample += 1) {
    await nextFrame()
    if (!isCurrent()) return null

    const position = readerExactTextPagePosition(renderer)
    if (!position) {
      previousKey = ''
      stableSamples = 0
      continue
    }

    const key = [
      position.index,
      position.pageIndex,
      position.pageCount,
    ].join(':')
    stableSamples = key === previousKey
      ? stableSamples + 1
      : 1
    previousKey = key
    if (stableSamples >= required) return position
  }

  return null
}
