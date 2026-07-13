# Optional Integration State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve QA finding C14 by ensuring Aurral and Bindery hub data cannot silently turn authorization, malformed-response, unavailable-service, or stale-cache conditions into an indistinguishable empty state.

**Architecture:** Preserve existing repository `Result` APIs for compatibility, but add a typed `OptionalIntegrationResult<T>` projection for hub-facing loads. Repository cache helpers will report live/fresh/stale provenance explicitly, HTTP clients will throw typed status exceptions instead of returning false empty collections, and ViewModels will expose typed availability alongside existing data state so screens can render precise status without discarding cached content.

**Tech Stack:** Kotlin Multiplatform, Ktor, kotlinx.serialization, Compose Multiplatform resources, StateFlow, kotlin.test, Android host source-contract tests.

---

### Task 1: Define and test the typed result contract

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/OptionalIntegrationResult.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/OptionalIntegrationResultTest.kt`

- [ ] **Step 1: Write failing classification tests**

Specify the result states before production code:

```kotlin
assertIs<OptionalIntegrationResult.Empty>(optionalIntegrationResult(Result.success(emptyList<String>()), false, List<String>::isEmpty))
assertIs<OptionalIntegrationResult.Stale<*>>(optionalIntegrationResult(Result.success(listOf("cached")), true, List<String>::isEmpty))
assertEquals(
	OptionalIntegrationFailureKind.Unauthorized,
	optionalIntegrationResult<String>(Result.failure(OptionalIntegrationHttpException(401, "Unauthorized")), false) { false }
		.failureOrNull()?.kind
)
assertEquals(
	OptionalIntegrationFailureKind.Malformed,
	optionalIntegrationResult<String>(Result.failure(SerializationException("bad payload")), false) { false }
		.failureOrNull()?.kind
)
```

Also cover disabled, misconfigured, unavailable, fresh data, and stale empty data.

- [ ] **Step 2: Run the focused test and verify RED**

Run `./gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.models.OptionalIntegrationResultTest"` and expect unresolved result types.

- [ ] **Step 3: Implement the minimal sealed result model**

Create:

```kotlin
enum class OptionalIntegrationFailureKind { Disabled, Misconfigured, Unauthorized, Malformed, Unavailable }
data class OptionalIntegrationFailure(val kind: OptionalIntegrationFailureKind, val message: String)
sealed interface OptionalIntegrationResult<out T> {
	data class Available<T>(val data: T) : OptionalIntegrationResult<T>
	data object Empty : OptionalIntegrationResult<Nothing>
	data class Stale<T>(val data: T, val failure: OptionalIntegrationFailure) : OptionalIntegrationResult<T>
	data class Unavailable(val failure: OptionalIntegrationFailure) : OptionalIntegrationResult<Nothing>
}
interface OptionalIntegrationHttpFailure { val statusCode: Int }
class OptionalIntegrationHttpException(
	override val statusCode: Int,
	message: String
) : IllegalStateException(message), OptionalIntegrationHttpFailure
```

The mapper must inspect the throwable cause chain for HTTP 401/403 and `SerializationException`; configuration callers provide Disabled/Misconfigured directly rather than relying on message parsing.

### Task 2: Preserve cache provenance and stop false empty responses

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralApiClient.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyApiClient.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryOptionalStateTest.kt`
- Test: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryOptionalStateTest.kt`

- [ ] **Step 1: Write failing repository tests**

Cover these exact outcomes for both repositories: disabled/missing configuration returns typed Unavailable; live empty payload returns Empty; live data returns Available; failed live request with decodable cache returns Stale with the cached data; 401/403 returns Unauthorized when no cache exists; malformed JSON returns Malformed; network failure returns Unavailable.

- [ ] **Step 2: Introduce typed HTTP exceptions**

