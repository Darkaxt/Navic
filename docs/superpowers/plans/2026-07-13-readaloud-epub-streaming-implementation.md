# Readaloud EPUB Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve B11 by opening the managed readaloud EPUB archive once, buffering only bounded metadata, streaming only package-referenced audio to disk, and reusing the existing publication file instead of creating a duplicate EPUB copy.

**Architecture:** Add a file-backed `StorytellerEpubArchive` around `ZipFile` with normalized entry lookup, bounded metadata reads, direct-to-file audio streaming, and deterministic read metrics. The runtime loader owns one archive scope and passes it to the parser and audio materializer; cache generation uses a staged `v2` directory, leaves the legacy layout untouched until successful promotion, and removes legacy files only after the new layout is complete.

**Tech Stack:** Kotlin Multiplatform Android source set, JVM `ZipFile`/NIO file operations, managed reader session storage, Kotlin test, Gradle Android host tests.

---

## Contract And File Map

- Create `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerEpubArchive.android.kt`: one-open archive lifecycle, normalized entry index, bounded metadata reads, streamed extraction, and metrics.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerMediaOverlayParser.android.kt`: parse from `StorytellerEpubArchive`, load only OPF, referenced SMIL, and text documents referenced by those SMIL files; retain byte-array entry points only as test/backward-compatible wrappers that use a temporary file.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerReadaloudAudioCache.android.kt`: accept the open archive plus the resolver-owned publication file/URL; stream manifest audio to a staged versioned cache and atomically promote it.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerReadaloudRuntimeLoader.android.kt`: remove `publicationFile.readBytes()`, open the archive once, parse and materialize inside that scope, and expose optional read metrics for deterministic tests.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerMediaOverlayParserTest.kt`: preserve parser behavior through the file-backed archive path.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudAudioCacheTest.kt`: verify direct streaming, publication reuse, `v2` layout, legacy preservation on failure, and legacy cleanup after success.
- Modify `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudRuntimeLoaderTest.kt`: verify one archive open, resolver publication reuse, cache reuse, and no whole-publication byte read.
- Create `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudStreamingTest.kt`: construct a large archive on disk and prove bounded metadata buffering, fixed-size audio copy buffering, selective entry access, and one archive open.

## Task 1: Lock The Streaming Archive Contract

**Files:**
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudStreamingTest.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudRuntimeLoaderTest.kt`

- [x] **Step 1: Add a large on-disk EPUB fixture without creating a large audio byte array**

Write ZIP entries through a reusable 64 KiB generated block. Include valid container/OPF/SMIL/XHTML metadata, a 24 MiB manifest audio entry, and a 32 MiB unreferenced decoy entry.

```kotlin
private fun writeGeneratedEntry(zip: ZipOutputStream, path: String, byteCount: Long) {
	zip.putNextEntry(ZipEntry(path))
	val block = ByteArray(64 * 1024) { index -> ((index * 31) and 0xff).toByte() }
	var remaining = byteCount
	while (remaining > 0L) {
		val count = minOf(block.size.toLong(), remaining).toInt()
		zip.write(block, 0, count)
		remaining -= count
	}
	zip.closeEntry()
}
```

- [x] **Step 2: Add failing assertions for the production memory contract**

```kotlin
assertEquals(1, metrics.archiveOpenCount)
assertTrue(metrics.peakBufferedMetadataBytes < 1024 * 1024)
assertEquals(64 * 1024, metrics.peakStreamBufferBytes)
assertEquals(24L * 1024 * 1024, metrics.streamedAudioBytes)
assertEquals(listOf("EPUB/Audio/chapter1.mp3"), metrics.streamedEntryNames)
assertFalse(metrics.openedEntryNames.contains("EPUB/Unused/decoy.bin"))
```

- [x] **Step 3: Add a source contract that rejects whole-publication materialization**

Read the runtime, parser, and cache production sources and reject `resolved.publicationFile.readBytes()`, `epubEntries`, and `Map<String, ByteArray>` archive storage.

- [x] **Step 4: Run the focused tests and record RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHost --tests "paige.navic.reader.StorytellerReadaloudStreamingTest" --tests "paige.navic.reader.StorytellerReadaloudRuntimeLoaderTest" --console=plain
```

Expected: compilation fails because `StorytellerArchiveReadMetrics` and the file-backed streaming path do not exist.

## Task 2: Implement One-Open Archive Access

**Files:**
- Create: `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerEpubArchive.android.kt`
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudStreamingTest.kt`

- [x] **Step 1: Add deterministic metrics and the archive lifecycle**

```kotlin
internal data class StorytellerArchiveReadMetrics(
	var archiveOpenCount: Int = 0,
	var peakBufferedMetadataBytes: Int = 0,
	var peakStreamBufferBytes: Int = 0,
	var streamedAudioBytes: Long = 0,
	val openedEntryNames: MutableList<String> = mutableListOf(),
	val streamedEntryNames: MutableList<String> = mutableListOf()
)

internal class StorytellerEpubArchive private constructor(
	private val zipFile: ZipFile,
	private val metrics: StorytellerArchiveReadMetrics
) : Closeable {
	companion object {
		fun open(file: File, metrics: StorytellerArchiveReadMetrics = StorytellerArchiveReadMetrics()): StorytellerEpubArchive
	}
}
```

