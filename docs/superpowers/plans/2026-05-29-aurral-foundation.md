# Aurral Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first native Aurral integration slice: preferences, URL/auth policy, API client foundation, Settings -> Integrations UI, diagnostics, and settings-search entries.

**Architecture:** Mirror the LidaClips integration shape, but keep Aurral-specific auth and Flow media URL helpers in a separate repository. This slice does not add catalog browsing, acquisition actions, or Flow playback yet; it creates the tested foundation those features will use.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, Ktor client, kotlinx.serialization, Multiplatform Settings, kotlin.test.

---

## File Structure

- Create `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt` for Aurral URL normalization, auth headers, stream/artwork URL helpers, connection testing, service status DTOs, and the Ktor client.
- Create `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryTest.kt` for URL/auth/media URL/status mapping tests.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt` to add Aurral preferences and `aurralRequestHeadersMap()`.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/PreferenceManagerTest.kt` to lock Aurral preference defaults and Basic Auth header generation.
- Create `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/viewmodels/SettingsAurralViewModel.kt` for connection test and service-status state.
- Create `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/AurralScreen.kt` for the native settings surface.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/IntegrationsScreen.kt` to link to Aurral below LidaClips.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchResults.kt` to add live Aurral settings rows.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/App.kt` and `composeApp/src/commonMain/kotlin/paige/navic/domain/models/Screen.kt` to add the Settings -> Aurral destination.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/di/RepositoryModule.kt` and `composeApp/src/commonMain/kotlin/paige/navic/di/ViewModelModule.kt` to register Aurral repository and view model.
- Modify `composeApp/src/commonMain/composeResources/values/strings.xml` for Aurral titles, subtitles, options, and status copy.

## Task 1: Repository Policy Tests

**Files:**
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryTest.kt`
- Later create: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`

- [ ] **Step 1: Write the failing URL/auth tests**

Add tests for these expected APIs:

```kotlin
assertNull(configuredAurralBaseUrl(" "))
assertEquals("https://aurral.example.com", configuredAurralBaseUrl(" https://aurral.example.com/ "))
assertEquals("https://aurral.example.com/aurral", configuredAurralBaseUrl("https://aurral.example.com/aurral/"))
assertNull(configuredAurralBaseUrl("aurral.example.com"))
assertNull(configuredAurralBaseUrl("https://aurral.example.com?debug=true"))
assertNull(configuredAurralBaseUrl("https://user:pass@aurral.example.com"))
assertEquals("https://aurral.example.com/aurral/api/health", aurralEndpoint("https://aurral.example.com/aurral/", "/api/health"))
assertEquals(mapOf("Authorization" to "Basic dXNlcjpwYXNz"), aurralBasicAuthHeaders(" user ", " pass "))
assertEquals(emptyMap(), aurralBasicAuthHeaders("", "pass"))
assertEquals(mapOf("Authorization" to "Bearer session-token"), aurralBearerAuthHeaders(" session-token "))
```

- [ ] **Step 2: Run the repository tests and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.domain.repositories.AurralRepositoryTest
```

Expected: fail because `AurralRepositoryTest` references Aurral APIs that do not exist yet.

- [ ] **Step 3: Implement minimal URL/auth helpers**

Create `AurralRepository.kt` with:

```kotlin
internal const val AURRAL_BASE_URL_REQUIRED_MESSAGE = "Enter the Aurral URL first."
internal const val AURRAL_BASE_URL_INVALID_SCHEME_MESSAGE = "Aurral URL must start with http:// or https://."
internal const val AURRAL_BASE_URL_INVALID_HOST_MESSAGE = "Aurral URL must include a host and cannot include credentials, a query, or a fragment."

