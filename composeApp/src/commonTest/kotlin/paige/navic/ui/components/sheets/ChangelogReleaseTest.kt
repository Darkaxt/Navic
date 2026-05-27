package paige.navic.ui.components.sheets

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
