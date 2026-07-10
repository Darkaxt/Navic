# Now Playing Continuity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the display awake under the approved expanded Now Playing policy and eliminate the coverless vinyl frame when the destination cover is already cached.

**Architecture:** A pure common policy decides when the screen flag belongs to Now Playing, while small Android and iOS observers publish external-power state only when charging-only mode needs it. `CoverArt` gains an opt-in Coil memory-cache loading placeholder; only `NowPlayingArtwork` enables it, so existing artwork surfaces and genuine fallback behavior remain unchanged.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android battery broadcasts, UIKit battery notifications, Coil 3.5.0, kotlin.test, Android host tests, ADB, GitHub Actions.

---

Spec: `docs/superpowers/specs/2026-07-10-now-playing-continuity-design.md`

Baseline at plan creation:

- Branch: `master`
- Public version: `v1.0.11-theta84`
- Android version code: `512`
- Next release: `v1.0.11-theta85`
- Next Android version code: `513`
- Local branch is ahead of `fork/master` only by the committed design specification.
- Existing untracked `output/` and `releases/` directories are outside implementation commits.

## File Map

### Screen-On Policy And Settings

- Create `composeApp/src/commonMain/kotlin/paige/navic/domain/models/settings/NowPlayingScreenOnMode.kt`
  - Defines the three persisted choices and localized labels.
- Create `composeApp/src/commonMain/kotlin/paige/navic/domain/models/NowPlayingScreenOnPolicy.kt`
  - Pure decision function for active song, pause state, mode, and external power.
- Create `composeApp/src/commonTest/kotlin/paige/navic/domain/models/NowPlayingScreenOnPolicyTest.kt`
  - Exhaustive truth-table tests.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
  - Persists the mode with `Off` as default.
- Modify `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/PreferenceManagerTest.kt`
  - Verifies default and round-trip persistence.
- Modify `composeApp/src/commonMain/composeResources/values/strings.xml`
  - Adds the setting title, scope subtitle, and three mode labels.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/NowPlayingScreen.kt`
  - Adds the settings selection row.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchAppearanceRows.kt`
  - Adds the searchable version of the same selection.

### Platform Power State

- Create `composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.kt`
  - Common `expect` composable returning connected, disconnected, or unknown.
- Create `composeApp/src/androidMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.android.kt`
  - Reads and observes `ACTION_BATTERY_CHANGED` and cleans up its receiver.
- Create `composeApp/src/iosMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.ios.kt`
  - Reads and observes `UIDevice` battery state and cleans up its observer.
- Create `composeApp/src/androidHostTest/kotlin/paige/navic/ui/components/common/ExternalPowerStateAndroidTest.kt`
  - Tests Android plugged-value mapping.
- Create `composeApp/src/androidHostTest/kotlin/paige/navic/ui/components/common/ExternalPowerStateSourceTest.kt`
  - Guards expect/actual observation and cleanup contracts, including the iOS source on Windows.

### Now Playing Ownership

- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt`
  - Observes power only for visible charging-only mode and conditionally composes `KeepScreenOn()`.
- Create `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreenOnSourceTest.kt`
  - Guards full-screen-only ownership and focused observer activation.

### Cached Artwork Loading

- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/CoverArt.kt`
  - Adds an opt-in cached loading placeholder key and renders Coil's loading painter above the generated fallback.
- Modify `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/Artwork.kt`
  - Enables the cached loading placeholder for expanded artwork only.
- Create `composeApp/src/commonTest/kotlin/paige/navic/ui/components/common/CoverArtLoadingPlaceholderPolicyTest.kt`
  - Tests cache-key opt-in behavior.
- Create `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingArtworkContinuitySourceTest.kt`
  - Guards Coil request wiring, loading-layer ordering, normalization, and Now Playing opt-in.

### Release

- Modify `androidApp/build.gradle.kts`
  - Bumps `versionCode` from `512` to `513` and `versionName` from `v1.0.11-theta84` to `v1.0.11-theta85` after all implementation gates pass.

## Task 1: Pure Screen-On Policy And Persisted Mode

**Files:**

- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/settings/NowPlayingScreenOnMode.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/models/NowPlayingScreenOnPolicy.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/models/NowPlayingScreenOnPolicyTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/manager/PreferenceManagerTest.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Write the failing screen-on truth-table test**

Create `NowPlayingScreenOnPolicyTest.kt`:

```kotlin
package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingScreenOnMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingScreenOnPolicyTest {
	@Test
	fun offNeverKeepsTheScreenOn() {
		assertFalse(shouldKeepNowPlayingScreenOn(NowPlayingScreenOnMode.Off, true, false, true))
		assertFalse(shouldKeepNowPlayingScreenOn(NowPlayingScreenOnMode.Off, true, false, false))
	}

	@Test
	fun activeSongAndPlaybackAreAlwaysRequired() {
		NowPlayingScreenOnMode.entries.forEach { mode ->
			assertFalse(shouldKeepNowPlayingScreenOn(mode, false, false, true))
			assertFalse(shouldKeepNowPlayingScreenOn(mode, true, true, true))
		}
	}

	@Test
	fun chargingModeRequiresExternalPower() {
		assertTrue(
			shouldKeepNowPlayingScreenOn(
				NowPlayingScreenOnMode.WhilePlayingAndCharging,
				hasActiveSong = true,
				isPaused = false,
				isExternalPowerConnected = true
			)
		)
		assertFalse(
			shouldKeepNowPlayingScreenOn(
				NowPlayingScreenOnMode.WhilePlayingAndCharging,
				hasActiveSong = true,
				isPaused = false,
				isExternalPowerConnected = false
			)
		)
	}

	@Test
	fun whilePlayingModeDoesNotRequireExternalPower() {
		assertTrue(
			shouldKeepNowPlayingScreenOn(
				NowPlayingScreenOnMode.WhilePlaying,
				hasActiveSong = true,
				isPaused = false,
				isExternalPowerConnected = false
			)
		)
	}
}
```

- [ ] **Step 2: Add the failing preference test**

Add the `NowPlayingScreenOnMode` import and this test to `PreferenceManagerTest.kt`:

```kotlin
@Test
fun nowPlayingScreenOnModeDefaultsOffAndPersists() {
	val settings = MapSettings()
	val manager = PreferenceManager(settings)

	assertEquals(NowPlayingScreenOnMode.Off, manager.nowPlayingScreenOnMode)

	manager.nowPlayingScreenOnMode = NowPlayingScreenOnMode.WhilePlayingAndCharging

	assertEquals(
		NowPlayingScreenOnMode.WhilePlayingAndCharging,
		PreferenceManager(settings).nowPlayingScreenOnMode
	)
}
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest `
  --tests "paige.navic.domain.models.NowPlayingScreenOnPolicyTest" `
  --tests "paige.navic.domain.manager.PreferenceManagerTest"
```

Expected: compilation fails because `NowPlayingScreenOnMode`, `shouldKeepNowPlayingScreenOn`, and `nowPlayingScreenOnMode` do not exist.

- [ ] **Step 4: Add localized resources and the settings enum**

Add these resources beside the existing Now Playing options in `strings.xml`:

```xml
<string name="option_now_playing_screen_on_mode">Keep screen on</string>
<string name="subtitle_now_playing_screen_on_mode">Expanded Now Playing only</string>
<string name="option_now_playing_screen_on_off">Off</string>
<string name="option_now_playing_screen_on_playing_charging">While playing and charging</string>
<string name="option_now_playing_screen_on_playing">While playing</string>
```

Create `NowPlayingScreenOnMode.kt`:

```kotlin
package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_now_playing_screen_on_off
import navic.composeapp.generated.resources.option_now_playing_screen_on_playing
import navic.composeapp.generated.resources.option_now_playing_screen_on_playing_charging
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingScreenOnMode(val displayName: StringResource) {
	Off(Res.string.option_now_playing_screen_on_off),
	WhilePlayingAndCharging(Res.string.option_now_playing_screen_on_playing_charging),
	WhilePlaying(Res.string.option_now_playing_screen_on_playing)
}
```

- [ ] **Step 5: Implement the pure policy**

Create `NowPlayingScreenOnPolicy.kt`:

```kotlin
package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingScreenOnMode

fun shouldKeepNowPlayingScreenOn(
	mode: NowPlayingScreenOnMode,
	hasActiveSong: Boolean,
	isPaused: Boolean,
	isExternalPowerConnected: Boolean
): Boolean {
	if (!hasActiveSong || isPaused) return false

	return when (mode) {
		NowPlayingScreenOnMode.Off -> false
		NowPlayingScreenOnMode.WhilePlayingAndCharging -> isExternalPowerConnected
		NowPlayingScreenOnMode.WhilePlaying -> true
	}
}
```

- [ ] **Step 6: Persist the preference**

Import `NowPlayingScreenOnMode` in `PreferenceManager.kt` and add this beside the other Now Playing preferences:

```kotlin
var nowPlayingScreenOnMode by preference(NowPlayingScreenOnMode.Off)
```

- [ ] **Step 7: Run the focused tests and verify GREEN**

Run the command from Step 3.

Expected: `NowPlayingScreenOnPolicyTest` and `PreferenceManagerTest` pass.

- [ ] **Step 8: Commit the policy and preference**

```powershell
git add -- `
  composeApp/src/commonMain/composeResources/values/strings.xml `
  composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt `
  composeApp/src/commonMain/kotlin/paige/navic/domain/models/settings/NowPlayingScreenOnMode.kt `
  composeApp/src/commonMain/kotlin/paige/navic/domain/models/NowPlayingScreenOnPolicy.kt `
  composeApp/src/commonTest/kotlin/paige/navic/domain/manager/PreferenceManagerTest.kt `
  composeApp/src/commonTest/kotlin/paige/navic/domain/models/NowPlayingScreenOnPolicyTest.kt
git commit -m "Add Now Playing screen-on policy"
```

## Task 2: Cross-Platform External-Power Observation

**Files:**