internal fun configuredAurralBaseUrl(baseUrl: String): String?
internal fun aurralBaseUrlConfigurationError(baseUrl: String): String?
internal fun aurralEndpoint(baseUrl: String, path: String): String
internal fun aurralBasicAuthHeaders(username: String, password: String): Map<String, String>
internal fun aurralBearerAuthHeaders(token: String?): Map<String, String>
```

Use the same URL normalization policy as LidaClips: only HTTP(S), required host, optional valid port and path prefix, no embedded credentials, query, or fragment.

- [ ] **Step 4: Run the repository tests and verify GREEN**

Run the same command. Expected: all tests in `AurralRepositoryTest` pass.

## Task 2: Flow Media URL Tests

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`

- [ ] **Step 1: Write failing Flow URL tests**

Add tests for bearer-query media URLs:

```kotlin
assertEquals(
    "https://aurral.example.com/api/weekly-flow/stream/job-123?token=session-token",
    aurralFlowStreamUrl("https://aurral.example.com", " job-123 ", " session-token ")
)
assertEquals(
    "https://aurral.example.com/aurral/api/weekly-flow/artwork/playlist-1?token=session-token",
    aurralFlowArtworkUrl("https://aurral.example.com/aurral", " playlist-1 ", " session-token ")
)
assertNull(aurralFlowStreamUrl("https://aurral.example.com", "", "session-token"))
assertNull(aurralFlowStreamUrl("https://aurral.example.com", "job-123", ""))
```

- [ ] **Step 2: Run and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.domain.repositories.AurralRepositoryTest
```

Expected: fail because Flow URL helpers are not implemented.

- [ ] **Step 3: Implement Flow URL helpers**

Add:

```kotlin
internal fun aurralFlowStreamUrl(baseUrl: String, jobId: String, sessionToken: String?): String?
internal fun aurralFlowArtworkUrl(baseUrl: String, playlistId: String, sessionToken: String?): String?
```

Encode path segments and query token with URL-safe encoding. Return `null` when base URL, id, or token is invalid.

- [ ] **Step 4: Run and verify GREEN**

Run the same command. Expected: repository tests pass.

## Task 3: Preference Defaults and Headers

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/PreferenceManagerTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`

- [ ] **Step 1: Write failing preference tests**

Add tests proving:

```kotlin
assertFalse(preferenceManager.aurralEnabled)
assertEquals("", preferenceManager.aurralBaseUrl)
assertEquals("", preferenceManager.aurralUsername)
assertEquals("", preferenceManager.aurralPassword)
assertEquals(emptyMap(), preferenceManager.aurralRequestHeadersMap())
preferenceManager.aurralUsername = "user"
preferenceManager.aurralPassword = "pass"
assertEquals(mapOf("Authorization" to "Basic dXNlcjpwYXNz"), preferenceManager.aurralRequestHeadersMap())
```

- [ ] **Step 2: Run and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.domain.manager.PreferenceManagerTest
```

Expected: fail because Aurral preferences do not exist.

- [ ] **Step 3: Implement preferences**

Add preference properties:

```kotlin
var aurralEnabled by preference(false)
var aurralBaseUrl by preference("")
var aurralUsername by preference("")
var aurralPassword by preference("")

fun aurralRequestHeadersMap(): Map<String, String> =
    paige.navic.domain.repositories.aurralBasicAuthHeaders(aurralUsername, aurralPassword)
```

- [ ] **Step 4: Run and verify GREEN**

Run the same command. Expected: preference tests pass.

## Task 4: Repository Client and Diagnostics

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`

- [ ] **Step 1: Write failing connection/status tests**

Use a fake `AurralApiClient` and verify:

```kotlin
val repository = AurralRepository(preferenceManager, fakeClient)
assertEquals(AurralConnectionResult.Connected, repository.testConnection())
assertEquals(AurralConnectionResult.Unauthorized, repository.testConnection() when fake health returns 401)
assertEquals(AurralConnectionResult.Failed(AURRAL_BASE_URL_REQUIRED_MESSAGE), repository.testConnection() when base URL blank)
assertEquals(2, repository.getServiceStatus().getOrThrow().flowsCount)
assertEquals(1, repository.getServiceStatus().getOrThrow().requestsCount)
```

