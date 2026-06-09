package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import kotlinx.collections.immutable.toImmutableList
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_ebook_reader_font_family
import navic.composeapp.generated.resources.option_ebook_reader_font_family_sans
import navic.composeapp.generated.resources.option_ebook_reader_font_family_serif
import navic.composeapp.generated.resources.option_ebook_reader_font_size
import navic.composeapp.generated.resources.option_ebook_reader_flow
import navic.composeapp.generated.resources.option_ebook_reader_line_height
import navic.composeapp.generated.resources.option_ebook_reader_margin
import navic.composeapp.generated.resources.option_ebook_reader_media_overlay
import navic.composeapp.generated.resources.option_ebook_reader_paged
import navic.composeapp.generated.resources.option_ebook_reader_scroll
import navic.composeapp.generated.resources.option_ebook_reader_theme
import navic.composeapp.generated.resources.option_ebook_reader_theme_dark
import navic.composeapp.generated.resources.option_ebook_reader_theme_light
import navic.composeapp.generated.resources.option_ebook_reader_web_debugging
import navic.composeapp.generated.resources.subtitle_ebook_reader_font_family
import navic.composeapp.generated.resources.subtitle_ebook_reader_font_size
import navic.composeapp.generated.resources.subtitle_ebook_reader_line_height
import navic.composeapp.generated.resources.subtitle_ebook_reader_margin
import navic.composeapp.generated.resources.subtitle_ebook_reader_media_overlay
import navic.composeapp.generated.resources.subtitle_ebook_reader_paged
import navic.composeapp.generated.resources.subtitle_ebook_reader_theme
import navic.composeapp.generated.resources.subtitle_ebook_reader_web_debugging
import navic.composeapp.generated.resources.title_ebook_reader
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.reader.ReaderDarkTheme
import paige.navic.reader.ReaderLightTheme
import paige.navic.reader.ReaderSansFontFamily
import paige.navic.reader.ReaderSerifFontFamily
import paige.navic.reader.readerDefaultSettings
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormTitle
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.screens.settings.components.SettingSelectionRow
import paige.navic.ui.screens.settings.components.SettingSwitchRow
import paige.navic.util.core.PlatformType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsEbooksScreen() {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val settings = preferenceManager.readerDefaultSettings()
	val fontFamily = ReaderFontFamilyOption.forFontFamily(settings.fontFamily)
	val theme = ReaderThemeOption.forTheme(settings.theme)
	val flow = if (settings.paged == false) ReaderFlowOption.Scroll else ReaderFlowOption.Paged
	val lineHeightPercent = (((settings.lineHeight ?: 1.55) * 100.0).roundToInt())

	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_ebook_reader)) },
				hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
			Column(
				Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(top = 16.dp, end = 16.dp, start = 16.dp, bottom = 32.dp)
			) {
				FormTitle(stringResource(Res.string.title_ebook_reader))
				Form(Modifier.fillMaxWidth()) {
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_font_family)) },
						items = ReaderFontFamilyOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_font_family),
						selection = fontFamily,
						onSelect = { option -> preferenceManager.readerFontFamily = option.fontFamily }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_font_size)) },
						items = readerFontSizeOptions.toImmutableList(),
						label = { percent -> "$percent%" },
						description = stringResource(Res.string.subtitle_ebook_reader_font_size),
						selection = settings.fontSizePercent ?: 100,
						onSelect = { percent -> preferenceManager.readerFontSizePercent = percent }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_line_height)) },
						items = readerLineHeightOptions.toImmutableList(),
						label = { percent -> readerLineHeightLabel(percent) },
						description = stringResource(Res.string.subtitle_ebook_reader_line_height),
						selection = lineHeightPercent,
						onSelect = { percent -> preferenceManager.readerLineHeightPercent = percent }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_margin)) },
						items = readerMarginOptions.toImmutableList(),
						label = { percent -> "$percent%" },
						description = stringResource(Res.string.subtitle_ebook_reader_margin),
						selection = settings.marginPercent ?: 0,
						onSelect = { percent -> preferenceManager.readerMarginPercent = percent }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_theme)) },
						items = ReaderThemeOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_theme),
						selection = theme,
						onSelect = { option -> preferenceManager.readerTheme = option.theme }
					)
					SettingSelectionRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_flow)) },
						items = ReaderFlowOption.entries.toImmutableList(),
						label = { option -> stringResource(option.title) },
						description = stringResource(Res.string.subtitle_ebook_reader_paged),
						selection = flow,
						onSelect = { option -> preferenceManager.readerPaged = option.paged }
					)
					SettingSwitchRow(
						title = { Text(stringResource(Res.string.option_ebook_reader_media_overlay)) },
						subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_media_overlay)) },
						value = preferenceManager.readerMediaOverlayEnabled,
						onSetValue = { enabled -> preferenceManager.readerMediaOverlayEnabled = enabled }
					)
					if (platformContext.platformType == PlatformType.Android) {
						SettingSwitchRow(
							title = { Text(stringResource(Res.string.option_ebook_reader_web_debugging)) },
							subtitle = { Text(stringResource(Res.string.subtitle_ebook_reader_web_debugging)) },
							value = preferenceManager.readerWebContentsDebuggingEnabled,
							onSetValue = { enabled -> preferenceManager.readerWebContentsDebuggingEnabled = enabled }
						)
					}
				}
			}
		}
	}
}

private enum class ReaderFontFamilyOption(
	val fontFamily: String,
	val title: StringResource
) {
	Sans(ReaderSansFontFamily, Res.string.option_ebook_reader_font_family_sans),
	Serif(ReaderSerifFontFamily, Res.string.option_ebook_reader_font_family_serif);

	companion object {
		fun forFontFamily(fontFamily: String?): ReaderFontFamilyOption =
			entries.firstOrNull { option -> option.fontFamily == fontFamily } ?: Sans
	}
}

private enum class ReaderThemeOption(
	val theme: String,
	val title: StringResource
) {
	Light(ReaderLightTheme, Res.string.option_ebook_reader_theme_light),
	Dark(ReaderDarkTheme, Res.string.option_ebook_reader_theme_dark);

	companion object {
		fun forTheme(theme: String?): ReaderThemeOption =
			entries.firstOrNull { option -> option.theme == theme } ?: Light
	}
}

private enum class ReaderFlowOption(
	val paged: Boolean,
	val title: StringResource
) {
	Paged(true, Res.string.option_ebook_reader_paged),
	Scroll(false, Res.string.option_ebook_reader_scroll)
}

private val readerFontSizeOptions = listOf(90, 100, 112, 125, 140, 160, 180)
private val readerLineHeightOptions = listOf(120, 135, 155, 170, 190, 220)
private val readerMarginOptions = listOf(0, 4, 8, 12, 16, 24)

@Composable
private fun readerLineHeightLabel(percent: Int): String =
	"${percent / 100}.${(percent % 100).toString().padStart(2, '0')}".trimEnd('0').trimEnd('.')