- Create: `composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.android.kt`
- Create: `composeApp/src/iosMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.ios.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/components/common/ExternalPowerStateAndroidTest.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/ui/components/common/ExternalPowerStateSourceTest.kt`

- [ ] **Step 1: Write the failing Android mapping test**

Create `ExternalPowerStateAndroidTest.kt`:

```kotlin
package paige.navic.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalPowerStateAndroidTest {
	@Test
	fun pluggedValuesMapToExternalPower() {
		assertEquals(null, externalPowerConnectedFromPluggedValue(null))
		assertEquals(null, externalPowerConnectedFromPluggedValue(-1))
		assertEquals(false, externalPowerConnectedFromPluggedValue(0))
		assertEquals(true, externalPowerConnectedFromPluggedValue(1))
		assertEquals(true, externalPowerConnectedFromPluggedValue(2))
		assertEquals(true, externalPowerConnectedFromPluggedValue(4))
		assertEquals(true, externalPowerConnectedFromPluggedValue(8))
	}
}
```

- [ ] **Step 2: Write the failing expect/actual source contract**

Create `ExternalPowerStateSourceTest.kt`:

```kotlin
package paige.navic.ui.components.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class ExternalPowerStateSourceTest {
	@Test
	fun commonAndPlatformSourcesObserveAndReleasePowerState() {
		val common = source("commonMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.kt")
		val android = source("androidMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.android.kt")
		val ios = source("iosMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.ios.kt")

		assertContains(common, "expect fun rememberExternalPowerConnected(): Boolean?")
		assertContains(android, "Intent.ACTION_BATTERY_CHANGED")
		assertContains(android, "context.unregisterReceiver(receiver)")
		assertContains(ios, "UIDeviceBatteryStateDidChangeNotification")
		assertContains(ios, "NSNotificationCenter.defaultCenter.removeObserver(observer)")
		assertContains(ios, "if (!wasBatteryMonitoringEnabled)")
	}

	private fun source(path: String): String =
		listOf(File("src/$path"), File("composeApp/src/$path"))
			.firstOrNull(File::isFile)
			?.readText()
			?: error("Could not locate $path")
}
```

- [ ] **Step 3: Run both tests and verify RED**

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest `
  --tests "paige.navic.ui.components.common.ExternalPowerStateAndroidTest" `
  --tests "paige.navic.ui.components.common.ExternalPowerStateSourceTest"
```

Expected: compilation fails because the mapping function and expect/actual files do not exist.

- [ ] **Step 4: Add the common expect API**

Create `ExternalPowerState.kt`:

```kotlin
package paige.navic.ui.components.common

import androidx.compose.runtime.Composable

@Composable
expect fun rememberExternalPowerConnected(): Boolean?
```

- [ ] **Step 5: Add the Android observer and pure mapping**

Create `ExternalPowerState.android.kt`:

```kotlin
package paige.navic.ui.components.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import paige.navic.util.core.Logger

internal fun externalPowerConnectedFromPluggedValue(plugged: Int?): Boolean? =
	plugged?.takeIf { it >= 0 }?.let { it != 0 }

private fun Intent?.externalPowerConnected(): Boolean? =
	externalPowerConnectedFromPluggedValue(
		this?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
	)

@Composable
actual fun rememberExternalPowerConnected(): Boolean? {
	val context = LocalContext.current
	var connected by remember(context) { mutableStateOf<Boolean?>(null) }

	DisposableEffect(context) {
		val receiver = object : BroadcastReceiver() {
			override fun onReceive(context: Context?, intent: Intent?) {
				connected = intent.externalPowerConnected()
			}
		}
		var registered = false
		try {
			@Suppress("DEPRECATION")
			val sticky = context.registerReceiver(
				receiver,
				IntentFilter(Intent.ACTION_BATTERY_CHANGED)
			)
			registered = true
			connected = sticky.externalPowerConnected()
		} catch (error: RuntimeException) {
			connected = null
			Logger.w("ExternalPowerState", "Unable to observe Android external power", error)
		}

		onDispose {
			if (registered) {
				try {
					context.unregisterReceiver(receiver)
				} catch (error: RuntimeException) {
					Logger.w("ExternalPowerState", "Unable to release Android power observer", error)
				}
			}
		}
	}

	return connected
}
```

The nonzero plugged mapping covers AC, USB, wireless, and dock values while keeping plugged/full true.

- [ ] **Step 6: Add the iOS observer**

Create `ExternalPowerState.ios.kt`:

```kotlin
@file:OptIn(ExperimentalForeignApi::class)

package paige.navic.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryStateCharging
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification
import platform.UIKit.UIDeviceBatteryStateFull
import platform.UIKit.UIDeviceBatteryStateUnplugged

private fun currentExternalPowerConnected(device: UIDevice): Boolean? =
	when (device.batteryState) {
		UIDeviceBatteryStateCharging,
		UIDeviceBatteryStateFull -> true
		UIDeviceBatteryStateUnplugged -> false
		else -> null
	}

