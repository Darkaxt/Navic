import assert from 'node:assert/strict'
import test from 'node:test'

// paginator.js defines a custom element at module scope; stub the DOM globals it needs.
globalThis.HTMLElement ??= class {}
globalThis.customElements ??= { define() {} }
globalThis.NodeFilter ??= { SHOW_ELEMENT: 1, SHOW_TEXT: 4, SHOW_CDATA_SECTION: 8, FILTER_ACCEPT: 1, FILTER_REJECT: 2, FILTER_SKIP: 3 }

const { anchorClientRect } = await import(
  new URL('../../../composeApp/src/androidMain/assets/reader/vendor/foliate-js/paginator.js', import.meta.url)
)

const rect = (left, width = 200, height = 300) => ({ left, right: left + width, top: 0, bottom: height, width, height })
const element = (...rects) => ({ nodeType: 1, getClientRects: () => rects })
const range = ({ start, end, rects = [] }) => ({
  collapsed: false,
  startContainer: start,
  startOffset: 0,
  endContainer: end,
  endOffset: 0,
  getClientRects: () => rects,
})

test('image-only section range (wrapper,0)-(img,0) without client rects scrolls to its boundary element (#49)', () => {
  const wrapper = element(rect(2560))
  const img = element(rect(2600))
  assert.deepEqual(anchorClientRect(range({ start: wrapper, end: img })), rect(2560))
})

test('boundary fallback skips zero-size boundaries and uses a text node parent', () => {
  const hidden = element(rect(0, 0, 0))
  const parent = element(rect(5120))
  const text = { nodeType: 3, parentElement: parent }
  assert.deepEqual(anchorClientRect(range({ start: hidden, end: text })), rect(5120))
})

test('text range keeps its own first visible rect', () => {
  const wrapper = element(rect(0))
  const own = rect(2600, 120, 20)
  assert.deepEqual(anchorClientRect(range({ start: wrapper, end: wrapper, rects: [rect(2560, 0, 20), own] })), own)
})

test('range with no rect anywhere stays unscrollable, fraction anchors are not ranges', () => {
  assert.equal(anchorClientRect(range({ start: element(), end: element() })), null)
  assert.equal(anchorClientRect(0.25), undefined)
  assert.equal(anchorClientRect(null), undefined)
})
