# Bindery Secure Credential Implementation Plan

> **Execution:** Implement inline in this task. The user explicitly requested no agents. Track every step with the checkboxes below.

**Goal:** Move the Bindery API key out of plaintext KMP settings, migrate existing Android installations without losing the key, and ensure authenticated requests never intentionally attach the key to a non-Bindery origin.

**Architecture:** `PreferenceManager` owns a small platform-provided `CredentialStore` abstraction. Android encrypts credential values with AES-GCM using a non-exportable Android Keystore key and persists only the encrypted envelope; this first public prerelease retains a read-only plaintext migration path and removes plaintext only after a verified secure write. Bindery request builders derive a canonical origin from the validated configured base URL, filter headers at the final request URL, and disable automatic redirects on the authenticated Ktor client.

**Tech Stack:** Kotlin Multiplatform, Android Keystore/JCA, Android `SharedPreferences`, Ktor 3, Media3, Koin, Kotlin test, Android host source-contract tests, ADB emulator upgrade verification.

---

## Scope Decisions

- Android is the product target. The iOS source set receives only a plaintext compatibility provider so the existing KMP build graph remains compilable; this is not iOS secure-storage support.
- Do not add `androidx.security:security-crypto`: Android officially deprecated `EncryptedSharedPreferences` and `MasterKey` in favor of direct Android Keystore/JCA use.
- Keep `X-Api-Key` because changing the server authentication contract is outside this client-only tranche.
- New or edited values are never written to plaintext settings. Plaintext reads exist only to migrate installations predating `v1.0.11-kappa1`.
- If secure migration cannot commit and read back the value, preserve the legacy value for a future retry. Never delete the only working credential.
- Do not add cancellation timeouts. Existing unrelated timeout policy is unchanged.

## File Map

- Create `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/CredentialStore.kt`: minimal credential-store contract plus settings-backed compatibility implementation.
- Create `composeApp/src/androidMain/kotlin/paige/navic/domain/manager/AndroidKeystoreCredentialStore.kt`: AES-GCM Android Keystore implementation and encrypted-envelope persistence.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`: secure Bindery key property and one-time migration.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/di/ManagerModule.kt`: explicit two-argument `PreferenceManager` construction.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/di/PlatformModule.android.kt`: register Android secure credential storage.
- Modify `composeApp/src/iosMain/kotlin/paige/navic/di/PlatformModule.ios.kt`: register compile-only settings compatibility storage.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyUrlPolicy.kt`: canonical origin comparison and final-URL header filtering.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyApiClient.kt`: filter headers for every final URL and reject automatic redirects.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/reader/ReadaloudPlaybackService.android.kt`: remove globally defaulted API-key headers and scope audio requests by URL.
- Modify `composeApp/src/androidMain/kotlin/paige/navic/ui/components/common/CoilBitmapLoader.kt`: resolve headers from the requested artwork URI rather than globally.
- Modify `androidApp/src/main/res/xml/backup_rules.xml` and `androidApp/src/main/res/xml/data_extraction_rules.xml`: exclude encrypted credential preferences from cloud backup and device transfer.
- Test `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/BinderyCredentialMigrationTest.kt`.
- Test `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryTest.kt`.
- Test `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyApiClientSecurityTest.kt`.
- Test `composeApp/src/androidHostTest/kotlin/paige/navic/domain/manager/AndroidKeystoreCredentialStoreSourceTest.kt`.
- Test `composeApp/src/androidHostTest/kotlin/paige/navic/reader/BinderyPlaybackHeaderScopeSourceTest.kt`.

### Task 1: Lock The Credential Migration Contract

- [ ] Add failing tests proving an existing plaintext `binderyApiKey` migrates once, is read back from secure storage, and is removed from `MapSettings`.
- [ ] Add failing tests proving an existing secure key wins and stale plaintext is removed.
- [ ] Add a failing test proving a failed secure write leaves plaintext readable and present for a later retry.
- [ ] Add failing tests proving normal setters write only secure storage and blank values clear both locations.
- [ ] Run `./gradlew :composeApp:testAndroidHostTest --tests "paige.navic.domain.manager.BinderyCredentialMigrationTest"` and confirm the missing `CredentialStore`/constructor contract fails.
- [ ] Implement `CredentialStore`, the settings compatibility store, and the `PreferenceManager` migration/property behavior.
- [ ] Rerun the focused test and confirm green.
- [ ] Commit as `feat(bindery): migrate api key to credential store`.

### Task 2: Add Android Keystore Persistence