Index entries by `normalizedMediaOverlayResource`, reject duplicate normalized paths, and increment `archiveOpenCount` exactly once after `ZipFile` opens.

- [x] **Step 2: Add bounded metadata reads**

`readMetadata(path)` must reject entries larger than 8 MiB using both declared size and a bounded streaming count. It records the normalized path and peak retained metadata bytes, then returns only that entry's bytes.

- [x] **Step 3: Add direct streamed extraction**

`copyEntryTo(path, target)` uses one 64 KiB buffer and `FileOutputStream`; it never returns audio bytes. Record streamed path, byte count, and maximum buffer size. Return `false` for missing or external entries without creating a target.

- [x] **Step 4: Run the archive contract test**

Run the Task 1 focused command. Expected: archive-level tests compile; loader/source-contract assertions remain RED until Tasks 3-4.

## Task 3: Parse Only Referenced Metadata

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerMediaOverlayParser.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerMediaOverlayParserTest.kt`

- [x] **Step 1: Move package parsing to `StorytellerEpubArchive`**

Add `internal fun parsePackage(archive: StorytellerEpubArchive)` and make production parsing use it. Resolve container and OPF through bounded reads; use archive entry names only for OPF fallback.

- [x] **Step 2: Split SMIL decoding from label enrichment**

Parse SMIL into an internal value carrying audio path, text path, fragment, clip bounds, and fallback label. Collect distinct referenced text paths, read only those XHTML documents, then construct `MediaOverlayClip` values with the existing label precedence.

- [x] **Step 3: Preserve byte-array compatibility without archive maps**

The existing `parse(ByteArray)` and `parsePackage(ByteArray)` wrappers write a temporary EPUB, call the file-backed parser, and delete the temporary file. Remove `epubEntries`, `ZipInputStream`, and `ByteArrayOutputStream` from production parser code.

- [x] **Step 4: Run parser tests**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHost --tests "paige.navic.reader.StorytellerMediaOverlayParserTest" --console=plain
```

Expected: all existing clip, label, metadata, and playback-plan assertions pass unchanged.

## Task 4: Stream Audio Into A Versioned Managed Cache

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerReadaloudAudioCache.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudAudioCacheTest.kt`

- [x] **Step 1: Change materialization inputs**

Replace `epubBytes` with `archive`, `publicationFile`, and `publicationUrl`. Return the resolver-owned publication unchanged and extract only `readaloudPackage.audioResources` with archive entries.

- [x] **Step 2: Add collision-free deterministic audio names**

Use the package order plus sanitized leaf name, for example `audio-0001-chapter1.mp3`, and keep URI lookup keyed by normalized resource href.

- [x] **Step 3: Stage and promote cache layout `v2`**

Build under `storyteller-readaloud/<session>/v2.pending`, then move to `v2` with NIO atomic move when supported and same-filesystem replacement otherwise. On failure, remove only pending output and leave legacy/root files readable. After successful promotion, remove every legacy child except `v2`.

- [x] **Step 4: Test success, failure, and reuse**

Verify the publication file is not copied, only referenced audio is present, the second materialization reuses complete `v2` files, failed extraction preserves a seeded legacy file, successful extraction removes it, and lease release removes the session root.

- [x] **Step 5: Run cache tests**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHost --tests "paige.navic.reader.StorytellerReadaloudAudioCacheTest" --console=plain
```

Expected: all versioning, extraction, fallback, and cleanup assertions pass.

## Task 5: Integrate The Runtime Loader

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/paige/navic/reader/StorytellerReadaloudRuntimeLoader.android.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/StorytellerReadaloudRuntimeLoaderTest.kt`

- [x] **Step 1: Remove the whole-publication byte read**

Open `resolved.publicationFile` once and perform parser plus cache work inside the same `use` block.

```kotlin
val metrics = StorytellerArchiveReadMetrics()
val (readaloudPackage, cache) = StorytellerEpubArchive.open(resolved.publicationFile, metrics).use { archive ->
	val parsed = StorytellerMediaOverlayParser.parsePackage(archive)
	parsed to StorytellerReadaloudAudioCache.materialize(
		archive = archive,
		publicationFile = resolved.publicationFile,
		publicationUrl = resolved.publicationUrl,
		readaloudPackage = parsed,
		cacheRoot = cacheRoot,
		sessionId = resolved.cacheKey
	)
}
```

- [x] **Step 2: Expose metrics through an optional observer callback**

Use `archiveReadObserver: (StorytellerArchiveReadMetrics) -> Unit = {}` and invoke it after the archive closes. This is observation only; it does not alter control flow or introduce a timeout.

- [x] **Step 3: Update runtime assertions**

Expect the runtime publication URL to remain under `reader-publications`, the source resource to fetch once, the archive to open once per load, the second load to reuse resolver and audio caches, and the combined leases to remove both managed session roots.

- [x] **Step 4: Run the integrated B11 tests**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHost --tests "paige.navic.reader.StorytellerMediaOverlayParserTest" --tests "paige.navic.reader.StorytellerReadaloudAudioCacheTest" --tests "paige.navic.reader.StorytellerReadaloudRuntimeLoaderTest" --tests "paige.navic.reader.StorytellerReadaloudStreamingTest" --console=plain
```

