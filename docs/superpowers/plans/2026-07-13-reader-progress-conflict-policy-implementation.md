# Reader Progress Conflict Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve B13 with timestamp-aware reader start-locator selection and preserved divergence diagnostics.

**Architecture:** `ReaderProgressSync.kt` returns a typed decision from remote and local candidates. Reader open-request construction keeps explicit route locators authoritative, attaches fallback conflict evidence to the engine request, and the reader screen logs it before dispatch.

**Tech Stack:** Kotlin Multiplatform, `kotlin.time.Instant`, Kotlin test, Android host Gradle tests.

---

## File Map

- Modify `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderProgressSync.kt` for candidates, timestamp parsing, selection, and local progress lookup.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt` to carry an optional conflict diagnostic.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOpenRequest.kt` to use the typed decision.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt` to pass local progress metadata and emit diagnostics.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderProgressSyncTest.kt` for policy tests.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/reader/ReaderOpenRequestFactoryTest.kt` for integration tests.

## Task 1: Lock The Timestamp Policy In RED

- [x] Add tests that call `resolveReaderStartLocator` with `ReaderStartLocatorCandidate` values and assert:
  - newer local 20% beats older remote 80%;
  - newer remote 10% beats older local 70% as an explicit reread;
  - newer remote 80% beats older local 20%;
  - equal timestamps choose remote;
  - a missing or malformed timestamp uses the legacy progress policy;
  - divergence preserves both candidates and the selected source.
- [x] Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderProgressSyncTest" --console=plain
```

Expected: compilation fails because the candidate and decision APIs do not exist.

## Task 2: Implement Candidate Resolution

- [x] Add `ReaderStartLocatorSource`, `ReaderStartLocatorSelectionPolicy`, `ReaderStartLocatorCandidate`, `ReaderStartLocatorConflict`, and `ReaderStartLocatorDecision`.
- [x] Implement `resolveReaderStartLocator(remoteCandidate, localCandidate)` using comparable timestamps first and the current placeholder/progress policy only as fallback.
- [x] Parse numeric epoch milliseconds before `Instant.parse`; malformed values return no timestamp.
- [x] Add `ReaderReadingProgressState.startProgressFor(...)`; keep `startLocatorFor(...)` delegating to it for compatibility.
- [x] Run the focused progress test and confirm every case passes.
- [x] Commit with `fix(reader): make progress conflict policy timestamp-aware`.

## Task 3: Integrate Open Requests And Diagnostics

- [x] Add failing `ReaderOpenRequestFactoryTest` cases proving a newer behind candidate wins, explicit route navigation still wins, and divergence is attached to the request.
- [x] Add `startLocatorConflict: ReaderStartLocatorConflict? = null` to `ReaderEngineOpenRequest`.
- [x] Extend `toReaderEngineOpenRequest` with local progress metadata, resolve fallback candidates, and attach the conflict only when no explicit route locator exists.
- [x] Update `ReaderScreen` to obtain `startProgressFor(...)`, pass it to request construction, and log the selected source plus both candidate summaries when a conflict exists.
- [x] Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHost --tests "paige.navic.reader.ReaderProgressSyncTest" --tests "paige.navic.ui.screens.reader.ReaderOpenRequestFactoryTest" --console=plain
```

Expected: all focused tests pass with zero failures.
- [x] Commit with `fix(reader): retain start locator conflict diagnostics`.

Focused evidence: 40 tests passed across `ReaderProgressSyncTest` and `ReaderOpenRequestFactoryTest`. RED compilation failed on the absent candidate/decision API and then on the absent request integration. GREEN passed after timestamp arbitration, conflict retention, and reader-screen diagnostics were wired. Broad and device validation remains deferred by execution order.

## Deferred Validation

Per the requested execution order, broad reader suites, Android device checks, release versioning, and publication remain deferred until all roadmap code changes are implemented.