- [ ] Add an Android host source-contract test requiring `AndroidKeyStore`, `AES/GCM/NoPadding`, a fresh IV per write, authenticated decryption, synchronous commit verification, and no plaintext value persistence.
- [ ] Add source-contract assertions requiring Android DI to use `AndroidKeystoreCredentialStore` and iOS DI to use only the compatibility store.
- [ ] Run the focused source test and confirm it fails because the Android implementation does not exist.
- [ ] Implement an Android Keystore AES key with encrypt/decrypt purposes, GCM block mode, and no padding.
- [ ] Store a versioned Base64 IV/ciphertext envelope in private `SharedPreferences`; verify the persisted envelope decrypts to the submitted value before reporting success.
- [ ] Register platform providers and change `ManagerModule` to explicit `PreferenceManager(get(), get())` construction.
- [ ] Exclude the encrypted preference file from both Android backup rule formats.
- [ ] Run focused migration/source tests and `:androidApp:assembleDebug`.
- [ ] Commit as `feat(android): encrypt bindery credentials with keystore`.

### Task 3: Scope API Headers To The Validated Bindery Origin

- [ ] Add failing origin-policy tests for same-origin paths, host case, default ports, explicit non-default ports, user-info rejection, off-origin absolute URLs, unsupported schemes, and missing URLs.
- [ ] Add a failing MockEngine test proving an absolute off-origin catalog/resource path receives no `X-Api-Key`.
- [ ] Add a failing MockEngine redirect test proving an authenticated 3xx response does not trigger a second request.
- [ ] Run the focused tests and confirm off-origin/redirect cases fail for the expected reason.
- [ ] Replace string-sliced origins with Ktor `Url` parsing and canonical `(scheme, host, effectivePort)` comparison.
- [ ] Compute each request endpoint once and pass only `binderyRequestHeadersForUrl(baseUrl, endpoint, headers)` into the request builder.
- [ ] Set `followRedirects = false` on the isolated Bindery Ktor client so custom authentication cannot be replayed to a redirect target.
- [ ] Rerun focused repository/API tests and confirm green.
- [ ] Commit as `fix(bindery): constrain api key to configured origin`.

### Task 4: Remove Global Playback And Artwork Authentication

- [ ] Add a failing source-contract test proving `ReadaloudPlaybackService` no longer calls `setDefaultRequestProperties(binderyApiKeyHeaders(...))`.
- [ ] Add a failing policy test proving audiobook descriptors carry headers only when their final URI matches the configured Bindery origin.
- [ ] Add a failing source-contract test proving `CoilBitmapLoader` receives the requested URI when selecting headers.
- [ ] Run the focused tests and confirm the current global-header behavior fails them.
- [ ] Scope each audiobook descriptor's headers through `binderyRequestHeadersForUrl` after resolving its final URI.
- [ ] Make the Media3 data source consume descriptor/request headers instead of installing a service-global API key.
- [ ] Change `CoilBitmapLoader` to `(Uri) -> Map<String, String>` and filter notification artwork headers against the configured Bindery origin.
- [ ] Rerun focused playback, player-policy, and source-contract tests.
- [ ] Commit as `fix(bindery): scope playback authentication per resource`.

### Task 5: Validate Migration And Publish Kappa1

- [ ] Rebase onto current `fork/master` without touching reader-animation worktrees.
- [ ] Run all new tests plus existing Bindery repository, optional-state, player-policy, and DI tests.
- [ ] Run `./gradlew :androidApp:assembleDebug`, `./scripts/verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-kappa1`, and `git diff --check`.
- [ ] On `emulator-5554`, install `v1.0.11-iota10`, seed a legacy plaintext Bindery key, then install the signed `kappa1` candidate in place.
- [ ] Verify the key remains usable, the legacy preference entry is gone, the encrypted envelope does not contain the plaintext, and the app launches without fatal/Koin/Keystore errors.
- [ ] Fast-forward public `master`, tag `v1.0.11-kappa1`, push, and wait for the Android release workflow; iOS remains skipped.
- [ ] Download the public APK and verify GitHub digest, APK SHA-256, established signing certificate, `versionCode=533`, `versionName=v1.0.11-kappa1`, signed upgrade, and clean startup.
- [ ] Record B17 and release evidence in this plan, the QA analysis, and the remediation roadmap; keep plaintext-read compatibility explicitly pending removal after at least one public prerelease.
- [ ] Push the evidence commit, remove the B17 worktree/branch and downloaded APK, and confirm unrelated reader worktrees are unchanged.

## Self-Review

- Spec coverage: secure migration, failed-write preservation, plaintext clearing, Android secure storage, backup exclusion, origin scoping, redirect containment, playback/resource paths, staged compatibility, Android-only release, and cleanup are each assigned to a task.
- Placeholder scan: no deferred implementation placeholders remain.
- Type consistency: all consumers use `CredentialStore`, `PreferenceManager.binderyApiKey`, and `binderyRequestHeadersForUrl`; Android alone uses `AndroidKeystoreCredentialStore`.
