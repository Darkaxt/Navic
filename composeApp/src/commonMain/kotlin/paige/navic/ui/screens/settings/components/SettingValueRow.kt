package paige.navic.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import paige.navic.ui.components.common.FormRow

@Composable
fun SettingValueRow(
	title: @Composable () -> Unit,
	value: String,
	subtitle: (@Composable () -> Unit)? = null,
	contentPadding: PaddingValues = PaddingValues(14.dp),
	onClick: (() -> Unit)? = null
) {
	FormRow(
		contentPadding = contentPadding,
		onClick = onClick
	) {
		Column(Modifier.weight(1f)) {
			title()
			subtitle?.let { subtitle ->
				CompositionLocalProvider(
					LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				) {
					subtitle()
				}
			}
		}
		SettingTrailingValueText(text = value)
	}
}
