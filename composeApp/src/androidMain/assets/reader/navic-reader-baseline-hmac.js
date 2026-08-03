const ReaderBaselineExcludedElements = new Set([
  'script',
  'style',
  'template',
  'noscript',
  'nav',
  'header',
  'footer',
  'aside',
])

const ReaderBaselineExcludedRoles = new Set([
  'banner',
  'complementary',
  'contentinfo',
  'doc-endnote',
  'doc-footnote',
  'navigation',
])

const ReaderBaselineExcludedSemantics = new Set([
  'acknowledgments',
  'colophon',
  'cover',
  'cover-image',
  'copyright-page',
  'dedication',
  'endnote',
  'endnotes',
  'footnote',
  'halftitlepage',
  'imprint',
  'landmarks',
  'noteref',
  'page-list',
  'titlepage',
  'toc',
])

const ReaderBaselineMinimumNonWhitespaceCodePoints = 64
const ReaderBaselineHmacAlgorithm = Object.freeze({ name: 'HMAC', hash: 'SHA-256' })
const ReaderBaselineTextDomain = 'navic-reader-page-plain-text-v1\u0000'
const ReaderBaselineLocatorDomain = 'navic-reader-page-locator-v1\u0000'

const readerTokenSet = value => {
  if (value == null) return new Set()
  const values = typeof value === 'string'
    ? [value]
    : Array.isArray(value) || value instanceof Set
      ? Array.from(value)
      : typeof value === 'object'
        ? Object.entries(value).filter(([, enabled]) => enabled).map(([key]) => key)
        : [value]
  return new Set(values
    .flatMap(candidate => String(candidate || '').toLowerCase().split(/\s+/u))
    .filter(Boolean))
}

const readerElementSemantics = element => {
  const values = [
    element?.getAttribute?.('epub:type'),
    element?.getAttributeNS?.('http://www.idpf.org/2007/ops', 'type'),
    element?.getAttribute?.('type'),
  ]
  return new Set(values.flatMap(value => Array.from(readerTokenSet(value))))
}

const readerElementIsAuthoredHidden = element => {
  if (!element) return false
  if (element.hasAttribute?.('hidden')) return true
  if (String(element.getAttribute?.('aria-hidden') || '').trim().toLowerCase() === 'true') return true
  const inlineStyle = element.style
  if (inlineStyle?.display === 'none') return true
  if (inlineStyle?.visibility === 'hidden' || inlineStyle?.visibility === 'collapse') return true
  const view = element.ownerDocument?.defaultView
  if (!view?.getComputedStyle) return false
  try {
    const style = view.getComputedStyle(element)
    return style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse'
  } catch (_) {
    return true
  }
}

const readerSourceTextNodeIsEligible = node => {
  for (let element = node?.parentElement; element; element = element.parentElement) {
    if (ReaderBaselineExcludedElements.has(String(element.localName || '').toLowerCase())) return false
    if (ReaderBaselineExcludedRoles.has(String(element.getAttribute?.('role') || '').trim().toLowerCase())) return false
    if (Array.from(readerElementSemantics(element)).some(value => ReaderBaselineExcludedSemantics.has(value))) {
      return false
    }
    if (readerElementIsAuthoredHidden(element)) return false
  }
  return true
}

const readerRangeDocument = range => {
  const start = range?.startContainer
  const end = range?.endContainer
  const startDocument = start?.nodeType === 9 ? start : start?.ownerDocument
  const endDocument = end?.nodeType === 9 ? end : end?.ownerDocument
  return startDocument && startDocument === endDocument ? startDocument : null
}

const readerSectionIsExcluded = ({ section, cover = false, fixedLayout = false } = {}) => {
  if (fixedLayout || cover || section?.linear === 'no') return true
  const semantics = new Set([
    ...readerTokenSet(section?.epubType),
    ...readerTokenSet(section?.type),
    ...readerTokenSet(section?.properties),
    ...readerTokenSet(section?.rel),
  ])
  return Array.from(semantics).some(value => ReaderBaselineExcludedSemantics.has(value))
}

const readerHmacBytesEqual = (left, right) => {
  if (!(left instanceof Uint8Array) || !(right instanceof Uint8Array) || left.length !== right.length) {
    return false
  }
  let difference = 0
  for (let index = 0; index < left.length; index += 1) difference |= left[index] ^ right[index]
  return difference === 0
}

const readerFingerprintablePlainText = text => {
  if (typeof text !== 'string' || !text) return null
  let nonWhitespaceCodePoints = 0
  for (const character of text) {
    if (!/\s/u.test(character)) nonWhitespaceCodePoints += 1
  }
  return nonWhitespaceCodePoints >= ReaderBaselineMinimumNonWhitespaceCodePoints ? text : null
}

export const normalizeReaderBaselinePlainText = value =>
  String(value || '').normalize('NFC').replace(/\s+/gu, ' ').trim()

export const captureReaderSourceTextBaseline = doc => {
  if (!doc?.createTreeWalker) return null
  const NodeFilterClass = doc.defaultView?.NodeFilter || globalThis.NodeFilter
  if (!NodeFilterClass) return null
  const entries = []
  const walker = doc.createTreeWalker(doc, NodeFilterClass.SHOW_TEXT)
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (!readerSourceTextNodeIsEligible(node)) continue
    entries.push(Object.freeze({ node, sourceText: String(node.data || '') }))
  }
  return Object.freeze({
    document: doc,
    entries: Object.freeze(entries),
  })
}