- [ ] **Step 2: Run and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.domain.repositories.AurralRepositoryTest
```

Expected: fail because repository, status models, and API client interface are incomplete.

- [ ] **Step 3: Implement repository/client foundation**

Add:

```kotlin
class AurralRepository(...)
interface AurralApiClient
sealed interface AurralConnectionResult
data class AurralServiceStatus(...)
```

Ktor client behavior:
- `testConnection()` calls `/api/health`.
- `getServiceStatus()` calls `/api/health`, `/api/auth/me`, `/api/weekly-flow/status?includeJobs=false`, and `/api/requests`.
- `Authorization: Basic ...` is sent when username/password are configured.
- 401 maps to `Unauthorized`; 403 maps to `Forbidden`; other non-success statuses map to `Failed`.

- [ ] **Step 4: Run and verify GREEN**

Run the same command. Expected: repository tests pass.

## Task 5: Settings UI and Search

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/viewmodels/SettingsAurralViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/AurralScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/IntegrationsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchResults.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/Screen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/di/RepositoryModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/di/ViewModelModule.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add navigation, DI, and resources**

Add a `Screen.Settings.Aurral` destination, import `SettingsAurralScreen()` in `App.kt`, register `AurralRepository`, and register `SettingsAurralViewModel`.

- [ ] **Step 2: Add Aurral settings screen**

The screen must contain:
- Enable Aurral switch.
- Aurral URL text field, default blank.
- Username text field.
- Password field using password visual transformation.
- Test connection button.
- Status card showing health, auth user/permission hints, Flow count, shared playlist count, and request count when available.

- [ ] **Step 3: Add Integrations entry and settings search rows**

Add Aurral under Settings -> Integrations and add functional filtered search rows for:
- Enable Aurral.
- Aurral URL.
- Aurral username.
- Aurral password.

- [ ] **Step 4: Run compile verification**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests paige.navic.domain.repositories.AurralRepositoryTest --tests paige.navic.domain.manager.PreferenceManagerTest
.\gradlew.bat :androidApp:assembleDebug --stacktrace
```

Expected: tests pass and Android debug build succeeds.

## Task 6: Commit, Push, Release Decision

**Files:**
- All files changed in Tasks 1-5.

- [ ] **Step 1: Review diff**

Run:

```powershell
git status --short
git diff --stat
git diff
```

Confirm the diff is limited to Aurral foundation work and no unrelated files were reverted.

- [ ] **Step 2: Commit and push**

Run:

```powershell
git add docs/superpowers/plans/2026-05-29-aurral-foundation.md composeApp/src/commonMain composeApp/src/commonTest
git commit -m "feat: add Aurral integration foundation"
git push fork master
```

- [ ] **Step 3: Release only after build evidence**

If `assembleRelease` succeeds and version/tag state is clear, create the next beta release with `gh`, upload the release APK, and install only the release package (`darkaxt.navic`) to the phone if connected. Never install `Navic (Dev)`.

## Self-Review

- Spec coverage: this plan covers Aurral foundation, settings, diagnostics, auth, and settings search. It intentionally does not cover native catalog browsing, acquisition marking, Flow playback, Kreate polish, or LidaClips offline clip storage; those are separate follow-up slices in the Aurral design spec.
- Placeholder scan: no task uses undefined placeholder instructions; each task has file paths, expected APIs, and verification commands.
- Type consistency: helper names are consistent across tests and implementation tasks: `configuredAurralBaseUrl`, `aurralEndpoint`, `aurralBasicAuthHeaders`, `aurralBearerAuthHeaders`, `aurralFlowStreamUrl`, `aurralFlowArtworkUrl`, `AurralRepository`, `AurralApiClient`, `AurralConnectionResult`, and `AurralServiceStatus`.