Make `AurralApiException` and `BinderyApiException` implement the shared HTTP status contract. Aurral discovery/recent/library methods must throw on 401/403 rather than returning `emptyList()`. A genuine successful empty response remains empty; unsupported 404 endpoints return a typed unavailable failure instead of pretending the library is empty.

- [ ] **Step 3: Return cache provenance from hub-facing helpers**

Add an internal `CachedPayload<T>(data, source)` where source is Live, FreshCache, or StaleCache. Keep compatibility methods returning `Result<T>` by mapping to `.data`, and add `getDiscoveryOptional(...)` plus `getCatalogOptional(path)` that return `OptionalIntegrationResult<T>` with explicit stale provenance.

- [ ] **Step 4: Run repository tests**

Run the two new suites plus existing `AurralRepositoryTest`, `AurralRepositoryArtistEnrichmentTest`, `BinderyRepositoryTest`, and `BinderyRepositoryResourceJsonTest`.

### Task 3: Expose typed availability from ViewModels

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralHubViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyHubViewModel.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/OptionalIntegrationViewModelSourceTest.kt`

- [ ] **Step 1: Write failing source contracts**

Require both ViewModels to expose `StateFlow<OptionalIntegrationResult<...>?>`, call the typed repository methods, retain stale data in their existing `UiState`, and avoid `.getOrNull()` for hub row network loads.

- [ ] **Step 2: Wire Aurral availability**

Map Available/Empty/Stale/Unavailable into the existing discovery data flow while publishing the typed result separately. Incremental discovery supplements must update their section only on success and publish a typed failure instead of silently doing nothing.

- [ ] **Step 3: Wire Bindery availability**

Aggregate row loads without `.getOrNull()`: preserve successfully resolved rows, but publish Stale or Unavailable when any required hub request fails. Cached root/rows remain visible through the Stale result.

### Task 4: Render precise user-visible states

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/OptionalIntegrationStatus.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralHubScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyHubScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/ui/components/common/OptionalIntegrationStatusPolicyTest.kt`

- [ ] **Step 1: Write failing display-policy tests**

Map Disabled, Misconfigured, Unauthorized, Malformed, Unavailable, Empty, and Stale to distinct resource keys and severity. Stale is a warning while keeping content visible; Unauthorized/Malformed/Unavailable are errors; Empty is neutral.

- [ ] **Step 2: Add resource-backed status content**

Use concise status messages: disabled, configuration required, credentials rejected, response unreadable, service unavailable, no content, and showing cached content. Do not expose exception stack traces or credentials.

- [ ] **Step 3: Render status without replacing valid cached content**

Show the status as an unframed full-width grid/list item. Stale data remains interactive below the warning; Unavailable without data uses the existing `ContentUnavailable`/error presentation; successful empty uses the existing integration-specific empty message.

### Task 5: Release and record C14

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`

- [ ] **Step 1: Prepare `v1.0.11-iota10`**

Increment versionCode 531 to 532 and set versionName `v1.0.11-iota10`.

- [ ] **Step 2: Run release gates**

Run all new C14 tests, affected Aurral/Bindery suites, Android debug assembly, version verification, and `git diff --check` after rebasing public master.

- [ ] **Step 3: Publish and verify**

Publish Android only. Verify workflow success, public SHA-256, established signing certificate, embedded version, signed upgrade from iota9, live process, and absence of fatal/Koin startup errors.

- [ ] **Step 4: Record and clean**

Mark only C14 released, update roadmap accounting from 21/34 to 22/33, push evidence, remove temporary APKs, then remove this worktree and branch after proving all commits are on `fork/master`.

## Self-review

- Coverage: unavailable, unauthorized, malformed, empty, and stale-fallback are distinct typed and visible states for both audited integrations.
- Compatibility: existing repository `Result` and `UiState` consumers remain valid while hub paths opt into typed projection.
- Boundaries: no reader implementation files, no new retries, no cancellation timeouts, and no credential-storage/SSRF work from B17/B18.