export const projectReaderBaselinePlainText = (baseline, range) => {
  const doc = readerRangeDocument(range)
  if (!baseline || baseline.document !== doc || range?.collapsed) return null
  if (baseline.entries.some(entry => entry.node.data !== entry.sourceText)) return null
  const parts = []
  try {
    for (const entry of baseline.entries) {
      if (!range.intersectsNode(entry.node)) continue
      const start = range.startContainer === entry.node ? range.startOffset : 0
      const end = range.endContainer === entry.node ? range.endOffset : entry.sourceText.length
      if (
        !Number.isInteger(start) ||
        !Number.isInteger(end) ||
        start < 0 ||
        end > entry.sourceText.length ||
        end <= start
      ) continue
      parts.push(entry.sourceText.slice(start, end))
    }
  } catch (_) {
    return null
  }
  return normalizeReaderBaselinePlainText(parts.join('')) || null
}

export class ReaderDuplicatePageFingerprintDiagnostics {
  constructor({ cryptoProvider = globalThis.crypto, postEvent = () => false } = {}) {
    this.cryptoProvider = cryptoProvider
    this.postEvent = postEvent
    this.sessionGeneration = 0
    this.commitGeneration = 0
    this.keyPromise = null
    this.baselines = new WeakMap()
    this.records = new Map()
    this.reportedPairs = new Set()
  }

  beginSession() {
    this.sessionGeneration += 1
    this.commitGeneration = 0
    this.baselines = new WeakMap()
    this.records.clear()
    this.reportedPairs.clear()
    const subtle = this.cryptoProvider?.subtle
    this.keyPromise = subtle?.generateKey
      ? subtle.generateKey(ReaderBaselineHmacAlgorithm, false, ['sign']).catch(() => null)
      : Promise.resolve(null)
  }

  endSession() {
    this.sessionGeneration += 1
    this.commitGeneration += 1
    this.keyPromise = null
    this.baselines = new WeakMap()
    this.records.clear()
    this.reportedPairs.clear()
  }

  captureDocument(doc, options = {}) {
    if (!this.keyPromise || !doc || readerSectionIsExcluded(options)) return false
    if (this.baselines.has(doc)) return true
    const baseline = captureReaderSourceTextBaseline(doc)
    if (!baseline?.entries.length) return false
    this.baselines.set(doc, baseline)
    return true
  }

  async compareCommittedPage({ range, pageOrdinal, locator } = {}) {
    const sessionGeneration = this.sessionGeneration
    const commitGeneration = ++this.commitGeneration
    const doc = readerRangeDocument(range)
    const baseline = doc ? this.baselines.get(doc) : null
    const normalizedLocator = typeof locator === 'string' ? locator.trim() : ''
    if (
      !baseline ||
      !Number.isInteger(pageOrdinal) ||
      pageOrdinal <= 0 ||
      !normalizedLocator ||
      !this.keyPromise
    ) return false
    const plainText = readerFingerprintablePlainText(
      projectReaderBaselinePlainText(baseline, range)
    )
    if (!plainText) return false

    const key = await this.keyPromise
    if (
      !key ||
      sessionGeneration !== this.sessionGeneration ||
      commitGeneration !== this.commitGeneration
    ) return false

    let textHmac
    let locatorHmac
    try {
      const encoder = new TextEncoder()
      const [textSignature, locatorSignature] = await Promise.all([
        this.cryptoProvider.subtle.sign(
          ReaderBaselineHmacAlgorithm.name,
          key,
          encoder.encode(ReaderBaselineTextDomain + plainText)
        ),
        this.cryptoProvider.subtle.sign(
          ReaderBaselineHmacAlgorithm.name,
          key,
          encoder.encode(ReaderBaselineLocatorDomain + normalizedLocator)
        ),
      ])
      textHmac = new Uint8Array(textSignature)
      locatorHmac = new Uint8Array(locatorSignature)
    } catch (_) {
      return false
    }
    if (
      sessionGeneration !== this.sessionGeneration ||
      commitGeneration !== this.commitGeneration
    ) return false

    const duplicate = Array.from(this.records.entries())
      .filter(([ordinal, record]) => ordinal !== pageOrdinal && readerHmacBytesEqual(record.textHmac, textHmac))
      .sort(([left], [right]) => Math.abs(left - pageOrdinal) - Math.abs(right - pageOrdinal))[0]
    this.records.set(pageOrdinal, { textHmac, locatorHmac })
    if (!duplicate) return true

    const [previousPageOrdinal, previousRecord] = duplicate
    const pairKey = [pageOrdinal, previousPageOrdinal].sort((left, right) => left - right).join(':')
    if (this.reportedPairs.has(pairKey)) return true
    this.reportedPairs.add(pairKey)
    this.postEvent({
      type: 'duplicatePageSuspected',
      currentPageOrdinal: pageOrdinal,
      previousPageOrdinal,
      plainTextSame: true,
      locatorSame: readerHmacBytesEqual(previousRecord.locatorHmac, locatorHmac),
    })
    return true
  }
}
