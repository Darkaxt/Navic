package paige.navic.reader

/** Subordinate numeric metadata from the original Live Foliate paginator, scoped by
 * the command-bound destination result carrying it. This is NOT a semantic binding,
 * pagination-profile upgrade, allocation, captured decoration or displayed frame.
 * Native allocation must separately authenticate current destination/configuration
 * and the exact original physical installation before consuming this metadata. */
data class ReaderFoliateResolvedLayout(
    val layoutGeneration: Long,
    val viewGeneration: Long,
    val navigationGeneration: Long,
    val sectionIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val width: Double,
    val height: Double
) {
    init {
        require(layoutGeneration in 1L..9_007_199_254_740_991L)
        require(viewGeneration in 1L..9_007_199_254_740_991L)
        require(navigationGeneration in 0L..9_007_199_254_740_991L)
        require(sectionIndex >= 0 && pageCount > 0 && pageIndex in 0 until pageCount)
        require(width.isFinite() && width > 0.0 && height.isFinite() && height > 0.0)
    }
}
