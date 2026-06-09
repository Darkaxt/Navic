package paige.navic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_copy_logs
import navic.composeapp.generated.resources.action_delete
import navic.composeapp.generated.resources.info_no_logs
import navic.composeapp.generated.resources.title_logs
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.domain.manager.AppLogEntry
import paige.navic.domain.manager.AppLogManager
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Code
import paige.navic.icons.outlined.Copy
import paige.navic.icons.outlined.Delete
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.util.core.AppLogLevel

@Composable
fun SettingsLogsScreen() {
	val appLogManager = koinInject<AppLogManager>()
	val entries by appLogManager.entries.collectAsState()
	val listState = rememberLazyListState()
	@Suppress("DEPRECATION")
	val clipboardManager = LocalClipboardManager.current

	LaunchedEffect(entries.size) {
		if (entries.isNotEmpty()) {
			listState.requestScrollToItem(entries.lastIndex)
		}
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_logs)) },
				actions = {
					TopBarButton(
						onClick = { clipboardManager.setText(AnnotatedString(appLogManager.exportText())) },
						enabled = entries.isNotEmpty()
					) {
						Icon(Icons.Outlined.Copy, stringResource(Res.string.action_copy_logs))
					}
					TopBarButton(
						onClick = appLogManager::clear,
						enabled = entries.isNotEmpty()
					) {
						Icon(Icons.Outlined.Delete, stringResource(Res.string.action_delete))
					}
				}
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
			if (entries.isEmpty()) {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.padding(innerPadding),
					contentAlignment = Alignment.Center
				) {
					ContentUnavailable(
						icon = Icons.Outlined.Code,
						label = stringResource(Res.string.info_no_logs)
					)
				}
			} else {
				LazyColumn(
					modifier = Modifier.horizontalScroll(rememberScrollState()),
					state = listState,
					contentPadding = PaddingValues(
						top = innerPadding.calculateTopPadding() + 8.dp,
						bottom = innerPadding.calculateBottomPadding() + 8.dp,
						start = 8.dp,
						end = 8.dp
					)
				) {
					items(entries, key = { it.id }) { entry ->
						LogEntryRow(entry = entry)
					}
				}
			}
		}
	}
}

@Composable
private fun LogEntryRow(entry: AppLogEntry) {
	@Suppress("DEPRECATION")
	val clipboardManager = LocalClipboardManager.current
	Surface(
		onClick = {
			clipboardManager.setText(AnnotatedString(entry.toLogText()))
		}
	) {
		Row(
			verticalAlignment = Alignment.Top,
			modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
		) {
			Box(
				modifier = Modifier
					.padding(end = 6.dp)
					.size(24.dp)
					.clip(MaterialTheme.shapes.extraSmall)
					.background(entry.level.containerColor()),
				contentAlignment = Alignment.Center
			) {
				Text(
					text = entry.level.shortLabel,
					fontSize = 12.sp,
					color = entry.level.contentColor()
				)
			}
			Column {
				Text(
					text = "${entry.timestampMillis} ${entry.level.shortLabel}/${entry.tag}: ${entry.message}",
					fontFamily = FontFamily.Monospace,
					fontSize = 12.sp,
					maxLines = 1
				)
				entry.throwable?.takeIf { it.isNotBlank() }?.let { throwable ->
					Text(
						text = throwable,
						fontFamily = FontFamily.Monospace,
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}
	}
}

private fun AppLogEntry.toLogText(): String =
	buildString {
		append(timestampMillis)
		append(" ")
		append(level.shortLabel)
		append("/")
		append(tag)
		append(": ")
		append(message)
		throwable?.takeIf { it.isNotBlank() }?.let {
			append("\n")
			append(it)
		}
	}

@Composable
private fun AppLogLevel.containerColor() =
	when (this) {
		AppLogLevel.Debug -> MaterialTheme.colorScheme.surfaceContainerHighest
		AppLogLevel.Info -> MaterialTheme.colorScheme.primaryContainer
		AppLogLevel.Warning -> MaterialTheme.colorScheme.tertiaryContainer
		AppLogLevel.Error -> MaterialTheme.colorScheme.errorContainer
	}

@Composable
private fun AppLogLevel.contentColor() =
	when (this) {
		AppLogLevel.Debug -> MaterialTheme.colorScheme.onSurfaceVariant
		AppLogLevel.Info -> MaterialTheme.colorScheme.onPrimaryContainer
		AppLogLevel.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
		AppLogLevel.Error -> MaterialTheme.colorScheme.onErrorContainer
	}
