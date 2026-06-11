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
