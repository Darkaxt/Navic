export const ReaderPageTurnPresentationScopePreview = 'preview'
export const ReaderPageTurnPresentationScopeLive = 'live'

const PreviewReceiptKeys = Object.freeze([
  'scope',
  'token',
  'pageIndex',
  'previewGeneration',
  'foregroundMutationGeneration',
  'presentationSequence',
])
const LiveReceiptKeys = Object.freeze([
  'scope',
  'token',
  'pageIndex',
  'foliateSessionId',
  'rasterGeneration',
  'textureGeneration',
  'foregroundMutationGeneration',
  'presentationSequence',
])

const isNonNegativeInteger = value => Number.isSafeInteger(value) && value >= 0
const isPositiveInteger = value => Number.isSafeInteger(value) && value > 0
const isNonEmptyString = value => typeof value === 'string' && value.length > 0

const hasExactKeys = (value, expectedKeys) => {
  const keys = Object.keys(value)
  return keys.length === expectedKeys.length && expectedKeys.every(key => keys.includes(key))
}

const parsedReceiptValue = value => {
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

export function parseReaderPageTurnPresentationReceipt(value) {
  const candidate = parsedReceiptValue(value)
  if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) return null
  if (!isNonEmptyString(candidate.token) || !isNonNegativeInteger(candidate.pageIndex)) return null
  if (!isPositiveInteger(candidate.presentationSequence)) return null
  if (!isPositiveInteger(candidate.foregroundMutationGeneration)) return null

  if (candidate.scope === ReaderPageTurnPresentationScopePreview) {
    if (!hasExactKeys(candidate, PreviewReceiptKeys)) return null
    if (!isNonNegativeInteger(candidate.previewGeneration)) return null
    return Object.freeze({
      scope: candidate.scope,
      token: candidate.token,
      pageIndex: candidate.pageIndex,
      previewGeneration: candidate.previewGeneration,
      foregroundMutationGeneration: candidate.foregroundMutationGeneration,
      presentationSequence: candidate.presentationSequence,
    })
  }

  if (candidate.scope === ReaderPageTurnPresentationScopeLive) {
    if (!hasExactKeys(candidate, LiveReceiptKeys)) return null
    if (
      !isNonEmptyString(candidate.foliateSessionId) ||
      !isNonNegativeInteger(candidate.rasterGeneration) ||
      !isNonNegativeInteger(candidate.textureGeneration)
    ) return null
    return Object.freeze({
      scope: candidate.scope,
      token: candidate.token,
      pageIndex: candidate.pageIndex,
      foliateSessionId: candidate.foliateSessionId,
      rasterGeneration: candidate.rasterGeneration,
      textureGeneration: candidate.textureGeneration,
      foregroundMutationGeneration: candidate.foregroundMutationGeneration,
      presentationSequence: candidate.presentationSequence,
    })
  }

  return null
}

export function issueReaderPageTurnPresentationReceipt(target, previousPresentationSequence = 0) {
  if (!isNonNegativeInteger(previousPresentationSequence) || previousPresentationSequence >= Number.MAX_SAFE_INTEGER) {
    throw new RangeError('Presentation sequence cannot be advanced')
  }
  const receipt = parseReaderPageTurnPresentationReceipt({
    ...target,
    presentationSequence: previousPresentationSequence + 1,
  })
  if (!receipt) throw new TypeError('Invalid page-turn presentation target')
  return receipt
}

export function readerPageTurnPresentationReceiptMatches(receipt, target) {
  const parsed = parseReaderPageTurnPresentationReceipt(receipt)
  if (!parsed || !target || typeof target !== 'object') return false
  if (
    parsed.scope !== target.scope ||
    parsed.token !== target.token ||
    parsed.pageIndex !== target.pageIndex ||
    parsed.foregroundMutationGeneration !== target.foregroundMutationGeneration
  ) return false

  if (parsed.scope === ReaderPageTurnPresentationScopePreview) {
    return hasExactKeys(target, PreviewReceiptKeys.filter(key => key !== 'presentationSequence')) &&
      parsed.previewGeneration === target.previewGeneration
  }

  return hasExactKeys(target, LiveReceiptKeys.filter(key => key !== 'presentationSequence')) &&
    parsed.foliateSessionId === target.foliateSessionId &&
    parsed.rasterGeneration === target.rasterGeneration &&
    parsed.textureGeneration === target.textureGeneration
}
