import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import test from 'node:test'

const moduleUrl = new URL('../../androidMain/assets/reader/navic-reader-open-diagnostics.js', import.meta.url)
const phases = [
  'open-view-started', 'open-view-completed',
  'open-cover-started', 'open-cover-completed',
  'open-navigation-started', 'open-navigation-completed',
  'open-pagination-started', 'open-pagination-completed',
]

test('open diagnostics emit only fixed original-boundary labels', async () => {
  assert.ok(existsSync(moduleUrl), 'Original open boundaries need failure-independent closed observations')
  const { readerObserveOpenPhase } = await import(moduleUrl.href)
  const original = console.log
  const records = []
  try {
    console.log = (...args) => records.push(args)
    for (const phase of phases) readerObserveOpenPhase(phase)
    for (const invalid of [null, '', 'secret.epub', 'open-view-started url=private', {}, Symbol('private')]) {
      readerObserveOpenPhase(invalid)
    }
  } finally { console.log = original }
  assert.deepEqual(records, phases.map(phase => ['[NavicReader]', phase]))
})

test('throwing console observation cannot reject or replace original work', async () => {
  assert.ok(existsSync(moduleUrl), 'Original open boundaries need failure-independent closed observations')
  const { readerObserveOpenPhase } = await import(moduleUrl.href)
  const original = console.log
  try {
    console.log = () => { throw new Error('diagnostic-only-failure') }
    for (const phase of phases) assert.doesNotThrow(() => readerObserveOpenPhase(phase))
  } finally { console.log = original }
})
