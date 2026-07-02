package paige.navic.ui.components.sheets

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChangelogReleaseTest {
	@Test
	fun updateUrlPrefersNavicApkAsset() {
		val release = GitHubRelease(
			tag = "v1.0.0-alpha47",
			url = "https://github.com/Darkaxt/Navic/releases/tag/v1.0.0-alpha47",
			body = "Release notes",
			assets = listOf(
				GitHubReleaseAsset(
					name = "Navic.ipa",
					contentType = "application/octet-stream",
					downloadUrl = "https://github.com/Darkaxt/Navic/releases/download/v1.0.0-alpha47/Navic.ipa"
				),
				GitHubReleaseAsset(
					name = "Navic.apk",
					contentType = "application/vnd.android.package-archive",
					downloadUrl = "https://github.com/Darkaxt/Navic/releases/download/v1.0.0-alpha47/Navic.apk"
				)
			)
		)

		assertEquals(
			"https://github.com/Darkaxt/Navic/releases/download/v1.0.0-alpha47/Navic.apk",
			release.updateUrl
		)
	}

	@Test
	fun updateReleaseReportsDirectApkWhenApkAssetIsAvailable() {
		val release = GitHubRelease(
			tag = "v1.0.0-alpha47",
			url = "https://github.com/Darkaxt/Navic/releases/tag/v1.0.0-alpha47",
			body = "Release notes",
			assets = listOf(
				GitHubReleaseAsset(
					name = "Navic.apk",
					contentType = "application/vnd.android.package-archive",
					downloadUrl = "https://github.com/Darkaxt/Navic/releases/download/v1.0.0-alpha47/Navic.apk"
				)
			)
		)

		assertEquals(true, release.hasDirectApkUpdate)
	}

	@Test
	fun updateDigestUsesPreferredApkAssetSha256Digest() {
		val release = GitHubRelease(
			tag = "v1.0.0-alpha47",
			url = "https://github.com/Darkaxt/Navic/releases/tag/v1.0.0-alpha47",
			body = "Release notes",
			assets = listOf(
				GitHubReleaseAsset(
					name = "Navic.ipa",
					contentType = "application/octet-stream",
					downloadUrl = "https://github.com/Darkaxt/Navic/releases/download/v1.0.0-alpha47/Navic.ipa",
					digest = "sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
				),
				GitHubReleaseAsset(
					name = "Navic.apk",
					contentType = "application/vnd.android.package-archive",
					downloadUrl = "https://github.com/Darkaxt/Navic/releases/download/v1.0.0-alpha47/Navic.apk",
					digest = "sha256:A400E600AD25DE6C09912BF52C70F4FAC8BD6321CE7741FB94E87A6F6EFF24D2"
				)
			)
		)

		assertEquals(
			"a400e600ad25de6c09912bf52c70f4fac8bd6321ce7741fb94e87a6f6eff24d2",
			release.updateSha256Digest
		)
	}

	@Test
	fun updateDigestIgnoresMalformedApkAssetDigest() {
		val release = GitHubRelease(
			tag = "v1.0.0-alpha47",
			url = "https://github.com/Darkaxt/Navic/releases/tag/v1.0.0-alpha47",
			body = "Release notes",
			assets = listOf(
				GitHubReleaseAsset(
					name = "Navic.apk",
					contentType = "application/vnd.android.package-archive",
					downloadUrl = "https://github.com/Darkaxt/Navic/releases/download/v1.0.0-alpha47/Navic.apk",
					digest = "sha256:not-a-real-digest"
				)
			)
		)

		assertEquals(null, release.updateSha256Digest)
	}

	@Test
	fun updateUrlFallsBackToReleasePageWhenApkAssetIsMissing() {
		val release = GitHubRelease(
			tag = "v1.0.0-alpha47",
			url = "https://github.com/Darkaxt/Navic/releases/tag/v1.0.0-alpha47",
			body = "Release notes",
			assets = listOf(
				GitHubReleaseAsset(
					name = "Navic.ipa",
					contentType = "application/octet-stream",
					downloadUrl = "https://github.com/Darkaxt/Navic/releases/download/v1.0.0-alpha47/Navic.ipa"
				)
			)
		)

		assertEquals(
			"https://github.com/Darkaxt/Navic/releases/tag/v1.0.0-alpha47",
			release.updateUrl
		)
	}

	@Test
	fun updateReleaseReportsNoDirectApkWhenFallingBackToReleasePage() {
		val release = GitHubRelease(
			tag = "v1.0.0-alpha47",
			url = "https://github.com/Darkaxt/Navic/releases/tag/v1.0.0-alpha47",
			body = "Release notes",
			assets = listOf(
				GitHubReleaseAsset(
					name = "Navic.ipa",
					contentType = "application/octet-stream",
					downloadUrl = "https://github.com/Darkaxt/Navic/releases/download/v1.0.0-alpha47/Navic.ipa"
				)
			)
		)

		assertEquals(false, release.hasDirectApkUpdate)
	}

	@Test
	fun updateInstallProgressPercentClampsAndRoundsProgress() {
		assertEquals(0, normalizedUpdateInstallProgressPercent(-0.1f))
		assertEquals(13, normalizedUpdateInstallProgressPercent(0.126f))
		assertEquals(100, normalizedUpdateInstallProgressPercent(1.4f))
	}

	@Test
	fun updateInstallProgressPercentIgnoresUnavailableProgress() {
		assertEquals(null, normalizedUpdateInstallProgressPercent(null))
		assertEquals(null, normalizedUpdateInstallProgressPercent(Float.NaN))
	}

	@Test
	fun updateDownloadProgressLabelUsesSinglePercentSign() {
		val strings = File("src/commonMain/composeResources/values/strings.xml").readText()

		assertFalse(
			strings.contains("name=\"action_downloading_update_percent\">Downloading update... %1\$d%%</string>"),
			"The update download label should not render a doubled percent sign."
		)
	}

	@Test
	fun updateCheckDetectsNewerBetaRelease() {
		assertEquals(
			true,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.0-beta2",
				remoteTag = "v1.0.0-beta3"
			)
		)
	}

	@Test
	fun betaReleaseOutranksHigherNumberedAlphaRelease() {
		assertEquals(
			true,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.0-alpha88",
				remoteTag = "v1.0.0-beta2"
			)
		)
	}

	@Test
	fun gammaReleaseOutranksHigherNumberedBetaRelease() {
		assertEquals(
			true,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.10-beta77",
				remoteTag = "v1.0.10-gamma1"
			)
		)
		assertEquals(
			false,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.10-gamma1",
				remoteTag = "v1.0.10-beta88"
			)
		)
	}

	@Test
	fun zetaReleaseOutranksHigherNumberedEpsilonRelease() {
		assertEquals(
			true,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.10-epsilon50",
				remoteTag = "v1.0.10-zeta1"
			)
		)
	}

	@Test
	fun patchBridgeReleaseOutranksLegacyAlphaBuilds() {
		assertEquals(
			true,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.0-alpha88",
				remoteTag = "v1.0.10-beta1"
			)
		)
	}

	@Test
	fun updateCheckDoesNotOfferSameRelease() {
		assertEquals(
			false,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.0-beta2",
				remoteTag = "v1.0.0-beta2"
			)
		)
	}

	@Test
	fun manualUpdateCheckReportsLatestVersionWhenRemoteIsNotNewer() {
		assertEquals(
			UpdateCheckNotice.UpToDate,
			manualUpdateCheckNotice(
				currentVersion = "v1.0.10-beta3",
				remoteTag = "v1.0.10-beta3",
				manualCheck = true
			)
		)

		assertEquals(
			UpdateCheckNotice.UpToDate,
			manualUpdateCheckNotice(
				currentVersion = "v1.0.10-beta3",
				remoteTag = "v1.0.10-beta2",
				manualCheck = true
			)
		)
	}

	@Test
	fun automaticUpdateCheckDoesNotReportLatestVersion() {
		assertEquals(
			null,
			manualUpdateCheckNotice(
				currentVersion = "v1.0.10-beta3",
				remoteTag = "v1.0.10-beta3",
				manualCheck = false
			)
		)
	}

	@Test
	fun manualUpdateCheckDoesNotReportLatestVersionWhenUpdateExists() {
		assertEquals(
			null,
			manualUpdateCheckNotice(
				currentVersion = "v1.0.10-beta3",
				remoteTag = "v1.0.10-beta4",
				manualCheck = true
			)
		)
	}

	@Test
	fun stableReleaseOutranksSameVersionBetaRelease() {
		assertEquals(
			true,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.0-beta2",
				remoteTag = "v1.0.0"
			)
		)
		assertEquals(
			false,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.0",
				remoteTag = "v1.0.0-beta2"
			)
		)
	}

	@Test
	fun higherPatchPrereleaseOutranksLowerStableRelease() {
		assertEquals(
			true,
			shouldOfferReleaseUpdate(
				currentVersion = "v1.0.0",
				remoteTag = "v1.0.1-alpha1"
			)
		)
	}
}