@Composable
actual fun rememberExternalPowerConnected(): Boolean? {
	val device = UIDevice.currentDevice
	var connected by remember(device) { mutableStateOf<Boolean?>(null) }

	DisposableEffect(device) {
		val wasBatteryMonitoringEnabled = device.batteryMonitoringEnabled
		device.batteryMonitoringEnabled = true
		connected = currentExternalPowerConnected(device)
		val observer = NSNotificationCenter.defaultCenter.addObserverForName(
			name = UIDeviceBatteryStateDidChangeNotification,
			`object` = device,
			queue = NSOperationQueue.mainQueue
		) { _ ->
			connected = currentExternalPowerConnected(device)
		}

		onDispose {
			NSNotificationCenter.defaultCenter.removeObserver(observer)
			if (!wasBatteryMonitoringEnabled) {
				device.batteryMonitoringEnabled = false
			}
		}
	}

	return connected
}
```

- [ ] **Step 7: Run the focused Android host tests and verify GREEN**

Run the command from Step 3.

Expected: both power-state tests pass. The iOS source contract passes on Windows; native compilation is covered by the macOS workflow gate in Task 6.

- [ ] **Step 8: Commit the platform power observer**

```powershell
git add -- `
  composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.kt `
  composeApp/src/androidMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.android.kt `
  composeApp/src/iosMain/kotlin/paige/navic/ui/components/common/ExternalPowerState.ios.kt `
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/components/common/ExternalPowerStateAndroidTest.kt `
  composeApp/src/androidHostTest/kotlin/paige/navic/ui/components/common/ExternalPowerStateSourceTest.kt
git commit -m "Observe external power for Now Playing"
```

## Task 3: Settings And Expanded Now Playing Ownership

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/NowPlayingScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchAppearanceRows.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreenOnSourceTest.kt`

- [ ] **Step 1: Write the failing ownership source test**

Create `NowPlayingScreenOnSourceTest.kt`:

```kotlin
package paige.navic.ui.screens.nowPlaying

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class NowPlayingScreenOnSourceTest {
	@Test
	fun fullNowPlayingOwnsTheScreenFlagAndChargingObserver() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt"
		).readText()

		assertContains(source, "val isNowPlayingVisible = currentScreen is Screen.NowPlaying")
		assertContains(source, "screenOnMode == NowPlayingScreenOnMode.WhilePlayingAndCharging")
		assertContains(source, "rememberExternalPowerConnected() == true")
		assertContains(source, "shouldKeepNowPlayingScreenOn(")
		assertContains(source, "if (isNowPlayingVisible && keepNowPlayingScreenOn)")
		assertContains(source, "KeepScreenOn()")
	}

	@Test
	fun settingAndSearchUseTheSamePersistedMode() {
		val settings = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/settings/NowPlayingScreen.kt"
		).readText()
		val search = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchAppearanceRows.kt"
		).readText()

		assertContains(settings, "selection = preferenceManager.nowPlayingScreenOnMode")
		assertContains(settings, "onSelect = { preferenceManager.nowPlayingScreenOnMode = it }")
		assertContains(search, "id = \"now-playing.screen-on-mode\"")
		assertContains(search, "selection = preferenceManager.nowPlayingScreenOnMode")
	}
}
```

- [ ] **Step 2: Run the source test and verify RED**

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest `
  --tests "paige.navic.ui.screens.nowPlaying.NowPlayingScreenOnSourceTest"
```

Expected: assertions fail because neither the settings rows nor screen ownership wiring exists.

- [ ] **Step 3: Add the Now Playing selection row**

Import the setting title/subtitle resources and `NowPlayingScreenOnMode` in the settings screen. Add this row near the top of the main Now Playing form, after swipe-to-skip:

```kotlin
SettingSelectionRow(
	items = NowPlayingScreenOnMode.entries.toImmutableList(),
	label = { stringResource(it.displayName) },
	selection = preferenceManager.nowPlayingScreenOnMode,
	onSelect = { preferenceManager.nowPlayingScreenOnMode = it },
	description = stringResource(Res.string.subtitle_now_playing_screen_on_mode),
	title = { Text(stringResource(Res.string.option_now_playing_screen_on_mode)) }
)
```

- [ ] **Step 4: Add the searchable selection**

Add this row in `settingsSearchAppearanceRows`, with the other Now Playing behavior rows:

```kotlin
add(selectionRow(
	id = "now-playing.screen-on-mode",
	path = path(nowPlaying, behaviour),
	title = stringResource(Res.string.option_now_playing_screen_on_mode),
	subtitle = stringResource(Res.string.subtitle_now_playing_screen_on_mode),
	keywords = listOf("screen", "awake", "charging", "power", "playback"),
	items = NowPlayingScreenOnMode.entries,
	label = { stringResource(it.displayName) },
	selection = preferenceManager.nowPlayingScreenOnMode,
	onSelect = { preferenceManager.nowPlayingScreenOnMode = it }
))
```

- [ ] **Step 5: Wire composition-scoped ownership**

In `NowPlayingScreen.kt`, import `NowPlayingScreenOnMode`, `shouldKeepNowPlayingScreenOn`, `KeepScreenOn`, and `rememberExternalPowerConnected`. Immediately after resolving `currentScreen`, add:

```kotlin
val isNowPlayingVisible = currentScreen is Screen.NowPlaying
val screenOnMode = preferenceManager.nowPlayingScreenOnMode
val shouldObserveExternalPower =
	isNowPlayingVisible &&
		screenOnMode == NowPlayingScreenOnMode.WhilePlayingAndCharging
