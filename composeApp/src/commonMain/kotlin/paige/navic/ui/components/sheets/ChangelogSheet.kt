package paige.navic.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kyant.capsule.ContinuousCapsule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_downloading_update
import navic.composeapp.generated.resources.action_downloading_update_percent
import navic.composeapp.generated.resources.action_dont_show_again
import navic.composeapp.generated.resources.action_update_app
import navic.composeapp.generated.resources.info_update
import navic.composeapp.generated.resources.info_update_install_failed
import navic.composeapp.generated.resources.notice_update_latest
import navic.composeapp.generated.resources.title_update
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalSnackbarState
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.util.core.Logger
import paige.navic.util.core.PlatformContext
import paige.navic.ui.components.common.Markdown
import paige.navic.ui.theme.defaultFont

@Serializable
data class GitHubRelease(
	@SerialName("tag_name") val tag: String,
	@SerialName("html_url") val url: String,
	@SerialName("body") val body: String,
	@SerialName("assets") val assets: List<GitHubReleaseAsset> = emptyList()
) {
	private val preferredApkAsset: GitHubReleaseAsset?
		get() = assets.preferredApkAsset()

	val updateUrl: String
		get() = preferredApkAsset?.downloadUrl ?: url

	val updateSha256Digest: String?
		get() = preferredApkAsset?.normalizedSha256Digest()

	val hasDirectApkUpdate: Boolean
		get() = updateUrl.isApkDownloadUrl()
}

@Serializable
data class GitHubReleaseAsset(
	@SerialName("name") val name: String,
	@SerialName("content_type") val contentType: String? = null,
	@SerialName("browser_download_url") val downloadUrl: String,
	@SerialName("digest") val digest: String? = null
)

private fun List<GitHubReleaseAsset>.preferredApkAsset(): GitHubReleaseAsset? {
	val exactForkApk = firstOrNull { it.name.equals("Navic.apk", ignoreCase = true) }
	if (exactForkApk != null) return exactForkApk

	val androidPackageContentType = "application/vnd.android.package-archive"
	return firstOrNull {
		it.name.endsWith(".apk", ignoreCase = true) && it.contentType.equals(androidPackageContentType, ignoreCase = true)
	} ?: firstOrNull {
		it.contentType.equals(androidPackageContentType, ignoreCase = true)
	} ?: firstOrNull {
		it.name.endsWith(".apk", ignoreCase = true)
	}
}

private fun String.isApkDownloadUrl(): Boolean =
	substringBefore('?')
		.substringBefore('#')
		.endsWith(".apk", ignoreCase = true)

private data class AppReleaseVersion(
	val major: Int,
	val minor: Int,
	val patch: Int,
	val preReleaseRank: Int,
	val preReleaseNumber: Int
) : Comparable<AppReleaseVersion> {
	override fun compareTo(other: AppReleaseVersion): Int =
		compareValuesBy(
			this,
			other,
			AppReleaseVersion::major,
			AppReleaseVersion::minor,
			AppReleaseVersion::patch,
			AppReleaseVersion::preReleaseRank,
			AppReleaseVersion::preReleaseNumber
		)
}

