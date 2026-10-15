import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

// Structural cutover guards only. These do not establish compilation, physical
// readiness, installed runtime behavior or emulator acceptance.
const root = new URL('../../androidMain/kotlin/paige/navic/ui/screens/reader/', import.meta.url)
const source = name => readFileSync(new URL(name, root), 'utf8')
const host = source('KomikkuReaderNativeFrameHost.android.kt')

test('Native composes the complete private production package and constructs its existing coordinator', () => {
    assert.ok(/ReaderProductionActivatedSessionPorts\.mint\s*\(/.test(host),
        'Actual Native composition must mint all twelve production consumers')
    assert.ok(/ReaderActivatedTransitionPorts\s*\(/.test(host),
        'The production package must reach the existing runtime coordinator')
    assert.ok(/ReaderResumableTransitionCoordinator\s*\(/.test(host))
    assert.ok(/ReaderTransitionMode\.Active/.test(host))
})

test('Installed commands reach original preparation, Curl and frame consumers rather than fixture ports', () => {
    assert.ok(/fun prepareInstalled\s*\(/.test(source('ReaderPageRasterPreparationController.android.kt')),
        'Original preparation must consume installed commands, not only own synthetic physical starts')
    assert.ok(/fun reserveInstalledDeck\s*\(/.test(source('ReaderPlayLikeCurlFoliateController.android.kt')),
        'Original Curl must submit the reserved material generation')
    assert.ok(/fun createOriginalActivatedFramePort\s*\(/.test(host),
        'Native must publish and prove the actual command-only frame')
    assert.ok(/fun createOriginalActivatedOwnerAndInputPort\s*\(/.test(host),
        'Owner and physical input must be acknowledged atomically at the original host')
})