val isExternalPowerConnected = if (shouldObserveExternalPower) {
	rememberExternalPowerConnected() == true
} else {
	false
}
```

After `playerState` and `song` are available, add:

```kotlin
val keepNowPlayingScreenOn = shouldKeepNowPlayingScreenOn(
	mode = screenOnMode,
	hasActiveSong = song != null,
	isPaused = playerState.isPaused,
	isExternalPowerConnected = isExternalPowerConnected
)
if (isNowPlayingVisible && keepNowPlayingScreenOn) {
	KeepScreenOn()
}
```

Keep the existing broader `isPlayerCurrent` behavior unchanged for Queue and Playback Speed rendering. Only the new screen-retention flag uses the narrower `isNowPlayingVisible` check.

- [ ] **Step 6: Run policy, preference, platform, and source tests**

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest `
  --tests "paige.navic.domain.models.NowPlayingScreenOnPolicyTest" `
  --tests "paige.navic.domain.manager.PreferenceManagerTest" `
  --tests "paige.navic.ui.components.common.ExternalPowerStateAndroidTest" `
  --tests "paige.navic.ui.components.common.ExternalPowerStateSourceTest" `
  --tests "paige.navic.ui.screens.nowPlaying.NowPlayingScreenOnSourceTest"
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit settings and ownership wiring**

```powershell
git add -- `
  composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/NowPlayingScreen.kt `
  composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchAppearanceRows.kt `
  composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt `
  composeApp/src/commonTest/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreenOnSourceTest.kt
git commit -m "Apply screen-on mode to Now Playing"
```

## Task 4: Cached Destination Artwork During Loading

**Files:**

- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/CoverArt.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/Artwork.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/ui/components/common/CoverArtLoadingPlaceholderPolicyTest.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingArtworkContinuitySourceTest.kt`

- [ ] **Step 1: Write the failing cache-key policy test**

Create `CoverArtLoadingPlaceholderPolicyTest.kt`:

```kotlin
package paige.navic.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverArtLoadingPlaceholderPolicyTest {
	@Test
	fun cachedPlaceholderRequiresOptInAndAResolvedKey() {
		assertNull(cachedCoverArtLoadingPlaceholderKey(false, "album-1:trim-whitespace"))
		assertNull(cachedCoverArtLoadingPlaceholderKey(true, null))
		assertNull(cachedCoverArtLoadingPlaceholderKey(true, ""))
		assertEquals(
			"album-1:trim-whitespace",
			cachedCoverArtLoadingPlaceholderKey(true, "album-1:trim-whitespace")
		)
	}
}
```

- [ ] **Step 2: Write the failing Coil and Now Playing source contract**

Create `NowPlayingArtworkContinuitySourceTest.kt`:

```kotlin
package paige.navic.ui.screens.nowPlaying

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class NowPlayingArtworkContinuitySourceTest {
	@Test
	fun coverArtUsesTheMatchingMemoryEntryOnlyDuringLoading() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/ui/components/common/CoverArt.kt"
		).readText()

		assertContains(source, "useCachedLoadingPlaceholder: Boolean = false")
		assertContains(source, "placeholderMemoryCacheKey(cachedLoadingPlaceholderKey)")
		assertContains(source, "if (cachedLoadingPlaceholderKey != null)")
		assertContains(source, "SubcomposeAsyncImageContent()")
		assertContains(source, "CoverArtFallback(")
	}

	@Test
	fun nowPlayingOptsInWithTheSameNormalizationIdentityAsUpNext() {
		val artwork = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/Artwork.kt"
		).readText()
		val upNext = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/rows/UpNextRow.kt"
		).readText()

		assertContains(artwork, "useCachedLoadingPlaceholder = true")
		assertContains(artwork, "normalization = CoverArtNormalization.TrimWhitespace")
		assertContains(upNext, "normalization = CoverArtNormalization.TrimWhitespace")
	}
}
```

- [ ] **Step 3: Run both tests and verify RED**

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest `
  --tests "paige.navic.ui.components.common.CoverArtLoadingPlaceholderPolicyTest" `
  --tests "paige.navic.ui.screens.nowPlaying.NowPlayingArtworkContinuitySourceTest"
```

Expected: compilation or assertions fail because the opt-in, helper, Coil request wiring, and Now Playing argument do not exist.

- [ ] **Step 4: Add the opt-in and pure cache-key helper**

Add the parameter to `CoverArt` after `crossfadeMs`:

```kotlin
useCachedLoadingPlaceholder: Boolean = false,
```

Add this top-level internal helper near the existing cache-key helpers:

```kotlin
internal fun cachedCoverArtLoadingPlaceholderKey(
	enabled: Boolean,
	resolvedImageCacheKey: String?
): String? = resolvedImageCacheKey
	?.takeIf { enabled && it.isNotBlank() }
```

