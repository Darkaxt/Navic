# Settings Search Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the oversized settings-search registry so `SettingsSearchResults.kt` becomes a focused renderer.

**Architecture:** Keep the existing `SettingsSearchRow` builders and search filtering policy. Add section-specific row registry files for appearance, playback, ebook, storage, integrations, and developer settings, coordinated by a small shared context.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android host tests.

---

### Task 1: Protect The Split With Source Tests

**Files:**
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetTestFixtures.kt`
- Modify: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- Modify: reader host tests that inspect settings search source text

- [x] **Step 1: Add `settingsSearchSourceText()`**

Read all `SettingsSearch*.kt` files from the common settings package and concatenate them for source-text assertions.

- [x] **Step 2: Add the failing source-layout test**

Assert `SettingsSearchResults.kt` stays under 300 lines and that section registry files exist.

- [x] **Step 3: Verify the test fails**

Run:

```powershell
./gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.settingsSearchResultsStaysFocusedOnRenderingSearchResults
```

Expected: fail while the monolithic registry is still in `SettingsSearchResults.kt`.

### Task 2: Split The Registry

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchResults.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchRegistry.kt`
- Create: section registry files named in the source-layout test

- [x] **Step 1: Create shared registry context**

Store shared labels, platform flags, path builder, preference manager, player, storage/cache snapshots, and cache stats.

- [x] **Step 2: Move registry rows by section**

Move rows into focused functions:

```kotlin
@Composable
internal fun settingsSearchAppearanceRows(context: SettingsSearchContext): List<SearchableSettingsRow>
```

- [x] **Step 3: Keep `SettingsSearchResults.kt` as renderer/orchestrator**

The renderer should collect rows from `searchableSettingsRows()` and render filtered matches only.

### Task 3: Verify

**Files:**
- Test: `composeApp/src/androidHostTest/kotlin/paige/navic/reader/ReaderRuntimeAssetsTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/settings/SettingsSearchPolicyTest.kt`

- [x] **Step 1: Run focused source-layout test**

```powershell
./gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.reader.ReaderRuntimeAssetsTest.settingsSearchResultsStaysFocusedOnRenderingSearchResults
```

Expected: pass.

- [x] **Step 2: Run settings search policy tests**

```powershell
./gradlew.bat :composeApp:allTests --tests paige.navic.ui.screens.settings.SettingsSearchPolicyTest
```

Expected: pass or report the exact unsupported test filter if this aggregate task cannot filter common tests.

- [x] **Step 3: Re-measure file sizes**

Confirm `SettingsSearchResults.kt` is under 300 lines and no new settings-search registry file exceeds 1200 lines.
