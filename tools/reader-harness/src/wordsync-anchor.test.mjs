import assert from 'node:assert/strict'
import test from 'node:test'
import {
  readerWordSyncAnchorOnPage,
  readerWordSyncNextAnchor,
  readerWordSyncPersistentStartByte,
} from '../../../composeApp/src/androidMain/assets/reader/navic-reader-wordsync-anchor.js'

const segment = (rawByteStart, rawByteEnd, rawProvenanceId = 'chapter-8') =>
  ({ rawProvenanceId, rawByteStart, rawByteEnd })
const page = (rawByteStart, rawByteEnd, rawProvenanceId = 'chapter-8') =>
  ({ rawProvenanceId, rawByteStart, rawByteEnd })

// Replays one page of playback: each painted segment either extends the anchor or re-anchors.
const playOnPage = (segments, visibleRanges) => {
  let anchor = null
  return segments.map((fragment, index) => {
    const startByte = readerWordSyncPersistentStartByte(anchor, fragment, visibleRanges[index], true)
    anchor = readerWordSyncNextAnchor(anchor, fragment, startByte, true)
    return startByte ?? fragment.rawByteStart
  })
}

test('keeps the page anchor when the committed range is reposted with late raw provenance', () => {
  // Open: segment 77 paints before raw fields reach the committed range; 78/79 paint after the repost.
  const painted = playOnPage(
    [segment(4692, 4707), segment(4708, 4760), segment(4761, 4820)],
    [{ visibleStart: 0, visibleEnd: 900 }, page(4692, 6100), page(4692, 6100)],
  )
  assert.deepEqual(painted, [4692, 4692, 4692])
})

test('keeps the page anchor across a relayout repost of the same page', () => {
  const painted = playOnPage(
    [segment(4692, 4707), segment(4708, 4760)],
    [page(4692, 6100), page(4688, 6120)],
  )
  assert.deepEqual(painted, [4692, 4692])
})

test('re-anchors after a forward page turn, a backward seek and another document', () => {
  const anchor = segment(4692, 4707)
  assert.equal(readerWordSyncPersistentStartByte(anchor, segment(6101, 6150), page(6100, 7400), true), null)
  assert.equal(readerWordSyncPersistentStartByte(anchor, segment(4600, 4690), page(4500, 6100), true), null)
  assert.equal(
    readerWordSyncPersistentStartByte(anchor, segment(4708, 4760, 'chapter-9'), page(4692, 6100, 'chapter-9'), true),
    null,
  )
  assert.equal(readerWordSyncAnchorOnPage(anchor, page(3000, 4692)), false)
})

test('keeps a segment that spans into the next page as that page anchor', () => {
  assert.equal(readerWordSyncAnchorOnPage(segment(6050, 6130), page(6100, 7400)), true)
})

test('non-persistent mode never anchors', () => {
  assert.equal(readerWordSyncPersistentStartByte(segment(1, 2), segment(3, 4), page(0, 10), false), null)
  assert.equal(readerWordSyncNextAnchor(segment(1, 2), segment(3, 4), null, false), null)
})