After `resolvedImageCacheKey` is calculated, resolve the opt-in key:

```kotlin
val cachedLoadingPlaceholderKey = cachedCoverArtLoadingPlaceholderKey(
	enabled = useCachedLoadingPlaceholder,
	resolvedImageCacheKey = resolvedImageCacheKey
)
```

- [ ] **Step 5: Wire Coil's placeholder memory key without changing the final request**

Include `cachedLoadingPlaceholderKey` in the `remember` keys for `model`. Inside `ImageRequest.Builder.apply`, before request headers, add:

```kotlin
if (cachedLoadingPlaceholderKey != null) {
	placeholderMemoryCacheKey(cachedLoadingPlaceholderKey)
}
```

Keep `.memoryCacheKey(resolvedImageCacheKey)`, `.diskCacheKey(resolvedImageCacheKey)`, normal cache policies, destination constraints, and crossfade unchanged. Coil may use a smaller matching bitmap as the immediate loading painter while continuing the normal destination request.

- [ ] **Step 6: Layer the cached loading painter above the generated fallback**

Replace the current `loading` body with:

```kotlin
loading = {
	Box(Modifier.fillMaxSize()) {
		CoverArtFallback(
			fallbackContent = fallbackContent,
			generatedArtwork = resolvedGeneratedArtwork,
			modifier = Modifier.fillMaxSize(),
			showLoadingIndicator = true
		)
		if (cachedLoadingPlaceholderKey != null) {
			SubcomposeAsyncImageContent()
		}
	}
},
```

The generated fallback remains underneath for cache misses or transparent placeholder regions. The success and error slots remain unchanged.

- [ ] **Step 7: Enable the behavior only in expanded Now Playing artwork**

Add this argument to the `CoverArt` call in `NowPlayingArtwork`, next to `normalization`:

```kotlin
useCachedLoadingPlaceholder = true,
```

Do not enable it in `PlaybackSongCoverArt`, generic grids, album pages, artist pages, Lyrics, or the mini player.

- [ ] **Step 8: Run the focused artwork tests and verify GREEN**

Run the command from Step 3.

Expected: both tests pass.

- [ ] **Step 9: Run the existing Now Playing artwork and Up Next tests**

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest `
  --tests "paige.navic.domain.models.NowPlayingArtworkRotationPolicyTest" `
  --tests "paige.navic.domain.models.NowPlayingUpNextPolicyTest" `
  --tests "paige.navic.ui.screens.nowPlaying.NowPlayingWideLandscapeSourceTest" `
  --tests "paige.navic.ui.components.common.CoverArtLoadingPlaceholderPolicyTest" `
  --tests "paige.navic.ui.screens.nowPlaying.NowPlayingArtworkContinuitySourceTest"
```

Expected: all selected tests pass.

- [ ] **Step 10: Commit the artwork continuity fix**

```powershell
git add -- `
  composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/CoverArt.kt `
  composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/Artwork.kt `
  composeApp/src/commonTest/kotlin/paige/navic/ui/components/common/CoverArtLoadingPlaceholderPolicyTest.kt `
  composeApp/src/commonTest/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingArtworkContinuitySourceTest.kt
git commit -m "Keep cached artwork through song transitions"
```

## Task 5: Local Verification

**Files:** No new files unless a failing gate requires a scoped correction.

- [ ] **Step 1: Run every focused continuity test together**

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest `
  --tests "paige.navic.domain.models.NowPlayingScreenOnPolicyTest" `
  --tests "paige.navic.domain.manager.PreferenceManagerTest" `
  --tests "paige.navic.ui.components.common.ExternalPowerStateAndroidTest" `
  --tests "paige.navic.ui.components.common.ExternalPowerStateSourceTest" `
  --tests "paige.navic.ui.screens.nowPlaying.NowPlayingScreenOnSourceTest" `
  --tests "paige.navic.ui.components.common.CoverArtLoadingPlaceholderPolicyTest" `
  --tests "paige.navic.ui.screens.nowPlaying.NowPlayingArtworkContinuitySourceTest" `
  --tests "paige.navic.domain.models.NowPlayingArtworkRotationPolicyTest" `
  --tests "paige.navic.domain.models.NowPlayingUpNextPolicyTest" `
  --tests "paige.navic.ui.screens.nowPlaying.NowPlayingWideLandscapeSourceTest"
```

Expected: `BUILD SUCCESSFUL` with all selected tests passing.

- [ ] **Step 2: Run the full Android host suite**

```powershell
.\gradlew.bat --no-daemon :composeApp:testAndroidHostTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build the Android debug APK as the local compile gate**

```powershell
.\gradlew.bat --no-daemon :androidApp:assembleDebug --stacktrace
```

Expected: `BUILD SUCCESSFUL` and `androidApp/build/outputs/apk/debug/Navic.apk` exists. Do not replace the signed `darkaxt.navic` package with this debug build.

- [ ] **Step 4: Check patch hygiene and commit boundaries**

