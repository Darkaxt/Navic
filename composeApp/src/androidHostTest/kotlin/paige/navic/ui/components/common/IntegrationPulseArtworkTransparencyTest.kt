package paige.navic.ui.components.common

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class IntegrationPulseArtworkTransparencyTest {
	@Test
	fun brandedPulseArtworkHasTransparentCorners() {
		val offenders = PulseArtworkFiles.filterNot { file ->
			val image = ImageIO.read(pulseArtwork(file)) ?: error("Could not read $file")
			listOf(
				image.getRGB(0, 0),
				image.getRGB(image.width - 1, 0),
				image.getRGB(0, image.height - 1),
				image.getRGB(image.width - 1, image.height - 1)
			).all { argb -> (argb ushr 24) == 0 }
		}

		assertTrue(
			offenders.isEmpty(),
			"Pulse artwork corners must be transparent; opaque corners render as square backgrounds. Offenders: $offenders"
		)
	}

	private fun pulseArtwork(name: String): File =
		listOf(
			File("src/commonMain/composeResources/drawable/$name"),
			File("composeApp/src/commonMain/composeResources/drawable/$name")
		).firstOrNull { it.isFile }
			?: error("Could not locate $name")
}

private val PulseArtworkFiles = listOf(
	"aurral_logo_pulse.png",
	"bindery_logo_pulse.png",
	"lastfm_logo_pulse.png",
	"lidaclips_logo_pulse.png",
	"musicbrainz_logo_color_pulse.png"
)
