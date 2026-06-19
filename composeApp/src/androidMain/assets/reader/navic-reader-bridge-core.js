export const readerRoot = document.body
export const overlayClass = 'navic-active-overlay-fragment'

export const log = (label, ...details) => console.debug('[NavicReader]', label, ...details)
export const logError = (label, ...details) => console.error('[NavicReader]', label, ...details)

export const readerTraceValue = (value, depth = 0) => {
  if (value === null || value === undefined) return value
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value
  if (depth >= 2) return String(value)
  if (Array.isArray(value)) return value.slice(0, 12).map(item => readerTraceValue(item, depth + 1))
  if (typeof value === 'object') {
    const result = {}
    for (const [key, entry] of Object.entries(value).slice(0, 24)) {
      if (typeof entry === 'function') continue
      result[key] = readerTraceValue(entry, depth + 1)
    }
    return result
  }
  return String(value)
}

export const readerTrace = (type, payload = {}) => {
  const trace = window.__navicReaderTrace
  if (!trace || typeof trace.push !== 'function') return
  trace.push({
    type,
    timestamp: Date.now(),
    payload: readerTraceValue(payload),
  })
}

export const readerLocationPostKey = message => [
  message?.href || '',
  message?.cfi || '',
  Number.isFinite(message?.pageIndex) ? message.pageIndex : '',
  Number.isFinite(message?.pageCount) ? message.pageCount : '',
  message?.tocTitle || '',
].join('|')

export const describeUrl = url => {
  try {
    const parsed = new URL(url)
    const fileName = parsed.pathname.split('/').filter(Boolean).pop() || ''
    return `${parsed.protocol}${fileName}`
  } catch {
    return typeof url === 'string' ? url.slice(0, 80) : typeof url
  }
}

export const post = message => {
  const json = JSON.stringify(message)
  log('post', message.type, message.code || '')
  if (window.NavicAndroidBridge?.postMessage) {
    window.NavicAndroidBridge.postMessage(json)
  } else {
    log('bridge-unavailable', message)
  }
}

export const reportError = (error, code = 'reader_error') => {
  const message = error?.message || String(error)
  logError('reportError', code, message, error?.stack || error)
  readerRoot.replaceChildren(errorElement(message))
  post({ type: 'error', code, message })
}

export const errorElement = message => {
  const element = document.createElement('div')
  element.className = 'reader-error'
  element.textContent = message
  return element
}
