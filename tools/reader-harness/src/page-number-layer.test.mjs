import assert from 'node:assert/strict'
import test from 'node:test'

// Minimal DOM: helpers.js only needs element creation and inline styles for this renderer.
class FakeElement {
  constructor(tag) {
    this.tagName = tag
    this.children = []
    this.attributes = {}
    this.dataset = {}
    this.textContent = ''
    const styles = {}
    this.style = {
      values: styles,
      setProperty: (name, value) => { styles[name] = value },
      getPropertyValue: name => styles[name] ?? '',
      removeProperty: name => { delete styles[name] },
    }
  }
  setAttribute(name, value) { this.attributes[name] = String(value) }
  getAttribute(name) { return this.attributes[name] ?? null }
  append(child) { this.children.push(child) }
  replaceChildren() { this.children = [] }
  querySelector() { return null }
  querySelectorAll() { return [] }
  addEventListener() {}
}
globalThis.document ??= {
  body: new FakeElement('body'),
  documentElement: new FakeElement('html'),
  createElement: tag => new FakeElement(tag),
  querySelector: () => null,
  addEventListener() {},
}
globalThis.window ??= globalThis
globalThis.getComputedStyle ??= () => ({ getPropertyValue: () => '' })

const { applyReaderPageNumberLayer } = await import(
  new URL('../../../composeApp/src/androidMain/assets/reader/navic-reader-helpers.js', import.meta.url)
)

const presentation = overrides => ({
  spreadSlots: true,
  labels: [
    { slot: 'left', text: '13 / 448' },
    { slot: 'right', text: '14 / 448' },
  ],
  pageCount: 224,
  fontFamily: 'Roboto, sans-serif',
  fontWeight: 400,
  letterSpacing: 0,
  wordSpacing: 0,
  blendMode: 'multiply',
  color: 'color-mix(in srgb, #2b2118 58%, transparent)',
  fontSize: '13.12px',
  bottom: '18px',
  ...overrides,
})

test('passive raster spread presentation renders both destination labels in their slots (#31)', () => {
  const layer = new FakeElement('div')
  applyReaderPageNumberLayer(layer, presentation())
  assert.deepEqual(
    layer.children.map(slot => [slot.getAttribute('data-navic-page-number-slot'), slot.textContent, slot.style.values.left]),
    [['left', '13 / 448', '25%'], ['right', '14 / 448', '75%']],
  )
  assert.equal(layer.dataset.navicPageNumberTotal, '224')
  const style = layer.style.values
  assert.equal(style.position, 'fixed')
  assert.equal(style.bottom, '18px')
  assert.equal(style['font-size'], '13.12px')
  assert.equal(style['font-family'], 'Roboto, sans-serif')
  assert.equal(style.color, 'color-mix(in srgb, #2b2118 58%, transparent)')
  assert.equal(style.opacity, '0.82')
  assert.equal(style['mix-blend-mode'], 'multiply')
})

test('re-rendering replaces the previous labels instead of accumulating them', () => {
  const layer = new FakeElement('div')
  applyReaderPageNumberLayer(layer, presentation())
  applyReaderPageNumberLayer(layer, presentation({ labels: [{ slot: 'left', text: '15 / 448' }, { slot: 'right', text: '16 / 448' }] }))
  assert.deepEqual(layer.children.map(slot => slot.textContent), ['15 / 448', '16 / 448'])
})

test('single-page presentation keeps the centred label', () => {
  const layer = new FakeElement('div')
  applyReaderPageNumberLayer(layer, presentation({ spreadSlots: false, labels: [{ slot: 'center', text: '7 / 223' }], pageCount: 223 }))
  assert.equal(layer.textContent, '7 / 223')
  assert.equal(layer.style.values.left, '50%')
  assert.equal(layer.style.values.transform, 'translateX(-50%)')
})

test('passive capture targets carry and render the destination labels (#31)', async () => {
  const { readFile } = await import('node:fs/promises')
  const asset = path => readFile(new URL(`../../../composeApp/src/androidMain/assets/reader/${path}`, import.meta.url), 'utf8')
  const pageTurns = await asset('navic-reader-page-turns.js')
  const target = pageTurns.slice(pageTurns.indexOf('const opaqueCaptureTarget = JSON.stringify({'))
  assert.match(target.slice(0, target.indexOf('})')), /pageNumber: this\.readerPageNumberRasterPresentation\(ordinal, descriptor\.pageCount\)/)
  const session = await asset('passive-raster-prototype/production-raster-foliate-session.js')
  assert.match(session, /applyReaderPageNumberLayer\(ensureReaderPageNumberLayer\(\), presentation\)/)
  const profile = session.slice(session.indexOf('const applyPassiveProfile = async'))
  assert.ok(
    profile.indexOf('await renderPassivePageNumbers(target, settings)') >= 0 &&
      profile.indexOf('await renderPassivePageNumbers(target, settings)') < profile.indexOf('waitForReaderRasterAssets(document)'),
    'labels must be rendered before the raster asset wait and capture',
  )
})
