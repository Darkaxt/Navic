export const readerHrefComparable = value => {
  const text = String(value || '').trim().split('#')[0].replace(/\\/g, '/')
  if (!text) return ''
  try {
    return decodeURIComponent(text).replace(/^\.?\//, '').replace(/^\/+/, '').toLowerCase()
  } catch (_) {
    return text.replace(/^\.?\//, '').replace(/^\/+/, '').toLowerCase()
  }
}

export const readerHrefMatches = (left, right) => {
  const first = readerHrefComparable(left)
  const second = readerHrefComparable(right)
  if (!first || !second) return false
  return first === second || first.endsWith(`/${second}`) || second.endsWith(`/${first}`)
}

export const readerHrefMatchesSection = (href, section) =>
  Boolean(section) && [
    section.href,
    section.id,
    section.url,
    section.name,
  ].some(candidate => readerHrefMatches(href, candidate))

export const stableHash = value => {
  const text = String(value || '')
  let hash = 2166136261
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}
