package paige.navic.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderEngineRenderer

@Composable
fun ReaderViewerHost(
	readerTitle: String,
	controllerState: ReaderControllerState,
	engineRenderer: ReaderEngineRenderer,
	onEngineHostEvent: (ReaderEngineHostEvent) -> Unit,
	modifier: Modifier = Modifier
) {
	Box(
		modifier = modifier
			.background(Color.Black),
		contentAlignment = Alignment.Center
	) {
		if (controllerState.errorMessage != null) {
			ReaderViewerStatus(
				title = readerTitle,
				primary = "Reader error",
				secondary = controllerState.errorMessage,
				modifier = Modifier
					.fillMaxWidth(0.72f)
					.padding(24.dp)
			)
		} else {
			ReaderEngineContent(
				readerTitle = readerTitle,
				engineRenderer = engineRenderer,
				onEngineHostEvent = onEngineHostEvent,
				modifier = Modifier.matchParentSize()
			)
		}
	}
}

@Composable
private fun ReaderEngineContent(
	readerTitle: String,
	engineRenderer: ReaderEngineRenderer,
	onEngineHostEvent: (ReaderEngineHostEvent) -> Unit,
	modifier: Modifier = Modifier
) {
	when (engineRenderer) {
		ReaderEngineRenderer.Empty -> Box(
			modifier = modifier.background(Color(0xFF202329)),
			contentAlignment = Alignment.Center
		) {
			ReaderViewerStatus(
				title = readerTitle,
				primary = "Preparing reader",
				secondary = "Resolving publication resources for the engine adapter.",
				modifier = Modifier
					.fillMaxWidth(0.72f)
					.padding(24.dp)
			)
		}
		is ReaderEngineRenderer.FoliatePublication -> ReaderEngineWebViewHost(
			publicationUrl = engineRenderer.publicationUrl,
			title = engineRenderer.title.ifBlank { readerTitle },
			kind = engineRenderer.kind,
			mediaOverlayEnabled = engineRenderer.mediaOverlayEnabled,
			externalShellCover = engineRenderer.externalShellCover,
			suppressWebShellCover = engineRenderer.suppressWebShellCover,
			nativeShellCoverTint = engineRenderer.nativeShellCoverTint,
			settings = engineRenderer.settings,
			startCfi = engineRenderer.startLocator?.cfi,
			startHref = engineRenderer.startLocator?.href,
			startProgress = engineRenderer.startLocator?.progress,
			rawTextProvenanceDescriptors = engineRenderer.rawTextProvenanceDescriptors,
			command = engineRenderer.command,
			commandKey = engineRenderer.commandKey,
			onEvent = onEngineHostEvent,
			modifier = modifier
		)
	}
}

@Composable
private fun ReaderViewerStatus(
	title: String,
	primary: String,
	secondary: String,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.headlineMedium,
			fontWeight = FontWeight.SemiBold,
			textAlign = TextAlign.Center,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			color = Color.White
		)
		Spacer(Modifier.height(18.dp))
		Text(
			text = primary,
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.SemiBold,
			textAlign = TextAlign.Center,
			color = Color.White
		)
		Spacer(Modifier.height(8.dp))
		Text(
			text = secondary,
			style = MaterialTheme.typography.bodyLarge,
			textAlign = TextAlign.Center,
			color = Color(0xCCFFFFFF)
		)
	}
}
