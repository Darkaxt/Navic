const ReaderFlowPagedVertical = 'paged-vertical'
const ReaderDirectionRtl = 'rtl'

export const readerMotionAxis = flowMode =>
  flowMode === ReaderFlowPagedVertical ? 'vertical' : 'horizontal'

export const readerMotionDelta = ({
  deltaX,
  deltaY,
  flowMode,
} = {}) => {
  const x = Number(deltaX)
  const y = Number(deltaY)
  return readerMotionAxis(flowMode) === 'vertical'
    ? { x: 0, y: Number.isFinite(y) ? y : 0 }
    : { x: Number.isFinite(x) ? x : 0, y: 0 }
}

export const readerPageDragPreviewMotion = ({
  deltaX,
  deltaY,
  lastDeltaX,
  lastDeltaY,
  flowMode,
} = {}) => {
  const current = readerMotionDelta({ deltaX, deltaY, flowMode })
  const previous = readerMotionDelta({
    deltaX: lastDeltaX,
    deltaY: lastDeltaY,
    flowMode,
  })
  return {
    axis: readerMotionAxis(flowMode),
    current,
    previous,
    incrementalDelta: {
      x: current.x - previous.x,
      y: current.y - previous.y,
    },
  }
}

export const readerPaperTextureDragDirection = ({
  deltaX,
  deltaY,
  flowMode,
  readerDirection,
  threshold = 24,
} = {}) => {
  const x = Number(deltaX)
  const y = Number(deltaY)
  const min = Math.max(1, Number(threshold) || 24)
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null
  if (flowMode === ReaderFlowPagedVertical) {
    if (Math.abs(y) < min || Math.abs(y) <= Math.abs(x)) return null
    return y < 0 ? 'next' : 'previous'
  }
  if (Math.abs(x) < min || Math.abs(x) <= Math.abs(y)) return null
  const rtl = readerDirection === ReaderDirectionRtl
  if (x < 0) return rtl ? 'previous' : 'next'
  return rtl ? 'next' : 'previous'
}

export const readerPageTurnDirection = direction =>
  direction === 'next' || direction === 'previous' ? direction : null

export const readerSurfacePaperTextureScrollOffset = ({
  position,
  baseOffset,
  viewportWidth,
  viewportHeight,
  rendererPageSize,
  flowMode,
  pageTurnDirection,
  fallbackPageTurnDirection,
} = {}) => {
  const currentPosition = Number(position)
  const basePosition = Number(baseOffset)
  if (!Number.isFinite(currentPosition) || !Number.isFinite(basePosition)) return { x: 0, y: 0 }
  const width = Number(viewportWidth)
  const height = Number(viewportHeight)
  const maxOffset = Math.max(
    1,
    flowMode === ReaderFlowPagedVertical
      ? (Number.isFinite(height) ? height : 0)
      : (Number.isFinite(width) ? width : 0)
  )
  const pageSize = Number(rendererPageSize)
  const deltaScale = Number.isFinite(pageSize) && pageSize > 1
    ? maxOffset / pageSize
    : 1
  const delta = (currentPosition - basePosition) * deltaScale
  const explicitDirection = readerPageTurnDirection(pageTurnDirection)
  const fallbackDirection = readerPageTurnDirection(fallbackPageTurnDirection)
  const directionlessBoundaryThreshold = Math.max(1, maxOffset * 0.75)
  const directionlessBoundaryLikeDelta = Math.abs(delta) >= directionlessBoundaryThreshold
  const effectiveDirection = explicitDirection || (directionlessBoundaryLikeDelta ? fallbackDirection : null)
  const hasKnownDirection = Boolean(effectiveDirection)
  const expectedDirectionSign = effectiveDirection === 'next' ? 1 : -1
  const wrapsDirectionlessBoundary = !hasKnownDirection && directionlessBoundaryLikeDelta
  const bounded = wrapsDirectionlessBoundary
    ? 0
    : Math.max(-maxOffset, Math.min(maxOffset, delta))
  const signedOffset = hasKnownDirection
    ? expectedDirectionSign * Math.min(maxOffset, Math.abs(delta))
    : bounded
  return flowMode === ReaderFlowPagedVertical
    ? { x: 0, y: -signedOffset }
    : { x: -signedOffset, y: 0 }
}
