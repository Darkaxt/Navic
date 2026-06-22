# Whispersync Implementation Review — Potential Flaws

- **Date:** 2026-06-22
- **Scope:** Static code review of the Whispersync feature against the design intent.
- **Reference:** extends `docs/superpowers/specs/2026-06-18-whispersync-design.md`
- **Status:** eta81 hardening addressed findings 1-4 with focused regression coverage; findings 5-7 remain lower-priority follow-up/watch items.

## Method

Read the core implementation paths and integration points and reasoned about
correctness/robustness:

- Models & parser: `composeApp/.../reader/WhispersyncModels.kt`
- Sync state machine: `composeApp/.../reader/ReaderWhispersyncSyncCoordinator.kt`
- Playback control / seek-target matching: `composeApp/.../reader/ReaderWhispersyncPlaybackPolicy.kt`
- Reader controller wiring: `composeApp/.../reader/ReaderController.kt` (whispersync section)
- Sidecar fetch / decode / caching: `composeApp/.../domain/repositories/BinderyRepository.kt`
- Resume / companion-progress policy: `composeApp/.../ui/screens/bindery/BinderyAudiobookPlayerPolicy.kt`

This is a static, code-level review. Per the design spec, on-device validation of
the two-way sync loop remains the separate gating item and is **not** reassessed here.

## Summary

The architecture is sound where it is riskiest:

- The audio↔page **feedback loop is correctly suppressed** — audio-driven page
  moves emit `visibleTextRange(source=media-overlay-follow)`, which the controller
  stores without re-seeking (`ReaderController.kt:808`, `:1321`).
- Engine commands are **de-duplicated** via an incrementing `engineCommandKey`
  (`ReaderController.kt:668-669`, `ReaderWhispersyncSyncCoordinator.kt:215-225`).
- Resume precedence (companion vs direct audiobook progress) picks the newest by
  `updatedAtMs` with strict `>`, so ties favor direct progress
  (`BinderyAudiobookPlayerPolicy.kt:284`).
- Sidecar decode failures degrade to `LoadFailed` (wrapped in `runCatching`),
  never an app crash (`BinderyRepository.kt:423/444/482`).

The flaws cluster in two areas: **timeline parsing / matching tolerance**, and
the **"Mismatch" status taxonomy**.

## eta81 Resolution Notes

The eta81 release hardening addressed the release-blocking findings before public packaging:

- Range-less text segments no longer produce arbitrary reader-to-audio seek targets.
- Timeline gaps now surface a neutral `NoActiveCue` status instead of a repairable mismatch.
- Sidecar numeric parsing skips malformed segment fields safely and accepts fractional millisecond values.
- Audio resource matching now prefers exact normalized resource candidates and explicit track indexes; it no longer cross-matches unrelated tracks by suffix-only basename.
- Regression coverage was added in `WhispersyncTimelineParserTest`, `ReaderWhispersyncSyncCoordinatorTest`, and `ReaderWhispersyncPlaybackPolicyTest`.

## Findings (ranked by severity)

### 1. `seekTargetForVisibleTextRange` falls back to range-less segments with no positional basis — Medium-High

**Location:** `WhispersyncModels.kt:162-184` (esp. `:169-174`), consumed at `:63-75`.

**Issue:** `overlapScore()` returns `WhispersyncRangeScore(overlap = 0, centerDistance = Double.MAX_VALUE)`
for segments that lack `textStart`/`textEnd` — instead of `null` (which is what
ranged-but-non-overlapping segments return at `:178`). These range-less segments
therefore survive the `mapNotNull` filter and sort last (overlap 0).

**Impact:** if the visible character range overlaps *no* ranged segment on the same
href, but range-less segments exist on that href, one of them is returned as the
seek target. `firstOrNull()` after a stable sort picks the **first range-less
segment in list order** — i.e. the audiobook is seeked to that segment's `startMs`
with **zero textual relationship** to what is on screen.

**Suggested fix:** return `null` (no target) from `overlapScore` for range-less
segments too (or exclude them from the candidate set), so a page with no real
overlap yields no seek rather than an arbitrary one.

### 2. Audio in an unmapped gap is misclassified as a repairable "Mismatch" — Medium-High (UX)

**Location:** `ReaderWhispersyncSyncCoordinator.kt:115-129` (`onAudiobookPlaybackPositionStep`).

**Issue:** whenever `activeSegment` finds no segment, the step returns
`ReaderWhispersyncStatusKind.Mismatch`. A real audiobook has non-narration gaps
(chapter breaks, music, acknowledgments) that legitimately have no segment.

