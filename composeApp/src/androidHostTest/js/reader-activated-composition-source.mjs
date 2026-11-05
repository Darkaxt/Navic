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
    assert.ok(/frame\s*=\s*viewerContainer\.createInstalledFramePort\(\)/.test(host) &&
        /fun createInstalledFramePort\(\)[\s\S]*?override fun prepareTarget[\s\S]*?nativePagePresentationPublisher\.prepareTarget\(command, onFact\)[\s\S]*?override fun present[\s\S]*?nativePagePresentationPublisher\.present\(command, onFact\)/.test(host),
        'Native must wire actual command-only target preparation and presentation to its original publisher')
    assert.ok(/ReaderOwnerAndInputPublicationBarrier\([\s\S]*?narrowOrVeto = inputSafety::narrowOrVeto,[\s\S]*?publishAtomically = viewerContainer::canPublishInstalledOwnerAndInput\)/.test(host) &&
        /ownerAndInput = object : ReaderOwnerAndInputPublicationPort[\s\S]*?override fun publish\(command: ReaderTransitionCommand\.CommitOwnerAndInputLease\) = publication\.publish\(command\)[\s\S]*?override fun publish\(command: ReaderTransitionCommand\.PublishRetainedOwnerAndInputLease\) = publication\.publish\(command\)/.test(host),
        'Both installed publication commands must reach the original atomic owner/input barrier')
})
