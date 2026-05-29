package paige.navic.ui.components.toolbars

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import paige.navic.LocalPlatformContext
import paige.navic.ui.theme.defaultFont

internal data class SheetToolbarPadding(
	val horizontal: Dp,
	val top: Dp,
	val bottom: Dp
)

internal fun sheetToolbarPadding(
	isLandscape: Boolean,
	isBottomToolbar: Boolean
): SheetToolbarPadding {
	if (isLandscape) {
		return SheetToolbarPadding(
			horizontal = 16.dp,
			top = 0.dp,
			bottom = if (isBottomToolbar) 12.dp else 0.dp
		)
	}

	return if (isBottomToolbar) {
		SheetToolbarPadding(horizontal = 16.dp, top = 12.dp, bottom = 36.dp)
	} else {
		SheetToolbarPadding(horizontal = 16.dp, top = 24.dp, bottom = 24.dp)
	}
}

@Composable
fun SheetToolbar(
	modifier: Modifier = Modifier,
	windowInsets: WindowInsets,
	isBottomToolbar: Boolean = false,
	title: @Composable () -> Unit = {},
	navigationIcon: @Composable () -> Unit,
	actions: @Composable () -> Unit = {}
) {
	val platformContext = LocalPlatformContext.current
	val isLandscape = platformContext.sizeClass.widthSizeClass > WindowWidthSizeClass.Compact
	val toolbarPadding = sheetToolbarPadding(
		isLandscape = isLandscape,
		isBottomToolbar = isBottomToolbar
	)
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = toolbarPadding.horizontal)
			.padding(top = toolbarPadding.top, bottom = toolbarPadding.bottom)
			.windowInsetsPadding(windowInsets),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			navigationIcon()
			CompositionLocalProvider(
				LocalTextStyle provides MaterialTheme.typography.bodyMedium
					.copy(
						fontFamily = defaultFont(round = 100f),
						shadow = Shadow(
							color = MaterialTheme.colorScheme.inverseOnSurface,
							offset = Offset(0f, 4f),
							blurRadius = 10f
						)
					)
			) {
				title()
			}
		}
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp)
		) {
			actions()
		}
	}
}

@Composable
fun SheetActionButton(
	icon: ImageVector,
	contentDescription: String,
	isStartRounded: Boolean = false,
	isEndRounded: Boolean = false,
	containerColor: Color? = null,
	contentColor: Color? = null,
	onClick: () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isPressed by interactionSource.collectIsPressedAsState()
	val startRadius by animateDpAsState(if (isStartRounded || isPressed) 12.dp else 4.dp)
	val endRadius by animateDpAsState(if (isEndRounded || isPressed) 12.dp else 4.dp)
	Surface(
		onClick = onClick,
		shape = ContinuousRoundedRectangle(
			topStart = startRadius,
			bottomStart = startRadius,
			topEnd = endRadius,
			bottomEnd = endRadius
		),
		color = containerColor ?: MaterialTheme.colorScheme.surfaceContainer,
		contentColor = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.size(45.dp, 40.dp),
		interactionSource = interactionSource,
		shadowElevation = 4.dp
	) {
		Box(contentAlignment = Alignment.Center) {
			Icon(
				imageVector = icon,
				contentDescription = contentDescription,
				modifier = Modifier.size(20.dp)
			)
		}
	}
}
