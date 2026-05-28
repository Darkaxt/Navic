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
}