```powershell
git diff --check
git status --short
git log -5 --oneline
```

Expected: no whitespace errors; only `output/` and `releases/` remain untracked; implementation is split into the four focused commits from Tasks 1-4.

## Task 6: Version, Cross-Platform CI, And Signed Release

**Files:**

- Modify: `androidApp/build.gradle.kts`

- [ ] **Step 1: Verify remote and release identity have not moved**

```powershell
git fetch fork --prune
git log --left-right --cherry-pick --oneline master...fork/master
git tag --list "v1.0.11-theta85"
Select-String -Path androidApp/build.gradle.kts -Pattern "versionCode|versionName"
```

Expected before the version commit: local implementation commits are the only left-side entries, `fork/master` has no right-side entries, the theta85 tag does not exist, and Android still reports `512` / `v1.0.11-theta84`.

- [ ] **Step 2: Verify the theta85 version guard is RED**

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta85
```

Expected: failure reporting current `v1.0.11-theta84`.

- [ ] **Step 3: Bump the Android release identity**

In `androidApp/build.gradle.kts`, set:

```kotlin
versionCode = 513
versionName = "v1.0.11-theta85"
```

- [ ] **Step 4: Verify the version guard is GREEN and commit**

```powershell
.\scripts\verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-theta85
git diff --check
git add -- androidApp/build.gradle.kts
git commit -m "Prepare v1.0.11-theta85"
```

Expected: version verification passes and the release identity commit contains only `androidApp/build.gradle.kts`.

- [ ] **Step 5: Push master and run the macOS iOS compile/package gate**

```powershell
git push fork master
$headSha = (git rev-parse HEAD).Trim()
gh workflow run build.yml --repo Darkaxt/Navic --ref master -f build_ios=true
do {
	$runs = gh run list `
	  --repo Darkaxt/Navic `
	  --workflow "Build Navic" `
	  --branch master `
	  --event workflow_dispatch `
	  --limit 10 `
	  --json databaseId,headSha,createdAt | ConvertFrom-Json
	$run = $runs | Where-Object { $_.headSha -eq $headSha } | Select-Object -First 1
	if ($null -eq $run) {
		Write-Host "Waiting for the workflow-dispatch run for $headSha"
		Start-Sleep -Seconds 5
	}
} while ($null -eq $run)
$runId = $run.databaseId
gh run watch $runId --repo Darkaxt/Navic --exit-status
gh run view $runId --repo Darkaxt/Navic --json conclusion,url,jobs
```

Expected: the workflow concludes `success`; Android release packaging and the macOS `Build iOS IPA` job both pass. The watch command is a release-monitoring heartbeat, not a cancellation timeout.

- [ ] **Step 6: Tag and publish the signed theta85 release**

```powershell
git tag -a v1.0.11-theta85 -m "v1.0.11-theta85"
.\scripts\publish-github-release.ps1 `
  -Tag v1.0.11-theta85 `
  -Repo Darkaxt/Navic `
  -Remote fork `
  -Branch master `
  -AllowPublicRelease `
  -ReleaseReadinessNote "Now Playing screen-on modes and cached artwork transition continuity passed focused tests, the full Android host suite, Android assembly, and macOS iOS packaging."
```

Expected: the script pushes the tag, watches the tag workflow to success, and reports the published GitHub release with `Navic.apk`.

- [ ] **Step 7: Download and verify the published artifact**

```powershell
New-Item -ItemType Directory -Force releases\v1.0.11-theta85 | Out-Null
gh release download v1.0.11-theta85 `
  --repo Darkaxt/Navic `
  --pattern Navic.apk `
  --dir releases\v1.0.11-theta85 `
  --clobber
gh release view v1.0.11-theta85 `
  --repo Darkaxt/Navic `
  --json tagName,url,publishedAt,assets
