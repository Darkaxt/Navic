package paige.navic.domain.models

import paige.navic.domain.models.settings.ToolbarPosition

fun shouldShowSheetToolbarTop(position: ToolbarPosition): Boolean =
	position == ToolbarPosition.Top

fun shouldShowSheetToolbarBottom(position: ToolbarPosition): Boolean =
	position == ToolbarPosition.Bottom

fun shouldReserveNowPlayingToolbarGap(
	position: ToolbarPosition,
	isLandscape: Boolean
): Boolean =
	!isLandscape && position != ToolbarPosition.Hidden