Expected: all B11 tests pass with zero failures and the source contract finds no archive materialization pattern.

## Implementation And Validation Evidence

- The initial focused run failed on the missing archive metrics/type and new cache inputs, recording the required RED state before production implementation.
- The integrated parser/cache/loader/streaming suite passed 10/10. The adjacent owner batch passed 154/154 after excluding four failures reproduced unchanged on the clean public baseline; the post-device-fix parser/cache/loader/streaming/controller batch passed 16/16.
- The generated 56 MiB large EPUB proof opened the archive once, retained less than 1 MiB of metadata, used a 64 KiB audio copy buffer, streamed only the referenced 24 MiB audio entry, and never opened the unreferenced 32 MiB decoy.
- Emulator `readerDev` validation loaded a valid EPUB 3 media-overlay fixture and reached `publicationReady`. Runtime storage contained one 11,910-byte `reader-publications/.../publication.epub` and one 16,920-byte `storyteller-readaloud/.../v2/audio-0001-chapter1.mp3`, with no second EPUB.
- Media3 loaded one track/one clip and played the extracted MP3 to completion (`position=4059 ms`, `buffered=4048 ms`) without a playback or Android runtime error. Normal reader exit removed both managed session trees.
- Validation also exposed a pre-existing fatal media-button ambiguity from two discoverable `MediaSessionService` implementations. Navic now uses an explicit `PlaybackMediaButtonReceiver` for main-player restart routing while retaining both valid service declarations; the manifest/widget/source contract passes.
- Public `v1.0.11-iota20` was published from `f5a4a719` by successful GitHub Actions run `29271201032` with all iOS jobs skipped. The 46,241,680-byte APK SHA-256 is `39a04f73189c566b227cc776ca10ebbe9d9c3efff98af36379c17a15e666e858`, matching GitHub's digest; v2 certificate SHA-256 is `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7` and embedded metadata is `547/iota20`.
- The public APK passed all 30 packaged reader-vendor hashes and attribution verification, upgraded in place from `iota19`/546, and cold-started `MainActivity` successfully in 1,297 ms. The process remained alive and resumed with no AndroidRuntime, Koin, or Media3 error-level output.

## Task 6: Validate, Document, And Release

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-readaloud-epub-streaming-implementation.md`
- Modify: `androidApp/build.gradle.kts`

- [x] **Step 1: Run adjacent owner tests**

Include publication resolver, managed storage, parser, cache, runtime loader, reader runtime navigation, readaloud sync, media-overlay sync, controller, and coordinator tests. Record exact XML counts.

- [x] **Step 2: Run Android governance and assembly gates**

Run release-version verification for `v1.0.11-iota20`, source vendor 30/30, verifier tamper self-test, attribution, Android debug and reader-dev assembly, then packaged vendor/attribution for both APKs. Do not invoke iOS tasks.

- [x] **Step 3: Run Android readaloud smoke validation**

Open a readaloud EPUB through reader-dev, confirm `publicationReady`, local `file:` audio plan resources, playback start, overlay progression, session cleanup on exit, and no AndroidRuntime/Media3 fatal. Capture managed paths proving one publication file plus versioned audio output, not two EPUB copies.

- [x] **Step 4: Update B11 evidence and release metadata**

Mark B11 released, record the bounded large-archive proof and device evidence, then set `versionCode=547` and `versionName=v1.0.11-iota20`.

- [x] **Step 5: Commit, publish, and independently validate**

Fast-forward public master, create annotated `v1.0.11-iota20`, publish Android with all iOS jobs skipped, download the public APK, verify digest/certificate/metadata/governance, upgrade and cold-start it, push the immutable evidence commit, then remove only this completed worktree and local branch.

## Self-Review

- B11 single-open extraction: Tasks 1, 2, and 5.
- OPF/SMIL plus referenced text parsing: Task 3.
- Referenced-audio-only direct streaming: Tasks 2 and 4.
- No duplicate archive map, full `readBytes`, or duplicate publication file: Tasks 1, 4, and 5.
- Deterministic large-archive memory proof: Task 1.
- Versioned cache, old-layout preservation until success, and cleanup after regeneration: Task 4.
- Android-only staged release and independent public validation: Task 6.
- Placeholder scan: no deferred or unspecified implementation steps remain.