Get-FileHash releases\v1.0.11-theta85\Navic.apk -Algorithm SHA256
```

Expected: release metadata names `v1.0.11-theta85`, the asset list includes `Navic.apk`, and a SHA-256 digest is recorded.

## Task 7: Physical Tablet Acceptance

**Files:** Runtime evidence may be placed under existing untracked `output/`; do not commit recordings or extracted frames.

- [ ] **Step 1: Reconnect and install the signed release**

```powershell
adb devices -l
$serial = "R52W60CFTRL"
adb -s $serial install -r releases\v1.0.11-theta85\Navic.apk
adb -s $serial shell dumpsys package darkaxt.navic | Select-String -Pattern "versionName|versionCode|lastUpdateTime"
adb -s $serial shell monkey -p darkaxt.navic 1
```

Expected: the physical Samsung tablet is listed, install returns `Success`, and package state reports `versionName=v1.0.11-theta85` and `versionCode=513`.

- [ ] **Step 2: Prepare reversible display and battery-state observation**

Record the current screen-off value and use a short device sleep interval only for this acceptance test:

```powershell
$originalScreenOffTimeout = (adb -s $serial shell settings get system screen_off_timeout).Trim()
adb -s $serial shell settings put system screen_off_timeout 15000
adb -s $serial shell dumpsys battery reset
```

Use the Navic UI to open Settings > Appearance > Now Playing and select each mode. Use these commands to inspect ownership and playback state:

```powershell
adb -s $serial shell dumpsys window windows | Select-String -Pattern "mHoldScreenWindow|KEEP_SCREEN_ON|mCurrentFocus"
adb -s $serial shell dumpsys power | Select-String -Pattern "mWakefulness|mHoldingDisplaySuspendBlocker|Display Power"
adb -s $serial shell cmd media_session dispatch pause
adb -s $serial shell cmd media_session dispatch play
```

- [ ] **Step 3: Verify all three screen-on modes**

For `Off`:

- Start playback and remain on expanded Now Playing.
- Verify no Navic hold-screen owner appears.
- Leave the device untouched beyond the temporary 15-second device setting and verify normal sleep remains possible.

For `While playing and charging`:

```powershell
adb -s $serial shell dumpsys battery unplug
adb -s $serial shell dumpsys window windows | Select-String -Pattern "mHoldScreenWindow|KEEP_SCREEN_ON"
adb -s $serial shell dumpsys battery set ac 1
adb -s $serial shell dumpsys window windows | Select-String -Pattern "mHoldScreenWindow|KEEP_SCREEN_ON"
adb -s $serial shell cmd media_session dispatch pause
adb -s $serial shell dumpsys window windows | Select-String -Pattern "mHoldScreenWindow|KEEP_SCREEN_ON"
```

Expected: no hold while simulated unplugged, a Navic hold while powered and playing, and no hold after pause.

For `While playing`:

- Simulate unplugged state, resume playback, and verify Navic holds the screen.
- Collapse Now Playing or open a different top-level screen and verify the new hold is released.
- Return to Now Playing, pause, and verify the hold is released.

- [ ] **Step 4: Restore device state even if a check fails**

```powershell
adb -s $serial shell dumpsys battery reset
adb -s $serial shell settings put system screen_off_timeout $originalScreenOffTimeout
adb -s $serial shell settings get system screen_off_timeout
```

Expected: battery simulation is cleared and the original screen-off setting is restored exactly.

- [ ] **Step 5: Capture an automatic shuffled transition**

In Navic:

- Enable Up Next artwork and set the count to five.
- Start shuffled playback and verify the first upcoming cover is visible.
- Move the current song near its natural end using the progress control; do not press Next.

Start recording:

```powershell
New-Item -ItemType Directory -Force output\theta85-now-playing | Out-Null
$recording = Start-Process `
  -FilePath adb `
  -ArgumentList @("-s", $serial, "shell", "screenrecord", "/sdcard/theta85-transition.mp4") `
  -WindowStyle Hidden `
  -PassThru
```

After the automatic title/artist transition is visible, stop recording and pull it:

```powershell
adb -s $serial shell pkill -INT screenrecord
$recording.WaitForExit()
adb -s $serial pull /sdcard/theta85-transition.mp4 output\theta85-now-playing\theta85-transition.mp4
adb -s $serial shell rm /sdcard/theta85-transition.mp4
```

- [ ] **Step 6: Inspect transition frames**

```powershell
New-Item -ItemType Directory -Force output\theta85-now-playing\frames | Out-Null
ffmpeg -i output\theta85-now-playing\theta85-transition.mp4 `
  -vf fps=20 `
  output\theta85-now-playing\frames\frame-%05d.png
```

Inspect every frame from the last old-song frame through the first stable new-song frame.

Expected:

- The new song's matching cached cover is visible as soon as the destination vinyl appears.
- No generated coverless vinyl frame appears between the old and new covers.
- No previous-song cover is shown under the new song metadata.
- A deliberately uncached or genuinely missing cover still uses generated fallback artwork without a blank surface.

- [ ] **Step 7: Verify final repository and release state**

```powershell
git status --short --branch
git log -6 --oneline --decorate
git ls-remote --tags fork refs/tags/v1.0.11-theta85
gh release view v1.0.11-theta85 --repo Darkaxt/Navic --json url,tagName,assets
```

Expected: `master` matches `fork/master`; only pre-existing untracked runtime directories remain; the remote tag and GitHub release both exist with `Navic.apk`.

## Completion Criteria

- All three setting values are present, searchable, persisted, and default to `Off`.
- Only visible expanded Now Playing can acquire the new screen flag.
- Active playback is required; pause, unplug in charging-only mode, collapse, and navigation release the flag.
- Android and iOS observers release their platform subscriptions when composition ends.
- Power state is observed only when visible charging-only mode requires it.
- Now Playing uses a matching normalized memory-cache image during the larger destination request's loading state.
- The generated loading/error artwork remains available for cache misses and genuine failures.
- Queue order, five-item download prefetching, playback service behavior, Lyrics, Reader, LidaClips, and mini-player screen policies are unchanged.
- Focused tests, full Android host tests, Android assembly, macOS iOS packaging, signed release publication, and physical tablet acceptance all pass.
- `v1.0.11-theta85` / `513` is installed and verified on the physical tablet.
- No cancellation timeout, wake lock, or unrelated refactor is introduced.