**Impact:** audio landing in a gap surfaces a **"Whispersync mismatch" attention
state + repair button** to the user during every gap, even though nothing is wrong.
This is a taxonomy problem: an unmapped region is not a desync.

**Suggested fix:** introduce a neutral "no active cue" state (keep last overlay or
dim) distinct from a genuine text/audio `Mismatch`; reserve `Mismatch` for a real
resource/position conflict.

### 3. Numeric sidecar parsing is not tolerant — one bad field discards the whole sidecar — Medium

**Location:** `WhispersyncModels.kt:261-275` (`intValue` / `millisecondValue` / `secondsValue`), used at `:213-226`.

**Issue:** the numeric accessors call `.jsonPrimitive` directly, which **throws on
object/array values** (unlike `stringValue`, which safely uses `as? JsonPrimitive`).
Additionally `millisecondValue` uses `longOrNull`, so a float millisecond such as
`263360.5` parses to `null`.

**Impact:** not a crash (decode is wrapped in `runCatching`, degrading to
`LoadFailed`), but this **contradicts the spec's "tolerant of payload variants /
must not discard segments"** intent — a single malformed numeric field makes the
**entire** sidecar unusable, with no partial segment recovery.

**Suggested fix:** make the numeric accessors null-safe on non-primitive values
(`as? JsonPrimitive`), accept float milliseconds, and skip bad **segments** rather
than aborting the whole sidecar.

### 4. Loose resource matching can cross-match the wrong track — Medium

**Location:** `WhispersyncModels.kt:280-298` (`matchesAudioResourceOrTrack`,
`audioResourceCandidates`, `normalizedWhispersyncResourceCandidates`) and
`ReaderWhispersyncPlaybackPolicy.kt:55-77` (`trackIndexForWhispersyncAudioResource`).

**Issue:** matching uses `endsWith("/$other")` plus multiple candidate forms
(cleaned / without-scheme / url-path). Two distinct resources sharing a path suffix
(e.g. `…/audio/part1.mp3` vs `…/somewhere/part1.mp3`, or generic `chapter1.mp3`)
will match each other.

**Impact:** wrong active segment or wrong playback track selected. The exact
`audioTrackIndex` fallback is safer but only fires when *no* resource matches, so a
false resource match pre-empts it.

**Suggested fix:** prefer exact normalized equality and the `audioTrackIndex`;
treat suffix-only matches as a weaker signal that must be corroborated by track
index or position.

### 5. Half-open segment boundaries + gaps produce transient mismatches — Medium (feeds #2)

**Location:** `WhispersyncModels.kt:44-51` (`activeSegment`).

**Issue:** `position >= startMs && position < endMs`. At `position == endMs` the
segment is excluded; it only resolves cleanly if the next segment is exactly
contiguous (`startMs == prev.endMs`).

**Impact:** any gap or non-contiguous boundary yields no match → `Mismatch`. This is
the mechanical trigger behind finding #2; narration sidecars are rarely perfectly
contiguous.

### 6. Pausing playback does not update Whispersync status or clear the overlay — Low

**Location:** `ReaderController.kt:653-654` (`onReadaloudPlaybackState`).

**Issue:** when `!isPlaying`, the step is built with `status = null`, so the prior
status (e.g. "Whispersync playing") and the text overlay persist while paused.

**Impact:** minor state/UX inconsistency (status says "playing" while paused).

### 7. Zero-length / inverted segments dropped silently — Low

**Location:** `WhispersyncModels.kt:227` (`if (endMs <= startMs) return null`).

**Issue:** reasonable to drop, but combined with finding #3's all-or-nothing decode
there is no diagnostic when segments are lost, making field-evolution bugs
invisible.

**Suggested fix:** log/count dropped segments for diagnostics.

## Verified correct (checked, not flaws)

- **Feedback loop suppression:** `ReaderController.kt:808`, `:1321-1322`.
- **Engine-command de-duplication:** `ReaderController.kt:668-669`,
  `ReaderWhispersyncSyncCoordinator.kt:215-225`.
- **Resume precedence by `updatedAtMs`:** `BinderyAudiobookPlayerPolicy.kt:284`.
- **Decode crash safety (runCatching → LoadFailed):** `BinderyRepository.kt:423/444/482`.

## Confidence & caveats

- Findings #1, #3, #5 are high-confidence from the logic.
- Findings #2 and #4 depend on real sidecar / audio-resource shape (how gappy
  sidecars are, whether resources share suffixes). Worth confirming against a
  production sidecar (the book `3809` sidecar, or the POC sidecars referenced in
  the design spec under "Diagnostic-only ASR matching references").
- This review is code-level only; release-device validation of the two-way sync
  loop remains the separate gating item per the design spec.
