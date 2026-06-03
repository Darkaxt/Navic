# Bindery Book Landing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a native Bindery Book landing page that shows book metadata plus inert audiobook and ebook version rows.

**Architecture:** Keep Author and Collection detail screens intact, and add a dedicated Book detail route/screen because books need publication metadata, manifest reading order, resource catalog entries, and later progress/playback actions. Add pure display-policy helpers for resource grouping so repository decoding and UI behavior can be tested without Compose.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin view models, OPDS 2 JSON over Ktor, kotlin.test.

---

### Task 1: Preserve OPDS Book Resource Data

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryTest.kt`

- [x] Add failing tests proving OPDS publication JSON preserves top-level `links`, `properties`, `duration`, `readingOrder`, and resource-catalog `resources`.
- [x] Add `BinderyResourceCatalog` and `BinderyBookResource` models, decode resource links with `kind`, `size`, `duration`, and `href`.
- [x] Add repository method `getBookResources(bookId)` and API client method `fetchBookResources(...)`.
- [x] Verify through `.\gradlew.bat :composeApp:testAndroid`.

### Task 2: Add Book Display Policies

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyCatalogDisplayPolicy.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/bindery/BinderyCatalogDisplayPolicyTest.kt`

- [x] Add failing tests for `binderyDestinationForBook(...)`, book id normalization, resource grouping into audiobook/ebook rows, and human-readable size/duration labels.
- [x] Reuse existing publication card data for routing and load full book metadata through the manifest route.
- [x] Add helpers that create non-clickable version rows from manifest reading order, publication acquisition links, and resource catalog resources.
- [x] Verify through `.\gradlew.bat :composeApp:testAndroid`.

### Task 3: Add Book Route, ViewModel, and Screen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/Screen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/App.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookScreen.kt`

- [x] Add `Screen.BinderyBook(bookId, title)`.
- [x] Register the route in `App.kt` under audiobook navigation metadata.
- [x] Add `BinderyBookViewModel` that loads `/opds/books/{id}/manifest` and `/opds/books/{id}/resources`, keeping previous data during refresh.
- [x] Add `BinderyBookScreen` with portrait cover, title, author, year/duration, expandable description, subject chips, and inert available-version rows.
- [x] Use loading and failed integration indicators consistently with existing Bindery screens.

### Task 4: Wire Book Clicks and Align Detail Sections

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyCatalogScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyDetailScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/components/layouts/HorizontalSection.kt`

- [x] Wire book cards in Books/Audiobooks catalogs to `Screen.BinderyBook`.
- [x] Wire Author/Collection publication cards to `Screen.BinderyBook`.
- [x] Add optional horizontal padding parameters to `horizontalSectionWithAvailableWidth` and default them to the current values.
- [x] Use zero extra inner padding for Author > Collections and Publications headers inside `ArtGrid`, so cards and headings align to the same content edge.

### Task 5: Verify, Document, and Publish

**Files:**
- Modify: `README.md`

- [x] Run targeted Bindery coverage through `.\gradlew.bat :composeApp:testAndroid`.
- [x] Run `.\gradlew.bat :androidApp:assembleDebug` locally and let the tag-triggered GitHub workflow run signed `:androidApp:packageRelease` with release secrets.
- [x] Update README with the Book landing page and available-version behavior.
- [ ] Commit, push to `fork/master`, and publish the next gamma APK release.
