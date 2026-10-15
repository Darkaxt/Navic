package android.webkit

/** Test-only subclass in the SDK constructor's package; values stay synthetic and in memory. */
public class ReaderSyntheticWebResourceError(
	private val suppliedCode: Int,
	private val suppliedDescription: CharSequence,
) : WebResourceError() {
	override fun getErrorCode(): Int = suppliedCode
	override fun getDescription(): CharSequence = suppliedDescription
}
