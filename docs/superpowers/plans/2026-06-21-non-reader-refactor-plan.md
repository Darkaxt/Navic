# Non-Reader File Refactoring Plan

Date: 2026-06-21
Scope: Non-reader files only (reader files handled by Codex)
Branch: `codex/komikku-reader-backbone-eta64` (merged with master)

## Phase 1: AurralDtoMapping.kt — Split DTOs from mappers (886 lines)

**File:** `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralDtoMapping.kt`

**Problem:** 33 `@Serializable` DTO data classes + 36 mapping functions in one file. DTOs and mappers are separate concerns — DTOs define the wire format, mappers convert to domain models.

**Plan:**
1. Create `AurralDtos.kt` — move all `@Serializable` data classes (DTOs)
2. Rename `AurralDtoMapping.kt` to `AurralDtoMappers.kt` — keep only the mapping functions
3. Update all imports across the codebase

**Risk:** Low — pure file reorganization, no logic changes.

## Phase 2: EbooksScreen.kt — Move enums to domain models (763 lines)

**File:** `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/EbooksScreen.kt`

**Problem:** 11 setting enums defined inside a UI file. Domain model enums should live in `domain/models/settings/`.

**Plan:**
1. Create `composeApp/src/commonMain/kotlin/paige/navic/domain/models/settings/ReaderSettingEnums.kt`
2. Move all 11 enums from `EbooksScreen.kt` to the new file
3. Update all imports

**Risk:** Low — moving type definitions, no logic changes.

## Phase 3: BinderyBookVersionPolicy.kt — Split by concern (1043 lines)

**File:** `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicy.kt`

**Problem:** 5 enums + 5 data classes + 84 functions mixing: book-version routing, Whispersync launch matching, finding-row construction, codec/quality ranking, and stable-key helpers.

**Plan:**
1. Create `BinderyWhispersyncLaunchPolicy.kt` — move Whispersync launch action/match/destination functions (lines ~397-460, ~624-670)
2. Create `BinderyBookVersionFormatMetadata.kt` — move codec/quality/duration helper functions (lines ~729-850)
3. Create `BinderyBookFindingPolicy.kt` — move finding-row construction and grouping functions (lines ~89-260)
4. Keep `BinderyBookVersionPolicy.kt` for version-row construction + routing + stable-key helpers

**Risk:** Medium — need to verify internal function visibility and update imports across the Bindery UI files.

## Phase 4: AurralRepository.kt — Split cache and enrichment (1074 lines)

**File:** `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`

**Problem:** API calls + cache storage + DTO mapping + optimistic-state tracking + enrichment all in one class.

**Plan:**
1. Create `AurralOptimisticStateTracker.kt` — move optimistic-state tracking functions (rememberOptimistic*, bumpArtistStateRevision, withOptimisticMonitoring, etc.)
2. Create `AurralEnrichmentPolicy.kt` — move enrichment logic (withLocalArtistState, withLibraryArtists, getArtistEnrichment enrichment portion)
3. Keep `AurralRepository.kt` as the API orchestration layer

**Risk:** Medium — the optimistic-state functions access private fields; may need to extract as inner classes or pass state explicitly.

## Verification

After each phase:
- `git diff --check` (whitespace)
- `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:compileCommonMain` (compilation)
- `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:testAndroidHost --tests "paige.navic.reader.*"` (no reader regressions)
- `.\gradlew.bat --no-daemon --no-build-cache "-Pkotlin.incremental=false" :composeApp:test` (full test suite if time allows)
