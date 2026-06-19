package paige.navic.ui.screens.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.MaxNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MaxNowPlayingBackgroundDimPercent
import paige.navic.domain.models.MinNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MinNowPlayingBackgroundDimPercent
import paige.navic.domain.models.LidaClipsVideoCacheSizeOptionsMb
import paige.navic.domain.models.lidaClipsVideoCacheSizeLabel
import paige.navic.domain.models.normalizedBinderyBookGridColumns
import paige.navic.domain.models.nowPlayingBackgroundBlurDp
import paige.navic.domain.models.settings.*
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderFontSourceNavic
import paige.navic.reader.ReaderLightTheme
import paige.navic.reader.ReaderOrientationDefault
import paige.navic.reader.ReaderNavBarTypeVerticalRight
import paige.navic.reader.ReaderPdfFitWidth
import paige.navic.reader.ReaderSansFontFamily
import paige.navic.reader.ReaderTapZoneDefault
import paige.navic.reader.ReaderTapZoneInvertNone
import kotlin.math.roundToInt

@Composable
internal fun settingsSearchDeveloperRows(context: SettingsSearchContext): List<SearchableSettingsRow> = with(context) {
	buildList {
		if (!isApple) {
			add(switchRow(
				id = "developer.updates",
				path = path(developer),
				title = stringResource(Res.string.option_check_for_updates),
				subtitle = stringResource(Res.string.subtitle_check_for_updates),
				value = preferenceManager.checkForUpdates,
				onSetValue = { preferenceManager.checkForUpdates = it }
			))
		}
		add(switchRow(
			id = "developer.issue-logging",
			path = path(developer),
			title = stringResource(Res.string.option_issue_logging),
			subtitle = stringResource(Res.string.subtitle_issue_logging),
			keywords = listOf("logs", "diagnostics", "playback", "errors", "issues"),
			value = preferenceManager.issueLoggingEnabled,
			onSetValue = appLogManager::setEnabled
		))
		if (isAndroid) {
			add(switchRow(
				id = "developer.web-debugging",
				path = path(developer),
				title = stringResource(Res.string.option_ebook_reader_web_debugging),
				subtitle = stringResource(Res.string.subtitle_ebook_reader_web_debugging),
				keywords = listOf("reader", "ebook", "EPUB", "WebView", "DevTools", "debugging", "diagnostics"),
				value = preferenceManager.readerWebContentsDebuggingEnabled,
				onSetValue = { enabled -> preferenceManager.readerWebContentsDebuggingEnabled = enabled }
			))
		}
		add(switchRow(
			id = "developer.show-tap-zones",
			path = path(developer),
			title = stringResource(Res.string.option_ebook_reader_show_tap_zones),
			subtitle = stringResource(Res.string.subtitle_ebook_reader_show_tap_zones),
			keywords = listOf("reader", "ebook", "EPUB", "tap", "gesture", "Komikku", "debug", "diagnostics", "zones", "visible"),
			value = preferenceManager.readerShowTapZones,
			onSetValue = { enabled -> preferenceManager.readerShowTapZones = enabled }
		))
		add(switchRow(
			id = "developer.reverse-proxy-basic-auth",
			path = path(developer, stringResource(Res.string.title_reverse_proxy_auth)),
			title = stringResource(Res.string.option_reverse_proxy_basic_auth),
			subtitle = stringResource(Res.string.subtitle_reverse_proxy_basic_auth),
			keywords = listOf("Traefik", "Authorization", "Basic Auth"),
			value = preferenceManager.reverseProxyBasicAuthEnabled,
			onSetValue = {
				preferenceManager.reverseProxyBasicAuthEnabled = it
				sessionManager.refreshClient()
			}
		))
		add(textFieldRow(
			id = "developer.reverse-proxy-username",
			path = path(developer, stringResource(Res.string.title_reverse_proxy_auth)),
			title = stringResource(Res.string.option_reverse_proxy_username),
			value = preferenceManager.reverseProxyBasicAuthUsername,
			keywords = listOf("Traefik", "Basic Auth"),
			onValueChange = {
				preferenceManager.reverseProxyBasicAuthUsername = it
				sessionManager.refreshClient()
			}
		))
		add(textFieldRow(
			id = "developer.reverse-proxy-password",
			path = path(developer, stringResource(Res.string.title_reverse_proxy_auth)),
			title = stringResource(Res.string.option_reverse_proxy_password),
			value = preferenceManager.reverseProxyBasicAuthPassword,
			keywords = listOf("Traefik", "Basic Auth"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = {
				preferenceManager.reverseProxyBasicAuthPassword = it
				sessionManager.refreshClient()
			}
		))
	}
}