package paige.navic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import paige.navic.domain.manager.PreferenceManager
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.screens.reader.LivingPlateInkTuning

private val LivingPlatePaceNames = listOf("Steady", "Slow - fast - slow", "Fast start, eases off", "Slow start")

/**
 * Developer-only tuning of the living-plate ink colouring (the colouring lab's knobs). Changes apply
 * the next time a plate arrives on screen.
 */
@Composable
internal fun LivingPlateTuningSection(preferenceManager: PreferenceManager) {
	Form {
		FormRow {
			Column(Modifier.weight(1f)) {
				Text("Living plates: ink colouring")
				Text(
					"Applies the next time a plate comes on screen.",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
		TuningSlider("Colouring time", preferenceManager.livingPlateColourMillis, 500, 10_000, 100,
			{ seconds(it, 10) }) { preferenceManager.livingPlateColourMillis = it }
		TuningSlider("Pace", preferenceManager.livingPlatePace, 0, 3, 1,
			{ LivingPlatePaceNames.getOrElse(it) { "?" } }) { preferenceManager.livingPlatePace = it }
		TuningSlider("Feathery tendrils", preferenceManager.livingPlateInkTendrils, 0, 60, 1,
			{ "$it%" }) { preferenceManager.livingPlateInkTendrils = it }
		TuningSlider("Edge raggedness", preferenceManager.livingPlateInkRagged, 0, 40, 1,
			{ "$it%" }) { preferenceManager.livingPlateInkRagged = it }
		TuningSlider("Splatter specks", preferenceManager.livingPlateInkSpecks, 0, 100, 1,
			{ "$it%" }) { preferenceManager.livingPlateInkSpecks = it }
		TuningSlider("Front softness", preferenceManager.livingPlateInkEdge, 2, 300, 1,
			{ "${it / 1000.0}" }) { preferenceManager.livingPlateInkEdge = it }
		TuningSlider("Ink variation", preferenceManager.livingPlateInkSeed, 1, 50, 1,
			{ "#$it" }) { preferenceManager.livingPlateInkSeed = it }
		TuningSlider("Fade into loop", preferenceManager.livingPlateLoopFadeMillis, 0, 3_000, 50,
			{ seconds(it, 100) }) { preferenceManager.livingPlateLoopFadeMillis = it }
		FormRow(onClick = {
			val defaults = LivingPlateInkTuning()
			preferenceManager.livingPlateColourMillis = defaults.colourMillis
			preferenceManager.livingPlatePace = defaults.pace
			preferenceManager.livingPlateInkTendrils = defaults.tendrilsPercent
			preferenceManager.livingPlateInkRagged = defaults.raggedPercent
			preferenceManager.livingPlateInkSpecks = defaults.specksPercent
			preferenceManager.livingPlateInkEdge = defaults.edgePerMille
			preferenceManager.livingPlateInkSeed = defaults.seed
			preferenceManager.livingPlateLoopFadeMillis = defaults.loopFadeMillis
		}) {
			Text("Reset living plate colouring", color = MaterialTheme.colorScheme.primary)
		}
	}
}

/** [millis] as seconds rounded to 1/[per] s. */
private fun seconds(millis: Int, per: Int): String = "${(millis * per / 1000.0).roundToInt() / per.toDouble()} s"

@Composable
private fun TuningSlider(
	label: String,
	value: Int,
	min: Int,
	max: Int,
	step: Int,
	format: (Int) -> String,
	onChange: (Int) -> Unit
) {
	FormRow {
		Column(Modifier.fillMaxWidth()) {
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
				Text(label)
				Text(
					format(value),
					modifier = Modifier.padding(start = 16.dp),
					fontFamily = FontFamily.Monospace,
					fontSize = 13.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			val steps = ((max - min) / step - 1).coerceAtLeast(0)
			Slider(
				value = value.coerceIn(min, max).toFloat(),
				onValueChange = { raw -> onChange((((raw - min) / step).roundToInt() * step + min).coerceIn(min, max)) },
				valueRange = min.toFloat()..max.toFloat(),
				steps = if (steps <= 100) steps else 0
			)
		}
	}
}
