# Network Client Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve QA finding A16 by giving every Navic network client one auditable baseline while retaining a distinct `HttpClient` instance and service-local authentication/configuration for every consumer.

**Architecture:** Add `NetworkClientFactory` and shared network JSON profiles under `data/remote`. The factory creates a fresh client on every call, installs the Navic user agent and optional content negotiation centrally, and accepts only service-local Ktor configuration; it does not install retries, mutable authentication, or cancellation timeouts. Existing service-specific timeout behavior remains local during this behavior-preserving slice, while all direct construction sites and Subsonic's client configuration adopt the common baseline.

**Tech Stack:** Kotlin Multiplatform, Ktor 3, kotlinx.serialization, Koin, kotlin.test, Ktor MockEngine, Gradle Android host tests.

---

### Task 1: Prove the shared policy and isolation contract

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/data/remote/NetworkClientFactoryTest.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/data/remote/NetworkClientPolicySourceTest.kt`

- [x] **Step 1: Add MockEngine only to the common-test dependency set**

Add `ktor-client-mock` at the existing Ktor version and reference it from `commonTest`; do not add a production dependency.

- [x] **Step 2: Write failing behavior tests**

Specify the desired API before production code exists:

```kotlin
val requests = mutableListOf<HttpRequestData>()
val factory = NetworkClientFactory {
	MockEngine { request ->
		requests += request
		respond("{\"value\":\"ok\",\"unknown\":true}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
	}
}
val authenticated = factory.create(
	json = NetworkJson.tolerant,
	configure = { defaultRequest { header(HttpHeaders.Authorization, "Bearer secret") } }
)
val anonymous = factory.create(json = NetworkJson.tolerant)

assertNotSame(authenticated, anonymous)
assertEquals("ok", authenticated.get("https://service-one.example/value").body<NetworkFixture>().value)
anonymous.get("https://service-two.example/value")
assertEquals("Navic", requests[0].headers[HttpHeaders.UserAgent])
assertEquals("Bearer secret", requests[0].headers[HttpHeaders.Authorization])
assertNull(requests[1].headers[HttpHeaders.Authorization])
```

Add a source-contract test that fails while production files outside `NetworkClientFactory.kt` still contain `HttpClient {` or `HttpClient()` and while `SubsonicClientFactory` does not call the common baseline configurator.

- [x] **Step 3: Run the focused tests and verify RED**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.data.remote.NetworkClientFactoryTest" --tests "paige.navic.data.remote.NetworkClientPolicySourceTest"
```

Expected: compilation/source-contract failure because `NetworkClientFactory`, `NetworkJson`, and centralized construction do not exist.

### Task 2: Implement the minimal shared network policy

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/data/remote/NetworkClientFactory.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/data/remote/SubsonicClientFactory.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/di/ManagerModule.kt`

- [x] **Step 1: Implement JSON profiles and fresh-client construction**

Create one factory whose production path invokes `HttpClient` for every call and whose test-only engine supplier also produces a fresh engine:

```kotlin
internal object NetworkJson {
	val compatible = Json { ignoreUnknownKeys = true }
	val tolerant = Json(compatible) { isLenient = true }
}

internal class NetworkClientFactory(
	private val engineFactory: (() -> HttpClientEngine)? = null
) {
	fun create(
		json: Json? = null,
		userAgent: String = NAVIC_USER_AGENT,
		configure: HttpClientConfig<*>.() -> Unit = {}
	): HttpClient {
		val policy: HttpClientConfig<*>.() -> Unit = {
			installNavicNetworkBaseline(userAgent)
			if (json != null) install(ContentNegotiation) { json(json) }
			configure()
		}
		return engineFactory?.let { HttpClient(it(), policy) } ?: HttpClient(policy)
	}
}
```

The shared baseline installs only `UserAgent`. It must not install auth, redirects, retries, logging of sensitive data, or `HttpTimeout`.

- [x] **Step 2: Reuse the baseline in Subsonic construction**

Replace Subsonic's duplicate `UserAgent` installation with `installNavicNetworkBaseline()`. Keep custom server headers inside `SubsonicClientFactory` so they cannot become global factory state.

- [x] **Step 3: Register the factory**

Add `singleOf(::NetworkClientFactory)` next to `SubsonicClientFactory` in `ManagerModule.kt`.

- [x] **Step 4: Run the behavior test**

Run the two tests from Task 1. Expected: the MockEngine behavior test passes; the source-contract test still fails on unmigrated clients.

### Task 3: Migrate service API clients without sharing authentication

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralApiClient.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralSerialization.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyApiClient.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderySerialization.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/LastFmRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/LidaClipsRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/MusicBrainzArtworkRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/di/RepositoryModule.kt`

- [x] **Step 1: Inject `NetworkClientFactory` into each Ktor API client**

Use `factory.create(json = NetworkJson.tolerant) { ...existing service-local plugins... }`. Preserve request-level header application and status handling exactly. MusicBrainz passes its required descriptive user agent through the `userAgent` argument.

- [x] **Step 2: Point service JSON aliases at the shared profile**

Keep `AURRAL_JSON` and `BinderyJson` as compatibility aliases if call sites use them for DTO decoding, but assign both to `NetworkJson.tolerant` instead of constructing duplicate `Json` instances.

- [x] **Step 3: Wire API interfaces through Koin**

Register distinct `AurralApiClient`, `BinderyApiClient`, `LastFmApiClient`, and `LidaClipsApiClient` instances, each constructed with the factory. Pass those instances explicitly into repositories. Preserve constructor defaults used by isolated repository tests.

- [x] **Step 4: Run repository-focused regression tests**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.AurralRepositoryTest" --tests "paige.navic.domain.repositories.BinderyRepositoryTest" --tests "paige.navic.domain.repositories.LastFmRepositoryTest" --tests "paige.navic.domain.repositories.LidaClipsRepositoryTest"
```

Expected: all selected tests pass with no auth or DTO behavior changes.

### Task 4: Migrate raw download, lyrics, and update clients

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/LyricsRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/DownloadManager.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/LidaClipCacheManager.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/components/sheets/ChangelogSheet.kt`

- [x] **Step 1: Inject the factory as the final constructor dependency**

Replace each direct client with `networkClientFactory.create(...)`. Keep download and clip clients uncredentialed by default; continue applying headers only on the individual request that needs them. Use `NetworkJson.compatible` for the GitHub update DTO and local lyrics configuration.

- [x] **Step 2: Preserve existing service-local behavior**

Move existing timeout plugin blocks unchanged into the factory configure lambda. This slice neither introduces new timeouts nor converts existing service behavior. Do not add global retries.

- [x] **Step 3: Run source and compilation gates**

Run:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.data.remote.NetworkClientFactoryTest" --tests "paige.navic.data.remote.NetworkClientPolicySourceTest" :androidApp:assembleDebug
```

Expected: all selected tests pass, no direct production `HttpClient` construction remains outside the factory, and Android debug assembly succeeds.

### Task 5: Release and record A16 evidence

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`

- [x] **Step 1: Bump the prerelease**

Increment `versionCode` from 529 to 530 and set `versionName` to `v1.0.11-iota8`.

- [ ] **Step 2: Run the release gate**

Run focused policy/repository tests, `:androidApp:assembleDebug`, `scripts/verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-iota8`, and `git diff --check` after rebasing `fork/master`.

- [ ] **Step 3: Publish and independently verify**

Tag and publish `v1.0.11-iota8` with Android enabled and iOS skipped. Download the public APK, compare its SHA-256 with GitHub metadata, verify the established release certificate, confirm versionCode 530/versionName iota8, then upgrade and launch it on `emulator-5554` with no fatal startup exception.

- [ ] **Step 4: Record evidence and clean the worktree**

Mark only `A16` released, update roadmap counts from 20/35 to 21/34, commit and push evidence, remove the temporary APK, then remove this isolated worktree and branch after proving both commits are on `fork/master`.

## Self-review

- A16 coverage: all ten current construction paths are included; shared JSON and baseline policy are tested; instances remain per-consumer; authentication remains service/request-local.
- Explicit exclusions: no new retry behavior, no global mutable auth, no cancellation timeout, and no reader implementation files.
- Proof strength: behavior tests inspect actual Ktor requests through MockEngine; source guards prevent regression to direct construction; release verification covers the published artifact and runtime upgrade.
