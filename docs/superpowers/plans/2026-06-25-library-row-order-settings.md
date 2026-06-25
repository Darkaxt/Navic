# Library Row Order Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a settings page for ordering and hiding Library rows, including Aurral rows.

**Architecture:** Add a pure row-order policy, persist order/hidden IDs in `PreferenceManager`, render Library sections through the policy, and expose a draggable settings page using existing reorder utilities.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, russhwolf settings, Gradle Android host tests.

---

### Task 1: Row Order Policy

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/LibraryRowOrderPolicy.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/library/LibraryRowOrderPolicyTest.kt`

- [ ] Write failing tests for default order, custom order, hidden rows, missing rows appended visible, obsolete IDs ignored, and Aurral kind mapping.
- [ ] Implement `LibraryRowId`, serialization helpers, and effective-order functions.
- [ ] Run the focused policy test.

### Task 2: Preferences

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/PreferenceManagerTest.kt`

- [ ] Add preferences for Library row order and hidden row IDs.
- [ ] Preserve existing defaults.
- [ ] Run affected preference tests.

### Task 3: Settings Page

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/Screen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/AppearanceScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/LibraryRowsScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] Add a `Screen.Settings.LibraryRows` route.
- [ ] Add a row under Appearance > Library.
- [ ] Create a draggable full-screen settings page with eye/eye-off visibility buttons.

### Task 4: Library Rendering

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/LibraryScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/components/Content.kt`

- [ ] Pass effective row IDs into Library content.
- [ ] Emit each Library section from row IDs.
- [ ] Keep existing empty-section behavior.
- [ ] Ensure Aurral loading and resolved rows follow order/visibility.
- [ ] Ensure Quick Picks refresh is skipped when hidden.

### Task 5: Validation

**Files:**
- Create or modify focused guard tests under `composeApp/src/commonTest`.

- [ ] Run `git diff --check`.
- [ ] Run focused policy tests.
- [ ] Run focused Android host tests for the new guards.
- [ ] Commit and report exact verification output.