private val appReleaseVersionPattern =
	Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([A-Za-z]+)(\d*)?)?$""")

private fun parseAppReleaseVersion(value: String): AppReleaseVersion? {
	val match = appReleaseVersionPattern.matchEntire(value.trim()) ?: return null
	val preReleaseLabel = match.groupValues[4].takeIf { it.isNotBlank() }?.lowercase()
	val preReleaseRank = when (preReleaseLabel) {
		null -> 4
		"rc" -> 3
		"gamma" -> 2
		"beta" -> 1
		"alpha" -> 0
		else -> -1
	}
	val preReleaseNumber = match.groupValues[5]
		.takeIf { it.isNotBlank() }
		?.toIntOrNull()
		?: 0
	return AppReleaseVersion(
		major = match.groupValues[1].toIntOrNull() ?: return null,
		minor = match.groupValues[2].toIntOrNull() ?: return null,
		patch = match.groupValues[3].toIntOrNull() ?: return null,
		preReleaseRank = preReleaseRank,
		preReleaseNumber = preReleaseNumber
	)
}

internal fun shouldOfferReleaseUpdate(currentVersion: String, remoteTag: String): Boolean {
	val current = parseAppReleaseVersion(currentVersion) ?: return false
	val remote = parseAppReleaseVersion(remoteTag) ?: return false
	return remote > current
}

internal enum class UpdateCheckNotice {
	UpToDate
}

internal fun manualUpdateCheckNotice(
	currentVersion: String,
	remoteTag: String,
	manualCheck: Boolean
): UpdateCheckNotice? =
	if (manualCheck && !shouldOfferReleaseUpdate(currentVersion, remoteTag))
		UpdateCheckNotice.UpToDate
	else null

internal fun GitHubReleaseAsset.normalizedSha256Digest(): String? {
	val value = digest
		?.trim()
		?.lowercase()
		?.removePrefix("sha256:")
		?: return null
	return value.takeIf {
		it.length == 64 && it.all { char -> char in '0'..'9' || char in 'a'..'f' }
	}
}

internal fun normalizedUpdateInstallProgressPercent(progress: Float?): Int? =
	progress
		?.takeIf { it.isFinite() }
		?.coerceIn(0f, 1f)
		?.let { (it * 100f).roundToInt() }

class ChangelogViewModel(
	platformContext: PlatformContext
) : ViewModel() {
	private val _release = MutableStateFlow<GitHubRelease?>(null)
	val release = _release.asStateFlow()
	private val _isInstallingUpdate = MutableStateFlow(false)
	val isInstallingUpdate = _isInstallingUpdate.asStateFlow()
	private val _updateInstallError = MutableStateFlow<String?>(null)
	val updateInstallError = _updateInstallError.asStateFlow()
	private val _updateInstallProgress = MutableStateFlow<Float?>(null)
	val updateInstallProgress = _updateInstallProgress.asStateFlow()
	private val _updateCheckNotice = MutableStateFlow<UpdateCheckNotice?>(null)
	internal val updateCheckNotice = _updateCheckNotice.asStateFlow()

	private val updateClient = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}

	fun checkForUpdates(currentVersion: String, manualCheck: Boolean = false) {
		viewModelScope.launch {
			_updateCheckNotice.value = null
			_release.value = try {
				val release: GitHubRelease =
					updateClient.get("https://api.github.com/repos/Darkaxt/Navic/releases/latest")
						.body()
				_updateCheckNotice.value =
					manualUpdateCheckNotice(currentVersion, release.tag, manualCheck)
				if (shouldOfferReleaseUpdate(currentVersion, release.tag))
					release
				else null
			} catch (e: Exception) {
				Logger.e("ChangelogViewModel", "couldn't check for updates", e)
				null
			}
		}
	}

	fun clearRelease() {
		_updateInstallError.value = null
		_updateInstallProgress.value = null
		_release.value = null
	}

	fun clearUpdateCheckNotice() {
		_updateCheckNotice.value = null
	}

	fun installUpdate(
		updateUrl: String,
		updateSha256Digest: String?,
		updateInstaller: UpdateInstaller
	) {
		if (_isInstallingUpdate.value) return
		viewModelScope.launch {
			_isInstallingUpdate.value = true
			_updateInstallError.value = null
			_updateInstallProgress.value = null
			updateInstaller.installApk(
				updateUrl = updateUrl,
				expectedSha256Digest = updateSha256Digest,
				onProgress = { progress -> _updateInstallProgress.value = progress }
			)
				.onSuccess {
					clearRelease()
				}
				.onFailure { error ->
					Logger.e("ChangelogViewModel", "couldn't install update", error)
					_updateInstallError.value = error.message
				}
			_isInstallingUpdate.value = false
			_updateInstallProgress.value = null
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(updateCheckRequests: Int = 0) {
	val preferenceManager = koinInject<PreferenceManager>()
	val platformContext = LocalPlatformContext.current
	val snackbarState = LocalSnackbarState.current
	val uriHandler = LocalUriHandler.current
	val updateInstaller = rememberUpdateInstaller()
	val viewModel = koinViewModel<ChangelogViewModel>(
		parameters = { parametersOf(platformContext) }
	)
	val release by viewModel.release.collectAsStateWithLifecycle()
	val updateCheckNotice by viewModel.updateCheckNotice.collectAsStateWithLifecycle()
	val isInstallingUpdate by viewModel.isInstallingUpdate.collectAsStateWithLifecycle()
	val updateInstallError by viewModel.updateInstallError.collectAsStateWithLifecycle()
	val updateInstallProgress by viewModel.updateInstallProgress.collectAsStateWithLifecycle()
	val updateInstallProgressPercent = normalizedUpdateInstallProgressPercent(updateInstallProgress)
	val latestVersionMessage = stringResource(Res.string.notice_update_latest)
	LaunchedEffect(platformContext.appVersion, updateCheckRequests) {
		viewModel.checkForUpdates(
			currentVersion = platformContext.appVersion,
			manualCheck = updateCheckRequests > 0
		)
	}
	LaunchedEffect(updateCheckNotice) {
		when (updateCheckNotice) {
			UpdateCheckNotice.UpToDate -> {
				snackbarState.showSnackbar(latestVersionMessage)
				viewModel.clearUpdateCheckNotice()
			}

			null -> Unit
		}
	}

	release?.let { release ->
		ModalBottomSheet(
			onDismissRequest = { viewModel.clearRelease() },
			sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp)
			) {
				Column(
					modifier = Modifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = stringResource(Res.string.title_update),
						style = MaterialTheme.typography.titleLarge,
						fontFamily = defaultFont(round = 100f)
					)
					Text(
						stringResource(Res.string.info_update, release.tag),
						style = MaterialTheme.typography.bodyMedium
					)
				}

				Spacer(Modifier.height(8.dp))

				Markdown(
					text = release.body,
					modifier = Modifier
						.heightIn(max = 400.dp)
						.fillMaxWidth()
						.clip(MaterialTheme.shapes.large)
						.background(MaterialTheme.colorScheme.surfaceContainerHigh)
						.verticalScroll(rememberScrollState())
						.padding(10.dp)
				)

				Spacer(Modifier.height(8.dp))
				Spacer(Modifier.weight(1f))

				updateInstallError?.let { error ->
					Text(
						text = stringResource(Res.string.info_update_install_failed, error),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall,
						modifier = Modifier
							.fillMaxWidth()
							.padding(bottom = 8.dp)
					)
				}

				if (isInstallingUpdate) {
					if (updateInstallProgressPercent == null) {
						LinearProgressIndicator(
							modifier = Modifier
								.fillMaxWidth()
								.padding(bottom = 8.dp)
						)
					} else {
						LinearProgressIndicator(
							progress = {
								(updateInstallProgressPercent.toFloat() / 100f).coerceIn(0f, 1f)
							},
							modifier = Modifier
								.fillMaxWidth()
								.padding(bottom = 8.dp)
						)
					}
				}

				Button(
					onClick = {
						platformContext.clickSound()
						if (release.hasDirectApkUpdate && updateInstaller.canInstallApk) {
							viewModel.installUpdate(
								updateUrl = release.updateUrl,
								updateSha256Digest = release.updateSha256Digest,
								updateInstaller = updateInstaller
							)
						} else {
							viewModel.clearRelease()
							uriHandler.openUri(release.updateUrl)
						}
					},
					modifier = Modifier.fillMaxWidth(),
					shape = ContinuousCapsule,
					enabled = !isInstallingUpdate
				) {
					Text(
						text = when {
							isInstallingUpdate && updateInstallProgressPercent != null ->
								stringResource(
									Res.string.action_downloading_update_percent,
									updateInstallProgressPercent
								)

							isInstallingUpdate ->
								stringResource(Res.string.action_downloading_update)

							else ->
								stringResource(Res.string.action_update_app)
						},
						fontFamily = defaultFont(100)
					)
				}

				OutlinedButton(
					onClick = {
						platformContext.clickSound()
						viewModel.clearRelease()
						preferenceManager.checkForUpdates = false
					},
					modifier = Modifier.fillMaxWidth(),
					shape = ContinuousCapsule,
					enabled = !isInstallingUpdate
				) {
					Text(
						text = stringResource(Res.string.action_dont_show_again),
						fontFamily = defaultFont(100)
					)
				}
			}
		}
	}
}
